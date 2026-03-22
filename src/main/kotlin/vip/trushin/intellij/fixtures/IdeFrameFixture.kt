package vip.trushin.intellij.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

/**
 * Top-level IDE frame fixture. All other fixtures are discovered
 * relative to this anchor (P3: hierarchical selectors).
 */
@DefaultXpath(by = "IdeFrameImpl", xpath = "//div[@class='IdeFrameImpl']")
@FixtureName("IDE Frame")
class IdeFrameFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    /**
     * Focus this IDE window (bring to front).
     */
    fun focus() = step("Focus IDE window") {
        callJs<Boolean>("component.toFront(); component.requestFocus(); true;")
        Thread.sleep(300)
    }

    /**
     * Invoke an IDE action by ID on the EDT with project context.
     * This is the reliable way to trigger actions in multi-window environments.
     */
    fun invokeAction(actionId: String) = step("Invoke action: $actionId") {
        callJs<Boolean>("""
            importClass(javax.swing.SwingUtilities);
            var project = component.getProject();
            SwingUtilities.invokeLater(new java.lang.Runnable({
                run: function() {
                    var am = com.intellij.openapi.actionSystem.ActionManager.getInstance();
                    var action = am.getAction("$actionId");
                    if (action != null) {
                        var dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                            .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                            .build();
                        var event = com.intellij.openapi.actionSystem.AnActionEvent.createFromDataContext("", null, dataContext);
                        action.actionPerformed(event);
                    }
                }
            }));
            true;
        """.trimIndent())
    }

    /**
     * Show a tool window by ID on the EDT.
     */
    fun showToolWindow(toolWindowId: String) = step("Show tool window: $toolWindowId") {
        callJs<Boolean>("""
            importClass(javax.swing.SwingUtilities);
            var project = component.getProject();
            SwingUtilities.invokeLater(new java.lang.Runnable({
                run: function() {
                    var twm = com.intellij.openapi.wm.ToolWindowManager.getInstance(project);
                    var tw = twm.getToolWindow("$toolWindowId");
                    if (tw != null) {
                        tw.show(null);
                    }
                }
            }));
            true;
        """.trimIndent())
    }

    /**
     * Access the toolbar branch widget (ToolbarComboButton in MainToolbar).
     */
    fun branchWidget(): ComponentFixture = step("Find branch widget") {
        findAll<ComponentFixture>(
            byXpath("//div[@class='ToolbarComboButton']")
        ).first()
    }

    /**
     * Open the Git tool window via ToolWindowManager (reliable in multi-window).
     */
    fun openGitToolWindow() = step("Open Git tool window") {
        showToolWindow("Git")
        Thread.sleep(1000)
    }

    /**
     * Open the Commit tool window via ToolWindowManager.
     */
    fun openCommitToolWindow() = step("Open Commit tool window") {
        showToolWindow("Commit")
        Thread.sleep(1000)
    }

    /**
     * Open the Git Branches popup via action ID (reliable in multi-window).
     */
    fun openBranchesPopupViaAction() = step("Open branches popup via action") {
        invokeAction("Git.Branches")
        Thread.sleep(2000)
    }

    /**
     * Execute an IDE action by its action ID (legacy - use invokeAction for EDT-safe version).
     */
    fun executeAction(actionId: String) = step("Execute action: $actionId") {
        invokeAction(actionId)
    }

    /**
     * Get the current Git branch name for the project.
     */
    fun getCurrentBranch(): String = step("Get current branch") {
        runGitCommand("rev-parse", "--abbrev-ref", "HEAD")
    }

    /**
     * Checkout a branch programmatically via Git4Idea API on EDT.
     */
    fun checkoutBranch(branchName: String) = step("Checkout branch: $branchName") {
        callJs<Boolean>("""
            importClass(javax.swing.SwingUtilities);
            var project = component.getProject();
            SwingUtilities.invokeLater(new java.lang.Runnable({
                run: function() {
                    var brancher = Packages.git4idea.branch.GitBrancher.getInstance(project);
                    var repoManager = Packages.git4idea.repo.GitRepositoryManager.getInstance(project);
                    var repos = java.util.ArrayList(repoManager.getRepositories());
                    brancher.checkout("$branchName", false, repos, null);
                }
            }));
            true;
        """.trimIndent())
        Thread.sleep(3000)
    }

    /**
     * Create and checkout a new branch programmatically via Git4Idea API.
     */
    fun createAndCheckoutBranch(branchName: String) = step("Create and checkout: $branchName") {
        callJs<Boolean>("""
            importClass(javax.swing.SwingUtilities);
            var project = component.getProject();
            SwingUtilities.invokeLater(new java.lang.Runnable({
                run: function() {
                    var brancher = Packages.git4idea.branch.GitBrancher.getInstance(project);
                    var repoManager = Packages.git4idea.repo.GitRepositoryManager.getInstance(project);
                    var repos = java.util.ArrayList(repoManager.getRepositories());
                    brancher.checkoutNewBranch("$branchName", repos);
                }
            }));
            true;
        """.trimIndent())
        Thread.sleep(3000)
    }

    /**
     * Run a git command in the project's repository root.
     */
    fun runGitCommand(vararg args: String): String = step("Git: ${args.joinToString(" ")}") {
        callJs<String>("""
            var project = component.getProject();
            var basePath = project.getBasePath();
            var cmd = new java.util.ArrayList();
            cmd.add("git");
            ${args.joinToString("\n            ") { "cmd.add(\"$it\");" }}
            var pb = new java.lang.ProcessBuilder(cmd);
            pb.directory(new java.io.File(basePath));
            pb.redirectErrorStream(true);
            var process = pb.start();
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            var result = "";
            var line = reader.readLine();
            while (line != null) {
                result += line + "\n";
                line = reader.readLine();
            }
            process.waitFor();
            result.trim();
        """.trimIndent())
    }
}

/**
 * Extension to find the IDE frame from RemoteRobot.
 * Uses findAll + first() because multiple IdeFrameImpl windows may exist.
 */
fun RemoteRobot.ideFrame(timeout: Duration = Duration.ofSeconds(30)): IdeFrameFixture {
    val frames = findAll(IdeFrameFixture::class.java)
    if (frames.isNotEmpty()) return frames.first()
    // Retry with timeout
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    while (System.currentTimeMillis() < deadline) {
        val retryFrames = findAll(IdeFrameFixture::class.java)
        if (retryFrames.isNotEmpty()) return retryFrames.first()
        Thread.sleep(500)
    }
    throw IllegalStateException("IdeFrameImpl not found within $timeout")
}
