"""Check a PR's changed paths without opening private files or using contributor text as code."""
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess


def android_change(path: str) -> bool:
    return path.startswith(("app/", "benchmark/", "coloros-wakeup-proxy/", "third-party/", "patches/", "gradle/")) or path in {
        "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
        ".github/workflows/pr-checks.yml", ".github/scripts/pr_scope.py",
    }


def forbidden_path(path: str) -> bool:
    parts = PurePosixPath(path.lower()).parts
    return bool(parts) and (
        parts[0] in {"sleepdown-server", "tmp", "debug-artifacts", ".gradle-user-home", ".sleepdown-secrets"}
        or any(part in {"build", ".gradle", ".kotlin"} for part in parts)
        or PurePosixPath(path.lower()).suffix in {".jks", ".keystore", ".apk", ".aab", ".db", ".sqlite", ".sqlite3"}
        or parts[-1] in {"local.properties", ".env", "ui.xml"}
    )


def main() -> None:
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    pull = event["pull_request"]
    base, head = pull["base"]["sha"], pull["head"]["sha"]
    if not all(re.fullmatch(r"[0-9a-f]{40,64}", sha) for sha in (base, head)):
        raise ValueError("Invalid PR commit identifiers")
    diff = f"{base}...{head}"
    paths = subprocess.check_output(
        ["git", "diff", "--name-only", "--diff-filter=ACMR", "-z", diff], text=True, encoding="utf-8"
    ).strip("\0").split("\0")
    paths = [path for path in paths if path]
    if any(forbidden_path(path) for path in paths):
        raise SystemExit("Remove private data, credentials, local files or build outputs from this PR. See CONTRIBUTING.md.")
    # Unified patch context lines intentionally end in a space; validate patches during dependency setup.
    subprocess.run(["git", "diff", "--check", diff, "--", ".", ":(exclude)**/*.patch"], check=True)
    all_paths = subprocess.check_output(["git", "diff", "--name-only", "-z", diff], text=True, encoding="utf-8").split("\0")
    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
        output.write(f"android={str(any(android_change(path) for path in all_paths)).lower()}\n")
    print(f"Checked {len(paths)} added or modified paths.")


if __name__ == "__main__":
    main()
