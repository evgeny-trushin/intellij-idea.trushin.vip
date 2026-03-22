"""
Data models, constants, and enums for the Self-Development Organism system.
"""

import json
from datetime import datetime, timezone
from pathlib import Path
from dataclasses import dataclass, field, asdict
from typing import Dict, List, Optional
from enum import Enum


# ==================== Constants ====================

SELFDEV_DIR = Path(__file__).resolve().parent
ROOT_DIR = SELFDEV_DIR.parent  # default: analyze the project (../), not selfdev itself
STATE_FILE = ROOT_DIR / "organism_state.json"
REQUIREMENTS_DIR = ROOT_DIR / "requirements"
PRINCIPLES_DIR = ROOT_DIR / "principles"

# Directories to analyze
ANALYZABLE_DIRS = ["src", "components", "pages", "lib", "utils", "services"]
TEST_DIRS = ["tests", "__tests__", "test", "spec"]

# Thresholds
COMPLEXITY_THRESHOLD = 10  # McCabe complexity
COVERAGE_TARGET = 80  # Percentage
MAX_FILE_LINES = 300
MAX_FUNCTION_LINES = 50


# ==================== Enums ====================

class DevelopmentStage(Enum):
    EMBRYONIC = "embryonic"      # Generation 0-3
    GROWTH = "growth"            # Generation 4-10
    MATURATION = "maturation"    # Generation 11-20
    HOMEOSTASIS = "homeostasis"  # Generation 21+


class Perspective(Enum):
    USER = "user"
    TEST = "test"
    SYSTEM = "system"
    ANALYTICS = "analytics"
    DEBUG = "debug"



class Priority(Enum):
    CRITICAL = 1
    HIGH = 2
    MEDIUM = 3
    LOW = 4
    INFO = 5


# ==================== Data Classes ====================

@dataclass
class Prompt:
    """A generated development prompt"""
    perspective: Perspective
    priority: Priority
    title: str
    description: str
    file_path: Optional[str] = None
    line_number: Optional[int] = None
    metric_current: Optional[float] = None
    metric_target: Optional[float] = None
    acceptance_criteria: List[str] = field(default_factory=list)
    tags: List[str] = field(default_factory=list)


@dataclass
class FileAnalysis:
    """Analysis results for a single file"""
    path: str
    lines: int
    functions: int
    classes: int
    imports: int
    complexity: float
    has_tests: bool
    issues: List[str] = field(default_factory=list)


@dataclass
class OrganismState:
    """Current state of the self-developing organism"""
    generation: int = 0
    created_at: str = ""
    last_updated: str = ""
    development_stage: str = "embryonic"
    fitness_scores: Dict[str, float] = field(default_factory=dict)
    fitness_history: List[Dict] = field(default_factory=list)
    prompts_generated: int = 0
    prompts_completed: int = 0
    last_git_hash: str = ""
    last_increment_shown: int = 0

    @classmethod
    def load(cls, path: Path) -> "OrganismState":
        """Load state from file or create new"""
        if path.exists():
            try:
                with open(path, "r") as f:
                    data = json.load(f)
                # Filter to only known fields to avoid TypeError on unknown keys
                known_fields = {f.name for f in cls.__dataclass_fields__.values()}
                filtered = {k: v for k, v in data.items() if k in known_fields}
                return cls(**filtered)
            except (json.JSONDecodeError, TypeError):
                pass
        return cls(created_at=datetime.now(timezone.utc).isoformat())

    def save(self, path: Path) -> None:
        """Save state to file"""
        self.last_updated = datetime.now(timezone.utc).isoformat()
        with open(path, "w") as f:
            json.dump(asdict(self), f, indent=2)

    def get_stage(self) -> DevelopmentStage:
        """Get current development stage based on generation"""
        if self.generation <= 3:
            return DevelopmentStage.EMBRYONIC
        elif self.generation <= 10:
            return DevelopmentStage.GROWTH
        elif self.generation <= 20:
            return DevelopmentStage.MATURATION
        else:
            return DevelopmentStage.HOMEOSTASIS
