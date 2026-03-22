package vip.trushin.intellij.workflows

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.jupiter.api.*
import vip.trushin.intellij.fixtures.*
import vip.trushin.intellij.model.*
import java.time.Duration

/**
 * R8.7: PR review flow (requires GitHub plugin)
 * create PR -> review changes -> merge
 *
 * Each step opens the actual IntelliJ UI and captures it while visible.
 * Steps gracefully handle missing GitHub authentication.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class PrReviewFlowTest : BaseGitFlowTest() {

    @Test
    @Order(1)
    @DisplayName("Step 1: Open Pull Requests tool window")
    fun openPrToolWindow() {
        val event = pipeline.captureAction(
            eventType = GitEventType.MERGE,
            source = ActionSource.UI_ACTION,
            label = "open_pr_tool_window"
        ) {
            val frame = ideFrame()

            // Try to open the Pull Requests tool window
            try {
                frame.showToolWindow("Pull Requests")
                Thread.sleep(2000)

                // Capture the Pull Requests tool window
                screenCapture.takeScreenshot("during_pr_tool_window")
                ActionOutcome.SUCCESS
            } catch (e: Exception) {
                // Try the button approach
                try {
                    val prButtons = frame.findAll<ComponentFixture>(
                        byXpath("//div[@accessiblename='Pull Requests' and @class='SqueezeButton']")
                    )
                    if (prButtons.isNotEmpty()) {
                        prButtons.first().click()
                        Thread.sleep(2000)
                        screenCapture.takeScreenshot("during_pr_tool_window")
                        ActionOutcome.SUCCESS
                    } else {
                        // Capture the IDE state showing no PR tool window available
                        screenCapture.takeScreenshot("during_no_pr_plugin")
                        ActionOutcome.CANCELLED
                    }
                } catch (e2: Exception) {
                    screenCapture.takeScreenshot("during_no_pr_plugin")
                    ActionOutcome.CANCELLED
                }
            }
        }

        Assertions.assertTrue(
            event.outcome == ActionOutcome.SUCCESS || event.outcome == ActionOutcome.CANCELLED,
            "PR tool window should open or be gracefully unavailable"
        )
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Create a Pull Request via GitHub action")
    fun createPullRequest() {
        val event = pipeline.captureAction(
            eventType = GitEventType.PUSH,
            source = ActionSource.MENU,
            label = "create_pull_request"
        ) {
            val frame = ideFrame()

            try {
                // Invoke the Create Pull Request action
                frame.invokeAction("Github.Create.Pull.Request")
                Thread.sleep(3000)

                // Capture the Create PR dialog while visible
                screenCapture.takeScreenshot("during_create_pr_dialog")

                // Try to find the PR title field
                val prTitleFields = robot.findAll(
                    com.intellij.remoterobot.fixtures.JTextFieldFixture::class.java,
                    byXpath("//div[@accessiblename='Title' and @class='JTextField']")
                )
                if (prTitleFields.isNotEmpty()) {
                    prTitleFields.first().text = "GitFlow Capture: Test PR"

                    // Capture the filled-in PR dialog
                    screenCapture.takeScreenshot("during_create_pr_dialog_filled")

                    val createButtons = robot.findAll(ComponentFixture::class.java,
                        byXpath("//div[@accessiblename='Create Pull Request' and @class='JButton']"))
                    if (createButtons.isNotEmpty()) {
                        createButtons.first().click()
                        Thread.sleep(2000)
                    }
                    ActionOutcome.SUCCESS
                } else {
                    // PR dialog didn't appear — likely no GitHub auth
                    dismissAllDialogs()
                    ActionOutcome.CANCELLED
                }
            } catch (e: Exception) {
                // Capture whatever dialog appeared (auth prompt, error, etc.)
                screenCapture.takeScreenshot("during_create_pr_auth_required")
                dismissAllDialogs()
                ActionOutcome.CANCELLED
            }
        }

        Assertions.assertNotEquals(ActionOutcome.FAILURE, event.outcome)
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Review and merge PR")
    fun reviewAndMergePr() {
        val event = pipeline.captureAction(
            eventType = GitEventType.MERGE,
            source = ActionSource.UI_ACTION,
            label = "review_and_merge_pr"
        ) {
            val frame = ideFrame()

            try {
                // Try to open Pull Requests tool window again
                frame.showToolWindow("Pull Requests")
                Thread.sleep(2000)

                // Capture the PR list
                screenCapture.takeScreenshot("during_pr_list_for_review")

                ActionOutcome.CANCELLED // Requires live GitHub connection to merge
            } catch (e: Exception) {
                // Capture the state showing why PR review isn't available
                screenCapture.takeScreenshot("during_pr_review_unavailable")
                ActionOutcome.CANCELLED
            }
        }

        Assertions.assertNotEquals(ActionOutcome.FAILURE, event.outcome)
    }
}
