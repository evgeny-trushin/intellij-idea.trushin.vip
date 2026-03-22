"""Tests for PromptFormatter."""

import unittest
from pathlib import Path

import sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from models import OrganismState, Perspective, Priority, Prompt
from formatters import PromptFormatter


class TestPromptFormatter(unittest.TestCase):

    def test_format_prompt_no_color(self):
        formatter = PromptFormatter()
        p = Prompt(
            perspective=Perspective.USER,
            priority=Priority.HIGH,
            title="Test title",
            description="Test description",
            acceptance_criteria=["Criterion 1"],
        )
        output = formatter.format_prompt(p)
        self.assertIn("[HIGH]", output)
        self.assertIn("Test title", output)
        self.assertIn("Test description", output)
        self.assertIn("Criterion 1", output)

    def test_format_prompt_with_location(self):
        formatter = PromptFormatter()
        p = Prompt(
            perspective=Perspective.SYSTEM,
            priority=Priority.MEDIUM,
            title="Refactor",
            description="Too complex",
            file_path="src/module.py",
            line_number=42,
        )
        output = formatter.format_prompt(p)
        self.assertIn("src/module.py:42", output)

    def test_format_prompt_with_metrics(self):
        formatter = PromptFormatter()
        p = Prompt(
            perspective=Perspective.TEST,
            priority=Priority.HIGH,
            title="Coverage",
            description="Low coverage",
            metric_current=30.0,
            metric_target=80.0,
        )
        output = formatter.format_prompt(p)
        self.assertIn("30.0", output)
        self.assertIn("80.0", output)

    def test_format_header(self):
        formatter = PromptFormatter()
        state = OrganismState(generation=5)
        output = formatter.format_header(Perspective.USER, 0.75, state)
        self.assertIn("USER", output)
        self.assertIn("75.00%", output)
        self.assertIn("growth", output)

    def test_format_summary(self):
        formatter = PromptFormatter()
        state = OrganismState()
        state.fitness_scores = {"user": 0.8, "test": 0.6}
        prompts = [
            Prompt(perspective=Perspective.USER, priority=Priority.HIGH,
                   title="A", description="a"),
            Prompt(perspective=Perspective.TEST, priority=Priority.CRITICAL,
                   title="B", description="b"),
        ]
        output = formatter.format_summary(state, prompts)
        self.assertIn("Total Prompts: 2", output)
        self.assertIn("CRITICAL: 1", output)
        self.assertIn("HIGH: 1", output)

    def test_no_ansi_in_output(self):
        """Output must be plain text with no ANSI escape codes (principle CLN)."""
        formatter = PromptFormatter()
        p = Prompt(
            perspective=Perspective.USER,
            priority=Priority.CRITICAL,
            title="Critical issue",
            description="Very critical",
        )
        output = formatter.format_prompt(p)
        self.assertNotIn("\033[", output)
        self.assertIn("[CRITICAL]", output)

    def test_format_prompt_without_location(self):
        formatter = PromptFormatter()
        p = Prompt(
            perspective=Perspective.USER,
            priority=Priority.INFO,
            title="Info only",
            description="No location",
        )
        output = formatter.format_prompt(p)
        self.assertNotIn("Location:", output)

    def test_format_prompt_file_path_only(self):
        formatter = PromptFormatter()
        p = Prompt(
            perspective=Perspective.USER,
            priority=Priority.LOW,
            title="File only",
            description="Has file",
            file_path="src/module.py",
        )
        output = formatter.format_prompt(p)
        self.assertIn("src/module.py", output)
        self.assertNotIn(":", output.split("src/module.py")[1].split("\n")[0])

    def test_format_summary_empty(self):
        formatter = PromptFormatter()
        state = OrganismState()
        prompts = []
        output = formatter.format_summary(state, prompts)
        self.assertIn("Total Prompts: 0", output)
        self.assertNotIn("Overall Fitness", output)  # No scores

    def test_format_summary_with_fitness_no_prompts(self):
        formatter = PromptFormatter()
        state = OrganismState()
        state.fitness_scores = {"user": 1.0}
        prompts = []
        output = formatter.format_summary(state, prompts)
        self.assertIn("Total Prompts: 0", output)
        self.assertIn("Overall Fitness: 100.00%", output)

    def test_format_prompt_all_fields(self):
        formatter = PromptFormatter()
        p = Prompt(
            perspective=Perspective.SYSTEM,
            priority=Priority.MEDIUM,
            title="Title",
            description="Desc",
            file_path="foo.py",
            line_number=10,
            metric_current=5.0,
            metric_target=10.0,
            acceptance_criteria=["Do this", "Do that"]
        )
        output = formatter.format_prompt(p)
        self.assertIn("[MEDIUM]", output)
        self.assertIn("Title", output)
        self.assertIn("Desc", output)
        self.assertIn("foo.py:10", output)
        self.assertIn("Current: 5.0 -> Target: 10.0", output)
        self.assertIn("Acceptance Criteria:", output)
        self.assertIn("- Do this", output)
        self.assertIn("- Do that", output)


    def test_format_summary_empty_prompts(self):
        """Summary with no prompts should still render."""
        formatter = PromptFormatter()
        state = OrganismState()
        state.fitness_scores = {"user": 0.9}
        output = formatter.format_summary(state, [])
        self.assertIn("Total Prompts: 0", output)
        self.assertIn("90.00%", output)

    def test_format_summary_no_fitness_scores(self):
        """Summary without fitness scores should not show overall fitness."""
        formatter = PromptFormatter()
        state = OrganismState()
        prompts = [
            Prompt(perspective=Perspective.USER, priority=Priority.HIGH,
                   title="A", description="a"),
        ]
        output = formatter.format_summary(state, prompts)
        self.assertIn("Total Prompts: 1", output)
        self.assertNotIn("Overall Fitness", output)

    def test_all_priorities_plain_text(self):
        """Each priority level should render as plain text with no ANSI codes."""
        formatter = PromptFormatter()
        for priority in Priority:
            p = Prompt(
                perspective=Perspective.USER,
                priority=priority,
                title=f"{priority.name} test",
                description="desc",
            )
            output = formatter.format_prompt(p)
            self.assertNotIn("\033[", output,
                             f"ANSI code found for {priority.name}")
            self.assertIn(f"[{priority.name}]", output)

    def test_format_prompt_with_all_fields(self):
        """Prompt with all fields populated."""
        formatter = PromptFormatter()
        p = Prompt(
            perspective=Perspective.SYSTEM,
            priority=Priority.HIGH,
            title="Full prompt",
            description="Full description",
            file_path="src/module.py",
            line_number=42,
            metric_current=15.0,
            metric_target=10.0,
            acceptance_criteria=["Criterion A", "Criterion B"],
            tags=["complexity"],
        )
        output = formatter.format_prompt(p)
        self.assertIn("[HIGH]", output)
        self.assertIn("Full prompt", output)
        self.assertIn("src/module.py:42", output)
        self.assertIn("15.0", output)
        self.assertIn("10.0", output)
        self.assertIn("Criterion A", output)
        self.assertIn("Criterion B", output)

    def test_format_prompt_no_acceptance_criteria(self):
        """Prompt without acceptance criteria should not show that section."""
        formatter = PromptFormatter()
        p = Prompt(
            perspective=Perspective.USER,
            priority=Priority.MEDIUM,
            title="No criteria",
            description="No AC",
        )
        output = formatter.format_prompt(p)
        self.assertNotIn("Acceptance Criteria", output)

    def test_format_header_embryonic_stage(self):
        formatter = PromptFormatter()
        state = OrganismState(generation=0)
        output = formatter.format_header(Perspective.TEST, 0.5, state)
        self.assertIn("TEST", output)
        self.assertIn("embryonic", output)
        self.assertIn("50.00%", output)

    def test_format_header_homeostasis_stage(self):
        formatter = PromptFormatter()
        state = OrganismState(generation=25)
        output = formatter.format_header(Perspective.SYSTEM, 0.95, state)
        self.assertIn("homeostasis", output)
        self.assertIn("95.00%", output)

    def test_format_summary_all_priority_counts(self):
        """Summary should list each priority type with count."""
        formatter = PromptFormatter()
        state = OrganismState()
        state.fitness_scores = {"user": 0.5}
        prompts = [
            Prompt(perspective=Perspective.USER, priority=Priority.CRITICAL,
                   title="A", description="a"),
            Prompt(perspective=Perspective.USER, priority=Priority.HIGH,
                   title="B", description="b"),
            Prompt(perspective=Perspective.USER, priority=Priority.HIGH,
                   title="C", description="c"),
            Prompt(perspective=Perspective.USER, priority=Priority.MEDIUM,
                   title="D", description="d"),
            Prompt(perspective=Perspective.USER, priority=Priority.LOW,
                   title="E", description="e"),
            Prompt(perspective=Perspective.USER, priority=Priority.INFO,
                   title="F", description="f"),
        ]
        output = formatter.format_summary(state, prompts)
        self.assertIn("Total Prompts: 6", output)
        self.assertIn("CRITICAL: 1", output)
        self.assertIn("HIGH: 2", output)
        self.assertIn("MEDIUM: 1", output)
        self.assertIn("LOW: 1", output)
        self.assertIn("INFO: 1", output)


if __name__ == "__main__":
    unittest.main()
