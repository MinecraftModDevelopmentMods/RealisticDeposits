"""Report gold reserves and rewards without imposing a vanilla-equivalent budget."""

import argparse
import json
import random
import statistics
from pathlib import Path

from gold_survey_110 import SEEDS
from qualify_110 import ORESPAWN_SHA256, digest, write_json


def checked_candidate(artifacts):
    candidates = set()
    probes = set()
    for record in artifacts:
        mods = [value for name, value in record.items()
                if name.startswith("RealisticDeposits-") and name.endswith(".jar")]
        engines = [value for name, value in record.items() if name.startswith("OreSpawn-")]
        assert len(mods) == 1 and engines == [ORESPAWN_SHA256], "Use the exact public engine and one mod jar"
        candidates.add(mods[0])
        probes.add(record["gold-survey.jar"])
    assert len(candidates) == len(probes) == 1, "Every survey must use the same immutable mod and probe"
    return candidates.pop()


def reward_row(lode, river, control):
    assert river["native_sediment"], "Use fully decorated river samples, not just the terrain estimate"
    primary = lode["lode_ore"] / lode["represented_chunks"]
    placer = river["actual_rich_gravel"] / (36 * river["placer_sample_chunks"])
    vanilla = control["gold_ore"] / control["chunks"]
    background = vanilla * 0.15
    productive = lode["productive_districts"]
    distances = sorted(lode.get("prospecting_distances", []))
    return {
        "seed": lode["seed"], "set": lode["set"], "lodes": primary, "placers": placer,
        "background": background, "total_ingots": primary + placer + background,
        "vanilla_ingots": vanilla, "productive_districts": productive,
        "mean_reserve_per_district": lode["lode_ore"] / productive if productive else None,
        "characteristic_district_spacing_blocks": (lode["represented_chunks"] * 256 / productive) ** 0.5
        if productive else None,
        "horizontal_prospecting_distance_blocks": {
            "median": statistics.median(distances), "p90": distances[int(0.9 * (len(distances) - 1))],
            "maximum": distances[-1], "samples": len(distances),
        } if distances else None,
    }


def ratio_interval(rows):
    random_source = random.Random(830157)
    estimates = []
    for _ in range(10000):
        sample = [random_source.choice(rows) for _ in rows]
        estimates.append(sum(row["total_ingots"] for row in sample) / sum(row["vanilla_ingots"] for row in sample))
    estimates.sort()
    return [estimates[249], estimates[9749]]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("lodes", "controls", "sediment", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    assert not args.output.exists(), "Keep earlier reports and choose another output"
    inputs = [args.lodes, args.controls, args.sediment]
    lodes, controls, sediment = [{row["seed"]: row for row in json.loads(path.read_text())} for path in inputs]
    assert set(lodes) == set(controls) == set(sediment) == set(SEEDS)

    artifacts = [json.loads((path.parent / "artifacts.json").read_text()) for path in inputs]
    candidate = checked_candidate(artifacts)
    rows = [reward_row(lodes[seed], sediment[seed], controls[seed]) for seed in SEEDS]

    report = {"candidate_sha256": candidate, "seeds": rows, "groups": {},
              "inputs": {str(path): digest(path) for path in inputs}, "limitations": [
                  "Lode reserves are a natural-terrain estimate before other ore and structure collisions.",
                  "Background rewards apply the verified 15% policy to matched decorated controls.",
                  "Characteristic spacing is the square root of surveyed area per district, not a guaranteed walking distance.",
                  "Prospecting distances are horizontal distances to an estimated productive district, not a walking route or guarantee of finding its entrance.",
                  "Whole-seed intervals show rare-deposit uncertainty; no vanilla-equivalent reward target is imposed.",
                  "Gold ore smelts to one ingot in vanilla 1.10; Fortune affects placer nuggets, not those ore blocks.",
                  "Third-party ore processing is outside these vanilla-smelting reward estimates.",
              ]}
    for name in ("calibration", "holdout", "all"):
        selected = [row for row in rows if name == "all" or row["set"] == name]
        total = statistics.mean(row["total_ingots"] for row in selected)
        vanilla = statistics.mean(row["vanilla_ingots"] for row in selected)
        report["groups"][name] = {
            "seeds": len(selected), "expected_ingots_per_chunk": total,
            "vanilla_ingots_per_chunk": vanilla, "ratio": total / vanilla,
            "seed_bootstrap_95_percent_ratio": ratio_interval(selected),
            "source_share": {key: statistics.mean(row[key] for row in selected) / total
                             for key in ("lodes", "placers", "background")},
            "fortune_expected_ingots_per_chunk": {
                str(level): statistics.mean(row["lodes"] + row["background"] + row["placers"] * bonus for row in selected)
                for level, bonus in enumerate((1, 4 / 3, 7 / 4, 11 / 5))
            },
        }
    report["passed"] = True
    write_json(args.output, report)
    print(json.dumps(report["groups"], indent=2))


if __name__ == "__main__":
    main()
