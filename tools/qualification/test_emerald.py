"""An emerald policy pass must prove more than the presence of some ore."""

import copy
import unittest

from emerald_110 import compare_background, followed_zones


class EmeraldPolicyReceiptTest(unittest.TestCase):
    def test_prospecting_counts_only_face_connected_generated_clues(self):
        mine = {"ore": ["0,20,0", "3,21,0", "7,21,0"], "zone_ore": [1, 1, 1],
                "landing": "0,20,1", "trace": ["1,20,0", "2,20,0", "2,21,0", "6,21,0"]}
        self.assertEqual([1, 2], followed_zones(mine))
        mine["trace"].remove("2,20,0")
        self.assertEqual([1], followed_zones(mine))

    def test_distinguishes_shared_rng_from_changed_rules_or_gates(self):
        control = {"ordinary_rules": {"emerald": {"frequency": 1.0}}, "background_events": 25,
                   "background_denied": 0, "background": ["0,12,0", "1,13,2"],
                   "first": {"ore": []}}
        active = copy.deepcopy(control)
        active["background"][1] = "3,13,2"
        active["first"]["ore"].append("12,105,10")
        self.assertEqual(1, compare_background(active, control)["matching_positions"])
        for field, value in (("ordinary_rules", {}), ("background_denied", 1), ("background", [])):
            with self.subTest(field=field):
                broken = dict(active, **{field: value})
                with self.assertRaises(AssertionError):
                    compare_background(broken, control)
