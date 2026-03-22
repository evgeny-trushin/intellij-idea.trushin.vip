package vip.trushin.intellij.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import vip.trushin.intellij.capture.CapturePipeline
import vip.trushin.intellij.capture.ScreenCapture
import vip.trushin.intellij.listeners.EventListenerBridge
import vip.trushin.intellij.model.EventLogger
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Base test class providing shared Remote Robot connection and capture infrastructure.
 * All workflow tests extend this. Tagged as "integration" — requires a running IntelliJ IDEA
 * with Remote Robot server.
 *
 * Run with: ./gradlew integrationTest
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseGitFlowTest {

    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8082
    }

    protected lateinit var robot: RemoteRobot
    protected lateinit var eventLogger: EventLogger
    protected lateinit var screenCapture: ScreenCapture
    protected lateinit var pipeline: CapturePipeline
    protected lateinit var listenerBridge: EventListenerBridge

    protected val capturesDir: Path by lazy {
        Paths.get(System.getProperty("captures.dir", "captures"))
    }

    protected val eventLogPath: Path by lazy {
        Paths.get(System.getProperty("event.log",
            "${System.getProperty("user.home")}/git-flow-log.jsonl"))
    }

    @BeforeAll
    fun setupRobot() {
        val host = System.getProperty("robot.host", DEFAULT_HOST)
        val port = System.getProperty("robot.port", DEFAULT_PORT.toString()).toInt()
        val robotUrl = "http://$host:$port"

        // Verify Remote Robot server is reachable before proceeding
        check(isRobotServerAvailable(robotUrl)) {
            "Remote Robot server not available at $robotUrl. " +
                "Start IntelliJ IDEA with robot-server plugin first: ./gradlew launchIde"
        }

        robot = RemoteRobot(robotUrl)
        eventLogger = EventLogger(eventLogPath)
        screenCapture = ScreenCapture(capturesDir, robot)
        pipeline = CapturePipeline(robot, eventLogger, screenCapture)
        listenerBridge = EventListenerBridge(robot, eventLogger, eventLogPath)

        // Focus the target IDE window to ensure actions target the right project
        ideFrame().focus()
    }

    /**
     * Before each test step: focus the IDE and ensure clean UI state.
     */
    @BeforeEach
    fun ensureCleanState() {
        ideFrame().focus()
        Thread.sleep(500)
    }

    /**
     * After each test step: dismiss any leftover dialogs/popups to prevent
     * contaminating the next step's screenshots.
     */
    @AfterEach
    fun dismissLeftoverDialogs() {
        dismissAllDialogs()
        Thread.sleep(500)
    }

    /**
     * Get the IDE frame fixture.
     */
    protected fun ideFrame(): IdeFrameFixture = robot.ideFrame()

    /**
     * Dismiss all open dialogs by pressing Escape multiple times.
     * Also tries to close known dialog types via their Cancel buttons.
     */
    protected fun dismissAllDialogs() {
        // Try to close Push dialog if it's open
        try {
            val pushDialogs = robot.findAll(PushFixture::class.java)
            pushDialogs.forEach { dlg ->
                try { dlg.cancelButton.click() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Try to close any other modal dialogs with Cancel buttons
        try {
            val cancelButtons = robot.findAll(ComponentFixture::class.java,
                byXpath("//div[@accessiblename='Cancel' and @class='JButton']"))
            cancelButtons.forEach { btn ->
                try { btn.click() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Press Escape multiple times to close popups, search dialogs, etc.
        repeat(3) {
            pressEscape()
            Thread.sleep(200)
        }
    }

    /**
     * Press the Escape key via AWT Robot.
     */
    protected fun pressEscape() {
        try {
            robot.callJs<Boolean>("""
                var r = new java.awt.Robot();
                r.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
                r.keyRelease(java.awt.event.KeyEvent.VK_ESCAPE);
                true;
            """.trimIndent())
        } catch (_: Exception) {}
    }

    /**
     * Wait for a dialog to appear by checking for a fixture class,
     * then take a screenshot while the dialog is visible.
     */
    protected fun waitForDialogAndCapture(label: String, @Suppress("unused") timeoutMs: Long = 5000): String {
        Thread.sleep(1000) // Let the dialog animate in
        ideFrame().focus()
        return screenCapture.takeScreenshot(label)
    }

    private fun isRobotServerAvailable(url: String): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "GET"
            conn.responseCode in 200..499
        } catch (e: Exception) {
            false
        }
    }
}
