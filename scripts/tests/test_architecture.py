"""Regression checks for dependency enforcement, rather than the current application layout."""
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_architecture as architecture


class ArchitectureTest(unittest.TestCase):
    def scan_alias_fixture(self, references):
        with TemporaryDirectory(dir=architecture.ROOT) as temporary:
            root = Path(temporary)
            base = root / "app/src/main/java" / architecture.PACKAGE.replace('.', '/')
            base.mkdir(parents=True)
            (base / "Compat.kt").write_text(
                'typealias Screen = com.xiaomanjun.sleepdownschedule.feature.home.Screen\n'
                'typealias Chained = com.xiaomanjun.sleepdownschedule.Screen\n'
                'typealias Model = com.xiaomanjun.sleepdownschedule.model.Value\n'
                'typealias Loop = com.xiaomanjun.sleepdownschedule.Loop\n', encoding="utf-8")
            (base / "core").mkdir()
            (base / "core/Sample.kt").write_text(references, encoding="utf-8")
            return architecture.scan(root)[0]

    def test_root_alias_import_and_qualified_usage_cannot_hide_a_feature_dependency(self):
        for reference in ['import com.xiaomanjun.sleepdownschedule.Screen as Renamed',
                          'val screen: com.xiaomanjun.sleepdownschedule.Chained? = null']:
            with self.subTest(reference=reference):
                errors = architecture.assess(self.scan_alias_fixture(reference), {})
                self.assertTrue(any('core -> feature.home' in error for error in errors))

    def test_root_wildcard_resolves_used_aliases_and_alias_chains(self):
        deps = self.scan_alias_fixture('import com.xiaomanjun.sleepdownschedule.*\nval screen: Chained? = null')
        self.assertEqual({"feature.home"}, {item.target for item in deps if item.source == "core"})

    def test_unused_aliases_and_masked_names_do_not_add_dependencies(self):
        deps = self.scan_alias_fixture('import com.xiaomanjun.sleepdownschedule.*\n'
            '// Screen Chained\nval name = "Screen"\nval data: Model? = null')
        self.assertEqual({"model"}, {item.target for item in deps if item.source == "core"})
        self.assertEqual([], architecture.assess(deps, {}))

    def test_cyclic_aliases_terminate_without_inventing_a_target(self):
        deps = self.scan_alias_fixture('import com.xiaomanjun.sleepdownschedule.Loop\nval value: Loop? = null')
        self.assertEqual([], [item for item in deps if item.source == "core"])

    def dependency(self, source, target, path="sample.kt"):
        return architecture.Dependency(path, 3, source, target)

    def test_shared_layers_cannot_reference_features_or_persistence_from_domain(self):
        for source, target in [("core", "feature.home"), ("data", "feature.agent"),
                               ("domain", "data"), ("model", "domain"), ("platform", "app")]:
            with self.subTest(source=source, target=target):
                self.assertTrue(architecture.assess([self.dependency(source, target)], {}))

    def test_feature_to_app_is_rejected(self):
        self.assertIn("Forbidden dependency", architecture.assess([self.dependency("feature.home", "app")], {})[0])

    def test_wallpaper_and_performance_cannot_depend_on_glass_rendering(self):
        for area in ("performance", "wallpaper"):
            with self.subTest(area=area):
                path = f"app/src/github/java/{architecture.PACKAGE.replace('.', '/')}/core/{area}/Sample.kt"
                self.assertIn("Forbidden dependency", architecture.assess(
                    [self.dependency("core", "glass", path)], {})[0])
        # Shared glass controls still participate in the explicitly tracked core/glass debt.
        path = f"app/src/main/java/{architecture.PACKAGE.replace('.', '/')}/core/ui/Sample.kt"
        self.assertEqual([], architecture.assess([self.dependency("core", "glass", path)], {}))

    def test_three_feature_cycle_is_rejected(self):
        dependencies = [self.dependency("feature.one", "feature.two"),
                        self.dependency("feature.two", "feature.three"),
                        self.dependency("feature.three", "feature.one")]
        self.assertEqual(3, len(architecture.cyclic_edges(dependencies)))
        self.assertEqual(3, len(architecture.assess(dependencies, {})))

    def test_acyclic_feature_dependency_is_allowed(self):
        self.assertEqual([], architecture.assess([self.dependency("feature.one", "feature.two")], {}))

    def test_existing_cycle_does_not_allow_a_new_cycle_edge(self):
        old = [self.dependency("feature.one", "feature.two"), self.dependency("feature.two", "feature.one")]
        baseline = {"legacy_cycle_edges": ["feature.one -> feature.two", "feature.two -> feature.one"]}
        self.assertEqual([], architecture.assess(old, baseline))
        new = old + [self.dependency("feature.two", "feature.three"), self.dependency("feature.three", "feature.one")]
        self.assertEqual(2, len(architecture.assess(new, baseline)))

    def test_legacy_reverse_dependency_is_limited_to_the_original_file(self):
        baseline = {"legacy_dependencies": {"old.kt": ["app"]}}
        old = self.dependency("feature.home", "app", "old.kt")
        self.assertEqual([], architecture.assess([old], baseline))
        self.assertIn("new.kt", architecture.assess([old, self.dependency("feature.home", "app", "new.kt")], baseline)[0])

    def test_resolved_debt_must_be_removed_from_baseline(self):
        baseline = {"legacy_dependencies": {"old.kt": ["app"]}, "legacy_cycle_edges": ["core -> glass"]}
        self.assertEqual(2, len(architecture.assess([], baseline)))

    def test_literals_and_nested_comments_are_masked_without_changing_line_numbers(self):
        sample = '\n'.join(['// com.xiaomanjun.sleepdownschedule.app.Fake',
            '/* outer /* nested */ com.xiaomanjun.sleepdownschedule.app.Fake */',
            'val text = "https://example.com/com.xiaomanjun.sleepdownschedule.app.Fake"',
            'val raw = """com.xiaomanjun.sleepdownschedule.feature.home.Fake"""',
            'import com.xiaomanjun.sleepdownschedule.domain.schedule.Real as Alias'])
        code = architecture.code_only(sample)
        matches = list(architecture.REFERENCE.finditer(code))
        self.assertEqual(1, len(matches))
        self.assertEqual(5, code.count('\n', 0, matches[0].start()) + 1)
        self.assertEqual(len(sample), len(code))

    def test_scan_covers_channel_java_and_kotlin_sources_but_excludes_tests(self):
        with TemporaryDirectory(dir=architecture.ROOT) as temporary:
            root = Path(temporary)
            for source_set, language, extension in [("main", "java", "kt"), ("github", "java", "java"),
                                                    ("store", "kotlin", "kt"), ("test", "java", "kt")]:
                path = root / "app/src" / source_set / language / architecture.PACKAGE.replace('.', '/') / "core" / f"Sample.{extension}"
                path.parent.mkdir(parents=True)
                path.write_text('import com.xiaomanjun.sleepdownschedule.feature.home.*\n'
                                'val direct = com.xiaomanjun.sleepdownschedule.app.state.Value\n', encoding="utf-8")
            dependencies, checked = architecture.scan(root)
            self.assertEqual(3, checked)
            self.assertEqual(6, len(dependencies))
            self.assertEqual({"app", "feature.home"}, {item.target for item in dependencies})

if __name__ == '__main__':
    unittest.main()
