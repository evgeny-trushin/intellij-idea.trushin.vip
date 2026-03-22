package vip.trushin.intellij.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GitFlowEventTest {

    @Test
    fun `event has auto-generated id and timestamp`() {
        val event = GitFlowEvent(type = GitEventType.COMMIT, source = ActionSource.UI_ACTION)
        assertTrue(event.id.isNotBlank())
        assertTrue(event.timestamp.isNotBlank())
    }

    @Test
    fun `event defaults to empty collections and SUCCESS`() {
        val event = GitFlowEvent(type = GitEventType.PUSH, source = ActionSource.TOOLBAR)
        assertEquals(ActionOutcome.SUCCESS, event.outcome)
        assertTrue(event.files.isEmpty())
        assertTrue(event.commits.isEmpty())
        assertTrue(event.metadata.isEmpty())
        assertEquals("", event.branch)
        assertEquals("", event.uiPath)
        assertEquals("", event.screenshot)
        assertEquals("", event.uiTreeDump)
    }

    @Test
    fun `toJson produces valid JSON round-trip`() {
        val event = GitFlowEvent(
            type = GitEventType.MERGE,
            source = ActionSource.MENU,
            branch = "main",
            files = listOf("a.kt", "b.kt"),
            commits = listOf("abc123"),
            outcome = ActionOutcome.CONFLICT,
            metadata = mapOf("sourceBranch" to "feature/x")
        )
        val json = event.toJson()
        val restored = GitFlowEvent.fromJson(json)

        assertEquals(event.type, restored.type)
        assertEquals(event.source, restored.source)
        assertEquals(event.branch, restored.branch)
        assertEquals(event.files, restored.files)
        assertEquals(event.commits, restored.commits)
        assertEquals(event.outcome, restored.outcome)
        assertEquals(event.metadata, restored.metadata)
    }

    @Test
    fun `toJsonPretty is human-readable`() {
        val event = GitFlowEvent(type = GitEventType.STASH, source = ActionSource.KEYBOARD_SHORTCUT)
        val pretty = event.toJsonPretty()
        assertTrue(pretty.contains("\n"))
        assertTrue(pretty.contains("STASH"))
    }

    @Test
    fun `all event types are represented`() {
        val types = GitEventType.entries
        assertEquals(11, types.size)
        assertTrue(types.contains(GitEventType.CHERRY_PICK))
        assertTrue(types.contains(GitEventType.RESET))
        assertTrue(types.contains(GitEventType.REBASE))
    }

    @Test
    fun `all action sources are represented`() {
        val sources = ActionSource.entries
        assertEquals(4, sources.size)
    }

    @Test
    fun `all outcomes are represented`() {
        val outcomes = ActionOutcome.entries
        assertEquals(4, outcomes.size)
        assertTrue(outcomes.contains(ActionOutcome.CANCELLED))
    }
}
