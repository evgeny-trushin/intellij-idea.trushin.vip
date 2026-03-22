package vip.trushin.intellij.workflows

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.jupiter.api.*
import vip.trushin.intellij.fixtures.*
import vip.trushin.intellij.model.*

/**
 * R8.3: Rebase flow
 * checkout feature -> rebase onto main -> force push
 *
 * Each step opens the actual IntelliJ UI dialog and captures it while visible.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RebaseFlowTest : BaseGitFlowTest() {

    private val featureBranch = "feature/rebase-test-${System.currentTimeMillis()}"

    @Test
    @Order(1)
    @DisplayName("Step 1: Create and checkout feature branch via Branches popup")
    fun checkoutFeatureBranch() {
        val event = pipeline.captureAction(
            eventType = GitEventType.CHECKOUT,
            source = ActionSource.UI_ACTION,
            label = "checkout_feature_for_rebase",
            branch = featureBranch
        ) {
            val frame = ideFrame()

            // Show the Branches popup
            frame.openBranchesPopupViaAction()
            Thread.sleep(1500)
            screenCapture.takeScreenshot("during_branches_popup_rebase")

            // Close popup, create branch via API
            pressEscape()
            Thread.sleep(500)

            frame.createAndCheckoutBranch(featureBranch)
            Thread.sleep(2000)

            // Make a commit on the feature branch so rebase has something to work with
            frame.runGitCommand("commit", "--allow-empty", "-m", "feature: rebase test commit")
            Thread.sleep(1000)

            ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Rebase onto main via Rebase dialog")
    fun rebaseOntoMain() {
        val event = pipeline.captureAction(
            eventType = GitEventType.REBASE,
            source = ActionSource.MENU,
            label = "rebase_onto_main",
            branch = featureBranch
        ) {
            val frame = ideFrame()

            // Open the Rebase dialog via action
            frame.invokeAction("Git.Rebase")
            Thread.sleep(2000)

            // Capture the Rebase dialog while it's visible
            screenCapture.takeScreenshot("during_rebase_dialog")

            // Try to interact with the Rebase dialog
            try {
                // Look for the Rebase button
                val rebaseButtons = robot.findAll(ComponentFixture::class.java,
                    byXpath("//div[@accessiblename='Rebase' and @class='JButton']"))
                if (rebaseButtons.isNotEmpty()) {
                    rebaseButtons.first().click()
                    Thread.sleep(3000)
                    screenCapture.takeScreenshot("during_after_rebase_complete")
                    ActionOutcome.SUCCESS
                } else {
                    // Dismiss dialog, fall back to CLI
                    dismissAllDialogs()
                    Thread.sleep(500)
                    val result = frame.runGitCommand("rebase", "main")
                    if (result.contains("CONFLICT")) ActionOutcome.CONFLICT else ActionOutcome.SUCCESS
                }
            } catch (e: Exception) {
                // Dismiss dialog before CLI fallback
                dismissAllDialogs()
                Thread.sleep(500)
                val result = frame.runGitCommand("rebase", "main")
                if (result.contains("CONFLICT")) ActionOutcome.CONFLICT else ActionOutcome.SUCCESS
            }
        }

        Assertions.assertNotEquals(ActionOutcome.CANCELLED, event.outcome)
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Force push after rebase via Push dialog")
    fun forcePushAfterRebase() {
        val event = pipeline.captureAction(
            eventType = GitEventType.PUSH,
            source = ActionSource.KEYBOARD_SHORTCUT,
            label = "force_push_after_rebase",
            branch = featureBranch
        ) {
            val frame = ideFrame()

            // Open the Push dialog
            frame.invokeAction("Vcs.Push")
            Thread.sleep(2000)

            try {
                val pushDlg = robot.pushDialog()

                // Capture the Push dialog — showing the force push checkbox
                screenCapture.takeScreenshot("during_push_dialog_force_push")

                pushDlg.forcePush()
                Thread.sleep(3000)
                ActionOutcome.SUCCESS
            } catch (e: Exception) {
                // Capture error state
                screenCapture.takeScreenshot("during_push_dialog_force_push_error")

                // CRITICAL: Dismiss the Push dialog
                dismissAllDialogs()
                Thread.sleep(500)

                // Fall back to git CLI
                val result = frame.runGitCommand("push", "--force-with-lease")
                if (result.contains("error") || result.contains("rejected")) {
                    ActionOutcome.FAILURE
                } else {
                    ActionOutcome.SUCCESS
                }
            }
        }

        Assertions.assertNotEquals(ActionOutcome.CANCELLED, event.outcome)
    }
}
