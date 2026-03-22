package vip.trushin.intellij.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

/**
 * Fixture for the Commit tool window (R4.3).
 * In IntelliJ 2024.2 New UI, the commit panel is inline (ChangesViewCommitPanelSplitter)
 * rather than a separate dialog. The tool window decorator may use different class names.
 */
@DefaultXpath(
    by = "CommitToolWindow",
    xpath = "//div[@class='ChangesViewCommitPanelSplitter' or (@accessiblename='Commit' and @class='InternalDecoratorImpl')]"
)
@FixtureName("Commit Tool Window")
class CommitFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    /**
     * The commit message editor text area.
     */
    val commitMessageEditor
        get() = find<JTextAreaFixture>(
            byXpath("//div[@class='CommitMessage' or @class='EditorComponentImpl']"),
            Duration.ofSeconds(5)
        )

    /**
     * The changed files tree showing staged/unstaged files.
     * In 2024.2 New UI, this is LocalChangesListView.
     */
    val changedFilesTree
        get() = find<JTreeFixture>(
            byXpath("//div[@class='LocalChangesListView' or @class='ChangesTree']"),
            Duration.ofSeconds(5)
        )

    /**
     * The "Commit" button.
     */
    val commitButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Commit' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    /**
     * The "Commit and Push..." button (dropdown variant).
     */
    val commitAndPushButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Commit and Push...' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    /**
     * The "Amend" checkbox for amending the previous commit.
     */
    val amendCheckbox
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Amend' and @class='JBCheckBox']"),
            Duration.ofSeconds(3)
        )

    /**
     * Type a commit message and click the commit button.
     */
    fun commitWithMessage(message: String) = step("Commit with message: $message") {
        commitMessageEditor.click()
        remoteRobot.callJs<Boolean>("""
            const editor = component;
            editor.setText("$message");
            true;
        """.trimIndent())
        commitButton.click()
    }

    /**
     * Type a commit message and commit + push.
     */
    fun commitAndPushWithMessage(message: String) = step("Commit and push: $message") {
        commitMessageEditor.click()
        remoteRobot.callJs<Boolean>("""
            const editor = component;
            editor.setText("$message");
            true;
        """.trimIndent())
        commitAndPushButton.click()
    }

    /**
     * Get the list of changed file names visible in the tree.
     */
    fun getChangedFileNames(): List<String> = step("Get changed file names") {
        changedFilesTree.collectRows().map { it.toString() }
    }

    /**
     * Check the amend checkbox.
     */
    fun enableAmend() = step("Enable amend commit") {
        val checkbox = amendCheckbox
        if (!checkbox.callJs<Boolean>("component.isSelected()")) {
            checkbox.click()
        }
    }

    /**
     * Wait for the commit to complete (tool window updates).
     */
    fun waitForCommitComplete(timeout: Duration = Duration.ofSeconds(15)) =
        step("Wait for commit to complete") {
            waitFor(timeout, description = "Commit operation to finish") {
                try {
                    commitMessageEditor.text.isEmpty()
                } catch (e: Exception) {
                    true // Editor cleared or dialog closed = commit done
                }
            }
        }
}

/**
 * Find the Commit tool window from within the IDE frame.
 * In 2024.2 New UI, searches for the inline commit panel (ChangesViewCommitPanelSplitter).
 */
fun IdeFrameFixture.commitToolWindow(timeout: Duration = Duration.ofSeconds(10)): CommitFixture {
    // Use findAll to handle multi-window environments
    val fixtures = remoteRobot.findAll(CommitFixture::class.java)
    if (fixtures.isNotEmpty()) return fixtures.first()
    // Retry with timeout
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    while (System.currentTimeMillis() < deadline) {
        val retry = remoteRobot.findAll(CommitFixture::class.java)
        if (retry.isNotEmpty()) return retry.first()
        Thread.sleep(500)
    }
    throw IllegalStateException("CommitFixture not found within $timeout")
}
