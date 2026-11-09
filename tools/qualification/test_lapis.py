"""Keep lapis rewards and immutable-input checks sensitive to mistakes."""

import copy
import tempfile
import unittest
from pathlib import Path

import lapis_110
import lapis_mines_110
import lapis_balance_110 as balance
from qualify_110 import ORESPAWN_SHA256


class LapisChecksTest(unittest.TestCase):
    def test_strata_only_fixture_preserves_the_public_profile_and_unrelated_rules(self):
        profile = {"schema_version": 7, "custom_setting": 17, "ores": {
            "mineralogy:gold": {"enabled": True, "custom": 3},
            "custom:gold": {"enabled": True, "custom": 9},
        }}
        original = copy.deepcopy(profile)
        result = lapis_mines_110.strata_only(profile)
        self.assertEqual(original, profile)
        self.assertFalse(result["ores"]["mineralogy:gold"]["enabled"])
        self.assertEqual(profile["ores"]["custom:gold"], result["ores"]["custom:gold"])
        self.assertEqual(17, result["custom_setting"])

    def test_rewards_count_native_lapis_pieces_not_ore_blocks(self):
        lenses = {"seed": 7, "set": "holdout", "lens_ore": 1000, "represented_chunks": 100,
                  "productive_districts": 2, "district_reserves": [{"ore": 400}, {"ore": 600}],
                  "prospecting_distances": [100, 200, 300]}
        control = {"seed": 7, "lapis_ore": 400, "chunks": 100}
        result = balance.reward_row(lenses, control)
        self.assertAlmostEqual(63.6, result["pieces_per_chunk"])
        self.assertEqual(24, result["vanilla_pieces_per_chunk"])

    def test_surveys_require_matching_public_inputs(self):
        record = {"RealisticDeposits-0.5.0.110021.jar": "A" * 64,
                  "OreSpawn-4.1.6.110021.jar": ORESPAWN_SHA256, "lapis-probe.jar": "B" * 64}
        self.assertEqual("A" * 64, balance.checked_inputs([record, record]))
        for key in record:
            changed = copy.deepcopy(record)
            changed[key] = "C" * 64
            with self.assertRaises(AssertionError):
                balance.checked_inputs([record, changed])

    def test_reload_check_rejects_changed_ore_and_terrain(self):
        sample = {"terrain_sha256": "A", "lapis_sha256": "B", "lens_ore": 300, "outside_ore": 20}
        for key in sample:
            changed = dict(sample)
            changed[key] = "changed"
            with self.assertRaises(AssertionError):
                lapis_110.same(sample, changed)

    def test_controlled_placement_disables_unrelated_catalogues(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            lapis_110.configure(root, "standalone")
            import json
            for path in (root / "config/realisticdeposits/deposits").glob("*.json"):
                catalogue = json.loads(path.read_text())
                for definition in catalogue["deposits"]:
                    self.assertEqual(path.stem == "lapis", definition["enabled"])


if __name__ == "__main__":
    unittest.main()
