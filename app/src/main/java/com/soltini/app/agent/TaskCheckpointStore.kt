package com.soltini.app.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Everything needed to continue a single phone task after the service / process was killed. */
data class TaskCheckpoint(
    val taskId: String,
    val goal: String,
    val scope: String,
    val planSteps: List<String>,
    val successCriteria: String,
    val step: Int,
    val notes: Map<String, String>,
    val processed: List<String>,
    val actionLog: List<String>,
    val updatedAt: Long
)

/**
 * TaskCheckpointStore - saved after every step of [AgentTaskEngine]; cleared when the task ends on purpose
 * (completed / failed / cancelled). So a checkpoint that is still there means the task was interrupted
 * (service killed, process death, crash) and can be resumed with the `resume_task` tool.
 */
object TaskCheckpointStore {

    private const val PREFS = "myra_task_checkpoint"
    private const val KEY = "cp"
    const val MAX_AGE_MS = 2L * 60 * 60 * 1000

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, cp: TaskCheckpoint) {
        try {
            val o = JSONObject()
                .put("taskId", cp.taskId).put("goal", cp.goal).put("scope", cp.scope)
                .put("planSteps", JSONArray(cp.planSteps)).put("successCriteria", cp.successCriteria)
                .put("step", cp.step)
                .put("notes", JSONObject().also { n -> cp.notes.forEach { (k, v) -> n.put(k, v) } })
                .put("processed", JSONArray(cp.processed.takeLast(200)))
                .put("actionLog", JSONArray(cp.actionLog.takeLast(30)))
                .put("updatedAt", cp.updatedAt)
            prefs(context).edit().putString(KEY, o.toString()).apply()
        } catch (_: Exception) {}
    }

    /** The saved checkpoint if it is recent enough to still make sense, else null. */
    fun load(context: Context, maxAgeMs: Long = MAX_AGE_MS): TaskCheckpoint? {
        val raw = prefs(context).getString(KEY, null) ?: return null
        return try {
            val o = JSONObject(raw)
            val cp = TaskCheckpoint(
                taskId = o.optString("taskId"),
                goal = o.optString("goal"),
                scope = o.optString("scope", "single"),
                planSteps = strings(o.optJSONArray("planSteps")),
                successCriteria = o.optString("successCriteria"),
                step = o.optInt("step"),
                notes = o.optJSONObject("notes")?.let { n -> n.keys().asSequence().associateWith { k -> n.optString(k) } } ?: emptyMap(),
                processed = strings(o.optJSONArray("processed")),
                actionLog = strings(o.optJSONArray("actionLog")),
                updatedAt = o.optLong("updatedAt")
            )
            if (cp.goal.isBlank() || System.currentTimeMillis() - cp.updatedAt > maxAgeMs) null else cp
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    /** Short reminder text for a notification, or null when nothing is waiting. */
    fun summary(context: Context): String? {
        val cp = load(context) ?: return null
        return "Ek kaam beech me ruk gaya tha: \"${cp.goal.take(60)}\" (step ${cp.step}). 'adhura kaam jaari rakho' bolo."
    }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else List(a.length()) { a.optString(it) }
}
