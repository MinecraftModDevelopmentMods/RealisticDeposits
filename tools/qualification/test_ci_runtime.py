"""Keep hosted checks lightweight and retain complete local runtime coverage."""

import re
import unittest
from pathlib import Path
from unittest.mock import patch

import ci_runtime


class LocalPackagedPlanTest(unittest.TestCase):
    def test_a_pass_marker_cannot_hide_a_runtime_error(self):
        ci_runtime.check_runtime_log("[main/ERROR]: FML appears to be missing any signature data\nPASS")
        for line in ("[Server thread/FATAL]: Error executing task", "[main/ERROR]: Missing a model",
                     "Tried to assign a mutable BlockPos", "GL ERROR 1281"):
            with self.assertRaises(AssertionError):
                ci_runtime.check_runtime_log(line + "\nRD_GOLD_PASS first")

    def test_local_init_script_prepares_every_probe_used_by_the_driver(self):
        script = (ci_runtime.ROOT / "tools/qualification/probes.init.gradle").read_text()
        for task in ("preparePolicyQualification", "prepareCoalQualification",
                     "prepareDiamondQualification", "prepareGoldQualification", "prepareMineQualification"):
            self.assertIn(task, script)

    def test_hosted_workflows_never_launch_packaged_minecraft_suites(self):
        for path in (ci_runtime.ROOT / ".github/workflows").glob("*.yml"):
            workflow = path.read_text()
            self.assertNotIn("ci_runtime.py", workflow, str(path))
            self.assertNotIn("probes.init.gradle", workflow, str(path))
            self.assertNotRegex(workflow, r"\bprepare\w*Qualification\b", str(path))
            self.assertNotRegex(workflow, r"\brun(?:Server|Client|TestServer)\b", str(path))
            self.assertNotRegex(workflow, r"\b\w+_110\.py\b", str(path))
            for suite in ci_runtime.suite_plan().values():
                self.assertNotIn(suite["script"], workflow, str(path))

    def test_ci_build_has_a_short_timeout(self):
        workflow = (ci_runtime.ROOT / ".github/workflows/ci.yml").read_text()
        build = workflow.split("  build:\n", 1)[1]
        timeout = re.search(r"timeout-minutes:\s*(\d+)", build)
        self.assertIsNotNone(timeout)
        self.assertLessEqual(int(timeout.group(1)), 30)

    def test_packaged_driver_rejects_hosted_execution(self):
        for environment in ({"GITHUB_ACTIONS": "true"}, {"CI": "true"}, {"CI": "1"}):
            with self.subTest(environment=environment):
                with self.assertRaisesRegex(RuntimeError, "local-only"):
                    ci_runtime.require_local_execution(environment)

    def test_packaged_driver_accepts_local_execution(self):
        ci_runtime.require_local_execution({})
        ci_runtime.require_local_execution({"CI": "false"})

    def test_main_rejects_ci_before_preparing_any_inputs(self):
        with patch.dict("os.environ", {"GITHUB_ACTIONS": "true"}):
            with self.assertRaisesRegex(RuntimeError, "local-only"):
                ci_runtime.main()

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
