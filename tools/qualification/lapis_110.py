"""Check lapis lenses on disposable, checksum-bound Forge servers."""

import argparse
import copy
import json
import shutil
import subprocess
from pathlib import Path

from coal_110 import prepare
from ci_runtime import FORGE_HASH, check_runtime_log
from qualify_110 import FORGE_JAR, checked_orespawn_sha256, check_ports, digest, write_json


ROOT = Path(__file__).resolve().parents[2]
RULE_ID = "realisticdeposits:deposit/realisticdeposits/overworld_lapis_lenses"


def run(args, root, phase, case):
    command = [str(args.java), "-Xms256m", "-Xmx2G", "-XX:ActiveProcessorCount=2",
               "-Drd.lapis.enabled=true", f"-Drd.lapis.phase={phase}",
               f"-Drd.lapis.reverse={str(case == 'reverse').lower()}",
               "-jar", FORGE_JAR, "nogui"]
    with (root / f"{phase}.log").open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=root, stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            code = process.wait(timeout=420)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise AssertionError(f"Timed out: {root}/{phase}; keep the evidence")

    log = (root / f"{phase}.log").read_text(encoding="utf-8", errors="replace")
    assert code == 0 and f"RD_LAPIS_PASS {phase}" in log, f"Failed: {root}/{phase}"
    check_runtime_log(log)
    assert not list((root / "crash-reports").glob("*")), "A crash report was produced"
    full_log = root / "logs/fml-server-latest.log"
    if full_log.is_file():
        check_runtime_log(full_log.read_text(encoding="utf-8", errors="replace"))
        shutil.copy2(full_log, root / f"{phase}.fml.log")

    print(f"PASS {case}/{phase}", flush=True)
    return json.loads((root / f"lapis-{phase}.json").read_text())


def same(left, right):
    for key in ("terrain_sha256", "lapis_sha256", "lens_ore", "outside_ore"):
        assert left[key] == right[key], f"Saved sample changed: {key}"


def configure(root, case):
    natural = case in ("normal", "mineralogy", "inactive", "legacy")

    for name in ("vanilla", "coal", "diamonds", "gold", "lapis"):
        catalogue = json.loads((ROOT / f"src/main/resources/assets/realisticdeposits/defaults/{name}.json").read_text())
        for definition in catalogue["deposits"]:
            definition["enabled"] = name == "lapis" and case not in ("off", "world-enable")
            if name != "lapis":
                continue

            definition["abandoned_mine"]["enabled"] = False
            if not natural:
                definition.update(alignment="massive_rare", min_y=80, max_y=120)
                definition.pop("geomes")

        write_json(root / f"config/realisticdeposits/deposits/{name}.json", catalogue)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "orespawn", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--mineralogy", type=Path)
    parser.add_argument("--geology-profile", type=Path, help="Read-only active-strata reference for inactive/legacy cases")
    parser.add_argument("--base-port", type=int, default=27500)
    parser.add_argument("--cases", nargs="+", default=("standalone", "normal", "reverse", "off", "world-off", "world-enable"),
                        choices=("standalone", "normal", "reverse", "off", "world-off", "world-enable",
                                 "mineralogy", "inactive", "legacy"))
    args = parser.parse_args()
    check_ports(range(args.base_port, args.base_port + len(args.cases)))
    assert not args.output.exists(), "Use a new evidence directory"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    checked_orespawn_sha256(args.orespawn)
    assert "eula=true" in (args.runtime / "eula.txt").read_text()
    args.output.mkdir(parents=True)
    inputs = args.output / "inputs"
    inputs.mkdir()

    for name in ("mod", "orespawn", "probe", "mineralogy", "geology_profile"):
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
        properties = (root / "server.properties").read_text().replace("coal-test-world", "lapis-test-world")
        if not natural:
            properties = properties.replace(";1;decoration", ";7;")
        (root / "server.properties").write_text(properties, encoding="utf-8", newline="\n")
        configure(root, case)

        if case in ("inactive", "legacy"):
            assert args.geology_profile, "Supply a sealed active-strata reference"
            profile = json.loads(args.geology_profile.read_text())
            profile["ores"] = {key: value for key, value in profile["ores"].items()
                               if not key.startswith("realisticdeposits:")}
            for directory in ("providers", "ore_providers"):
                profile.get(directory, {}).pop("realisticdeposits", None)
            profile.pop("ore_sources", None)
            profile.pop("ore_source_policies", None)
            if case == "inactive":
                profile["terrain_dimensions"]["minecraft:overworld"]["enabled"] = False
            else:
                profile["geology_mode"] = "legacy"
            write_json(root / "config/orespawn-worldgen.json", profile)

        first = run(args, root, "first", case)
        if case in ("off", "world-enable"):
            assert first["first"]["lens_ore"] == 0, "A disabled rule generated lapis lenses"
        else:
            assert first["first"]["lens_ore"] > 0, "No lapis lens generated"

        saved = root / "lapis-test-world/serverconfig/orespawn-worldgen.json"
        previous = None
        if case in ("world-off", "world-enable"):
            previous = json.loads(saved.read_text())
            previous["ores"][RULE_ID]["enabled"] = case == "world-enable"
            write_json(saved, previous)

        saved_hash = digest(saved)
        later = None
        for phase in ("reload", "later", "stable"):
            report = run(args, root, phase, case)
            same(first["first"], report["first"])
            if previous is not None and phase == "reload":
                refreshed = json.loads(saved.read_text())
                for key in set(previous) | set(refreshed):
                    if key not in ("ore_sources", "ore_source_policies"):
                        assert previous.get(key) == refreshed.get(key), f"World setting changed: {key}"
                saved_hash = digest(saved)
            else:
                assert digest(saved) == saved_hash, "Saved profile changed"

            if phase == "later":
                later = report["later"]
                if case in ("off", "world-off"):
                    assert later["lens_ore"] == 0, "A disabled saved source still generated lenses"
                else:
                    assert later["lens_ore"] > 0, "Later lapis lenses did not generate"
            elif phase == "stable":
                same(later, report["later"])

        result = {"case": case, "passed": True, "sample": first["first"], "profile_sha256": saved_hash}
        if case == "reverse":
            forward = next((item for item in results if item["case"] == "standalone"), None)
            if forward:
                same(forward["sample"], result["sample"])
        results.append(result)
        write_json(args.output / "results.json", {"passed": True, "cases": results, "boots": len(results) * 4})


if __name__ == "__main__":
    main()
