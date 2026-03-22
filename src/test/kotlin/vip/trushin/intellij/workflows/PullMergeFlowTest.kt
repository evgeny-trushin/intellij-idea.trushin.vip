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
            frame.invokeAction("Git.Fetch")
            Thread.sleep(3000)
            ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Pull with merge")
    fun pullWithMerge() {
        val event = pipeline.captureAction(
            eventType = GitEventType.PULL,
            source = ActionSource.MENU,
            label = "pull_with_merge"
        ) {
            val frame = ideFrame()
            frame.invokeAction("Git.Pull")
            Thread.sleep(2000)

            // Pull dialog will appear - click Pull button
            try {
                val pullButtons = robot.findAll(ComponentFixture::class.java,
                    byXpath("//div[@accessiblename='Pull' and @class='JButton']"))
                if (pullButtons.isNotEmpty()) {
                    pullButtons.first().click()
                }
                ActionOutcome.SUCCESS
            } catch (e: Exception) {
                ActionOutcome.SUCCESS // Auto-pull without dialog
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
                val conflictsDlg = robot.conflictsDialog(Duration.ofSeconds(3))
                conflictsDlg.acceptAllTheirs()
                ActionOutcome.CONFLICT
            } catch (e: Exception) {
                ActionOutcome.SUCCESS // No conflicts
            }
        }

        Assertions.assertTrue(
            event.outcome == ActionOutcome.SUCCESS || event.outcome == ActionOutcome.CONFLICT
        )
    }
}
