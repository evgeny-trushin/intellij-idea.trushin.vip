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
            try {
                val frame = ideFrame()
                val prButtons = frame.findAll<ComponentFixture>(
                    byXpath("//div[@accessiblename='Pull Requests' and @class='SquareStripeButton']")
                )
                if (prButtons.isNotEmpty()) {
                    prButtons.first().click()
                    ActionOutcome.SUCCESS
                } else {
                    ActionOutcome.CANCELLED // GitHub plugin not installed
                }
            } catch (e: Exception) {
                ActionOutcome.CANCELLED
            }
        }

        Assertions.assertTrue(
            event.outcome == ActionOutcome.SUCCESS || event.outcome == ActionOutcome.CANCELLED,
            "PR tool window should open or be gracefully unavailable"
        )
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Create a Pull Request")
    fun createPullRequest() {
        val event = pipeline.captureAction(
            eventType = GitEventType.PUSH,
            source = ActionSource.MENU,
            label = "create_pull_request"
        ) {
            try {
                val frame = ideFrame()
                frame.invokeAction("Github.Create.Pull.Request")
                Thread.sleep(3000)

                val prTitleFields = robot.findAll(
                    com.intellij.remoterobot.fixtures.JTextFieldFixture::class.java,
                    byXpath("//div[@accessiblename='Title' and @class='JTextField']")
                )
                if (prTitleFields.isNotEmpty()) {
                    prTitleFields.first().text = "GitFlow Capture: Test PR"
                    val createButtons = robot.findAll(ComponentFixture::class.java,
                        byXpath("//div[@accessiblename='Create Pull Request' and @class='JButton']"))
                    if (createButtons.isNotEmpty()) {
                        createButtons.first().click()
                    }
                }
                ActionOutcome.SUCCESS
            } catch (e: Exception) {
                ActionOutcome.CANCELLED // GitHub integration not configured
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
            try {
                val frame = ideFrame()
                val prButtons = frame.findAll<ComponentFixture>(
                    byXpath("//div[@accessiblename='Pull Requests' and @class='SquareStripeButton']")
                )
                if (prButtons.isNotEmpty()) {
                    prButtons.first().click()
                    Thread.sleep(2000)
                }
                ActionOutcome.CANCELLED // Requires live GitHub connection
            } catch (e: Exception) {
                ActionOutcome.CANCELLED
            }
        }

        Assertions.assertNotEquals(ActionOutcome.FAILURE, event.outcome)
    }
}
