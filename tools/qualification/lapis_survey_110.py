"""Survey compact lapis reserves and matched vanilla rewards across fixed seeds."""

import argparse
import json
import shutil
import subprocess
from pathlib import Path

from coal_110 import prepare
from ci_runtime import FORGE_HASH, check_runtime_log
from gold_survey_110 import SEEDS, checked_indices
from qualify_110 import FORGE_JAR, checked_orespawn_sha256, digest, write_json


ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "orespawn", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--regions", type=int, default=16)
    parser.add_argument("--seed-index", type=int, nargs="+", default=list(range(len(SEEDS))))
    parser.add_argument("--control", action="store_true")
    parser.add_argument("--reload", action="store_true")
    args = parser.parse_args()
    checked_indices(args.seed_index)
    assert args.regions >= 4 and not args.output.exists(), "Use a new evidence directory and at least four regions"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    checked_orespawn_sha256(args.orespawn)
    args.output.mkdir(parents=True)
    inputs = args.output / "inputs"
    inputs.mkdir()

    for name in ("mod", "orespawn", "probe"):
        source = getattr(args, name)
        destination = inputs / source.name
        shutil.copy2(source, destination)
        setattr(args, name, destination)

    write_json(args.output / "artifacts.json", {p.name: digest(p) for p in inputs.iterdir()})
    results = []

    for index in args.seed_index:
        args.port = 49500 + index + (20 if args.control else 0)
        root = prepare(args, args.output / str(index), normal=True)
        settings = (root / "server.properties").read_text().replace("coal-test-world", "lapis-test-world")
        settings = settings.replace("3346071746553592696", str(SEEDS[index]))
        (root / "server.properties").write_text(settings, encoding="utf-8", newline="\n")

        for name in ("vanilla", "coal", "diamonds", "gold", "lapis", "emerald"):
            catalogue = json.loads((ROOT / f"src/main/resources/assets/realisticdeposits/defaults/{name}.json").read_text())
            for definition in catalogue["deposits"]:
                definition["enabled"] = name == "lapis" and not args.control
                if "abandoned_mine" in definition:
                    definition["abandoned_mine"]["enabled"] = False
            write_json(root / f"config/realisticdeposits/deposits/{name}.json", catalogue)

        command = [str(args.java), "-Xms256m", "-Xmx3G", "-XX:ActiveProcessorCount=2",
                   "-Drd.lapis.survey=true", f"-Drd.lapis.survey.regions={args.regions}",
                   f"-Drd.lapis.survey.control={str(args.control).lower()}", "-jar", FORGE_JAR, "nogui"]
        report = None
        profile_hash = None

        for phase in (("survey", "reload") if args.reload else ("survey",)):
            with (root / f"{phase}.log").open("w", encoding="utf-8") as output:
                process = subprocess.Popen(command, cwd=root, stdout=output, stderr=subprocess.STDOUT,
                                           creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
                try:
                    code = process.wait(timeout=900)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
                    raise AssertionError(f"Survey timed out; keep {root}")

            text = (root / f"{phase}.log").read_text(encoding="utf-8", errors="replace")
            assert code == 0 and "RD_LAPIS_SURVEY_PASS" in text, f"Survey failed: {root}/{phase}"
            check_runtime_log(text)
            assert not list((root / "crash-reports").glob("*"))
            current = json.loads((root / "lapis-survey.json").read_text())
            profile = root / "lapis-test-world/serverconfig/orespawn-worldgen.json"
            if report is not None:
                assert report == current and digest(profile) == profile_hash, "Reload changed the survey or profile"
            else:
                report = current
                profile_hash = digest(profile)
                shutil.copy2(root / "lapis-survey.json", root / "lapis-survey-first.json")

        report["set"] = "calibration" if index < 4 else "holdout"
        report["reload_checked"] = args.reload
        results.append(report)
        write_json(args.output / "results.json", results)
        print(f"PASS seed {index} ({report['set']}): {report.get('lens_ore', report.get('lapis_ore'))} ore", flush=True)


if __name__ == "__main__":
    main()
