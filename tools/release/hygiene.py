"""Reject local notes and generated evidence before sharing source."""

import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FORBIDDEN = re.compile(
    r"(^|/)(agents?\.md|claude\.md|gemini\.md|agent-notes|\.agents?|\.codex|\.claude|\.gemini|"
    r"build|bin|run(?:-[^/]*)?|logs|crash-reports|\.verification-[^/]*)(/|$)|"
    r"(?:agent|codex)[-_]report|\.(?:log|jfr|hprof)$", re.IGNORECASE)


def main():
    paths = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=ROOT
    ).decode().split("\0")
    rejected = sorted({path for path in paths if path and FORBIDDEN.search(path)})
    if rejected:
        raise SystemExit("Local files would be shared:\n" + "\n".join(rejected))
    print("Repository hygiene passed: no agent notes or generated evidence.")


if __name__ == "__main__":
    main()
