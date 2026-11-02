"""Do not mistake public status bookkeeping for permission to change settings."""

import copy
import unittest
from gold_engine_upgrade_110 import check_global_metadata, check_custom_profile_refresh, check_engine_version_metadata


class GoldEngineUpgradeChecksTest(unittest.TestCase):
    def test_version_refresh_cannot_change_generation_or_source_preferences(self):
        group = "orespawn:gold|minecraft:overworld"
        before = {"manage_vanilla_ores": False, "ores": {"a": {"frequency": 0.375}},
                  "ore_source_policies": {group: {"placement_sources": {"orespawn:standard": "a"},
                                                "candidates": [{"owner": "orespawn", "owner_version": "4.1.4.110021"}]}}}
        after = copy.deepcopy(before)
        after["ore_source_policies"][group]["candidates"][0]["owner_version"] = "4.1.5.110021"
        check_engine_version_metadata(before, after, "4.1.5.110021")
        for key, value in (("frequency", 2), ("enabled", False)):
            invalid = copy.deepcopy(after)
            invalid["ores"]["a"][key] = value
            with self.assertRaisesRegex(AssertionError, "settings"):
                check_engine_version_metadata(before, invalid, "4.1.5.110021")
        invalid = copy.deepcopy(after)
        invalid["ore_source_policies"][group]["placement_sources"] = {}
        with self.assertRaisesRegex(AssertionError, "settings"):
            check_engine_version_metadata(before, invalid, "4.1.5.110021")
        with self.assertRaisesRegex(AssertionError, "settings"):
            check_engine_version_metadata(before, after, "4.1.6.110021")

    def test_a_channel_edit_cannot_change_saved_source_preferences(self):
        group = "orespawn:gold|minecraft:overworld"
        before = {"ores": {"a": {"frequency": 0.375}}, "ore_source_policies": {group: {
            "placement_sources": {"orespawn:standard": "a"}, "candidates": []}}}
        after = copy.deepcopy(before)
        after["ore_source_policies"][group]["candidates"].append({"placement_channel": "test:custom"})
        check_custom_profile_refresh(before, after)
        after["ore_source_policies"][group]["placement_sources"] = {}
        with self.assertRaisesRegex(AssertionError, "setting"):
            check_custom_profile_refresh(before, after)

    def test_only_the_public_provider_status_may_change(self):
        name = "realisticdeposits:deposit/realisticdeposits/overworld_gold_lodes"
        before = {"manage_vanilla_ores": False, "ores": {name: {
            "source_provider": "realisticdeposits", "enabled": False, "frequency": 0.375}}}
        after = copy.deepcopy(before)
        after["ores"][name]["orphaned_provider"] = True
        check_global_metadata(before, after)

        for altered in ("frequency", "enabled"):
            invalid = copy.deepcopy(after)
            invalid["ores"][name][altered] = 1
            with self.assertRaisesRegex(AssertionError, "settings"):
                check_global_metadata(before, invalid)

        invalid = copy.deepcopy(after)
        invalid["manage_vanilla_ores"] = True
        with self.assertRaisesRegex(AssertionError, "settings"):
            check_global_metadata(before, invalid)

        invalid = copy.deepcopy(after)
        invalid["ores"][name]["placement_channel"] = "another:channel"
        with self.assertRaisesRegex(AssertionError, "settings"):
            check_global_metadata(before, invalid)


if __name__ == "__main__":
    unittest.main()
