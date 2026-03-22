package vip.trushin.intellij.capture

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import vip.trushin.intellij.model.GitEventType
import vip.trushin.intellij.model.GitFlowEvent
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Generates Markdown use-case reports from captured events and screenshots (R7.4, R7.5).
 */
class ReportGenerator(
    private val eventLogPath: Path,
    private val capturesDir: Path,
    private val outputPath: Path
) {
    private val logger = LoggerFactory.getLogger(ReportGenerator::class.java)
    private val gson = Gson()

    /**
     * Read all events from the JSONL log file.
     */
    private fun readEvents(): List<GitFlowEvent> {
        val file = eventLogPath.toFile()
        if (!file.exists()) {
            logger.warn("Event log not found: {}", eventLogPath)
            return emptyList()
        }
        return file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    gson.fromJson(line, GitFlowEvent::class.java)
                } catch (e: Exception) {
                    logger.warn("Failed to parse event line: {}", e.message)
                    null
                }
            }
    }

    /**
     * Map Git event type to equivalent CLI command.
     */
    private fun gitCommandEquivalent(event: GitFlowEvent): String {
        return when (event.type) {
            GitEventType.COMMIT -> {
                val files = if (event.files.isNotEmpty()) event.files.joinToString(" ") else "."
                "git add $files && git commit -m \"<message>\""
            }
            GitEventType.PUSH -> "git push origin ${event.branch}"
            GitEventType.PULL -> "git pull origin ${event.branch}"
            GitEventType.FETCH -> "git fetch --all"
            GitEventType.MERGE -> "git merge ${event.metadata["sourceBranch"] ?: "<branch>"}"
            GitEventType.REBASE -> "git rebase ${event.metadata["ontoBranch"] ?: "main"}"
            GitEventType.CHECKOUT -> "git checkout ${event.branch}"
            GitEventType.BRANCH_CREATE -> "git checkout -b ${event.branch}"
            GitEventType.STASH -> "git stash push -m \"<message>\""
            GitEventType.CHERRY_PICK -> {
                val commits = event.commits.joinToString(" ")
                "git cherry-pick $commits"
            }
            GitEventType.RESET -> {
                val mode = event.metadata["mode"]?.lowercase() ?: "mixed"
                val commit = event.commits.firstOrNull() ?: "HEAD~1"
                "git reset --$mode $commit"
            }
        }
    }

    /**
     * Generate the full Markdown report (R7.4).
     */
    fun generate(): String {
        val events = readEvents()
        logger.info("Generating report from {} events", events.size)

        val report = buildString {
            appendLine("# IntelliJ IDEA Git Flow Capture Report")
            appendLine()
            appendLine("Generated: ${java.time.Instant.now()}")
            appendLine()
            appendLine("---")
            appendLine()

            // Summary table
            appendLine("## Summary")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|--------|-------|")
            appendLine("| Total events captured | ${events.size} |")
            appendLine("| Event types | ${events.map { it.type }.distinct().joinToString(", ")} |")
            appendLine("| Success rate | ${successRate(events)} |")
            appendLine("| Time range | ${timeRange(events)} |")
            appendLine()

            // Group events by type
            appendLine("## Events by Type")
            appendLine()
            events.groupBy { it.type }.forEach { (type, typeEvents) ->
                appendLine("### $type (${typeEvents.size} events)")
                appendLine()
                appendLine("| # | Timestamp | Source | Branch | Outcome | Git Equivalent |")
                appendLine("|---|-----------|--------|--------|---------|----------------|")
                typeEvents.forEachIndexed { index, event ->
                    val gitCmd = gitCommandEquivalent(event)
                    appendLine("| ${index + 1} | ${event.timestamp} | ${event.source} | ${event.branch} | ${event.outcome} | `$gitCmd` |")
                }
                appendLine()
            }

            // Detailed event log
            appendLine("## Detailed Event Log")
            appendLine()
            events.forEachIndexed { index, event ->
                appendLine("### Event ${index + 1}: ${event.type}")
                appendLine()
                appendLine("- **ID:** `${event.id}`")
                appendLine("- **Timestamp:** ${event.timestamp}")
                appendLine("- **Source:** ${event.source}")
                appendLine("- **UI Path:** ${event.uiPath}")
                appendLine("- **Branch:** ${event.branch}")
                appendLine("- **Outcome:** ${event.outcome}")
                appendLine("- **Git Equivalent:** `${gitCommandEquivalent(event)}`")

                if (event.files.isNotEmpty()) {
                    appendLine("- **Files:** ${event.files.joinToString(", ")}")
                }
                if (event.commits.isNotEmpty()) {
                    appendLine("- **Commits:** ${event.commits.joinToString(", ")}")
                }
                if (event.screenshot.isNotEmpty()) {
                    val relativePath = try {
                        outputPath.parent.relativize(Paths.get(event.screenshot))
                    } catch (e: Exception) { event.screenshot }
                    appendLine("- **Screenshot:** ![${event.type} screenshot]($relativePath)")
                }
                appendLine()
            }

            // Mermaid sequence diagram (R7.5)
            if (events.isNotEmpty()) {
                appendLine("## Workflow Sequence Diagram")
                appendLine()
                appendLine("```mermaid")
                appendLine("sequenceDiagram")
                appendLine("    participant User")
                appendLine("    participant IDE as IntelliJ IDEA")
                appendLine("    participant Git as Git (Local)")
                appendLine("    participant Remote as Git (Remote)")
                appendLine()

                events.forEach { event ->
                    val arrow = if (event.outcome.name == "SUCCESS") "->>" else "--x"
                    when (event.type) {
                        GitEventType.COMMIT ->
                            appendLine("    User${arrow}IDE: Commit (${event.branch})")
                        GitEventType.PUSH -> {
                            appendLine("    User${arrow}IDE: Push")
                            appendLine("    IDE${arrow}Remote: git push (${event.branch})")
                        }
                        GitEventType.PULL -> {
                            appendLine("    User${arrow}IDE: Pull")
                            appendLine("    Remote${arrow}IDE: fetch + merge (${event.branch})")
                        }
                        GitEventType.FETCH -> {
                            appendLine("    User${arrow}IDE: Fetch")
                            appendLine("    Remote${arrow}Git: fetch refs")
                        }
                        GitEventType.MERGE ->
                            appendLine("    User${arrow}IDE: Merge into ${event.branch}")
                        GitEventType.REBASE ->
                            appendLine("    User${arrow}IDE: Rebase onto ${event.branch}")
                        GitEventType.CHECKOUT ->
                            appendLine("    User${arrow}IDE: Checkout ${event.branch}")
                        GitEventType.BRANCH_CREATE ->
                            appendLine("    User${arrow}IDE: Create branch ${event.branch}")
                        GitEventType.STASH ->
                            appendLine("    User${arrow}IDE: Stash changes")
                        GitEventType.CHERRY_PICK ->
                            appendLine("    User${arrow}IDE: Cherry-pick ${event.commits.firstOrNull() ?: ""}")
                        GitEventType.RESET ->
                            appendLine("    User${arrow}IDE: Reset (${event.metadata["mode"] ?: "mixed"})")
                    }
                }

                appendLine("```")
                appendLine()
            }

            // Screenshots index
            val captureFiles = capturesDir.toFile().listFiles()
                ?.filter { it.extension == "png" }
                ?.sortedBy { it.name }
                ?: emptyList()

            if (captureFiles.isNotEmpty()) {
                appendLine("## Captured Screenshots")
                appendLine()
                captureFiles.forEach { file ->
                    val relativePath = try {
                        outputPath.parent.relativize(file.toPath())
                    } catch (e: Exception) { file.name }
                    appendLine("### ${file.nameWithoutExtension}")
                    appendLine()
                    appendLine("![${file.nameWithoutExtension}]($relativePath)")
                    appendLine()
                }
            }
        }

        // Write the report
        outputPath.toFile().writeText(report)
        logger.info("Report generated: {}", outputPath)
        return report
    }

    private fun successRate(events: List<GitFlowEvent>): String {
        if (events.isEmpty()) return "N/A"
        val successCount = events.count { it.outcome.name == "SUCCESS" }
        val rate = (successCount.toDouble() / events.size * 100).toInt()
        return "$rate% ($successCount/${events.size})"
    }

    private fun timeRange(events: List<GitFlowEvent>): String {
        if (events.isEmpty()) return "N/A"
        val first = events.minByOrNull { it.timestamp }?.timestamp ?: "?"
        val last = events.maxByOrNull { it.timestamp }?.timestamp ?: "?"
        return "$first to $last"
    }
}

fun main(args: Array<String>) {
    val eventLog = Paths.get(args.getOrElse(0) {
        "${System.getProperty("user.home")}/git-flow-log.jsonl"
    })
    val capturesDir = Paths.get(args.getOrElse(1) { "captures" })
    val output = Paths.get(args.getOrElse(2) { "use-case-report.md" })

    val generator = ReportGenerator(eventLog, capturesDir, output)
    generator.generate()
    println("Report generated: $output")
}
