package com.soltini.app.agent

import android.content.Context
import com.soltini.app.notifications.NotificationRepository

/**
 * OtpReader - finds a fresh one-time code in recent notifications (SMS, email, bank apps).
 * Opt-in: AppSettings.isOtpReaderEnabled must be on. Codes older than 2 minutes are ignored.
 */
object OtpReader {

    data class Otp(val code: String, val app: String, val secondsAgo: Long)

    private const val VALID_MS = 120_000L

    private val KEYWORD_THEN_CODE = Regex(
        "(?i)(?:otp|one[- ]?time|verification|verify|passcode|security code|login code|code)\\D{0,40}(\\d{4,8})"
    )
    private val CODE_THEN_KEYWORD = Regex("(?i)(\\d{4,8})\\D{0,30}(?:is your|is the|is ur)")
    private val ANY_CODE = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")
    private val KEYWORDS = Regex("(?i)(otp|one[- ]?time|verification|verify|passcode|security code|login code)")

    fun latest(context: Context): Otp? {
        val now = System.currentTimeMillis()
        val items = try {
            NotificationRepository.getInstance(context).getRecentNotifications(25)
        } catch (_: Exception) { emptyList() }

        for (n in items) {
            val age = now - n.timestamp
            if (age > VALID_MS || age < 0) continue
            val body = "${n.title} ${n.text}"
            if (!KEYWORDS.containsMatchIn(body) && !body.contains("code", ignoreCase = true)) continue

            val code = KEYWORD_THEN_CODE.find(body)?.groupValues?.get(1)
                ?: CODE_THEN_KEYWORD.find(body)?.groupValues?.get(1)
                ?: ANY_CODE.findAll(body).map { it.groupValues[1] }.toList().singleOrNull()
            if (code != null) return Otp(code, n.appName, age / 1000)
        }
        return null
    }
}
