package vip.trushin.intellij.workflows

import org.junit.jupiter.api.*
import vip.trushin.intellij.fixtures.*
import vip.trushin.intellij.model.*

/**
 * R8.6: Rollback flow
 * log -> select commit -> reset (soft/mixed/hard)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RollbackFlowTest : BaseGitFlowTest() {

    @Test
    @Order(1)
    @DisplayName("Step 1: Open Git Log")
    fun openGitLog() {
        val event = pipeline.captureAction(
            eventType = GitEventType.RESET,
            source = ActionSource.UI_ACTION,
            label = "open_log_for_rollback"
        ) {
            val frame = ideFrame()
            frame.openGitToolWindow()
            Thread.sleep(2000)

            // Show recent commits
            val logOutput = frame.runGitCommand("log", "--oneline", "-5")
            println("Recent commits:\n$logOutput")

            ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Reset with soft mode")
    fun resetSoft() {
        val event = pipeline.captureAction(
            eventType = GitEventType.RESET,
            source = ActionSource.UI_ACTION,
            label = "reset_soft"
        ) {
            val frame = ideFrame()
            val result = frame.runGitCommand("reset", "--soft", "HEAD~1")
            if (result.contains("fatal")) ActionOutcome.FAILURE else ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Reset with mixed mode")
    fun resetMixed() {
        val event = pipeline.captureAction(
            eventType = GitEventType.RESET,
            source = ActionSource.UI_ACTION,
            label = "reset_mixed"
        ) {
            val frame = ideFrame()
            // First undo the soft reset by re-committing
            frame.runGitCommand("commit", "-m", "re-commit after soft reset")
            val result = frame.runGitCommand("reset", "--mixed", "HEAD~1")
            if (result.contains("fatal")) ActionOutcome.FAILURE else ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Reset with hard mode")
    fun resetHard() {
        val event = pipeline.captureAction(
            eventType = GitEventType.RESET,
            source = ActionSource.UI_ACTION,
            label = "reset_hard"
        ) {
            val frame = ideFrame()
            val result = frame.runGitCommand("reset", "--hard", "HEAD")
            if (result.contains("fatal")) ActionOutcome.FAILURE else ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }
}
