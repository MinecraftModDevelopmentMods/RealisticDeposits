"""Complete an older saved shaft on disposable control and upgraded servers."""

import argparse
import json
import shutil
import subprocess
from pathlib import Path

from coal_110 import prepare
from ci_runtime import FORGE_HASH
from mines_110 import run
from qualify_110 import FORGE_JAR, checked_orespawn_sha256, digest, world_fingerprint, write_json


ROOT = Path(__file__).resolve().parents[2]


def frozen_boot(java, root, phase):
    command = [str(java), "-Xms256m", "-Xmx2G", "-XX:ActiveProcessorCount=2",
               "-Drd.frozen.enabled=true", f"-Drd.frozen.phase={phase}", "-jar", FORGE_JAR, "nogui"]
    with (root / f"frozen-{phase}.log").open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=root, stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            code = process.wait(timeout=300)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise AssertionError("Frozen shaft check timed out; retain its evidence")
    log = (root / f"frozen-{phase}.log").read_text(encoding="utf-8", errors="replace")
    assert code == 0 and f"RD_FROZEN_MINE_PASS {phase}" in log, f"{root.name}/{phase} failed"
    assert not [line for line in log.splitlines() if ("/ERROR]" in line or "/FATAL]" in line)
                and "FML appears to be missing any signature data" not in line]
    assert not list((root / "crash-reports").glob("*"))
    shutil.copy2(root / "logs/fml-server-latest.log", root / f"frozen-{phase}.fml.log")
    print(f"PASS {root.name}/{phase}", flush=True)
    return json.loads((root / f"frozen-{phase}.json").read_text())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "old-mod", "orespawn", "old-probe", "frozen-probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--old-mod-sha256", required=True)
    args = parser.parse_args()
    assert not args.output.exists(), "Use a fresh evidence directory"
    assert digest(args.old_mod) == args.old_mod_sha256.upper()
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH
    checked_orespawn_sha256(args.orespawn)
    args.output.mkdir(parents=True)
    inputs = args.output / "inputs"
    inputs.mkdir()
    for name in ("mod", "old_mod", "orespawn", "old_probe", "frozen_probe"):
        source = getattr(args, name)
        destination = inputs / (name + ".jar")
        shutil.copy2(source, destination)
        setattr(args, name, destination)
    write_json(args.output / "artifacts.json", {p.name: digest(p) for p in inputs.iterdir()})
    current = args.mod
    args.mod = args.old_mod
    args.probe = args.old_probe
    args.port = 48670
    source = prepare(args, args.output / "source")
    shutil.copy2(args.frozen_probe, source / "mods/frozen_probe.jar")
    settings = (source / "server.properties").read_text().replace("coal-test-world", "mine-test-world").replace(";1;decoration", ";1;")
    (source / "server.properties").write_text(settings, encoding="utf-8", newline="\n")
    for name in ("vanilla", "coal", "diamonds"):
        catalogue = json.loads((ROOT / f"src/main/resources/assets/realisticdeposits/defaults/{name}.json").read_text())
        for definition in catalogue["deposits"]:
            definition["enabled"] = name == "vanilla"
        write_json(source / f"config/realisticdeposits/deposits/{name}.json", catalogue)
    run(args.java, source, "first")
    before = world_fingerprint(source / "mine-test-world")
    reports = []
    for index, name in enumerate(("control", "upgraded")):
        target = args.output / name
        shutil.copytree(source, target)
        if name == "upgraded":
            shutil.copy2(current, target / "mods/old_mod.jar")
        (target / "server.properties").write_text(settings.replace("48670", str(48671 + index)), encoding="utf-8", newline="\n")
        profile = target / "mine-test-world/serverconfig/orespawn-worldgen.json"
        profile_hash = digest(profile)
        complete = frozen_boot(args.java, target, "complete")
        assert frozen_boot(args.java, target, "reload") == complete
        assert digest(profile) == profile_hash, "Saved profile changed"
        reports.append(complete)
    assert reports[0] == reports[1], "New access logic changed the old saved shaft or its later terrain"
    assert before == world_fingerprint(source / "mine-test-world"), "The source fixture changed"
    write_json(args.output / "results.json", {"passed": True, "boots": 5, "control_matches_upgrade": True,
                                              "source_unchanged": True, "shaft": reports[0]})


if __name__ == "__main__":
    main()
