"""Check which packaged suites the CI driver actually runs."""

import unittest
from pathlib import Path

import ci_runtime


class PackagedCiPlanTest(unittest.TestCase):
    def test_a_pass_marker_cannot_hide_a_runtime_error(self):
        ci_runtime.check_runtime_log("[main/ERROR]: FML appears to be missing any signature data\nPASS")
        for line in ("[Server thread/FATAL]: Error executing task", "[main/ERROR]: Missing a model",
                     "Tried to assign a mutable BlockPos", "GL ERROR 1281"):
            with self.assertRaises(AssertionError):
                ci_runtime.check_runtime_log(line + "\nRD_GOLD_PASS first")

    def test_the_workflow_prepares_every_probe_used_by_the_driver(self):
        workflow = (ci_runtime.ROOT / ".github/workflows/ci.yml").read_text()
        for task in ("preparePolicyQualification", "prepareCoalQualification",
                     "prepareDiamondQualification", "prepareGoldQualification", "prepareMineQualification"):
            self.assertIn(task, workflow)

    def test_every_deposit_has_policy_and_gameplay_coverage(self):
        plan = ci_runtime.suite_plan()
        for resource in ("iron", "coal", "diamond", "gold"):
            self.assertIn("policy-" + resource, plan)
        self.assertIn("--mesa", plan["policy-gold-mesa"]["arguments"])
        self.assertIn("gold", plan)
        self.assertIn("gold-workings", plan)
        self.assertIn("workings", plan)
        self.assertIn("iron-workings", plan)

    def test_gold_uses_its_own_probe_and_does_not_opt_into_local_dependencies(self):
        plan = ci_runtime.suite_plan()
        command = ci_runtime.suite_command(plan["gold"], Path("java"), Path("runtime"),
                                          Path("mod.jar"), Path("orespawn.jar"), Path("gold-probe.jar"),
                                          Path("evidence"), 32000)
        self.assertIn("gold_110.py", command[1])
        self.assertIn("source-enable", command)
        self.assertIn("source-world-off", command)
        self.assertNotIn("--local-orespawn-sha256", command)
        self.assertEqual("gold-probe.jar", plan["gold"]["probe"])


if __name__ == "__main__":
    unittest.main()
