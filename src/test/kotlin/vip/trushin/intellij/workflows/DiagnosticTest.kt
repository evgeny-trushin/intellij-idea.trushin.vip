package vip.trushin.intellij.workflows

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import vip.trushin.intellij.fixtures.IdeFrameFixture
import vip.trushin.intellij.fixtures.StashDialogFixture
import vip.trushin.intellij.fixtures.UnstashDialogFixture
import vip.trushin.intellij.fixtures.ideFrame

/**
 * Run: ./gradlew integrationTest --tests "*DiagnosticTest"
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DiagnosticTest {

    private lateinit var robot: RemoteRobot
    private lateinit var frame: IdeFrameFixture

    @BeforeAll
    fun setup() {
        val host = System.getProperty("robot.host", "127.0.0.1")
        val port = System.getProperty("robot.port", "8082")
        robot = RemoteRobot("http://$host:$port")
        frame = robot.ideFrame()
        frame.callJs<Boolean>("component.toFront(); component.requestFocus(); true;")
        Thread.sleep(500)
        repeat(10) { pressEscape() }
        Thread.sleep(500)
    }

    private fun pressEscape() {
        try {
            robot.callJs<Boolean>("""
                var r = new java.awt.Robot();
                r.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
                r.keyRelease(java.awt.event.KeyEvent.VK_ESCAPE);
                true;
            """.trimIndent())
        } catch (_: Exception) {}
        Thread.sleep(200)
    }

    @Test
    @Order(1)
    @DisplayName("Diag 1: Branch popup - type and see what happens")
    fun branchPopupTypeTest() {
        frame.invokeAction("Git.Branches")
        Thread.sleep(3000)

        // Type 'm' using AWT Robot
        robot.callJs<Boolean>("""
            var r = new java.awt.Robot();
            r.keyPress(java.awt.event.KeyEvent.VK_M);
            r.keyRelease(java.awt.event.KeyEvent.VK_M);
            true;
        """.trimIndent())
        Thread.sleep(1000)

        // Check if search field appeared
        val searchFields = robot.findAll(ComponentFixture::class.java,
            byXpath("//div[@class='SearchTextField' or @class='SearchField' or @class='TextFieldWithProcessing']"))
        println("DIAG: Search fields after type: ${searchFields.size}")

        // Check for speed search
        val speedSearch = robot.findAll(ComponentFixture::class.java,
            byXpath("//div[contains(@class, 'SpeedSearch') or contains(@class, 'speedSearch')]"))
        println("DIAG: SpeedSearch: ${speedSearch.size}")

        // Check what's visible in the popup now
        val hww = robot.findAll(ComponentFixture::class.java,
            byXpath("//div[@class='HeavyWeightWindow']"))
        println("DIAG: HeavyWeightWindow: ${hww.size}")

        // Type 'ain' to complete 'main'
        robot.callJs<Boolean>("""
            var r = new java.awt.Robot();
            var keys = [java.awt.event.KeyEvent.VK_A, java.awt.event.KeyEvent.VK_I, java.awt.event.KeyEvent.VK_N];
            for (var i = 0; i < keys.length; i++) {
                r.keyPress(keys[i]);
                r.keyRelease(keys[i]);
                java.lang.Thread.sleep(50);
            }
            true;
        """.trimIndent())
        Thread.sleep(1000)

        // Press Enter to select
        robot.callJs<Boolean>("""
            var r = new java.awt.Robot();
            r.keyPress(java.awt.event.KeyEvent.VK_ENTER);
            r.keyRelease(java.awt.event.KeyEvent.VK_ENTER);
            true;
        """.trimIndent())
        Thread.sleep(1500)

        // Check if submenu appeared
        val menuItems = robot.findAll(ComponentFixture::class.java,
            byXpath("//div[@class='ActionMenuItem']"))
        println("DIAG: ActionMenuItem after enter: ${menuItems.size}")
        menuItems.take(10).forEach { m ->
            try {
                val name = m.callJs<String>("""
                    var ctx = component.getAccessibleContext();
                    ctx != null && ctx.getAccessibleName() != null ? ctx.getAccessibleName() : "null";
                """.trimIndent())
                println("  - MenuItem: '$name'")
            } catch (_: Exception) {}
        }

        // Check for HeavyWeightWindow (submenu might be another popup)
        val hww2 = robot.findAll(ComponentFixture::class.java,
            byXpath("//div[@class='HeavyWeightWindow']"))
        println("DIAG: HeavyWeightWindow after enter: ${hww2.size}")

        // Also check for any popup with Checkout
        val checkoutItems = robot.findAll(ComponentFixture::class.java,
            byXpath("//div[contains(@accessiblename, 'Checkout') or contains(@visible_text, 'Checkout')]"))
        println("DIAG: Checkout items: ${checkoutItems.size}")

        pressEscape()
        pressEscape()
        pressEscape()
    }

    @Test
    @Order(2)
    @DisplayName("Diag 2: Unstash dialog via action")
    fun unstashViaAction() {
        // Try different action IDs for unstash
        val actionIds = listOf("Git.Unstash", "Git.UnstashChanges", "Vcs.Git.Unstash")
        for (actionId in actionIds) {
            try {
                frame.invokeAction(actionId)
                Thread.sleep(2000)

                val unstashFixtures = robot.findAll(UnstashDialogFixture::class.java)
                println("DIAG: $actionId -> UnstashDialogFixture: ${unstashFixtures.size}")

                val myDialogs = robot.findAll(ComponentFixture::class.java,
                    byXpath("//div[@class='MyDialog']"))
                println("DIAG: $actionId -> MyDialog: ${myDialogs.size}")

                if (unstashFixtures.isNotEmpty() || myDialogs.isNotEmpty()) {
                    pressEscape()
                    break
                }
            } catch (e: Exception) {
                println("DIAG: $actionId failed: ${e.message?.take(100)}")
            }
        }

        pressEscape()
        pressEscape()
    }

    @Test
    @Order(3)
    @DisplayName("Diag 3: Try Git checkout via action")
    fun checkoutViaAction() {
        // Try checking out main branch directly via action
        try {
            frame.callJs<Boolean>("""
                importClass(javax.swing.SwingUtilities);
                var project = component.getProject();
                SwingUtilities.invokeLater(new java.lang.Runnable({
                    run: function() {
                        var gitRepoManager = git4idea.repo.GitRepositoryManager.getInstance(project);
                        var repos = gitRepoManager.getRepositories();
                        if (repos.size() > 0) {
                            var repo = repos.get(0);
                            var currentBranch = repo.getCurrentBranch();
                            var result = "currentBranch=" + (currentBranch != null ? currentBranch.getName() : "null");
                            // List local branches
                            var branches = repo.getBranches().getLocalBranches();
                            var iter = branches.iterator();
                            while (iter.hasNext()) {
                                result += " branch:" + iter.next().getName();
                            }
                            java.lang.System.setProperty("diag.branches", result);
                        }
                    }
                }));
                true;
            """.trimIndent())
            Thread.sleep(1000)

            val branchInfo = frame.callJs<String>("""
                java.lang.System.getProperty("diag.branches", "not-set");
            """.trimIndent())
            println("DIAG: Branch info: $branchInfo")
        } catch (e: Exception) {
            println("DIAG: Branch info failed: ${e.message?.take(200)}")
        }

        // Try doing checkout programmatically
        try {
            frame.callJs<Boolean>("""
                importClass(javax.swing.SwingUtilities);
                var project = component.getProject();
                SwingUtilities.invokeLater(new java.lang.Runnable({
                    run: function() {
                        var gitRepoManager = git4idea.repo.GitRepositoryManager.getInstance(project);
                        var repos = gitRepoManager.getRepositories();
                        if (repos.size() > 0) {
                            var repo = repos.get(0);
                            var branches = repo.getBranches().getLocalBranches();
                            var mainBranch = null;
                            var iter = branches.iterator();
                            while (iter.hasNext()) {
                                var b = iter.next();
                                if (b.getName() == "main") {
                                    mainBranch = b;
                                    break;
                                }
                            }
                            if (mainBranch != null) {
                                var brancher = git4idea.branch.GitBrancher.getInstance(project);
                                brancher.checkout("main", false, java.util.Collections.singletonList(repo), null);
                                java.lang.System.setProperty("diag.checkout", "checkout-main-started");
                            } else {
                                java.lang.System.setProperty("diag.checkout", "main-branch-not-found");
                            }
                        }
                    }
                }));
                true;
            """.trimIndent())
            Thread.sleep(3000)

            val checkoutResult = frame.callJs<String>("""
                java.lang.System.getProperty("diag.checkout", "not-set");
            """.trimIndent())
            println("DIAG: Checkout result: $checkoutResult")
        } catch (e: Exception) {
            println("DIAG: Checkout failed: ${e.message?.take(200)}")
        }
    }
}
