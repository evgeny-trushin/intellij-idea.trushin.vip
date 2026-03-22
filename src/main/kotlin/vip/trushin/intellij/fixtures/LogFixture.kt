package vip.trushin.intellij.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

/**
 * Fixture for the Git Log tool window (R4.3).
 * Provides access to commit list, diff panel, and context menu actions.
 * In 2024.2 New UI, the tool window may use different decorator classes.
 */
@DefaultXpath(
    by = "GitLogToolWindow",
    xpath = "//div[@class='InternalDecoratorImpl']"
)
@FixtureName("Git Log Tool Window")
class LogFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    /**
     * The Log tab within the Git tool window.
     * Uses ContentTabLabel with accessiblename "Log".
     */
    val logTab
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Log' and @class='ContentTabLabel']"),
            Duration.ofSeconds(5)
        )

    /**
     * The commit table/list in the log view.
     */
    val commitTable
        get() = find<ComponentFixture>(
            byXpath("//div[@class='GraphTable']"),
            Duration.ofSeconds(5)
        )

    /**
     * The diff panel that shows when a commit is selected.
     */
    val diffPanel
        get() = find<ComponentFixture>(
            byXpath("//div[@class='DiffPanel']"),
            Duration.ofSeconds(10)
        )

    /**
     * The branch filter dropdown.
     */
    val branchFilter
        get() = find<ComponentFixture>(
            byXpath("//div[@class='BranchFilterPopupComponent']"),
            Duration.ofSeconds(5)
        )

    /**
     * The search/filter text field.
     */
    val searchField
        get() = find<JTextFieldFixture>(
            byXpath("//div[@class='SearchTextField']"),
            Duration.ofSeconds(5)
        )

    /**
     * Switch to the Log tab.
     */
    fun openLogTab() = step("Open Log tab") {
        logTab.click()
    }

    /**
     * Select a commit by row index (0-based).
     */
    fun selectCommitByIndex(index: Int) = step("Select commit at index $index") {
        commitTable.callJs<Boolean>("""
            const table = component;
            table.setRowSelectionInterval($index, $index);
            table.scrollRectToVisible(table.getCellRect($index, 0, true));
            true;
        """.trimIndent())
    }

    /**
     * Right-click on a commit to open context menu.
     */
    fun rightClickCommit(index: Int) = step("Right-click commit at index $index") {
        selectCommitByIndex(index)
        commitTable.rightClick()
    }

    /**
     * Cherry-pick the selected commit via context menu.
     */
    fun cherryPickSelected() = step("Cherry-pick selected commit") {
        commitTable.rightClick()
        waitFor(Duration.ofSeconds(3)) {
            findAll<ComponentFixture>(
                byXpath("//div[@accessiblename='Cherry-Pick' and @class='ActionMenuItem']")
            ).isNotEmpty()
        }
        find<ComponentFixture>(
            byXpath("//div[@accessiblename='Cherry-Pick' and @class='ActionMenuItem']")
        ).click()
    }

    /**
     * Reset to selected commit via context menu.
     */
    fun resetToSelected(mode: ResetMode = ResetMode.MIXED) = step("Reset to selected commit ($mode)") {
        commitTable.rightClick()
        waitFor(Duration.ofSeconds(3)) {
            findAll<ComponentFixture>(
                byXpath("//div[@accessiblename='Reset Current Branch to Here...' and @class='ActionMenuItem']")
            ).isNotEmpty()
        }
        find<ComponentFixture>(
            byXpath("//div[@accessiblename='Reset Current Branch to Here...' and @class='ActionMenuItem']")
        ).click()
    }
}

enum class ResetMode { SOFT, MIXED, HARD }

/**
 * Fixture for the Git Reset dialog.
 */
@DefaultXpath(
    by = "ResetDialog",
    xpath = "//div[@title='Reset Head']"
)
@FixtureName("Reset Dialog")
class ResetDialogFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    fun selectMode(mode: ResetMode) = step("Select reset mode: $mode") {
        val radioName = when (mode) {
            ResetMode.SOFT -> "Soft"
            ResetMode.MIXED -> "Mixed"
            ResetMode.HARD -> "Hard"
        }
        find<ComponentFixture>(
            byXpath("//div[@accessiblename='$radioName' and @class='JBRadioButton']"),
            Duration.ofSeconds(3)
        ).click()
    }

    val resetButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Reset' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    fun reset(mode: ResetMode) = step("Reset with mode: $mode") {
        selectMode(mode)
        resetButton.click()
    }
}

/**
 * Fixture for the Stash dialog (Git -> Stash Changes).
 */
@DefaultXpath(
    by = "StashDialog",
    xpath = "//div[contains(@title, 'Stash')]"
)
@FixtureName("Stash Dialog")
class StashDialogFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    val stashButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Create Stash' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    /**
     * Create a stash. In 2024.2 New UI the message field may not be present,
     * so we click Create Stash directly.
     */
    fun stashWithMessage(message: String) = step("Stash with message: $message") {
        // Try to find and fill message field (not always present in New UI)
        try {
            val messageField = find<JTextFieldFixture>(
                byXpath("//div[@class='JTextField' or @class='JBTextField' or @class='EditorTextField']"),
                Duration.ofSeconds(1)
            )
            messageField.text = message
        } catch (_: Exception) {
            // Message field not present in 2024.2 New UI - proceed without it
        }
        stashButton.click()
    }
}

/**
 * Fixture for the Unstash dialog (Git -> Unstash Changes).
 */
@DefaultXpath(
    by = "UnstashDialog",
    xpath = "//div[contains(@title, 'Unstash') or contains(@title, 'unstash')]"
)
@FixtureName("Unstash Dialog")
class UnstashDialogFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    val stashList
        get() = find<ComponentFixture>(
            byXpath("//div[@class='JBList']"),
            Duration.ofSeconds(3)
        )

    val popStashCheckbox
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Pop stash' and @class='JBCheckBox']"),
            Duration.ofSeconds(3)
        )

    val applyButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Apply Stash' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    fun popLatestStash() = step("Pop latest stash") {
        val checkbox = popStashCheckbox
        if (!checkbox.callJs<Boolean>("component.isSelected()")) {
            checkbox.click()
        }
        applyButton.click()
    }
}

fun IdeFrameFixture.gitLogToolWindow(timeout: Duration = Duration.ofSeconds(10)): LogFixture {
    val fixtures = remoteRobot.findAll(LogFixture::class.java)
    if (fixtures.isNotEmpty()) return fixtures.first()
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    while (System.currentTimeMillis() < deadline) {
        val retry = remoteRobot.findAll(LogFixture::class.java)
        if (retry.isNotEmpty()) return retry.first()
        Thread.sleep(500)
    }
    throw IllegalStateException("LogFixture not found within $timeout")
}

fun RemoteRobot.resetDialog(timeout: Duration = Duration.ofSeconds(5)): ResetDialogFixture {
    val fixtures = findAll(ResetDialogFixture::class.java)
    if (fixtures.isNotEmpty()) return fixtures.first()
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    while (System.currentTimeMillis() < deadline) {
        val retry = findAll(ResetDialogFixture::class.java)
        if (retry.isNotEmpty()) return retry.first()
        Thread.sleep(500)
    }
    throw IllegalStateException("ResetDialog not found within $timeout")
}

fun RemoteRobot.stashDialog(timeout: Duration = Duration.ofSeconds(5)): StashDialogFixture {
    val fixtures = findAll(StashDialogFixture::class.java)
    if (fixtures.isNotEmpty()) return fixtures.first()
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    while (System.currentTimeMillis() < deadline) {
        val retry = findAll(StashDialogFixture::class.java)
        if (retry.isNotEmpty()) return retry.first()
        Thread.sleep(500)
    }
    throw IllegalStateException("StashDialog not found within $timeout")
}

fun RemoteRobot.unstashDialog(timeout: Duration = Duration.ofSeconds(5)): UnstashDialogFixture {
    val fixtures = findAll(UnstashDialogFixture::class.java)
    if (fixtures.isNotEmpty()) return fixtures.first()
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    while (System.currentTimeMillis() < deadline) {
        val retry = findAll(UnstashDialogFixture::class.java)
        if (retry.isNotEmpty()) return retry.first()
        Thread.sleep(500)
    }
    throw IllegalStateException("UnstashDialog not found within $timeout")
}
