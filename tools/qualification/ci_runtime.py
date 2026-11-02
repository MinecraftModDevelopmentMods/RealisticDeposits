"""Prepare an official Forge server and run the packaged policy matrix."""

import argparse
import hashlib
import json
import re
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


def suite_plan():
    suites = {}
    for resource in ("iron", "coal", "diamond", "gold"):
        suites["policy-" + resource] = {
            "script": "qualify_110.py", "probe": "policy-probe.jar",
            "arguments": ["--resource", resource], "ports": True,
        }
    suites["policy-gold-mesa"] = {
        "script": "qualify_110.py", "probe": "policy-probe.jar",
        "arguments": ["--resource", "gold", "--mesa"], "ports": True,
    }
    for name, script, probe, cases, ports in (
        ("iron-workings", "mines_110.py", "mine-probe.jar",
         ["flat", "off", "chance-zero", "wet", "order-reverse", "order-shuffle", "structure", "blocked"], False),
        ("coal", "coal_110.py", "coal-probe.jar", ["standalone", "normal", "structure", "workings-flat"], False),
        ("diamonds", "diamonds_110.py", "diamond-probe.jar",
         ["standalone", "reverse", "source-off", "placer-off", "source-enable"], True),
        ("gold", "gold_110.py", "gold-probe.jar",
         ["standalone", "normal", "reverse", "source-off", "source-enable", "source-world-off",
          "source-dimension-off", "placer-off"], True),
        ("workings", "pipe_mines_110.py", "mine-probe.jar",
         ["flat", "reverse", "off", "chance-zero", "source-off", "structure", "wet", "blocked"], False),
        ("gold-workings", "gold_mines_110.py", "mine-probe.jar",
         ["flat", "reverse", "off", "chance-zero", "source-off", "structure", "wet", "blocked"], True),
    ):
        suites[name] = {"script": script, "probe": probe, "arguments": ["--cases", *cases], "ports": ports}
    return suites


def suite_command(spec, java, runtime, mod, orespawn, probe, output, port):
    command = [sys.executable, str(ROOT / "tools/qualification" / spec["script"]),
               "--java", str(java), "--runtime", str(runtime), "--mod", str(mod),
               "--orespawn", str(orespawn), "--probe", str(probe), "--output", str(output),
               *spec["arguments"]]
    if spec["ports"]:
        command.extend(["--base-port", str(port)])
    return command


def check_runtime_log(text):
    errors = [line for line in text.splitlines()
              if re.search(r"/(ERROR|FATAL)\]|Tried to assign a mutable BlockPos|\bGL ERROR\b", line)
              and "FML appears to be missing any signature data" not in line]
    assert not errors, "Unexpected packaged-runtime log errors: " + "\n".join(errors)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "build/qualification/packaged")
    parser.add_argument("--runtime", type=Path, help="Reuse an existing official runtime without modifying it")
    parser.add_argument("--mod", type=Path, help="Use a sealed local release jar")
    parser.add_argument("--orespawn", type=Path, help="Use an already downloaded, checksum-verified public jar")
    parser.add_argument("--probe-directory", type=Path, default=ROOT / "build/qualification")
    parser.add_argument("--only", nargs="+", choices=tuple(suite_plan()), help="Run selected suites locally")
    parser.add_argument("--base-port", type=int, default=32000)
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
    shutil.copy2(args.mod or ROOT / "build/libs" / mod.name, mod)
    plan = suite_plan()
    selected = args.only or list(plan)
    assert len(set(selected)) == len(selected), "Do not replay a suite in the same evidence directory"
    for name in {plan[suite]["probe"] for suite in selected}:
        shutil.copy2(args.probe_directory / name, candidate / name)

    runtime = args.runtime.resolve() if args.runtime else output / "runtime"
    if not args.runtime:
        installer = output / "forge-installer.jar"
        download("https://maven.minecraftforge.net/net/minecraftforge/forge/1.10.2-12.18.3.2511/"
                 "forge-1.10.2-12.18.3.2511-installer.jar", installer, INSTALLER_HASH)
        runtime.mkdir()
        with (output / "install.log").open("w") as log:
            subprocess.run([str(args.java), "-jar", str(installer), "--installServer", str(runtime)],
                           cwd=runtime, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=600,
                           creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        (runtime / "eula.txt").write_text("eula=true\n")
    forge = runtime / "forge-1.10.2-12.18.3.2511-universal.jar"
    if hashlib.sha256(forge.read_bytes()).hexdigest().upper() != FORGE_HASH:
        raise AssertionError("The installed Forge server changed")
    assert "eula=true" in (runtime / "eula.txt").read_text(), "Accept the supplied runtime's EULA first"
    orespawn = output / f"OreSpawn-{properties['orespawn_version']}.jar"
    file_id = properties["orespawn_curse_file_id"]
    if args.orespawn:
        shutil.copy2(args.orespawn, orespawn)
    else:
        download(f"https://cursemaven.com/curse/maven/mmd-orespawn-245586/{file_id}/"
                 f"mmd-orespawn-245586-{file_id}.jar", orespawn, properties["orespawn_sha256"])
    assert hashlib.sha256(orespawn.read_bytes()).hexdigest().upper() == properties["orespawn_sha256"]

    completed = []
    for index, name in enumerate(selected):
        suite = plan[name]
        target = output / name
        command = suite_command(suite, args.java, runtime, mod, orespawn,
                                candidate / suite["probe"], target, args.base_port + index * 100)
        subprocess.run(command, check=True)
        result = json.loads((target / "results.json").read_text())
        assert result["passed"], f"Packaged suite failed: {name}"
        assert not list(target.rglob("crash-*.txt")), f"Packaged suite produced a crash: {name}"
        for log in target.rglob("*.log"):
            check_runtime_log(log.read_text(encoding="utf-8", errors="replace"))
        if name.startswith("policy-"):
            assert len(result["cases"]) == 32 and len(result["switches"]) == 2
        completed.append(name)
        (output / "results.json").write_text(json.dumps({
            "passed": len(completed) == len(selected), "expected_suites": selected, "suites": completed,
        }, indent=2) + "\n")


if __name__ == "__main__":
    main()
