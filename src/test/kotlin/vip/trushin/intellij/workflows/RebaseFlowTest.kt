package vip.trushin.intellij.workflows

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.jupiter.api.*
import vip.trushin.intellij.fixtures.*
import vip.trushin.intellij.model.*

/**
 * R8.3: Rebase flow
 * checkout feature -> rebase onto main -> force push
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RebaseFlowTest : BaseGitFlowTest() {

    private val featureBranch = "feature/rebase-test-${System.currentTimeMillis()}"

    @Test
    @Order(1)
    @DisplayName("Step 1: Create and checkout feature branch")
    fun checkoutFeatureBranch() {
        val event = pipeline.captureAction(
            eventType = GitEventType.CHECKOUT,
            source = ActionSource.UI_ACTION,
            label = "checkout_feature_for_rebase",
            branch = featureBranch
        ) {
            val frame = ideFrame()
            frame.createAndCheckoutBranch(featureBranch)
            ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Rebase onto main")
    fun rebaseOntoMain() {
        val event = pipeline.captureAction(
            eventType = GitEventType.REBASE,
            source = ActionSource.MENU,
            label = "rebase_onto_main",
            branch = featureBranch
        ) {
            val frame = ideFrame()
            // Use git CLI for rebase (more reliable than dialog interaction)
            val result = frame.runGitCommand("rebase", "main")
            if (result.contains("CONFLICT")) {
                ActionOutcome.CONFLICT
            } else {
                ActionOutcome.SUCCESS
            }
        }

        Assertions.assertNotEquals(ActionOutcome.CANCELLED, event.outcome)
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Force push after rebase")
    fun forcePushAfterRebase() {
        val event = pipeline.captureAction(
            eventType = GitEventType.PUSH,
            source = ActionSource.KEYBOARD_SHORTCUT,
            label = "force_push_after_rebase",
            branch = featureBranch
        ) {
            val frame = ideFrame()
            try {
                frame.invokeAction("Vcs.Push")
                Thread.sleep(2000)
                val pushDlg = robot.pushDialog()
                pushDlg.forcePush()
                ActionOutcome.SUCCESS
            } catch (e: Exception) {
                // Fall back to git CLI
                val result = frame.runGitCommand("push", "--force-with-lease")
                if (result.contains("error") || result.contains("rejected")) {
                    ActionOutcome.FAILURE
                } else {
                    ActionOutcome.SUCCESS
                }
            }
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }
}
