"""Check the Mesa correction on copies of public OreSpawn gold-policy worlds."""

import argparse
import copy
import json
import shutil
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import qualify_110 as policy

PRE_MESA_RELEASE_SHA256 = "DB254E9E865685A74262DE9D469A006AB3C8053573CC6136FD4A8591AD806CA3"


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def check_global_metadata(before, after):
    expected = copy.deepcopy(before)
    for name, rule in after.get("ores", {}).items():
        previous = expected.get("ores", {}).get(name)
        if (previous is not None and name.startswith("realisticdeposits:deposit/")
                and previous.get("source_provider") == "realisticdeposits"
                and rule.get("orphaned_provider") is True):
            previous["orphaned_provider"] = True
    assert after == expected, "Reload changed settings beyond the public provider-status metadata"


def set_port(root, port):
    settings = root / "server.properties"
    lines = settings.read_text().splitlines()
    settings.write_text("\n".join(f"server-port={port}" if line.startswith("server-port=") else line
                                  for line in lines) + "\n", encoding="utf-8", newline="\n")


def check_custom_profile_refresh(before, after):
    expected = copy.deepcopy(before)
    group = "orespawn:gold|minecraft:overworld"
    expected["ore_source_policies"][group]["candidates"] = after["ore_source_policies"][group]["candidates"]
    assert expected == after, "The public reload changed a setting instead of refreshing its candidate list"


def check_engine_version_metadata(before, after, version):
    expected = copy.deepcopy(before)
    for group, settings in expected.get("ore_source_policies", {}).items():
        previous = settings.get("candidates", [])
        current = after["ore_source_policies"][group].get("candidates", [])
        assert len(previous) == len(current), "Engine upgrade changed the source candidates"
        for old, new in zip(previous, current):
            if (old.get("owner") == "orespawn" and "owner_version" in old
                    and new.get("owner_version") == version):
                old["owner_version"] = version
    assert expected == after, "Engine upgrade changed settings beyond its displayed version"


def scenario(args, origin, mode, managed, index, custom=False):
    name = f"{origin}-{mode}-{'on' if managed else 'off'}"
    source = args.source_matrix / name
    fingerprint = policy.world_fingerprint(source / "world")
    root = args.output / (name + ("-custom-channel" if custom else ""))
    shutil.copytree(source, root)

    control = args.output / (root.name + "-public-control")
    shutil.copytree(source, control)
    set_port(control, args.base_port + 64 + index)

    old = list((root / "mods").glob("OreSpawn-*.jar"))
    assert len(old) == 1
    policy.checked_orespawn_sha256(old[0], PRE_MESA_RELEASE_SHA256)
    old[0].rename(root / "retained-public-orespawn.jar")
    shutil.copy2(args.orespawn, root / "mods" / args.orespawn.name)

    profile_path = root / "world/serverconfig/orespawn-worldgen.json"
    if custom:
        profile = load(profile_path)
        profile["ores"]["orespawn:legacy/gold_ore"]["dimensions"]["minecraft:overworld"][
            "placement_channel"] = "qualification:custom_gold"
        policy.write_json(profile_path, profile)
        policy.write_json(control / "world/serverconfig/orespawn-worldgen.json", profile)

    profile_hash = policy.digest(profile_path)
    set_port(root, args.base_port + index)

    before = load(source / "qualification-first.json")["first"]
    public = policy.run(args, control, "reload", label="public-engine-reload")
    policy.same_sample(before, public["first"])
    control_profile = control / "world/serverconfig/orespawn-worldgen.json"
    if custom:
        check_custom_profile_refresh(load(profile_path), load(control_profile))
        # Save the edited channel with the public engine first, then upgrade
        # that settled save. Keep the pre-edit copies as evidence too.
        assert root.resolve().parent == args.output.resolve()
        for name in ("world", "config"):
            original = root / name
            retained = root / ("before-custom-" + name)
            assert original.resolve().parent == root.resolve() and not retained.exists()
            original.rename(retained)
            shutil.copytree(control / name, original)
        profile_hash = policy.digest(profile_path)
    else:
        assert policy.digest(control_profile) == profile_hash
    expected_profile = load(profile_path)
    if args.mod:
        previous_mods = list((root / "mods").glob("RealisticDeposits-*.jar"))
        assert len(previous_mods) == 1
        previous_mods[0].rename(root / "retained-previous-realisticdeposits.jar")
        shutil.copy2(args.mod, root / "mods" / args.mod.name)
    for path in (source / "config").rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(source / "config")
        after = control / "config" / relative
        if relative.as_posix() == "orespawn-worldgen.json":
            check_global_metadata(load(path), load(after))
        else:
            assert policy.digest(path) == policy.digest(after), "The public control changed a custom/imported file"
    config_hashes = {path.relative_to(control / "config"): policy.digest(path)
                     for path in (control / "config").rglob("*") if path.is_file()}
    old_later_path = source / "qualification-later.json"
    old_later = load(old_later_path)["later"] if old_later_path.exists() else None
    properties = {"rd.qualification.laterBase": 320}
    if old_later is not None:
        properties["rd.qualification.protectedBase"] = 128

    assert not (root / "world/region/r.10.10.mca").exists(), "Later sample was already generated"

    for phase in ("reload", "later", "stable"):
        report = policy.run(args, root, phase, properties, label="engine-upgrade-" + phase)
        policy.same_sample(before, report["first"])
        assert report["first"]["native_iron_events"] == 0, "Old chunks were regenerated"
        if old_later is not None:
            policy.same_sample(old_later, report["protected"])
        if phase == "later":
            if custom:
                assert report["later"]["ordinary_iron"] > 0, "Custom channel was scaled or suppressed"
                assert report["later"]["ordinary_outside_import_height"] == 0, "Mesa duplicated the custom rule"
                assert report["later"]["massive_iron"] > 0
                assert report["later"]["denied_native_iron_events"] == 81
            else:
                policy.check_profile(report, origin, mode, managed)
                policy.check_generation(report["later"], origin, mode, managed)
            policy.same_sample(before, report["first_after_later"])
            later = report["later"]
        elif phase == "stable":
            policy.same_sample(later, report["later"])
            assert report["later"]["native_iron_events"] == 0

        if phase == "reload" and args.orespawn_version:
            check_engine_version_metadata(expected_profile, load(profile_path), args.orespawn_version)
            profile_hash = policy.digest(profile_path)
        else:
            assert policy.digest(profile_path) == profile_hash, "The engine rewrote saved world settings"
        actual = {path.relative_to(root / "config"): policy.digest(path)
                  for path in (root / "config").rglob("*") if path.is_file()}
        if phase == "reload" and args.orespawn_version:
            assert set(actual) == set(config_hashes), "The engine changed the configuration file set"
            for relative, expected in config_hashes.items():
                if relative.as_posix() == "orespawn-worldgen.json":
                    check_engine_version_metadata(load(control / "config" / relative),
                                                  load(root / "config" / relative), args.orespawn_version)
                else:
                    assert actual[relative] == expected, "A custom/imported configuration file changed"
            config_hashes = actual
        assert actual == config_hashes, "Custom/imported settings differ from the public reload control"

    assert policy.world_fingerprint(source / "world") == fingerprint, "A public-release source world changed"
    result = {"case": root.name, "passed": True, "boots": 4, "old_chunks_unchanged": True,
              "settings_unchanged": True, "source_unchanged": True, "custom_channel": custom,
              "global_metadata_matches_public_control": True,
              "engine_version_metadata": args.orespawn_version,
              "later": later}
    policy.write_json(root / "engine-upgrade-result.json", result)
    print("PASS engine upgrade " + root.name, flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "orespawn", "source-matrix", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--local-orespawn-sha256", help="Explicit checksum for an unpublished test candidate")
    parser.add_argument("--orespawn-version", help="Permit only this displayed engine-version metadata refresh")
    parser.add_argument("--mod", type=Path, help="Use the repinned Realistic Deposits jar after the public control")
    parser.add_argument("--jobs", type=int, default=2)
    parser.add_argument("--base-port", type=int, default=29100)
    parser.add_argument("--only", nargs="+", help="Run these exact case names in a fresh evidence directory")
    args = parser.parse_args()
    candidate = policy.checked_orespawn_sha256(args.orespawn, args.local_orespawn_sha256)
    assert not args.output.exists(), "Keep earlier evidence; choose a new directory"
    args.output.mkdir(parents=True)
    policy.RESOURCE_KEY = "gold_ore"
    policy.DEPOSIT_ID = "realisticdeposits:deposit/realisticdeposits/qualification_gold"
    cases = [(origin, mode, managed, False) for origin in policy.ORIGINS
             for mode in policy.POLICIES for managed in (False, True)]
    cases.extend((origin, "replacement", True, True) for origin in ("os3", "existing-override"))
    if args.only:
        cases = [case for case in cases if
                 f"{case[0]}-{case[1]}-{'on' if case[2] else 'off'}" +
                 ("-custom-channel" if case[3] else "") in args.only]
        assert len(cases) == len(set(args.only)), "Unknown or duplicate upgrade case names"
    results, failures = [], []

    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        pending = {pool.submit(scenario, args, origin, mode, managed, index, custom):
                   (origin, mode, managed, custom)
                   for index, (origin, mode, managed, custom) in enumerate(cases)}
        for future in as_completed(pending):
            try:
                results.append(future.result())
            except Exception as error:
                failures.append({"case": pending[future], "error": repr(error)})
                print("FAIL engine upgrade", pending[future], repr(error), flush=True)

    policy.write_json(args.output / "results.json", {"passed": not failures, "orespawn_sha256": candidate,
                                                    "cases": results, "failures": failures,
                                                    "boots": len(results) * 4})
    assert not failures, "Engine upgrade checks failed; evidence is preserved"


if __name__ == "__main__":
    main()
