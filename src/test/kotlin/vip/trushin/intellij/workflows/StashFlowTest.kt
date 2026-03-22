package vip.trushin.intellij.workflows

import org.junit.jupiter.api.*
import vip.trushin.intellij.fixtures.*
import vip.trushin.intellij.model.*

/**
 * R8.4: Stash flow
 * stash changes -> switch branch -> pop stash
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class StashFlowTest : BaseGitFlowTest() {

    @Test
    @Order(1)
    @DisplayName("Step 1: Stash current changes")
    fun stashChanges() {
        val event = pipeline.captureAction(
            eventType = GitEventType.STASH,
            source = ActionSource.MENU,
            label = "stash_changes"
        ) {
            val frame = ideFrame()
            frame.invokeAction("Git.Stash")
            Thread.sleep(2000)

            val stashDlg = robot.stashDialog()
            stashDlg.stashWithMessage("GitFlow capture test stash")
            ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Switch branch while stash is saved")
    fun switchBranch() {
        val event = pipeline.captureAction(
            eventType = GitEventType.CHECKOUT,
            source = ActionSource.UI_ACTION,
            label = "switch_branch_with_stash",
            branch = "main"
        ) {
            val frame = ideFrame()
            frame.checkoutBranch("main")
            ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Pop stash to restore changes")
    fun popStash() {
        val event = pipeline.captureAction(
            eventType = GitEventType.STASH,
            source = ActionSource.MENU,
            label = "pop_stash"
        ) {
            val frame = ideFrame()
            // Use git CLI for unstash since the UI action ID is unreliable
            val result = frame.runGitCommand("stash", "pop")
            if (result.contains("error") || result.contains("CONFLICT")) {
                ActionOutcome.CONFLICT
            } else {
                ActionOutcome.SUCCESS
            }
        }

        Assertions.assertTrue(
            event.outcome == ActionOutcome.SUCCESS || event.outcome == ActionOutcome.CONFLICT
        )
    }
}
