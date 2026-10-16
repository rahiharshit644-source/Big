package com.soltini.app.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One replayable action: a tool name and its arguments. */
data class MacroStep(val tool: String, val args: JSONObject)

/** A routine the agent already completed successfully once, so it can be replayed without the LLM. */
data class Macro(
    val key: String,
    val goal: String,
    val steps: List<MacroStep>,
    val successes: Int,
    val failures: Int,
    val lastUsed: Long
)

/**
 * MacroStore - "learned workflows".
 *
 * After a task finishes successfully (and passed the final check) its screen-changing steps are saved under the
 * normalized goal text. The next time the SAME request comes in the engine replays them step by step, verifying
 * every step changed the screen, and falls back to the normal LLM loop from the current screen on the first
 * mismatch. A macro that fails twice is deleted automatically.
 *
 * Only self-contained routines are saved: they must start with open_app, contain only UI actions, never a
 * risky/confirmed step, a password/PIN/OTP, or a bulk ("all") task.
 */
object MacroStore {

    private const val PREFS = "myra_macros"
    private const val KEY = "macros"
    private const val MAX_MACROS = 40
    private const val MAX_FAILURES = 2

    /** Tools that are safe to replay blindly (UI actions only - nothing that sends, pays, deletes or calls). */
    val REPLAY_SAFE_TOOLS: Set<String> =
        AgentToolCatalog.UI_TOOLS + AgentToolCatalog.TYPE_TOOLS + setOf("hide_keyboard", "tap_by_vision", "fill_single_field")

    fun normalize(goal: String): String =
        goal.lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadAll(context: Context): MutableList<Macro> {
        val raw = prefs(context).getString(KEY, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val stepsArr = o.getJSONArray("steps")
                Macro(
                    key = o.getString("key"),
                    goal = o.optString("goal"),
                    steps = List(stepsArr.length()) { j ->
                        val s = stepsArr.getJSONObject(j)
                        MacroStep(s.getString("tool"), s.optJSONObject("args") ?: JSONObject())
                    },
                    successes = o.optInt("successes"),
                    failures = o.optInt("failures"),
                    lastUsed = o.optLong("lastUsed")
                )
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveAll(context: Context, list: List<Macro>) {
        val arr = JSONArray()
        list.sortedByDescending { it.lastUsed }.take(MAX_MACROS).forEach { m ->
            val steps = JSONArray()
            m.steps.forEach { steps.put(JSONObject().put("tool", it.tool).put("args", it.args)) }
            arr.put(
                JSONObject().put("key", m.key).put("goal", m.goal).put("steps", steps)
                    .put("successes", m.successes).put("failures", m.failures).put("lastUsed", m.lastUsed)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun find(context: Context, goal: String): Macro? {
        val key = normalize(goal)
        if (key.isEmpty()) return null
        return loadAll(context).firstOrNull { it.key == key && it.failures < MAX_FAILURES && it.steps.isNotEmpty() }
    }

    @Synchronized
    fun save(context: Context, goal: String, steps: List<MacroStep>) {
        val key = normalize(goal)
        if (key.isEmpty() || steps.isEmpty()) return
        val list = loadAll(context)
        val old = list.firstOrNull { it.key == key }
        list.removeAll { it.key == key }
        list.add(
            Macro(
                key = key, goal = goal.take(200), steps = steps,
                successes = (old?.successes ?: 0) + 1, failures = 0, lastUsed = System.currentTimeMillis()
            )
        )
        saveAll(context, list)
    }

    @Synchronized
    fun recordSuccess(context: Context, key: String) {
        val list = loadAll(context)
        val i = list.indexOfFirst { it.key == key }
        if (i < 0) return
        list[i] = list[i].copy(successes = list[i].successes + 1, failures = 0, lastUsed = System.currentTimeMillis())
        saveAll(context, list)
    }

    @Synchronized
    fun recordFailure(context: Context, key: String) {
        val list = loadAll(context)
        val i = list.indexOfFirst { it.key == key }
        if (i < 0) return
        val updated = list[i].copy(failures = list[i].failures + 1)
        if (updated.failures >= MAX_FAILURES) list.removeAt(i) else list[i] = updated
        saveAll(context, list)
    }

    @Synchronized
    fun count(context: Context): Int = loadAll(context).size

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}
