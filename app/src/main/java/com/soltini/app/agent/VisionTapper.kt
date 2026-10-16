package com.soltini.app.agent

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.soltini.app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * VisionTapper - last-resort "tap what you can SEE" for screens where the accessibility tree is empty or
 * useless (Flutter / game / canvas / WebView / custom-drawn UI).
 *
 *   screenshot -> Gemini vision returns a bounding box for the described element -> tap its centre
 *   -> wait for the UI to settle -> report whether the screen really changed.
 *
 * Privacy: banking / payment apps are never sent to vision (same rule as the screen companion).
 */
class VisionTapper(private val context: Context) {

    companion object {
        private const val TAG = "VisionTapper"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val MODELS = listOf("gemini-2.5-flash", "gemini-2.0-flash")
        private const val MAX_IMG_WIDTH = 1080
    }

    private val appSettings = AppSettings(context)

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private data class Hit(val x: Float, val y: Float, val label: String)

    private fun err(msg: String) = JSONObject().put("status", "error").put("error", msg)

    suspend fun tap(description: String): JSONObject = withContext(Dispatchers.IO) {
        val desc = description.trim()
        if (desc.isEmpty()) return@withContext err("description of what to tap is required")

        val svc = SoltiniAccessibilityService.getInstance()
            ?: return@withContext err("Accessibility Service not active")

        val fg = ScreenObserver.foregroundPackage()
        if (appSettings.isBankingProtectionEnabled && appSettings.isBankingApp(fg)) {
            return@withContext err("A banking/payment app is open, so I do not send its screen to vision. Use normal click or ask Boss to tap.")
        }

        val apiKey = appSettings.effectiveApiKey()
        if (apiKey.isBlank()) return@withContext err("Gemini API key is not set")

        val bmp = svc.takeScreenshotBitmap()
            ?: return@withContext err("Screenshot not available (needs Android 11+, or the screen is protected)")

        var found: Hit? = null
        try {
            found = locate(apiKey, bmp, desc)
        } catch (e: Exception) {
            Log.w(TAG, "locate failed: ${e.message}")
        } finally {
            try { bmp.recycle() } catch (_: Exception) {}
        }
        val hit = found ?: return@withContext err("I could not see '$desc' on the screen.")

        val before = ScreenObserver.signature()
        val tapped = svc.tapAt(hit.x, hit.y)
        if (!tapped) return@withContext err("The tap gesture was not accepted by Android.")

        val after = ScreenObserver.waitForSettle(1500)
        val changed = !after.sameAs(before)
        Log.i(TAG, "vision tap '$desc' -> (${hit.x.toInt()}, ${hit.y.toInt()}) '${hit.label}' changed=$changed")

        JSONObject().apply {
            put("status", "success")
            put("via", "vision")
            put("tapped", hit.label.ifBlank { desc })
            put("x", hit.x.toInt())
            put("y", hit.y.toInt())
            put("screen_changed", changed)
            if (!changed) put("note", "Tap was sent but the screen looks the same. The element may need a different target or the app is still loading.")
        }
    }

    private fun locate(apiKey: String, bmp: Bitmap, desc: String): Hit? {
        val w = bmp.width
        val h = bmp.height
        val scaled = if (w > MAX_IMG_WIDTH) {
            Bitmap.createScaledBitmap(bmp, MAX_IMG_WIDTH, (h * MAX_IMG_WIDTH.toFloat() / w).toInt(), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        if (scaled !== bmp) scaled.recycle()
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        val prompt = "This is a screenshot of an Android phone.\n" +
            "Find the single UI element that best matches this description: \"$desc\".\n" +
            "Return ONLY JSON: {\"found\": true|false, \"label\": \"what the element is\", \"box_2d\": [ymin, xmin, ymax, xmax]}\n" +
            "box_2d are integers from 0 to 1000, normalized to the image (y first, then x). " +
            "If several elements match pick the most prominent / first one. If it is not visible set found=false."

        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", b64)))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0.0).put("responseMimeType", "application/json"))

        for (model in MODELS) {
            try {
                val req = Request.Builder()
                    .url("$BASE_URL/$model:generateContent?key=$apiKey")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "vision $model HTTP ${resp.code}: ${text.take(160)}")
                        return@use
                    }
                    parseHit(text, w, h)?.let { return it }
                }
            } catch (e: Exception) {
                Log.w(TAG, "vision $model failed: ${e.message}")
            }
        }
        return null
    }

    private fun parseHit(responseJson: String, w: Int, h: Int): Hit? {
        val root = JSONObject(responseJson)
        val parts = root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) parts.optJSONObject(i)?.optString("text")?.let { sb.append(it) }
        val raw = sb.toString().trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (raw.isEmpty()) return null

        val obj: JSONObject = if (raw.startsWith("[")) {
            JSONArray(raw).optJSONObject(0) ?: return null
        } else JSONObject(raw)

        if (!obj.optBoolean("found", true)) return null
        val box = obj.optJSONArray("box_2d") ?: return null
        if (box.length() < 4) return null
        val ymin = box.optDouble(0).coerceIn(0.0, 1000.0)
        val xmin = box.optDouble(1).coerceIn(0.0, 1000.0)
        val ymax = box.optDouble(2).coerceIn(0.0, 1000.0)
        val xmax = box.optDouble(3).coerceIn(0.0, 1000.0)
        if (xmax <= xmin || ymax <= ymin) return null

        val cx = ((xmin + xmax) / 2.0 / 1000.0 * w).toFloat()
        val cy = ((ymin + ymax) / 2.0 / 1000.0 * h).toFloat()
        return Hit(cx, cy, obj.optString("label", ""))
    }
}
