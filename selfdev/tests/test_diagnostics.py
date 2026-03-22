"""Tests for AnalyticsPerspective and DebugPerspective from diagnostics.py."""

import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock

import sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from models import OrganismState, Perspective, Priority
from analyzers import GitAnalyzer
from diagnostics import AnalyticsPerspective, DebugPerspective


def _mock_git_analyzer(**overrides):
    """Create a mock GitAnalyzer with sensible defaults."""
    mock = MagicMock(spec=GitAnalyzer)
    mock.get_current_hash.return_value = "abc12345"
    mock.get_recent_commits.return_value = overrides.get("commits", [])
    mock.get_uncommitted_changes.return_value = overrides.get("uncommitted", [])
    mock.get_branch.return_value = "main"
    return mock


class TestAnalyticsPerspective(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.state = OrganismState()

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def _make_analyzer(self, state=None, commits=None):
        s = state or self.state
        analyzer = AnalyticsPerspective(Path(self.tmp_dir), s)
        analyzer.git_analyzer = _mock_git_analyzer(commits=commits or [])
        return analyzer

    def test_get_perspective(self):
        analyzer = self._make_analyzer()
        self.assertEqual(analyzer.get_perspective(), Perspective.ANALYTICS)

    def test_insufficient_history_single_entry(self):
        self.state.fitness_history = [{"overall": 0.5, "generation": 0}]
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        self.assertEqual(fitness, 0.5)
        self.assertEqual(len(prompts), 1)
        self.assertEqual(prompts[0].priority, Priority.INFO)
        self.assertIn("Insufficient", prompts[0].title)

    def test_insufficient_history_empty(self):
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        self.assertEqual(fitness, 0.5)
        self.assertEqual(len(prompts), 1)

    def test_exactly_two_history_entries(self):
        """Edge case: 2 entries passes insufficient-history gate but has no older set for trends."""
        self.state.fitness_history = [
            {"overall": 0.5, "generation": 0},
            {"overall": 0.6, "generation": 1},
        ]
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        # Should not return the "Insufficient history" prompt
        info_prompts = [p for p in prompts if "Insufficient" in p.title]
        self.assertEqual(len(info_prompts), 0)
        # But should also not generate false trend prompts (no older entries)
        trend_prompts = [p for p in prompts
                         if "declining" in p.title.lower() or "positive" in p.title.lower()]
        self.assertEqual(len(trend_prompts), 0)

    def test_six_entries_enables_trend_analysis(self):
        """6 entries: 1 older + 5 recent is the minimum for trend analysis."""
        self.state.fitness_history = [
            {"overall": 0.3, "generation": 0},  # older
        ] + [
            {"overall": 0.8, "generation": i} for i in range(1, 6)  # recent
        ]
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        positive = [p for p in prompts if "positive" in p.title.lower()]
        self.assertGreater(len(positive), 0)

    def test_declining_trend_generates_high_prompt(self):
        self.state.fitness_history = (
            [{"overall": 0.9, "generation": i} for i in range(6)]
            + [{"overall": 0.3, "generation": i} for i in range(6, 11)]
        )
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        declining = [p for p in prompts if "declining" in p.title.lower()]
        self.assertGreater(len(declining), 0)
        self.assertEqual(declining[0].priority, Priority.HIGH)

    def test_improving_trend_generates_info_prompt(self):
        self.state.fitness_history = (
            [{"overall": 0.2, "generation": i} for i in range(6)]
            + [{"overall": 0.9, "generation": i} for i in range(6, 11)]
        )
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        positive = [p for p in prompts if "positive" in p.title.lower()]
        self.assertGreater(len(positive), 0)
        self.assertEqual(positive[0].priority, Priority.INFO)

    def test_neutral_trend_no_trend_prompt(self):
        """Trend between -0.1 and 0.1 should not generate a trend prompt."""
        self.state.fitness_history = (
            [{"overall": 0.5, "generation": i} for i in range(6)]
            + [{"overall": 0.55, "generation": i} for i in range(6, 11)]
        )
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        trend_prompts = [p for p in prompts
                         if "declining" in p.title.lower() or "positive" in p.title.lower()]
        self.assertEqual(len(trend_prompts), 0)

    def test_high_fix_rate_detected(self):
        self.state.fitness_history = [
            {"overall": 0.5, "generation": i} for i in range(3)
        ]
        commits = [
            {"hash": f"fix{i}", "message": f"fix: issue {i}", "date": "2026-01-01"}
            for i in range(7)
        ] + [
            {"hash": "feat0", "message": "add feature", "date": "2026-01-01"},
        ]
        analyzer = self._make_analyzer(commits=commits)
        fitness, prompts = analyzer.analyze()
        fix_prompts = [p for p in prompts if "fix rate" in p.title.lower()]
        self.assertGreater(len(fix_prompts), 0)
        self.assertEqual(fix_prompts[0].priority, Priority.MEDIUM)

    def test_low_fix_rate_no_prompt(self):
        self.state.fitness_history = [
            {"overall": 0.5, "generation": i} for i in range(3)
        ]
        commits = [
            {"hash": f"feat{i}", "message": f"add feature {i}", "date": "2026-01-01"}
            for i in range(8)
        ] + [
            {"hash": "fix0", "message": "fix typo", "date": "2026-01-01"},
        ]
        analyzer = self._make_analyzer(commits=commits)
        fitness, prompts = analyzer.analyze()
        fix_prompts = [p for p in prompts if "fix rate" in p.title.lower()]
        self.assertEqual(len(fix_prompts), 0)

    def test_no_commits_no_fix_rate_prompt(self):
        self.state.fitness_history = [
            {"overall": 0.5, "generation": i} for i in range(3)
        ]
        analyzer = self._make_analyzer(commits=[])
        fitness, prompts = analyzer.analyze()
        fix_prompts = [p for p in prompts if "fix rate" in p.title.lower()]
        self.assertEqual(len(fix_prompts), 0)

    def test_fitness_is_1_0_with_no_issues(self):
        """Analytics fitness is 1.0 when history is sufficient and no issues."""
        self.state.fitness_history = [
            {"overall": 0.5, "generation": i} for i in range(3)
        ]
        analyzer = self._make_analyzer()
        fitness, _ = analyzer.analyze()
        self.assertEqual(fitness, 1.0)

    def test_fitness_reduced_by_declining_trend(self):
        """Declining trend (HIGH priority) reduces fitness by 0.3."""
        self.state.fitness_history = (
            [{"overall": 0.9, "generation": i} for i in range(6)]
            + [{"overall": 0.3, "generation": i} for i in range(6, 11)]
        )
        analyzer = self._make_analyzer()
        fitness, _ = analyzer.analyze()
        self.assertEqual(fitness, 0.7)

    def test_fitness_reduced_by_high_fix_rate(self):
        """High fix rate (MEDIUM priority) reduces fitness by 0.15."""
        self.state.fitness_history = [
            {"overall": 0.5, "generation": i} for i in range(3)
        ]
        commits = [
            {"hash": f"fix{i}", "message": f"fix: issue {i}", "date": "2026-01-01"}
            for i in range(7)
        ] + [
            {"hash": "feat0", "message": "add feature", "date": "2026-01-01"},
        ]
        analyzer = self._make_analyzer(commits=commits)
        fitness, _ = analyzer.analyze()
        self.assertEqual(fitness, 0.85)

    def test_fitness_reduced_by_both_issues(self):
        """Both declining trend and high fix rate reduce fitness cumulatively."""
        self.state.fitness_history = (
            [{"overall": 0.9, "generation": i} for i in range(6)]
            + [{"overall": 0.3, "generation": i} for i in range(6, 11)]
        )
        commits = [
            {"hash": f"fix{i}", "message": f"fix: issue {i}", "date": "2026-01-01"}
            for i in range(7)
        ] + [
            {"hash": "feat0", "message": "add feature", "date": "2026-01-01"},
        ]
        analyzer = self._make_analyzer(commits=commits)
        fitness, _ = analyzer.analyze()
        self.assertAlmostEqual(fitness, 0.55)

    def test_fitness_is_0_5_without_history(self):
        """Analytics fitness is 0.5 when history is insufficient (early return)."""
        analyzer = self._make_analyzer()
        fitness, _ = analyzer.analyze()
        self.assertEqual(fitness, 0.5)

    def test_missing_overall_key_defaults_to_0_5(self):
        """History entries without 'overall' key should default to 0.5."""
        self.state.fitness_history = (
            [{"generation": i} for i in range(6)]
            + [{"generation": i} for i in range(6, 11)]
        )
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        # All entries default to 0.5, so trend should be ~0 (neutral)
        trend_prompts = [p for p in prompts
                         if "declining" in p.title.lower() or "positive" in p.title.lower()]
        self.assertEqual(len(trend_prompts), 0)


class TestDebugPerspective(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.state = OrganismState()

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def _make_analyzer(self, uncommitted=None):
        analyzer = DebugPerspective(Path(self.tmp_dir), self.state)
        analyzer.git_analyzer = _mock_git_analyzer(uncommitted=uncommitted or [])
        return analyzer

    def test_get_perspective(self):
        analyzer = self._make_analyzer()
        self.assertEqual(analyzer.get_perspective(), Perspective.DEBUG)

    def test_clean_codebase_high_fitness(self):
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        self.assertEqual(fitness, 1.0)
        self.assertEqual(len(prompts), 0)

    def test_uncommitted_changes_prompt(self):
        analyzer = self._make_analyzer(uncommitted=["M file.py", "?? new.py"])
        fitness, prompts = analyzer.analyze()
        uncommitted = [p for p in prompts if "uncommitted" in p.title.lower()]
        self.assertGreater(len(uncommitted), 0)
        self.assertEqual(uncommitted[0].priority, Priority.MEDIUM)
        self.assertIn("2 files", uncommitted[0].description)

    def test_todo_comments_detected(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "module.py").write_text("# TODO: implement this\ndef func(): pass\n")
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        todo_prompts = [p for p in prompts if "todo" in p.tags]
        self.assertGreater(len(todo_prompts), 0)
        self.assertEqual(todo_prompts[0].priority, Priority.MEDIUM)

    def test_fixme_gets_high_priority(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "module.py").write_text("# FIXME: critical bug here\ndef func(): pass\n")
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        fixme_prompts = [p for p in prompts if "fixme" in p.tags]
        self.assertGreater(len(fixme_prompts), 0)
        self.assertEqual(fixme_prompts[0].priority, Priority.HIGH)

    def test_bug_gets_high_priority(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "module.py").write_text("# BUG: race condition\ndef func(): pass\n")
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        bug_prompts = [p for p in prompts if "bug" in p.tags]
        self.assertGreater(len(bug_prompts), 0)
        self.assertEqual(bug_prompts[0].priority, Priority.HIGH)

    def test_xxx_and_hack_detected(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "module.py").write_text(
            "# XXX: questionable approach\n# HACK: temporary workaround\n"
        )
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        tags = {tag for p in prompts for tag in p.tags}
        self.assertIn("xxx", tags)
        self.assertIn("hack", tags)

    def test_max_10_todo_prompts(self):
        """_generate_todo_prompts limits output to 10 items."""
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        lines = [f"# TODO: item {i}" for i in range(15)]
        (src / "module.py").write_text("\n".join(lines))
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        todo_prompts = [p for p in prompts if "todo" in p.tags]
        self.assertLessEqual(len(todo_prompts), 10)

    def test_pycache_excluded_from_todo_scan(self):
        src = Path(self.tmp_dir) / "src"
        cache = src / "__pycache__"
        cache.mkdir(parents=True)
        (cache / "module.cpython-311.py").write_text("# TODO: should be ignored\n")
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        todo_prompts = [p for p in prompts if "todo" in p.tags]
        self.assertEqual(len(todo_prompts), 0)

    def test_nonexistent_analyzable_dir_no_error(self):
        """Scanning directories that don't exist should not raise."""
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        self.assertIsInstance(prompts, list)

    def test_fitness_floor_at_0_1(self):
        """Fitness should never go below 0.1 regardless of issue count."""
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        lines = [f"# TODO: item {i}" for i in range(30)]
        (src / "module.py").write_text("\n".join(lines))
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        self.assertGreaterEqual(fitness, 0.1)

    def test_todo_file_location_in_prompt(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "handler.py").write_text("# TODO: refactor this handler\n")
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        todo_prompts = [p for p in prompts if "todo" in p.tags]
        self.assertGreater(len(todo_prompts), 0)
        self.assertIn("handler.py", todo_prompts[0].file_path)
        self.assertEqual(todo_prompts[0].line_number, 1)

    def test_todo_with_colon_separator(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "module.py").write_text("# TODO: implement feature X\n")
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        todo_prompts = [p for p in prompts if "todo" in p.tags]
        self.assertGreater(len(todo_prompts), 0)
        self.assertIn("implement feature X", todo_prompts[0].title)

    def test_todo_with_space_separator(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "module.py").write_text("# TODO implement feature Y\n")
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        todo_prompts = [p for p in prompts if "todo" in p.tags]
        self.assertGreater(len(todo_prompts), 0)

    def test_case_insensitive_todo_detection(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "module.py").write_text("# todo: lowercase todo\n# Todo: mixed case\n")
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        todo_prompts = [p for p in prompts if "todo" in p.tags]
        self.assertEqual(len(todo_prompts), 2)

    def test_multiple_files_with_todos(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "a.py").write_text("# TODO: fix A\n")
        (src / "b.py").write_text("# TODO: fix B\n")
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        todo_prompts = [p for p in prompts if "todo" in p.tags]
        self.assertEqual(len(todo_prompts), 2)
        files = {p.file_path for p in todo_prompts}
        self.assertEqual(len(files), 2)

    def test_combined_issues_reduce_fitness(self):
        """Both TODOs and uncommitted changes should lower fitness."""
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "module.py").write_text("# TODO: fix this\n# FIXME: broken\n")
        analyzer = self._make_analyzer(uncommitted=["M file.py"])
        fitness, prompts = analyzer.analyze()
        self.assertLess(fitness, 1.0)
        self.assertGreater(len(prompts), 1)

    def test_code_quality_issues_generate_prompts(self):
        """Long files and high complexity should produce debug prompts."""
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "big.py").write_text("\n".join(["x = 1"] * 400))
        analyzer = self._make_analyzer()
        fitness, prompts = analyzer.analyze()
        code_prompts = [p for p in prompts if "code-quality" in p.tags]
        self.assertGreater(len(code_prompts), 0)
        self.assertIn("too long", code_prompts[0].description.lower())


if __name__ == "__main__":
    unittest.main()
