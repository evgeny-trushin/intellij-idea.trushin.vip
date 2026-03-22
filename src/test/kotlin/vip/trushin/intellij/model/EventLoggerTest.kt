package vip.trushin.intellij.model

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class EventLoggerTest {

    private lateinit var tempFile: Path
    private lateinit var logger: EventLogger

    @BeforeEach
    fun setup() {
        tempFile = Files.createTempFile("event-log-test-", ".jsonl")
        // Start with empty file
        tempFile.toFile().writeText("")
        logger = EventLogger(tempFile)
    }

    @AfterEach
    fun cleanup() {
        Files.deleteIfExists(tempFile)
    }

    @Test
    fun `log writes JSONL to file`() {
        val event = GitFlowEvent(type = GitEventType.COMMIT, source = ActionSource.UI_ACTION, branch = "main")
        logger.log(event)

        val lines = tempFile.toFile().readLines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("COMMIT"))
        assertTrue(lines[0].contains("main"))
    }

    @Test
    fun `readAll returns logged events`() {
        logger.log(GitFlowEvent(type = GitEventType.COMMIT, source = ActionSource.UI_ACTION))
        logger.log(GitFlowEvent(type = GitEventType.PUSH, source = ActionSource.TOOLBAR))
        logger.log(GitFlowEvent(type = GitEventType.FETCH, source = ActionSource.MENU))

        val events = logger.readAll()
        assertEquals(3, events.size)
        assertEquals(GitEventType.COMMIT, events[0].type)
        assertEquals(GitEventType.PUSH, events[1].type)
        assertEquals(GitEventType.FETCH, events[2].type)
    }

    @Test
    fun `readByType filters correctly`() {
        logger.log(GitFlowEvent(type = GitEventType.COMMIT, source = ActionSource.UI_ACTION))
        logger.log(GitFlowEvent(type = GitEventType.PUSH, source = ActionSource.UI_ACTION))
        logger.log(GitFlowEvent(type = GitEventType.COMMIT, source = ActionSource.KEYBOARD_SHORTCUT))

        val commits = logger.readByType(GitEventType.COMMIT)
        assertEquals(2, commits.size)

        val pushes = logger.readByType(GitEventType.PUSH)
        assertEquals(1, pushes.size)

        val merges = logger.readByType(GitEventType.MERGE)
        assertTrue(merges.isEmpty())
    }

    @Test
    fun `readAll returns empty list for missing file`() {
        val missingFile = tempFile.parent.resolve("nonexistent.jsonl")
        val emptyLogger = EventLogger(missingFile)
        assertTrue(emptyLogger.readAll().isEmpty())
    }

    @Test
    fun `clear empties the log file`() {
        logger.log(GitFlowEvent(type = GitEventType.COMMIT, source = ActionSource.UI_ACTION))
        logger.log(GitFlowEvent(type = GitEventType.PUSH, source = ActionSource.UI_ACTION))
        assertEquals(2, logger.readAll().size)

        logger.clear()
        assertTrue(logger.readAll().isEmpty())
        assertTrue(tempFile.toFile().exists())
    }

    @Test
    fun `path returns the log file path`() {
        assertEquals(tempFile, logger.path())
    }

    @Test
    fun `multiple events preserve order`() {
        val types = listOf(
            GitEventType.BRANCH_CREATE,
            GitEventType.COMMIT,
            GitEventType.PUSH,
            GitEventType.MERGE,
            GitEventType.STASH
        )
        types.forEach { type ->
            logger.log(GitFlowEvent(type = type, source = ActionSource.UI_ACTION))
        }

        val readTypes = logger.readAll().map { it.type }
        assertEquals(types, readTypes)
    }
}
