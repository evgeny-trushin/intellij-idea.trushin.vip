"""Tests for --revert, --revert_from, and --redo functionality."""

import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from analyzers import GitAnalyzer
from increment_tracker import IncrementTracker


def _make_increment_file(req_dir: Path, number: int, status: str = "todo",
                         short_desc: str = "test_feature") -> Path:
    """Create a minimal increment markdown file and return its path."""
    filename = f"increment_{number:04d}_{status}_{short_desc}.md"
    path = req_dir / filename
    content = textwrap.dedent(f"""\
        # Increment {number:04d}: Test Feature {number}

        **Requirement ID:** R{number}

        ## Description
        Implement test feature {number}.

        ## Acceptance Criteria
        - [ ] Feature {number} works correctly
        - [ ] Tests pass

        ## Related Principles
        - [B1](../principles/B1.md)
    """)
    path.write_text(content, encoding="utf-8")
    return path


class TestIncrementTrackerRevert(unittest.TestCase):
    """Tests for IncrementTracker.format_revert_prompt."""

    def setUp(self):
        self.tmp_dir = Path(tempfile.mkdtemp())
        self.req_dir = self.tmp_dir / "requirements"
        self.req_dir.mkdir()
        self.prin_dir = self.tmp_dir / "principles"
        self.prin_dir.mkdir()
        (self.prin_dir / "B1.md").write_text("# B1 — Principle\nContent.")
        self.tracker = IncrementTracker(self.tmp_dir)

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    @patch.object(GitAnalyzer, "get_commits_for_increment")
    @patch.object(GitAnalyzer, "get_diff_for_commit")
    def test_revert_prompt_with_commits(self, mock_diff, mock_commits):
        """--revert=0001 generates a prompt listing related git commits."""
        _make_increment_file(self.req_dir, 1, status="done")

        mock_commits.return_value = [
            {"hash": "aaa11111", "message": "INCREMENT 0001: Test Feature 1",
             "date": "2026-01-01"},
        ]
        mock_diff.return_value = " file1.py | 10 ++++\n 1 file changed"

        output = self.tracker.format_revert_prompt(1)

        self.assertIn("REVERT INCREMENT 0001", output)
        self.assertIn("aaa11111", output)
        self.assertIn("INCREMENT 0001", output)
        self.assertIn("git revert --no-commit", output)
        self.assertIn("REVERT INCREMENT 0001", output)

    @patch.object(GitAnalyzer, "get_commits_for_increment")
    @patch.object(GitAnalyzer, "get_diff_for_commit")
    def test_revert_prompt_no_commits(self, mock_diff, mock_commits):
        """--revert produces helpful output even when no commits are found."""
        _make_increment_file(self.req_dir, 5)
        mock_commits.return_value = []

        output = self.tracker.format_revert_prompt(5)

        self.assertIn("REVERT INCREMENT 0005", output)
        self.assertIn("No commits found", output)
        self.assertIn("Search manually", output)

    @patch.object(GitAnalyzer, "get_commits_for_increment")
    @patch.object(GitAnalyzer, "get_diff_for_commit")
    def test_revert_prompt_done_file_rename_hint(self, mock_diff, mock_commits):
        """Revert prompt for a done increment suggests renaming back to todo."""
        _make_increment_file(self.req_dir, 2, status="done")
        mock_commits.return_value = [
            {"hash": "bbb22222", "message": "INCREMENT 0002: done",
             "date": "2026-01-02"},
        ]
        mock_diff.return_value = ""

        output = self.tracker.format_revert_prompt(2)

        self.assertIn("POST-REVERT", output)
        self.assertIn("_todo_", output)

    @patch.object(GitAnalyzer, "get_commits_for_increment")
    @patch.object(GitAnalyzer, "get_diff_for_commit")
    def test_revert_prompt_multiple_commits(self, mock_diff, mock_commits):
        """Revert prompt lists multiple commits in order."""
        _make_increment_file(self.req_dir, 3, status="done")
        mock_commits.return_value = [
            {"hash": "ccc33333", "message": "INCREMENT 0003: part 2",
             "date": "2026-01-03"},
            {"hash": "ccc33331", "message": "INCREMENT 0003: part 1",
             "date": "2026-01-02"},
        ]
        mock_diff.return_value = ""

        output = self.tracker.format_revert_prompt(3)

        self.assertIn("ccc33333", output)
        self.assertIn("ccc33331", output)
        # Each commit should have a revert line
        self.assertEqual(output.count("git revert --no-commit"), 2)


class TestIncrementTrackerRevertFrom(unittest.TestCase):
    """Tests for IncrementTracker.format_revert_from_prompt."""

    def setUp(self):
        self.tmp_dir = Path(tempfile.mkdtemp())
        self.req_dir = self.tmp_dir / "requirements"
        self.req_dir.mkdir()
        self.prin_dir = self.tmp_dir / "principles"
        self.prin_dir.mkdir()
        self.tracker = IncrementTracker(self.tmp_dir)

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    @patch.object(GitAnalyzer, "get_commits_for_increment")
    def test_revert_from_to_current_todo(self, mock_commits):
        """--revert_from=0005 with current todo at 0003 covers 0005→0003."""
        # Increments 1-2 done, 3-5 todo
        _make_increment_file(self.req_dir, 1, status="done")
        _make_increment_file(self.req_dir, 2, status="done")
        _make_increment_file(self.req_dir, 3, status="todo")
        _make_increment_file(self.req_dir, 4, status="todo")
        _make_increment_file(self.req_dir, 5, status="todo")

        mock_commits.return_value = []

        output = self.tracker.format_revert_from_prompt(5)

        # Should revert 5, 4, 3
        self.assertIn("REVERT INCREMENTS 0005", output)
        self.assertIn("0003", output)
        self.assertIn("INCREMENT 0005", output)
        self.assertIn("INCREMENT 0004", output)
        self.assertIn("INCREMENT 0003", output)

    @patch.object(GitAnalyzer, "get_commits_for_increment")
    def test_revert_from_lists_commits_per_increment(self, mock_commits):
        """Each increment in range shows its own commits."""
        _make_increment_file(self.req_dir, 1, status="todo")
        _make_increment_file(self.req_dir, 2, status="todo")

        def side_effect(num):
            if num == 2:
                return [{"hash": "ddd44444", "message": "INCREMENT 0002: feat",
                         "date": "2026-01-01"}]
            return []

        mock_commits.side_effect = side_effect

        output = self.tracker.format_revert_from_prompt(2)

        self.assertIn("ddd44444", output)
        self.assertIn("(no commits found)", output)

    @patch.object(GitAnalyzer, "get_commits_for_increment")
    def test_revert_from_all_done(self, mock_commits):
        """When all increments are done, revert_from still works."""
        _make_increment_file(self.req_dir, 1, status="done")
        _make_increment_file(self.req_dir, 2, status="done")
        _make_increment_file(self.req_dir, 3, status="done")

        mock_commits.return_value = []

        output = self.tracker.format_revert_from_prompt(3)

        # Should mention increment range
        self.assertIn("REVERT INCREMENTS 0003", output)
        self.assertIn("STEPS", output)


class TestIncrementTrackerRedo(unittest.TestCase):
    """Tests for IncrementTracker.format_redo_prompt."""

    def setUp(self):
        self.tmp_dir = Path(tempfile.mkdtemp())
        self.req_dir = self.tmp_dir / "requirements"
        self.req_dir.mkdir()
        self.prin_dir = self.tmp_dir / "principles"
        self.prin_dir.mkdir()
        (self.prin_dir / "B1.md").write_text("# B1 — Principle\nContent.")
        self.tracker = IncrementTracker(self.tmp_dir)

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    @patch.object(GitAnalyzer, "get_commits_for_increment")
    @patch.object(GitAnalyzer, "get_diff_for_commit")
    def test_redo_prompt_contains_revert_and_implement(self, mock_diff,
                                                       mock_commits):
        """--redo=0001 includes both revert instructions and the requirement."""
        _make_increment_file(self.req_dir, 1, status="done")

        mock_commits.return_value = [
            {"hash": "eee55555", "message": "INCREMENT 0001: done",
             "date": "2026-01-01"},
        ]
        mock_diff.return_value = ""

        output = self.tracker.format_redo_prompt(1)

        # Revert section
        self.assertIn("REVERT INCREMENT 0001", output)
        self.assertIn("git revert --no-commit", output)

        # Re-implement section
        self.assertIn("RE-IMPLEMENT INCREMENT 0001", output)
        self.assertIn("REQUIREMENT:", output)
        self.assertIn("Implement test feature 1", output)
        self.assertIn("ACCEPTANCE CRITERIA:", output)

        # Workflow
        self.assertIn("WORKFLOW:", output)
        self.assertIn("(redo)", output)

    @patch.object(GitAnalyzer, "get_commits_for_increment")
    @patch.object(GitAnalyzer, "get_diff_for_commit")
    def test_redo_prompt_includes_principles(self, mock_diff, mock_commits):
        """Redo prompt resolves and includes applicable principles."""
        _make_increment_file(self.req_dir, 1, status="todo")
        mock_commits.return_value = []

        output = self.tracker.format_redo_prompt(1)

        self.assertIn("APPLICABLE PRINCIPLES:", output)
        self.assertIn("B1", output)

    @patch.object(GitAnalyzer, "get_commits_for_increment")
    @patch.object(GitAnalyzer, "get_diff_for_commit")
    def test_redo_prompt_nonexistent_increment(self, mock_diff, mock_commits):
        """Redo for a non-existent increment gives a fallback message."""
        mock_commits.return_value = []

        output = self.tracker.format_redo_prompt(99)

        self.assertIn("RE-IMPLEMENT INCREMENT 0099", output)
        self.assertIn("not found or empty", output)


class TestGitAnalyzerIncrementMethods(unittest.TestCase):
    """Tests for GitAnalyzer methods used by revert/redo."""

    def setUp(self):
        self.tmp_dir = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    @patch("subprocess.run")
    def test_get_commits_for_increment(self, mock_run):
        """get_commits_for_increment searches git log for increment tag."""
        mock_result1 = MagicMock()
        mock_result1.stdout = "aaa111|INCREMENT 0001: feat|2026-01-01"
        mock_result2 = MagicMock()
        mock_result2.stdout = ""

        mock_run.side_effect = [mock_result1, mock_result2]

        analyzer = GitAnalyzer(self.tmp_dir)
        commits = analyzer.get_commits_for_increment(1)

        self.assertEqual(len(commits), 1)
        self.assertEqual(commits[0]["hash"], "aaa111")
        self.assertIn("INCREMENT 0001", commits[0]["message"])

    @patch("subprocess.run")
    def test_get_commits_for_increment_deduplicates(self, mock_run):
        """Same commit found by both search patterns is not duplicated."""
        mock_result = MagicMock()
        mock_result.stdout = "aaa111|INCREMENT 0001: feat|2026-01-01"

        mock_run.side_effect = [mock_result, mock_result]

        analyzer = GitAnalyzer(self.tmp_dir)
        commits = analyzer.get_commits_for_increment(1)

        self.assertEqual(len(commits), 1)

    @patch("subprocess.run")
    def test_get_diff_for_commit(self, mock_run):
        """get_diff_for_commit returns stat output."""
        mock_result = MagicMock()
        mock_result.stdout = " file.py | 5 +++++\n 1 file changed"
        mock_run.return_value = mock_result

        analyzer = GitAnalyzer(self.tmp_dir)
        diff = analyzer.get_diff_for_commit("abc123")

        self.assertIn("file.py", diff)

    @patch("subprocess.run")
    def test_get_commits_in_range(self, mock_run):
        """get_commits_in_range aggregates commits across increments."""
        call_count = [0]

        def side_effect(*args, **kwargs):
            call_count[0] += 1
            result = MagicMock()
            # Alternate between returning a commit and empty
            cmd = args[0]
            grep_arg = [a for a in cmd if a.startswith("--grep=")]
            if grep_arg and "0001" in grep_arg[0]:
                result.stdout = "aaa111|INCREMENT 0001|2026-01-01"
            elif grep_arg and "0002" in grep_arg[0]:
                result.stdout = "bbb222|INCREMENT 0002|2026-01-02"
            else:
                result.stdout = ""
            return result

        mock_run.side_effect = side_effect

        analyzer = GitAnalyzer(self.tmp_dir)
        commits = analyzer.get_commits_in_range(1, 2)

        self.assertGreaterEqual(len(commits), 1)


class TestCLIRevertRedoArgs(unittest.TestCase):
    """Tests for organism.py CLI argument parsing for revert/redo."""

    def setUp(self):
        self.tmp_dir = Path(tempfile.mkdtemp())
        self.req_dir = self.tmp_dir / "requirements"
        self.req_dir.mkdir()
        self.prin_dir = self.tmp_dir / "principles"
        self.prin_dir.mkdir()

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    @patch.object(GitAnalyzer, "get_commits_for_increment", return_value=[])
    @patch.object(GitAnalyzer, "get_diff_for_commit", return_value="")
    def test_cli_revert_arg(self, mock_diff, mock_commits):
        """organism.py --revert=0001 invokes format_revert_prompt."""
        _make_increment_file(self.req_dir, 1)

        from organism import main
        with patch("sys.argv", ["organism.py", "--revert=0001",
                                f"--root={self.tmp_dir}"]):
            # Should not raise
            main()

    @patch.object(GitAnalyzer, "get_commits_for_increment", return_value=[])
    def test_cli_revert_from_arg(self, mock_commits):
        """organism.py --revert_from=0005 invokes format_revert_from_prompt."""
        _make_increment_file(self.req_dir, 1, status="todo")
        _make_increment_file(self.req_dir, 5, status="todo")

        from organism import main
        with patch("sys.argv", ["organism.py", "--revert_from=0005",
                                f"--root={self.tmp_dir}"]):
            main()

    @patch.object(GitAnalyzer, "get_commits_for_increment", return_value=[])
    @patch.object(GitAnalyzer, "get_diff_for_commit", return_value="")
    def test_cli_redo_arg(self, mock_diff, mock_commits):
        """organism.py --redo=0001 invokes format_redo_prompt."""
        _make_increment_file(self.req_dir, 1)

        from organism import main
        with patch("sys.argv", ["organism.py", "--redo=0001",
                                f"--root={self.tmp_dir}"]):
            main()


if __name__ == "__main__":
    unittest.main()
