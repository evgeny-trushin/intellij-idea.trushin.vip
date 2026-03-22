# IntelliJ IDEA Git Flow Capture — Principles & Requirements

## Goal

Build a macOS solution that programmatically runs IntelliJ IDEA, captures all windows/dialogs/interactions related to Git functionality, and extracts the underlying logic, UI structure, and workflow patterns.

---

## Principles

### P1: Component Tree Over Pixels

IntelliJ IDEA is built on Java Swing/AWT — its UI is a navigable hierarchical component tree (like a DOM), not an opaque canvas. All automation must leverage this structured tree, never image-based pixel matching. This ensures stability across themes, resolutions, and minor UI updates.

### P2: Inside-Out Over Outside-In

Prefer tools that operate *inside* the IntelliJ Platform (JetBrains Driver, Remote Robot, IDE Scripting Console) over external macOS-level tools (AppleScript, Appium). Inside-out tools have typed access to Swing components, Git4Idea APIs, and the message bus. Outside-in tools see a "black box."

### P3: Hierarchical Selectors Over Flat Search

Always locate UI elements by navigating the component hierarchy from a known anchor (e.g., `ideFrame { button(byAccessibleName("Commit")) }`). Flat global searches are fragile when multiple components share the same name. Hierarchical paths are self-documenting and resilient.

### P4: Event Capture + UI Capture = Complete Picture

UI automation alone captures *what buttons were clicked*. Internal event listeners (GitRepositoryChangeListener, CommittedChangesListener) capture *what Git operations actually executed*. Both layers together produce a complete, verifiable record.

### P5: Stable Attributes Over Volatile Text

Prefer selectors based on `accessiblename`, `javaclass`, or `myicon` over `visible_text` which changes with localization and UI updates. Discover attributes via the live inspection server, not guesswork.

### P6: macOS Accessibility Is a Gate, Not a Tool

macOS Accessibility permissions must be granted manually (System Settings → Privacy & Security → Accessibility). This cannot be automated or bypassed. Plan for it as a one-time setup step, not a runtime concern.

### P7: Resilience Over Speed

IDE UI is asynchronous — dialogs appear after variable delays, operations complete at different speeds. Use explicit waits and assertions (`shouldBe(present)`, `waitUntilEnabled()`) rather than fixed `sleep()` calls.

---

## Architecture

### Three-Layer Strategy

```
┌─────────────────────────────────────────────────┐
│  Layer 1: JetBrains Driver / Remote Robot        │  PRIMARY
│  Kotlin DSL → Swing component tree               │
│  HTTP inspection server on localhost              │
│  Typed fixtures for Git dialogs/windows           │
├─────────────────────────────────────────────────┤
│  Layer 2: IDE Internal Event Listeners            │  LOGIC CAPTURE
│  Git4Idea message bus topics                      │
│  GitRepositoryChangeListener                      │
│  CommittedChangesListener                         │
│  VCS action hooks                                 │
├─────────────────────────────────────────────────┤
│  Layer 3: macOS Accessibility (fallback)          │  FALLBACK
│  macapptree / AppleScript / osascript             │
│  Native file choosers, system dialogs             │
│  Screenshot capture (screencapture)               │
└─────────────────────────────────────────────────┘
```

---

## Requirements

### R1: Environment Setup

- **R1.1** macOS 11+ with Xcode command-line tools installed
- **R1.2** IntelliJ IDEA (Community or Ultimate) installed
- **R1.3** JDK 17+ available for Driver framework / Remote Robot
- **R1.4** Accessibility permissions granted for the automation tool executable
- **R1.5** Disable native macOS dialogs in IntelliJ launch flags:
  - `ide.mac.file.chooser.native=false`
  - `jbScreenMenuBar.enabled=false`
  - `apple.laf.useScreenMenuBar=false`

### R2: Git UI Component Map

The solution must identify and interact with these IntelliJ Git UI areas:

| Git Operation | UI Location | Discovery Method |
|---|---|---|
| Commit | Commit tool window / ⌘K dialog | `byAccessibleName("Commit")` |
| Push | Push dialog / ⌘⇧K | `byAccessibleName("Push")` |
| Pull / Fetch | VCS menu → Pull / Update Project | Menu navigation |
| Branch create/switch | Status bar branch widget / Branches popup | `byVisibleText("Git: <branch>")` |
| Merge | VCS → Git → Merge | Menu navigation → dialog |
| Rebase | VCS → Git → Rebase | Menu navigation → dialog |
| Log / History | Git tool window → Log tab | `byAccessibleName("Git")` |
| Diff viewer | Double-click changed file | `byType("DiffPanel")` |
| Stash / Shelve | VCS → Git → Stash Changes | Menu navigation |
| Resolve conflicts | Merge conflicts dialog | Event-triggered dialog |
| Cherry-pick | Log → right-click commit | Context menu |
| Reset | Log → right-click commit → Reset | Context menu → dialog |

### R3: Capture Data Model

Every captured interaction must produce a structured event:

```
GitFlowEvent {
  id:          string       // UUID
  timestamp:   ISO-8601     // When it happened
  type:        enum         // COMMIT, PUSH, PULL, FETCH, MERGE, REBASE,
                            // CHECKOUT, BRANCH_CREATE, STASH, CHERRY_PICK, RESET
  source:      enum         // UI_ACTION | KEYBOARD_SHORTCUT | MENU | TOOLBAR
  uiPath:      string       // Hierarchical component path clicked
  branch:      string       // Current branch at time of action
  files:       string[]     // Files involved (if applicable)
  commits:     string[]     // Commit hashes (if applicable)
  outcome:     enum         // SUCCESS | FAILURE | CONFLICT | CANCELLED
  screenshot:  string       // Path to screenshot taken at this step
  uiTreeDump:  string       // Path to inspection server HTML dump
}
```

### R4: Automation Engine

- **R4.1** Use JetBrains Remote Robot (v0.11.23+) or Driver Framework as primary engine
- **R4.2** Launch IntelliJ via `IdeLauncher` with robot-server plugin on port 8082
- **R4.3** Provide Kotlin fixture classes for each Git dialog:
  - `CommitFixture` — commit message, changed files tree, commit/amend buttons
  - `BranchFixture` — branch popup, new branch dialog, checkout
  - `PushFixture` — push dialog, remote selection, force push checkbox
  - `MergeFixture` — merge dialog, strategy selection
  - `LogFixture` — commit list, diff panel, context menu actions
- **R4.4** Expose inspection server at `http://localhost:8082` for interactive discovery

### R5: Event Listener Layer

- **R5.1** Subscribe to `GitRepositoryChangeListener.TOPIC` for repo state changes
- **R5.2** Subscribe to `CommittedChangesListener.TOPIC` for commit events
- **R5.3** Subscribe to `GIT_AUTHENTICATION_SUCCESS` for remote operations
- **R5.4** Use `GitBranchIncomingOutgoingManager` to distinguish push from pull/fetch
- **R5.5** Log all events as JSONL to `~/git-flow-log.jsonl`

### R6: Capture Pipeline

- **R6.1** Before each action: capture screenshot + UI tree dump
- **R6.2** Execute the action via Driver/Remote Robot
- **R6.3** After each action: capture screenshot + UI tree dump + wait for event confirmation
- **R6.4** Correlate UI action logs with internal Git event logs
- **R6.5** Support screen recording via `screencapture -v` for full session video

### R7: Output Artifacts

The solution must produce:

- **R7.1** `git-flow-log.jsonl` — Structured event stream from internal listeners
- **R7.2** `captures/` directory — Timestamped screenshots at each step
- **R7.3** `captures/*_hierarchy.xml` — UI tree dumps at each step
- **R7.4** `use-case-report.md` — Human-readable document mapping:
  - UI action → Git command equivalent → outcome → screenshot evidence
- **R7.5** Optional: Mermaid sequence diagram of the captured workflow

### R8: Supported Git Workflows to Capture

Minimum set of end-to-end flows:

1. **Feature branch flow**: create branch → edit file → stage → commit → push
2. **Pull & merge flow**: fetch → pull → resolve conflicts → commit merge
3. **Rebase flow**: checkout feature → rebase onto main → force push
4. **Stash flow**: stash changes → switch branch → pop stash
5. **Cherry-pick flow**: log → select commit → cherry-pick → push
6. **Rollback flow**: log → select commit → reset (soft/mixed/hard)
7. **PR review flow** (if GitHub plugin enabled): create PR → review changes → merge

### R9: Technology Stack

| Component | Technology | Purpose |
|---|---|---|
| Automation engine | JetBrains Remote Robot 0.11.23 / Driver Framework | Swing component tree navigation |
| Build system | Gradle (Kotlin DSL) | Dependency management, test runner |
| Language | Kotlin | Scripts, fixtures, test orchestration |
| Event capture | IDE Scripting Console / LivePlugin | Git4Idea message bus listeners |
| macOS fallback | macapptree (Python) / AppleScript | Native dialogs, accessibility tree |
| Screenshot | Remote Robot `getScreenshot()` + `screencapture` | Visual evidence |
| Output format | JSONL + Markdown + PNG | Structured logs + reports |

### R10: Known Constraints

- **R10.1** macOS Accessibility permissions cannot be granted programmatically
- **R10.2** Only one automation session at a time (macOS limitation)
- **R10.3** IntelliJ "Islands" UI (2025.3+) may change component names — selectors need version-specific maintenance
- **R10.4** CloudFront distribution creation for hosting results takes 15-40 min
- **R10.5** IDE freezes on app switch (JBR-9171) and after sleep (JBR-8637) can disrupt sessions
- **R10.6** Native macOS file choosers are invisible to Swing automation — must be disabled via launch flags

---

## Implementation Phases

### Phase 1: MVP — Single Git Action Capture
- Set up Gradle project with Remote Robot dependencies
- Launch IntelliJ with robot-server plugin
- Capture one action: open Git Log tool window
- Take screenshot + dump UI tree
- Validate inspection server works at localhost:8082

### Phase 2: Git Component Discovery
- Manually perform all Git operations listed in R2
- Use inspection server to map every component's `accessiblename`
- Build fixture classes for each dialog (R4.3)
- Document stable selectors in a reference table

### Phase 3: Event Listener Integration
- Inject Git4Idea event listeners via IDE Scripting Console
- Capture events to JSONL log
- Validate correlation between UI clicks and internal events

### Phase 4: End-to-End Flow Capture
- Script all 7 workflows from R8
- Run with full logging + screenshots
- Generate use-case reports

### Phase 5: Analysis & Documentation
- Correlate UI actions with Git command equivalents
- Generate Mermaid diagrams
- Package as a reusable capture toolkit

---

## Source Research

Analysis synthesized from 5 independent expert consultations:

| Source | Primary Recommendation | Unique Contribution |
|---|---|---|
| `docs/opus.md` | Remote Robot + IDE Scripting Console + macapptree | 3-layer architecture, fixture code examples, event listener injection |
| `docs/gemini.md` | Remote Robot via plugin install + manual inspection | Step-by-step setup guide, visual automation fallback (SikuliX) |
| `docs/qwen.md` | JetBrains Driver Framework (Kotlin DSL) | Deep architectural analysis, selector strategy, live inspection server |
| `docs/perplexity.md` | Hybrid: IntelliJ APIs (Plan B) + AppleScript (Plan A) | GitFlowEvent data model, IDE Scripting Console approach, passive listeners |
| `docs/grok.md` | Appium Mac2 Driver (Python) | Appium-based alternative, XML hierarchy dumps, screen recording |

**Consensus across all sources:**
1. Playwright cannot automate IntelliJ (browser-only)
2. JetBrains Remote Robot / Driver Framework is the best primary tool
3. Internal IDE event listeners complement UI capture
4. macOS Accessibility permissions are a mandatory manual step
5. Image-based automation (SikuliX, PyAutoGUI) is too brittle for production use
