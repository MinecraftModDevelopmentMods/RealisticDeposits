"""Check exact packaged emerald generation and ordinary-ore controls locally."""

import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

from ci_runtime import FORGE_HASH, check_runtime_log, require_local_execution
from coal_110 import prepare
from qualify_110 import FORGE_JAR, check_ports, checked_orespawn_sha256, digest, write_json


CATALOGUES = ("vanilla", "coal", "diamonds", "gold", "lapis", "emerald")
ROOT = Path(__file__).resolve().parents[2]
RULE = "realisticdeposits:deposit/realisticdeposits/overworld_emerald_pockets"
CASES = (
    "native-off", "native-off-control", "native-on", "native-on-control", "reverse",
    "os1-on", "os1-on-control", "os3-off", "os3-off-control", "saved-off",
    "schist", "inactive", "no-schist", "legacy", "manual", "manual-defaults", "wet", "infested", "structure", "existing",
    "survey-b", "survey-c", "rich", "trace", "rich-mine", "rich-wet", "large-mine",
    "rarity-a", "rarity-b", "rarity-c",
)
STRATA = {"schist", "inactive", "no-schist"}
WORKINGS = {"manual", "manual-defaults", "wet", "infested", "structure", "existing", "rich-mine", "rich-wet", "large-mine"}
SEEDS = {"survey-b": "-481973256913825465", "survey-c": "7028280810328432262",
         "rarity-b": "-481973256913825465", "rarity-c": "7028280810328432262"}


def run(java, root, phase, case):
    background = case.startswith(("native-", "os1-", "os3-"))
    find = "large" if case == "large-mine" else "rich" if case.startswith("rich") else case if case == "trace" else ""
    command = [str(java), "-Xms256m", "-Xmx2G", "-XX:ActiveProcessorCount=2",
               f"-Drd.emerald.phase={phase}", f"-Drd.emerald.reverse={str(case == 'reverse').lower()}",
               f"-Drd.emerald.workings={case if case in WORKINGS else 'off'}",
               f"-Drd.emerald.find={find}",
               f"-Drd.emerald.background={str(background).lower()}", "-jar", FORGE_JAR, "nogui"]
    with (root / f"{phase}.log").open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=root, stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            code = process.wait(timeout=300)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise AssertionError(f"Timed out: {root}/{phase}; evidence retained")
    log = (root / f"{phase}.log").read_text(encoding="utf-8", errors="replace")
    assert code == 0 and f"RD_EMERALD_PASS {phase}" in log, f"{root.name}/{phase}: see console log"
    check_runtime_log(log)
    assert not list((root / "crash-reports").glob("*")), f"Crash report in {root}"
    full = root / "logs/fml-server-latest.log"
    if full.is_file():
        shutil.copy2(full, root / f"{phase}.fml.log")
        check_runtime_log(full.read_text(encoding="utf-8", errors="replace"))
    print(f"PASS {root.name}/{phase}", flush=True)
    return json.loads((root / f"emerald-{phase}.json").read_text())


def scenario(args, case, port):
    from copy import copy
    fixture = copy(args)
    fixture.port = port
    flat = case.startswith(("native-", "os1-", "os3-")) or case == "reverse"
    root = prepare(fixture, args.output / case, mineralogy=case in STRATA, normal=not flat)
    properties = (root / "server.properties").read_text().replace("coal-test-world", "emerald-test-world")
    if flat:
        properties = properties.replace("127*minecraft:stone,minecraft:grass;1;decoration",
                                        "143*minecraft:stone,minecraft:grass;3;decoration")
    if case.startswith("manual") or case in {"rich-mine", "rich-wet", "large-mine"}:
        properties = properties.replace("generate-structures=false", "generate-structures=true")
    if case in SEEDS:
        properties = properties.replace("3346071746553592696", SEEDS[case])
    (root / "server.properties").write_text(properties, encoding="utf-8", newline="\n")
    control = case.endswith("-control")
    managed = case.startswith(("native-on", "os1-on"))
    for name in CATALOGUES:
        if case == "manual-defaults":
            continue
        data = json.loads((ROOT / f"src/main/resources/assets/realisticdeposits/defaults/{name}.json").read_text())
        for definition in data["deposits"]:
            definition["enabled"] = name == "emerald" and not control and case != "existing"
            if name == "emerald" and case not in WORKINGS:
                definition["abandoned_mine"]["enabled"] = False
            if name == "emerald" and flat:
                # Separate the proof pockets from native/imported ore and
                # from vanilla's cross-chunk dirt and gravel decoration.
                definition.update(min_y=100, max_y=120)
        write_json(root / f"config/realisticdeposits/deposits/{name}.json", data)

    profile = json.loads((args.strata_profile if case in STRATA else args.base_profile).read_text())
    profile["manage_vanilla_ores"] = managed
    if flat:
        profile["place_fluid_deposits"] = False
    profile["ores"] = {key: value for key, value in profile["ores"].items()
                       if not key.startswith("realisticdeposits:")}
    for directory in ("providers", "ore_providers"):
        profile.get(directory, {}).pop("realisticdeposits", None)
    profile.pop("ore_sources", None)
    profile.pop("ore_source_policies", None)
    if case == "inactive":
        profile["terrain_dimensions"]["minecraft:overworld"]["enabled"] = False
    elif case == "no-schist":
        for rock in profile["rocks"].values():
            if rock.get("block") == "mineralogy:schist":
                rock.update(enabled=False, ore_replaceable=False)
    elif case == "legacy":
        profile["geology_mode"] = "legacy"
    if case != "manual-defaults":
        write_json(root / "config/orespawn-worldgen.json", profile)

    if case.startswith("rarity-"):
        return run(args.java, root, "rarity", case)

    imported = None
    if case.startswith(("os1-", "os3-")):
        (root / "config/orespawn.cfg").write_text(
            'general {\n B:"Replace Vanilla Oregen"=' + str(managed).lower()
            + '\n B:Retrogen=false\n B:"Force Retrogen"=false\n}\n', encoding="utf-8")
        if case.startswith("os1-"):
            imported = root / "config/orespawn/orespawn.json"
            write_json(imported, {"dimensions": [{"dimension": "0", "ores": [{
                "blockID": "minecraft:emerald_ore", "size": 5, "variation": 0,
                "frequency": 3.25, "minHeight": 12, "maxHeight": 19,
            }]}]})
        else:
            imported = root / "config/orespawn3/orespawn.json"
            write_json(imported, {"version": "2.0", "spawns": {"emerald_ore": {
                "enabled": True, "retrogen": False, "feature": "default", "replaces": "default",
                "dimensions": [0], "biomes": {"excludes": []},
                "parameters": {"frequency": 3.25, "size": 5, "variation": 0, "minHeight": 12, "maxHeight": 19},
                "blocks": [{"name": "minecraft:emerald_ore", "chance": 100}],
            }}})
    import_hash = digest(imported) if imported else None
    first = run(args.java, root, "first", case)
    assert first["silk_touch"] and first["fortune3_mean"] > 1.0
    assert first["profile"]["manage_vanilla_ores"] == managed
    assert "background_generation_scale" not in first["profile"]["ores"][RULE]["dimensions"]["minecraft:overworld"]
    if imported:
        assert digest(imported) == import_hash
        assert any(key.startswith("orespawn:legacy/") and rule.get("block") == "minecraft:emerald_ore"
                   for key, rule in first["profile"]["ores"].items()), "Legacy emerald source was not imported"
    if case in {"no-schist", "legacy"}:
        assert first["targets"]["accepted"] == 0
    elif not control and case != "existing":
        assert first["first"]["ore"], f"{case}: no emeralds at the selected pocket"
    if case in STRATA:
        assert first["physical"] == (case != "inactive")
        assert first["eligible_states"] == {"schist": 1, "inactive": 4, "no-schist": 0}[case]
    if case == "manual":
        assert first["first"].get("open_route"), f"Manual route not open: {first['first']}"
    if case == "rich-mine":
        assert first["first"]["walked_pockets"] >= 2
    if case == "large-mine":
        find = first["first"]
        assert find["large"] and 260 <= find["planned"] <= 420
        assert len(find["ore"]) >= 200 and 4 <= len(find["zone_ore"]) <= 5 and min(find["zone_ore"]) > 0
        assert len(find["trace"]) >= find["strike"] // 2 and find["walked_pockets"] >= 2
        first["reachable_prospecting_zones"] = followed_zones(find)
        assert len(first["reachable_prospecting_zones"]) >= 2, "No intact clue route out of the worked zone"
        assert 0 < first["targets"]["large_count"] < first["targets"]["medium_count"]
    if case == "rich-wet":
        assert first["first"]["rejected_workings"] == "Unsafe blocks in the approach"
        assert first["no_partial_entrance"] and first["obstruction_preserved"]
        assert re.search(r"Left abandoned workings sealed .*?(?:minecraft:(?:flowing_)?water|avoid opening a liquid)",
                         (root / "first.log").read_text(encoding="utf-8", errors="replace")), (
                             "The linked workings must be rejected by the injected water itself")
    if case == "rich":
        find = first["first"]
        assert find["rich"] and 70 <= find["planned"] <= 120 and len(find["ore"]) > 60
        assert 4 <= find["pockets"] <= 6 and 52 <= find["strike"] <= 80 and find["trace"]
    if case == "trace":
        find = first["first"]
        assert not find["rich"] and find["planned"] == 26 and len(find["ore"]) == 26
        assert find["pockets"] == 3 and find["strike"] == 38 and len(find["trace"]) >= 20
        assert not set(find["trace"]).intersection(find["ore"])
    if case == "manual-defaults" and not first["first"].get("open_route"):
        assert first["first"]["rejected_workings"] == "Unsafe blocks in the approach"
        assert first["no_partial_entrance"], "The natural obstruction left a partial entrance"
    if case in {"wet", "infested", "structure"}:
        assert not first["first"].get("open_route"), "Unsafe workings were opened"
        assert "entrance" not in first["first"], "An unsafe site kept a mine entrance"
        assert first["no_partial_entrance"], "An unsafe site carved stairs before rejecting the route"
        assert first["obstruction_preserved"], "The placed obstruction was modified"
        if case == "structure":
            assert first["first"]["rejected_workings"] == "Another structure"
        else:
            assert first["first"]["rejected_workings"] == "Unsafe blocks in the approach"
    if case == "existing":
        saved = root / "emerald-test-world/serverconfig/orespawn-worldgen.json"
        changed = json.loads(saved.read_text())
        changed["ores"][RULE]["enabled"] = True
        write_json(saved, changed)
        existing = run(args.java, root, "existing", case)
        assert existing["first"]["rejected_workings"] == "Existing terrain"
        assert existing["first"]["ore"] == first["first"]["ore"], "Existing terrain received emeralds"
        first["existing"] = existing["first"]
    if case in {"native-off", "saved-off", "schist", "manual", "rich-mine", "large-mine"}:
        saved = root / "emerald-test-world/serverconfig/orespawn-worldgen.json"
        if case == "saved-off":
            changed = json.loads(saved.read_text())
            changed["ores"][RULE]["enabled"] = False
            write_json(saved, changed)
        reload = run(args.java, root, "reload", case)
        assert reload["first"] == first["first"], "Existing pocket or mine changed across reload"
        profile_hash = digest(saved)
        later = run(args.java, root, "later", case)
        assert later["first"] == first["first"], "Opening later chunks changed the saved pocket"
        assert bool(later["later"]["ore"]) == (case != "saved-off"), "Saved source switch was ignored"
        assert digest(saved) == profile_hash, "Captured profile changed after the later chunks"
        first["later"] = later["later"]
    if case.startswith(("native-", "os1-", "os3-")):
        ordinary = {key: value for key, value in first["profile"]["ores"].items()
                    if not key.startswith("realisticdeposits:") and "emerald" in key}
        first["ordinary_rules"] = ordinary
        assert first["background_events"] > 0, f"No vanilla emerald callbacks in the hill fixture: {case}"
        assert first["background_denied"] == (first["background_events"] if managed else 0)
    first.pop("profile")
    return first


def followed_zones(find):
    """Walk the generated ore-and-diorite clues from the zone beside the landing."""
    def point(value):
        return tuple(map(int, value.split(",")))

    ore = list(map(point, find["ore"]))
    zones = {}
    offset = 0
    for zone, count in enumerate(find["zone_ore"], start=1):
        zones.update((cell, zone) for cell in ore[offset:offset + count])
        offset += count
    assert offset == len(ore)
    landing = point(find["landing"])
    start = zones[min(ore, key=lambda cell: sum(abs(a - b) for a, b in zip(cell, landing)))]
    remaining = set(ore).union(map(point, find["trace"]))
    queue = [cell for cell in ore if zones[cell] == start]
    remaining.difference_update(queue)
    reached = {start}
    for x, y, z in queue:
        if (x, y, z) in zones:
            reached.add(zones[x, y, z])
        for cell in ((x-1, y, z), (x+1, y, z), (x, y-1, z), (x, y+1, z), (x, y, z-1), (x, y, z+1)):
            if cell in remaining:
                remaining.remove(cell)
                queue.append(cell)
    return sorted(reached)


def compare_background(active, control):
    """Check supply rules and gates; record realised positions without asserting identity."""
    assert active["ordinary_rules"] == control["ordinary_rules"], "Ordinary emerald definitions changed"
    for key in ("background_events", "background_denied"):
        assert active[key] == control[key], f"Ordinary emerald gate changed: {key}"
    assert active["background"] and control["background"], "Empty ordinary emerald sample"
    assert len(active["first"]["ore"]) > len(control["first"]["ore"]), "No additional ore in the high pocket"
    left, right = set(active["background"]), set(control["background"])
    # The public 1.10 engine shares the decoration RNG with new ore rules.
    # A matched run checks ordinary availability, not identical coordinates.
    return {"active": len(left), "control": len(right), "matching_positions": len(left & right),
            "vanilla_events": active["background_events"], "denied": active["background_denied"]}


def main():
    require_local_execution(os.environ)
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "orespawn", "probe", "output", "base-profile"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--mineralogy", type=Path)
    parser.add_argument("--strata-profile", type=Path)
    parser.add_argument("--base-port", type=int, default=29200)
    parser.add_argument("--workers", type=int, choices=(1, 2), default=2)
    parser.add_argument("--cases", nargs="+", choices=CASES, default=CASES)
    args = parser.parse_args()
    assert len(set(args.cases)) == len(args.cases), "Choose each case once"
    if STRATA.intersection(args.cases):
        assert args.mineralogy and args.strata_profile, "Strata cases need sealed reference inputs"
    check_ports(range(args.base_port, args.base_port + len(args.cases)))
    assert not args.output.exists(), "Use a new evidence directory"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    checked_orespawn_sha256(args.orespawn)
    assert "eula=true" in (args.runtime / "eula.txt").read_text()
    inputs = args.output / "inputs"
    inputs.mkdir(parents=True)
    for name in ("mod", "orespawn", "probe", "mineralogy", "base_profile", "strata_profile"):
        source = getattr(args, name)
        if source:
            target = inputs / (name + "-" + source.name if name.endswith("profile") else source.name)
            shutil.copy2(source, target)
            setattr(args, name, target)
    write_json(args.output / "artifacts.json", {p.name: digest(p) for p in inputs.iterdir()})
    results = {}
    failures = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        cases = {pool.submit(scenario, args, case, args.base_port + i): case for i, case in enumerate(args.cases)}
        for future in concurrent.futures.as_completed(cases):
            case = cases[future]
            try:
                results[case] = future.result()
            except Exception as failure:
                failures[case] = str(failure)
                print(f"FAIL {case}: {failure}", flush=True)
            write_json(args.output / "results.json", {"passed": False, "cases": results, "failures": failures})
    comparisons = {}
    for key in ("native-off", "native-on", "os1-on", "os3-off"):
        if key in results and key + "-control" in results:
            comparisons[key] = compare_background(results[key], results[key + "-control"])
    if "reverse" in results and "native-off" in results:
        assert results["reverse"]["first"] == results["native-off"]["first"], "Chunk order changed the pocket"
    assert not failures, failures
    write_json(args.output / "results.json", {"passed": True, "cases": results,
                                               "ordinary_comparisons": comparisons})


if __name__ == "__main__":
    main()
