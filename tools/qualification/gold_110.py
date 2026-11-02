"""Check gold lodes and river placers in disposable packaged Forge worlds."""

import argparse
import copy
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
               "-Drd.gold.enabled=true", f"-Drd.gold.phase={phase}",
               f"-Drd.gold.fixture_river={str(case not in ('normal', 'mineralogy', 'inactive', 'legacy')).lower()}",
               f"-Drd.gold.reverse={str(case == 'reverse').lower()}",
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
    assert code == 0 and f"RD_GOLD_PASS {phase}" in log, f"Failed: {root}/{phase}"
    unexpected = [line for line in log.splitlines() if ("/ERROR]" in line or "/FATAL]" in line)
                  and "FML appears to be missing any signature data" not in line]
    unexpected.extend(line for line in log.splitlines() if "Tried to assign a mutable BlockPos" in line)
    assert not unexpected and not list((root / "crash-reports").glob("*")), unexpected
    if (root / "logs/fml-server-latest.log").is_file():
        shutil.copy2(root / "logs/fml-server-latest.log", root / f"{phase}.fml.log")
    print(f"PASS {case}/{phase}", flush=True)
    return json.loads((root / f"gold-{phase}.json").read_text())


def same(left, right):
    for key in ("terrain_sha256", "gold_sha256", "lode_ore", "rich_gravel", "background_ore"):
        assert left[key] == right[key], f"Saved sample changed: {key}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "orespawn", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--mineralogy", type=Path)
    parser.add_argument("--local-orespawn-sha256", help="Exact checksum of the deliberately selected local test candidate")
    parser.add_argument("--geology-profile", type=Path, help="Read-only active-strata fixture for inactive and legacy cases")
    parser.add_argument("--base-port", type=int, default=48800)
    parser.add_argument("--cases", nargs="+", default=("standalone", "normal", "source-off", "placer-off", "reverse"),
                        choices=("standalone", "normal", "source-off", "source-enable", "source-world-off",
                                 "source-dimension-off", "placer-off", "reverse", "mineralogy", "inactive", "legacy"))
    args = parser.parse_args()
    assert not args.output.exists(), "Use a new evidence directory"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    checked_orespawn_sha256(args.orespawn, args.local_orespawn_sha256)
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
        properties = (root / "server.properties").read_text().replace("coal-test-world", "gold-test-world")
        if not natural:
            # Fixed hosts make the chunk-order comparison independent of vanilla's
            # cross-chunk lakes and decoration. Normal cases retain that decoration.
            properties = properties.replace(";1;decoration", ";7;")
        (root / "server.properties").write_text(properties, encoding="utf-8", newline="\n")
        gold = json.loads((ROOT / "src/main/resources/assets/realisticdeposits/defaults/gold.json").read_text())
        if not natural:
            gold["deposits"][0]["alignment"] = "massive_rare"
            gold["deposits"][0].pop("geomes")
        if case in ("source-off", "source-enable"):
            gold["deposits"][0]["enabled"] = False
        if case == "placer-off":
            gold["deposits"][1]["enabled"] = False
        write_json(root / "config/realisticdeposits/deposits/gold.json", gold)
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
            assert sample["lode_ore"] > 0, "The gold lode did not generate"
        if case in ("standalone", "reverse"):
            assert sample["rich_gravel"] > 0, "The river placer did not generate"
        if case == "placer-off":
            assert sample["rich_gravel"] == 0
        profile = root / "gold-test-world/serverconfig/orespawn-worldgen.json"
        explicitly_enabled = None
        disabled_profile = None
        if case in ("source-world-off", "source-dimension-off"):
            captured = json.loads(profile.read_text())
            source_rule = captured["ores"]["realisticdeposits:deposit/realisticdeposits/overworld_gold_lodes"]
            if case == "source-world-off":
                source_rule["enabled"] = False
            else:
                source_rule["dimensions"]["minecraft:overworld"]["enabled"] = False
            write_json(profile, captured)
            disabled_profile = copy.deepcopy(captured)
            for candidate in disabled_profile["ore_source_policies"]["orespawn:gold|minecraft:overworld"]["candidates"]:
                if candidate["source_id"] == "realisticdeposits:deposit/realisticdeposits/overworld_gold_lodes":
                    candidate["placement_active"] = False
        if case == "source-enable":
            captured = json.loads(profile.read_text())
            for rule_id, rule in captured["ores"].items():
                if rule_id.startswith("realisticdeposits:") and "overworld_gold_" in rule_id:
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
                    assert old_choices == new_choices, "Enabling gold changed an existing source choice"
                added = set(after_policies) - set(before_policies)
                assert added <= {"realisticdeposits:gold_gravel|minecraft:overworld"}
                profile_hash = digest(profile)
            elif disabled_profile is not None and phase == "reload":
                assert json.loads(profile.read_text()) == disabled_profile, "Disabling the source changed an unrelated world setting"
                profile_hash = digest(profile)
            else:
                assert digest(profile) == profile_hash, "Saved profile changed"
            if phase == "later":
                later = current["later"]
                if case in ("source-world-off", "source-dimension-off"):
                    assert later["shallow_ore"] == 0 and later["rich_gravel"] == 0, "A disabled world source still fed placers"
                if case == "source-enable":
                    assert later["shallow_ore"] > 0 and later["rich_gravel"] > 0, "The explicit world override did not enable gold"
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
