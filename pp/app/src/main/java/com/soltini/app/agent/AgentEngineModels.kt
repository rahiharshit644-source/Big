package com.soltini.app.agent

import org.json.JSONArray
import org.json.JSONObject

enum class TaskState { PLANNING, RUNNING, WAITING_USER, PAUSED, COMPLETED, FAILED, CANCELLED }

/** Lightweight fingerprint of what is on screen; used to prove that an action changed something. */
data class ScreenSignature(
    val pkg: String,
    val cls: String,
    val count: Int,
    val hash: Long,
    val texts: List<String>
) {
    fun sameAs(o: ScreenSignature?): Boolean {
        if (o == null) return false
        if (pkg != o.pkg) return false
        if (hash == o.hash) return true
        return count == o.count && texts == o.texts
    }
}

/** Compact, model-readable description of the current screen. */
data class ScreenDigest(
    val text: String,
    val pkg: String,
    val interactiveCount: Int
)

data class TaskPlan(
    val goal: String,
    val scope: String,              // "single" | "all" | "until_found"
    val steps: List<String>,
    val successCriteria: String
)

/** Immutable status picture returned by task_status. */
data class TaskSnapshot(
    val taskId: String,
    val goal: String,
    val state: TaskState,
    val step: Int,
    val maxSteps: Int,
    val lastAction: String,
    val lastResult: String,
    val plan: List<String>,
    val notes: Map<String, String>,
    val processedItems: Int,
    val recoveries: Int,
    val pendingQuestion: String?
) {
    val progressPercent: Int
        get() = when (state) {
            TaskState.COMPLETED -> 100
            TaskState.PLANNING -> 2
            else -> ((step * 100) / maxSteps.coerceAtLeast(1)).coerceIn(3, 95)
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("task_id", taskId)
        put("goal", goal)
        put("state", state.name)
        put("step", step)
        put("max_steps", maxSteps)
        put("progress_percent", progressPercent)
        put("last_action", lastAction)
        put("last_result", lastResult)
        put("plan", JSONArray(plan))
        put("notes", JSONObject(notes as Map<*, *>))
        put("items_processed", processedItems)
        put("recoveries", recoveries)
        if (pendingQuestion != null) put("waiting_for_user", pendingQuestion)
    }
}
