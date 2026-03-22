# macOS Desktop App Automation & IntelliJ IDEA Git Flow Capture

I'll answer as a world-famous desktop automation and DevTools expert specializing in IDE instrumentation and CI/CD toolchain integration, recipient of the JetBrains Community Champion award.

Great question, Evgeny — this sits at the intersection of desktop automation, IDE extensibility, and git workflow capture. Let me break down the full landscape and then give you a concrete plan.

## Short Answer

**Playwright alone can't do it** — it's browser-only. But there are several powerful solutions that *can* control macOS native apps including IntelliJ IDEA, and some can be combined with Playwright-like APIs.

## The Solution Landscape

Let me research the current state of these tools to make sure I'm giving you the most up-to-date info:Now I have a comprehensive picture. Let me build you a detailed analysis and action plan.Now let me put together a clear architecture of the available approaches and a concrete step-by-step plan for your specific goal.Here's the full analysis and a concrete action plan.

## Can Playwright do this?

No, not directly. Playwright controls **browsers** (Chromium, Firefox, WebKit), not native macOS apps. IntelliJ IDEA is a Java Swing desktop application. However, the *philosophy* of Playwright (query a component tree → interact → assert) is absolutely achievable through other tools.

## The 3-Layer Strategy

**Layer 1 — JetBrains Remote Robot** is the most powerful option for your goal. It's an official JetBrains library that lets you write and execute UI tests against IntelliJ IDEA, communicating over HTTP protocol with a robot-server plugin running inside the IDE. The latest version is 0.11.23. There's also a newer **Driver Framework** (released Feb 2025) that provides a Kotlin DSL mirroring IntelliJ's Swing component hierarchy, with built-in fixtures for common components like `ideFrame`, `codeEditor`, `button`, and `tree`.

**Layer 2 — macOS Accessibility API** via Python. MacPaw's `macapptree` package extracts the accessibility tree of macOS applications in JSON format, and can capture screenshots with labeled bounding boxes. The older `atomacos` library provides full programmatic control — clicking buttons, reading text, navigating trees.

**Layer 3 — AI Vision agents** like Claude Computer Use can act as a fallback when the above two layers can't reach a specific UI element.

## Step-by-Step Plan to Control IntelliJ & Capture Git Flow

### Phase 1: Set up Remote Robot (the core engine)

**Step 1.1** — Create a Gradle project that includes the Remote Robot dependency:

```kotlin
// build.gradle.kts
repositories {
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}
dependencies {
    testImplementation("com.intellij.remoterobot:remote-robot:0.11.23")
    testImplementation("com.intellij.remoterobot:remote-fixtures:0.11.23")
    testImplementation("com.intellij.remoterobot:ide-launcher:0.11.23")
}
```

**Step 1.2** — Launch IntelliJ IDEA with the robot-server plugin. The library downloads and boots IntelliJ with the robot-server plugin listening on a configured port. Critical macOS flags to set:

```kotlin
val ideaProcess = IdeLauncher.launchIde(
    ideDownloader.downloadAndExtract(Ide.IDEA_COMMUNITY, tmpDir),
    mapOf(
        "robot-server.port" to "8082",
        "ide.mac.file.chooser.native" to "false",       // disable native Mac dialogs
        "jbScreenMenuBar.enabled" to "false",            // disable Mac native menu
        "apple.laf.useScreenMenuBar" to "false"          // keeps menu in IDEA window
    ),
    emptyList(),
    listOf(ideDownloader.downloadRobotPlugin(tmpDir)),
    tmpDir
)
```

On macOS, interaction via `java.awt.Robot` requires granting Accessibility permissions under System Settings → Privacy & Security.

**Step 1.3** — Explore the IntelliJ component tree. Once the IDE is running with robot-server, open `http://localhost:8082` in your browser to see the full Swing component tree rendered as HTML — inspectable with browser DevTools.

### Phase 2: Map the Git UI components

**Step 2.1** — Identify key Git UI fixtures you need to interact with. IntelliJ's Git integration lives in these areas:

| Git operation | IntelliJ UI location | How to find |
|---|---|---|
| Commit | VCS → Commit tool window | `byAccessibleName("Commit")` |
| Branch operations | Bottom status bar / Git Branches popup | `byVisibleText("Git: main")` |
| Push/Pull | VCS menu or toolbar | `byAccessibleName("Push")` |
| Log/History | Git tool window → Log tab | `byAccessibleName("Git")` |
| Merge/Rebase | VCS → Git → Merge/Rebase | Menu navigation |
| Diff viewer | Double-click changed file | `byType("DiffPanel")` |
| Stash | VCS → Git → Stash Changes | Menu path |

**Step 2.2** — Write fixture classes for each Git operation. Example for the commit flow:

```kotlin
@FixtureName("CommitToolWindow")
class CommitFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {

    val commitMessageEditor
        get() = find<JTextAreaFixture>(byXpath("//div[@class='CommitMessage']"))

    val commitButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Commit' and @class='JButton']")
        )

    val changedFilesList
        get() = find<JTreeFixture>(byXpath("//div[@class='ChangesTree']"))

    fun getChangedFiles(): List<String> {
        return callJs("""
            const tree = component;
            const model = tree.getModel();
            const root = model.getRoot();
            const files = [];
            for (let i = 0; i < model.getChildCount(root); i++) {
                files.push(model.getChild(root, i).toString());
            }
            files;
        """)
    }
}
```

**Step 2.3** — Write fixture for branch operations:

```kotlin
class GitBranchFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {

    fun currentBranch(): String {
        return callJs("""
            com.intellij.dvcs.repo.VcsRepositoryManager
                .getInstance(component.project)
                .repositories[0]
                .currentBranchName
        """)
    }

    fun openBranchPopup() {
        find<ComponentFixture>(byXpath(
            "//div[contains(@accessiblename, 'Git:')]"
        )).click()
    }
}
```

### Phase 3: Build the event capture layer

This is where you capture *all* git flow logic happening in the UI.

**Step 3.1** — Use IntelliJ's IDE Scripting Console to inject listeners. The IDE Scripting Console can run Kotlin, JavaScript, and Groovy scripts that automate IDE features and extract information, with full access to the IntelliJ Platform API.

Create a Kotlin script that hooks into Git4Idea events:

```kotlin
// inject-git-listener.kts — run via IDE Scripting Console
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.dvcs.repo.VcsRepositoryManager
import git4idea.repo.GitRepositoryChangeListener
import java.io.File

val project = ProjectManager.getInstance().openProjects.first()
val logFile = File(System.getProperty("user.home") + "/git-flow-log.jsonl")

// Listen for repository changes (branch switch, commit, fetch)
project.messageBus.connect().subscribe(
    GitRepositoryChangeListener.TOPIC,
    GitRepositoryChangeListener { repo ->
        val entry = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "event" to "repo_change",
            "branch" to repo.currentBranchName,
            "state" to repo.state.name,
            "remotes" to repo.remotes.map { it.name }
        )
        logFile.appendText(com.google.gson.Gson().toJson(entry) + "\n")
    }
)
```

**Step 3.2** — Add a VCS action listener to capture every git operation triggered through the UI:

```kotlin
// Also inject via scripting console or as plugin code
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.committed.CommittedChangesListener

project.messageBus.connect().subscribe(
    CommittedChangesListener.TOPIC,
    object : CommittedChangesListener {
        override fun changesLoaded(/*...*/) {
            logEvent("committed_changes_loaded", /*...*/)
        }
    }
)
```

**Step 3.3** — Complement with macOS-level UI capture using `macapptree`:

```python
# pip install macapptree
from macapptree import get_app_tree, capture_screenshot

# Get IntelliJ's accessibility tree as JSON
tree = get_app_tree("IntelliJ IDEA")

# Capture labeled screenshot showing all UI elements
capture_screenshot("IntelliJ IDEA", output_path="intellij_state.png", labels=True)

# Parse the tree to find git-related UI elements
def find_git_elements(node, results=None):
    if results is None:
        results = []
    title = node.get("AXTitle", "") or node.get("AXDescription", "") or ""
    if any(kw in title.lower() for kw in ["git", "commit", "branch", "push", "pull", "merge"]):
        results.append({
            "role": node.get("AXRole"),
            "title": title,
            "position": node.get("AXPosition"),
            "size": node.get("AXSize"),
            "enabled": node.get("AXEnabled"),
        })
    for child in node.get("AXChildren", []):
        find_git_elements(child, results)
    return results

git_elements = find_git_elements(tree)
```

### Phase 4: Orchestrate a full git flow test

**Step 4.1** — Combine everything into an end-to-end scenario:

```kotlin
@Test
fun captureFullGitFlow() {
    val robot = RemoteRobot("http://localhost:8082")

    step("Open project") {
        val welcome = robot.find(WelcomeFrameFixture::class.java)
        welcome.openProjectLink().click()
        // navigate to project path
    }

    step("Create feature branch") {
        val idea = robot.find(IdeaFrameFixture::class.java)
        // Click the branch indicator in status bar
        idea.find<ComponentFixture>(byXpath(
            "//div[contains(@accessiblename, 'Git:')]"
        )).click()
        pause(500)

        // Click "New Branch"
        idea.find<ComponentFixture>(byVisibleText("New Branch")).click()
        pause(300)

        // Type branch name
        keyboard { enterText("feature/my-feature"); enter() }
    }

    step("Make changes and commit") {
        // Open a file
        keyboard {
            hotKey(KeyEvent.VK_META, KeyEvent.VK_SHIFT, KeyEvent.VK_O)
            enterText("Main.kt")
            hotKey(KeyEvent.VK_ENTER)
        }
        pause(1000)

        // Edit file
        val editor = idea.find<EditorFixture>(byXpath("//div[@class='EditorComponentImpl']"))
        editor.click()
        keyboard {
            hotKey(KeyEvent.VK_META, KeyEvent.VK_END)
            enter()
            enterText("// New feature code")
        }

        // Open commit dialog
        keyboard { hotKey(KeyEvent.VK_META, KeyEvent.VK_K) }
        pause(500)

        // Write commit message and commit
        val commitMessage = idea.find<JTextAreaFixture>(
            byXpath("//div[@class='CommitMessage']")
        )
        commitMessage.text = "feat: add new feature"
        idea.find<ComponentFixture>(byVisibleText("Commit")).click()
    }

    step("Push to remote") {
        keyboard { hotKey(KeyEvent.VK_META, KeyEvent.VK_SHIFT, KeyEvent.VK_K) }
        pause(500)
        idea.find<ComponentFixture>(byVisibleText("Push")).click()
    }

    step("Capture final state via accessibility API") {
        // Call Python script to snapshot the UI tree
        Runtime.getRuntime().exec(arrayOf(
            "python3", "capture_git_state.py"
        )).waitFor()
    }
}
```

### Phase 5: Build the analysis pipeline

**Step 5.1** — Parse the `git-flow-log.jsonl` file generated by the event listeners to reconstruct the complete git workflow timeline.

**Step 5.2** — Correlate UI actions (from Remote Robot test logs) with git events (from the message bus listener) to produce a full map of "user clicked X → git operation Y was triggered → result Z appeared in the UI."

**Step 5.3** — Generate a visual report using Mermaid or a React dashboard showing the git flow sequence captured from the IDE.

## Recommended tech stack summary

For your specific use case, I'd recommend this combination:

**Primary control** → JetBrains Remote Robot (Java/Kotlin) — gives you typed access to every Swing component in IntelliJ, including Git tool windows, commit dialogs, branch popups, and diff viewers. Communicates over HTTP so your test runner can sit outside the IDE process.

**Event capture** → IDE Scripting Console injected listeners on `GitRepositoryChangeListener` and `CommittedChangesListener` topics — captures every git event at the IntelliJ Platform API level, not just what you see in the UI.

**UI state snapshots** → `macapptree` (Python) — gives you JSON accessibility tree dumps and labeled screenshots for any moment during the flow, useful for debugging and documentation.

**Fallback for tricky dialogs** → AppleScript / `osascript` for interacting with native macOS dialogs that the Java Robot can't see (like native file choosers, though you should disable those with the launch flags above).
