"""Read a Lapis mine on matched disposable 0.5 and current-candidate world copies."""

import argparse
import json
import os
from pathlib import Path
import shutil

from ci_runtime import FORGE_HASH, require_local_execution
from frozen_mines_110 import check_saved_profile
from mines_110 import run
from qualify_110 import FORGE_JAR, check_ports, checked_orespawn_sha256, digest, world_fingerprint, write_json


OLD = "RealisticDeposits-0.5.0.110021.jar"
OLD_HASH = "E79FBDC996703E6B31703D31F1DEF7E5690F71C4E4CE3DB406624A736A41AA93"
OLD_ENGINE = "OreSpawn-4.1.5.110021.jar"
OLD_ENGINE_HASH = "1E580EBB8D93B1256E2E81C7D0D815DEAAAC58A028CB5A77A52773B62048E010"
NEW_ENGINE_VERSION = "4.1.6.110021"
WORLD = "mine-test-world"
PROFILE = Path(WORLD) / "serverconfig/orespawn-worldgen.json"
MINE = Path(WORLD) / "data/realisticdeposits_mines.dat"


def main():
    require_local_execution(os.environ)
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "source", "mod", "orespawn", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--base-port", type=int, default=29400)
    args = parser.parse_args()
    assert not args.output.exists(), "Use a new evidence directory"
    check_ports(range(args.base_port, args.base_port + 2))
    assert digest(args.source / FORGE_JAR) == FORGE_HASH
    assert digest(args.source / "mods" / OLD) == OLD_HASH
    checked_orespawn_sha256(args.source / "mods" / OLD_ENGINE, OLD_ENGINE_HASH)
    checked_orespawn_sha256(args.orespawn)
    baseline = world_fingerprint(args.source / WORLD)
    before = json.loads((args.source / PROFILE).read_text())
    mine_hash = digest(args.source / MINE)
    catalogue_dir = Path("config/realisticdeposits/deposits")
    catalogues = {file.name: digest(file) for file in (args.source / catalogue_dir).glob("*.json")}
    args.output.mkdir(parents=True)
    write_json(args.output / "artifacts.json",
               {args.mod.name: digest(args.mod), args.orespawn.name: digest(args.orespawn),
                **{p.name: digest(p) for p in (args.source / "mods").glob("*.jar")}})
    for index, name in enumerate(("old-control", "upgraded")):
        root = args.output / name
        root.mkdir()
        for directory in ("config", "libraries", "mods", WORLD):
            shutil.copytree(args.source / directory, root / directory)
        for file in (FORGE_JAR, "minecraft_server.1.10.2.jar", "eula.txt", "lapis-target.properties",
                     "ops.json", "whitelist.json", "banned-ips.json", "banned-players.json"):
            shutil.copy2(args.source / file, root / file)
        settings = (args.source / "server.properties").read_text()
        (root / "server.properties").write_text("\n".join(
            f"server-port={args.base_port + index}" if line.startswith("server-port=") else line
            for line in settings.splitlines()) + "\n", encoding="utf-8")
        if index:
            (root / "mods" / OLD).rename(root / "retained-old-mod.jar")
            (root / "mods" / OLD_ENGINE).rename(root / "retained-old-orespawn.jar")
            shutil.copy2(args.mod, root / "mods" / args.mod.name)
            shutil.copy2(args.orespawn, root / "mods" / args.orespawn.name)
        run(args.java, root, "stable", {"rd.mines.lapis": "true", "rd.mines.scenario": "normal-structures"})
        after = json.loads((root / PROFILE).read_text())
        if index:
            # The engine may refresh its displayed version, but only on
            # the same existing OreSpawn-owned source records.
            for key, policy in before.get("ore_source_policies", {}).items():
                old_sources = policy.get("candidates", [])
                new_sources = after["ore_source_policies"][key].get("candidates", [])
                assert len(old_sources) == len(new_sources)
                for old, new in zip(old_sources, new_sources):
                    if (old.get("owner") == "orespawn" and "owner_version" in old
                            and new.get("owner_version") == NEW_ENGINE_VERSION):
                        new["owner_version"] = old["owner_version"]
            check_saved_profile(before, after, "0.7.0.110021")
        else:
            assert before == after
        assert not any(key.endswith("overworld_emerald_pockets") for key in after["ores"])
        assert digest(root / MINE) == mine_hash, "Existing Lapis mine data was rewritten"
        for file, expected in catalogues.items():
            assert digest(root / catalogue_dir / file) == expected, f"Existing catalogue changed: {file}"
    assert world_fingerprint(args.source / WORLD) == baseline, "Source Lapis world changed"
    write_json(args.output / "results.json", {"passed": True, "boots": 2, "saved_mine_sha256": mine_hash,
                                              "old_profile_unchanged_except_display_version": True})


if __name__ == "__main__":
    main()
