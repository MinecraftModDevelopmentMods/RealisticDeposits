"""Check release wiring without uploading anything."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class ReleaseContractTest(unittest.TestCase):
    def test_public_dependency_and_alpha_version(self):
        properties = dict(line.split("=", 1) for line in
                          (ROOT / "gradle.properties").read_text().splitlines()
                          if "=" in line and not line.startswith("#"))
        self.assertEqual("0.1.1.110021", properties["mod_version"])
        self.assertEqual("4.1.3.110021", properties["orespawn_version"])
        self.assertEqual("9335434", properties["orespawn_curse_file_id"])
        self.assertEqual("1689703", properties["curseforge_project_id"])

    def test_actions_are_pinned(self):
        workflows = list((ROOT / ".github/workflows").glob("*.yml"))
        self.assertGreaterEqual(len(workflows), 5)

        for workflow in workflows:
            for action in re.findall(r"uses:\s*(\S+)", workflow.read_text()):
                self.assertRegex(action, r"^[\w./-]+@[0-9a-f]{40}$", str(workflow))

    def test_publication_is_guarded_and_ordered(self):
        workflow = (ROOT / ".github/workflows/deploy-release.yml").read_text()
        self.assertIn("confirm_live_publication", workflow)
        self.assertIn("MinecraftModDevelopmentMods/RealisticDeposits", workflow)
        self.assertIn("curseforge-dependencies: 245586(required)", workflow)
        self.assertIn("preparedReleaseDir", workflow)
        self.assertLess(workflow.index("  publish_maven:"), workflow.index("  publish_curseforge:"))
        self.assertLess(workflow.index("  publish_curseforge:"), workflow.index("  publish_github:"))
        self.assertIn("github-prerelease: true", workflow)


if __name__ == "__main__":
    unittest.main()
