package com.soltini.app.weather

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

data class HourPoint(val label: String, val tempC: Int, val desc: String)
data class DayPoint(val label: String, val highC: Int, val lowC: Int, val desc: String)
data class WeatherData(
    val place: String,
    val tempC: Int,
    val feelsC: Int,
    val desc: String,
    val highC: Int,
    val lowC: Int,
    val humidity: Int,
    val windKmph: Int,
    val uvIndex: Int,
    val hourly: List<HourPoint>,
    val days: List<DayPoint>
)

/**
 * Parses the wttr.in "j1" JSON that PublicApiExecutor.fetchWeather() returns inside its "data" field.
 * Returns null when the essential fields are missing, so the UI shows an error instead of made-up numbers.
 * Fields wttr.in does not provide (e.g. air quality) are simply not modelled.
 */
object WeatherParser {

    private fun JSONObject.firstValue(key: String): String =
        optJSONArray(key)?.optJSONObject(0)?.optString("value").orEmpty()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun String.toIntOrNullSafe(): Int? = trim().toIntOrNull()

    fun parse(root: JSONObject, nowHour: Int): WeatherData? {
        val current = root.optJSONArray("current_condition").objects().firstOrNull() ?: return null
        val temp = current.optString("temp_C").toIntOrNullSafe() ?: return null
        val days = root.optJSONArray("weather").objects()
        val today = days.firstOrNull() ?: return null
        val high = today.optString("maxtempC").toIntOrNullSafe() ?: return null
        val low = today.optString("mintempC").toIntOrNullSafe() ?: return null

        val area = root.optJSONArray("nearest_area").objects().firstOrNull()
        val place = listOfNotNull(
            area?.firstValue("areaName")?.ifBlank { null },
            area?.firstValue("region")?.ifBlank { null }
        ).distinct().joinToString(", ").ifBlank { "Current location" }

        // Next few 3-hourly slots, starting with the slot that contains "now"
        val slots = ArrayList<HourPoint>()
        days.take(2).forEachIndexed { dayIdx, d ->
            d.optJSONArray("hourly").objects().forEach { h ->
                val raw = h.optString("time").toIntOrNullSafe() ?: return@forEach
                val hour = raw / 100
                if (dayIdx == 0 && hour + 3 <= nowHour) return@forEach
                val t = h.optString("tempC").toIntOrNullSafe() ?: return@forEach
                val label = if (dayIdx == 0 && slots.isEmpty()) "Now" else formatHour(hour)
                slots += HourPoint(label, t, h.firstValue("weatherDesc"))
            }
        }

        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val dayPoints = days.mapNotNull { d ->
            val hi = d.optString("maxtempC").toIntOrNullSafe() ?: return@mapNotNull null
            val lo = d.optString("mintempC").toIntOrNullSafe() ?: return@mapNotNull null
            val raw = d.optString("date")
            val label = try { inFmt.parse(raw)?.let { outFmt.format(it) } ?: raw } catch (_: Exception) { raw }
            val midDesc = d.optJSONArray("hourly").objects().let { it.getOrNull(it.size / 2) }?.firstValue("weatherDesc").orEmpty()
            DayPoint(label, hi, lo, midDesc)
        }

        return WeatherData(
            place = place,
            tempC = temp,
            feelsC = current.optString("FeelsLikeC").toIntOrNullSafe() ?: temp,
            desc = current.firstValue("weatherDesc").ifBlank { "—" },
            highC = high,
            lowC = low,
            humidity = current.optString("humidity").toIntOrNullSafe() ?: 0,
            windKmph = current.optString("windspeedKmph").toIntOrNullSafe() ?: 0,
            uvIndex = current.optString("uvIndex").toIntOrNullSafe() ?: 0,
            hourly = slots.take(5),
            days = dayPoints
        )
    }

    private fun formatHour(h: Int): String {
        val hh = h % 24
        val suffix = if (hh < 12) "AM" else "PM"
        val h12 = when (val x = hh % 12) { 0 -> 12 else -> x }
        return "$h12 $suffix"
    }
}
