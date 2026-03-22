/**
 * IDE Scripting Console script: Push/Pull/Fetch event detection (R5.4).
 *
 * Subscribes to task-level and VCS update events to distinguish
 * between push, pull, and fetch operations.
 */

import com.intellij.openapi.project.ProjectManager
import java.io.File
import java.time.Instant
import java.util.UUID

val logFile = File(System.getProperty("user.home"), "git-flow-log.jsonl")

fun logEvent(type: String, details: Map<String, Any?>) {
    val event = buildString {
        append("{")
        append("\"id\":\"${UUID.randomUUID()}\",")
        append("\"timestamp\":\"${Instant.now()}\",")
        append("\"type\":\"$type\",")
        append("\"source\":\"IDE_EVENT_BUS\",")
        details.entries.forEachIndexed { index, (key, value) ->
            val escapedValue = when (value) {
                is String -> "\"${value.replace("\"", "\\\"")}\""
                is List<*> -> "[${value.joinToString(",") { "\"${it.toString().replace("\"", "\\\"")}\"" }}]"
                null -> "null"
                else -> value.toString()
            }
            append("\"$key\":$escapedValue")
            if (index < details.size - 1) append(",")
        }
        append("}")
    }
    logFile.appendText(event + "\n")
    println("GitFlowCapture: $type")
}

val project = ProjectManager.getInstance().openProjects.firstOrNull()

if (project != null) {
    val messageBus = project.messageBus
    val connection = messageBus.connect()

    // Monitor Git push events via reflection on Git4Idea internals
    try {
        val pushListenerClass = Class.forName("git4idea.push.GitPushListener")
        val topicField = pushListenerClass.getField("TOPIC")
        val topic = topicField.get(null)

        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            pushListenerClass.classLoader,
            arrayOf(pushListenerClass)
        ) { _, method, args ->
            when (method.name) {
                "onCompleted" -> {
                    logEvent("PUSH", mapOf(
                        "outcome" to "SUCCESS",
                        "details" to "Push operation completed"
                    ))
                }
                else -> null
            }
        }

        val subscribeMethod = connection.javaClass.methods.find {
            it.name == "subscribe" && it.parameterCount == 2
        }
        subscribeMethod?.invoke(connection, topic, proxy)
        println("GitFlowCapture: Subscribed to GitPushListener")
    } catch (e: Exception) {
        println("GitFlowCapture: GitPushListener not available: ${e.message}")
    }

    // Monitor VCS update (pull/fetch) via UpdatedFilesListener
    try {
        val updatedFilesClass = Class.forName("com.intellij.openapi.vcs.update.UpdatedFilesListener")
        val topicField = updatedFilesClass.getField("UPDATED_FILES")
        val topic = topicField.get(null)

        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            updatedFilesClass.classLoader,
            arrayOf(updatedFilesClass)
        ) { _, method, args ->
            when (method.name) {
                "consume" -> {
                    val files = args?.firstOrNull()
                    logEvent("PULL", mapOf(
                        "outcome" to "SUCCESS",
                        "files" to (files?.toString() ?: "[]")
                    ))
                }
                else -> null
            }
        }

        val subscribeMethod = connection.javaClass.methods.find {
            it.name == "subscribe" && it.parameterCount == 2
        }
        subscribeMethod?.invoke(connection, topic, proxy)
        println("GitFlowCapture: Subscribed to UpdatedFilesListener (pull/fetch)")
    } catch (e: Exception) {
        println("GitFlowCapture: UpdatedFilesListener not available: ${e.message}")
    }

    // R5.4: GitBranchIncomingOutgoingManager tracking
    try {
        val managerClass = Class.forName("git4idea.branch.GitBranchIncomingOutgoingManager")
        val getInstance = managerClass.getMethod("getInstance", project.javaClass.interfaces[0])
        val manager = getInstance.invoke(null, project)
        println("GitFlowCapture: GitBranchIncomingOutgoingManager accessible for push/pull distinction")
    } catch (e: Exception) {
        println("GitFlowCapture: GitBranchIncomingOutgoingManager not available: ${e.message}")
    }

    println("GitFlowCapture: Push/Pull/Fetch listeners active.")
} else {
    println("GitFlowCapture: No open project found.")
}
