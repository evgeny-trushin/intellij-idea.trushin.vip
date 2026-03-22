"""Tests for all perspective analyzers."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from models import OrganismState, Perspective, Priority
from analyzers import GitAnalyzer
from perspectives import TestPerspective, SystemPerspective
from user_perspective import UserPerspective
from diagnostics import AnalyticsPerspective, DebugPerspective


def _mock_git_analyzer():
    mock = MagicMock(spec=GitAnalyzer)
    mock.get_current_hash.return_value = "abc12345"
    mock.get_recent_commits.return_value = [
        {"hash": "abc12345", "message": "Initial commit", "date": "2026-01-01"}
    ]
    mock.get_uncommitted_changes.return_value = []
    mock.get_branch.return_value = "main"
    return mock


class TestUserPerspective(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.state = OrganismState()

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_no_readme_critical_prompt(self):
        analyzer = UserPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        critical = [p for p in prompts if p.priority == Priority.CRITICAL]
        self.assertGreater(len(critical), 0)
        self.assertIn("README", critical[0].title)

    def test_with_readme(self):
        (Path(self.tmp_dir) / "README.md").write_text("# Project\n" + "x" * 5000)
        analyzer = UserPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        critical = [p for p in prompts if p.priority == Priority.CRITICAL]
        self.assertEqual(len(critical), 0)
        self.assertGreater(fitness, 0.5)

    def test_short_readme_high_prompt(self):
        (Path(self.tmp_dir) / "README.md").write_text("# Hi")
        analyzer = UserPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        high = [p for p in prompts if p.priority == Priority.HIGH]
        self.assertGreater(len(high), 0)

    def test_package_json_with_description(self):
        (Path(self.tmp_dir) / "README.md").write_text("# Project\n" + "x" * 5000)
        (Path(self.tmp_dir) / "package.json").write_text(
            json.dumps({"name": "test", "description": "A test project"})
        )
        analyzer = UserPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        self.assertGreater(fitness, 0.5)

    def test_package_json_without_description(self):
        (Path(self.tmp_dir) / "README.md").write_text("# Project\n" + "x" * 5000)
        (Path(self.tmp_dir) / "package.json").write_text(
            json.dumps({"name": "test"})
        )
        analyzer = UserPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        medium = [p for p in prompts if p.priority == Priority.MEDIUM]
        self.assertGreater(len(medium), 0)

    def test_increment_tracker_replaces_requirements_parsing(self):
        """User perspective should no longer parse requirements.md."""
        (Path(self.tmp_dir) / "README.md").write_text("# Project\n" + "x" * 5000)
        req_content = "# Reqs\n### R1: First\nDo something.\n### R2: Second\nDo more.\n"
        (Path(self.tmp_dir) / "requirements.md").write_text(req_content)
        analyzer = UserPerspective(Path(self.tmp_dir), self.state)
        _, prompts = analyzer.analyze()
        # No requirement-tagged prompts should appear since requirements parsing
        # has been moved to the increment tracker
        req_prompts = [p for p in prompts if p.tags and p.tags[0] in ["R1", "R2"]]
        self.assertEqual(len(req_prompts), 0)

    def test_user_perspective_without_requirements_md(self):
        """User perspective should work fine without requirements.md."""
        (Path(self.tmp_dir) / "README.md").write_text("# Project\n" + "x" * 5000)
        analyzer = UserPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        self.assertIsInstance(fitness, float)
        self.assertIsInstance(prompts, list)


class TestTestPerspective(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.state = OrganismState()

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_no_test_dir_critical(self):
        analyzer = TestPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        self.assertEqual(fitness, 0.0)
        critical = [p for p in prompts if p.priority == Priority.CRITICAL]
        self.assertGreater(len(critical), 0)

    def test_nested_test_dir_found(self):
        """Test dirs inside sub-directories should be detected."""
        subpkg = Path(self.tmp_dir) / "mypackage"
        subpkg.mkdir()
        tests_dir = subpkg / "tests"
        tests_dir.mkdir()
        (tests_dir / "test_core.py").write_text("def test_core(): pass")
        analyzer = TestPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        critical = [p for p in prompts if p.priority == Priority.CRITICAL
                    and "test directory" in p.title.lower()]
        self.assertEqual(len(critical), 0, "Should NOT ask to create test dir when nested tests/ exists")

    def test_with_test_dir(self):
        (Path(self.tmp_dir) / "tests").mkdir()
        (Path(self.tmp_dir) / "tests" / "test_a.py").write_text("def test_a(): pass")
        analyzer = TestPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        self.assertGreater(fitness, 0.0)

    def test_with_source_and_test_files(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "module.py").write_text("def func(): pass")
        tests = Path(self.tmp_dir) / "tests"
        tests.mkdir()
        (tests / "test_module.py").write_text("def test_func(): pass")
        analyzer = TestPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        self.assertGreater(fitness, 0.0)

    def test_low_coverage_prompt(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        for i in range(5):
            (src / f"module_{i}.py").write_text(f"def func_{i}(): pass")
        tests = Path(self.tmp_dir) / "tests"
        tests.mkdir()
        (tests / "test_one.py").write_text("def test_one(): pass")
        analyzer = TestPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        high = [p for p in prompts if p.priority == Priority.HIGH]
        self.assertGreater(len(high), 0)


class TestSystemPerspective(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.state = OrganismState()

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_no_files_info_prompt(self):
        analyzer = SystemPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        self.assertEqual(fitness, 0.5)
        self.assertEqual(len(prompts), 1)
        self.assertEqual(prompts[0].priority, Priority.INFO)

    def test_with_source_files(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "module.py").write_text("def func(): pass")
        analyzer = SystemPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        self.assertGreater(fitness, 0.0)

    def test_long_file_refactor_prompt(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "big.py").write_text("\n".join(["x = 1"] * 400))
        analyzer = SystemPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        refactor = [p for p in prompts if "Refactor" in p.title]
        self.assertGreater(len(refactor), 0)

    def test_high_complexity_prompt(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        lines = ["def func():"]
        for i in range(15):
            lines.append(f"    if x == {i}:")
            lines.append(f"        return {i}")
        (src / "complex.py").write_text("\n".join(lines))
        analyzer = SystemPerspective(Path(self.tmp_dir), self.state)
        fitness, prompts = analyzer.analyze()
        complexity_prompts = [p for p in prompts if "complexity" in p.title.lower()]
        self.assertGreater(len(complexity_prompts), 0)


class TestAnalyticsPerspective(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_insufficient_history(self):
        state = OrganismState()
        analyzer = AnalyticsPerspective(Path(self.tmp_dir), state)
        analyzer.git_analyzer = _mock_git_analyzer()
        fitness, prompts = analyzer.analyze()
        self.assertEqual(fitness, 0.5)
        self.assertEqual(prompts[0].priority, Priority.INFO)

    def test_with_history_declining_trend(self):
        state = OrganismState()
        state.fitness_history = [
            {"overall": 0.8, "generation": i} for i in range(6)
        ] + [
            {"overall": 0.4, "generation": i} for i in range(6, 11)
        ]
        analyzer = AnalyticsPerspective(Path(self.tmp_dir), state)
        analyzer.git_analyzer = _mock_git_analyzer()
        fitness, prompts = analyzer.analyze()
        declining = [p for p in prompts if "declining" in p.title.lower()]
        self.assertGreater(len(declining), 0)

    def test_with_history_improving_trend(self):
        state = OrganismState()
        state.fitness_history = [
            {"overall": 0.3, "generation": i} for i in range(6)
        ] + [
            {"overall": 0.8, "generation": i} for i in range(6, 11)
        ]
        analyzer = AnalyticsPerspective(Path(self.tmp_dir), state)
        analyzer.git_analyzer = _mock_git_analyzer()
        fitness, prompts = analyzer.analyze()
        positive = [p for p in prompts if "positive" in p.title.lower() or "Positive" in p.title]
        self.assertGreater(len(positive), 0)

    def test_high_fix_rate_prompt(self):
        state = OrganismState()
        state.fitness_history = [
            {"overall": 0.5, "generation": i} for i in range(3)
        ]
        mock_git = _mock_git_analyzer()
        mock_git.get_recent_commits.return_value = [
            {"hash": f"abc{i}", "message": f"fix bug {i}", "date": "2026-01-01"}
            for i in range(8)
        ] + [
            {"hash": "def0", "message": "add feature", "date": "2026-01-01"},
            {"hash": "def1", "message": "update docs", "date": "2026-01-01"},
        ]
        analyzer = AnalyticsPerspective(Path(self.tmp_dir), state)
        analyzer.git_analyzer = mock_git
        fitness, prompts = analyzer.analyze()
        fix_rate = [p for p in prompts if "fix rate" in p.title.lower() or "bug fix" in p.title.lower()]
        self.assertGreater(len(fix_rate), 0)


class TestDebugPerspective(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.state = OrganismState()

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_clean_debug(self):
        analyzer = DebugPerspective(Path(self.tmp_dir), self.state)
        analyzer.git_analyzer = _mock_git_analyzer()
        fitness, prompts = analyzer.analyze()
        self.assertGreater(fitness, 0.5)

    def test_uncommitted_changes(self):
        mock_git = _mock_git_analyzer()
        mock_git.get_uncommitted_changes.return_value = [" M file.py", "?? new.py"]
        analyzer = DebugPerspective(Path(self.tmp_dir), self.state)
        analyzer.git_analyzer = mock_git
        fitness, prompts = analyzer.analyze()
        uncommitted = [p for p in prompts if "uncommitted" in p.title.lower() or "Uncommitted" in p.title]
        self.assertGreater(len(uncommitted), 0)

    def test_todo_detection(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        (src / "module.py").write_text("# TODO: Fix this\ndef func(): pass\n# FIXME: broken")
        analyzer = DebugPerspective(Path(self.tmp_dir), self.state)
        analyzer.git_analyzer = _mock_git_analyzer()
        fitness, prompts = analyzer.analyze()
        todo_prompts = [p for p in prompts if any(t in p.tags for t in ["todo", "fixme"])]
        self.assertGreater(len(todo_prompts), 0)

    def test_fitness_decreases_with_issues(self):
        src = Path(self.tmp_dir) / "src"
        src.mkdir()
        todos = "\n".join([f"# TODO: item {i}" for i in range(15)])
        (src / "module.py").write_text(todos)
        analyzer = DebugPerspective(Path(self.tmp_dir), self.state)
        analyzer.git_analyzer = _mock_git_analyzer()
        fitness, prompts = analyzer.analyze()
        self.assertLess(fitness, 0.5)


if __name__ == "__main__":
    unittest.main()
