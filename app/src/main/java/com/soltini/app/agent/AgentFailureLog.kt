package com.soltini.app.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * AgentFailureLog - small on-device ring buffer of what went wrong while Myra operated the phone
 * (which tool, in which app, for which goal). Shown in Settings > Agent Log so bugs are easy to spot.
 */
object AgentFailureLog {

    data class Entry(val time: Long, val app: String, val tool: String, val goal: String, val problem: String)

    private const val PREFS = "myra_agent_failures"
    private const val KEY = "log"
    private const val MAX = 60

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun add(context: Context, goal: String, tool: String, problem: String) {
        try {
            val app = try { ScreenObserver.foregroundPackage() } catch (_: Exception) { "" }
            val arr = try { JSONArray(prefs(context).getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
            val entry = JSONObject().put("t", System.currentTimeMillis()).put("app", app).put("tool", tool)
                .put("goal", goal.take(120)).put("problem", problem.take(200))
            val list = ArrayList<JSONObject>()
            for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
            list.add(entry)
            val trimmed = list.takeLast(MAX)
            prefs(context).edit().putString(KEY, JSONArray(trimmed).toString()).apply()
        } catch (_: Exception) {}
    }

    /** Newest first. */
    @Synchronized
    fun all(context: Context): List<Entry> = try {
        val arr = JSONArray(prefs(context).getString(KEY, "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Entry(o.optLong("t"), o.optString("app"), o.optString("tool"), o.optString("goal"), o.optString("problem"))
        }.asReversed()
    } catch (_: Exception) { emptyList() }

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}
