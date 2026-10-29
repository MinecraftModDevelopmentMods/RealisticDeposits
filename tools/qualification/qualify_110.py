"""Qualify Realistic Deposits policies on disposable Forge 1.10.2 servers."""

import argparse
import copy
import hashlib
import json
import re
import shutil
import subprocess
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path


ORESPAWN_SHA256 = "DB254E9E865685A74262DE9D469A006AB3C8053573CC6136FD4A8591AD806CA3"
FORGE_JAR = "forge-1.10.2-12.18.3.2511-universal.jar"
POLICIES = ("additive", "hybrid-default", "hybrid-15", "replacement")
ORIGINS = ("native", "os1", "os3", "existing-override")
DEPOSIT_ID = "realisticdeposits:deposit/realisticdeposits/qualification_iron"
RESOURCE_KEY = "iron_ore"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def checked_orespawn_sha256(path, local_sha256=None):
    expected = local_sha256.upper() if local_sha256 else ORESPAWN_SHA256
    assert re.fullmatch(r"[0-9A-F]{64}", expected), "Supply the complete candidate SHA-256"
    actual = digest(path)
    assert actual == expected, "OreSpawn does not match the selected public or local candidate checksum"
    return actual


def world_fingerprint(root):
    result = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        if path.is_file():
            result.update(path.relative_to(root).as_posix().encode("utf-8"))
            result.update(bytes.fromhex(digest(path)))
    return result.hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8", newline="\n")


def imported_rule(frequency=6.5):
    return {
        "enabled": True,
        "block": "minecraft:" + RESOURCE_KEY,
        "source_mod": "orespawn",
        "native_generation": False,
        "retrogen": False,
        "dimensions": {"minecraft:overworld": {
            "enabled": True, "min_y": 12, "max_y": 18, "frequency": frequency,
            "quantity": 5, "pattern": "orespawn:default", "spread": 8,
            "vertical_spread": 0, "node_size": 4, "host_blocks": ["minecraft:stone"],
        }},
    }


def prepare(args, name, origin, policy, managed, port, bootstrap=False):
    root = args.output / name
    root.mkdir(parents=True)
    shutil.copytree(args.runtime / "libraries", root / "libraries")

    for filename in (FORGE_JAR, "minecraft_server.1.10.2.jar", "eula.txt"):
        shutil.copy2(args.runtime / filename, root / filename)

    mods = root / "mods"
    mods.mkdir()

    artifacts = (args.orespawn,) if bootstrap else (args.mod, args.orespawn, args.probe)
    for artifact in artifacts:
        shutil.copy2(artifact, mods / artifact.name)

    settings = (
        "level-name=world\nlevel-seed=3346071746553592696\nlevel-type=FLAT\n"
        f"generator-settings=3;minecraft:bedrock,{239 if RESOURCE_KEY == 'coal_ore' else 127}*minecraft:stone,minecraft:grass;1;decoration\n"
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
        "generate-structures=false\nview-distance=3\nmax-tick-time=-1\nallow-nether=false\n"
        "spawn-animals=false\nspawn-monsters=false\n"
    )
    (root / "server.properties").write_text(settings, encoding="utf-8", newline="\n")

    for filename in ("ops.json", "whitelist.json", "banned-ips.json", "banned-players.json"):
        write_json(root / filename, [])

    if not bootstrap:
        global_config = copy.deepcopy(args.base_config)
        global_config["manage_vanilla_ores"] = managed
        write_json(root / "config/orespawn-worldgen.json", global_config)
    catalog = json.loads((Path(__file__).parent / "deposit.json").read_text(encoding="utf-8"))
    definition = catalog["deposits"][0]
    definition["id"] = "realisticdeposits:qualification_" + RESOURCE_KEY.removesuffix("_ore")
    definition["resource"] = "minecraft:" + RESOURCE_KEY
    if RESOURCE_KEY == "coal_ore":
        # Keep the controlled deposit above vanilla coal so both supplies can be counted separately.
        definition.update(min_y=180, max_y=199)
        definition["shape"] = {
            "type": "coal_measures", "strike_length": [64, 96], "down_dip_width": [16, 24],
            "beds": [2, 2], "thickness": [1, 1], "rock_interval": [2, 2],
            "maximum_dip_degrees": 0, "fold_amplitude": 0, "maximum_fault_throw": 0,
        }
    definition["interaction"] = "hybrid" if policy.startswith("hybrid") else policy

    if policy == "hybrid-15":
        definition["background_generation_scale"] = 0.15

    write_json(root / "config/realisticdeposits/deposits/vanilla.json", catalog)
    coal = json.loads((Path(__file__).parents[2] / "src/main/resources/assets/realisticdeposits/defaults/coal.json")
                      .read_text(encoding="utf-8"))
    coal["deposits"][0]["enabled"] = False
    write_json(root / "config/realisticdeposits/deposits/coal.json", coal)
    sources = {}

    if origin != "native":
        flag = str(managed).lower()
        legacy_flags = root / "config/orespawn.cfg"
        legacy_flags.write_text(
            f'general {{\n B:"Replace Vanilla Oregen"={flag}\n B:Retrogen=false\n'
            ' B:"Force Retrogen"=false\n}\n', encoding="utf-8", newline="\n")
        sources[legacy_flags] = digest(legacy_flags)

        if origin == "os1":
            source = root / "config/orespawn/orespawn.json"
            write_json(source, {"dimensions": [{"dimension": "0", "ores": [{
                "blockID": "minecraft:" + RESOURCE_KEY, "size": 5, "variation": 0,
                "frequency": 3.25, "minHeight": 12, "maxHeight": 19,
            }]}]})
        else:
            source = root / "config/orespawn3/orespawn.json"
            iron = {
                "enabled": True, "retrogen": False, "feature": "default", "replaces": "default",
                "dimensions": [0], "biomes": {"excludes": []},
                "parameters": {"frequency": 3.25, "size": 5, "variation": 0,
                               "minHeight": 12, "maxHeight": 19},
                "blocks": [{"name": "minecraft:" + RESOURCE_KEY, "chance": 100}],
            }
            gold = copy.deepcopy(iron)
            gold["enabled"] = False
            gold["blocks"] = [{"name": "minecraft:gold_ore", "chance": 100}]
            write_json(source, {"version": "2.0", "spawns": {RESOURCE_KEY: iron, "gold_ore": gold}})

        sources[source] = digest(source)

    if origin == "existing-override":
        override = root / "config/orespawn-orespawn.json"
        disabled = imported_rule()
        disabled.update(enabled=False, block="minecraft:gold_ore")
        write_json(override, {
            "schema_version": 4, "provider_modid": "orespawn", "provider_revision": 1,
            "ores": {"orespawn:legacy/" + RESOURCE_KEY: imported_rule(),
                     "orespawn:legacy/gold_ore": disabled},
        })
        sources[override] = digest(override)

    return root, sources


def factory_config(args):
    root, _ = prepare(args, "factory-defaults", "native", "additive", False, args.base_port - 1, bootstrap=True)
    command = [str(args.java), "-Xms256m", "-Xmx2G", "-XX:ActiveProcessorCount=2",
               "-jar", FORGE_JAR, "nogui"]

    with (root / "bootstrap.log").open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=root, stdin=subprocess.PIPE,
                                   stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            process.communicate(input=b"stop\n", timeout=120)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise AssertionError("The public OreSpawn defaults bootstrap timed out")

    if process.returncode:
        raise AssertionError("The public OreSpawn defaults bootstrap failed")

    return json.loads((root / "config/orespawn-worldgen.json").read_text(encoding="utf-8"))


def run(args, root, phase, extra_properties=None, label=None):
    label = label or phase
    command = [str(args.java), "-Xms256m", "-Xmx2G", "-XX:ActiveProcessorCount=2",
               "-Drd.qualification.enabled=true", f"-Drd.qualification.phase={phase}"]
    command.append("-Drd.qualification.resource=" + RESOURCE_KEY.removesuffix("_ore"))
    command.extend(f"-D{key}={value}" for key, value in (extra_properties or {}).items())
    command.extend(["-jar", FORGE_JAR, "nogui"])

    with (root / f"{label}.log").open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=root, stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            code = process.wait(timeout=300)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise AssertionError(f"{root.name}/{phase} timed out; evidence is preserved")

    log = (root / f"{label}.log").read_text(encoding="utf-8", errors="replace")
    if code or f"RD_POLICY_QUALIFICATION_PASS {phase}" not in log:
        raise AssertionError(f"{root.name}/{phase} failed; see {root / (phase + '.log')}")

    unexpected = [line for line in log.splitlines() if ("/ERROR]" in line or "/FATAL]" in line)
                  and "FML appears to be missing any signature data" not in line]
    if unexpected or list((root / "crash-reports").glob("*")):
        raise AssertionError(f"{root.name}/{phase} has unexpected errors: {unexpected}")

    forge_log = root / "logs/fml-server-latest.log"
    if forge_log.is_file():
        shutil.copy2(forge_log, root / f"{label}.fml.log")

    report = json.loads((root / f"qualification-{phase}.json").read_text(encoding="utf-8"))
    if label != phase:
        write_json(root / f"{label}.json", report)
    return report


def same_sample(left, right):
    keys = ("terrain_sha256", "massive_sha256", "ordinary_iron", "massive_iron", "chunks")
    assert all(left[key] == right[key] for key in keys), "Existing terrain changed"


def check_profile(report, origin, policy, managed):
    profile = report["profile"]
    assert profile["manage_vanilla_ores"] == managed, "World management setting was replaced"
    owned = {key: value for key, value in profile["ores"].items() if key.startswith("realisticdeposits:")}
    default_coal = owned.pop("realisticdeposits:deposit/realisticdeposits/overworld_coal_measures", None)
    if default_coal is not None:
        assert default_coal["enabled"] is False, "The unrelated default coalfield must remain disabled"
    assert set(owned) == {DEPOSIT_ID}, "Duplicate or missing Realistic Deposits rule"
    rule = owned[DEPOSIT_ID]
    assert rule["retrogen"] is False
    dimension = rule["dimensions"]["minecraft:overworld"]
    expected_scale = {"additive": None, "hybrid-default": 0.25, "hybrid-15": 0.15, "replacement": 0.0}[policy]
    assert dimension.get("background_generation_scale") == expected_scale

    if origin != "native":
        imported_id = "orespawn:legacy/minecraft_" + RESOURCE_KEY + "_0" if origin == "os1" else "orespawn:legacy/" + RESOURCE_KEY
        imported = profile["ores"][imported_id]
        assert imported["enabled"] is True and imported["retrogen"] is False
        placement = imported["dimensions"]["minecraft:overworld"]
        assert (placement["min_y"], placement["max_y"], placement["quantity"]) == (12, 18, 5)
        assert placement["frequency"] == (6.5 if origin == "existing-override" else 3.25)

        if origin in ("os3", "existing-override"):
            assert profile["ores"]["orespawn:legacy/gold_ore"]["enabled"] is False


def check_generation(sample, origin, policy, managed):
    assert sample["massive_iron"] > 0, "The ordinary-generation policy suppressed the large deposit"
    assert sample["missed_eligible_stone"] == 0, "A planned deposit left eligible stone unfilled"
    assert sample["unexpected_high_iron"] == 0, "Large-deposit iron appeared outside the planned body"
    assert sample["native_iron_events"] == sample["chunks"], "Native ore event observation is incomplete"
    denied = sample["denied_native_iron_events"]

    if managed or policy == "replacement":
        assert denied == sample["native_iron_events"], "The native pass was not fully suppressed"
    elif policy == "additive":
        assert denied == 0, "Additive mode changed native generation"
    else:
        assert 0 < denied < sample["native_iron_events"], "Hybrid did not partially reduce native generation"

    if policy == "replacement":
        assert sample["ordinary_iron"] == 0, "Replacement left ordinary iron generation active"
    else:
        assert sample["ordinary_iron"] > 0, "Background iron disappeared"

    if managed and origin != "native":
        assert sample["ordinary_outside_import_height"] == 0, "A vanilla/default pass duplicated the imported rule"


def check_legacy_flags(path, managed):
    text = path.read_text(encoding="utf-8")
    expected = {"Replace Vanilla Oregen": managed, "Retrogen": False, "Force Retrogen": False}

    for key, value in expected.items():
        match = re.search(r'^\s*B:(?:"' + re.escape(key) + r'"|' + re.escape(key)
                          + r')\s*=\s*(true|false)\s*$', text, flags=re.MULTILINE)
        assert match and (match.group(1) == "true") == value, f"Legacy setting changed: {key}"


def scenario(args, origin, policy, managed, port):
    name = f"{origin}-{policy}-{'on' if managed else 'off'}"
    root, sources = prepare(args, name, origin, policy, managed, port)
    first = run(args, root, "first")
    check_profile(first, origin, policy, managed)
    check_generation(first["first"], origin, policy, managed)
    if origin != "native":
        legacy_flags = root / "config/orespawn.cfg"
        check_legacy_flags(legacy_flags, managed)
        # Forge adds current options and formats this live config on its first load.
        sources[legacy_flags] = digest(legacy_flags)
    assert all(digest(path) == expected for path, expected in sources.items()), "A legacy JSON source or override changed"
    for filename in ("orespawn-orespawn.json", "orespawn-upgrade-report.txt", "orespawn-os3-migration-report.json"):
        path = root / "config" / filename
        if path.is_file():
            sources[path] = digest(path)
    profile_path = root / "world/serverconfig/orespawn-worldgen.json"
    profile_hash = digest(profile_path)

    # Future-world defaults must not replace an existing world's saved choice.
    global_path = root / "config/orespawn-worldgen.json"
    global_config = json.loads(global_path.read_text(encoding="utf-8"))
    global_config["manage_vanilla_ores"] = not managed
    write_json(global_path, global_config)

    phases = {"first": first}
    for phase in ("reload", "later", "stable"):
        if phase == "later":
            assert not (root / "world/region/r.4.4.mca").exists(), "Later chunks were opened early"

        report = run(args, root, phase)
        check_profile(report, origin, policy, managed)
        same_sample(first["first"], report["first"])
        assert report["first"]["native_iron_events"] == 0, "Reload regenerated old chunks"
        assert digest(profile_path) == profile_hash, "World profile changed on reload"
        assert all(digest(path) == expected for path, expected in sources.items()), "An import source or override changed"

        if phase == "later":
            check_generation(report["later"], origin, policy, managed)
            same_sample(first["first"], report["first_after_later"])
        elif phase == "stable":
            same_sample(phases["later"]["later"], report["later"])
            assert report["later"]["native_iron_events"] == 0

        phases[phase] = report
        print(f"PASS {name}/{phase}", flush=True)

    summary = {"name": name, "origin": origin, "policy": policy, "managed": managed,
               "first": first["first"], "later": phases["later"]["later"],
               "protected_chunks": 162, "profile_sha256": profile_hash}
    write_json(root / "result.json", summary)
    return summary


def check_switches(args, original):
    source = args.output / original["name"]
    source_hash = world_fingerprint(source / "world")
    root = args.output / (original["name"] + "-switches")
    shutil.copytree(source, root)
    policy = original["policy"]
    original_ores = json.loads((source / "qualification-first.json").read_text(encoding="utf-8"))["profile"]["ores"]
    retained = {path: digest(path) for path in (root / "config").rglob("*") if path.is_file()
                and path.name in ("orespawn.cfg", "orespawn-orespawn.json", "orespawn-upgrade-report.txt",
                                  "orespawn-os3-migration-report.json")}
    legacy_source = root / "config/orespawn3/orespawn.json"
    retained[legacy_source] = digest(legacy_source)
    outcomes = []

    for managed, base, label in ((True, 192, "on"), (False, 256, "off-again")):
        profile_path = root / "world/serverconfig/orespawn-worldgen.json"
        profile = json.loads(profile_path.read_text(encoding="utf-8"))
        profile["manage_vanilla_ores"] = managed
        write_json(profile_path, profile)
        properties = {"rd.qualification.laterBase": base, "rd.qualification.protectedBase": 128}
        report = run(args, root, "later", properties, label=label)
        check_profile(report, "os3", policy, managed)
        check_generation(report["later"], "os3", policy, managed)
        same_sample(original["first"], report["first"])
        same_sample(original["first"], report["first_after_later"])
        same_sample(original["later"], report["protected"])
        assert report["profile"]["ores"] == original_ores, "Changing management rewrote an imported ore rule"

        reload = run(args, root, "stable", properties, label=label + "-reload")
        same_sample(report["later"], reload["later"])
        same_sample(original["first"], reload["first"])
        same_sample(original["later"], reload["protected"])
        assert reload["later"]["native_iron_events"] == 0
        assert all(digest(path) == value for path, value in retained.items()), "Switching rewrote legacy settings"
        assert reload["profile"] == report["profile"], "Reload changed the switched world profile"
        outcomes.append({"managed": managed, "new_sample": report["later"]})
        print(f"PASS {original['name']}/switch-{label}", flush=True)

    assert world_fingerprint(source / "world") == source_hash, "The source fixture changed"
    summary = {"policy": policy, "protected_chunks": 162, "source_unchanged": True, "switches": outcomes}
    write_json(root / "switch-result.json", summary)
    return summary


def check_matrix(results):
    for origin in ORIGINS:
        for managed in (False, True):
            cases = {item["policy"]: item for item in results
                     if item["origin"] == origin and item["managed"] == managed}
            if set(cases) != set(POLICIES):
                continue

            for sample in ("first", "later"):
                hashes = {item[sample]["planned_sha256"] for item in cases.values()}
                assert len(hashes) == 1, f"Policy changed the large deposit: {origin}/{managed}/{sample}"
                counts = {policy: item[sample]["ordinary_iron"] for policy, item in cases.items()}
                assert counts["additive"] > counts["hybrid-default"] > 0
                assert counts["additive"] > counts["hybrid-15"] > 0
                assert counts["replacement"] == 0


def main():
    global RESOURCE_KEY, DEPOSIT_ID
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "runtime", "mod", "orespawn", "probe", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--jobs", type=int, default=2)
    parser.add_argument("--base-port", type=int, default=25900,
                        help="First loopback port; choose a separate range for concurrent matrices")
    parser.add_argument("--resource", choices=("iron", "coal"), default="iron")
    parser.add_argument("--only", default="", help="Run matching case names for focused diagnostics")
    parser.add_argument("--local-orespawn-sha256",
                        help="Explicit checksum for testing an unpublished local OreSpawn fix")
    args = parser.parse_args()
    RESOURCE_KEY = args.resource + "_ore"
    DEPOSIT_ID = "realisticdeposits:deposit/realisticdeposits/qualification_" + args.resource

    orespawn_sha256 = checked_orespawn_sha256(args.orespawn, args.local_orespawn_sha256)
    if "eula=true" not in (args.runtime / "eula.txt").read_text(encoding="utf-8"):
        raise AssertionError("The supplied Forge runtime has not accepted the EULA")
    if args.output.exists():
        raise AssertionError("Keep previous evidence and choose a new output directory")

    args.output.mkdir(parents=True)
    write_json(args.output / "artifacts.json", {
        "orespawn_kind": "local" if args.local_orespawn_sha256 else "published",
        "orespawn_sha256": orespawn_sha256,
        "realisticdeposits_sha256": digest(args.mod),
        "probe_sha256": digest(args.probe),
        "forge_sha256": digest(args.runtime / FORGE_JAR),
    })
    args.base_config = factory_config(args)
    results = []
    failures = []
    cases = [(origin, policy, managed) for origin in ORIGINS for policy in POLICIES for managed in (False, True)]
    cases = [case for case in cases if args.only in f"{case[0]}-{case[1]}-{'on' if case[2] else 'off'}"]

    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        pending = {pool.submit(scenario, args, *case, args.base_port + index): case
                   for index, case in enumerate(cases)}

        for future in as_completed(pending):
            try:
                results.append(future.result())
            except Exception as failure:
                failures.append({"case": pending[future], "error": repr(failure)})
                print(f"FAIL {pending[future]}: {failure!r}", flush=True)

    switches = []
    if not failures:
        try:
            check_matrix(results)
            if not args.only:
                for policy in ("hybrid-15", "replacement"):
                    original = next(item for item in results if item["origin"] == "os3"
                                    and item["policy"] == policy and not item["managed"])
                    switches.append(check_switches(args, original))
        except AssertionError as failure:
            failures.append({"case": "cross-policy comparison", "error": repr(failure)})

    write_json(args.output / "results.json", {"passed": not failures, "cases": results,
                                            "switches": switches, "failures": failures})

    if failures:
        raise AssertionError(f"{len(failures)} qualification case(s) failed; evidence is preserved")

    print(f"RD_POLICY_MATRIX_PASS cases={len(results)} boots={len(results) * 4 + len(switches) * 4}", flush=True)


if __name__ == "__main__":
    main()
