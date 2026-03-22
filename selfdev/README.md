# Self-Development Organism

A Python-based codebase analysis tool inspired by biological development principles. It examines code from multiple perspectives and generates prioritised development prompts with actionable acceptance criteria.

Reference: [Self-Development Organism](https://github.com/evgeny-trushin/selfdev)

## How It Works

The system treats software development as biological evolution. A codebase progresses through **development stages** (Embryonic, Growth, Maturation, Homeostasis) across **generations**. Each generation, the organism analyses the code from five perspectives and produces ranked prompts — concrete tasks ordered by impact on overall fitness.

State is persisted in `organism_state.json` so the system remembers its evolutionary history across runs.

## Development Workflow

Follow these steps **in order** for every generation:

1. **Run `./develop.sh`** — generates prioritised prompts based on codebase state
2. **Implement the prompts** — make the actual code changes described by the prompts
3. **Run `./develop.sh --advance`** — records fitness scores and advances the generation
4. **Commit & push everything together** — `git add -A && git commit && git push`

> **Important:** Never commit `organism_state.json` changes alone.
> Always commit them together with the implementation that addresses the prompts.

## Quick Start

```bash
# Run all perspectives (default)
./develop.sh

# Run a single perspective
./develop.sh --user
./develop.sh --test
./develop.sh --system
./develop.sh --analytics
./develop.sh --debug

# Combine perspectives
./develop.sh --user --test

# View current organism state
./develop.sh --state

# Advance to the next generation after implementing prompts
./develop.sh --advance

# Revert a specific increment using git history
./develop.sh --revert=0001

# Revert from increment 0020 down to the current todo
./develop.sh --revert_from=0020

# Revert and re-implement an increment from scratch
./develop.sh --redo=0001

# Analyse the selfdev project itself
./develop.sh --self

# Analyse an arbitrary directory
./develop.sh --root /path/to/project

# Disable coloured output
./develop.sh --no-color
```

Requires **Python 3.8+** with no external dependencies (stdlib only).

## Revert and Redo

Three commands let you roll back or redo increments using git history:

| Command | Description |
|---------|-------------|
| `--revert=N` | Generate a prompt to **revert** increment N. Lists all git commits matching `INCREMENT N` and produces `git revert` commands in reverse chronological order. If the increment file is marked done it suggests renaming it back to todo. |
| `--revert_from=N` | Generate a prompt to **revert a range** of increments from N back to the current todo. Covers every increment in the range and lists commits for each one. |
| `--redo=N` | **Revert then re-implement** increment N. Outputs the revert instructions first, then the full requirement (description, acceptance criteria, principles) so you can implement it from scratch. |

### Example: revert a single increment

```bash
./develop.sh --revert=0001
```

Output includes:
- All git commits whose message contains `INCREMENT 0001`
- A `--stat` diff summary for each commit
- Step-by-step `git revert --no-commit` commands
- Post-revert cleanup (rename done → todo)

### Example: revert a range

```bash
./develop.sh --revert_from=0020
```

Reverts increments 0020, 0019, … down to the current todo increment, processing them in reverse order.

### Example: redo an increment

```bash
./develop.sh --redo=0001
```

Combines the revert prompt with the original requirement prompt so you can undo the previous implementation and start fresh.

## Perspectives

| Perspective | Flag | Analyses | Key Metrics |
|-------------|------|----------|-------------|
| **User** | `--user` | Documentation, README, package metadata | Content quality, completeness |
| **Test** | `--test` | Test coverage, test-to-source ratio | File coverage, complex file testing |
| **System** | `--system` | Architecture, complexity, file length | Cyclomatic complexity (<10), file lines (<300) |
| **Analytics** | `--analytics` | Fitness trends, commit patterns | Regression detection, fix rate |
| **Debug** | `--debug` | TODO/FIXME comments, uncommitted changes | Issue count, technical debt |

Each perspective returns a **fitness score** (0-100%) and a list of prioritised prompts (CRITICAL > HIGH > MEDIUM > LOW > INFO).

## Development Stages

| Stage | Generations | Focus |
|-------|-------------|-------|
| Embryonic | 0-3 | Basic functionality and survival |
| Growth | 4-10 | Specialisation and optimisation |
| Maturation | 11-20 | Refinement and stability |
| Homeostasis | 21+ | Maintenance mode |

## Prompt Format

Each generated prompt includes:

- **Priority** — CRITICAL, HIGH, MEDIUM, LOW, or INFO
- **Title** — short description of the task
- **Description** — context about the current problem
- **Location** — file path and optional line number
- **Metrics** — current value vs target value
- **Acceptance criteria** — specific conditions for completion

## Project Structure

```
project/
├── organism_state.json    # Persisted evolutionary state
├── requirements.md        # System requirements specification
├── principles.md          # Biological and lateral thinking principles
└── selfdev/
    ├── organism.py        # Core analyser (perspectives, state, CLI)
    ├── develop.sh         # Shell wrapper with coloured banner
    ├── tests/
    │   ├── __init__.py
    │   └── test_organism.py # unittest suite (890+ lines)
    └── README.md          # This file
```

## Running Tests

```bash
python -m pytest tests/ -v
```

## Design Principles

The system is guided by two sets of principles defined in `principles.md`:

**Biological** — Morphogenesis through priority gradients, differentiation over time, apoptosis of dead code, and feedback loops for stability and growth.

**Lateral Thinking** — Challenge assumptions before generating prompts, enter the codebase from different angles per perspective, and escape local optima when metrics plateau.

See `principles.md` for the full set and `requirements.md` for the detailed specification.
