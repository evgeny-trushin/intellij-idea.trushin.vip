# Agent Requirements: Run Tests & Capture Git UI Use Cases

## Goal

Run the IntelliJ Git Flow Capture test suite against a live IntelliJ IDEA instance, capture every Git UI interaction as a structured use case with before/after screenshots, UI tree dumps, and detailed step-by-step instructions that allow a human to manually replicate each UI workflow from scratch.

The final deliverable is a `use-case-report.md` file containing visual evidence, action sequences, and precise UI navigation paths for every captured Git workflow.

---

## Prerequisites

Before any test execution, verify the environment is ready:

### 1. System Checks

```bash
# Run the setup script to validate environment
./scripts/setup.sh
```

Confirm:
- macOS 11+ with Xcode command-line tools
- JDK 17+ installed and on PATH
- IntelliJ IDEA (Community or Ultimate) installed in `/Applications` or `~/Applications`
- Git CLI available
- macOS Accessibility permissions granted for the automation tool (System Settings > Privacy & Security > Accessibility)

### 2. Build the Project

```bash
./gradlew build
```

This compiles all source code, runs unit tests, and validates that the `GitFlowEvent` data model, `EventLogger`, and `ScreenCapture` classes work correctly.

### 3. Download the Robot Server Plugin

```bash
./gradlew downloadRobotPlugin
```

This downloads the JetBrains Remote Robot `robot-server-plugin` JAR to `build/robot-plugin/`. This plugin is injected into IntelliJ at launch to expose the HTTP inspection server on port 8082.

### 4. Launch IntelliJ IDEA with Robot Server

```bash
./gradlew launchIde
```

This starts IntelliJ IDEA with:
- Robot server plugin active on `http://127.0.0.1:8082`
- Native macOS file choosers disabled (`-Dide.mac.file.chooser.native=false`)
- Project trust dialog suppressed (`-Didea.trust.all.projects=true`)
- 4GB heap for stable operation (`-Xmx4g`)

**Wait until the IDE is fully loaded.** Verify the robot server is running:

```bash
curl -s http://127.0.0.1:8082 | head -5
```

You should receive an HTML response (the inspection server UI). You can also browse `http://127.0.0.1:8082/hierarchy` in a browser to see the live UI component tree.

### 5. Open a Git-Enabled Project in IntelliJ

The IDE must have a project open that is a Git repository with a configured remote. This is required for push/pull/fetch operations to execute meaningfully.

---

## Test Execution

### Run All Workflow Tests

```bash
./gradlew integrationTest \
  -Drobot.host=127.0.0.1 \
  -Drobot.port=8082 \
  -Dcaptures.dir=./captures \
  -Devent.log=~/git-flow-log.jsonl
```

### Run a Specific Workflow

```bash
# Feature branch flow only
./gradlew integrationTest --tests "*FeatureBranchFlowTest" \
  -Drobot.host=127.0.0.1 \
  -Drobot.port=8082 \
  -Dcaptures.dir=./captures \
  -Devent.log=~/git-flow-log.jsonl

# Or use the convenience script
./scripts/run-capture.sh feature
```

### Available Workflows

| Script Argument | Test Class | Workflow (Requirement) |
|-----------------|------------|----------------------|
| `feature` | `FeatureBranchFlowTest` | Create branch > edit > stage > commit > push (R8.1) |
| `pull-merge` | `PullMergeFlowTest` | Fetch > pull > resolve conflicts > commit merge (R8.2) |
| `rebase` | `RebaseFlowTest` | Checkout feature > rebase onto main > force push (R8.3) |
| `stash` | `StashFlowTest` | Stash changes > switch branch > pop stash (R8.4) |
| `cherry-pick` | `CherryPickFlowTest` | Log > select commit > cherry-pick > push (R8.5) |
| `rollback` | `RollbackFlowTest` | Log > select commit > reset (soft/mixed/hard) (R8.6) |
| `pr-review` | `PrReviewFlowTest` | Create PR > review changes > merge (R8.7) |
| `all` | All of the above | Full suite |

---

## Capture Pipeline: What Happens During Each Test Step

Every test step calls `pipeline.captureAction()` which executes the following sequence:

### Step-by-Step Capture Flow

```
┌────────────────────────────────────────────────────────────────────┐
│  1. PRE-CAPTURE                                                     │
│     ├── Take screenshot → captures/YYYYMMDD_HHMMSS_SSS_before_X.png│
│     └── Dump UI tree   → captures/YYYYMMDD_HHMMSS_SSS_before_X_hierarchy.xml│
│                                                                      │
│  2. EXECUTE ACTION                                                   │
│     ├── Perform UI automation (click, type, invoke IDE action)      │
│     ├── If UI fails → fall back to Git CLI equivalent               │
│     └── Return ActionOutcome: SUCCESS | FAILURE | CONFLICT | CANCELLED│
│                                                                      │
│  3. POST-CAPTURE                                                     │
│     ├── Take screenshot → captures/YYYYMMDD_HHMMSS_SSS_after_X.png │
│     └── Dump UI tree   → captures/YYYYMMDD_HHMMSS_SSS_after_X_hierarchy.xml│
│                                                                      │
│  4. LOG EVENT                                                        │
│     └── Append GitFlowEvent as JSONL to ~/git-flow-log.jsonl        │
│                                                                      │
│  5. CORRELATE (optional)                                             │
│     └── Poll event log for matching IDE-internal Git event          │
└────────────────────────────────────────────────────────────────────┘
```

### Screenshot Strategy

- **Before screenshot**: Shows the IDE state BEFORE the action — the dialog about to be opened, the branch widget before switching, the commit window before typing.
- **After screenshot**: Shows the IDE state AFTER the action — the confirmation notification, the updated branch name, the push result dialog.
- **Fallback**: If Remote Robot's `getScreenshot()` fails, macOS `screencapture -x` is used automatically.
- **Naming**: `{timestamp}_{label}.png` — timestamp is `yyyyMMdd_HHmmss_SSS` format, label describes the action.

### UI Tree Dump Strategy

- Each capture also saves the full Swing component hierarchy as XML from the inspection server (`http://127.0.0.1:8082/hierarchy`).
- These XML dumps record every visible component's class name, accessible name, bounds, and text — enabling precise UI replication.
- **Naming**: `{timestamp}_{label}_hierarchy.xml`

---

## Use Case Descriptions: Detailed UI Replication Instructions

Each workflow below describes the exact IntelliJ IDEA UI interactions captured, with enough detail for a human to replicate them manually.

---

### Use Case 1: Feature Branch Flow (R8.1)

**Goal**: Create a new feature branch, make a code change, commit it, and push to the remote.

#### Step 1 — Create a New Feature Branch

| Detail | Value |
|--------|-------|
| **Action** | Create and checkout a new Git branch |
| **UI Path** | Main toolbar > Branch widget (ToolbarComboButton) > "New Branch..." |
| **Keyboard Alternative** | None (use branch widget or VCS menu) |
| **IDE Action ID** | `Git.Branches` (opens branches popup) |
| **Git CLI Equivalent** | `git checkout -b feature/my-feature` |
| **Expected Visual State (Before)** | IDE main window visible. Bottom status bar or top toolbar shows current branch name (e.g., "main"). No popups or dialogs open. |
| **Expected Visual State (After)** | Branch widget now displays the new branch name. A notification balloon may appear: "Checked out new branch 'feature/my-feature'". |

**Detailed UI Steps to Replicate:**
1. Look at the **main toolbar** at the top of the IntelliJ window. Find the **branch widget** — a button showing the current branch name (e.g., "main") with a dropdown arrow.
2. Click the branch widget. A **Git Branches popup** appears listing local branches, remote branches, and options.
3. Click **"New Branch..."** at the top of the popup.
4. In the **"Create New Branch"** dialog:
   - Type the branch name (e.g., `feature/gitflow-capture-test-1234567890`).
   - Ensure **"Checkout branch"** checkbox is checked.
   - Click **"Create"**.
5. Wait for the branch widget to update to show the new branch name.
6. **Screenshot captures**: Before — IDE with branch widget showing "main". After — IDE with branch widget showing new branch name.

#### Step 2 — Edit a File via IDE

| Detail | Value |
|--------|-------|
| **Action** | Open the "Go to File" dialog to demonstrate file navigation |
| **UI Path** | Navigate > File... (GotoFile dialog) |
| **Keyboard Shortcut** | `Cmd+Shift+O` (macOS) |
| **IDE Action ID** | `GotoFile` |
| **Expected Visual State (Before)** | IDE editor area visible, no popup dialogs. |
| **Expected Visual State (After)** | GotoFile popup appears with a search field. After closing (Escape), the editor area is visible again. |

**Detailed UI Steps to Replicate:**
1. Press `Cmd+Shift+O` or go to **Navigate > File...** in the menu bar.
2. The **"Go to File"** popup appears — a floating search dialog with a text field and file list.
3. Observe the popup (screenshot captured at this point).
4. Press **Escape** to close the popup.
5. **Screenshot captures**: Before — clean editor. After — GotoFile popup visible (or just closed).

#### Step 3 — Stage and Commit Changes

| Detail | Value |
|--------|-------|
| **Action** | Stage all changes and create a commit |
| **UI Path** | Commit tool window (left sidebar) > Message field > "Commit" button |
| **Keyboard Shortcut** | `Cmd+K` (open Commit tool window) |
| **IDE Action ID** | `CheckinProject` (triggers commit dialog/window) |
| **Git CLI Equivalent** | `git add . && git commit -m "test: GitFlow capture test commit"` |
| **Expected Visual State (Before)** | Commit tool window open. Changed files listed in the "Changes" tree. Commit message field is empty. |
| **Expected Visual State (After)** | Commit message field cleared (commit succeeded). Notification: "1 file committed". Changes tree may now be empty. |

**Detailed UI Steps to Replicate:**
1. Press `Cmd+K` or click the **Commit** icon in the left tool window bar to open the **Commit tool window**.
2. The Commit tool window appears showing:
   - **Changed files tree** (top section) — lists modified/new/deleted files with checkboxes.
   - **Commit message editor** (bottom section) — a text area for the commit message.
   - **Commit button** and **Commit and Push...** button at the bottom.
3. Verify that the desired files are checked in the changes tree.
4. Click in the **commit message editor** and type: `test: GitFlow capture test commit`
5. Click the **"Commit"** button.
6. Wait for the commit to complete. A notification balloon appears at the bottom-right confirming the commit.
7. **Screenshot captures**: Before — Commit window with changes listed. After — Commit complete, changes tree cleared.

**Fallback (if UI commit fails):**
The test falls back to `git add -A && git commit -m "test: GitFlow capture test commit"` via the `IdeFrameFixture.runGitCommand()` method, which executes git CLI inside the IDE's project directory.

#### Step 4 — Push to Remote

| Detail | Value |
|--------|-------|
| **Action** | Push the current branch to the remote repository |
| **UI Path** | VCS > Git > Push... (Push dialog) |
| **Keyboard Shortcut** | `Cmd+Shift+K` |
| **IDE Action ID** | `Vcs.Push` |
| **Git CLI Equivalent** | `git push -u origin feature/my-feature` |
| **Expected Visual State (Before)** | IDE main window. No dialogs open. |
| **Expected Visual State (After)** | Push dialog appears showing commits to push, remote/branch selection. After clicking "Push", a notification confirms success or shows an error. |

**Detailed UI Steps to Replicate:**
1. Press `Cmd+Shift+K` or go to **VCS > Git > Push...** in the menu bar.
2. The **Push dialog** appears showing:
   - **Repository/branch selection** at the top (e.g., `origin/feature/my-feature`).
   - **Commits to push** — a list of commits that will be pushed.
   - **Push** and **Cancel** buttons at the bottom.
   - Optional **Force push** checkbox.
3. Review the commits listed.
4. Click **"Push"**.
5. Wait for the push operation. A notification balloon confirms success ("Pushed 1 commit to origin/feature/my-feature") or shows an error.
6. **Screenshot captures**: Before — IDE without dialogs. After — Push dialog visible or push result notification.

---

### Use Case 2: Pull & Merge Flow (R8.2)

**Goal**: Fetch remote changes, pull them into the current branch, resolve any conflicts, and commit the merge.

#### Step 1 — Fetch from Remote

| Detail | Value |
|--------|-------|
| **Action** | Fetch all remote branches |
| **IDE Action ID** | `Git.Fetch` |
| **Git CLI Equivalent** | `git fetch --all` |
| **UI Path** | VCS > Git > Fetch or Git tool window > Fetch button |

**Detailed UI Steps:**
1. Go to **VCS > Git > Fetch** in the menu bar, or use the Git tool window toolbar.
2. IntelliJ fetches from all configured remotes. A progress indicator appears in the bottom status bar.
3. When complete, a notification confirms "Fetched from all remotes" or shows updated branches.

#### Step 2 — Pull with Merge

| Detail | Value |
|--------|-------|
| **Action** | Pull remote changes into the current branch |
| **IDE Action ID** | `Vcs.UpdateProject` |
| **Keyboard Shortcut** | `Cmd+T` |
| **Git CLI Equivalent** | `git pull origin <branch>` |

**Detailed UI Steps:**
1. Press `Cmd+T` or go to **VCS > Update Project...**.
2. The **Update Project** dialog appears with options:
   - **Update Type**: Merge / Rebase / Branch Default
   - **Clean working tree before update**: Stash / Shelve
3. Select **"Merge"** as the update type.
4. Click **"OK"**.
5. If no conflicts: notification confirms successful pull.
6. If conflicts: see Step 3.

#### Step 3 — Resolve Merge Conflicts

| Detail | Value |
|--------|-------|
| **Action** | Resolve merge conflicts via the three-way merge dialog |
| **UI Path** | Conflicts dialog > Select file > "Merge..." button |
| **Git CLI Equivalent** | `git mergetool` (then `git add <file>`) |

**Detailed UI Steps:**
1. When conflicts exist, IntelliJ shows a **Merge Conflicts** dialog listing conflicted files.
2. For each file, you can choose:
   - **Accept Yours** — keep your version
   - **Accept Theirs** — keep the remote version
   - **Merge...** — open the three-way merge editor
3. Click **"Merge..."** to open the **three-way merge editor**:
   - **Left panel**: Your local version
   - **Center panel**: The merge result (editable)
   - **Right panel**: The remote version
   - **Red/green highlights** show conflicts and changes
   - **">>"** and **"<<"** arrows apply individual changes from left or right
4. Resolve all conflicts by choosing or editing each section in the center panel.
5. Click **"Apply"** when all conflicts are resolved.
6. Repeat for each conflicted file.

#### Step 4 — Commit the Merge

| Detail | Value |
|--------|-------|
| **Action** | Commit the merge result |
| **Git CLI Equivalent** | `git commit` (auto-generated merge commit message) |

**Detailed UI Steps:**
1. After resolving all conflicts, the Commit tool window (`Cmd+K`) shows the merged files.
2. A pre-filled merge commit message is present (e.g., "Merge branch 'main' into feature/...").
3. Click **"Commit"**.

---

### Use Case 3: Rebase Flow (R8.3)

**Goal**: Rebase a feature branch onto the latest main, then force push.

#### Step 1 — Checkout the Feature Branch

| Detail | Value |
|--------|-------|
| **UI Path** | Branch widget > select feature branch > "Checkout" |
| **Git CLI Equivalent** | `git checkout feature/my-feature` |

#### Step 2 — Rebase onto Main

| Detail | Value |
|--------|-------|
| **Action** | Rebase current branch onto main |
| **UI Path** | VCS > Git > Rebase... |
| **IDE Action ID** | `Git.Rebase` |
| **Git CLI Equivalent** | `git rebase main` |

**Detailed UI Steps:**
1. Go to **VCS > Git > Rebase...**.
2. The **Rebase** dialog appears:
   - **Onto**: Select the base branch (e.g., "main" or "origin/main").
   - **Interactive**: Optional checkbox to open interactive rebase editor.
3. Select **"main"** (or `origin/main`) as the "onto" branch.
4. Click **"Rebase"**.
5. If conflicts arise, IntelliJ pauses the rebase and shows the conflicts dialog (same as Use Case 2, Step 3). Resolve, then click **"Continue Rebase"** in the notification or VCS menu.
6. When complete, a notification confirms "Successfully rebased onto main".

#### Step 3 — Force Push

| Detail | Value |
|--------|-------|
| **Action** | Force push the rebased branch |
| **UI Path** | Push dialog > check "Force push" > "Push" |
| **Git CLI Equivalent** | `git push --force-with-lease origin feature/my-feature` |

**Detailed UI Steps:**
1. Open the Push dialog (`Cmd+Shift+K`).
2. Check the **"Force push"** checkbox (IntelliJ uses `--force-with-lease` by default for safety).
3. Click **"Push"**.

---

### Use Case 4: Stash Flow (R8.4)

**Goal**: Stash uncommitted changes, switch branches, then restore the stash.

#### Step 1 — Stash Changes

| Detail | Value |
|--------|-------|
| **Action** | Stash all uncommitted changes |
| **UI Path** | VCS > Git > Stash Changes... |
| **IDE Action ID** | `Git.Stash` |
| **Git CLI Equivalent** | `git stash push -m "my stash message"` |

**Detailed UI Steps:**
1. Go to **VCS > Git > Stash Changes...**.
2. The **Stash** dialog appears:
   - **Message** field: Enter a description for this stash.
   - **Keep index** checkbox: If checked, staged changes remain staged.
3. Type a message (e.g., "WIP: feature changes").
4. Click **"Create Stash"**.
5. Working directory is now clean. Changed files disappear from the Commit tool window.

#### Step 2 — Switch Branch

| Detail | Value |
|--------|-------|
| **Action** | Checkout a different branch |
| **UI Path** | Branch widget > select branch > "Checkout" |
| **Git CLI Equivalent** | `git checkout main` |

#### Step 3 — Pop Stash

| Detail | Value |
|--------|-------|
| **Action** | Restore the stashed changes |
| **UI Path** | VCS > Git > UnStash Changes... |
| **IDE Action ID** | `Git.Unstash` |
| **Git CLI Equivalent** | `git stash pop` |

**Detailed UI Steps:**
1. Go to **VCS > Git > UnStash Changes...**.
2. The **UnStash** dialog shows a list of stashes with messages and timestamps.
3. Select the desired stash entry.
4. Choose options:
   - **Pop stash** (apply and remove) or **Apply stash** (apply and keep).
   - **Reinstate index** checkbox.
5. Click **"Pop Stash"** or **"Apply Stash"**.
6. The previously stashed changes reappear in the Commit tool window.

---

### Use Case 5: Cherry-Pick Flow (R8.5)

**Goal**: Select a specific commit from the Git log and cherry-pick it onto the current branch.

#### Step 1 — Open Git Log

| Detail | Value |
|--------|-------|
| **Action** | Open the Git log/history tab |
| **UI Path** | Git tool window (bottom bar) > Log tab |
| **IDE Action ID** | `Vcs.ShowTabbedFileHistory` (or open Git tool window) |
| **Keyboard Alternative** | `Alt+9` (open Git tool window) |

**Detailed UI Steps:**
1. Click the **Git** icon in the bottom tool window bar, or press `Alt+9`.
2. The Git tool window opens with tabs: **Log**, **Console**, etc.
3. Click the **"Log"** tab to see the full commit history.
4. The log shows a commit graph with:
   - **Commit hash** (short), **author**, **date**, **message**
   - **Branch labels** (colored tags showing branch names)
   - **Graph lines** (visual branch topology)

#### Step 2 — Select and Cherry-Pick a Commit

| Detail | Value |
|--------|-------|
| **Action** | Cherry-pick a specific commit |
| **UI Path** | Git Log > right-click commit row > "Cherry-Pick" |
| **Git CLI Equivalent** | `git cherry-pick <commit-hash>` |

**Detailed UI Steps:**
1. In the **Log** tab, browse or search for the target commit.
2. **Right-click** on the commit row.
3. In the context menu, click **"Cherry-Pick"**.
4. IntelliJ applies the commit. If successful, a notification confirms it.
5. If conflicts: resolve via the merge dialog (same as Use Case 2, Step 3).

#### Step 3 — Push

Same as Use Case 1, Step 4.

---

### Use Case 6: Rollback / Reset Flow (R8.6)

**Goal**: Reset the current branch to a previous commit using soft, mixed, or hard reset.

#### Step 1 — Open Git Log

Same as Use Case 5, Step 1.

#### Step 2 — Select Commit and Reset

| Detail | Value |
|--------|-------|
| **Action** | Reset HEAD to a selected commit |
| **UI Path** | Git Log > right-click commit > "Reset Current Branch to Here..." |
| **Git CLI Equivalent** | `git reset --soft/--mixed/--hard <commit>` |

**Detailed UI Steps:**
1. In the **Log** tab, find the target commit to reset to.
2. **Right-click** on the commit row.
3. Click **"Reset Current Branch to Here..."**.
4. The **Reset** dialog appears with mode options:
   - **Soft** — moves HEAD only; staged changes and working directory preserved.
   - **Mixed** (default) — moves HEAD and unstages; working directory preserved.
   - **Hard** — moves HEAD, unstages, and discards all working directory changes.
5. Select the desired mode.
6. Click **"Reset"**.
7. The branch pointer moves. The Log tab updates to show the new HEAD position.
8. **Warning**: Hard reset is destructive — uncommitted changes are lost permanently.

---

### Use Case 7: PR Review Flow (R8.7)

**Goal**: Create a Pull Request, review changes, and merge — requires the GitHub plugin.

#### Step 1 — Create Pull Request

| Detail | Value |
|--------|-------|
| **Action** | Create a new Pull Request |
| **UI Path** | VCS > Git > Create Pull Request... (or GitHub tool window) |
| **IDE Action ID** | `Github.Create.Pull.Request` |

**Detailed UI Steps:**
1. Ensure the **GitHub** plugin is installed and authenticated.
2. Go to **VCS > Git > Create Pull Request...**.
3. The **Create Pull Request** dialog appears:
   - **From**: current branch
   - **To**: target branch (e.g., main)
   - **Title**: PR title
   - **Description**: Markdown-formatted PR body
4. Fill in the title and description.
5. Click **"Create Pull Request"**.
6. IntelliJ opens the PR in the GitHub tool window or shows a notification with a link.

#### Step 2 — Review Changes

| Detail | Value |
|--------|-------|
| **Action** | Review PR changes in the diff viewer |
| **UI Path** | GitHub tool window > Pull Requests tab > select PR > view files |

**Detailed UI Steps:**
1. Open the **GitHub** or **Pull Requests** tool window.
2. Select the PR from the list.
3. Click on individual files to see diffs:
   - **Side-by-side diff** (default) or **unified diff**.
   - Changed lines highlighted in green (additions) and red (deletions).
4. Add review comments by clicking the **"+"** icon in the gutter next to a line.

#### Step 3 — Merge PR

| Detail | Value |
|--------|-------|
| **Action** | Merge the Pull Request |
| **UI Path** | GitHub tool window > PR detail > "Merge" button |

**Detailed UI Steps:**
1. In the PR detail view, click the **"Merge"** button.
2. Choose merge strategy: **Merge commit**, **Squash and merge**, or **Rebase and merge**.
3. Confirm the merge.
4. The PR status changes to "Merged".

---

## Report Generation

After all tests complete, generate the final report:

```bash
./gradlew generateReport \
  -Devent.log=~/git-flow-log.jsonl \
  -Dcaptures.dir=./captures \
  -Dreport.output=./use-case-report.md
```

### Report Contents

The generated `use-case-report.md` includes:

1. **Summary table** — total events captured, event types, success rate, time range.
2. **Events by type** — tabular breakdown with timestamp, source, branch, outcome, and Git CLI equivalent for each event.
3. **Detailed event log** — for every captured event:
   - UUID, timestamp, event type, action source
   - UI path (which component was interacted with)
   - Branch name, affected files, commit hashes
   - Outcome (SUCCESS / FAILURE / CONFLICT / CANCELLED)
   - Git CLI equivalent command
   - Embedded screenshot (relative path to PNG in `captures/`)
4. **Mermaid sequence diagram** — visual workflow diagram showing User > IDE > Git > Remote interactions.
5. **Screenshot gallery** — all captured PNGs indexed by timestamp and label.

### Output Artifacts

| Artifact | Path | Description |
|----------|------|-------------|
| Event log | `~/git-flow-log.jsonl` | Structured JSONL, one `GitFlowEvent` per line |
| Screenshots | `captures/*.png` | Timestamped before/after screenshots per action |
| UI tree dumps | `captures/*_hierarchy.xml` | Full Swing component hierarchy at each step |
| Report | `use-case-report.md` | Markdown with tables, screenshots, Mermaid diagrams |

---

## Event Data Structure

Every captured action produces a `GitFlowEvent` in the JSONL log:

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": "2026-03-22T14:30:00.000Z",
  "type": "COMMIT",
  "source": "KEYBOARD_SHORTCUT",
  "uiPath": "stage_and_commit",
  "branch": "feature/my-feature",
  "files": ["src/Main.kt"],
  "commits": ["abc1234"],
  "outcome": "SUCCESS",
  "screenshot": "/path/to/captures/20260322_143000_000_after_stage_and_commit.png",
  "uiTreeDump": "/path/to/captures/20260322_143000_000_after_stage_and_commit_hierarchy.xml",
  "metadata": {}
}
```

### Event Types

| Type | Description | Typical Source |
|------|-------------|---------------|
| `BRANCH_CREATE` | New branch created and checked out | UI_ACTION, MENU |
| `CHECKOUT` | Switched to an existing branch | UI_ACTION |
| `COMMIT` | Changes committed to local repo | KEYBOARD_SHORTCUT, UI_ACTION |
| `PUSH` | Commits pushed to remote | KEYBOARD_SHORTCUT |
| `PULL` | Remote changes pulled (fetch + merge) | KEYBOARD_SHORTCUT |
| `FETCH` | Remote refs fetched without merge | MENU |
| `MERGE` | Branch merged into current branch | MENU, UI_ACTION |
| `REBASE` | Current branch rebased onto another | MENU |
| `STASH` | Working changes stashed | MENU |
| `CHERRY_PICK` | Specific commit applied to current branch | UI_ACTION |
| `RESET` | HEAD moved to a previous commit | UI_ACTION |

---

## Monitoring & Debugging During Test Runs

### Watch events in real-time

```bash
tail -f ~/git-flow-log.jsonl | python3 -m json.tool
```

### Browse live UI tree

Open `http://127.0.0.1:8082/hierarchy` in a browser during test execution to see the current Swing component tree. This is essential for debugging fixture selectors.

### Review screenshots as they are captured

```bash
# macOS: open the captures folder in Finder
open captures/

# Or watch for new files
fswatch captures/ | while read f; do echo "New capture: $f"; done
```

### Check IDE logs for errors

```bash
tail -f ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
```

### Run diagnostic test for interactive exploration

```bash
./gradlew integrationTest --tests "*DiagnosticTest" --info
```

---

## Success Criteria

The agent run is considered successful when:

1. All 7 workflow tests execute (some steps may fail due to missing remote or conflicts — that is expected and captured as `FAILURE` or `CONFLICT` outcomes).
2. The `captures/` directory contains before/after PNG screenshots for every test step.
3. The `captures/` directory contains corresponding `_hierarchy.xml` UI tree dumps.
4. The `~/git-flow-log.jsonl` file contains one `GitFlowEvent` per test step.
5. The `use-case-report.md` is generated with:
   - Summary statistics
   - Event tables with Git CLI equivalents
   - Embedded screenshot references
   - Mermaid sequence diagram
6. A human can read the report and replicate every workflow manually in IntelliJ IDEA by following the step-by-step UI instructions, knowing exactly which menus to open, which buttons to click, which keyboard shortcuts to use, and what the expected visual outcome looks like at each step.
