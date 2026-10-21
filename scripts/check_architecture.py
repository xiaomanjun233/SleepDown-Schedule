"""Check source dependency boundaries without compiling Android or reading private files."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.xiaomanjun.sleepdownschedule"
SOURCE = ROOT / "app/src/main/java/com/xiaomanjun/sleepdownschedule"
FORBIDDEN = {
    "core": {"app", "feature", "platform"},
    "data": {"app", "feature", "platform"},
    "domain": {"app", "feature", "platform"},
    "model": {"app", "feature", "platform"},
    "platform": {"app", "feature"},
    "feature": {"platform"},
}
REFERENCE = re.compile(r"\b" + re.escape(PACKAGE) + r"\.(app|feature|platform)\.")


def main() -> int:
    errors = []
    checked = 0
    for path in sorted(SOURCE.rglob("*.kt")):
        layer = path.relative_to(SOURCE).parts[0]
        forbidden = FORBIDDEN.get(layer, set())
        if not forbidden:
            continue
        checked += 1
        in_comment = False
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("/*"):
                in_comment = True
            if in_comment:
                if "*/" in stripped:
                    in_comment = False
                continue
            if stripped.startswith(("//", "package ")):
                continue
            for match in REFERENCE.finditer(line.split("//", 1)[0]):
                if match[1] in forbidden:
                    errors.append(f"{path.relative_to(ROOT).as_posix()}:{number}: {layer} -> {match[1]}")
    if errors:
        print("Forbidden source dependencies:\n" + "\n".join(errors), file=sys.stderr)
        return 1
    print(f"Architecture boundaries passed ({checked} Kotlin files).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
