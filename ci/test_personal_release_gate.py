import contextlib
import io
import os
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

import personal_release_gate


class PersonalReleaseGateTest(unittest.TestCase):
    def test_gate_refuses_and_identifies_missing_evidence(self):
        output = io.StringIO()
        with contextlib.redirect_stderr(output):
            self.assertEqual(personal_release_gate.main(), 2)
        message = output.getvalue()
        self.assertIn("PUBLICATION BLOCKED", message)
        self.assertIn("No username configured for repo null/tak", message)
        self.assertIn("SHA-256", message)
        self.assertIn("unknown releases, tags or refs", message)
        self.assertIn(personal_release_gate.DESTINATION, message)
        self.assertIn("project 6850, main", message)
        self.assertEqual(len(personal_release_gate.BLOCKERS), 5)

    def test_environment_cannot_enable_publication_or_redirect_destination(self):
        output = io.StringIO()
        overrides = {
            "ENABLE_PERSONAL_RELEASE": "true",
            "CI_PROJECT_ID": "6850",
            "CI_COMMIT_BRANCH": "main",
            "CI_COMMIT_REF_PROTECTED": "true",
            "GITHUB_REPOSITORY": "company/do-not-publish",
            "GITHUB_TOKEN": "test-secret-must-not-appear",
        }
        with patch.dict(os.environ, overrides), contextlib.redirect_stderr(output):
            self.assertEqual(personal_release_gate.main(), 2)
        self.assertNotIn("company/do-not-publish", output.getvalue())
        self.assertNotIn("test-secret-must-not-appear", output.getvalue())

    def test_cli_fails_instead_of_reporting_success(self):
        result = subprocess.run(
            [sys.executable, str(Path(personal_release_gate.__file__).resolve())],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, "")
        self.assertIn("No publisher is enabled", result.stderr)


if __name__ == "__main__":
    unittest.main()
