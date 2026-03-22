package vip.trushin.intellij.fixtures

import com.intellij.remoterobot.RemoteRobot
import org.junit.jupiter.api.BeforeAll
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
     * Get the IDE frame fixture.
     */
    protected fun ideFrame(): IdeFrameFixture = robot.ideFrame()

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
