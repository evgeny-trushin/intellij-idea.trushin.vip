package vip.trushin.intellij.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import java.time.Duration

/**
 * Fixture for the Push dialog (Cmd+Shift+K) (R4.3).
 */
@DefaultXpath(
    by = "PushDialog",
    xpath = "//div[@title='Push Commits']"
)
@FixtureName("Push Dialog")
class PushFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    /**
     * The push button to execute the push.
     */
    val pushButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Push' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    /**
     * Cancel button to close the dialog.
     */
    val cancelButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Cancel' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    /**
     * Force push checkbox.
     */
    val forcePushCheckbox
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Force push' and @class='JBCheckBox']"),
            Duration.ofSeconds(3)
        )

    /**
     * The commits tree showing what will be pushed.
     */
    val commitsTree
        get() = find<JTreeFixture>(
            byXpath("//div[@class='Tree']"),
            Duration.ofSeconds(3)
        )

    /**
     * Remote selector dropdown for choosing the push target.
     */
    val remoteSelector
        get() = find<ComponentFixture>(
            byXpath("//div[@class='PushTargetPanel']"),
            Duration.ofSeconds(3)
        )

    /**
     * Execute a normal push.
     */
    fun push() = step("Click Push") {
        pushButton.click()
    }

    /**
     * Execute a force push.
     */
    fun forcePush() = step("Force push") {
        val checkbox = forcePushCheckbox
        if (!checkbox.callJs<Boolean>("component.isSelected()")) {
            checkbox.click()
        }
        pushButton.click()
    }

    /**
     * Get list of commits that will be pushed.
     */
    fun getPendingCommits(): List<String> = step("Get pending commits") {
        commitsTree.collectRows().map { it.toString() }
    }
}

/**
 * Find the Push dialog from RemoteRobot.
 */
fun RemoteRobot.pushDialog(timeout: Duration = Duration.ofSeconds(10)): PushFixture {
    val fixtures = findAll(PushFixture::class.java)
    if (fixtures.isNotEmpty()) return fixtures.first()
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    while (System.currentTimeMillis() < deadline) {
        val retry = findAll(PushFixture::class.java)
        if (retry.isNotEmpty()) return retry.first()
        Thread.sleep(500)
    }
    throw IllegalStateException("PushDialog not found within $timeout")
}
