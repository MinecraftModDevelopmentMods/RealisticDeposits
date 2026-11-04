"""Compare old Gold worlds with and without the lapis update on disposable copies."""

import argparse
import json
import shutil
from pathlib import Path

from coal_110 import prepare
from frozen_mines_110 import check_saved_profile
from gold_110 import run, same
from qualify_110 import FORGE_JAR, checked_orespawn_sha256, digest, write_json, world_fingerprint
from ci_runtime import FORGE_HASH


ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "old-mod", "orespawn", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--old-mod-sha256", required=True)
    args = parser.parse_args()
    assert not args.output.exists(), "Use a new evidence directory"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    assert digest(args.old_mod) == args.old_mod_sha256.upper()
    checked_orespawn_sha256(args.orespawn)
    args.output.mkdir(parents=True)
    inputs = args.output / "inputs"
    inputs.mkdir()
    for name in ("mod", "old_mod", "orespawn", "probe"):
        source = getattr(args, name)
        target = inputs / source.name
        shutil.copy2(source, target)
        setattr(args, name, target)
    write_json(args.output / "artifacts.json", {p.name: digest(p) for p in inputs.iterdir()})

    current = args.mod
    args.mod = args.old_mod
    args.port = 28100
    baseline = prepare(args, args.output / "gold-first")
    properties = (baseline / "server.properties").read_text().replace("coal-test-world", "gold-test-world")
    properties = properties.replace(";1;decoration", ";7;")
    (baseline / "server.properties").write_text(properties, encoding="utf-8", newline="\n")
    for name in ("vanilla", "coal", "diamonds", "gold"):
        catalogue = json.loads((ROOT / f"src/main/resources/assets/realisticdeposits/defaults/{name}.json").read_text())
        for definition in catalogue["deposits"]:
            definition["enabled"] = definition["shape"]["type"] == "gold_lode"
            if "abandoned_mine" in definition:
                definition["abandoned_mine"]["enabled"] = False
            if definition["shape"]["type"] == "gold_lode":
                definition["alignment"] = "massive_rare"
                definition.pop("geomes")
        write_json(baseline / f"config/realisticdeposits/deposits/{name}.json", catalogue)

    before = run(args, baseline, "first", "old-gold")
    baseline_world = world_fingerprint(baseline / "gold-test-world")
    original_profile = before["profile"]
    previous_files = {path.name: digest(path) for path in
                      (baseline / "config/realisticdeposits/deposits").glob("*.json")}
    versions = []
    import zipfile
    with zipfile.ZipFile(current) as archive:
        version = next(row["version"] for row in json.loads(archive.read("mcmod.info"))
                       if row["modid"] == "realisticdeposits")

    for index, updated in enumerate((False, True)):
        root = args.output / ("upgraded" if updated else "old-control")
        shutil.copytree(baseline, root)
        settings = (root / "server.properties").read_text().replace("server-port=28100", f"server-port={28101 + index}")
        (root / "server.properties").write_text(settings, encoding="utf-8", newline="\n")
        if updated:
            old = root / "mods" / args.old_mod.name
            old.rename(root / "retained-old-mod.jar")
            shutil.copy2(current, root / "mods" / current.name)

        later = None
        for phase in ("reload", "later", "stable"):
            report = run(args, root, phase, root.name)
            same(before["first"], report["first"])
            assert not any(key.startswith("realisticdeposits:") and "lapis" in key
                           for key in report["profile"]["ores"]), "An old world silently acquired lapis rules"
            if updated:
                check_saved_profile(original_profile, report["profile"], version)
            else:
                assert report["profile"] == original_profile
            for name, expected in previous_files.items():
                assert digest(root / f"config/realisticdeposits/deposits/{name}") == expected, "An old catalogue changed"
            if phase == "later":
                later = report["later"]
            elif phase == "stable":
                same(later, report["later"])

        versions.append(later)

    same(versions[0], versions[1])
    assert world_fingerprint(baseline / "gold-test-world") == baseline_world, "The source fixture was modified"
    write_json(args.output / "results.json", {"passed": True, "boots": 7,
                                               "old_world_rules_unchanged": True,
                                               "reload_and_later_terrain_equal_to_old_control": True})


if __name__ == "__main__":
    main()
