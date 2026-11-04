"""Check release wiring without uploading anything."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class ReleaseContractTest(unittest.TestCase):
    def test_public_dependency_and_candidate_version(self):
        properties = dict(line.split("=", 1) for line in
                          (ROOT / "gradle.properties").read_text().splitlines()
                          if "=" in line and not line.startswith("#"))
        self.assertEqual("0.5.0.110021", properties["mod_version"])
        self.assertEqual("4.1.5.110021", properties["orespawn_version"])
        self.assertEqual("9397156", properties["orespawn_curse_file_id"])
        self.assertEqual("1E580EBB8D93B1256E2E81C7D0D815DEAAAC58A028CB5A77A52773B62048E010",
                         properties["orespawn_sha256"])
        self.assertEqual("1689703", properties["curseforge_project_id"])

    def test_beta_is_the_default_curseforge_channel(self):
        workflow = (ROOT / ".github/workflows/deploy-release.yml").read_text()
        channel = workflow.split("      curseforge_release_level:", 1)[1].split(
            "      confirm_live_publication:", 1)[0]
        self.assertRegex(channel, r"(?m)^        default: beta$")
        self.assertIn("          - alpha", channel)
        self.assertIn("          - release", channel)

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
        self.assertIn("GitHub Release type: prerelease", workflow)

    def test_codeql_observes_a_fresh_compilation(self):
        workflow = (ROOT / ".github/workflows/codeql-analysis.yml").read_text()
        self.assertIn("build-mode: manual", workflow)
        self.assertIn("--no-build-cache --rerun-tasks", workflow)

    def test_verified_manifest_is_checked_before_publication(self):
        workflow = (ROOT / ".github/workflows/deploy-release.yml").read_text()
        staging = workflow.split("      - name: Stage the immutable release artifact", 1)[1]
        staging = staging.split("      - name: Upload immutable release artifact", 1)[0]
        self.assertIn("inputs.expected_checksums_sha256", staging)
        self.assertIn('(cd "$bundle" && sha256sum --check SHA256SUMS)', staging)
        self.assertIn('"${EXPECTED_CHECKSUMS_SHA256,,}" "$bundle/SHA256SUMS" | sha256sum --check -',
                      staging)


if __name__ == "__main__":
    unittest.main()
