"""Check diamond pipes and river placers in disposable packaged Forge worlds."""

import argparse
import json
import shutil
import subprocess
from pathlib import Path

from coal_110 import prepare
from qualify_110 import FORGE_JAR, checked_orespawn_sha256, digest, write_json
from ci_runtime import FORGE_HASH


ROOT = Path(__file__).resolve().parents[2]


def run(args, root, phase, case):
    command = [str(args.java), "-Xms256m", "-Xmx2G", "-XX:ActiveProcessorCount=2",
               "-Drd.diamond.enabled=true", f"-Drd.diamond.phase={phase}",
               f"-Drd.diamond.fixture_river={str(case not in ('normal', 'mineralogy', 'inactive', 'legacy')).lower()}",
               f"-Drd.diamond.reverse={str(case == 'reverse').lower()}",
               "-jar", FORGE_JAR, "nogui"]
    with (root / f"{phase}.log").open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=root, stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            code = process.wait(timeout=420)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise AssertionError(f"Timed out: {root}/{phase}; evidence retained")
    log = (root / f"{phase}.log").read_text(encoding="utf-8", errors="replace")
    assert code == 0 and f"RD_DIAMOND_PASS {phase}" in log, f"Failed: {root}/{phase}"
    unexpected = [line for line in log.splitlines() if ("/ERROR]" in line or "/FATAL]" in line)
                  and "FML appears to be missing any signature data" not in line]
    unexpected.extend(line for line in log.splitlines() if "Tried to assign a mutable BlockPos" in line)
    assert not unexpected and not list((root / "crash-reports").glob("*")), unexpected
    if (root / "logs/fml-server-latest.log").is_file():
        shutil.copy2(root / "logs/fml-server-latest.log", root / f"{phase}.fml.log")
    print(f"PASS {case}/{phase}", flush=True)
    return json.loads((root / f"diamond-{phase}.json").read_text())


def same(left, right):
    for key in ("terrain_sha256", "diamonds_sha256", "pipe_ore", "rich_gravel", "background_ore"):
        assert left[key] == right[key], f"Saved sample changed: {key}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "orespawn", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--mineralogy", type=Path)
    parser.add_argument("--geology-profile", type=Path, help="Read-only active-strata fixture for inactive and legacy cases")
    parser.add_argument("--base-port", type=int, default=48800)
    parser.add_argument("--cases", nargs="+", default=("standalone", "normal", "source-off", "placer-off", "reverse"),
                        choices=("standalone", "normal", "source-off", "source-enable", "placer-off", "reverse", "mineralogy", "inactive", "legacy"))
    args = parser.parse_args()
    assert not args.output.exists(), "Use a new evidence directory"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    checked_orespawn_sha256(args.orespawn)
    args.output.mkdir(parents=True)
    inputs = args.output / "inputs"
    inputs.mkdir()
    for name in ("mod", "orespawn", "probe", "mineralogy"):
        source = getattr(args, name)
        if source:
            destination = inputs / source.name
            shutil.copy2(source, destination)
            setattr(args, name, destination)
    write_json(args.output / "artifacts.json", {p.name: digest(p) for p in inputs.iterdir()})
    results = []
    for index, case in enumerate(args.cases):
        args.port = args.base_port + index
        natural = case in ("normal", "mineralogy", "inactive", "legacy")
        root = prepare(args, args.output / case, mineralogy=case in ("mineralogy", "inactive", "legacy"), normal=natural)
        properties = (root / "server.properties").read_text().replace("coal-test-world", "diamond-test-world")
        if not natural:
            # Fixed hosts make the chunk-order comparison independent of vanilla's
            # cross-chunk lakes and decoration. Normal cases retain that decoration.
            properties = properties.replace(";1;decoration", ";7;")
        (root / "server.properties").write_text(properties, encoding="utf-8", newline="\n")
        diamonds = json.loads((ROOT / "src/main/resources/assets/realisticdeposits/defaults/diamonds.json").read_text())
        if not natural:
            diamonds["deposits"][0]["alignment"] = "massive_rare"
            diamonds["deposits"][0].pop("geomes")
        if case in ("source-off", "source-enable"):
            diamonds["deposits"][0]["enabled"] = False
        if case == "placer-off":
            diamonds["deposits"][1]["enabled"] = False
        write_json(root / "config/realisticdeposits/deposits/diamonds.json", diamonds)
        if case in ("inactive", "legacy"):
            assert args.geology_profile, "Supply a sealed active-strata reference"
            profile = json.loads(args.geology_profile.read_text())
            profile["ores"] = {key: value for key, value in profile["ores"].items()
                               if not key.startswith("realisticdeposits:")}
            profile.get("providers", {}).pop("realisticdeposits", None)
            profile.get("ore_providers", {}).pop("realisticdeposits", None)
            profile.pop("ore_sources", None)
            if case == "inactive":
                profile["terrain_dimensions"]["minecraft:overworld"]["enabled"] = False
            else:
                profile["geology_mode"] = "legacy"
                profile["cyano"]["realistic_coal_layers"] = True
            write_json(root / "config/orespawn-worldgen.json", profile)
        first = run(args, root, "first", case)
        sample = first["first"]
        if case in ("source-off", "source-enable"):
            assert sample["shallow_ore"] == 0 and sample["rich_gravel"] == 0
        else:
            assert sample["pipe_ore"] > 0, "The pipe did not generate"
        if case in ("standalone", "reverse"):
            assert sample["rich_gravel"] > 0, "The river placer did not generate"
        if case == "placer-off":
            assert sample["rich_gravel"] == 0
        profile = root / "diamond-test-world/serverconfig/orespawn-worldgen.json"
        explicitly_enabled = None
        if case == "source-enable":
            captured = json.loads(profile.read_text())
            for rule_id, rule in captured["ores"].items():
                if rule_id.startswith("realisticdeposits:") and "overworld_diamond_" in rule_id:
                    rule["enabled"] = True
            write_json(profile, captured)
            explicitly_enabled = captured
        profile_hash = digest(profile)
        for phase in ("reload", "later", "stable"):
            current = run(args, root, phase, case)
            same(first["first"], current["first"])
            if case == "source-enable" and phase == "reload":
                refreshed = json.loads(profile.read_text())
                # Enabling a source refreshes OreSpawn's derived source directory.
                for key in set(explicitly_enabled) | set(refreshed):
                    if key not in ("ore_sources", "ore_source_policies"):
                        assert explicitly_enabled.get(key) == refreshed.get(key), f"World setting changed: {key}"
                before_policies = explicitly_enabled["ore_source_policies"]
                after_policies = refreshed["ore_source_policies"]
                for key, previous in before_policies.items():
                    assert key in after_policies, "An existing source policy disappeared"
                    old_choices = {name: value for name, value in previous.items() if name != "candidates"}
                    new_choices = {name: value for name, value in after_policies[key].items() if name != "candidates"}
                    assert old_choices == new_choices, "Enabling diamonds changed an existing source choice"
                added = set(after_policies) - set(before_policies)
                assert added <= {"realisticdeposits:diamond_gravel|minecraft:overworld"}
                profile_hash = digest(profile)
            else:
                assert digest(profile) == profile_hash, "Saved profile changed"
            if phase == "later":
                later = current["later"]
                if case == "source-enable":
                    assert later["shallow_ore"] > 0 and later["rich_gravel"] > 0, "The explicit world override did not enable diamonds"
            elif phase == "stable":
                same(later, current["later"])
        results.append({"case": case, "passed": True, "sample": sample, "profile_sha256": profile_hash})
        if case == "reverse":
            forward = next((item for item in results if item["case"] == "standalone"), None)
            if forward:
                same(forward["sample"], sample)
        write_json(args.output / "results.json", {"passed": True, "cases": results})


if __name__ == "__main__":
    main()
