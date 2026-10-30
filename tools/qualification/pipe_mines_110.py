"""Check diamond-pipe workings, saved plans and safe opt-outs on disposable servers."""

import argparse
import json
import shutil
from pathlib import Path

from coal_110 import prepare
from ci_runtime import FORGE_HASH
from mines_110 import run
from qualify_110 import FORGE_JAR, checked_orespawn_sha256, digest, write_json


ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "orespawn", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--mineralogy", type=Path)
    parser.add_argument("--snapshot-first", action="store_true", help="Keep a first-load world copy for terrain diagnostics")
    parser.add_argument("--cases", nargs="+", default=("flat", "reverse", "off", "chance-zero", "source-off", "structure", "wet", "normal", "blocked"),
                        choices=("flat", "reverse", "off", "chance-zero", "source-off", "structure", "wet", "normal", "normal-structures", "blocked", "mineralogy", "mineralogy-flat", "regular"))
    args = parser.parse_args()
    assert not args.output.exists(), "Use a fresh evidence directory"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    checked_orespawn_sha256(args.orespawn)
    assert (args.runtime / "eula.txt").read_text().strip() == "eula=true"
    args.output.mkdir(parents=True)
    sealed = args.output / "inputs"
    sealed.mkdir()

    for name in ("mod", "orespawn", "probe", "mineralogy"):
        source = getattr(args, name)
        if source:
            target = sealed / source.name
            shutil.copy2(source, target)
            setattr(args, name, target)
    write_json(args.output / "artifacts.json", {p.name: digest(p) for p in sealed.iterdir()})

    results = []
    for index, case in enumerate(args.cases):
        args.port = 48920 + index
        natural = case in ("normal", "normal-structures", "mineralogy", "regular")
        root = prepare(args, args.output / case, mineralogy=case.startswith("mineralogy"), normal=natural)
        properties = (root / "server.properties").read_text().replace("coal-test-world", "mine-test-world")
        if case in ("normal-structures", "regular"):
            properties = properties.replace("generate-structures=false", "generate-structures=true")
        if case == "wet":
            properties = properties.replace("127*minecraft:stone,minecraft:grass;1;decoration",
                                            "127*minecraft:stone,20*minecraft:water;1;")
        elif not natural:
            properties = properties.replace(";1;decoration", ";1;")
        (root / "server.properties").write_text(properties, encoding="utf-8", newline="\n")

        for name in ("vanilla", "coal", "diamonds"):
            location = ROOT / (f"run/config/realisticdeposits/deposits/{name}.json" if case == "regular"
                               else f"src/main/resources/assets/realisticdeposits/defaults/{name}.json")
            catalogue = json.loads(location.read_text())
            if not natural:
                for definition in catalogue["deposits"]:
                    definition["enabled"] = definition["shape"]["type"] == "diamond_pipe" and case != "source-off"
                    if definition["shape"]["type"] != "diamond_pipe":
                        continue
                    definition["alignment"] = "massive_rare"
                    definition.pop("geomes")
                    definition["shape"]["upper_diameter"] = [96, 96]
                    if case == "off":
                        definition["abandoned_mine"]["enabled"] = False
                    if case == "chance-zero":
                        definition["abandoned_mine"]["chance"] = 0.0
            write_json(root / f"config/realisticdeposits/deposits/{name}.json", catalogue)

        saved_profile = root / "mine-test-world/serverconfig/orespawn-worldgen.json"
        for phase in ("first", "reload", "later", "stable"):
            run(args.java, root, phase, {"rd.mines.pipe": "true", "rd.mines.scenario": case,
                                       "rd.mines.startRegion": "96" if case.startswith("normal") or case in ("mineralogy", "regular") else "32",
                                       "rd.mines.wet": str(case == "wet").lower()})
            if phase == "first":
                profile_hash = digest(saved_profile)
                if args.snapshot_first:
                    shutil.copytree(root / "mine-test-world", root / "first-world-snapshot")
            else:
                assert digest(saved_profile) == profile_hash, "Workings changed the captured OreSpawn settings"

        receipt = dict(line.split("=", 1) for line in (root / "pipe-target.properties").read_text().splitlines()
                       if "=" in line and not line.startswith("#"))
        if case == "reverse":
            forward = next((item for item in results if item["case"] == "flat"), None)
            if forward:
                assert receipt["plan"] == forward["plan"], "Chunk order changed the pipe workings layout"
                assert receipt["completed_terrain"] == forward["terrain"], "Chunk order changed the completed workings"
        results.append({"case": case, "passed": True, "profile_sha256": profile_hash,
                        "plan": receipt["plan"], "terrain": receipt["completed_terrain"]})
        write_json(args.output / "results.json", {"passed": True, "boots": len(results) * 4, "cases": results})


if __name__ == "__main__":
    main()
