"""Keep saved mine upgrades strict while permitting the mod's displayed version."""

import copy
import unittest

from frozen_mines_110 import check_saved_profile


class FrozenProfileTest(unittest.TestCase):
    def test_only_the_owned_source_display_version_can_change(self):
        before = {"ores": {"iron": {"enabled": True, "frequency": 0.15}}, "ore_source_policies": {
            "iron": {"candidates": [{"owner": "realisticdeposits", "source_id": "realisticdeposits:deposit/iron",
                                      "owner_version": "0.3.0.110021"}]}}}
        after = copy.deepcopy(before)
        after["ore_source_policies"]["iron"]["candidates"][0]["owner_version"] = "0.4.0.110021"
        check_saved_profile(before, after, "0.4.0.110021")
        for key, value in (("enabled", False), ("frequency", 0.2)):
            changed = copy.deepcopy(after)
            changed["ores"]["iron"][key] = value
            with self.assertRaises(AssertionError):
                check_saved_profile(before, changed, "0.4.0.110021")

    def test_new_rules_and_other_owners_remain_protected(self):
        before = {"ore_source_policies": {"iron": {"candidates": [
            {"owner": "someone_else", "owner_version": "1"}]}}}
        after = copy.deepcopy(before)
        after["ore_source_policies"]["iron"]["candidates"][0]["owner_version"] = "2"
        with self.assertRaises(AssertionError):
            check_saved_profile(before, after, "0.4.0.110021")
        after = dict(before, ores={"new_gold": {"enabled": True}})
        with self.assertRaises(AssertionError):
            check_saved_profile(before, after, "0.4.0.110021")


if __name__ == "__main__":
    unittest.main()
