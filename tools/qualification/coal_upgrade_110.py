"""Compare an old iron-only save with disposable control and upgraded copies."""

import argparse
import json
import shutil
import subprocess
from pathlib import Path

from qualify_110 import FORGE_JAR, checked_orespawn_sha256, digest, world_fingerprint, write_json
from ci_runtime import FORGE_HASH


OLD_MINE_BUILD = "58BAA8A5A69B904ED18387C48D8E4CE63B1C411A6DF06C3056B720C6228604A5"


def run(java, root, phase):
    command = [str(java), "-Xms256m", "-Xmx2G", "-XX:ActiveProcessorCount=2",
               "-Drd.coal.upgrade=true", f"-Drd.coal.phase={phase}", "-jar", FORGE_JAR, "nogui"]
    with (root / f"{phase}.log").open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=root, stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            code = process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise AssertionError("Upgrade fixture timed out; retain its evidence")
    text = (root / f"{phase}.log").read_text(encoding="utf-8", errors="replace")
    assert code == 0 and f"RD_COAL_UPGRADE_PASS {phase}" in text, f"{root.name}/{phase} failed"
    assert not [line for line in text.splitlines() if ("/ERROR]" in line or "/FATAL]" in line)
                and "FML appears to be missing any signature data" not in line], text[-4000:]
    assert not list((root / "crash-reports").glob("*"))
    shutil.copy2(root / "logs/fml-server-latest.log", root / f"{phase}.fml.log")
    print(f"PASS {root.name}/{phase}", flush=True)
    return json.loads((root / f"coal-upgrade-{phase}.json").read_text(encoding="utf-8"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "old-mod", "orespawn", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--local-orespawn-sha256", required=True)
    parser.add_argument("--mineralogy", type=Path)
    parser.add_argument("--legacy-profile", type=Path,
                        help="Read-only geology reference for a separate legacy-coal-layer comparison")
    args = parser.parse_args()
    assert not args.output.exists(), "Keep old evidence and choose a new output"
    assert digest(args.old_mod) == OLD_MINE_BUILD
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    checked_orespawn_sha256(args.orespawn, args.local_orespawn_sha256)
    args.output.mkdir(parents=True)
    root = args.output / "source"
    root.mkdir()
    shutil.copytree(args.runtime / "libraries", root / "libraries")
    for name in (FORGE_JAR, "minecraft_server.1.10.2.jar", "eula.txt"):
        shutil.copy2(args.runtime / name, root / name)
    (root / "mods").mkdir()
    for source in (args.old_mod, args.orespawn, args.probe):
        shutil.copy2(source, root / "mods" / source.name)
    if args.mineralogy:
        shutil.copy2(args.mineralogy, root / "mods" / args.mineralogy.name)
    if args.legacy_profile:
        profile = json.loads(args.legacy_profile.read_text(encoding="utf-8"))
        profile["ores"] = {key: value for key, value in profile["ores"].items()
                           if not key.startswith("realisticdeposits:")}
        profile.pop("ore_sources", None)
        profile["geology_mode"] = "legacy"
        profile["cyano"].update(realistic_coal_layers=True,
                                 sedimentary_rocks=["mineralogy:shale", "minecraft:coal_ore",
                                                    "mineralogy:siltstone", "minecraft:sandstone"])
        write_json(root / "config/orespawn-worldgen.json", profile)
    (root / "server.properties").write_text(
        "level-name=coal-upgrade-world\nlevel-seed=3346071746553592696\nlevel-type=FLAT\n"
        "generator-settings=3;minecraft:bedrock,127*minecraft:stone,minecraft:grass;1;decoration\n"
        "server-ip=127.0.0.1\nserver-port=48773\nonline-mode=false\ngenerate-structures=false\n"
        "view-distance=3\nmax-tick-time=-1\nallow-nether=false\nspawn-animals=false\nspawn-monsters=false\n",
        encoding="utf-8", newline="\n")
    for name in ("ops.json", "whitelist.json", "banned-ips.json", "banned-players.json"):
        write_json(root / name, [])
    first = run(args.java, root, "first")
    source_hash = world_fingerprint(root / "coal-upgrade-world")
    profile_relative = "coal-upgrade-world/serverconfig/orespawn-worldgen.json"
    original_profile = digest(root / profile_relative)
    original_config = digest(root / "config/realisticdeposits/deposits/vanilla.json")
    completed = {}
    for name in ("control", "upgraded"):
        target = args.output / name
        shutil.copytree(root, target)
        if name == "upgraded":
            # Both local candidates have the same unreleased product version.
            shutil.copy2(args.mod, target / "mods" / args.old_mod.name)
        report = run(args.java, target, "complete")
        reload = run(args.java, target, "reload")
        assert first["old_terrain"] == report["old_terrain"] == reload["old_terrain"]
        assert first["chest"] == report["chest"] == reload["chest"]
        assert report["raw_marker"] and reload["raw_marker"]
        assert report["later_terrain"] == reload["later_terrain"]
        assert digest(target / profile_relative) == original_profile, "World settings were changed"
        assert digest(target / "config/realisticdeposits/deposits/vanilla.json") == original_config
        profile = json.loads((target / profile_relative).read_text(encoding="utf-8"))
        assert not any("overworld_coal_measures" in key for key in profile["ores"]), "Coal was injected into an old world"
        completed[name] = report
    assert completed["control"] == completed["upgraded"], "The update changed later-generated terrain or coal supply"
    assert world_fingerprint(root / "coal-upgrade-world") == source_hash
    write_json(args.output / "results.json", {"passed": True, "boots": 5, "source_unchanged": True,
                                              "control_matches_upgrade": True})


if __name__ == "__main__":
    main()
