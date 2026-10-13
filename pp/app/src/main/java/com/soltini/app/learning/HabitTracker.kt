package com.soltini.app.learning

import android.content.Context
import android.util.Log
import com.soltini.app.memory2.Memory2Engine
import com.soltini.app.memory2.MemoryType
import com.soltini.app.settings.AppSettings
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

/**
 * HabitTracker - learns what Boss actually does and turns it into memories.
 *
 *  - Favourites: songs (Spotify), apps, contacts (calls / messages), YouTube topics - by how often they are used.
 *  - Routines: "usually opens YouTube around 22:00", when one time window dominates over several days.
 *
 * Counting happens on-device only; a memory is written to Memory 2.0 (visible, editable and deletable in
 * Settings > Memory) once an item crosses 3 / 5 / 10 / 20 / 50 uses. Switch off with
 * AppSettings.isHabitLearningEnabled.
 */
object HabitTracker {

    private const val TAG = "HabitTracker"
    private const val PREFS = "myra_habits"
    private val THRESHOLDS = setOf(3, 5, 10, 20, 50, 100)

    private val lock = Any()

    private fun norm(s: String) = s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").take(80)

    /** Called after every tool execution. Cheap and never throws. */
    fun record(context: Context, tool: String, args: JSONObject, result: JSONObject) {
        try {
            val settings = AppSettings(context)
            if (!settings.isHabitLearningEnabled) return
            val status = result.optString("status", "")
            if (result.has("error") || status == "error" || status == "failed" || status == "needs_permission" ||
                status == "confirmation_required" || status == "clarification_needed" || status == "draft_ready") return

            val entry: Pair<String, String>? = when (tool) {
                "spotify_play" -> "song" to args.optString("query", args.optString("song", ""))
                "open_app" -> "app" to result.optString("app", args.optString("app_name", ""))
                "youtube_search" -> "video topic" to args.optString("query", "")
                "make_phone_call" -> "contact" to args.optString("contact_or_number", "")
                "whatsapp_call" -> "contact" to args.optString("contact_name", args.optString("phone_number", ""))
                "whatsapp_send_message" -> "contact" to args.optString("contact_name", args.optString("phone_number", ""))
                "send_sms" -> "contact" to args.optString("recipient", "")
                else -> null
            }
            val category = entry?.first ?: return
            val label = entry.second.trim()
            if (label.isBlank() || label.length > 80) return
            // phone numbers are not "favourite" material unless they are all we have
            if (category == "contact" && label.count { it.isDigit() } >= 7 && label.none { it.isLetter() }) return

            val (count, routine) = synchronized(lock) {
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val root = try { JSONObject(prefs.getString("data", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
                val key = "$category|${norm(label)}"
                val item = root.optJSONObject(key) ?: JSONObject().put("label", label).put("category", category)
                    .put("count", 0).put("hours", JSONArray(List(24) { 0 })).put("days", JSONArray())
                item.put("label", label)
                item.put("count", item.optInt("count", 0) + 1)
                item.put("last", System.currentTimeMillis())

                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val hours = item.optJSONArray("hours") ?: JSONArray(List(24) { 0 })
                hours.put(hour, hours.optInt(hour, 0) + 1)
                item.put("hours", hours)

                val today = LocalDate.now().toString()
                val days = item.optJSONArray("days") ?: JSONArray()
                val seen = (0 until days.length()).map { days.optString(it) }
                if (today !in seen) {
                    days.put(today)
                    if (days.length() > 14) item.put("days", JSONArray(seen.takeLast(13) + today)) else item.put("days", days)
                }
                root.put(key, item)
                prefs.edit().putString("data", root.toString()).apply()

                Pair(item.optInt("count"), detectRoutine(item))
            }

            if (count in THRESHOLDS) promoteFavourite(context, settings, category, label, count)
            if (routine != null) promoteRoutine(context, settings, category, label, routine)
        } catch (e: Exception) {
            Log.w(TAG, "record failed: ${e.message}")
        }
    }

    /** "22:00" if one 3-hour window holds >= 60% of at least 5 uses spread over >= 3 days, else null. */
    private fun detectRoutine(item: JSONObject): String? {
        val total = item.optInt("count", 0)
        val days = item.optJSONArray("days")?.length() ?: 0
        if (total < 5 || days < 3) return null
        val hours = item.optJSONArray("hours") ?: return null
        var bestHour = -1
        var bestSum = 0
        for (h in 0 until 24) {
            val sum = hours.optInt((h + 23) % 24) + hours.optInt(h) + hours.optInt((h + 1) % 24)
            if (sum > bestSum) { bestSum = sum; bestHour = h }
        }
        return if (bestHour >= 0 && bestSum >= total * 0.6) String.format(Locale.US, "%02d:00", bestHour) else null
    }

    private fun engine(context: Context, settings: AppSettings) = Memory2Engine.getInstance(context.applicationContext, settings)

    private fun upsert(context: Context, settings: AppSettings, key: String, text: String, type: MemoryType, importance: Float) {
        val eng = engine(context, settings)
        val existing = eng.getAllMemoriesUnified().firstOrNull { it.key == key }
        if (existing != null) {
            if (existing.userConfirmed && existing.content != text) {
                // the user edited / pinned it in Settings: do not overwrite their version
                return
            }
            eng.updateMemory(existing.id, text, type, newConfirmed = false)
        } else {
            eng.saveLongTerm(key = key, fact = text, importance = importance, type = type, confidence = 0.8f, userConfirmed = false)
        }
    }

    private fun promoteFavourite(context: Context, settings: AppSettings, category: String, label: String, count: Int) {
        val text = when (category) {
            "song" -> "Boss often plays \"$label\" on Spotify (about $count times) - a favourite."
            "app" -> "Boss uses the app $label a lot (opened about $count times)."
            "contact" -> "Boss often contacts $label (about $count calls/messages) - an important person."
            else -> "Boss often looks up \"$label\" on YouTube (about $count times)."
        }
        upsert(context, settings, "habit:$category:${norm(label)}", text, MemoryType.PREFERENCE, 0.6f)
        Log.i(TAG, "Saved favourite memory: $text")
    }

    private fun promoteRoutine(context: Context, settings: AppSettings, category: String, label: String, at: String) {
        val what = when (category) {
            "song" -> "plays \"$label\" on Spotify"
            "app" -> "opens $label"
            "contact" -> "contacts $label"
            else -> "searches YouTube for \"$label\""
        }
        upsert(context, settings, "routine:$category:${norm(label)}", "Boss usually $what around $at.", MemoryType.ROUTINE, 0.55f)
    }

    /** Top items per category, for the my_favorites tool. */
    fun favourites(context: Context, perCategory: Int = 3): JSONObject {
        val out = JSONObject()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = try { JSONObject(prefs.getString("data", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        val byCat = HashMap<String, MutableList<JSONObject>>()
        for (k in root.keys()) {
            val obj = root.optJSONObject(k) ?: continue
            byCat.getOrPut(obj.optString("category")) { ArrayList() }.add(obj)
        }
        for ((cat, list) in byCat) {
            val arr = JSONArray()
            list.sortedByDescending { it.optInt("count") }.take(perCategory).forEach {
                arr.put(JSONObject().put("name", it.optString("label")).put("times", it.optInt("count")))
            }
            out.put(cat, arr)
        }
        return JSONObject().put("status", if (out.length() == 0) "nothing_learned_yet" else "success").put("favourites", out)
    }

    fun clearAll(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
