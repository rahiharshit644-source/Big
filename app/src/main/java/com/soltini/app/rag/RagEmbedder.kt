package com.soltini.app.rag

import android.util.Log
import com.soltini.app.settings.AppSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * RagEmbedder - turns text into meaning-vectors with the Gemini embedding API, so a search for
 * "bijli ka bill" can find a chunk that says "electricity invoice" (pure keyword search cannot).
 *
 * Vectors are shortened to [DIMS] numbers and L2-normalised, so similarity is a plain dot product.
 * Returns null / empty on any problem (offline, quota, no key, feature switched off) and callers fall back
 * to keyword search, so retrieval never gets worse than before.
 */
class RagEmbedder(private val appSettings: AppSettings) {

    companion object {
        private const val TAG = "RagEmbedder"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val MODELS = listOf("gemini-embedding-001", "text-embedding-004")
        const val DIMS = 256
        private const val BATCH = 50

        fun toBytes(v: FloatArray): ByteArray {
            val bb = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            v.forEach { bb.putFloat(it) }
            return bb.array()
        }

        fun fromBytes(b: ByteArray): FloatArray {
            val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(b.size / 4) { bb.getFloat() }
        }

        fun dot(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var s = 0f
            for (i in a.indices) s += a[i] * b[i]
            return s
        }
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var workingModel: String? = null

    fun isEnabled(): Boolean = appSettings.isRagSemanticSearchEnabled && appSettings.effectiveApiKey().isNotBlank()

    fun embedQuery(text: String): FloatArray? {
        if (!isEnabled() || text.isBlank()) return null
        return embedBatch(listOf(text.take(2000)), "RETRIEVAL_QUERY")?.firstOrNull()
    }

    /** Embeds [texts] in batches; the result has one entry per input (null where a batch failed). */
    fun embedDocuments(texts: List<String>): List<FloatArray?> {
        if (!isEnabled()) return texts.map { null }
        val out = ArrayList<FloatArray?>(texts.size)
        for (batch in texts.chunked(BATCH)) {
            val r = embedBatch(batch.map { it.take(2000) }, "RETRIEVAL_DOCUMENT")
            if (r == null) { repeat(batch.size) { out.add(null) } } else out.addAll(r)
        }
        return out
    }

    private fun embedBatch(texts: List<String>, taskType: String): List<FloatArray?>? {
        val apiKey = appSettings.effectiveApiKey()
        if (apiKey.isBlank() || texts.isEmpty()) return null
        val order = listOfNotNull(workingModel) + MODELS.filter { it != workingModel }
        for (model in order) {
            try {
                val reqs = JSONArray()
                texts.forEach { t ->
                    reqs.put(
                        JSONObject()
                            .put("model", "models/$model")
                            .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", t))))
                            .put("taskType", taskType)
                            .put("outputDimensionality", DIMS)
                    )
                }
                val body = JSONObject().put("requests", reqs)
                val req = Request.Builder()
                    .url("$BASE_URL/$model:batchEmbedContents?key=$apiKey")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "embed $model HTTP ${resp.code}: ${text.take(160)}")
                        if (resp.code == 429) return null   // quota: stop, try again later
                        return@use
                    }
                    val arr = JSONObject(text).optJSONArray("embeddings") ?: return@use
                    val vectors = List(arr.length()) { i ->
                        val values = arr.getJSONObject(i).optJSONArray("values")
                        if (values == null) null else normalize(FloatArray(values.length()) { j -> values.getDouble(j).toFloat() })
                    }
                    if (vectors.size == texts.size) {
                        workingModel = model
                        return vectors
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "embed $model failed: ${e.message}")
            }
        }
        return null
    }

    private fun normalize(v: FloatArray): FloatArray {
        var n = 0.0
        for (x in v) n += x * x
        val len = sqrt(n).toFloat()
        if (len == 0f) return v
        return FloatArray(v.size) { v[it] / len }
    }
}
