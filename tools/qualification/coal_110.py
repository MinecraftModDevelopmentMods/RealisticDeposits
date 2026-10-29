"""Check coal beds on disposable Forge servers with the checksum-verified OreSpawn jar."""

import argparse
import copy
import json
import shutil
import subprocess
from pathlib import Path

from ci_runtime import FORGE_HASH
from qualify_110 import FORGE_JAR, checked_orespawn_sha256, digest, write_json


ROOT = Path(__file__).resolve().parents[2]


def run(java, root, phase, case):
    command = [str(java), "-Xms256m", "-Xmx2G", "-XX:ActiveProcessorCount=2",
               "-Drd.coal.enabled=true", f"-Drd.coal.phase={phase}",
               f"-Drd.coal.workings={str(case == 'workings').lower()}",
               f"-Drd.coal.survey={str(case == 'survey').lower()}",
               f"-Drd.coal.structure={str(case == 'structure').lower()}", "-jar", FORGE_JAR, "nogui"]
    with (root / f"{phase}.log").open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=root, stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            code = process.wait(timeout=300)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise AssertionError(f"{root.name}/{phase} timed out; keep its evidence")
    log = (root / f"{phase}.log").read_text(encoding="utf-8", errors="replace")
    assert code == 0 and f"RD_COAL_PASS {phase}" in log, f"{root.name}/{phase} failed"
    unexpected = [line for line in log.splitlines() if ("/ERROR]" in line or "/FATAL]" in line)
                  and "FML appears to be missing any signature data" not in line]
    assert not unexpected and not list((root / "crash-reports").glob("*")), unexpected
    if (root / "logs/fml-server-latest.log").is_file():
        shutil.copy2(root / "logs/fml-server-latest.log", root / f"{phase}.fml.log")
    print(f"PASS {root.name}/{phase}", flush=True)
    return json.loads((root / f"coal-{phase}.json").read_text(encoding="utf-8"))


def prepare(args, root, mineralogy=False, normal=False):
    root.mkdir()
    shutil.copytree(args.runtime / "libraries", root / "libraries")
    for name in (FORGE_JAR, "minecraft_server.1.10.2.jar", "eula.txt"):
        shutil.copy2(args.runtime / name, root / name)
    mods = root / "mods"
    mods.mkdir()
    for source in (args.mod, args.orespawn, args.probe):
        shutil.copy2(source, mods / source.name)
    if mineralogy:
        assert args.mineralogy, "Supply the exact compatible Mineralogy jar"
        shutil.copy2(args.mineralogy, mods / args.mineralogy.name)
    settings = (
        "level-name=coal-test-world\nlevel-seed=3346071746553592696\n"
        f"level-type={'DEFAULT' if normal else 'FLAT'}\n"
        "generator-settings=3;minecraft:bedrock,127*minecraft:stone,minecraft:grass;1;decoration\n"
        f"server-ip=127.0.0.1\nserver-port={getattr(args, 'port', 48771)}\nonline-mode=false\ngenerate-structures=false\n"
        "view-distance=3\nmax-tick-time=-1\nallow-nether=false\nspawn-animals=false\nspawn-monsters=false\n"
    )
    (root / "server.properties").write_text(settings, encoding="utf-8", newline="\n")
    for filename in ("ops.json", "whitelist.json", "banned-ips.json", "banned-players.json"):
        write_json(root / filename, [])
    return root


def same(left, right):
    for key in ("terrain_sha256", "coal_sha256", "coal_in_body", "coal_outside_body", "planned_positions"):
        assert left[key] == right[key], f"Reload changed {key}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "orespawn", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--mineralogy", type=Path)
    parser.add_argument("--geology-profile", type=Path, help="Read-only reference for the inactive-strata fixture")
    parser.add_argument("--local-orespawn-sha256", help="Explicit checksum for an optional unpublished test candidate")
    parser.add_argument("--cases", nargs="+", choices=("standalone", "normal", "mineralogy", "inactive", "legacy", "workings", "structure"),
                        default=("standalone", "normal", "mineralogy"))
    args = parser.parse_args()
    assert not args.output.exists(), "Use a new evidence directory"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    checked_orespawn_sha256(args.orespawn, args.local_orespawn_sha256)
    assert (args.runtime / "eula.txt").read_text().strip() == "eula=true"
    args.output.mkdir(parents=True)
    inputs = args.output / "inputs"
    inputs.mkdir()
    for source in (args.mod, args.orespawn, args.probe, args.mineralogy):
        if source:
            shutil.copy2(source, inputs / source.name)
    write_json(args.output / "artifacts.json", {path.name: digest(path) for path in inputs.iterdir()})
    # Every server uses the immutable copies, even if a later build refreshes build/libs.
    for name in ("mod", "orespawn", "probe", "mineralogy"):
        source = getattr(args, name)
        if source:
            setattr(args, name, inputs / source.name)
    results = []
    for case in args.cases:
        root = prepare(args, args.output / case, case in ("mineralogy", "inactive", "legacy"), case in ("normal", "workings"))
        if case in ("workings", "structure"):
            coal = json.loads((ROOT / "src/main/resources/assets/realisticdeposits/defaults/coal.json").read_text(encoding="utf-8"))
            if case == "workings":
                coal["deposits"][0]["abandoned_mine"] = {"enabled": True, "chance": 1.0}
            else:
                coal["deposits"][0]["interaction"] = "replacement"
                coal["deposits"][0].pop("background_generation_scale")
            write_json(root / "config/realisticdeposits/deposits/coal.json", coal)
        if case in ("inactive", "legacy"):
            assert args.geology_profile, "Supply the sealed, read-only active-strata reference"
            profile = json.loads(args.geology_profile.read_text(encoding="utf-8"))
            if case == "inactive":
                profile["terrain_dimensions"]["minecraft:overworld"]["enabled"] = False
            else:
                profile["geology_mode"] = "legacy"
                profile["cyano"]["realistic_coal_layers"] = True
                profile["ores"] = {key: value for key, value in profile["ores"].items()
                                   if not key.startswith("realisticdeposits:")}
                profile.get("providers", {}).pop("realisticdeposits", None)
                profile.get("ore_providers", {}).pop("realisticdeposits", None)
                profile.pop("ore_sources", None)
                coal = json.loads((ROOT / "src/main/resources/assets/realisticdeposits/defaults/coal.json").read_text())
                # A legacy profile has one named geome, not the modern basin map.
                coal["deposits"][0]["geomes"].append("orespawn:cyano")
                write_json(root / "config/realisticdeposits/deposits/coal.json", coal)
            write_json(root / "config/orespawn-worldgen.json", profile)
        first = run(args.java, root, "first", case)
        assert first["physical_strata"] == (case in ("mineralogy", "legacy")), "Unexpected terrain-replacement state"
        assert first["legacy_coal_layers"] == (case == "legacy")
        profile = root / "coal-test-world/serverconfig/orespawn-worldgen.json"
        profile_hash = digest(profile)
        for phase in ("reload", "later", "stable"):
            report = run(args.java, root, phase, case)
            same(first["first"], report["first"])
            assert digest(profile) == profile_hash, "The saved profile changed"
            if phase == "later":
                later = report["later"]
            elif phase == "stable":
                same(later, report["later"])
        results.append({"case": case, "passed": True, "coal_in_sample": first["first"]["coal_in_body"],
                        "profile_sha256": profile_hash})
        write_json(args.output / "results.json", {"passed": True, "cases": results})


if __name__ == "__main__":
    main()
