package vip.trushin.intellij.capture

import com.intellij.remoterobot.RemoteRobot
import org.slf4j.LoggerFactory
import vip.trushin.intellij.model.*
import java.nio.file.Path
import java.time.Duration

/**
 * Orchestrates the capture pipeline: screenshot + UI dump before/after
 * each action, event correlation (R6).
 */
class CapturePipeline(
    private val robot: RemoteRobot,
    private val eventLogger: EventLogger,
    private val screenCapture: ScreenCapture
) {
    private val logger = LoggerFactory.getLogger(CapturePipeline::class.java)

    /**
     * Execute a Git action with full capture pipeline (R6.1-R6.4).
     *
     * @param eventType  The type of Git event being performed
     * @param source     How the action was triggered
     * @param label      Human-readable label for file naming
     * @param branch     Current branch at time of action
     * @param action     Lambda that performs the actual UI automation
     * @return           The logged GitFlowEvent
     */
    fun captureAction(
        eventType: GitEventType,
        source: ActionSource,
        label: String,
        branch: String = "",
        files: List<String> = emptyList(),
        action: () -> ActionOutcome
    ): GitFlowEvent {
        logger.info("=== Capture: {} ({}) ===", label, eventType)

        // R6.1: Before action - capture screenshot + UI tree
        val beforeScreenshot = screenCapture.takeScreenshot("before_$label")
        val beforeUiTree = screenCapture.dumpUiTree("before_$label")
        logger.info("Pre-capture complete: screenshot={}, tree={}", beforeScreenshot, beforeUiTree)

        // R6.2: Execute the action
        var errorMessage: String? = null
        val outcome = try {
            action()
        } catch (e: Exception) {
            errorMessage = "${e.javaClass.simpleName}: ${e.message}"
            logger.error("Action '{}' failed: {}", label, errorMessage, e)
            ActionOutcome.FAILURE
        }

        // R6.3: After action - capture screenshot + UI tree
        val afterScreenshot = screenCapture.takeScreenshot("after_$label")
        val afterUiTree = screenCapture.dumpUiTree("after_$label")
        logger.info("Post-capture complete: screenshot={}, tree={}", afterScreenshot, afterUiTree)

        // Build and log the event
        val eventMetadata = if (errorMessage != null) mapOf("error" to errorMessage) else emptyMap()
        val event = GitFlowEvent(
            type = eventType,
            source = source,
            uiPath = label,
            branch = branch,
            files = files,
            outcome = outcome,
            screenshot = afterScreenshot,
            uiTreeDump = afterUiTree,
            metadata = eventMetadata
        )
        eventLogger.log(event)

        logger.info("=== Capture complete: {} -> {} ===", label, outcome)
        return event
    }

    /**
     * Wait for an IDE event to appear in the event log after a UI action,
     * correlating UI actions with internal Git events (R6.4).
     */
    fun waitForEventCorrelation(
        eventType: GitEventType,
        timeout: Duration = Duration.ofSeconds(10)
    ): GitFlowEvent? {
        val deadline = System.currentTimeMillis() + timeout.toMillis()
        val initialCount = eventLogger.readByType(eventType).size

        while (System.currentTimeMillis() < deadline) {
            val current = eventLogger.readByType(eventType)
            if (current.size > initialCount) {
                val newEvent = current.last()
                logger.info("Correlated event found: type={}, id={}", eventType, newEvent.id)
                return newEvent
            }
            Thread.sleep(250)
        }
        logger.warn("No correlated event found for {} within {}s", eventType, timeout.seconds)
        return null
    }
}
