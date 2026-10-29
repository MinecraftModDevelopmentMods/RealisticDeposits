"""Keep the qualification checks sensitive to generation and save regressions."""

import copy
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock

from qualify_110 import (ORESPAWN_SHA256, checked_orespawn_sha256, check_generation,
                         check_legacy_flags, check_matrix, digest, same_sample)


def sample(ordinary, denied):
    return {
        "chunks": 81,
        "ordinary_iron": ordinary,
        "massive_iron": 400,
        "ordinary_outside_import_height": 0,
        "native_iron_events": 81,
        "denied_native_iron_events": denied,
        "terrain_sha256": "unchanged terrain",
        "massive_sha256": "same deposit",
        "planned_sha256": "same planned body",
        "missed_eligible_stone": 0,
        "unexpected_high_iron": 0,
    }


class QualificationChecksTest(unittest.TestCase):
    def test_default_checksum_matches_the_build_dependency(self):
        properties = dict(line.split("=", 1) for line in
                          (Path(__file__).resolve().parents[2] / "gradle.properties").read_text().splitlines()
                          if "=" in line and not line.startswith("#"))
        self.assertEqual(properties["orespawn_sha256"], ORESPAWN_SHA256)

    def test_local_candidates_require_their_exact_explicit_checksum(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "local-orespawn.jar"
            path.write_bytes(b"unpublished candidate")

            with self.assertRaisesRegex(AssertionError, "checksum"):
                checked_orespawn_sha256(path)

            with self.assertRaisesRegex(AssertionError, "checksum"):
                checked_orespawn_sha256(path, "0" * 64)

            self.assertEqual(digest(path), checked_orespawn_sha256(path, digest(path)))

    def test_forge_can_format_the_cfg_without_changing_legacy_values(self):
        path = Mock()
        path.read_text.return_value = (
            '# Configuration file\ngeneral {\n B:"Replace Vanilla Oregen"=false\n'
            ' B:Retrogen=false\n B:"Force Retrogen"=false\n}\n'
            'worldgen {\n B:place_terrain=true\n}\n'
        )
        check_legacy_flags(path, False)

    def test_changed_legacy_flags_are_rejected(self):
        path = Mock()
        path.read_text.return_value = (
            'B:"Replace Vanilla Oregen"=false\nB:Retrogen=true\nB:"Force Retrogen"=false\n'
        )

        with self.assertRaisesRegex(AssertionError, "Legacy setting changed"):
            check_legacy_flags(path, False)

    def test_replacement_rejects_even_one_ordinary_ore_block(self):
        check_generation(sample(0, 81), "native", "replacement", False)

        with self.assertRaisesRegex(AssertionError, "ordinary iron"):
            check_generation(sample(1, 81), "native", "replacement", False)

    def test_hybrid_requires_partial_not_complete_native_suppression(self):
        check_generation(sample(100, 60), "native", "hybrid-default", False)

        with self.assertRaisesRegex(AssertionError, "partially reduce"):
            check_generation(sample(100, 81), "native", "hybrid-default", False)

    def test_imported_generation_cannot_leak_outside_its_custom_height(self):
        value = sample(100, 81)
        value["ordinary_outside_import_height"] = 1

        with self.assertRaisesRegex(AssertionError, "duplicated"):
            check_generation(value, "os3", "additive", True)

    def test_a_terrain_change_fails_reload_comparison(self):
        before = sample(100, 0)
        after = copy.deepcopy(before)
        after["terrain_sha256"] = "one changed block"

        with self.assertRaisesRegex(AssertionError, "terrain changed"):
            same_sample(before, after)

    def test_policy_comparison_detects_a_changed_large_deposit(self):
        results = []

        for policy, count in (("additive", 1000), ("hybrid-default", 250),
                              ("hybrid-15", 150), ("replacement", 0)):
            results.append({"origin": "native", "managed": False, "policy": policy,
                            "first": sample(count, 0), "later": sample(count, 0)})

        check_matrix(results)
        results[-1]["later"]["planned_sha256"] = "missing deposit"

        with self.assertRaisesRegex(AssertionError, "large deposit"):
            check_matrix(results)

    def test_a_policy_cannot_leave_eligible_stone_in_a_planned_deposit(self):
        value = sample(0, 81)
        value["missed_eligible_stone"] = 1

        with self.assertRaisesRegex(AssertionError, "eligible stone"):
            check_generation(value, "native", "replacement", False)

    def test_managed_imports_must_also_reduce_background_generation(self):
        results = []

        for policy, count in (("additive", 1350), ("hybrid-default", 1350),
                              ("hybrid-15", 1350), ("replacement", 0)):
            results.append({"origin": "os1", "managed": True, "policy": policy,
                            "first": sample(count, 81), "later": sample(count, 81)})

        with self.assertRaises(AssertionError):
            check_matrix(results)


if __name__ == "__main__":
    unittest.main()
