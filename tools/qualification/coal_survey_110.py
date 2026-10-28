"""Compare a fixed, widely spaced coal sample with an otherwise matched vanilla-coal control."""

import argparse
import json
import shutil
from pathlib import Path

from coal_110 import ROOT, prepare, run
from qualify_110 import FORGE_JAR, checked_orespawn_sha256, digest, write_json
from ci_runtime import FORGE_HASH


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "orespawn", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--local-orespawn-sha256", required=True)
    args = parser.parse_args()
    args.mineralogy = None
    args.port = 48775
    assert not args.output.exists(), "Use a new evidence directory"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    checked_orespawn_sha256(args.orespawn, args.local_orespawn_sha256)
    args.output.mkdir(parents=True)
    inputs = args.output / "inputs"
    inputs.mkdir()
    for name in ("mod", "orespawn", "probe"):
        source = getattr(args, name)
        target = inputs / source.name
        shutil.copy2(source, target)
        setattr(args, name, target)
    write_json(args.output / "artifacts.json", {path.name: digest(path) for path in inputs.iterdir()})
    results = {}
    for enabled in (False, True):
        label = "coalfields" if enabled else "vanilla-control"
        root = prepare(args, args.output / label, normal=True)
        coal = json.loads((ROOT / "src/main/resources/assets/realisticdeposits/defaults/coal.json").read_text())
        coal["deposits"][0]["enabled"] = enabled
        write_json(root / "config/realisticdeposits/deposits/coal.json", coal)
        run(args.java, root, "survey", "survey")
        results[label] = json.loads((root / "coal-survey.json").read_text())
    for key in ("regions", "selected_before_geology", "accepted_fields", "estimated_field_reserves"):
        assert results["coalfields"][key] == results["vanilla-control"][key]
    assert results["coalfields"]["accepted_fields"] > 0
    assert results["vanilla-control"]["actual_sample_coal"] > 0
    write_json(args.output / "results.json", {"passed": True, "results": results})


if __name__ == "__main__":
    main()
