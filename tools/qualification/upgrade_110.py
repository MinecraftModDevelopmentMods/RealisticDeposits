"""Check an OreSpawn update on disposable copies of the 4.1.2 matrix."""

import argparse
import json
import shutil
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from qualify_110 import (checked_orespawn_sha256, check_generation, check_profile,
                         digest, run, same_sample, world_fingerprint, write_json)


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def retained_inputs(root):
    return {path: digest(path) for path in (root / "config").rglob("*")
            if path.is_file() and path.name != "orespawn-worldgen.json"}


def scenario(args, origin, policy, index, custom=False):
    name = f"{origin}-{policy}-on"
    source = args.source_matrix / name
    source_hash = world_fingerprint(source)
    root = args.output / (name + ("-explicit-custom" if custom else ""))
    shutil.copytree(source, root)

    public = root / "mods/OreSpawn-4.1.2.110021-cursemaven.jar"
    checked_orespawn_sha256(public, "4D4975421FEEBF17505056306D06B0344D7A4553B6275885D7C5C5065B0348A6")
    public.rename(root / "retained-public-orespawn.jar")
    shutil.copy2(args.orespawn, root / "mods" / args.orespawn.name)
    old_probe = root / "mods/policy-probe.jar"
    shutil.copy2(args.probe, old_probe)

    if args.mod:
        old_mods = list((root / "mods").glob("RealisticDeposits-*.jar"))
        assert len(old_mods) == 1, "The source fixture has an ambiguous mod version"
        old_mods[0].rename(root / "retained-public-realisticdeposits.jar")
        shutil.copy2(args.mod, root / "mods" / args.mod.name)

    settings = root / "server.properties"
    lines = settings.read_text(encoding="utf-8").splitlines()
    lines = [f"server-port={26700 + index}" if line.startswith("server-port=") else line for line in lines]
    settings.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")

    before = load(source / "qualification-first.json")
    old_later = load(source / "qualification-later.json")["later"]
    profile_path = root / "world/serverconfig/orespawn-worldgen.json"
    expected_ores = load(profile_path)["ores"]

    if custom:
        profile = load(profile_path)
        placement = profile["ores"]["orespawn:legacy/iron_ore"]["dimensions"]["minecraft:overworld"]
        placement["placement_channel"] = "qualification:custom_iron"
        write_json(profile_path, profile)
        expected_ores = profile["ores"]

    retained = retained_inputs(root)
    properties = {"rd.qualification.laterBase": 128}
    first_reload = run(args, root, "stable", properties, label="upgrade-reload")
    same_sample(before["first"], first_reload["first"])
    same_sample(old_later, first_reload["later"])
    assert first_reload["first"]["native_iron_events"] == 0
    assert first_reload["later"]["native_iron_events"] == 0

    properties = {"rd.qualification.laterBase": 320, "rd.qualification.protectedBase": 128}
    assert not (root / "world/region/r.10.10.mca").exists(), "Upgrade sample was already generated"
    later = run(args, root, "later", properties, label="upgrade-later")
    check_profile(later, origin, policy, True)
    same_sample(before["first"], later["first"])
    same_sample(before["first"], later["first_after_later"])
    same_sample(old_later, later["protected"])

    if custom:
        sample = later["later"]
        assert sample["ordinary_iron"] > 0, "The fix suppressed an explicit custom channel"
        assert sample["massive_iron"] > 0 and sample["missed_eligible_stone"] == 0
        assert sample["unexpected_high_iron"] == 0
    else:
        check_generation(later["later"], origin, policy, True)

    final = run(args, root, "stable", properties, label="upgrade-later-reload")
    same_sample(later["later"], final["later"])
    same_sample(before["first"], final["first"])
    same_sample(old_later, final["protected"])
    assert final["later"]["native_iron_events"] == 0

    for report in (first_reload, later, final):
        assert report["profile"]["manage_vanilla_ores"] is True
        assert report["profile"]["ores"] == expected_ores, "The fix rewrote saved ore definitions"

    assert all(digest(path) == value for path, value in retained.items()), "An imported setting changed"
    assert final["profile"] == later["profile"], "Profile changed after the upgrade settled"
    assert world_fingerprint(source) == source_hash, "The published-release source fixture changed"

    result = {"case": root.name, "origin": origin, "policy": policy,
              "passed": True, "source_unchanged": True,
              "old_chunks_unchanged": True, "imported_settings_unchanged": True,
              "explicit_custom_channel": custom, "new_sample": later["later"]}
    write_json(root / "upgrade-result.json", result)
    print("UPGRADE PASS", root.name, "ordinary iron:", later["later"]["ordinary_iron"], flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "orespawn", "probe", "source-matrix", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--local-orespawn-sha256")
    parser.add_argument("--mod", type=Path, help="Use the new Realistic Deposits jar too")
    parser.add_argument("--jobs", type=int, default=2)
    args = parser.parse_args()

    candidate = checked_orespawn_sha256(args.orespawn, args.local_orespawn_sha256)
    assert not args.output.exists(), "Keep previous evidence and choose a new output directory"
    args.output.mkdir(parents=True)
    cases = [(origin, policy, False) for origin in ("os1", "os3", "existing-override")
             for policy in ("hybrid-default", "hybrid-15", "replacement")]
    cases += [(origin, "additive", False) for origin in ("native", "os1", "os3", "existing-override")]
    cases += [("native", "hybrid-15", False), ("native", "replacement", False),
              ("os3", "replacement", True)]
    results = []
    failures = []

    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        pending = {pool.submit(scenario, args, origin, policy, index, custom): (origin, policy, custom)
                   for index, (origin, policy, custom) in enumerate(cases)}

        for future in as_completed(pending):
            try:
                results.append(future.result())
            except Exception as failure:
                failures.append({"case": pending[future], "error": repr(failure)})
                print("UPGRADE FAIL", pending[future], repr(failure), flush=True)

    for result in results:
        if result["explicit_custom_channel"] or not result["policy"].startswith("hybrid"):
            continue

        additive = next((item for item in results if item["origin"] == result["origin"]
                         and item["policy"] == "additive"), None)

        if additive is not None and not (0 < result["new_sample"]["ordinary_iron"]
                                        < additive["new_sample"]["ordinary_iron"]):
            failures.append({"case": result["case"], "error": "Imported hybrid frequency was not reduced"})

    write_json(args.output / "results.json", {"passed": not failures, "orespawn_sha256": candidate,
                                            "cases": results, "failures": failures})
    assert not failures, "Upgrade qualification failed; evidence is preserved"


if __name__ == "__main__":
    main()
