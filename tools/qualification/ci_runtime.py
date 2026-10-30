"""Prepare an official Forge server and run the packaged policy matrix."""

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
INSTALLER_HASH = "664393C5A924B8F06CFB953903ABCF9FE5E99ECBE6AF8C7EA217339C766E519C"
FORGE_HASH = "809C2EA5B4D4F3E5A294D3A326744F20FEC412A1263E708297D00F512E377542"


def download(url, target, expected):
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["curl", "--fail", "--location", "--retry", "3",
                    "--connect-timeout", "30", "--max-time", "180",
                    "--output", str(target), url], check=True)
    content = target.read_bytes()
    actual = hashlib.sha256(content).hexdigest().upper()
    if actual != expected:
        raise AssertionError(f"Unexpected download checksum: {url}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "build/qualification/packaged")
    args = parser.parse_args()
    properties = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines()
                      if "=" in line and not line.startswith("#"))
    output = args.output.resolve()
    if output.exists():
        raise AssertionError("Choose a clean build directory; previous evidence is retained")
    output.mkdir(parents=True)
    candidate = output / "candidate"
    candidate.mkdir()
    mod = candidate / f"RealisticDeposits-{properties['mod_version']}.jar"
    probe = candidate / "policy-probe.jar"
    shutil.copy2(ROOT / "build/libs" / mod.name, mod)
    shutil.copy2(ROOT / "build/qualification/policy-probe.jar", probe)
    installer = output / "forge-installer.jar"
    download("https://maven.minecraftforge.net/net/minecraftforge/forge/1.10.2-12.18.3.2511/"
             "forge-1.10.2-12.18.3.2511-installer.jar", installer, INSTALLER_HASH)
    runtime = output / "runtime"
    runtime.mkdir()
    with (output / "install.log").open("w") as log:
        subprocess.run([str(args.java), "-jar", str(installer), "--installServer", str(runtime)],
                       cwd=runtime, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=600)
    forge = runtime / "forge-1.10.2-12.18.3.2511-universal.jar"
    if hashlib.sha256(forge.read_bytes()).hexdigest().upper() != FORGE_HASH:
        raise AssertionError("The installed Forge server changed")
    (runtime / "eula.txt").write_text("eula=true\n")
    orespawn = output / f"OreSpawn-{properties['orespawn_version']}.jar"
    file_id = properties["orespawn_curse_file_id"]
    download(f"https://cursemaven.com/curse/maven/mmd-orespawn-245586/{file_id}/"
             f"mmd-orespawn-245586-{file_id}.jar", orespawn, properties["orespawn_sha256"])
    command = [sys.executable, str(ROOT / "tools/qualification/qualify_110.py"),
               "--java", str(args.java), "--runtime", str(runtime),
               "--mod", str(mod), "--orespawn", str(orespawn), "--probe", str(probe),
               "--output", str(output / "matrix"), "--jobs", "2"]
    for resource in ("iron", "diamond"):
        matrix_output = output / ("matrix" if resource == "iron" else "diamond-matrix")
        matrix_command = list(command)
        matrix_command[matrix_command.index("--output") + 1] = str(matrix_output)
        matrix_command.extend(["--resource", resource])
        subprocess.run(matrix_command, check=True)
        result = json.loads((matrix_output / "results.json").read_text())
        if not result["passed"] or len(result["cases"]) != 32:
            raise AssertionError(f"The complete {resource} policy matrix did not pass")

    diamond_probe = candidate / "diamond-probe.jar"
    shutil.copy2(ROOT / "build/qualification/diamond-probe.jar", diamond_probe)
    subprocess.run([sys.executable, str(ROOT / "tools/qualification/diamonds_110.py"),
                    "--java", str(args.java), "--runtime", str(runtime), "--mod", str(mod),
                    "--orespawn", str(orespawn), "--probe", str(diamond_probe),
                    "--output", str(output / "diamond-runtime"),
                    "--cases", "standalone", "reverse", "source-off", "placer-off", "source-enable"], check=True)

    mine_probe = candidate / "mine-probe.jar"
    shutil.copy2(ROOT / "build/qualification/mine-probe.jar", mine_probe)
    subprocess.run([sys.executable, str(ROOT / "tools/qualification/pipe_mines_110.py"),
                    "--java", str(args.java), "--runtime", str(runtime), "--mod", str(mod),
                    "--orespawn", str(orespawn), "--probe", str(mine_probe),
                    "--output", str(output / "pipe-workings"),
                    "--cases", "flat", "reverse", "off", "chance-zero", "source-off", "structure", "wet", "blocked"], check=True)


if __name__ == "__main__":
    main()
