"""Tests for SelfDevelopmentOrganism orchestrator and CLI entry point."""

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from models import OrganismState, Perspective
from analyzers import GitAnalyzer
from organism import SelfDevelopmentOrganism


def _mock_git_analyzer():
    """Create a mock GitAnalyzer that doesn't require a real git repo."""
    mock = MagicMock(spec=GitAnalyzer)
    mock.get_current_hash.return_value = "abc12345"
    mock.get_recent_commits.return_value = [
        {"hash": "abc12345", "message": "Initial commit", "date": "2026-01-01"}
    ]
    mock.get_uncommitted_changes.return_value = []
    mock.get_branch.return_value = "main"
    mock.get_changed_files_in_last_commit.return_value = ["selfdev/organism.py"]
    return mock


def _patch_tests_pass():
    """Return a patcher that makes _run_tests always succeed."""
    return patch.object(SelfDevelopmentOrganism, "_run_tests",
                        return_value=(True, "all passed"))


class TestSelfDevelopmentOrganism(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()

    def tearDown(self):
        import shutil
        shutil.rmtree(self.tmp_dir)

    def test_organism_initialization(self):
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        self.assertEqual(len(organism.perspectives), 5)

    def test_run_single_perspective(self):
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        prompts = organism.run_perspective(Perspective.USER)
        self.assertIsInstance(prompts, list)

    def test_run_all_perspectives(self):
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        mock_git = _mock_git_analyzer()
        for p in organism.perspectives.values():
            p.git_analyzer = mock_git
        all_prompts = organism.run_all_perspectives()
        self.assertIsInstance(all_prompts, list)
        self.assertIn("user", organism.state.fitness_scores)
        self.assertIn("test", organism.state.fitness_scores)

    def test_advance_generation(self):
        # Create increment files for the tracker to find
        req_dir = Path(self.tmp_dir) / "requirements"
        req_dir.mkdir()
        (req_dir / "increment_0001_todo_test.md").write_text(
            "# Increment 0001: Test\n\n**Requirement ID:** R1\n**Status:** TODO\n\n"
            "## Description\nTest increment.\n\n## Acceptance Criteria\n- [ ] Done\n"
        )
        prin_dir = Path(self.tmp_dir) / "principles"
        prin_dir.mkdir()
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        organism.state = OrganismState()
        organism.state.fitness_scores = {"user": 0.5, "test": 0.3}
        mock_git = _mock_git_analyzer()
        for p in organism.perspectives.values():
            p.git_analyzer = mock_git
        initial_gen = organism.state.generation
        with _patch_tests_pass():
            organism.advance_generation()
        self.assertEqual(organism.state.generation, initial_gen + 1)
        self.assertEqual(len(organism.state.fitness_history), 1)
        # Verify the file was renamed to done
        self.assertTrue((req_dir / "increment_0001_done_test.md").exists())
        self.assertFalse((req_dir / "increment_0001_todo_test.md").exists())

    def test_print_state(self):
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        organism.state.fitness_scores = {"user": 0.7}
        organism.print_state()

    def test_perspectives_sorted_by_priority(self):
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        prompts = organism.run_perspective(Perspective.USER)
        if len(prompts) > 1:
            for i in range(len(prompts) - 1):
                self.assertLessEqual(prompts[i].priority.value, prompts[i + 1].priority.value)


    def test_run_perspective_stores_fitness_score(self):
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        mock_git = _mock_git_analyzer()
        for p in organism.perspectives.values():
            p.git_analyzer = mock_git
        organism.run_perspective(Perspective.DEBUG)
        self.assertIn("debug", organism.state.fitness_scores)
        self.assertIsInstance(organism.state.fitness_scores["debug"], float)

    def test_run_perspective_no_prompts(self):
        """Debug perspective on empty dir should print 'No issues found'."""
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        mock_git = _mock_git_analyzer()
        for p in organism.perspectives.values():
            p.git_analyzer = mock_git
        prompts = organism.run_perspective(Perspective.DEBUG)
        self.assertEqual(len(prompts), 0)

    @patch("organism.GitAnalyzer")
    def test_advance_generation_records_git_hash(self, mock_git_cls):
        mock_git_cls.return_value = _mock_git_analyzer()
        req_dir = Path(self.tmp_dir) / "requirements"
        req_dir.mkdir()
        (req_dir / "increment_0001_todo_test.md").write_text(
            "# Increment 0001: Test\n\n**Requirement ID:** R1\n**Status:** TODO\n\n"
            "## Description\nTest.\n\n## Acceptance Criteria\n- [ ] Done\n"
        )
        (Path(self.tmp_dir) / "principles").mkdir()
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        organism.state = OrganismState()
        with _patch_tests_pass():
            organism.advance_generation()
        self.assertEqual(organism.state.last_git_hash, "abc12345")

    def test_advance_generation_updates_stage(self):
        req_dir = Path(self.tmp_dir) / "requirements"
        req_dir.mkdir()
        (req_dir / "increment_0001_todo_test.md").write_text(
            "# Increment 0001: Test\n\n**Requirement ID:** R1\n**Status:** TODO\n\n"
            "## Description\nTest.\n\n## Acceptance Criteria\n- [ ] Done\n"
        )
        (Path(self.tmp_dir) / "principles").mkdir()
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        organism.state = OrganismState(generation=3)
        mock_git = _mock_git_analyzer()
        for p in organism.perspectives.values():
            p.git_analyzer = mock_git
        with _patch_tests_pass():
            organism.advance_generation()
        # Generation 4 = growth stage
        self.assertEqual(organism.state.development_stage, "growth")

    def test_advance_generation_stores_overall_key(self):
        """advance_generation should store an 'overall' key in fitness_history."""
        req_dir = Path(self.tmp_dir) / "requirements"
        req_dir.mkdir()
        (req_dir / "increment_0001_todo_test.md").write_text(
            "# Increment 0001: Test\n\n**Requirement ID:** R1\n**Status:** TODO\n\n"
            "## Description\nTest.\n\n## Acceptance Criteria\n- [ ] Done\n"
        )
        (Path(self.tmp_dir) / "principles").mkdir()
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        organism.state = OrganismState()
        organism.state.fitness_scores = {"user": 0.8, "test": 0.6}
        mock_git = _mock_git_analyzer()
        for p in organism.perspectives.values():
            p.git_analyzer = mock_git
        with _patch_tests_pass():
            organism.advance_generation()
        entry = organism.state.fitness_history[-1]
        self.assertIn("overall", entry)
        self.assertAlmostEqual(entry["overall"], 0.7)

    def test_advance_generation_with_no_increments(self):
        """advance_generation with no increment files prints completion message."""
        (Path(self.tmp_dir) / "requirements").mkdir()
        (Path(self.tmp_dir) / "principles").mkdir()
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        organism.state = OrganismState()
        mock_git = _mock_git_analyzer()
        for p in organism.perspectives.values():
            p.git_analyzer = mock_git
        # Should not raise, just print "all done"
        organism.advance_generation()

    def test_run_perspective_returns_sorted_prompts(self):
        """run_perspective should return prompts sorted by priority."""
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        prompts = organism.run_perspective(Perspective.USER)
        if len(prompts) > 1:
            for i in range(len(prompts) - 1):
                self.assertLessEqual(prompts[i].priority.value, prompts[i + 1].priority.value)

    def test_print_state_no_fitness(self):
        """Print state when no fitness scores exist."""
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        organism.state = OrganismState()
        organism.print_state()

    def test_all_five_perspectives_registered(self):
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        expected = {Perspective.USER, Perspective.TEST, Perspective.SYSTEM,
                    Perspective.ANALYTICS, Perspective.DEBUG}
        self.assertEqual(set(organism.perspectives.keys()), expected)

    def test_advance_blocked_when_tests_fail(self):
        """advance_generation must not advance when tests fail."""
        req_dir = Path(self.tmp_dir) / "requirements"
        req_dir.mkdir()
        (req_dir / "increment_0001_todo_test.md").write_text(
            "# Increment 0001: Test\n\n**Requirement ID:** R1\n**Status:** TODO\n\n"
            "## Description\nTest.\n\n## Acceptance Criteria\n- [ ] Done\n"
        )
        (Path(self.tmp_dir) / "principles").mkdir()
        organism = SelfDevelopmentOrganism(root_dir=Path(self.tmp_dir))
        organism.state = OrganismState()
        with patch.object(SelfDevelopmentOrganism, "_run_tests",
                          return_value=(False, "FAILED test_foo")):
            organism.advance_generation()
        # Generation should NOT have advanced
        self.assertEqual(organism.state.generation, 0)
        # File should still be todo
        self.assertTrue((req_dir / "increment_0001_todo_test.md").exists())


class TestCLI(unittest.TestCase):

    def test_self_flag(self):
        result = subprocess.run(
            [sys.executable, str(Path(__file__).resolve().parent.parent / "organism.py"),
             "--self", "--state"],
            capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0)
        self.assertIn("ORGANISM STATE", result.stdout)

    def test_help_flag(self):
        result = subprocess.run(
            [sys.executable, str(Path(__file__).resolve().parent.parent / "organism.py"),
             "--help"],
            capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0)
        self.assertIn("Self-Development System", result.stdout)

    def test_root_flag(self):
        with tempfile.TemporaryDirectory() as d:
            result = subprocess.run(
                [sys.executable, str(Path(__file__).resolve().parent.parent / "organism.py"),
                 "--root", d, "--state"],
                capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 0)

    def test_self_analysis_runs(self):
        result = subprocess.run(
            [sys.executable, str(Path(__file__).resolve().parent.parent / "organism.py"),
             "--self", "--user"],
            capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0)
        self.assertIn("USER", result.stdout)


    def test_multiple_perspectives_cli(self):
        """Running with two perspective flags shows both."""
        result = subprocess.run(
            [sys.executable, str(Path(__file__).resolve().parent.parent / "organism.py"),
             "--self", "--user", "--test"],
            capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0)
        self.assertIn("USER", result.stdout)
        self.assertIn("TEST", result.stdout)
        self.assertIn("SUMMARY", result.stdout)

    def test_single_perspective_no_summary(self):
        """Running with one perspective flag should not show summary."""
        result = subprocess.run(
            [sys.executable, str(Path(__file__).resolve().parent.parent / "organism.py"),
             "--self", "--debug"],
            capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0)
        self.assertIn("DEBUG", result.stdout)
        self.assertNotIn("SUMMARY", result.stdout)

    def test_all_flag_shows_all_perspectives(self):
        """--all flag should show all 6 perspectives."""
        with tempfile.TemporaryDirectory() as d:
            result = subprocess.run(
                [sys.executable, str(Path(__file__).resolve().parent.parent / "organism.py"),
                 "--root", d, "--all"],
                capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 0)
            for name in ["USER", "TEST", "SYSTEM", "ANALYTICS", "DEBUG"]:
                self.assertIn(name, result.stdout)
            self.assertIn("SUMMARY", result.stdout)


if __name__ == "__main__":
    unittest.main()
