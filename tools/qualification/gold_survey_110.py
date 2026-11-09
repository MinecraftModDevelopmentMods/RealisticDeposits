"""Measure gold geometry and native river sediments without loading survey chunks."""

import argparse
import json
import shutil
import subprocess
from pathlib import Path

from coal_110 import prepare
from qualify_110 import FORGE_JAR, checked_orespawn_sha256, digest, write_json
from ci_runtime import FORGE_HASH


SEEDS = (3346071746553592696, 741209317, -893146051, 608145372901, -2719046037, 49277810623)
ROOT = Path(__file__).resolve().parents[2]


def load_catalogue(name):
    return json.loads((ROOT / f"src/main/resources/assets/realisticdeposits/defaults/{name}.json")
                      .read_text(encoding="utf-8"))


def control_catalogues():
    catalogues = {name: load_catalogue(name) for name in ("vanilla", "coal", "diamonds", "gold", "lapis", "emerald")}
    for catalogue in catalogues.values():
        for definition in catalogue["deposits"]:
            definition["enabled"] = False
    return catalogues


def checked_indices(indices):
    if not indices or len(set(indices)) != len(indices) or any(index < 0 or index >= len(SEEDS) for index in indices):
        raise ValueError("Select distinct indices from the six fixed survey seeds")
    return indices


def run_survey(command, root, stage):
    with (root / f"{stage}.log").open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=root, stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            code = process.wait(timeout=900)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise AssertionError(f"Survey timed out; keep {root}")
    log = (root / f"{stage}.log").read_text(encoding="utf-8", errors="replace")
    assert code == 0 and "RD_GOLD_SURVEY_PASS" in log, f"Survey failed: {root}/{stage}"
    errors = [line for line in log.splitlines() if ("/ERROR]" in line or "/FATAL]" in line)
              and "FML appears to be missing any signature data" not in line]
    assert not errors, f"Survey logged an error: {errors}"
    assert not list((root / "crash-reports").glob("*")), f"Survey crash: {root}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "orespawn", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--regions", type=int, default=16)
    parser.add_argument("--local-orespawn-sha256", help="Exact checksum of the deliberately selected local test candidate")
    parser.add_argument("--chunks", type=int, default=4096)
    parser.add_argument("--seed-index", type=int, nargs="+", default=list(range(len(SEEDS))))
    parser.add_argument("--control", action="store_true")
    parser.add_argument("--native-sediment", action="store_true")
    parser.add_argument("--structures", action="store_true", help="Use ordinary structure generation for manual-site checks")
    parser.add_argument("--reload", action="store_true", help="Repeat the same measurement after a clean save and restart")
    args = parser.parse_args()
    checked_indices(args.seed_index)
    assert not (args.control and args.native_sediment), "Choose controls or river measurements, not both"
    assert args.regions > 0 and args.chunks > 0
    assert not args.output.exists(), "Keep earlier evidence; choose a new output directory"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    checked_orespawn_sha256(args.orespawn, args.local_orespawn_sha256)
    args.output.mkdir(parents=True)
    inputs = args.output / "inputs"
    inputs.mkdir()
    for name in ("mod", "orespawn", "probe"):
        path = getattr(args, name)
        target = inputs / path.name
        shutil.copy2(path, target)
        setattr(args, name, target)
    write_json(args.output / "artifacts.json", {p.name: digest(p) for p in inputs.iterdir()})
    results = []
    for index in args.seed_index:
        seed = SEEDS[index]
        args.port = 48900 + index + (32 if args.control else 64 if args.native_sediment else 0)
        root = prepare(args, args.output / str(index), normal=True)
        properties = (root / "server.properties").read_text().replace("coal-test-world", "gold-test-world")
        properties = properties.replace("3346071746553592696", str(seed))
        if args.structures:
            properties = properties.replace("generate-structures=false", "generate-structures=true")
        (root / "server.properties").write_text(properties, encoding="utf-8", newline="\n")
        if args.control:
            for name, catalogue in control_catalogues().items():
                write_json(root / f"config/realisticdeposits/deposits/{name}.json", catalogue)
        command = [str(args.java), "-Xms256m", "-Xmx3G", "-XX:ActiveProcessorCount=2",
                   "-Drd.gold.survey=true", f"-Drd.gold.survey.regions={args.regions}",
                   f"-Drd.gold.survey.control={str(args.control).lower()}",
                   f"-Drd.gold.survey.native_sediment={str(args.native_sediment).lower()}",
                   f"-Drd.gold.survey.chunks={args.chunks}", "-jar", FORGE_JAR, "nogui"]
        run_survey(command, root, "survey")
        report = json.loads((root / "gold-survey.json").read_text())
        if args.reload:
            shutil.copy2(root / "gold-survey.json", root / "gold-survey-first.json")
            profile = root / "gold-test-world/serverconfig/orespawn-worldgen.json"
            captured = digest(profile)
            run_survey(command, root, "survey-reload")
            assert json.loads((root / "gold-survey.json").read_text()) == report, "Reload changed the measured deposits"
            assert digest(profile) == captured, "Reload changed the saved world profile"
            report["reload_unchanged"] = True
        report["structures"] = args.structures
        report["set"] = "calibration" if index < 4 else "holdout"
        if args.control:
            report["ingots_per_chunk"] = report["gold_ore"] / report["chunks"]
        else:
            report["lode_ingots_per_chunk"] = report["lode_ore"] / report["represented_chunks"]
            report["placer_ingots_per_chunk"] = report["rich_gravel"] / (36 * report["placer_sample_chunks"])
            report["maximum_placer_ingots_per_chunk"] = report["eligible_sediment"] / (36 * report["placer_sample_chunks"])
            report["actual_placer_ingots_per_chunk"] = report["actual_rich_gravel"] / (36 * report["placer_sample_chunks"])
        results.append(report)
        write_json(args.output / "results.json", results)
        print(f"PASS seed {index} ({report['set']}): "
              f"{report.get('lode_ore', report.get('gold_ore', 0))} ore, "
              f"{report.get('actual_rich_gravel', 0)} decorated river gravel", flush=True)


if __name__ == "__main__":
    main()
