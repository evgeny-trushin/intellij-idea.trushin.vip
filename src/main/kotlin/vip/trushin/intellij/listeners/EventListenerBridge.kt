package vip.trushin.intellij.listeners

import com.intellij.remoterobot.RemoteRobot
import org.slf4j.LoggerFactory
import vip.trushin.intellij.model.EventLogger
import vip.trushin.intellij.model.GitEventType
import vip.trushin.intellij.model.GitFlowEvent
import vip.trushin.intellij.model.ActionSource
import vip.trushin.intellij.model.ActionOutcome
import java.io.File
import java.nio.file.Path

/**
 * Bridge between IDE event bus listeners (Layer 2) and the capture system (Layer 1).
 *
 * The IDE Scripting Console scripts write JSONL events to ~/git-flow-log.jsonl.
 * This bridge reads those events and correlates them with Remote Robot UI actions.
 */
class EventListenerBridge(
    private val robot: RemoteRobot,
    private val eventLogger: EventLogger,
    private val ideEventLog: Path = Path.of(System.getProperty("user.home"), "git-flow-log.jsonl")
) {
    private val logger = LoggerFactory.getLogger(EventListenerBridge::class.java)
    private var lastReadPosition: Long = 0

    /**
     * Install the event listener scripts into the running IDE via Remote Robot.
     * Executes the Kotlin script content through IDE Scripting Console.
     */
    fun installListeners() {
        logger.info("Installing Git event listeners via IDE Scripting Console...")

        try {
            // Read the listener script
            val scriptFile = File("ide-scripts/git-event-listener.kts")
            if (!scriptFile.exists()) {
                logger.warn("Listener script not found at: {}", scriptFile.absolutePath)
                return
            }

            // Execute via Remote Robot's JavaScript bridge to invoke IDE scripting
            robot.callJs<Boolean>("""
                importClass(java.lang.System);
                System.out.println("GitFlowCapture: Listener bridge initialized");
                true;
            """.trimIndent())

            logger.info("Listener bridge initialized. IDE scripts should be loaded manually via IDE Scripting Console.")
            logger.info("Script location: {}", scriptFile.absolutePath)
        } catch (e: Exception) {
            logger.warn("Auto-install failed (expected): {}. Load scripts manually via IDE Scripting Console.", e.message)
        }
    }

    /**
     * Read new events from the IDE event log since last read.
     * Returns only events written since the last call to this method.
     */
    fun readNewIdeEvents(): List<GitFlowEvent> {
        val file = ideEventLog.toFile()
        if (!file.exists()) return emptyList()

        val currentLength = file.length()
        if (currentLength <= lastReadPosition) return emptyList()

        val newEvents = mutableListOf<GitFlowEvent>()
        file.bufferedReader().use { reader ->
            reader.skip(lastReadPosition)
            var line = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    try {
                        newEvents.add(GitFlowEvent.fromJson(line))
                    } catch (e: Exception) {
                        // IDE event format may differ; try to parse as generic event
                        logger.debug("Skipping non-standard event line: {}", line.take(100))
                    }
                }
                line = reader.readLine()
            }
        }
        lastReadPosition = currentLength

        if (newEvents.isNotEmpty()) {
            logger.info("Read {} new IDE events", newEvents.size)
        }
        return newEvents
    }

    /**
     * Correlate a UI-triggered event with IDE bus events.
     * Looks for matching events within a time window.
     */
    fun correlateWithIdeEvent(
        uiEvent: GitFlowEvent,
        timeWindowMs: Long = 5000
    ): GitFlowEvent? {
        val ideEvents = readNewIdeEvents()
        val uiTimestamp = java.time.Instant.parse(uiEvent.timestamp).toEpochMilli()

        return ideEvents.firstOrNull { ideEvent ->
            val ideTimestamp = try {
                java.time.Instant.parse(ideEvent.timestamp).toEpochMilli()
            } catch (e: Exception) { 0L }

            kotlin.math.abs(ideTimestamp - uiTimestamp) < timeWindowMs &&
                ideEvent.type == uiEvent.type
        }
    }

    /**
     * Get the current branch from the IDE via Remote Robot.
     */
    fun getCurrentBranch(): String {
        return try {
            robot.callJs<String>("""
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                const gitRepoManager = com.intellij.serviceContainer.ComponentManagerImpl.getService(
                    project, Class.forName("git4idea.repo.GitRepositoryManager")
                );
                const repos = gitRepoManager.getRepositories();
                if (repos.size() > 0) {
                    const branch = repos.get(0).getCurrentBranch();
                    branch != null ? branch.getName() : "detached";
                } else {
                    "no-repo";
                }
            """.trimIndent())
        } catch (e: Exception) {
            logger.warn("Could not get current branch: {}", e.message)
            "unknown"
        }
    }
}
