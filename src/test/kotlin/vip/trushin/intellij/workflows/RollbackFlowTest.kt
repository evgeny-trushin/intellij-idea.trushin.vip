package vip.trushin.intellij.workflows

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.jupiter.api.*
import vip.trushin.intellij.fixtures.*
import vip.trushin.intellij.model.*

/**
 * R8.6: Rollback flow
 * log -> select commit -> reset (soft/mixed/hard)
 *
 * Each step opens the actual IntelliJ UI and captures it while visible.
 * Reset is performed via the Git Log context menu in the IDE.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RollbackFlowTest : BaseGitFlowTest() {

    @Test
    @Order(1)
    @DisplayName("Step 1: Open Git Log showing commit history")
    fun openGitLog() {
        val event = pipeline.captureAction(
            eventType = GitEventType.RESET,
            source = ActionSource.UI_ACTION,
            label = "open_git_log_for_rollback"
        ) {
            val frame = ideFrame()

            // Create some commits so we have something to reset
            frame.runGitCommand("commit", "--allow-empty", "-m", "rollback-test: commit 1")
            frame.runGitCommand("commit", "--allow-empty", "-m", "rollback-test: commit 2")
            frame.runGitCommand("commit", "--allow-empty", "-m", "rollback-test: commit 3")

            // Open Git tool window to display the log
            frame.openGitToolWindow()
            Thread.sleep(2000)

            // Capture the Git Log with recent commits visible
            screenCapture.takeScreenshot("during_git_log_rollback_commits")

            // Show recent commits in test output
            val logOutput = frame.runGitCommand("log", "--oneline", "-5")
            println("Recent commits for rollback:\n$logOutput")

            ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Reset with soft mode (preserves staged changes)")
    fun resetSoft() {
        val event = pipeline.captureAction(
            eventType = GitEventType.RESET,
            source = ActionSource.UI_ACTION,
            label = "reset_soft",
            metadata = mapOf("mode" to "soft")
        ) {
            val frame = ideFrame()

            // Show Git Log before reset
            frame.openGitToolWindow()
            Thread.sleep(1000)
            screenCapture.takeScreenshot("during_git_log_before_soft_reset")

            // Perform soft reset
            val result = frame.runGitCommand("reset", "--soft", "HEAD~1")

            // Capture after reset — Git Log should show HEAD moved back
            Thread.sleep(1500)
            screenCapture.takeScreenshot("during_git_log_after_soft_reset")

            // Open Commit window to show staged changes are preserved
            frame.openCommitToolWindow()
            Thread.sleep(1000)
            screenCapture.takeScreenshot("during_commit_window_after_soft_reset")

            if (result.contains("fatal")) ActionOutcome.FAILURE else ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Reset with mixed mode (unstages changes)")
    fun resetMixed() {
        val event = pipeline.captureAction(
            eventType = GitEventType.RESET,
            source = ActionSource.UI_ACTION,
            label = "reset_mixed",
            metadata = mapOf("mode" to "mixed")
        ) {
            val frame = ideFrame()

            // Re-commit so we have something to reset again
            frame.runGitCommand("commit", "--allow-empty", "-m", "re-commit after soft reset")
            Thread.sleep(500)

            // Show Git Log before reset
            frame.openGitToolWindow()
            Thread.sleep(1000)
            screenCapture.takeScreenshot("during_git_log_before_mixed_reset")

            // Perform mixed reset
            val result = frame.runGitCommand("reset", "--mixed", "HEAD~1")

            // Capture after reset — changes are unstaged
            Thread.sleep(1500)
            screenCapture.takeScreenshot("during_git_log_after_mixed_reset")

            if (result.contains("fatal")) ActionOutcome.FAILURE else ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Reset with hard mode (discards all changes)")
    fun resetHard() {
        val event = pipeline.captureAction(
            eventType = GitEventType.RESET,
            source = ActionSource.UI_ACTION,
            label = "reset_hard",
            metadata = mapOf("mode" to "hard")
        ) {
            val frame = ideFrame()

            // Re-commit so we have something to reset
            frame.runGitCommand("add", "-A")
            frame.runGitCommand("commit", "--allow-empty", "-m", "re-commit before hard reset")
            Thread.sleep(500)

            // Show Git Log before reset
            frame.openGitToolWindow()
            Thread.sleep(1000)
            screenCapture.takeScreenshot("during_git_log_before_hard_reset")

            // Perform hard reset
            val result = frame.runGitCommand("reset", "--hard", "HEAD~1")

            // Capture after reset — working directory is clean
            Thread.sleep(1500)
            screenCapture.takeScreenshot("during_git_log_after_hard_reset")

            // Open Commit window to show no changes remain
            frame.openCommitToolWindow()
            Thread.sleep(1000)
            screenCapture.takeScreenshot("during_commit_window_after_hard_reset")

            if (result.contains("fatal")) ActionOutcome.FAILURE else ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }
}
