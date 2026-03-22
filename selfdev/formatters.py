"""
Output formatting for the Self-Development Organism system.
"""

from collections import defaultdict
from typing import List

from models import (
    OrganismState,
    Perspective,
    Priority,
    Prompt,
)


class PromptFormatter:
    """Formats prompts for console output (plain text, no ANSI colors — see principle CLN)"""

    def __init__(self):
        pass

    def format_prompt(self, prompt: Prompt) -> str:
        """Format a single prompt"""
        lines = []

        priority_str = prompt.priority.name
        lines.append(f"[{priority_str}] {prompt.title}")

        lines.append(f"  {prompt.description}")

        if prompt.file_path:
            loc = prompt.file_path
            if prompt.line_number:
                loc += f":{prompt.line_number}"
            lines.append(f"  Location: {loc}")

        if prompt.metric_current is not None and prompt.metric_target is not None:
            lines.append(f"  Current: {prompt.metric_current:.1f} -> Target: {prompt.metric_target:.1f}")

        if prompt.acceptance_criteria:
            lines.append("  Acceptance Criteria:")
            for criterion in prompt.acceptance_criteria:
                lines.append(f"    - {criterion}")

        return "\n".join(lines)

    def format_header(self, perspective: Perspective, fitness: float, state: OrganismState) -> str:
        """Format the header for a perspective"""
        stage = state.get_stage()

        lines = [
            "",
            "=" * 60,
            f"  PERSPECTIVE: {perspective.value.upper()}",
            f"  Generation: {state.generation}  |  Stage: {stage.value}",
            f"  Fitness: {fitness:.2%}",
            "=" * 60,
            ""
        ]
        return "\n".join(lines)

    def format_summary(self, state: OrganismState, all_prompts: List[Prompt]) -> str:
        """Format a summary of all prompts"""
        lines = [
            "",
            "-" * 60,
            "  SUMMARY",
            "-" * 60,
            f"  Total Prompts: {len(all_prompts)}",
        ]

        by_priority = defaultdict(int)
        for p in all_prompts:
            by_priority[p.priority] += 1

        for priority in Priority:
            if by_priority[priority] > 0:
                lines.append(f"    {priority.name}: {by_priority[priority]}")

        if state.fitness_scores:
            overall = sum(state.fitness_scores.values()) / len(state.fitness_scores)
            lines.append(f"\n  Overall Fitness: {overall:.2%}")

        lines.append("-" * 60)
        return "\n".join(lines)
