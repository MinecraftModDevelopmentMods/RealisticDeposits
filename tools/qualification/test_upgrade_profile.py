"""A version-label refresh must not hide changed world settings."""

import copy
import unittest

from coal_upgrade_110 import captured_settings


class UpgradeProfileTest(unittest.TestCase):
    def test_only_owned_source_display_versions_are_ignored(self):
        old = {"owner": "realisticdeposits", "source_id": "realisticdeposits:deposit/iron",
               "owner_version": "0.2.0.110021", "enabled": True}
        current = dict(old, owner_version="0.3.0.110021")
        self.assertEqual(captured_settings(old), captured_settings(current))
        current["enabled"] = False
        self.assertNotEqual(captured_settings(old), captured_settings(current))

    def test_other_mod_metadata_and_nested_settings_remain_protected(self):
        old = {"ores": {"custom": {"owner": "someone_else", "owner_version": "1", "frequency": 0.15}}}
        current = copy.deepcopy(old)
        current["ores"]["custom"]["owner_version"] = "2"
        self.assertNotEqual(captured_settings(old), captured_settings(current))
        current = copy.deepcopy(old)
        current["ores"]["custom"]["frequency"] = 1.0
        self.assertNotEqual(captured_settings(old), captured_settings(current))


if __name__ == "__main__":
    unittest.main()
