package com.soltini.app.agent

import android.content.Context
import android.util.Log
import com.soltini.app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * SilentLaneRunner - runs phone-setting tasks that never touch the screen (alarm, timer, volume, brightness,
 * clipboard) IN PARALLEL with the screen task that is running, instead of waiting in the queue.
 *
 * The screen is a single shared resource, so everything that needs it stays sequential. Only a small, safe tool
 * set runs here. If the model cannot do it silently, the item goes back to the normal queue (nothing is lost).
 */
class SilentLaneRunner(
    private val context: Context,
    private val executor: AgentToolExecutor,
    private val appSettings: AppSettings
) {
    companion object {
        private const val TAG = "SilentLaneRunner"
        private const val URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

        val SAFE_TOOLS = setOf("set_alarm", "set_timer", "set_volume", "set_brightness", "copy_to_clipboard")

        private val SAFE_WORDS = Regex(
            "(?<![\\p{L}\\p{N}])(alarm|timer|volume|awaaz|brightness|clipboard)(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        )
        /** Anything that clearly needs another app or the screen keeps the task in the normal queue. */
        private val SCREEN_WORDS = Regex(
            "open|kholo|khol|send|bhej|message|msg|call|play|chala|search|dhundh|youtube|whatsapp|instagram|spotify|maps|navigate|install|click|tap|type|likh|scroll|screen",
            RegexOption.IGNORE_CASE
        )
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun isSilent(goal: String): Boolean = SAFE_WORDS.containsMatchIn(goal) && !SCREEN_WORDS.containsMatchIn(goal)

    /** Returns (success, short message). */
    suspend fun run(goal: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val key = appSettings.effectiveApiKey()
        if (key.isBlank()) return@withContext false to "API key not set"

        val decls = JSONArray()
        val all = AgentToolCatalog.build()
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            if (o.optString("name") in SAFE_TOOLS) decls.put(o)
        }
        val now = SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val prompt = "Do this phone-setting request by calling the tools. Call each needed tool exactly once.\n" +
            "Current local time: $now\nREQUEST: $goal"
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decls)))
            put("toolConfig", JSONObject().put("functionCallingConfig", JSONObject().put("mode", "ANY")))
            put("generationConfig", JSONObject().put("temperature", 0.0))
        }
        val calls = ArrayList<JSONObject>()
        try {
            val req = Request.Builder().url("$URL?key=$key").post(body.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext false to "model HTTP ${resp.code}"
                val parts = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")
                if (parts != null) for (i in 0 until parts.length()) parts.optJSONObject(i)?.optJSONObject("functionCall")?.let { calls.add(it) }
            }
        } catch (e: Exception) {
            return@withContext false to (e.message ?: "request failed")
        }
        if (calls.isEmpty()) return@withContext false to "no tool chosen"

        val done = ArrayList<String>()
        for (c in calls.take(4)) {
            val name = c.optString("name")
            if (name !in SAFE_TOOLS) return@withContext false to "tool $name not allowed in silent lane"
            val res = try { executor.executeSuspend(name, c.optJSONObject("args") ?: JSONObject()) }
            catch (e: Exception) { JSONObject().put("error", e.message ?: "failed") }
            if (res.has("error") || res.optString("status") == "error") {
                Log.w(TAG, "silent $name failed: $res")
                return@withContext false to (res.optString("error").ifBlank { "$name failed" })
            }
            done.add(name)
        }
        true to ("Ho gaya (" + done.joinToString(", ") + ")")
    }
}
