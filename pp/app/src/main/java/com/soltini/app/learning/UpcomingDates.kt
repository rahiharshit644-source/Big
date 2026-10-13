package com.soltini.app.learning

import android.content.Context
import android.util.Log
import com.soltini.app.memory2.Memory2Engine
import com.soltini.app.memory2.MemoryType
import com.soltini.app.settings.AppSettings
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * UpcomingDates - birthdays, anniversaries and other yearly dates Boss tells Myra about.
 *
 * Stored in two places on purpose: a small structured list (so "kiska birthday aane wala hai?" is computed
 * exactly) and a normal Memory 2.0 fact (so everyday recall and the Settings > Memory screen show it too).
 */
object UpcomingDates {

    private const val TAG = "UpcomingDates"
    private const val PREFS = "myra_dates"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context): JSONArray =
        try { JSONArray(prefs(context).getString("dates", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }

    private fun save(context: Context, arr: JSONArray) = prefs(context).edit().putString("dates", arr.toString()).apply()

    private fun norm(s: String) = s.trim().lowercase(Locale.ROOT)

    private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    fun remember(context: Context, label: String, kind: String, month: Int, day: Int, year: Int?): JSONObject {
        if (label.isBlank()) return JSONObject().put("status", "error").put("error", "label (whose date) is required")
        if (month !in 1..12 || day !in 1..31) return JSONObject().put("status", "error").put("error", "month must be 1-12 and day 1-31")
        try { LocalDate.of(2024, month, day) } catch (_: Exception) {
            return JSONObject().put("status", "error").put("error", "That date does not exist.")
        }
        val k = kind.ifBlank { "birthday" }.lowercase(Locale.ROOT)
        val arr = load(context)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!(norm(o.optString("label")) == norm(label) && o.optString("kind") == k)) out.put(o)
        }
        val entry = JSONObject().put("label", label.trim()).put("kind", k).put("month", month).put("day", day)
        if (year != null && year in 1900..2100) entry.put("year", year)
        out.put(entry)
        save(context, out)

        // mirror into Memory 2.0
        try {
            val settings = AppSettings(context)
            val eng = Memory2Engine.getInstance(context.applicationContext, settings)
            val text = "${label.trim()}'s $k is on $day ${MONTHS[month - 1]}" + (if (year != null) " (year $year)" else "") + "."
            val key = "date:$k:${norm(label)}"
            val existing = eng.getAllMemoriesUnified().firstOrNull { it.key == key }
            if (existing != null) eng.updateMemory(existing.id, text, MemoryType.FACT, newConfirmed = true)
            else eng.saveLongTerm(key = key, fact = text, importance = 0.8f, type = MemoryType.FACT, confidence = 0.95f, userConfirmed = true)
        } catch (e: Exception) {
            Log.w(TAG, "memory mirror failed: ${e.message}")
        }
        return JSONObject().put("status", "saved").put("label", label.trim()).put("kind", k)
            .put("date", "$day ${MONTHS[month - 1]}")
    }

    fun forget(context: Context, label: String, kind: String?): JSONObject {
        val arr = load(context)
        val out = JSONArray()
        var removed = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val match = norm(o.optString("label")) == norm(label) && (kind.isNullOrBlank() || o.optString("kind") == kind.lowercase(Locale.ROOT))
            if (match) {
                removed++
                try {
                    val eng = Memory2Engine.getInstance(context.applicationContext, AppSettings(context))
                    eng.getAllMemoriesUnified().firstOrNull { it.key == "date:${o.optString("kind")}:${norm(label)}" }?.let { eng.deleteMemory(it.id) }
                } catch (_: Exception) {}
            } else out.put(o)
        }
        save(context, out)
        return JSONObject().put("status", if (removed > 0) "removed" else "not_found").put("removed", removed)
    }

    private fun nextOccurrence(month: Int, day: Int, today: LocalDate): LocalDate {
        fun at(year: Int): LocalDate {
            // 29 Feb falls back to 28 Feb in non-leap years
            return if (month == 2 && day == 29 && !java.time.Year.isLeap(year.toLong())) LocalDate.of(year, 2, 28) else LocalDate.of(year, month, day)
        }
        val thisYear = at(today.year)
        return if (!thisYear.isBefore(today)) thisYear else at(today.year + 1)
    }

    fun upcoming(context: Context, withinDays: Int): JSONObject {
        val today = LocalDate.now()
        val arr = load(context)
        val list = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val next = nextOccurrence(o.getInt("month"), o.getInt("day"), today)
            val inDays = ChronoUnit.DAYS.between(today, next).toInt()
            if (inDays > withinDays.coerceIn(1, 366)) continue
            val r = JSONObject().put("label", o.optString("label")).put("kind", o.optString("kind"))
                .put("date", "${next.dayOfMonth} ${MONTHS[next.monthValue - 1]}").put("in_days", inDays)
            if (o.has("year")) r.put("turning_age_or_years", next.year - o.getInt("year"))
            list.add(r)
        }
        list.sortBy { it.getInt("in_days") }
        return JSONObject().put("status", "success").put("within_days", withinDays).put("count", list.size)
            .put("dates", JSONArray(list))
    }

    /** One short sentence if something falls on today's date, else null (used for the wake-up greeting). */
    fun todayHint(context: Context): String? {
        val today = LocalDate.now()
        val arr = load(context)
        val parts = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getInt("month") == today.monthValue && o.getInt("day") == today.dayOfMonth) {
                parts.add("${o.optString("label")}'s ${o.optString("kind")}")
            }
        }
        return if (parts.isEmpty()) null else "Today is " + parts.joinToString(" and ") + "."
    }
}
