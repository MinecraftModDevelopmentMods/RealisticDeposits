"""Report lapis-piece rewards and uncertainty without imposing a vanilla target."""

import argparse
import json
import random
import statistics
from pathlib import Path

from gold_survey_110 import SEEDS
from qualify_110 import ORESPAWN_SHA256, digest, write_json


def checked_inputs(records):
    mods = set()
    probes = set()
    for record in records:
        candidates = [value for name, value in record.items()
                      if name.startswith("RealisticDeposits-") and name.endswith(".jar")]
        engines = [value for name, value in record.items() if name.startswith("OreSpawn-")]
        assert len(candidates) == 1 and engines == [ORESPAWN_SHA256], "Use one mod and the exact public OreSpawn jar"
        mods.update(candidates)
        probes.add(record["lapis-probe.jar"])
    assert len(mods) == len(probes) == 1, "Survey and control must use the same immutable mod and probe"
    return mods.pop()


def reward_row(lenses, control):
    assert lenses["seed"] == control["seed"]
    primary = lenses["lens_ore"] / lenses["represented_chunks"]
    vanilla = control["lapis_ore"] / control["chunks"]
    background = vanilla * 0.15
    distances = sorted(lenses["prospecting_distances"])
    return {
        "seed": lenses["seed"], "set": lenses["set"], "lens_ore_per_chunk": primary,
        "background_ore_per_chunk": background, "vanilla_ore_per_chunk": vanilla,
        "pieces_per_chunk": 6 * (primary + background), "vanilla_pieces_per_chunk": 6 * vanilla,
        "productive_districts": lenses["productive_districts"],
        "reserves_ore": [row["ore"] for row in lenses["district_reserves"]],
        "prospecting_distance_blocks": {
            "median": statistics.median(distances), "p90": distances[int(0.9 * (len(distances) - 1))],
            "maximum": distances[-1], "samples": len(distances),
        } if distances else None,
    }


def interval(rows):
    source = random.Random(512907)
    ratios = []
    for _ in range(10000):
        sample = [source.choice(rows) for _ in rows]
        ratios.append(sum(row["pieces_per_chunk"] for row in sample)
                      / sum(row["vanilla_pieces_per_chunk"] for row in sample))
    ratios.sort()
    return [ratios[249], ratios[9749]]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("lenses", "controls", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    assert not args.output.exists(), "Keep earlier balance reports"
    inputs = (args.lenses, args.controls)
    candidate = checked_inputs([json.loads((path.parent / "artifacts.json").read_text()) for path in inputs])
    lenses, controls = [{row["seed"]: row for row in json.loads(path.read_text())} for path in inputs]
    assert set(lenses) == set(controls) == set(SEEDS), "Use all four survey and two holdout seeds"
    rows = [reward_row(lenses[seed], controls[seed]) for seed in SEEDS]
    report = {
        "passed": True, "candidate_sha256": candidate, "seeds": rows, "groups": {},
        "inputs": {str(path): digest(path) for path in inputs}, "limitations": [
            "Lenses are estimates against native terrain and caves before other-ore, structure and workings collisions.",
            "Background is the verified 15% policy applied to matched decorated vanilla controls.",
            "Survey and holdouts describe this limited standalone sample, not every seed or Mineralogy strata.",
            "Prospecting distances are horizontal distances to surveyed district centres, not walking routes or entrances.",
            "The search is bounded; a nearer district outside the survey can shorten those distances.",
            "Native 1.10 lapis drops four to eight dye pieces, mean six; Fortune multiplies that reward.",
            "No vanilla-equivalent reward threshold was used to tune or accept the defaults.",
        ],
    }

    for name in ("calibration", "holdout", "all"):
        selected = [row for row in rows if name == "all" or row["set"] == name]
        pieces = statistics.mean(row["pieces_per_chunk"] for row in selected)
        vanilla = statistics.mean(row["vanilla_pieces_per_chunk"] for row in selected)
        reserves = [reserve for row in selected for reserve in row["reserves_ore"]]
        report["groups"][name] = {
            "seeds": len(selected), "expected_pieces_per_chunk": pieces,
            "vanilla_pieces_per_chunk": vanilla, "ratio": pieces / vanilla,
            "seed_bootstrap_95_percent_ratio": interval(selected),
            "mean_district_ore": statistics.mean(reserves) if reserves else None,
            "district_ore_range": [min(reserves), max(reserves)] if reserves else None,
            "source_share": {
                key: statistics.mean(row[key + "_ore_per_chunk"] for row in selected) * 6 / pieces
                for key in ("lens", "background")
            },
            "fortune_expected_pieces_per_chunk": {
                str(level): pieces * multiplier for level, multiplier in enumerate((1, 4 / 3, 7 / 4, 11 / 5))
            },
        }

    write_json(args.output, report)
    print(json.dumps(report["groups"], indent=2))


if __name__ == "__main__":
    main()
