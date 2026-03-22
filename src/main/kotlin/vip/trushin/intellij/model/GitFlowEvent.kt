package vip.trushin.intellij.model

import com.google.gson.GsonBuilder
import java.time.Instant
import java.util.UUID

/**
 * Type of Git operation captured.
 */
enum class GitEventType {
    COMMIT,
    PUSH,
    PULL,
    FETCH,
    MERGE,
    REBASE,
    CHECKOUT,
    BRANCH_CREATE,
    STASH,
    CHERRY_PICK,
    RESET
}

/**
 * How the action was triggered.
 */
enum class ActionSource {
    UI_ACTION,
    KEYBOARD_SHORTCUT,
    MENU,
    TOOLBAR
}

/**
 * Outcome of the Git operation.
 */
enum class ActionOutcome {
    SUCCESS,
    FAILURE,
    CONFLICT,
    CANCELLED
}

/**
 * Structured event for every captured Git interaction (R3).
 */
data class GitFlowEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String = Instant.now().toString(),
    val type: GitEventType,
    val source: ActionSource,
    val uiPath: String = "",
    val branch: String = "",
    val files: List<String> = emptyList(),
    val commits: List<String> = emptyList(),
    val outcome: ActionOutcome = ActionOutcome.SUCCESS,
    val screenshot: String = "",
    val uiTreeDump: String = "",
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        private val gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

        private val compactGson = GsonBuilder().create()

        fun fromJson(json: String): GitFlowEvent = gson.fromJson(json, GitFlowEvent::class.java)
    }

    fun toJson(): String = compactGson.toJson(this)

    fun toJsonPretty(): String = gson.toJson(this)
}
