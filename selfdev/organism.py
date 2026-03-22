#!/usr/bin/env python3
"""
Self-Development Organism
A system that analyzes codebase state and generates prompts from multiple perspectives.
Inspired by biological development principles and lateral thinking.

This module serves as the main entry point and orchestrator, combining all
sub-modules: models, analyzers, perspectives, diagnostics, formatters,
and the increment tracker.

Workflow (increment-driven loop):
  1. ./develop.sh  →  output the current TODO increment with injected principles
  2. User implements the requirement
  3. git add -A && git commit -m "INCREMENT XXXX: desc" && git push
  4. ./develop.sh  →  verify & mark done, output next increment
  5. Repeat until all increments are completed
"""

import argparse
from datetime import datetime, timezone
from pathlib import Path
from typing import List

# Re-export all public symbols for backward compatibility
from models import (  # noqa: F401
    ROOT_DIR,
    STATE_FILE,
    ANALYZABLE_DIRS,
    TEST_DIRS,
    COMPLEXITY_THRESHOLD,
    COVERAGE_TARGET,
    MAX_FILE_LINES,
    MAX_FUNCTION_LINES,
    DevelopmentStage,
    Perspective,
    Priority,
    Prompt,
    FileAnalysis,
    OrganismState,
)
from analyzers import CodeAnalyzer, GitAnalyzer  # noqa: F401
from perspectives import (  # noqa: F401
    PerspectiveAnalyzer,
    TestPerspective,
    SystemPerspective,
)
from user_perspective import UserPerspective  # noqa: F401
from diagnostics import AnalyticsPerspective, DebugPerspective  # noqa: F401

from formatters import PromptFormatter  # noqa: F401
from increment_tracker import IncrementTracker  # noqa: F401


class SelfDevelopmentOrganism:
    """Main class orchestrating the self-development system"""

    def __init__(self, root_dir: Path = ROOT_DIR):
        self.root_dir = root_dir
        self.state = OrganismState.load(STATE_FILE)
        self.formatter = PromptFormatter()

        self.perspectives = {
            Perspective.USER: UserPerspective(root_dir, self.state),
            Perspective.TEST: TestPerspective(root_dir, self.state),
            Perspective.SYSTEM: SystemPerspective(root_dir, self.state),
            Perspective.ANALYTICS: AnalyticsPerspective(root_dir, self.state),
            Perspective.DEBUG: DebugPerspective(root_dir, self.state),

        }

    def run_perspective(self, perspective: Perspective, print_results: bool = True) -> List[Prompt]:
        """Run analysis from a specific perspective.

        When *print_results* is True (default / single-perspective mode),
        prompts are filtered to the highest priority found within this
        perspective and printed immediately.

        When *print_results* is False (batch mode used by
        *run_all_perspectives*), no output is produced — the caller is
        responsible for global filtering and printing.
        """
        analyzer = self.perspectives[perspective]
        fitness, prompts = analyzer.analyze()

        self.state.fitness_scores[perspective.value] = fitness

        prompts = sorted(prompts, key=lambda p: p.priority.value)

        if print_results:
            print(self.formatter.format_header(perspective, fitness, self.state))
            if prompts:
                highest = prompts[0].priority
                filtered = [p for p in prompts if p.priority == highest]
                for prompt in filtered:
                    print(self.formatter.format_prompt(prompt))
                    print()
            else:
                print("  No issues found from this perspective.")

        return prompts

    def run_all_perspectives(self) -> List[Prompt]:
        """Run all perspective analyses.

        Collects prompts from every perspective, determines the single
        highest priority across *all* of them, and displays only prompts
        at that level.
        """
        per_perspective: dict[Perspective, tuple[float, list[Prompt]]] = {}

        # Phase 1 — collect without printing
        for perspective in Perspective:
            prompts = self.run_perspective(perspective, print_results=False)
            fitness = self.state.fitness_scores[perspective.value]
            per_perspective[perspective] = (fitness, prompts)

        # Determine global highest priority
        all_prompts = [p for _, ps in per_perspective.values() for p in ps]
        if all_prompts:
            global_highest = min(all_prompts, key=lambda p: p.priority.value).priority
        else:
            global_highest = None

        # Phase 2 — print, filtering each perspective to global highest
        displayed_prompts: List[Prompt] = []
        for perspective in Perspective:
            fitness, prompts = per_perspective[perspective]
            print(self.formatter.format_header(perspective, fitness, self.state))
            if global_highest is not None:
                filtered = [p for p in prompts if p.priority == global_highest]
            else:
                filtered = []
            if filtered:
                for prompt in filtered:
                    print(self.formatter.format_prompt(prompt))
                    print()
                displayed_prompts.extend(filtered)
            else:
                print("  No issues found from this perspective.")

        print(self.formatter.format_summary(self.state, displayed_prompts))

        # Store all collected prompts for use by advance_generation
        self._last_all_prompts = all_prompts

        return displayed_prompts

    @staticmethod
    def _run_tests(root_dir: Path) -> tuple:
        """Run project tests and return (success: bool, output: str).

        Tries pytest first; falls back to unittest discover.
        """
        import subprocess
        test_dir = root_dir / "selfdev" / "tests"
        if not test_dir.exists():
            # Fallback: look for tests/ at root
            test_dir = root_dir / "tests"
        if not test_dir.exists():
            return True, "No test directory found — skipping."

        # Try pytest first
        try:
            result = subprocess.run(
                ["python3", "-m", "pytest", str(test_dir), "-q", "--tb=short"],
                cwd=root_dir,
                capture_output=True,
                text=True,
                timeout=120,
            )
            # If pytest is not installed, returncode is non-zero and stderr
            # contains "No module named pytest".  Fall through to unittest.
            if "No module named pytest" not in (result.stderr or ""):
                passed = result.returncode == 0
                output = (result.stdout + result.stderr).strip()
                return passed, output
        except FileNotFoundError:
            pass  # python3 not found on PATH — try unittest below

        # Fallback: unittest discover
        try:
            result = subprocess.run(
                ["python3", "-m", "unittest", "discover",
                 "-s", str(test_dir), "-q"],
                cwd=root_dir,
                capture_output=True,
                text=True,
                timeout=120,
            )
            passed = result.returncode == 0
            output = (result.stdout + result.stderr).strip()
            return passed, output
        except FileNotFoundError:
            return True, "python3 not found — skipping test check."
        except Exception as exc:
            return False, f"Test runner error: {exc}"

    def advance_generation(self):
        """Advance to next generation.

        Verifies the current TODO increment:
          - Tests must pass before an increment is marked done.
          - Changed files are listed for traceability.
        Then renames it to done, records fitness history, and outputs
        the next increment.
        """
        tracker = IncrementTracker(self.root_dir)
        current = tracker.current_todo()

        if current is None:
            print("\n  ★ ALL INCREMENTS COMPLETED — nothing to advance.")
            return

        # Gate: tests must pass before advancing
        tests_ok, test_output = self._run_tests(self.root_dir)
        if not tests_ok:
            print("\n  ✗ CANNOT ADVANCE — tests are failing.")
            print("    Fix the failures below before re-running develop.sh:\n")
            print(test_output)
            return

        # Mark the current increment as done
        done_path = tracker.mark_done(current)
        inc = tracker.parse_increment(done_path)

        # Record fitness history
        scores = dict(self.state.fitness_scores)
        if scores:
            scores["overall"] = sum(scores.values()) / len(scores)
        self.state.fitness_history.append({
            "generation": self.state.generation,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "increment": inc["number"],
            **scores
        })

        git_analyzer = GitAnalyzer(self.root_dir)
        self.state.last_git_hash = git_analyzer.get_current_hash()
        changed_files = git_analyzer.get_changed_files_in_last_commit()

        self.state.generation += 1
        self.state.development_stage = self.state.get_stage().value

        # Print done summary with traceability
        next_todo = tracker.current_todo()
        print(tracker.format_done_summary(done_path, next_todo,
                                          changed_files=changed_files))

        # Print next increment prompt (if any) and track it
        if next_todo:
            print(tracker.format_increment_prompt(next_todo))
            next_inc = tracker.parse_increment(next_todo)
            self.state.last_increment_shown = next_inc["number"]
        else:
            self.state.last_increment_shown = 0

        self.state.save(STATE_FILE)

    def print_state(self):
        """Print current organism state"""
        print("\n" + "=" * 60)
        print("  ORGANISM STATE")
        print("=" * 60)
        print(f"  Generation: {self.state.generation}")
        print(f"  Stage: {self.state.get_stage().value}")
        print(f"  Created: {self.state.created_at[:19] if self.state.created_at else 'N/A'}")
        print(f"  Last Updated: {self.state.last_updated[:19] if self.state.last_updated else 'N/A'}")
        print(f"  Git Hash: {self.state.last_git_hash or 'N/A'}")

        if self.state.fitness_scores:
            print("\n  Fitness Scores:")
            for perspective, score in self.state.fitness_scores.items():
                print(f"    {perspective}: {score:.2%}")

        print("=" * 60)


def main():
    """Main entry point for the self-development system"""
    parser = argparse.ArgumentParser(
        description="Self-Development System - Analyze codebase from multiple perspectives"
    )
    parser.add_argument("--user", action="store_true", help="Run user perspective analysis")
    parser.add_argument("--test", action="store_true", help="Run test perspective analysis")
    parser.add_argument("--system", action="store_true", help="Run system perspective analysis")
    parser.add_argument("--analytics", action="store_true", help="Run analytics perspective analysis")
    parser.add_argument("--debug", action="store_true", help="Run debug perspective analysis")

    parser.add_argument("--all", action="store_true", help="Run all perspectives")
    parser.add_argument("--state", action="store_true", help="Show current state")
    parser.add_argument("--advance", action="store_true", help="Advance to next generation")
    parser.add_argument("--revert", type=str, default=None,
                        help="Generate prompt to revert increment (e.g. --revert=0001)")
    parser.add_argument("--revert_from", type=str, default=None,
                        help="Generate prompt to revert from increment to current todo (e.g. --revert_from=0020)")
    parser.add_argument("--redo", type=str, default=None,
                        help="Generate prompt to revert and re-implement increment (e.g. --redo=0001)")
    parser.add_argument("--root", type=str, default=None,
                        help="Root directory to analyze (default: parent directory)")
    parser.add_argument("--selfdev", action="store_true",
                        help="Analyze the selfdev system itself instead of the project")

    args = parser.parse_args()

    if args.selfdev:
        root_dir = Path(__file__).resolve().parent
    elif args.root:
        root_dir = Path(args.root).resolve()
    else:
        root_dir = ROOT_DIR

    organism = SelfDevelopmentOrganism(root_dir=root_dir)

    if args.state:
        organism.print_state()
        return

    if args.advance:
        organism.advance_generation()
        return

    # --- Revert / Redo modes ---
    if args.revert:
        tracker = IncrementTracker(root_dir)
        increment_num = int(args.revert)
        print(tracker.format_revert_prompt(increment_num))
        return

    if args.revert_from:
        tracker = IncrementTracker(root_dir)
        from_num = int(args.revert_from)
        print(tracker.format_revert_from_prompt(from_num))
        return

    if args.redo:
        tracker = IncrementTracker(root_dir)
        increment_num = int(args.redo)
        print(tracker.format_redo_prompt(increment_num))
        return

    # --- Increment-driven mode (default) ---
    # If no specific perspective is requested, show the current increment.
    perspectives_to_run = []

    if args.user:
        perspectives_to_run.append(Perspective.USER)
    if args.test:
        perspectives_to_run.append(Perspective.TEST)
    if args.system:
        perspectives_to_run.append(Perspective.SYSTEM)
    if args.analytics:
        perspectives_to_run.append(Perspective.ANALYTICS)
    if args.debug:
        perspectives_to_run.append(Perspective.DEBUG)

    if perspectives_to_run:
        # Explicit perspective mode — run selected perspectives
        all_prompts = []
        for perspective in perspectives_to_run:
            prompts = organism.run_perspective(perspective)
            all_prompts.extend(prompts)

        if len(perspectives_to_run) > 1:
            print(organism.formatter.format_summary(organism.state, all_prompts))
    elif args.all:
        organism.run_all_perspectives()
    else:
        # Default: verify previous increment (if already shown) & show next
        tracker = IncrementTracker(root_dir)
        current = tracker.current_todo()

        if current is None:
            print("\n  ★ ALL INCREMENTS COMPLETED!")
            print("    Run with --all to see full perspective analysis.\n")
            organism.state.save(STATE_FILE)
            return

        inc_data = tracker.parse_increment(current)
        current_num = inc_data["number"]

        if organism.state.last_increment_shown == current_num:
            # Already showed this increment — show verification prompt
            # (NEVER auto-advance; the agent must explicitly rename)
            print(tracker.format_verification_prompt(current))
        else:
            # First time seeing this increment — show it
            print(tracker.format_increment_prompt(current))
            organism.state.last_increment_shown = current_num
            organism.state.save(STATE_FILE)

    return


if __name__ == "__main__":
    main()
