"""Upgrade disposable old-alpha worlds without opening a user's saved world."""

import argparse
import json
import shutil
import subprocess
from pathlib import Path

from qualify_110 import checked_orespawn_sha256, digest, world_fingerprint, write_json, FORGE_JAR
from ci_runtime import FORGE_HASH
from mines_110 import run


ROOT = Path(__file__).resolve().parents[2]
OLD_ALPHA_SHA256 = "ACBBE6BB44E72500262F7D43DA7720EF15FCF402BD1A37BA4263707F8CC185BB"


def properties(path):
    return dict(line.strip().split("=", 1) for line in path.read_text().splitlines()
                if "=" in line and not line.startswith("#"))


def edit_definition(value):
    count = 0
    if isinstance(value, dict):
        definition = value.get("definition")
        if isinstance(definition, dict) and definition.get("id") == "realisticdeposits:overworld_iron_formation":
            definition["abandoned_mine"] = {"enabled": True, "chance": 1.0}
            count += 1
        for child in value.values():
            count += edit_definition(child)
    elif isinstance(value, list):
        for child in value:
            count += edit_definition(child)
    return count


def expected_upgrade_profile(value):
    if isinstance(value, dict):
        if value.get("owner") == "realisticdeposits" and value.get("owner_version") == "0.1.1.110021":
            value["owner_version"] = "0.2.0.110021"
        for child in value.values():
            expected_upgrade_profile(child)
    elif isinstance(value, list):
        for child in value:
            expected_upgrade_profile(child)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "old-mod", "orespawn", "probe", "legacy-probe", "reference", "output"):
        parser.add_argument(f"--{name}", required=True, type=Path)
    parser.add_argument("--local-orespawn-sha256")
    args = parser.parse_args()
    output = args.output.resolve()
    assert not output.exists(), "Choose a new evidence directory"
    assert digest(args.old_mod) == OLD_ALPHA_SHA256, "The old fixture must use the exact public 0.1.1 alpha"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH, "Unexpected Forge runtime"
    checked_orespawn_sha256(args.orespawn, args.local_orespawn_sha256)
    receipt = properties(args.reference)
    marker = [int(part) for part in receipt["legacy_marker"].split(",")]
    raw = [int(part) for part in receipt["legacy_raw_marker"].split(",")]
    output.mkdir(parents=True)
    inputs = output / "inputs"
    inputs.mkdir()
    for role, source in (("current", args.mod), ("old", args.old_mod), ("orespawn", args.orespawn),
                         ("probe", args.probe), ("legacy", args.legacy_probe)):
        target = inputs / f"{role}.jar"
        shutil.copy2(source, target)
    write_json(output / "artifacts.json", {path.name: digest(path) for path in inputs.iterdir()})
    baseline = output / "old-alpha"
    baseline.mkdir()
    shutil.copytree(args.runtime / "libraries", baseline / "libraries")
    for name in (FORGE_JAR, "minecraft_server.1.10.2.jar", "eula.txt"):
        shutil.copy2(args.runtime / name, baseline / name)
    mods = baseline / "mods"
    mods.mkdir()
    for name in ("old", "orespawn", "legacy"):
        shutil.copy2(inputs / f"{name}.jar", mods / f"{name}.jar")
    settings = (
        "level-name=mine-test-world\nlevel-seed=3346071746553592696\nlevel-type=FLAT\n"
        "generator-settings=3;minecraft:bedrock,127*minecraft:stone,minecraft:grass;1;\n"
        "server-ip=127.0.0.1\nserver-port=48640\nonline-mode=false\ngenerate-structures=false\n"
        "view-distance=3\nmax-tick-time=-1\nallow-nether=false\nspawn-animals=false\nspawn-monsters=false\n"
    )
    (baseline / "server.properties").write_text(settings, encoding="utf-8", newline="\n")
    catalog = json.loads((ROOT / "src/main/resources/assets/realisticdeposits/defaults/vanilla.json")
                         .read_text(encoding="utf-8"))
    catalog["deposits"][0].pop("abandoned_mine", None)
    write_json(baseline / "config/realisticdeposits/deposits/vanilla.json", catalog)
    command = [str(args.java), "-Xms256m", "-Xmx2G", "-XX:ActiveProcessorCount=2", "-Drd.mines.legacy=true"]
    for key, value in zip(("markerX", "markerY", "markerZ", "rawX", "rawY", "rawZ"), marker + raw):
        command.append(f"-Drd.mines.legacy.{key}={value}")
    command.extend(["-jar", FORGE_JAR, "nogui"])
    with (baseline / "baseline.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=baseline, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            code = process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise AssertionError("The old-alpha fixture timed out")
    log = (baseline / "baseline.log").read_text(encoding="utf-8", errors="replace")
    assert code == 0 and "RD_MINES_LEGACY_PASS" in log, "Old-alpha fixture failed"
    errors = [line for line in log.splitlines() if ("/ERROR]" in line or "/FATAL]" in line)
              and "FML appears to be missing any signature data" not in line]
    assert not errors and not list((baseline / "crash-reports").glob("*")), errors
    fml = baseline / "logs/fml-server-latest.log"
    if fml.is_file():
        shutil.copy2(fml, baseline / "baseline.fml.log")
    before = world_fingerprint(baseline)
    results = []
    for explicit in (False, True):
        name = "explicit-opt-in" if explicit else "inherited-profile"
        target = output / name
        shutil.copytree(baseline, target, ignore=shutil.ignore_patterns("mods", "logs", "*.log"))
        mods = target / "mods"
        mods.mkdir()
        for role in ("current", "orespawn", "probe", "legacy"):
            shutil.copy2(inputs / f"{role}.jar", mods / f"{role}.jar")
        (target / "server.properties").write_text(settings.replace("48640", "48642" if explicit else "48641"),
                                                encoding="utf-8", newline="\n")
        changed = json.loads(json.dumps(catalog))
        changed["deposits"][0]["abandoned_mine"] = {"enabled": True, "chance": 1.0}
        write_json(target / "config/realisticdeposits/deposits/vanilla.json", changed)
        profile_path = target / "mine-test-world/serverconfig/orespawn-worldgen.json"
        if explicit:
            profile = json.loads(profile_path.read_text(encoding="utf-8"))
            assert edit_definition(profile) == 1, "Expected one captured deposit definition"
            write_json(profile_path, profile)
        expected_profile = json.loads(profile_path.read_text(encoding="utf-8"))
        expected_upgrade_profile(expected_profile)
        profile_after_upgrade = None
        for phase in ("upgrade-first", "upgrade-reload", "upgrade-later"):
            run(args.java, target, phase, {"rd.mines.upgrade.optIn": str(explicit).lower()})
            assert json.loads(profile_path.read_text(encoding="utf-8")) == expected_profile, "Saved generation settings changed"
            if profile_after_upgrade is None:
                profile_after_upgrade = digest(profile_path)
            assert digest(profile_path) == profile_after_upgrade, "Reload rewrote the upgraded profile"
            results.append({"case": name, "phase": phase, "passed": True})
    assert world_fingerprint(baseline) == before, "The old source fixture was modified"
    write_json(output / "results.json", {"passed": True, "boots": len(results) + 1, "results": results})


if __name__ == "__main__":
    main()
