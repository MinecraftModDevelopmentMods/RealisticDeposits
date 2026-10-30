"""Measure diamond geometry and native river sediments without loading survey chunks."""

import argparse
import json
import shutil
import subprocess
from pathlib import Path

from coal_110 import prepare
from qualify_110 import FORGE_JAR, checked_orespawn_sha256, digest, write_json
from ci_runtime import FORGE_HASH


SEEDS = (3346071746553592696, 741209317, -893146051, 608145372901, -2719046037, 49277810623)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "orespawn", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--regions", type=int, default=16)
    parser.add_argument("--chunks", type=int, default=4096)
    parser.add_argument("--seed-index", type=int, nargs="+", default=list(range(len(SEEDS))))
    parser.add_argument("--control", action="store_true")
    parser.add_argument("--native-sediment", action="store_true")
    args = parser.parse_args()
    assert not args.output.exists(), "Keep earlier evidence; choose a new output directory"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    checked_orespawn_sha256(args.orespawn)
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
        properties = (root / "server.properties").read_text().replace("coal-test-world", "diamond-test-world")
        properties = properties.replace("3346071746553592696", str(seed))
        (root / "server.properties").write_text(properties, encoding="utf-8", newline="\n")
        if args.control:
            catalogue = json.loads((Path(__file__).resolve().parents[2] /
                                    "src/main/resources/assets/realisticdeposits/defaults/diamonds.json").read_text())
            for definition in catalogue["deposits"]:
                definition["enabled"] = False
            write_json(root / "config/realisticdeposits/deposits/diamonds.json", catalogue)
        command = [str(args.java), "-Xms256m", "-Xmx3G", "-XX:ActiveProcessorCount=2",
                   "-Drd.diamond.survey=true", f"-Drd.diamond.survey.regions={args.regions}",
                   f"-Drd.diamond.survey.control={str(args.control).lower()}",
                   f"-Drd.diamond.survey.native_sediment={str(args.native_sediment).lower()}",
                   f"-Drd.diamond.survey.chunks={args.chunks}", "-jar", FORGE_JAR, "nogui"]
        with (root / "survey.log").open("w", encoding="utf-8") as output:
            process = subprocess.Popen(command, cwd=root, stdout=output, stderr=subprocess.STDOUT,
                                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            try:
                code = process.wait(timeout=900)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
                raise AssertionError(f"Survey timed out; keep {root}")
        log = (root / "survey.log").read_text(encoding="utf-8", errors="replace")
        assert code == 0 and "RD_DIAMOND_SURVEY_PASS" in log, f"Survey failed: {root}"
        assert not list((root / "crash-reports").glob("*")), f"Survey crash: {root}"
        report = json.loads((root / "diamond-survey.json").read_text())
        report["set"] = "calibration" if index < 4 else "holdout"
        if args.control:
            report["diamonds_per_chunk"] = report["diamond_ore"] / report["chunks"]
        else:
            report["pipe_diamonds_per_chunk"] = report["pipe_ore"] / report["represented_chunks"]
            report["placer_diamonds_per_chunk"] = report["rich_gravel"] / (16 * report["placer_sample_chunks"])
            report["maximum_placer_diamonds_per_chunk"] = report["eligible_sediment"] / (16 * report["placer_sample_chunks"])
            report["actual_placer_diamonds_per_chunk"] = report["actual_rich_gravel"] / (16 * report["placer_sample_chunks"])
        results.append(report)
        write_json(args.output / "results.json", results)
        print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
