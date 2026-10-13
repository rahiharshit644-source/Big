package com.soltini.app.browser

import android.content.Context
import android.util.Log
import com.soltini.app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * BrowserAgentLoop
 *
 * Implements the autonomous execution loop for complex web tasks following
 * the "agent-browser" (snapshot + refs) pattern.
 *
 * Loop Architecture:
 *   Goal -> Open/Navigate -> [Snapshot -> LLM Decision -> Execute Action (Click/Fill/Select) -> Settle] -> Repeat (up to maxSteps) -> Final Synthesized Answer
 */
class BrowserAgentLoop(
    private val context: Context,
    private val browserController: BrowserController,
    private val appSettings: AppSettings
) {

    companion object {
        private const val TAG = "BrowserAgentLoop"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val PRIMARY_MODEL = "gemini-2.5-flash"
        private const val FALLBACK_MODEL = "gemini-2.0-flash-lite"
        private const val MAX_STEPS = 12
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    data class StepLog(
        val step: Int,
        val action: String,
        val details: String,
        val outcome: String
    )

    /**
     * Runs an autonomous multi-step browser task until completion or max steps.
     */
    suspend fun executeTask(
        goal: String,
        startUrl: String? = null,
        onProgress: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = appSettings.effectiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext "Error: Gemini API key is missing in Settings. Please set your API key first."
        }

        Log.i(TAG, "Starting BrowserAgentLoop for goal: \"$goal\"")
        onProgress?.invoke("Browser task shuru ho raha hai: \"$goal\"...")

        // Step 1: Initial navigation if URL provided or needed
        if (!startUrl.isNullOrBlank()) {
            browserController.open(startUrl)
        } else if (browserController.currentUrl.value.isBlank() || browserController.currentUrl.value == "about:blank") {
            // Infer starting destination or open Google search
            val initialSearch = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(goal, "UTF-8")
            browserController.open(initialSearch)
        }

        val stepHistory = mutableListOf<StepLog>()
        var finalAnswer: String? = null

        for (step in 1..MAX_STEPS) {
            if (browserController.isStopRequested()) {
                finalAnswer = "Task user ne rok diya."
                break
            }
            val progressMsg = "Step $step/$MAX_STEPS: Analyzing webpage..."
            Log.i(TAG, progressMsg)
            onProgress?.invoke(progressMsg)

            // 1. Capture snapshot with @e1, @e2 refs
            val snapshot = browserController.snapshot()

            // 2. Query Gemini for next decision based on goal and snapshot
            val decisionJson = queryModelForDecision(goal, snapshot, stepHistory, apiKey)
            if (decisionJson == null) {
                Log.w(TAG, "Failed to get LLM decision at step $step, attempting fallback read.")
                break
            }

            val isComplete = decisionJson.optBoolean("task_completed", false)
            val action = decisionJson.optString("action", "snapshot").lowercase().trim()
            val ref = decisionJson.optString("ref", "").trim()
            val text = decisionJson.optString("text", "").trim()
            val answer = decisionJson.optString("answer", "").trim()
            val reason = decisionJson.optString("reason", "")

            Log.i(TAG, "Step $step Decision: action=$action ref=$ref text=$text isComplete=$isComplete reason=$reason")

            if (isComplete || action == "task_complete" || action == "done" || answer.isNotBlank()) {
                finalAnswer = if (answer.isNotBlank()) answer else reason
                Log.i(TAG, "Task completed successfully at step $step! Final answer: $finalAnswer")
                break
            }

            // 3. Execute the chosen browser action
            if (browserController.isStopRequested()) {
                finalAnswer = "Task user ne rok diya."
                break
            }
            // 'open' needs the URL in the url parameter (model returns it in "text")
            val outcome = browserController.executeAction(
                action, ref, text, url = if (action == "open") text else null
            )
            stepHistory.add(StepLog(step, action, "$ref $text".trim(), outcome))
            onProgress?.invoke("Step $step: $action ${if (ref.isNotBlank()) ref else ""} -> $outcome")

            delay(1200L) // Settle delay for DOM reaction
        }

        if (finalAnswer.isNullOrBlank()) {
            // Synthesize answer from page content if max steps reached
            val visibleText = browserController.readText()
            finalAnswer = synthesizeFinalResponse(goal, visibleText, apiKey)
        }

        return@withContext finalAnswer
    }

    private suspend fun queryModelForDecision(
        goal: String,
        snapshot: String,
        history: List<StepLog>,
        apiKey: String
    ): JSONObject? = withContext(Dispatchers.IO) {
        val historyStr = if (history.isEmpty()) "None (First step)" else {
            history.joinToString("\n") { "Step ${it.step}: action='${it.action}' details='${it.details}' -> outcome='${it.outcome}'" }
        }

        val systemPrompt = """You are an Autonomous Web Browser Agent running on an Android WebView.
Your job is to achieve the USER GOAL by navigating and interacting with the webpage using the snapshot + refs pattern.

USER GOAL: "$goal"

PREVIOUS ACTIONS IN THIS SESSION:
$historyStr

CURRENT WEBPAGE SNAPSHOT:
$snapshot

### INSTRUCTIONS:
1. Examine the current webpage title, URL, main text, and interactive elements (@e1, @e2, etc.).
2. If the user's goal has been achieved or the needed information is already visible in the snapshot/page, set "task_completed": true and provide the final detailed "answer" in natural Hindi/Hinglish.
3. Otherwise, select the single best next action to advance toward the goal:
   - "click": click ref (@e1, @e2)
   - "fill": type text into input ref (@e1, text="...")
   - "select": choose option in dropdown (@e1, text="option")
   - "press": press key (e.g. text="Enter")
   - "scroll": scroll page (text="down" or "up")
   - "open": navigate to a new URL
   - "back": go back in history
4. You must output ONLY valid JSON matching this schema:
{
  "thought": "brief reasoning of what to do next",
  "action": "click" | "fill" | "select" | "press" | "scroll" | "open" | "task_complete",
  "ref": "@e1" or "" (required for click, fill, select),
  "text": "text to fill or key to press" or "",
  "task_completed": true or false,
  "answer": "Final concise answer to give the user if task is completed, otherwise empty string"
}"""

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            })
        }

        val models = listOf(PRIMARY_MODEL, FALLBACK_MODEL)
        for (model in models) {
            val url = "$BASE_URL/$model:generateContent"
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("x-goog-api-key", apiKey)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val resp = httpClient.newCall(req).execute()
                val bodyStr = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    val root = JSONObject(bodyStr)
                    val text = root.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    return@withContext JSONObject(cleaned)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Model $model call failed: ${e.message}")
            }
        }
        return@withContext null
    }

    private suspend fun synthesizeFinalResponse(
        goal: String,
        pageText: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = "User asked: \"$goal\". Here is the text extracted from the final webpage:\n\n${pageText.take(3000)}\n\nProvide a concise, helpful summary in Hindi/Hinglish answering the user's goal directly."
        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }

        val url = "$BASE_URL/$PRIMARY_MODEL:generateContent"
        try {
            val req = Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val resp = httpClient.newCall(req).execute()
            val respBody = resp.use { it.body?.string() ?: "" }
            if (resp.isSuccessful) {
                val root = JSONObject(respBody)
                return@withContext root.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            }
        } catch (_: Exception) {}

        return@withContext "Task complete: Page inspected for \"$goal\"."
    }
}
