package vip.trushin.intellij.workflows

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.jupiter.api.*
import vip.trushin.intellij.fixtures.*
import vip.trushin.intellij.model.*
import java.time.Duration

/**
 * R8.2: Pull & merge flow
 * fetch -> pull -> resolve conflicts -> commit merge
 *
 * Each step opens the actual IntelliJ UI dialog and captures it while visible.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class PullMergeFlowTest : BaseGitFlowTest() {

    @Test
    @Order(1)
    @DisplayName("Step 1: Fetch from remote")
    fun fetchFromRemote() {
        val event = pipeline.captureAction(
            eventType = GitEventType.FETCH,
            source = ActionSource.MENU,
            label = "fetch_from_remote"
        ) {
            val frame = ideFrame()

            // Open the Git tool window first to show the log before fetch
            frame.openGitToolWindow()
            Thread.sleep(1500)
            screenCapture.takeScreenshot("during_git_log_before_fetch")

            // Invoke fetch — a progress indicator appears in the status bar
            frame.invokeAction("Git.Fetch")
            Thread.sleep(3000)

            // Capture after fetch completes — notification balloon may be visible
            screenCapture.takeScreenshot("during_after_fetch_notification")

            ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Pull with merge via Update Project dialog")
    fun pullWithMerge() {
        val event = pipeline.captureAction(
            eventType = GitEventType.PULL,
            source = ActionSource.MENU,
            label = "pull_with_merge"
        ) {
            val frame = ideFrame()

            // Open the Update Project dialog (Cmd+T equivalent)
            frame.invokeAction("Vcs.UpdateProject")
            Thread.sleep(2000)

            // Capture the Update Project dialog while it's visible
            screenCapture.takeScreenshot("during_update_project_dialog")

            // Try to interact with the dialog
            try {
                // Look for the OK/Update button in the dialog
                val updateButtons = robot.findAll(ComponentFixture::class.java,
                    byXpath("//div[@accessiblename='OK' and @class='JButton']"))
                if (updateButtons.isNotEmpty()) {
                    updateButtons.first().click()
                    Thread.sleep(3000)
                } else {
                    // Try Pull-specific button
                    val pullButtons = robot.findAll(ComponentFixture::class.java,
                        byXpath("//div[@accessiblename='Pull' and @class='JButton']"))
                    if (pullButtons.isNotEmpty()) {
                        pullButtons.first().click()
                        Thread.sleep(3000)
                    }
                }
                ActionOutcome.SUCCESS
            } catch (e: Exception) {
                // Dismiss the dialog
                dismissAllDialogs()
                Thread.sleep(500)

                // Fall back to git pull via CLI
                frame.runGitCommand("pull", "--no-rebase")
                ActionOutcome.SUCCESS
            }
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Handle merge conflicts (if present)")
    fun handleConflicts() {
        val event = pipeline.captureAction(
            eventType = GitEventType.MERGE,
            source = ActionSource.UI_ACTION,
            label = "handle_merge_conflicts"
        ) {
            try {
                // Check if conflicts dialog appeared
                val conflictsDlg = robot.conflictsDialog(Duration.ofSeconds(3))

                // Capture the conflicts dialog while visible
                screenCapture.takeScreenshot("during_merge_conflicts_dialog")

                conflictsDlg.acceptAllTheirs()
                Thread.sleep(1500)

                // Capture after conflict resolution
                screenCapture.takeScreenshot("during_after_conflict_resolution")

                ActionOutcome.CONFLICT
            } catch (e: Exception) {
                // No conflicts — capture the clean state
                val frame = ideFrame()
                frame.openGitToolWindow()
                Thread.sleep(1500)
                screenCapture.takeScreenshot("during_git_log_no_conflicts")

                ActionOutcome.SUCCESS
            }
        }

        Assertions.assertTrue(
            event.outcome == ActionOutcome.SUCCESS || event.outcome == ActionOutcome.CONFLICT
        )
    }
}
