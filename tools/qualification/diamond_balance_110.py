"""Compare expected diamond rewards with matched controls and show seed uncertainty."""

import argparse
import json
import random
import statistics
from pathlib import Path

from diamond_survey_110 import SEEDS
from qualify_110 import digest, write_json


def interval(records):
    random_source = random.Random(7029361)
    estimates = []
    for _ in range(10000):
        sampled = [random_source.choice(records) for _ in records]
        estimates.append(sum(row["total"] for row in sampled) / sum(row["vanilla"] for row in sampled))
    estimates.sort()
    return [estimates[249], estimates[9749]]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pipes", required=True, type=Path)
    parser.add_argument("--controls", required=True, type=Path)
    parser.add_argument("--sediment", required=True, nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    assert not args.output.exists(), "Keep previous reports and choose another output"
    pipes = {row["seed"]: row for row in json.loads(args.pipes.read_text())}
    controls = {row["seed"]: row for row in json.loads(args.controls.read_text())}
    sediment = {}
    for source in args.sediment:
        for row in json.loads(source.read_text()):
            assert row["seed"] not in sediment, "A sediment seed was counted twice"
            assert row["native_sediment"], "Use fully decorated sediment samples"
            sediment[row["seed"]] = row
    assert set(pipes) == set(controls) == set(sediment) == set(SEEDS)
    rows = []
    for seed in SEEDS:
        pipe = pipes[seed]["pipe_ore"] / pipes[seed]["represented_chunks"]
        placer = sediment[seed]["actual_rich_gravel"] / (16 * sediment[seed]["placer_sample_chunks"])
        vanilla = controls[seed]["diamond_ore"] / controls[seed]["chunks"]
        background = vanilla * 0.15
        rows.append({"seed": seed, "set": pipes[seed]["set"], "pipes": pipe, "placers": placer,
                     "background": background, "total": pipe + placer + background, "vanilla": vanilla})
    sources = [args.pipes, args.controls, *args.sediment]
    artifact_records = [json.loads((source.parent / "artifacts.json").read_text()) for source in sources]
    candidates = {value for record in artifact_records for name, value in record.items()
                  if name.startswith("RealisticDeposits-") and name.endswith(".jar")}
    assert len(candidates) == 1, "All balance samples must use the same immutable candidate"
    report = {"candidate_sha256": candidates.pop(), "seeds": rows, "groups": {},
              "inputs": {str(source): digest(source) for source in sources}, "limitations": [
        "Pipes are an area-weighted native-terrain estimate before collisions with other ore placements and structures.",
        "Background rewards use the verified 15% policy applied to matched native controls.",
        "Whole-seed bootstrap intervals reflect rare-district clustering; no per-seed 20% promise is made.",
        "Density-only tuning leaves district plans and placer links unchanged; native sediment provenance is recorded separately.",
    ]}
    for group in ("calibration", "holdout", "all"):
        selected = [row for row in rows if group == "all" or row["set"] == group]
        total = statistics.mean(row["total"] for row in selected)
        vanilla = statistics.mean(row["vanilla"] for row in selected)
        ratio = total / vanilla
        assert 0.8 <= ratio <= 1.2, f"The {group} aggregate is outside the agreed reward budget: {ratio}"
        report["groups"][group] = {
            "seeds": len(selected), "expected_diamonds_per_chunk": total, "vanilla_diamonds_per_chunk": vanilla,
            "ratio": ratio, "seed_bootstrap_95_percent_ratio": interval(selected),
            "source_split": {key: statistics.mean(row[key] for row in selected) / total
                             for key in ("pipes", "placers", "background")},
            "fortune_expected_diamonds_per_chunk": {str(level): total * multiplier
                                                    for level, multiplier in enumerate((1, 4 / 3, 7 / 4, 11 / 5))},
        }
    report["passed"] = True
    write_json(args.output, report)
    print(json.dumps(report["groups"], indent=2))


if __name__ == "__main__":
    main()
