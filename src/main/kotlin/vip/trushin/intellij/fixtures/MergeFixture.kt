package vip.trushin.intellij.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import java.time.Duration

/**
 * Fixture for the Merge dialog (Git -> Merge) (R4.3).
 */
@DefaultXpath(
    by = "MergeDialog",
    xpath = "//div[contains(@title, 'Merge')]"
)
@FixtureName("Merge Dialog")
class MergeFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    /**
     * Branch selector for the branch to merge from.
     */
    val branchSelector
        get() = find<ComponentFixture>(
            byXpath("//div[@class='BranchSelector']"),
            Duration.ofSeconds(3)
        )

    /**
     * Merge strategy dropdown (resolve, recursive, octopus, ours, subtree).
     */
    val strategySelector
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Strategy' and @class='ComboBox']"),
            Duration.ofSeconds(3)
        )

    /**
     * "No fast forward" checkbox.
     */
    val noFfCheckbox
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='No fast forward' and @class='JBCheckBox']"),
            Duration.ofSeconds(3)
        )

    /**
     * "Squash commits" checkbox.
     */
    val squashCheckbox
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Squash commits' and @class='JBCheckBox']"),
            Duration.ofSeconds(3)
        )

    /**
     * Commit message field.
     */
    val commitMessage
        get() = find<JTextAreaFixture>(
            byXpath("//div[@class='JBTextArea']"),
            Duration.ofSeconds(3)
        )

    /**
     * Merge button.
     */
    val mergeButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Merge' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    /**
     * Cancel button.
     */
    val cancelButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Cancel' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    /**
     * Select a branch and merge.
     */
    fun mergeFromBranch(branchName: String) = step("Merge from branch: $branchName") {
        branchSelector.click()
        find<ComponentFixture>(
            byXpath("//div[contains(@accessiblename, '$branchName')]"),
            Duration.ofSeconds(3)
        ).click()
        mergeButton.click()
    }
}

/**
 * Fixture for the Merge Conflicts dialog.
 */
@DefaultXpath(
    by = "ConflictsDialog",
    xpath = "//div[@title='Conflicts']"
)
@FixtureName("Merge Conflicts Dialog")
class ConflictsDialogFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    val conflictsList
        get() = find<JTreeFixture>(
            byXpath("//div[@class='Tree']"),
            Duration.ofSeconds(3)
        )

    val acceptYoursButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Accept Yours' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    val acceptTheirsButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Accept Theirs' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    val mergeManuallyButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Merge...' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    fun acceptAllYours() = step("Accept all yours") { acceptYoursButton.click() }
    fun acceptAllTheirs() = step("Accept all theirs") { acceptTheirsButton.click() }
}

fun RemoteRobot.mergeDialog(timeout: Duration = Duration.ofSeconds(10)): MergeFixture {
    val fixtures = findAll(MergeFixture::class.java)
    if (fixtures.isNotEmpty()) return fixtures.first()
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    while (System.currentTimeMillis() < deadline) {
        val retry = findAll(MergeFixture::class.java)
        if (retry.isNotEmpty()) return retry.first()
        Thread.sleep(500)
    }
    throw IllegalStateException("MergeDialog not found within $timeout")
}

fun RemoteRobot.conflictsDialog(timeout: Duration = Duration.ofSeconds(10)): ConflictsDialogFixture {
    val fixtures = findAll(ConflictsDialogFixture::class.java)
    if (fixtures.isNotEmpty()) return fixtures.first()
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    while (System.currentTimeMillis() < deadline) {
        val retry = findAll(ConflictsDialogFixture::class.java)
        if (retry.isNotEmpty()) return retry.first()
        Thread.sleep(500)
    }
    throw IllegalStateException("ConflictsDialog not found within $timeout")
}
