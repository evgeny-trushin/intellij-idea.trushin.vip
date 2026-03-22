package vip.trushin.intellij.model

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.file.Path

/**
 * Appends GitFlowEvent records as JSONL to a log file (R5.5, R7.1).
 */
class EventLogger(private val logFile: Path) {

    private val logger = LoggerFactory.getLogger(EventLogger::class.java)

    init {
        logFile.parent?.toFile()?.mkdirs()
        logger.info("Event logger writing to: {}", logFile)
    }

    /**
     * Append a single event as one JSON line.
     */
    fun log(event: GitFlowEvent) {
        val line = event.toJson()
        synchronized(this) {
            PrintWriter(FileWriter(logFile.toFile(), true)).use { writer ->
                writer.println(line)
            }
        }
        logger.debug("Logged event: type={}, outcome={}", event.type, event.outcome)
    }

    /**
     * Read all events from the log file.
     */
    fun readAll(): List<GitFlowEvent> {
        val file = logFile.toFile()
        if (!file.exists()) return emptyList()
        return file.readLines()
            .filter { it.isNotBlank() }
            .map { GitFlowEvent.fromJson(it) }
    }

    /**
     * Read events filtered by type.
     */
    fun readByType(type: GitEventType): List<GitFlowEvent> =
        readAll().filter { it.type == type }

    /**
     * Clear the log file.
     */
    fun clear() {
        logFile.toFile().writeText("")
        logger.info("Event log cleared")
    }

    /**
     * Get the path to the log file.
     */
    fun path(): Path = logFile
}
