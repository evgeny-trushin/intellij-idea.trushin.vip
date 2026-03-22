"""Tests for selfdev/increment_tracker.py — IncrementTracker core functionality."""

import shutil
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from increment_tracker import IncrementTracker


def _make_increment(req_dir: Path, number: int, status: str = "todo",
                    slug: str = "test-feature", content: str = None,
                    sep: str = "_") -> Path:
    """Create an increment file and return its path."""
    filename = f"increment_{number:04d}{sep}{status}{sep}{slug}.md"
    path = req_dir / filename
    if content is None:
        content = textwrap.dedent(f"""\
            # Increment {number:04d}: {slug.replace('-', ' ').title()}

            **Requirement ID:** R{number}

            ## Description
            Implement requirement {number} for {slug}.

            ## Acceptance Criteria
            - [ ] Feature works correctly
            - [ ] Tests pass
            - [ ] Documentation updated

            ## Related Principles
            - [P1](../principles/P1.md)
        """)
    path.write_text(content, encoding="utf-8")
    return path


class TestIncrementTrackerDiscovery(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = Path(tempfile.mkdtemp())
        self.req_dir = self.tmp_dir / "requirements"
        self.req_dir.mkdir()
        self.prin_dir = self.tmp_dir / "principles"
        self.prin_dir.mkdir()
        self.tracker = IncrementTracker(self.tmp_dir)

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_current_todo_with_single_todo(self):
        _make_increment(self.req_dir, 1, "todo")
        result = self.tracker.current_todo()
        self.assertIsNotNone(result)
        self.assertIn("todo", result.name)

    def test_current_todo_returns_lowest_numbered(self):
        _make_increment(self.req_dir, 3, "todo")
        _make_increment(self.req_dir, 1, "todo")
        _make_increment(self.req_dir, 2, "todo")
        result = self.tracker.current_todo()
        self.assertIn("0001", result.name)

    def test_current_todo_none_when_all_done(self):
        _make_increment(self.req_dir, 1, "done")
        _make_increment(self.req_dir, 2, "done")
        result = self.tracker.current_todo()
        self.assertIsNone(result)

    def test_current_todo_none_when_empty(self):
        result = self.tracker.current_todo()
        self.assertIsNone(result)

    def test_all_done_true_when_no_todos(self):
        _make_increment(self.req_dir, 1, "done")
        self.assertTrue(self.tracker.all_done())

    def test_all_done_false_when_todos_exist(self):
        _make_increment(self.req_dir, 1, "todo")
        self.assertFalse(self.tracker.all_done())

    def test_done_count_counts_done_files(self):
        _make_increment(self.req_dir, 1, "done")
        _make_increment(self.req_dir, 2, "done")
        _make_increment(self.req_dir, 3, "todo")
        self.assertEqual(self.tracker.done_count(), 2)

    def test_total_count_sums_todo_and_done(self):
        _make_increment(self.req_dir, 1, "done")
        _make_increment(self.req_dir, 2, "todo")
        _make_increment(self.req_dir, 3, "todo")
        self.assertEqual(self.tracker.total_count(), 3)

    def test_missing_requirements_dir_returns_empty(self):
        shutil.rmtree(self.req_dir)
        self.assertEqual(self.tracker.done_count(), 0)
        self.assertEqual(self.tracker.total_count(), 0)
        self.assertIsNone(self.tracker.current_todo())

    def test_underscore_separator_supported(self):
        _make_increment(self.req_dir, 1, "todo", sep="_")
        result = self.tracker.current_todo()
        self.assertIsNotNone(result)

    def test_hyphen_separator_supported(self):
        _make_increment(self.req_dir, 1, "todo", sep="-")
        result = self.tracker.current_todo()
        self.assertIsNotNone(result)


class TestIncrementTrackerParsing(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = Path(tempfile.mkdtemp())
        self.req_dir = self.tmp_dir / "requirements"
        self.req_dir.mkdir()

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_parse_extracts_number(self):
        path = _make_increment(self.req_dir, 42, "todo")
        data = IncrementTracker.parse_increment(path)
        self.assertEqual(data["number"], 42)

    def test_parse_extracts_status_todo(self):
        path = _make_increment(self.req_dir, 1, "todo")
        data = IncrementTracker.parse_increment(path)
        self.assertEqual(data["status"], "todo")

    def test_parse_extracts_status_done(self):
        path = _make_increment(self.req_dir, 1, "done")
        data = IncrementTracker.parse_increment(path)
        self.assertEqual(data["status"], "done")

    def test_parse_extracts_title(self):
        path = _make_increment(self.req_dir, 1, "todo")
        data = IncrementTracker.parse_increment(path)
        self.assertTrue(len(data["title"]) > 0)

    def test_parse_extracts_description(self):
        path = _make_increment(self.req_dir, 1, "todo")
        data = IncrementTracker.parse_increment(path)
        self.assertIn("Implement requirement", data["description"])

    def test_parse_extracts_requirement_id(self):
        path = _make_increment(self.req_dir, 1, "todo")
        data = IncrementTracker.parse_increment(path)
        self.assertEqual(data["requirement_id"], "R1")

    def test_parse_extracts_acceptance_criteria(self):
        path = _make_increment(self.req_dir, 1, "todo")
        data = IncrementTracker.parse_increment(path)
        self.assertIsInstance(data["acceptance_criteria"], list)
        self.assertGreater(len(data["acceptance_criteria"]), 0)

    def test_parse_extracts_related_principles(self):
        path = _make_increment(self.req_dir, 1, "todo")
        data = IncrementTracker.parse_increment(path)
        self.assertIsInstance(data["related_principles"], list)

    def test_parse_raw_content_present(self):
        path = _make_increment(self.req_dir, 1, "todo")
        data = IncrementTracker.parse_increment(path)
        self.assertIn("raw_content", data)
        self.assertIn("Increment", data["raw_content"])

    def test_parse_short_desc_extracted(self):
        path = _make_increment(self.req_dir, 5, "todo", slug="my-feature")
        data = IncrementTracker.parse_increment(path)
        self.assertIn("my", data["short_desc"])

    def test_parse_flexible_heading_fallback(self):
        content = textwrap.dedent("""\
            ### W5: Screenshot Assets

            Provide screenshot assets for the documentation.

            **Acceptance Criteria:**
            1. Screenshots added
            2. Alt text provided

            **Related Principles:**
            - [P1](../principles/P1.md)
        """)
        path = self.req_dir / "increment_0005_todo_screenshot-assets.md"
        path.write_text(content, encoding="utf-8")
        data = IncrementTracker.parse_increment(path)
        self.assertIn("W5", data["requirement_id"])
        self.assertGreater(len(data["acceptance_criteria"]), 0)

    def test_parse_empty_file(self):
        path = self.req_dir / "increment_0001_todo_empty.md"
        path.write_text("", encoding="utf-8")
        data = IncrementTracker.parse_increment(path)
        self.assertEqual(data["number"], 1)
        self.assertEqual(data["description"], "")


class TestIncrementTrackerMarkDone(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = Path(tempfile.mkdtemp())
        self.req_dir = self.tmp_dir / "requirements"
        self.req_dir.mkdir()
        self.tracker = IncrementTracker(self.tmp_dir)

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_mark_done_renames_underscore_separator(self):
        path = _make_increment(self.req_dir, 1, "todo", sep="_")
        new_path = self.tracker.mark_done(path)
        self.assertFalse(path.exists())
        self.assertTrue(new_path.exists())
        self.assertIn("done", new_path.name)

    def test_mark_done_renames_hyphen_separator(self):
        path = _make_increment(self.req_dir, 1, "todo", sep="-")
        new_path = self.tracker.mark_done(path)
        self.assertFalse(path.exists())
        self.assertTrue(new_path.exists())
        self.assertIn("done", new_path.name)

    def test_mark_done_updates_done_count(self):
        path = _make_increment(self.req_dir, 1, "todo")
        self.assertEqual(self.tracker.done_count(), 0)
        self.tracker.mark_done(path)
        self.assertEqual(self.tracker.done_count(), 1)

    def test_mark_done_updates_all_done(self):
        path = _make_increment(self.req_dir, 1, "todo")
        self.assertFalse(self.tracker.all_done())
        self.tracker.mark_done(path)
        self.assertTrue(self.tracker.all_done())


class TestIncrementTrackerPrinciples(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = Path(tempfile.mkdtemp())
        self.prin_dir = self.tmp_dir / "principles"
        self.prin_dir.mkdir()
        self.tracker = IncrementTracker(self.tmp_dir)

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_load_principle_by_code(self):
        (self.prin_dir / "P1.md").write_text("# P1 — Principle\nContent here.", encoding="utf-8")
        content = self.tracker.load_principle("P1")
        self.assertIsNotNone(content)
        self.assertIn("P1", content)

    def test_load_principle_missing_returns_none(self):
        result = self.tracker.load_principle("MISSING")
        self.assertIsNone(result)

    def test_resolve_principles_loads_content(self):
        (self.prin_dir / "B1.md").write_text("# B1 — Bio\nBiological principle.", encoding="utf-8")
        refs = [("B1", "../principles/B1.md")]
        results = self.tracker.resolve_principles(refs)
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0]["code"], "B1")
        self.assertIn("B1", results[0]["title"])
        self.assertIn("Biological", results[0]["content"])

    def test_resolve_principles_deduplicates(self):
        (self.prin_dir / "P1.md").write_text("# P1\nContent.", encoding="utf-8")
        refs = [("P1", "../principles/P1.md"), ("P1", "../principles/P1.md")]
        results = self.tracker.resolve_principles(refs)
        self.assertEqual(len(results), 1)

    def test_resolve_principles_skips_missing(self):
        refs = [("MISSING", "../principles/MISSING.md")]
        results = self.tracker.resolve_principles(refs)
        self.assertEqual(results, [])


class TestIncrementTrackerFormatPrompt(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = Path(tempfile.mkdtemp())
        self.req_dir = self.tmp_dir / "requirements"
        self.req_dir.mkdir()
        self.prin_dir = self.tmp_dir / "principles"
        self.prin_dir.mkdir()
        self.tracker = IncrementTracker(self.tmp_dir)

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_format_increment_prompt_contains_header(self):
        path = _make_increment(self.req_dir, 1, "todo")
        prompt = self.tracker.format_increment_prompt(path)
        self.assertIn("INCREMENT 0001", prompt)

    def test_format_increment_prompt_contains_requirement(self):
        path = _make_increment(self.req_dir, 1, "todo")
        prompt = self.tracker.format_increment_prompt(path)
        self.assertIn("REQUIREMENT:", prompt)

    def test_format_increment_prompt_contains_acceptance_criteria(self):
        path = _make_increment(self.req_dir, 1, "todo")
        prompt = self.tracker.format_increment_prompt(path)
        self.assertIn("ACCEPTANCE CRITERIA:", prompt)

    def test_format_increment_prompt_contains_workflow(self):
        path = _make_increment(self.req_dir, 1, "todo")
        prompt = self.tracker.format_increment_prompt(path)
        self.assertIn("WORKFLOW:", prompt)

    def test_format_increment_prompt_contains_rules(self):
        path = _make_increment(self.req_dir, 1, "todo")
        prompt = self.tracker.format_increment_prompt(path)
        self.assertIn("RULES:", prompt)

    def test_empty_file_generates_self_inspection_prompt(self):
        path = self.req_dir / "increment_0001_todo_empty.md"
        path.write_text("", encoding="utf-8")
        prompt = self.tracker.format_increment_prompt(path)
        self.assertIn("SELF-INSPECTION", prompt)

    def test_prompt_includes_principles_when_found(self):
        (self.prin_dir / "P1.md").write_text("# P1 — Principle\nPrinciple content.", encoding="utf-8")
        path = _make_increment(self.req_dir, 1, "todo")
        prompt = self.tracker.format_increment_prompt(path)
        self.assertIn("APPLICABLE PRINCIPLES:", prompt)

    def test_format_done_summary_contains_completed(self):
        path = _make_increment(self.req_dir, 1, "done")
        summary = self.tracker.format_done_summary(path, next_path=None)
        self.assertIn("COMPLETED", summary)
        self.assertIn("INCREMENT 0001", summary)

    def test_format_done_summary_all_done_message(self):
        path = _make_increment(self.req_dir, 1, "done")
        summary = self.tracker.format_done_summary(path, next_path=None)
        self.assertIn("ALL INCREMENTS COMPLETED", summary)

    def test_format_done_summary_next_ready_message(self):
        done_path = _make_increment(self.req_dir, 1, "done")
        next_path = _make_increment(self.req_dir, 2, "todo")
        summary = self.tracker.format_done_summary(done_path, next_path=next_path)
        self.assertIn("Next increment ready", summary)


class TestIncrementTrackerVerification(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = Path(tempfile.mkdtemp())
        self.req_dir = self.tmp_dir / "requirements"
        self.req_dir.mkdir()
        self.prin_dir = self.tmp_dir / "principles"
        self.prin_dir.mkdir()
        self.tracker = IncrementTracker(self.tmp_dir)

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_verification_prompt_contains_header(self):
        path = _make_increment(self.req_dir, 1, "todo")
        prompt = self.tracker.format_verification_prompt(path)
        self.assertIn("VERIFICATION", prompt)
        self.assertIn("INCREMENT 0001", prompt)

    def test_verification_prompt_contains_step_instructions(self):
        path = _make_increment(self.req_dir, 1, "todo")
        prompt = self.tracker.format_verification_prompt(path)
        self.assertIn("STEP 1", prompt)
        self.assertIn("STEP 2", prompt)
        self.assertIn("STEP 3", prompt)
        self.assertIn("STEP 4", prompt)

    def test_verification_prompt_contains_rename_instruction(self):
        path = _make_increment(self.req_dir, 1, "todo")
        prompt = self.tracker.format_verification_prompt(path)
        self.assertIn("RENAME", prompt)
        self.assertIn("todo", prompt)
        self.assertIn("done", prompt)

    def test_verification_prompt_criteria_listed(self):
        path = _make_increment(self.req_dir, 1, "todo")
        prompt = self.tracker.format_verification_prompt(path)
        self.assertIn("Feature works correctly", prompt)


class TestIncrementTrackerSelfInspection(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = Path(tempfile.mkdtemp())
        self.req_dir = self.tmp_dir / "requirements"
        self.req_dir.mkdir()
        self.tracker = IncrementTracker(self.tmp_dir)

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_self_inspection_prompt_for_empty_file(self):
        path = self.req_dir / "increment_0001_todo_empty.md"
        path.write_text("", encoding="utf-8")
        data = IncrementTracker.parse_increment(path)
        prompt = self.tracker.format_self_inspection_prompt(path, data)
        self.assertIn("SELF-INSPECTION", prompt)
        self.assertIn("DIAGNOSIS", prompt)
        self.assertIn("EMPTY", prompt)

    def test_self_inspection_prompt_for_non_standard_content(self):
        path = self.req_dir / "increment_0002_todo_odd.md"
        path.write_text("Some non-standard content without proper sections.", encoding="utf-8")
        data = IncrementTracker.parse_increment(path)
        prompt = self.tracker.format_self_inspection_prompt(path, data)
        self.assertIn("SELF-INSPECTION", prompt)

    def test_self_inspection_includes_file_name(self):
        path = self.req_dir / "increment_0001_todo_test-feature.md"
        path.write_text("", encoding="utf-8")
        data = IncrementTracker.parse_increment(path)
        prompt = self.tracker.format_self_inspection_prompt(path, data)
        self.assertIn(path.name, prompt)


if __name__ == "__main__":
    unittest.main()
