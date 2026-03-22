# CLAUDE.md — IntelliJ Git Flow Capture

## Project Overview

Automated capture and analysis of IntelliJ IDEA Git UI workflows using JetBrains Remote Robot. Captures UI interactions, correlates them with internal Git events, and generates documentation reports.

**Group:** `vip.trushin.intellij` | **Version:** 1.0.0 | **Language:** Kotlin 1.9.22 | **JVM:** 17+

## Quick Reference

```bash
# Build & unit tests
./gradlew build

# Download robot-server plugin
./gradlew downloadRobotPlugin

# Launch IDE with robot server (port 8082)
./gradlew launchIde

# Run integration tests (requires running IDE)
./gradlew integrationTest

# Run specific workflow test
./gradlew integrationTest --tests "*FeatureBranchFlowTest"

# Generate markdown report from captured events
./gradlew generateReport

# Run workflow via script
./scripts/run-capture.sh feature|pull-merge|rebase|stash|cherry-pick|rollback|pr-review|all
```

## Architecture

Three-layer capture strategy:

1. **JetBrains Remote Robot (PRIMARY)** — Kotlin DSL + XPath selectors, HTTP inspection server on `localhost:8082`
2. **IDE Internal Event Listeners (VERIFICATION)** — Git4Idea message bus topics, manually injected via IDE Scripting Console
3. **macOS Accessibility (FALLBACK)** — AppleScript/osascript for native dialogs and system file choosers

## Project Structure

```
src/main/kotlin/vip/trushin/intellij/
  capture/       — CapturePipeline, ScreenCapture, ReportGenerator
  fixtures/      — Remote Robot UI fixtures (IdeFrame, Commit, Log, Branch, Merge, Push)
  listeners/     — EventListenerBridge (correlates UI events with IDE events)
  model/         — GitFlowEvent data model, EventLogger (JSONL)
  util/          — IdeLauncher, MacAccessibility helpers

src/test/kotlin/vip/trushin/intellij/
  fixtures/      — BaseGitFlowTest (abstract base for workflow tests)
  model/         — Unit tests for event model and logger
  workflows/     — Integration tests for 7 Git workflows (R8.1–R8.7)

ide-scripts/     — Kotlin scripts for IDE Scripting Console (event listeners)
scripts/         — setup.sh (env validation), run-capture.sh (test runner)
docs/            — AI consultation notes
```

## Key Dependencies

- **JetBrains Remote Robot** 0.11.23 — UI automation engine
- **Gson** 2.10.1 — JSON serialization for event logging
- **OkHttp** 4.12.0 — HTTP client for inspection server
- **JUnit Jupiter** 5.10.1 — Test framework
- **Logback** 1.4.14 — Logging

## Testing

**Unit tests:** `./gradlew test` — Event model serialization, logger I/O

**Integration tests:** `./gradlew integrationTest` — Require running IDE with robot-server plugin. Tagged `@Tag("integration")`.

System properties for tests:
- `robot.host` (default: `127.0.0.1`)
- `robot.port` (default: `8082`)
- `captures.dir` (default: `./captures`)
- `event.log` (default: `~/git-flow-log.jsonl`)

### Writing Workflow Tests

1. Create class in `src/test/kotlin/.../workflows/`, extend `BaseGitFlowTest`
2. Use `@TestMethodOrder(MethodOrderer.OrderAnnotation::class)` + `@Order(n)` for sequential steps
3. Call `pipeline.captureAction()` for each Git operation (handles pre/post screenshots + event correlation)
4. Add entry to `scripts/run-capture.sh` case statement

## Code Conventions

- **Kotlin official code style** (`kotlin.code.style=official`)
- **Hierarchical selectors (P3):** Navigate from `robot.ideFrame()` anchor, not global flat searches
- **Stable attributes (P5):** Use `@class` and `@accessiblename` in XPath, avoid localized text
- **Explicit waits (P7):** Use `waitFor()` with conditions, never `Thread.sleep()`
- **Inside-out (P2):** Prefer Git4Idea APIs and IDE message bus over external tools
- **Fixtures:** Extend `CommonContainerFixture`, use `@DefaultXpath`/`@FixtureName` annotations, provide `RemoteRobot` extension functions

## Output Artifacts

| Artifact | Location | Format |
|----------|----------|--------|
| Event log | `~/git-flow-log.jsonl` | JSONL (one GitFlowEvent per line) |
| Screenshots | `captures/` | PNG, timestamped |
| UI tree dumps | `captures/` | XML hierarchy |
| Report | `use-case-report.md` | Markdown with Mermaid diagrams |

## IDE Launch Configuration

IntelliJ is launched with flags to disable native macOS dialogs and enable robot server:
- `-Dide.mac.file.chooser.native=false` — Keeps file choosers as Swing (automatable)
- `-Drobot-server.port=8082` — Inspection server for Remote Robot
- `-Didea.trust.all.projects=true` — Skip trust dialog
- `-Xmx4g` — 4GB heap

## Debugging

```bash
curl -v http://127.0.0.1:8082           # Check robot server
open http://127.0.0.1:8082/hierarchy     # Live UI tree browser
tail -f ~/git-flow-log.jsonl | jq .      # Monitor events
./gradlew integrationTest --tests "*DiagnosticTest" --info  # Diagnostic utilities
```

## Known Constraints

- **macOS Accessibility permissions** must be granted manually (System Settings > Privacy & Security)
- **Single automation session** at a time (macOS + IDE limitation)
- **IntelliJ 2025.3+ Islands UI** may change component names — selectors need maintenance
- **Native dialogs** invisible to Swing automation — disabled via launch flags
- **IDE freezes** (JBR-9171 app switch, JBR-8637 post-sleep) — refocus window before critical actions

## Requirements Spec

Full specification with 90+ requirements in `requirements.md` (R1–R10).
