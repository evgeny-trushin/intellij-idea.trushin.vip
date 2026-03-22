package vip.trushin.intellij.capture

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import vip.trushin.intellij.model.*
import java.nio.file.Files
import java.nio.file.Path

class ReportGeneratorTest {

    private lateinit var tempDir: Path
    private lateinit var eventLog: Path
    private lateinit var capturesDir: Path
    private lateinit var outputPath: Path

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("report-test-")
        eventLog = tempDir.resolve("events.jsonl")
        capturesDir = tempDir.resolve("captures")
        outputPath = tempDir.resolve("report.md")
        Files.createDirectories(capturesDir)
    }

    @AfterEach
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    private fun writeEvents(vararg events: GitFlowEvent) {
        eventLog.toFile().writeText(
            events.joinToString("\n") { it.toJson() }
        )
    }

    @Test
    fun `generates report from empty log`() {
        eventLog.toFile().writeText("")
        val generator = ReportGenerator(eventLog, capturesDir, outputPath)
        val report = generator.generate()

        assertTrue(report.contains("# IntelliJ IDEA Git Flow Capture Report"))
        assertTrue(report.contains("Total events captured | 0"))
        assertTrue(outputPath.toFile().exists())
    }

    @Test
    fun `generates report with missing log file`() {
        val generator = ReportGenerator(
            tempDir.resolve("missing.jsonl"), capturesDir, outputPath
        )
        val report = generator.generate()
        assertTrue(report.contains("Total events captured | 0"))
    }

    @Test
    fun `generates summary table with event data`() {
        writeEvents(
            GitFlowEvent(
                type = GitEventType.BRANCH_CREATE,
                source = ActionSource.UI_ACTION,
                branch = "feature/test",
                outcome = ActionOutcome.SUCCESS
            ),
            GitFlowEvent(
                type = GitEventType.COMMIT,
                source = ActionSource.KEYBOARD_SHORTCUT,
                branch = "feature/test",
                outcome = ActionOutcome.SUCCESS
            ),
            GitFlowEvent(
                type = GitEventType.PUSH,
                source = ActionSource.TOOLBAR,
                branch = "feature/test",
                outcome = ActionOutcome.FAILURE
            )
        )

        val generator = ReportGenerator(eventLog, capturesDir, outputPath)
        val report = generator.generate()

        assertTrue(report.contains("Total events captured | 3"))
        assertTrue(report.contains("66%"))  // 2/3 success
        assertTrue(report.contains("BRANCH_CREATE"))
        assertTrue(report.contains("COMMIT"))
        assertTrue(report.contains("PUSH"))
    }

    @Test
    fun `generates git command equivalents`() {
        writeEvents(
            GitFlowEvent(type = GitEventType.COMMIT, source = ActionSource.UI_ACTION, files = listOf("Main.kt")),
            GitFlowEvent(type = GitEventType.PUSH, source = ActionSource.UI_ACTION, branch = "main"),
            GitFlowEvent(type = GitEventType.PULL, source = ActionSource.UI_ACTION, branch = "develop"),
            GitFlowEvent(type = GitEventType.FETCH, source = ActionSource.UI_ACTION),
            GitFlowEvent(type = GitEventType.BRANCH_CREATE, source = ActionSource.UI_ACTION, branch = "feature/x"),
            GitFlowEvent(type = GitEventType.CHECKOUT, source = ActionSource.UI_ACTION, branch = "main"),
            GitFlowEvent(type = GitEventType.STASH, source = ActionSource.UI_ACTION),
            GitFlowEvent(
                type = GitEventType.CHERRY_PICK,
                source = ActionSource.UI_ACTION,
                commits = listOf("abc123")
            ),
            GitFlowEvent(
                type = GitEventType.RESET,
                source = ActionSource.UI_ACTION,
                metadata = mapOf("mode" to "hard"),
                commits = listOf("def456")
            ),
            GitFlowEvent(
                type = GitEventType.MERGE,
                source = ActionSource.UI_ACTION,
                metadata = mapOf("sourceBranch" to "feature/y")
            ),
            GitFlowEvent(
                type = GitEventType.REBASE,
                source = ActionSource.UI_ACTION,
                metadata = mapOf("ontoBranch" to "develop")
            )
        )

        val generator = ReportGenerator(eventLog, capturesDir, outputPath)
        val report = generator.generate()

        assertTrue(report.contains("git add Main.kt"))
        assertTrue(report.contains("git push origin main"))
        assertTrue(report.contains("git pull origin develop"))
        assertTrue(report.contains("git fetch --all"))
        assertTrue(report.contains("git checkout -b feature/x"))
        assertTrue(report.contains("git checkout main"))
        assertTrue(report.contains("git stash push"))
        assertTrue(report.contains("git cherry-pick abc123"))
        assertTrue(report.contains("git reset --hard def456"))
        assertTrue(report.contains("git merge feature/y"))
        assertTrue(report.contains("git rebase develop"))
    }

    @Test
    fun `generates mermaid sequence diagram`() {
        writeEvents(
            GitFlowEvent(type = GitEventType.BRANCH_CREATE, source = ActionSource.UI_ACTION, branch = "feature/x"),
            GitFlowEvent(type = GitEventType.COMMIT, source = ActionSource.UI_ACTION, branch = "feature/x"),
            GitFlowEvent(type = GitEventType.PUSH, source = ActionSource.UI_ACTION, branch = "feature/x")
        )

        val generator = ReportGenerator(eventLog, capturesDir, outputPath)
        val report = generator.generate()

        assertTrue(report.contains("```mermaid"))
        assertTrue(report.contains("sequenceDiagram"))
        assertTrue(report.contains("participant User"))
        assertTrue(report.contains("participant IDE as IntelliJ IDEA"))
        assertTrue(report.contains("Create branch feature/x"))
        assertTrue(report.contains("Commit"))
        assertTrue(report.contains("Push"))
        assertTrue(report.contains("git push"))
    }

    @Test
    fun `detailed event log includes all fields`() {
        writeEvents(
            GitFlowEvent(
                type = GitEventType.COMMIT,
                source = ActionSource.KEYBOARD_SHORTCUT,
                uiPath = "commit_changes",
                branch = "main",
                files = listOf("a.kt", "b.kt"),
                commits = listOf("abc123"),
                outcome = ActionOutcome.SUCCESS
            )
        )

        val generator = ReportGenerator(eventLog, capturesDir, outputPath)
        val report = generator.generate()

        assertTrue(report.contains("**Source:** KEYBOARD_SHORTCUT"))
        assertTrue(report.contains("**UI Path:** commit_changes"))
        assertTrue(report.contains("**Branch:** main"))
        assertTrue(report.contains("**Outcome:** SUCCESS"))
        assertTrue(report.contains("**Files:** a.kt, b.kt"))
        assertTrue(report.contains("**Commits:** abc123"))
    }

    @Test
    fun `report output file is written`() {
        writeEvents(
            GitFlowEvent(type = GitEventType.FETCH, source = ActionSource.UI_ACTION)
        )

        val generator = ReportGenerator(eventLog, capturesDir, outputPath)
        generator.generate()

        assertTrue(outputPath.toFile().exists())
        assertTrue(outputPath.toFile().readText().isNotBlank())
    }
}
