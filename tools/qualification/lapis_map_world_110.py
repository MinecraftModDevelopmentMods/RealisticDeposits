"""Read a finished lapis fixture on a disposable copy and capture a lengthwise section."""

import argparse
import json
import shutil
from pathlib import Path

from lapis_110 import run, same
from qualify_110 import check_ports, digest, world_fingerprint, write_json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "source", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--port", type=int, default=28870)
    args = parser.parse_args()
    check_ports([args.port])
    assert not args.output.exists(), "Use a new evidence directory"
    world = args.source / "lapis-test-world"
    before = world_fingerprint(world)
    reference = json.loads((args.source / "lapis-stable.json").read_text())
    args.output.mkdir(parents=True)
    for name in ("libraries", "mods", "config", "lapis-test-world"):
        shutil.copytree(args.source / name, args.output / name)
    for name in ("forge-1.10.2-12.18.3.2511-universal.jar", "minecraft_server.1.10.2.jar", "eula.txt",
                 "lapis-target.json", "server.properties", "ops.json", "whitelist.json", "banned-ips.json", "banned-players.json"):
        shutil.copy2(args.source / name, args.output / name)
    previous = list((args.output / "mods").glob("lapis-probe.jar"))
    assert len(previous) == 1, "Use a finished fixture with its one isolated lapis probe"
    previous[0].rename(args.output / "retained-previous-probe.jar")
    shutil.copy2(args.probe, args.output / "mods/lapis-probe.jar")
    settings = (args.output / "server.properties").read_text().splitlines()
    (args.output / "server.properties").write_text("\n".join(
        f"server-port={args.port}" if line.startswith("server-port=") else line for line in settings) + "\n",
        encoding="utf-8", newline="\n")
    profile = args.output / "lapis-test-world/serverconfig/orespawn-worldgen.json"
    profile_before = digest(profile)
    report = run(args, args.output, "stable", "map-copy")
    same(reference["first"], report["first"])
    same(reference["later"], report["later"])
    assert digest(profile) == profile_before, "Map capture changed the saved profile"
    assert world_fingerprint(world) == before, "The source fixture changed"
    write_json(args.output / "artifacts.json", {p.name: digest(p) for p in (args.output / "mods").glob("*.jar")})
    write_json(args.output / "results.json", {"passed": True, "boots": 1,
                                               "source_world_unchanged": True, "terrain_unchanged": True})


if __name__ == "__main__":
    main()
