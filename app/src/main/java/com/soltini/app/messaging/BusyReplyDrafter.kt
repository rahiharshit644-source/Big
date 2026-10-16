package com.soltini.app.messaging

import android.content.Context
import android.util.Log
import com.soltini.app.settings.AppSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * BusyReplyDrafter
 *
 * Turns what the contact actually wrote into ONE reply that answers every point.
 * Uses Gemini; if there is no API key / no network it falls back to a local template that is
 * at least tone-aware (casual for family) and varies its wording.
 *
 * NOTE: this class only WRITES text. Nothing is ever sent from here.
 */
class BusyReplyDrafter(private val context: Context) {

    companion object {
        private const val TAG = "BusyReplyDrafter"
        private const val URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun draft(
        userName: String,
        busyReason: String,
        contact: String,
        relation: String?,
        incoming: List<String>,
        previousReplies: List<String>
    ): String {
        val ai = try { askGemini(userName, busyReason, contact, relation, incoming, previousReplies) } catch (e: Exception) {
            Log.w(TAG, "Gemini draft failed: ${e.message}")
            null
        }
        return ai?.takeIf { it.isNotBlank() } ?: fallback(userName, busyReason, relation, incoming, previousReplies)
    }

    private fun askGemini(
        userName: String, busyReason: String, contact: String, relation: String?,
        incoming: List<String>, previousReplies: List<String>
    ): String? {
        val key = AppSettings(context).effectiveApiKey()
        if (key.isBlank()) return null

        val tone = if (relation != null)
            "CASUAL and warm, like talking to family: simple everyday words, no formal phrases, you may call them '$relation' once, at most one emoji."
        else
            "polite and friendly, natural, not stiff and not robotic."

        val prompt = buildString {
            appendLine("You write a chat reply ON BEHALF OF $userName, who is busy right now ($busyReason) and cannot chat.")
            appendLine("Contact: $contact${if (relation != null) " (their $relation)" else ""}.")
            appendLine("Their new messages, oldest first:")
            incoming.forEachIndexed { i, m -> appendLine("${i + 1}. $m") }
            appendLine()
            appendLine("RULES:")
            appendLine("- Read EVERY message and answer EACH question / point. Do not ignore anything they asked.")
            appendLine("- Reply in the same language and style as the contact (Hinglish stays Hinglish, English stays English).")
            appendLine("- 1 to 3 short sentences, like a real person typing. Mention being busy at most once, and only if it helps.")
            appendLine("- Tone: $tone")
            appendLine("- Never reveal you are an assistant/AI/bot. Never write 'auto-reply' or 'Myra'.")
            appendLine("- Do NOT invent facts, promise exact times, share private info, or agree to money / OTP / password / sensitive requests. If you cannot know the answer, say $userName will check and reply once free.")
            appendLine("- If it is urgent or an emergency, tell them to call.")
            if (previousReplies.isNotEmpty()) {
                appendLine("- Do NOT repeat or paraphrase these earlier replies to this contact:")
                previousReplies.takeLast(3).forEach { appendLine("  * $it") }
            }
            appendLine()
            append("Output ONLY the reply text, nothing else.")
        }

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 220))
        }.toString()

        val req = Request.Builder()
            .url("$URL?key=$key")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val raw = resp.body?.string() ?: return null
            val text = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
            return sanitize(text)
        }
    }

    private fun sanitize(t: String?): String? {
        val s = t?.trim()?.trim('"', '\'', '`')?.trim() ?: return null
        if (s.isBlank()) return null
        val lower = s.lowercase()
        if (lower.contains("auto-reply") || lower.contains("as an ai") || lower.contains("myra")) return null
        return s.take(400)
    }

    // ─── Offline fallback ───────────────────────────────────────────────────

    private fun fallback(
        userName: String, busyReason: String, relation: String?,
        incoming: List<String>, previousReplies: List<String>
    ): String {
        val lower = incoming.joinToString(" ").lowercase()
        val reason = when {
            busyReason.contains("driv") -> "gaadi chala raha hu"
            busyReason.contains("meet") -> "meeting me hu"
            busyReason.contains("stud") || busyReason.contains("padh") -> "padhai kar raha hu"
            busyReason.contains("gym") -> "gym me hu"
            busyReason.contains("sleep") || busyReason.contains("so ") -> "so raha hu"
            busyReason.isNotBlank() && busyReason != "busy" -> "$busyReason me hu"
            else -> "thoda busy hu"
        }
        val urgent = lower.contains("urgent") || lower.contains("emergency") || lower.contains("jaldi") || lower.contains("zaroori")
        val wantsCall = lower.contains("call")
        val asksWhere = lower.contains("kahan") || lower.contains("kidhar") || lower.contains("where")

        val options: List<String> = if (relation != null) {
            val who = relation.replaceFirstChar { it.uppercase() }
            when {
                urgent -> listOf("$who abhi $reason, zaroori hai to seedha call kar do 🙏")
                wantsCall -> listOf("$who abhi $reason, call nahi utha paunga. Free hote hi khud call karta hu 🙂")
                asksWhere -> listOf("$who abhi $reason, thodi der me free hoke batata hu 🙂")
                else -> listOf(
                    "$who abhi $reason, free hote hi baat karta hu 🙂",
                    "Haan $relation, dekh liya msg. Abhi $reason, thodi der me reply karta hu"
                )
            }
        } else {
            when {
                urgent -> listOf("Hello, $userName abhi $reason. Agar bahut zaroori hai to please seedha call kar lijiye.")
                wantsCall -> listOf("Hello, $userName abhi $reason aur call nahi utha sakte. Free hote hi aapko call karenge.")
                asksWhere -> listOf("Hello, $userName abhi $reason. Free hote hi aapko bata denge.")
                else -> listOf(
                    "Hello! $userName abhi $reason. Aapka message dekh liya hai, free hote hi reply karenge.",
                    "Hi, $userName abhi $reason. Aapki baat note kar li hai, thodi der me wapas aate hain."
                )
            }
        }
        return options.firstOrNull { it !in previousReplies } ?: options.last()
    }
}
