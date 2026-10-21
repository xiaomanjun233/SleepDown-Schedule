"""Check declared Kotlin/Java dependencies; keep existing architectural debt from growing."""

from collections import defaultdict
from dataclasses import dataclass
import json
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.xiaomanjun.sleepdownschedule"
FORBIDDEN = {
    "core": {"app", "feature", "platform"},
    "data": {"app", "feature", "platform", "glass", "transition"},
    "domain": {"app", "feature", "platform", "data", "core", "glass", "transition"},
    "model": {"app", "feature", "platform", "data", "core", "glass", "transition", "domain"},
    "platform": {"app", "feature"},
    "feature": {"app", "platform"},
    "glass": {"app", "feature"},
    "transition": {"app", "feature"},
}
LAYERS = set(FORBIDDEN) | {"app"}
AREA_FORBIDDEN = {
    "core/performance": {"glass"},
    "core/wallpaper": {"glass"},
}
REFERENCE = re.compile(r"\b" + re.escape(PACKAGE) + r"\.([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)")
ROOT_ALIAS = re.compile(r"\btypealias\s+(\w+)\s*=\s*" + re.escape(PACKAGE) + r"\.([\w.]+)")
ROOT_WILDCARD = re.compile(r"^import\s+" + re.escape(PACKAGE) + r"\.\*\s*;?\s*$", re.MULTILINE)


@dataclass(frozen=True)
class Dependency:
    path: str
    line: int
    source: str
    target: str

    @property
    def location(self) -> str:
        return f"{self.path}:{self.line}: {self.source} -> {self.target}"


def code_only(source: str) -> str:
    """Mask literals and nested comments, preserving offsets and line numbers."""
    result = list(source)
    index = 0
    while index < len(source):
        start = index
        if source.startswith("//", index):
            index = source.find("\n", index)
            if index < 0:
                index = len(source)
        elif source.startswith("/*", index):
            depth = 1
            index += 2
            while index < len(source) and depth:
                if source.startswith("/*", index):
                    depth += 1
                    index += 2
                elif source.startswith("*/", index):
                    depth -= 1
                    index += 2
                else:
                    index += 1
        elif source.startswith('"""', index):
            end = source.find('"""', index + 3)
            index = len(source) if end < 0 else end + 3
        elif source[index] in {'"', "'"}:
            quote = source[index]
            index += 1
            while index < len(source):
                if source[index] == "\\":
                    index += 2
                elif source[index] == quote:
                    index += 1
                    break
                else:
                    index += 1
        else:
            index += 1
            continue
        for position in range(start, min(index, len(source))):
            if result[position] != "\n":
                result[position] = " "
    return "".join(result)


def node(parts: list[str]) -> str | None:
    if not parts or parts[0] not in LAYERS:
        return None
    return ".".join(parts[:2]) if parts[0] == "feature" and len(parts) > 1 else parts[0]


def scan(root: Path) -> tuple[list[Dependency], int]:
    dependencies = []
    units = []
    aliases = defaultdict(set)
    for source_set in sorted((root / "app/src").iterdir()):
        if source_set.name.lower().startswith(("test", "androidtest")):
            continue
        for language in ("java", "kotlin"):
            package_root = source_set / language / PACKAGE.replace(".", "/")
            for path in sorted(package_root.rglob("*")):
                if path.suffix not in {".kt", ".java"}:
                    continue
                parts = path.relative_to(package_root).parts
                # Stable root Android entrypoints participate as application assembly.
                owner = node(list(parts[:-1])) or "app"
                source = code_only(path.read_text(encoding="utf-8"))
                units.append((path.relative_to(root).as_posix(), owner, source))
                if len(parts) == 1:
                    for match in ROOT_ALIAS.finditer(source):
                        aliases[match[1]].add(match[2])

    def targets(reference: str, visited: frozenset[str] = frozenset()) -> set[str]:
        direct = node(reference.split("."))
        if direct:
            return {direct}
        alias = reference.split(".")[0]
        if alias in visited:
            return set()
        return {target for value in aliases.get(alias, ())
            for target in targets(value, visited | {alias})}

    for path, owner, source in units:
        references = [(match[1], match.start()) for match in REFERENCE.finditer(source)]
        if ROOT_WILDCARD.search(source):
            references.extend((match[0], match.start()) for match in re.finditer(r"\b\w+\b", source)
                if match[0] in aliases)
        for reference, offset in references:
            for target in targets(reference):
                if target != owner:
                    dependencies.append(Dependency(path, source.count("\n", 0, offset) + 1, owner, target))
    return dependencies, len(units)


def forbidden_dependencies(dependencies: list[Dependency]) -> set[tuple[str, str]]:
    return {(item.path, item.target) for item in dependencies
        if item.target.split(".")[0] in FORBIDDEN.get(item.source.split(".")[0], set())
        or any(f"/{area}/" in item.path and item.target.split(".")[0] in targets
            for area, targets in AREA_FORBIDDEN.items())}


def cyclic_edges(dependencies: list[Dependency]) -> set[tuple[str, str]]:
    graph = defaultdict(set)
    for item in dependencies:
        graph[item.source].add(item.target)

    def reaches(start: str, end: str) -> bool:
        pending = [start]
        visited = set()
        while pending:
            current = pending.pop()
            if current == end:
                return True
            if current not in visited:
                visited.add(current)
                pending.extend(graph.get(current, ()))
        return False

    return {(source, target) for source in graph for target in graph[source] if reaches(target, source)}


def assess(dependencies: list[Dependency], baseline: dict) -> list[str]:
    legacy_dependencies = {(path, target) for path, targets in baseline.get("legacy_dependencies", {}).items()
        for target in targets}
    legacy_cycles = {tuple(edge.split(" -> ")) for edge in baseline.get("legacy_cycle_edges", [])}
    forbidden = forbidden_dependencies(dependencies)
    cycles = cyclic_edges(dependencies)
    errors = []
    for path, target in sorted(forbidden - legacy_dependencies):
        item = next(item for item in dependencies if item.path == path and item.target == target)
        errors.append(f"Forbidden dependency: {item.location}")
    for source, target in sorted(cycles - legacy_cycles):
        item = next(item for item in dependencies if item.source == source and item.target == target)
        errors.append(f"New cycle edge: {item.location}")
    for path, target in sorted(legacy_dependencies - forbidden):
        errors.append(f"Remove resolved dependency from baseline: {path} -> {target}")
    for source, target in sorted(legacy_cycles - cycles):
        errors.append(f"Remove resolved cycle edge from baseline: {source} -> {target}")
    return errors


def main() -> int:
    baseline = json.loads((ROOT / "scripts/architecture_baseline.json").read_text(encoding="utf-8"))
    dependencies, checked = scan(ROOT)
    errors = assess(dependencies, baseline)
    if errors:
        print("Architecture check failed:\n" + "\n".join(errors), file=sys.stderr)
        return 1
    print(f"Architecture boundaries passed ({checked} production Kotlin/Java files; "
        f"{len(forbidden_dependencies(dependencies))} legacy file dependencies, "
        f"{len(cyclic_edges(dependencies))} legacy cycle edges tracked).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
