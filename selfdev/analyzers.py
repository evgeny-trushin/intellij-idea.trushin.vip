"""
Code and Git analyzers for the Self-Development Organism system.
"""

import ast
import subprocess
from pathlib import Path
from typing import Dict, List, Optional

from models import (
    FileAnalysis,
    ANALYZABLE_DIRS,
    TEST_DIRS,
    COMPLEXITY_THRESHOLD,
    MAX_FILE_LINES,
)


class CodeAnalyzer:
    """Analyzes code structure and metrics"""

    def __init__(self, root_dir: Path):
        self.root_dir = root_dir
        self.file_analyses: Dict[str, FileAnalysis] = {}

    def analyze_file(self, file_path: Path) -> Optional[FileAnalysis]:
        """Analyze a single Python file"""
        if not file_path.exists() or not file_path.suffix == ".py":
            return None

        try:
            content = file_path.read_text(encoding="utf-8")
            tree = ast.parse(content)
        except (SyntaxError, UnicodeDecodeError):
            return FileAnalysis(
                path=str(file_path.relative_to(self.root_dir)),
                lines=0,
                functions=0,
                classes=0,
                imports=0,
                complexity=0,
                has_tests=False,
                issues=["Syntax error in file"]
            )

        lines = len(content.splitlines())
        functions = sum(1 for node in ast.walk(tree) if isinstance(node, ast.FunctionDef))
        classes = sum(1 for node in ast.walk(tree) if isinstance(node, ast.ClassDef))
        imports = sum(1 for node in ast.walk(tree) if isinstance(node, (ast.Import, ast.ImportFrom)))

        complexity = self._calculate_complexity(tree)

        rel_path = str(file_path.relative_to(self.root_dir))
        has_tests = any(td in rel_path for td in TEST_DIRS) or "test" in file_path.name.lower()

        issues = []
        if lines > MAX_FILE_LINES:
            issues.append(f"File too long: {lines} lines (max {MAX_FILE_LINES})")
        if complexity > COMPLEXITY_THRESHOLD:
            issues.append(f"High complexity: {complexity:.1f} (max {COMPLEXITY_THRESHOLD})")

        return FileAnalysis(
            path=rel_path,
            lines=lines,
            functions=functions,
            classes=classes,
            imports=imports,
            complexity=complexity,
            has_tests=has_tests,
            issues=issues
        )

    def _calculate_complexity(self, tree: ast.AST) -> float:
        """Calculate simplified cyclomatic complexity"""
        complexity = 1
        for node in ast.walk(tree):
            if isinstance(node, (ast.If, ast.While, ast.For, ast.ExceptHandler)):
                complexity += 1
            elif isinstance(node, ast.BoolOp):
                complexity += len(node.values) - 1
        return complexity

    def analyze_directory(self, dir_path: Path) -> Dict[str, FileAnalysis]:
        """Analyze all Python files in a directory"""
        results = {}
        if not dir_path.exists():
            return results

        for file_path in dir_path.rglob("*.py"):
            if "__pycache__" in str(file_path):
                continue
            analysis = self.analyze_file(file_path)
            if analysis:
                results[analysis.path] = analysis

        return results

    def get_all_analyses(self) -> Dict[str, FileAnalysis]:
        """Analyze all relevant directories.

        Scans pre-defined directory names *and* auto-discovers any
        immediate sub-directory of *root_dir* that contains ``.py``
        files (e.g. ``selfdev/``).
        """
        all_results = {}

        scanned: set = set()

        for dir_name in ANALYZABLE_DIRS + TEST_DIRS:
            dir_path = self.root_dir / dir_name
            if dir_path.is_dir():
                scanned.add(dir_path.resolve())
                results = self.analyze_directory(dir_path)
                all_results.update(results)

        # Auto-discover sub-directories containing Python files
        for child in sorted(self.root_dir.iterdir()):
            if not child.is_dir() or child.name.startswith("."):
                continue
            resolved = child.resolve()
            if resolved in scanned:
                continue
            if any(child.rglob("*.py")):
                scanned.add(resolved)
                results = self.analyze_directory(child)
                all_results.update(results)

        for file_path in self.root_dir.glob("*.py"):
            analysis = self.analyze_file(file_path)
            if analysis:
                all_results[analysis.path] = analysis

        self.file_analyses = all_results
        return all_results


class GitAnalyzer:
    """Analyzes Git history and state"""

    def __init__(self, root_dir: Path):
        self.root_dir = root_dir

    def get_current_hash(self) -> str:
        """Get current commit hash"""
        try:
            result = subprocess.run(
                ["git", "rev-parse", "HEAD"],
                cwd=self.root_dir,
                capture_output=True,
                text=True
            )
            return result.stdout.strip()[:8]
        except Exception:
            return ""

    def get_recent_commits(self, count: int = 10) -> List[Dict]:
        """Get recent commits"""
        try:
            result = subprocess.run(
                ["git", "log", f"-{count}", "--pretty=format:%H|%s|%ad", "--date=iso"],
                cwd=self.root_dir,
                capture_output=True,
                text=True
            )
            commits = []
            for line in result.stdout.strip().split("\n"):
                if "|" in line:
                    parts = line.split("|", 2)
                    commits.append({
                        "hash": parts[0][:8],
                        "message": parts[1] if len(parts) > 1 else "",
                        "date": parts[2] if len(parts) > 2 else ""
                    })
            return commits
        except Exception:
            return []

    def get_uncommitted_changes(self) -> List[str]:
        """Get list of uncommitted files"""
        try:
            result = subprocess.run(
                ["git", "status", "--porcelain"],
                cwd=self.root_dir,
                capture_output=True,
                text=True
            )
            return [line.strip() for line in result.stdout.strip().split("\n") if line.strip()]
        except Exception:
            return []

    def get_branch(self) -> str:
        """Get current branch name"""
        try:
            result = subprocess.run(
                ["git", "branch", "--show-current"],
                cwd=self.root_dir,
                capture_output=True,
                text=True
            )
            return result.stdout.strip()
        except Exception:
            return "unknown"

    def get_changed_files_in_last_commit(self) -> List[str]:
        """Return list of files changed in the most recent commit."""
        try:
            result = subprocess.run(
                ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD"],
                cwd=self.root_dir,
                capture_output=True,
                text=True,
            )
            return [f for f in result.stdout.strip().split("\n") if f.strip()]
        except Exception:
            return []

    def get_commits_for_increment(self, increment_number: int) -> List[Dict]:
        """Return commits whose message mentions the given increment number.

        Searches for patterns like ``INCREMENT 0001`` or ``increment_0001``
        in the git log.
        """
        tag = f"INCREMENT {increment_number:04d}"
        tag_alt = f"increment_{increment_number:04d}"
        try:
            result = subprocess.run(
                ["git", "log", "--all", "--pretty=format:%H|%s|%ad",
                 "--date=iso", f"--grep={tag}"],
                cwd=self.root_dir,
                capture_output=True,
                text=True,
            )
            lines = result.stdout.strip().split("\n") if result.stdout.strip() else []
            # Also search the alternative tag
            result2 = subprocess.run(
                ["git", "log", "--all", "--pretty=format:%H|%s|%ad",
                 "--date=iso", f"--grep={tag_alt}"],
                cwd=self.root_dir,
                capture_output=True,
                text=True,
            )
            lines2 = result2.stdout.strip().split("\n") if result2.stdout.strip() else []

            seen_hashes = set()
            commits = []
            for line in lines + lines2:
                if "|" not in line:
                    continue
                parts = line.split("|", 2)
                h = parts[0]
                if h in seen_hashes:
                    continue
                seen_hashes.add(h)
                commits.append({
                    "hash": h,
                    "message": parts[1] if len(parts) > 1 else "",
                    "date": parts[2] if len(parts) > 2 else "",
                })
            return commits
        except Exception:
            return []

    def get_diff_for_commit(self, commit_hash: str) -> str:
        """Return the diff introduced by a specific commit."""
        try:
            result = subprocess.run(
                ["git", "diff", f"{commit_hash}~1", commit_hash,
                 "--stat"],
                cwd=self.root_dir,
                capture_output=True,
                text=True,
            )
            return result.stdout.strip()
        except Exception:
            return ""

    def get_commits_in_range(self, from_increment: int,
                             to_increment: int) -> List[Dict]:
        """Return commits for all increments in the range [from, to]."""
        all_commits: List[Dict] = []
        seen: set = set()
        for num in range(from_increment, to_increment + 1):
            for c in self.get_commits_for_increment(num):
                if c["hash"] not in seen:
                    seen.add(c["hash"])
                    all_commits.append(c)
        return all_commits
