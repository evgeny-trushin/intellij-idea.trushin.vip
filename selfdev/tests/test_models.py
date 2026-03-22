"""Tests for models: enums, data classes, and OrganismState."""

import json
import tempfile
import unittest
from pathlib import Path

import sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from models import (
    DevelopmentStage,
    Perspective,
    Priority,
    Prompt,
    FileAnalysis,
    OrganismState,
)


class TestEnums(unittest.TestCase):

    def test_development_stages(self):
        self.assertEqual(DevelopmentStage.EMBRYONIC.value, "embryonic")
        self.assertEqual(DevelopmentStage.GROWTH.value, "growth")
        self.assertEqual(DevelopmentStage.MATURATION.value, "maturation")
        self.assertEqual(DevelopmentStage.HOMEOSTASIS.value, "homeostasis")

    def test_perspectives(self):
        self.assertEqual(len(Perspective), 5)
        values = [p.value for p in Perspective]
        self.assertIn("user", values)
        self.assertIn("test", values)
        self.assertIn("system", values)
        self.assertIn("analytics", values)
        self.assertIn("debug", values)

    def test_priority_ordering(self):
        self.assertLess(Priority.CRITICAL.value, Priority.HIGH.value)
        self.assertLess(Priority.HIGH.value, Priority.MEDIUM.value)
        self.assertLess(Priority.MEDIUM.value, Priority.LOW.value)
        self.assertLess(Priority.LOW.value, Priority.INFO.value)


class TestPromptDataclass(unittest.TestCase):

    def test_prompt_creation_minimal(self):
        p = Prompt(
            perspective=Perspective.USER,
            priority=Priority.HIGH,
            title="Test prompt",
            description="A test description",
        )
        self.assertEqual(p.perspective, Perspective.USER)
        self.assertEqual(p.priority, Priority.HIGH)
        self.assertIsNone(p.file_path)
        self.assertIsNone(p.line_number)
        self.assertEqual(p.acceptance_criteria, [])
        self.assertEqual(p.tags, [])

    def test_prompt_creation_full(self):
        p = Prompt(
            perspective=Perspective.SYSTEM,
            priority=Priority.CRITICAL,
            title="Refactor module",
            description="Module too complex",
            file_path="src/module.py",
            line_number=42,
            metric_current=15.0,
            metric_target=10.0,
            acceptance_criteria=["Reduce complexity"],
            tags=["refactoring"],
        )
        self.assertEqual(p.file_path, "src/module.py")
        self.assertEqual(p.line_number, 42)
        self.assertEqual(p.metric_current, 15.0)
        self.assertIn("Reduce complexity", p.acceptance_criteria)


class TestFileAnalysis(unittest.TestCase):

    def test_file_analysis_defaults(self):
        fa = FileAnalysis(
            path="test.py",
            lines=100,
            functions=5,
            classes=1,
            imports=3,
            complexity=5.0,
            has_tests=False,
        )
        self.assertEqual(fa.issues, [])
        self.assertFalse(fa.has_tests)


class TestOrganismState(unittest.TestCase):

    def test_default_state(self):
        state = OrganismState()
        self.assertEqual(state.generation, 0)
        self.assertEqual(state.development_stage, "embryonic")
        self.assertEqual(state.fitness_scores, {})
        self.assertEqual(state.fitness_history, [])

    def test_get_stage_embryonic(self):
        state = OrganismState(generation=0)
        self.assertEqual(state.get_stage(), DevelopmentStage.EMBRYONIC)
        state.generation = 3
        self.assertEqual(state.get_stage(), DevelopmentStage.EMBRYONIC)

    def test_get_stage_growth(self):
        state = OrganismState(generation=4)
        self.assertEqual(state.get_stage(), DevelopmentStage.GROWTH)
        state.generation = 10
        self.assertEqual(state.get_stage(), DevelopmentStage.GROWTH)

    def test_get_stage_maturation(self):
        state = OrganismState(generation=11)
        self.assertEqual(state.get_stage(), DevelopmentStage.MATURATION)
        state.generation = 20
        self.assertEqual(state.get_stage(), DevelopmentStage.MATURATION)

    def test_get_stage_homeostasis(self):
        state = OrganismState(generation=21)
        self.assertEqual(state.get_stage(), DevelopmentStage.HOMEOSTASIS)
        state.generation = 100
        self.assertEqual(state.get_stage(), DevelopmentStage.HOMEOSTASIS)

    def test_save_and_load(self):
        with tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False) as f:
            path = Path(f.name)
        try:
            state = OrganismState(generation=5)
            state.fitness_scores = {"user": 0.8, "test": 0.6}
            state.save(path)

            loaded = OrganismState.load(path)
            self.assertEqual(loaded.generation, 5)
            self.assertAlmostEqual(loaded.fitness_scores["user"], 0.8)
            self.assertAlmostEqual(loaded.fitness_scores["test"], 0.6)
            self.assertNotEqual(loaded.last_updated, "")
        finally:
            path.unlink()

    def test_load_missing_file(self):
        path = Path("/tmp/nonexistent_state_test.json")
        state = OrganismState.load(path)
        self.assertEqual(state.generation, 0)
        self.assertNotEqual(state.created_at, "")

    def test_load_corrupted_file(self):
        with tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False) as f:
            f.write("not valid json{{{")
            path = Path(f.name)
        try:
            state = OrganismState.load(path)
            self.assertEqual(state.generation, 0)
        finally:
            path.unlink()


if __name__ == "__main__":
    unittest.main()
