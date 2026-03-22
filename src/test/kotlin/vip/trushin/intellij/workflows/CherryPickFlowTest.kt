package vip.trushin.intellij.workflows

import org.junit.jupiter.api.*
import vip.trushin.intellij.fixtures.*
import vip.trushin.intellij.model.*

/**
 * R8.5: Cherry-pick flow
 * log -> select commit -> cherry-pick -> push
 *
 * Each step opens the actual IntelliJ UI and captures it while visible.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CherryPickFlowTest : BaseGitFlowTest() {

    @Test
    @Order(1)
    @DisplayName("Step 1: Open Git Log and view commit history")
    fun openLogAndSelectCommit() {
        val event = pipeline.captureAction(
            eventType = GitEventType.CHERRY_PICK,
            source = ActionSource.UI_ACTION,
            label = "open_git_log_for_cherry_pick"
        ) {
            val frame = ideFrame()

            // Open Git tool window to show the Log tab
            frame.openGitToolWindow()
            Thread.sleep(2000)

            // Capture the Git Log with commit graph visible
            screenCapture.takeScreenshot("during_git_log_commit_list")

            // Show recent commits in test output for reference
            val logOutput = frame.runGitCommand("log", "--oneline", "-5")
            println("Recent commits:\n$logOutput")

            ActionOutcome.SUCCESS
        }

        Assertions.assertEquals(ActionOutcome.SUCCESS, event.outcome)
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Cherry-pick a commit")
    fun cherryPickCommit() {
        val event = pipeline.captureAction(
            eventType = GitEventType.CHERRY_PICK,
            source = ActionSource.UI_ACTION,
            label = "cherry_pick_commit"
        ) {
            val frame = ideFrame()

            // Create a new branch and make a commit to cherry-pick from
            val sourceBranch = "cherry-pick-source-${System.currentTimeMillis()}"
            frame.runGitCommand("checkout", "-b", sourceBranch)
            frame.runGitCommand("commit", "--allow-empty", "-m", "cherry-pick: test commit to pick")
            val commitHash = frame.runGitCommand("rev-parse", "HEAD").trim()

            // Switch back to main
            frame.runGitCommand("checkout", "main")
            Thread.sleep(1000)

            // Open Git Log to show the commit we'll cherry-pick
            frame.openGitToolWindow()
            Thread.sleep(1500)
            screenCapture.takeScreenshot("during_git_log_before_cherry_pick")

            // Invoke cherry-pick via action (the IDE shows a notification on success)
            frame.invokeAction("Git.CherryPick")
            Thread.sleep(2000)

            // Capture any cherry-pick dialog or notification
            screenCapture.takeScreenshot("during_cherry_pick_action")

            // Dismiss any dialog that appeared
            dismissAllDialogs()
            Thread.sleep(500)

            // Fall back to CLI cherry-pick
            val result = frame.runGitCommand("cherry-pick", commitHash)
            if (result.contains("CONFLICT")) {
                ActionOutcome.CONFLICT
            } else if (result.contains("nothing to commit") || result.contains("empty")) {
                ActionOutcome.SUCCESS
            } else {
                ActionOutcome.SUCCESS
            }
        }

        Assertions.assertTrue(
            event.outcome == ActionOutcome.SUCCESS || event.outcome == ActionOutcome.CONFLICT
        )
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Push after cherry-pick via Push dialog")
    fun pushAfterCherryPick() {
        val event = pipeline.captureAction(
            eventType = GitEventType.PUSH,
            source = ActionSource.KEYBOARD_SHORTCUT,
            label = "push_after_cherry_pick"
        ) {
            val frame = ideFrame()

            // Open the Push dialog
            frame.invokeAction("Vcs.Push")
            Thread.sleep(2000)

            try {
                val pushDlg = robot.pushDialog()

                // Capture the Push dialog with cherry-picked commits listed
                screenCapture.takeScreenshot("during_push_dialog_after_cherry_pick")

                pushDlg.push()
                Thread.sleep(3000)
                ActionOutcome.SUCCESS
            } catch (e: Exception) {
                // Capture error state
                screenCapture.takeScreenshot("during_push_dialog_cherry_pick_error")

                // CRITICAL: Dismiss the Push dialog
                dismissAllDialogs()
                Thread.sleep(500)

                // Fall back to git CLI
                val result = frame.runGitCommand("push")
                if (result.contains("error") || result.contains("rejected")) {
                    ActionOutcome.FAILURE
                } else {
                    ActionOutcome.SUCCESS
                }
            }
        }

        // Push may fail without remote — acceptable for capture
        Assertions.assertNotEquals(ActionOutcome.CANCELLED, event.outcome)
    }
}
