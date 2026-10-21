"""Exercise scope checks against real branch ancestry, including the develop bootstrap."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("pr_scope", Path(__file__).resolve().parents[1] / "pr_scope.py")
scope = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scope)


class ScopeHistoryTest(unittest.TestCase):
    def test_bootstrap_ignores_inherited_whitespace_but_checks_new_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            run = subprocess.run

            def git(*args, **kwargs):
                return run(["git", *args], cwd=directory, check=True, capture_output=True, text=True, **kwargs)

            git("init")
            git("config", "user.name", "Scope test")
            git("config", "user.email", "scope@example.invalid")
            file = Path(directory) / "source.txt"
            file.write_text("original\n", encoding="utf-8")
            git("add", "source.txt"); git("commit", "-m", "develop")
            base = git("rev-parse", "HEAD").stdout.strip()
            file.write_text("vendor whitespace \n", encoding="utf-8")
            git("commit", "-am", "main")
            stable = git("rev-parse", "HEAD").stdout.strip()
            git("update-ref", "refs/remotes/origin/main", stable)
            (Path(directory) / "new.txt").write_text("new whitespace \n", encoding="utf-8")
            git("add", "new.txt"); git("commit", "-m", "task")
            head = git("rev-parse", "HEAD").stdout.strip()
            with patch.object(scope.subprocess, "run", side_effect=lambda args, **kw: run(args, cwd=directory, **kw)):
                diff = scope.whitespace_diff(base, head, "develop")
                self.assertEqual(diff, f"refs/remotes/origin/main...{head}")
                checked = run(["git", "diff", "--check", diff], cwd=directory, capture_output=True, text=True)
                self.assertIn("new.txt", checked.stdout)
                self.assertNotIn("source.txt", checked.stdout)
                self.assertEqual(scope.whitespace_diff(base, head, "main"), f"{base}...{head}")
                # Divergent develop cannot use main to suppress its own changes.
                self.assertEqual(scope.whitespace_diff(head, stable, "develop"), f"{head}...{stable}")
                git("update-ref", "-d", "refs/remotes/origin/main")
                self.assertEqual(scope.whitespace_diff(base, head, "develop"), f"{base}...{head}")

    def test_sensitive_scope_and_android_detection_remain_full_diff(self):
        self.assertTrue(scope.forbidden_path("SleepDown-Server/config.json"))
        self.assertTrue(scope.forbidden_path("app/build/output.apk"))
        self.assertTrue(scope.android_change("app/src/main/AndroidManifest.xml"))
        self.assertFalse(scope.android_change("docs/DEVELOPMENT_WORKFLOW.md"))


if __name__ == "__main__":
    unittest.main()
