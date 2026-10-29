"""Check abandoned workings on disposable, exact packaged Forge 1.10.2 servers."""

import argparse
import json
import shutil
import subprocess
from pathlib import Path

from qualify_110 import checked_orespawn_sha256, FORGE_JAR, digest, write_json
from ci_runtime import FORGE_HASH


ROOT = Path(__file__).resolve().parents[2]


def run(java, root, phase, properties=None):
    command = [str(java), "-Xms256m", "-Xmx2G", "-XX:ActiveProcessorCount=2",
               "-Drd.mines.enabled=true", f"-Drd.mines.phase={phase}"]
    command.extend(f"-D{key}={value}" for key, value in (properties or {}).items())
    command.extend(["-jar", FORGE_JAR, "nogui"])
    with (root / f"{phase}.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=root, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            code = process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise AssertionError(f"{root.name}/{phase} timed out; its evidence is retained")

    log = (root / f"{phase}.log").read_text(encoding="utf-8", errors="replace")
    assert code == 0 and f"RD_MINES_PASS {phase}" in log, f"{root.name}/{phase} failed"
    errors = [line for line in log.splitlines() if ("/ERROR]" in line or "/FATAL]" in line)
              and "FML appears to be missing any signature data" not in line]
    assert not errors and not list((root / "crash-reports").glob("*")), errors
    assert (root / f"mine-{phase}-pass.properties").is_file(), "Missing packaged probe receipt"
    fml = root / "logs/fml-server-latest.log"
    if fml.is_file():
        shutil.copy2(fml, root / f"{phase}.fml.log")
    print(f"PASS {root.name}/{phase}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "orespawn", "probe", "output"):
        parser.add_argument(f"--{name}", required=True, type=Path)
    cases = ["flat", "normal", "off", "chance-zero", "wet", "order-reverse", "order-shuffle", "structure", "blocked"]
    parser.add_argument("--cases", nargs="+", default=cases, choices=cases)
    parser.add_argument("--local-orespawn-sha256", help="Exact checksum of the deliberately selected local candidate")
    args = parser.parse_args()
    output = args.output.resolve()
    assert not output.exists(), "Choose a new evidence directory; previous results are never overwritten"
    assert digest(args.runtime / FORGE_JAR) == FORGE_HASH, "Unexpected Forge server"
    checked_orespawn_sha256(args.orespawn, args.local_orespawn_sha256)
    assert (args.runtime / "eula.txt").read_text().strip() == "eula=true", "Use an already accepted runtime"
    output.mkdir(parents=True)
    sealed = output / "inputs"
    sealed.mkdir()
    for source in (args.mod, args.orespawn, args.probe):
        shutil.copy2(source, sealed / source.name)
    write_json(output / "artifacts.json", {path.name: digest(path) for path in sealed.iterdir()})

    results = []
    for index, case in enumerate(args.cases):
        root = output / case
        root.mkdir()
        shutil.copytree(args.runtime / "libraries", root / "libraries")
        for name in (FORGE_JAR, "minecraft_server.1.10.2.jar", "eula.txt"):
            shutil.copy2(args.runtime / name, root / name)
        mods = root / "mods"
        mods.mkdir()
        for artifact in sealed.iterdir():
            shutil.copy2(artifact, mods / artifact.name)
        layers = "minecraft:bedrock,127*minecraft:stone,minecraft:grass"
        if case == "wet":
            layers = "minecraft:bedrock,127*minecraft:stone,20*minecraft:water"
        level_type = "DEFAULT" if case == "normal" else "FLAT"
        generator_settings = "" if case == "normal" else f"3;{layers};1;"
        settings = (
            f"level-name=mine-test-world\nlevel-seed=3346071746553592696\nlevel-type={level_type}\n"
            f"generator-settings={generator_settings}\nserver-ip=127.0.0.1\nserver-port={48610 + index}\n"
            f"online-mode=false\ngenerate-structures={'true' if case == 'normal' else 'false'}\nview-distance=3\nmax-tick-time=-1\n"
            "allow-nether=false\nspawn-animals=false\nspawn-monsters=false\n"
        )
        (root / "server.properties").write_text(settings, encoding="utf-8", newline="\n")
        catalog = json.loads((ROOT / "src/main/resources/assets/realisticdeposits/defaults/vanilla.json")
                             .read_text(encoding="utf-8"))
        catalog["deposits"][0]["abandoned_mine"] = {
            "enabled": case != "off", "chance": 0.0 if case == "chance-zero" else 1.0}
        write_json(root / "config/realisticdeposits/deposits/vanilla.json", catalog)
        if case == "flat":
            phases = ["first", "complete", "reload"]
        elif case == "normal":
            phases = ["default-first", "default-reload"]
        elif case.startswith("order-") or case in ("structure", "blocked"):
            phases = [case]
        else:
            phases = ["wet" if case == "wet" else "off"]
        for phase in phases:
            run(args.java, root, phase)
            results.append({"case": case, "phase": phase, "passed": True})
        write_json(output / "results.json", {"passed": True, "boots": len(results), "results": results})


if __name__ == "__main__":
    main()
