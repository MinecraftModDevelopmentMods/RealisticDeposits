"""Keep the gold survey controls and reward arithmetic honest."""

import copy
import unittest

import gold_balance_110 as balance
import gold_survey_110 as survey
from qualify_110 import ORESPAWN_SHA256


class GoldSurveyChecksTest(unittest.TestCase):
    def test_controls_disable_every_catalogue_without_editing_the_defaults(self):
        catalogues = survey.control_catalogues()
        self.assertEqual({"vanilla", "coal", "diamonds", "gold", "lapis"}, set(catalogues))
        self.assertTrue(all(not definition["enabled"] for catalogue in catalogues.values()
                            for definition in catalogue["deposits"]))
        self.assertTrue(any(definition["enabled"] for definition in
                            survey.load_catalogue("gold")["deposits"]))

    def test_seed_indices_must_be_unique_and_inside_the_fixed_survey(self):
        self.assertEqual([0, 4, 5], survey.checked_indices([0, 4, 5]))
        for indices in ([-1], [6], [0, 0], []):
            with self.assertRaises(ValueError):
                survey.checked_indices(indices)

    def test_all_surveys_must_use_the_same_public_engine_mod_and_probe(self):
        inputs = {"RealisticDeposits-0.4.0.110021.jar": "A" * 64,
                  "OreSpawn-4.1.5.110021.jar": ORESPAWN_SHA256,
                  "gold-survey.jar": "B" * 64}
        self.assertEqual("A" * 64, balance.checked_candidate([inputs, inputs, inputs]))
        for key in inputs:
            changed = copy.deepcopy(inputs)
            changed[key] = "C" * 64
            with self.assertRaises(AssertionError):
                balance.checked_candidate([inputs, changed, inputs])

    def test_gravel_rewards_are_nuggets_not_ingots(self):
        lode = {"seed": 1, "set": "holdout", "lode_ore": 1000,
                "represented_chunks": 100, "productive_districts": 2}
        river = {"native_sediment": True, "actual_rich_gravel": 360,
                 "placer_sample_chunks": 100}
        control = {"gold_ore": 2000, "chunks": 100}
        result = balance.reward_row(lode, river, control)
        self.assertAlmostEqual(10, result["lodes"])
        self.assertAlmostEqual(0.1, result["placers"])
        self.assertAlmostEqual(3, result["background"])
        self.assertAlmostEqual(13.1, result["total_ingots"])
        self.assertEqual(500, result["mean_reserve_per_district"])

    def test_placer_estimates_cannot_replace_decorated_river_measurements(self):
        with self.assertRaisesRegex(AssertionError, "decorated"):
            balance.reward_row({}, {"native_sediment": False}, {})


if __name__ == "__main__":
    unittest.main()
