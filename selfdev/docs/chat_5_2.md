##Answering example##

I will answer as a world famous developmental biology and complex systems engineer with Australia’s Prime Minister’s Prize for Science.

### 1) How a living organism builds itself from 1 cell

A 1 cell embryo is not “simple”. It is a compressed program plus a self running factory.

Step by step, what actually happens:

1. Information storage
   DNA stores recipes for proteins, but also control logic for when and where to use them.

2. Replication with controlled noise
   The cell copies itself again and again. Copying is very accurate, but not magical. There are repair systems, and rare errors.

3. Gene regulation creates different cell types
   Cells with the same DNA become different because different genes are switched on or off. This is done by gene regulatory networks: proteins that control other proteins, forming circuits with feedback.

4. Positional information
   Cells learn “where they are” by reading signals. Some signals form gradients: more signal here, less there. That lets identical cells choose different fates based on location.

5. Local rules create global structure
   No cell has the full blueprint as a picture. Each cell follows local rules: listen to neighbours, emit signals, divide, move, specialise, stop. Global shape emerges from local interactions.

6. Checkpoints and programmed failure
   Development uses quality gates. Cells that are wrong or damaged can self destruct. This is not drama, it is maintenance.

7. Memory layers beyond DNA
   Epigenetics stores “settings” on top of DNA: which parts are accessible. That lets a liver cell stay a liver cell after many divisions.

8. Homeostasis
   Mature organisms keep variables stable: temperature, sugar, pH. They do this with negative feedback loops and repair.

That combination is the trick: compact rules, local sensing, feedback, and relentless testing.

### 2) Self development principles you can steal for software

Here are living principles translated into “how software can grow from a small seed”:

1. Genome
   A small core spec plus constraints that rarely change.

2. Differentiation
   Same codebase, different modules that specialise: planner, builder, tester, documenter.

3. Signalling
   Modules communicate through typed messages, not shared hidden state.

4. Morphogens
   A global objective function that creates “gradients of priority”: reliability first, then speed, then features.

5. Checkpoints
   Nothing becomes “part of the body” unless it passes tests and safety rules.

6. Memory
   A state file that records decisions, scores, and what worked.

7. Selection
   Many candidate changes, few accepted. Acceptance is based on measured fitness.

8. Repair
   Automatic bug fixing loops triggered by failing tests.

9. Energy budget
   Limit time, tokens, and file change size per generation.

10. Boundedness
    A hard sandbox: only modify files inside the repo, only run approved commands, no surprise networking.

### 3) Concrete implementation plan for “software that grows like an organism”

1. Create a repo that contains 5 parts

1) genome folder: rules and prompts
2) src folder: the “body” code
3) tests folder: the immune system
4) tasks folder: the environment pressures
5) organism_state.json: memory

2. Define fitness
   Minimum fitness is “all tests pass”. Better fitness adds coverage, performance, documentation quality, and fewer defects.

3. Define a growth cycle per generation

1) sense: read tasks and current state
2) decide: pick 1 task
3) propose mutation: ask a model for file edits in strict JSON
4) apply: write edited files
5) validate: run tests
6) score: compute fitness
7) select: accept if fitness improves and rules are respected
8) record: update state and task status
9) reproduce: commit to git and push to remote

4. Make it safe by construction

1) allow only specific folders to be edited
2) cap number of files changed per generation
3) cap total bytes changed per generation
4) require tests to pass for acceptance
5) keep automatic rollback on failure

5. Run it repeatedly
   Each run is 1 generation. Over time the repo accumulates validated adaptations.

### 4) Working Python implementation you can run

This uses 2 public facts:
OpenAI Responses API is a POST to `/v1/responses` and supports `model`, `input`, `instructions`. ([OpenAI Platform][1])
OpenAI API keys use bearer authentication. ([OpenAI Platform][2])
Dulwich provides pure Python git operations like init, add, commit, push. ([Dulwich][3])

Install dependencies with pip by installing these packages: `dulwich` and `requests`. Then create the files exactly as below.

#### File: `requirements.txt`

```txt
dulwich==0.25.0
requests==2.32.3
```

#### File: `organism_state.json`

```json
{
  "generation": 0,
  "accepted": 0,
  "rejected": 0,
  "last_task_id": null,
  "fitness_history": [],
  "branch": "main"
}
```

#### File: `tasks/backlog.json`

```json
{
  "tasks": [
    {
      "id": "1",
      "status": "todo",
      "title": "Add a safe string slug function",
      "description": "Implement slugify(text) in src/product/core.py. It must lower case, keep letters and digits, convert spaces to underscores, collapse repeated underscores, and strip leading or trailing underscores.",
      "acceptance": [
        "All tests pass",
        "slugify(\"Hello World\") returns \"hello_world\"",
        "slugify(\"  A  B  \") returns \"a_b\"",
        "slugify(\"A!B@C\") returns \"a_b_c\""
      ]
    },
    {
      "id": "2",
      "status": "todo",
      "title": "Add a tiny metrics counter",
      "description": "Implement Metrics class in src/product/core.py with inc(name) and get(name). Add tests.",
      "acceptance": [
        "All tests pass",
        "Metrics starts at 0 for unseen keys",
        "inc increments by 1"
      ]
    },
    {
      "id": "3",
      "status": "todo",
      "title": "Improve organism robustness",
      "description": "Improve JSON extraction to handle extra surrounding text. Add tests for extract_first_json_object in organism_runner.py.",
      "acceptance": [
        "All tests pass",
        "extract_first_json_object returns valid parsed JSON when JSON is wrapped in other text"
      ]
    }
  ]
}
```

#### File: `genome/policy.txt`

```txt
Organism policy

1 Only edit files inside these folders
src
tests
genome
tasks

2 Never delete organism_state.json

3 Each generation may edit at most 5 files

4 Each generation may write at most 120000 bytes total across all edited files

5 Accept a mutation only if all tests pass

6 If tests fail, rollback all edits from that generation

7 Output edits as strict JSON with this shape
{
  "edits": [
    { "path": "relative_path", "content": "full_file_content" }
  ],
  "notes": "short explanation"
}
```

#### File: `genome/system_prompt.txt`

```txt
You are a careful software developer inside a self evolving organism.

You must output only strict JSON.
No markdown.
No extra commentary.

Follow organism policy.

Goal:
Given a single task and a snapshot of current files, propose a minimal set of file edits to satisfy acceptance criteria.

Rules:
1 Provide full file content for every edited file
2 Do not invent new folders outside policy
3 Keep edits minimal
4 Add tests in tests folder when implementing new behaviour
5 Ensure code is valid Python 3
6 Prefer unittest from the standard library
7 Output only the JSON object described in policy
```

#### File: `src/product/core.py`

```python
from __future__ import annotations

from dataclasses import dataclass


def slugify(text: str) -> str:
    raise NotImplementedError("slugify is not implemented yet")


@dataclass
class Metrics:
    def inc(self, name: str) -> None:
        raise NotImplementedError("Metrics.inc is not implemented yet")

    def get(self, name: str) -> int:
        raise NotImplementedError("Metrics.get is not implemented yet")
```

#### File: `tests/test_core.py`

```python
import unittest

from src.product.core import slugify, Metrics


class TestSlugify(unittest.TestCase):
    def test_basic(self) -> None:
        self.assertEqual(slugify("Hello World"), "hello_world")

    def test_collapse_spaces(self) -> None:
        self.assertEqual(slugify("  A  B  "), "a_b")

    def test_punct_to_underscore(self) -> None:
        self.assertEqual(slugify("A!B@C"), "a_b_c")

    def test_strip_edges(self) -> None:
        self.assertEqual(slugify("  A  "), "a")


class TestMetrics(unittest.TestCase):
    def test_unseen_is_zero(self) -> None:
        m = Metrics()
        self.assertEqual(m.get("x"), 0)

    def test_inc(self) -> None:
        m = Metrics()
        m.inc("x")
        m.inc("x")
        self.assertEqual(m.get("x"), 2)


if __name__ == "__main__":
    unittest.main()
```

#### File: `organism_runner.py`

```python
from __future__ import annotations

import copy
import hashlib
import json
import os
import time
import unittest
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Tuple

import requests
from dulwich import porcelain


ROOT = Path(__file__).resolve().parent
STATE_PATH = ROOT / "organism_state.json"
BACKLOG_PATH = ROOT / "tasks" / "backlog.json"
SYSTEM_PROMPT_PATH = ROOT / "genome" / "system_prompt.txt"
POLICY_PATH = ROOT / "genome" / "policy.txt"

ALLOWED_TOP_DIRS = {"src", "tests", "genome", "tasks"}
MAX_FILES_PER_GEN = 5
MAX_TOTAL_BYTES_PER_GEN = 120000


@dataclass
class Task:
    id: str
    status: str
    title: str
    description: str
    acceptance: List[str]


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def load_json(path: Path) -> Any:
    return json.loads(read_text(path))


def save_json(path: Path, obj: Any) -> None:
    tmp = path.with_suffix(".tmp")
    write_text(tmp, json.dumps(obj, ensure_ascii=False, indent=2))
    tmp.replace(path)


def sha256_text(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def ensure_repo() -> None:
    git_dir = ROOT / ".git"
    if git_dir.exists():
        return
    porcelain.init(str(ROOT))


def stage_policy_scoped_files() -> None:
    paths: List[str] = []
    for top in sorted(ALLOWED_TOP_DIRS):
        top_path = ROOT / top
        if not top_path.exists():
            continue
        for p in top_path.rglob("*"):
            if p.is_file():
                paths.append(str(p.relative_to(ROOT)))
    paths.append(str(STATE_PATH.relative_to(ROOT)))
    for rel in paths:
        porcelain.add(str(ROOT), rel)


def commit_local(message: str) -> None:
    porcelain.commit(str(ROOT), message.encode("utf-8"))


def push_if_configured() -> None:
    remote = os.environ.get("GIT_REMOTE_URL", "").strip()
    if not remote:
        return
    branch = os.environ.get("GIT_BRANCH", "").strip() or "main"
    username = os.environ.get("GIT_USERNAME", "").strip() or "token"
    password = os.environ.get("GIT_PASSWORD", "").strip()
    if not password:
        return
    porcelain.push(str(ROOT), remote, branch, username=username, password=password)


def discover_and_run_tests() -> Tuple[bool, int, int]:
    loader = unittest.TestLoader()
    suite = loader.discover(str(ROOT / "tests"))
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    fails = len(result.failures) + len(result.errors)
    return result.wasSuccessful(), result.testsRun, fails


def is_allowed_path(rel_path: str) -> bool:
    rel = Path(rel_path)
    if rel.is_absolute():
        return False
    parts = rel.parts
    if not parts:
        return False
    return parts[0] in ALLOWED_TOP_DIRS


def extract_first_json_object(text: str) -> Dict[str, Any]:
    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end == -1 or end <= start:
        raise ValueError("No JSON object found in model output")
    blob = text[start : end + 1]
    return json.loads(blob)


def openai_propose_edits(system_prompt: str, task: Task, snapshot: Dict[str, str]) -> Dict[str, Any]:
    api_key = os.environ.get("OPENAI_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY is not set")
    model = os.environ.get("OPENAI_MODEL", "").strip()
    if not model:
        raise RuntimeError("OPENAI_MODEL is not set")

    payload = {
        "model": model,
        "instructions": system_prompt,
        "input": json.dumps(
            {
                "task": {
                    "id": task.id,
                    "title": task.title,
                    "description": task.description,
                    "acceptance": task.acceptance,
                },
                "policy": read_text(POLICY_PATH),
                "snapshot": snapshot,
            },
            ensure_ascii=False,
        ),
    }

    r = requests.post(
        "https://api.openai.com/v1/responses",
        headers={"Authorization": f"Bearer {api_key}"},
        json=payload,
        timeout=180,
    )
    r.raise_for_status()
    data = r.json()

    output_texts: List[str] = []
    for item in data.get("output", []):
        if item.get("type") != "message":
            continue
        for c in item.get("content", []):
            if c.get("type") == "output_text":
                output_texts.append(c.get("text", ""))
    combined = "\n".join(output_texts).strip()
    if not combined:
        raise RuntimeError("Model returned no output text")
    return extract_first_json_object(combined)


def build_snapshot() -> Dict[str, str]:
    wanted_files: List[Path] = []

    for rel in [
        Path("src") / "product" / "core.py",
        Path("organism_runner.py"),
        Path("genome") / "policy.txt",
        Path("genome") / "system_prompt.txt",
    ]:
        p = ROOT / rel
        if p.exists():
            wanted_files.append(p)

    snap: Dict[str, str] = {}
    for p in wanted_files:
        snap[str(p.relative_to(ROOT))] = read_text(p)
    return snap


def load_state() -> Dict[str, Any]:
    if not STATE_PATH.exists():
        save_json(
            STATE_PATH,
            {
                "generation": 0,
                "accepted": 0,
                "rejected": 0,
                "last_task_id": None,
                "fitness_history": [],
                "branch": "main",
            },
        )
    return load_json(STATE_PATH)


def load_tasks() -> List[Task]:
    obj = load_json(BACKLOG_PATH)
    tasks: List[Task] = []
    for t in obj.get("tasks", []):
        tasks.append(
            Task(
                id=str(t.get("id", "")),
                status=str(t.get("status", "")),
                title=str(t.get("title", "")),
                description=str(t.get("description", "")),
                acceptance=list(t.get("acceptance", [])),
            )
        )
    return tasks


def save_tasks(tasks: List[Task]) -> None:
    obj = {"tasks": []}
    for t in tasks:
        obj["tasks"].append(
            {
                "id": t.id,
                "status": t.status,
                "title": t.title,
                "description": t.description,
                "acceptance": t.acceptance,
            }
        )
    save_json(BACKLOG_PATH, obj)


def pick_next_task(tasks: List[Task]) -> Task:
    for t in tasks:
        if t.status == "todo":
            return t
    raise RuntimeError("No todo tasks found")


def apply_edits(edits: List[Dict[str, Any]]) -> Dict[str, str]:
    if len(edits) > MAX_FILES_PER_GEN:
        raise RuntimeError("Too many files edited in 1 generation")

    backups: Dict[str, str] = {}
    total_bytes = 0

    for e in edits:
        path = str(e.get("path", ""))
        content = str(e.get("content", ""))
        if not path:
            raise RuntimeError("Edit missing path")
        if not is_allowed_path(path) and path != "organism_runner.py":
            raise RuntimeError("Edit path not allowed by policy")
        if path == str(STATE_PATH.name):
            raise RuntimeError("Direct edits to organism_state.json are not allowed")

        total_bytes += len(content.encode("utf-8"))
        if total_bytes > MAX_TOTAL_BYTES_PER_GEN:
            raise RuntimeError("Total edited bytes exceed budget")

        abs_path = ROOT / path
        if abs_path.exists():
            backups[path] = read_text(abs_path)
        else:
            backups[path] = ""

        write_text(abs_path, content)

    return backups


def rollback(backups: Dict[str, str]) -> None:
    for path, old in backups.items():
        abs_path = ROOT / path
        if old == "":
            if abs_path.exists():
                abs_path.unlink()
        else:
            write_text(abs_path, old)


def compute_fitness(tests_ok: bool, tests_run: int, fails: int) -> int:
    if not tests_ok:
        return 0
    base = 1000
    bonus = min(200, tests_run * 5)
    penalty = min(500, fails * 100)
    return base + bonus - penalty


def main() -> None:
    ensure_repo()

    state = load_state()
    tasks = load_tasks()

    task = pick_next_task(tasks)
    for t in tasks:
        if t.id == task.id:
            t.status = "doing"
    save_tasks(tasks)

    system_prompt = read_text(SYSTEM_PROMPT_PATH)
    snapshot = build_snapshot()

    gen = int(state.get("generation", 0)) + 1
    print(f"Generation {gen} starting at {utc_now_iso()}")
    print(f"Task {task.id}: {task.title}")

    backups: Dict[str, str] = {}
    accepted = False
    start = time.time()

    try:
        proposal = openai_propose_edits(system_prompt, task, snapshot)
        edits = proposal.get("edits", [])
        if not isinstance(edits, list):
            raise RuntimeError("edits is not a list")

        backups = apply_edits(edits)

        tests_ok, tests_run, fails = discover_and_run_tests()
        fitness = compute_fitness(tests_ok, tests_run, fails)

        prev_best = 0
        for h in state.get("fitness_history", []):
            prev_best = max(prev_best, int(h.get("fitness", 0)))

        accepted = bool(tests_ok and fitness >= prev_best)

        if accepted:
            for t in tasks:
                if t.id == task.id:
                    t.status = "done"
            save_tasks(tasks)

            state["accepted"] = int(state.get("accepted", 0)) + 1
            state["last_task_id"] = task.id

            stage_policy_scoped_files()
            commit_local(f"gen {gen} task {task.id} fitness {fitness}")
            push_if_configured()
        else:
            rollback(backups)
            for t in tasks:
                if t.id == task.id:
                    t.status = "todo"
            save_tasks(tasks)
            state["rejected"] = int(state.get("rejected", 0)) + 1

        state["generation"] = gen
        state.setdefault("fitness_history", [])
        state["fitness_history"].append(
            {
                "generation": gen,
                "task_id": task.id,
                "accepted": accepted,
                "fitness": fitness,
                "tests_run": tests_run,
                "fails": fails,
                "time_sec": int(time.time() - start),
                "notes_hash": sha256_text(str(proposal.get("notes", ""))),
                "when": utc_now_iso(),
            }
        )
        save_json(STATE_PATH, state)

        print(f"Generation {gen} finished. accepted={accepted} fitness={fitness}")

    except Exception as ex:
        if backups:
            rollback(backups)
        for t in tasks:
            if t.id == task.id and t.status == "doing":
                t.status = "todo"
        save_tasks(tasks)
        print(f"Generation {gen} crashed: {ex}")
        raise


if __name__ == "__main__":
    main()
```

### 5) How this behaves like a living organism, concretely

1. Genome
   `genome/policy.txt` and `genome/system_prompt.txt` are the developmental constraints and instructions.

2. Differentiation
   The runner plays roles: sensing, planning, building, testing, selecting.

3. Checkpoints
   Unittest pass is your embryonic checkpoint. No pass, no survival.

4. Memory
   `organism_state.json` records lineage and fitness history.

5. Selection
   Only mutations that meet the gate and do not regress best fitness are accepted.

6. Repair
   Failed generations rollback automatically.

7. Reproduction
   Accepted generations become commits, and optionally push to a remote using Dulwich. ([Dulwich][3])

### 6) What to set in your environment

1. `OPENAI_API_KEY`
2. `OPENAI_MODEL`
3. Optional for pushing to a remote
   `GIT_REMOTE_URL`
   `GIT_BRANCH`
   `GIT_USERNAME`
   `GIT_PASSWORD`

The Responses endpoint and auth approach used by the script match OpenAI’s API reference. ([OpenAI Platform][1])

That is the core “1 cell to organism” pattern applied to software: a small genome, local rules, feedback, selection, and memory. The next evolutionary upgrade is richer fitness signals, like coverage increase, linting, and performance microbenchmarks, so the organism can climb more interesting hills than “tests pass”.

[1]: https://platform.openai.com/docs/api-reference/responses "Responses | OpenAI API Reference"
[2]: https://platform.openai.com/docs/api-reference/introduction?utm_source=chatgpt.com "API Reference - OpenAI API"
[3]: https://dulwich.readthedocs.io/en/latest/tutorial/porcelain.html "Porcelain — dulwich 0.25.0 documentation"

