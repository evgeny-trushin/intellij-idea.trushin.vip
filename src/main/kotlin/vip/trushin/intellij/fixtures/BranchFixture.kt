package vip.trushin.intellij.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

/**
 * Fixture for the Git Branches popup (R4.3).
 * In IntelliJ 2024.2 New UI, the popup uses a BranchesTree inside a HeavyWeightWindow.
 * The popup is opened via Git.Branches action on EDT.
 */
@DefaultXpath(
    by = "BranchesPopup",
    xpath = "//div[@class='HeavyWeightWindow']"
)
@FixtureName("Git Branches Popup")
class BranchFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    /**
     * Click "New Branch..." using keyboard search in the branches popup.
     */
    fun clickNewBranch() = step("Click New Branch in popup") {
        typeInPopup("New Branch")
        Thread.sleep(500)
        pressEnter()
        Thread.sleep(500)
    }

    /**
     * Select a branch by name using the popup's search/filter.
     */
    fun selectBranch(branchName: String) = step("Select branch: $branchName") {
        typeInPopup(branchName)
        Thread.sleep(500)
    }

    /**
     * Checkout a branch by typing its name and selecting Checkout.
     */
    fun checkoutBranch(branchName: String) = step("Checkout branch: $branchName") {
        typeInPopup(branchName)
        Thread.sleep(500)
        pressEnter()
        Thread.sleep(1000)

        // Look for Checkout in the resulting submenu
        waitFor(Duration.ofSeconds(5)) {
            remoteRobot.findAll(ComponentFixture::class.java,
                byXpath("//div[contains(@accessiblename, 'Checkout')]")
            ).isNotEmpty()
        }
        remoteRobot.findAll(ComponentFixture::class.java,
            byXpath("//div[contains(@accessiblename, 'Checkout')]")
        ).first().click()
    }

    private fun typeInPopup(text: String) {
        remoteRobot.callJs<Boolean>("""
            var r = new java.awt.Robot();
            var chars = "$text";
            for (var i = 0; i < chars.length; i++) {
                var c = chars.charCodeAt(i);
                try {
                    r.keyPress(c);
                    r.keyRelease(c);
                } catch (e) {
                    // For special chars, try extended key code
                    var code = java.awt.event.KeyEvent.getExtendedKeyCodeForChar(c);
                    r.keyPress(code);
                    r.keyRelease(code);
                }
                java.lang.Thread.sleep(30);
            }
            true;
        """.trimIndent())
    }

    private fun pressEnter() {
        remoteRobot.callJs<Boolean>("""
            var r = new java.awt.Robot();
            r.keyPress(java.awt.event.KeyEvent.VK_ENTER);
            r.keyRelease(java.awt.event.KeyEvent.VK_ENTER);
            true;
        """.trimIndent())
    }
}

/**
 * Fixture for the "New Branch" dialog that appears after clicking "New Branch..."
 */
@DefaultXpath(
    by = "NewBranchDialog",
    xpath = "//div[contains(@title, 'Branch')]"
)
@FixtureName("New Branch Dialog")
class NewBranchDialogFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    val createButton
        get() = find<ComponentFixture>(
            byXpath("//div[@accessiblename='Create' and @class='JButton']"),
            Duration.ofSeconds(3)
        )

    /**
     * Create a new branch by typing the name (the text field should already be focused)
     * and clicking Create.
     */
    fun createBranch(name: String, checkout: Boolean = true) = step("Create branch: $name") {
        // The branch name field should be focused when dialog opens.
        // Type the name using AWT Robot for reliability.
        remoteRobot.callJs<Boolean>("""
            var r = new java.awt.Robot();
            // Select all existing text
            r.keyPress(java.awt.event.KeyEvent.VK_META);
            r.keyPress(java.awt.event.KeyEvent.VK_A);
            r.keyRelease(java.awt.event.KeyEvent.VK_A);
            r.keyRelease(java.awt.event.KeyEvent.VK_META);
            java.lang.Thread.sleep(100);
            // Type the branch name
            var chars = "$name";
            for (var i = 0; i < chars.length; i++) {
                var c = chars.charCodeAt(i);
                try {
                    r.keyPress(c);
                    r.keyRelease(c);
                } catch (e) {
                    var code = java.awt.event.KeyEvent.getExtendedKeyCodeForChar(c);
                    if (code != java.awt.event.KeyEvent.VK_UNDEFINED) {
                        r.keyPress(code);
                        r.keyRelease(code);
                    }
                }
                java.lang.Thread.sleep(20);
            }
            true;
        """.trimIndent())
        Thread.sleep(500)

        // Handle checkout checkbox if needed
        try {
            val checkbox = find<ComponentFixture>(
                byXpath("//div[@accessiblename='Checkout branch' and @class='JBCheckBox']"),
                Duration.ofSeconds(1)
            )
            val isChecked = checkbox.callJs<Boolean>("component.isSelected()")
            if (checkout != isChecked) {
                checkbox.click()
            }
        } catch (_: Exception) {
            // Checkbox may not be visible
        }

        createButton.click()
    }
}

/**
 * Open the branches popup from the IDE frame (via Git.Branches action).
 */
fun IdeFrameFixture.openBranchesPopup(): BranchFixture = step("Open branches popup") {
    openBranchesPopupViaAction()
    remoteRobot.findAll(BranchFixture::class.java).firstOrNull()
        ?: throw IllegalStateException("Branch popup not found after opening Git.Branches")
}

/**
 * Find the New Branch dialog after it opens.
 */
fun RemoteRobot.newBranchDialog(timeout: Duration = Duration.ofSeconds(5)): NewBranchDialogFixture {
    val fixtures = findAll(NewBranchDialogFixture::class.java)
    if (fixtures.isNotEmpty()) return fixtures.first()
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    while (System.currentTimeMillis() < deadline) {
        val retry = findAll(NewBranchDialogFixture::class.java)
        if (retry.isNotEmpty()) return retry.first()
        Thread.sleep(500)
    }
    throw IllegalStateException("NewBranchDialog not found within $timeout")
}
