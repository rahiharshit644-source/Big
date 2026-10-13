package com.soltini.app.memory2

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ConversationArchive
 *
 * Saves EVERY conversation turn (voice + typed, yours + Myra's) to plain files on the phone, so Myra
 * can remember and tell you later what was discussed.
 *
 *   <app external files>/conversations/YYYY-MM-DD.jsonl   one JSON line per message (used for recall/search)
 *   <app external files>/conversations/Myra_Conversations.txt   one human-readable file with everything
 *
 * - Fed automatically from Memory2Database.saveConversationEntry(), so no code path can miss a message.
 * - Survives "clear chat": clearing the on-screen transcript does NOT delete the archive.
 * - recall()/buildRecallBlock() are what lets Myra answer "kal humne kya baat ki thi?".
 * - exportToDownloads() copies everything to Downloads/Myra/ so you can open it with any file manager.
 */
class ConversationArchive private constructor(private val appContext: Context) {

    data class Turn(val id: String, val timestamp: Long, val who: String, val text: String)

    companion object {
        private const val TAG = "ConversationArchive"
        private const val DIR = "conversations"
        private const val TXT_NAME = "Myra_Conversations.txt"
        private const val MAX_TEXT = 4000

        @Volatile private var instance: ConversationArchive? = null

        fun init(context: Context): ConversationArchive =
            instance ?: synchronized(this) {
                instance ?: ConversationArchive(context.applicationContext).also { instance = it }
            }

        fun get(): ConversationArchive? = instance

        private val dayFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val stampFmt get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    private val lock = Any()
    private val dir: File by lazy {
        (appContext.getExternalFilesDir(DIR) ?: File(appContext.filesDir, DIR)).also { it.mkdirs() }
    }

    /** id -> last stored text, so re-saves of the same message (updated text) are kept but exact duplicates are not. */
    private val seen = HashMap<String, String>()
    private var seenLoaded = false

    val folderPath: String get() = dir.absolutePath

    private fun whoOf(sender: String) = if (sender.equals("USER", true)) "USER" else "MYRA"

    private fun loadSeenLocked() {
        if (seenLoaded) return
        seenLoaded = true
        try {
            dir.listFiles { f -> f.name.endsWith(".jsonl") }?.forEach { f ->
                f.forEachLine { line ->
                    try {
                        val o = JSONObject(line)
                        seen[o.getString("id")] = o.getString("text")
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadSeen failed: ${e.message}")
        }
    }

    /** Append one message. Safe to call from any thread. */
    fun append(id: String, sender: String, text: String, timestamp: Long) {
        val clean = text.trim().take(MAX_TEXT)
        if (clean.isBlank()) return
        try {
            synchronized(lock) {
                loadSeenLocked()
                if (seen[id] == clean) return
                seen[id] = clean
                val who = whoOf(sender)
                val day = dayFmt.format(Date(timestamp))
                val line = JSONObject().apply {
                    put("id", id); put("t", timestamp); put("who", who); put("text", clean)
                }.toString()
                File(dir, "$day.jsonl").appendText(line + "\n", Charsets.UTF_8)
                val label = if (who == "USER") "You " else "Myra"
                File(dir, TXT_NAME).appendText(
                    "[${stampFmt.format(Date(timestamp))}] $label: ${clean.replace("\n", " ")}\n", Charsets.UTF_8
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "append failed: ${e.message}", e)
        }
    }

    private fun readDay(file: File): List<Turn> {
        val byId = LinkedHashMap<String, Turn>()
        try {
            file.forEachLine { line ->
                try {
                    val o = JSONObject(line)
                    val id = o.getString("id")
                    byId[id] = Turn(id, o.getLong("t"), o.getString("who"), o.getString("text"))
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            Log.w(TAG, "readDay ${file.name}: ${e.message}")
        }
        return byId.values.sortedBy { it.timestamp }
    }

    private fun dayFiles(): List<File> =
        (dir.listFiles { f -> f.name.endsWith(".jsonl") } ?: emptyArray()).sortedBy { it.name }

    /** Number of saved messages. */
    fun count(): Int = synchronized(lock) { dayFiles().sumOf { f -> readDay(f).size } }

    /** The most recent [limit] messages, oldest first. */
    fun recent(limit: Int): List<Turn> = synchronized(lock) {
        val out = ArrayList<Turn>()
        for (f in dayFiles().asReversed()) {
            out.addAll(0, readDay(f))
            if (out.size >= limit) break
        }
        out.takeLast(limit)
    }

    private fun turnsOnDate(date: String): List<Turn> =
        File(dir, "$date.jsonl").takeIf { it.exists() }?.let { readDay(it) } ?: emptyList()

    private fun fmt(t: Turn): String =
        "[${stampFmt.format(Date(t.timestamp))}] ${if (t.who == "USER") "You" else "Myra"}: ${t.text}"

    /**
     * Text block put into Myra's system instruction when a session starts:
     * the last few turns, so she continues naturally and knows what was talked about.
     */
    fun buildRecallBlock(maxTurns: Int = 40, maxChars: Int = 6000): String {
        val turns = recent(maxTurns)
        if (turns.isEmpty()) return ""
        val total = count()
        val sb = StringBuilder()
        sb.append("\n\n### PREVIOUS CONVERSATIONS (saved on this phone — you REMEMBER these)\n")
        sb.append("You have a permanent local conversation memory with $total saved messages. ")
        sb.append("Below are the most recent ones. When the user asks \"pehle humne kya baat ki thi?\", \"kal kya bola tha?\", ")
        sb.append("\"mene tumhe kya bataya tha?\" or refers to anything older, answer from this log; for older or specific ")
        sb.append("things call the `recall_conversation` tool (query / days_ago / date) BEFORE saying you don't remember.\n")
        val lines = ArrayList<String>()
        var used = 0
        for (t in turns.asReversed()) {
            val line = fmt(t).let { if (it.length > 400) it.take(400) + "…" else it }
            if (used + line.length > maxChars) break
            lines.add(line); used += line.length + 1
        }
        lines.asReversed().forEach { sb.append(it).append('\n') }
        return sb.toString()
    }

    /**
     * Backs the `recall_conversation` tool.
     * args: query (keywords), days_ago (0 = today, 1 = yesterday...), date (YYYY-MM-DD), limit, export (bool)
     */
    fun recall(args: JSONObject): JSONObject {
        val result = JSONObject()
        try {
            if (args.optBoolean("export", false)) {
                val path = exportToDownloads()
                result.put("status", if (path != null) "success" else "error")
                result.put("message", if (path != null) "Saved all conversations to $path" else "Export failed")
                return result
            }

            val limit = args.optInt("limit", 12).coerceIn(1, 40)
            val query = args.optString("query", "").trim()
            val date = args.optString("date", "").trim()
            val hasDaysAgo = args.has("days_ago") && !args.isNull("days_ago")

            val pool: List<Turn> = synchronized(lock) {
                when {
                    date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> turnsOnDate(date)
                    hasDaysAgo -> {
                        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -args.optInt("days_ago", 0)) }
                        turnsOnDate(dayFmt.format(cal.time))
                    }
                    else -> dayFiles().takeLast(120).flatMap { readDay(it) }
                }
            }

            val picked: List<Turn> = if (query.isBlank()) {
                pool.takeLast(limit)
            } else {
                val tokens = query.lowercase(Locale.getDefault()).split(Regex("[^\\p{L}\\p{N}]+"))
                    .filter { it.length >= 2 }.distinct()
                val scored = pool.mapIndexed { idx, t ->
                    val low = t.text.lowercase(Locale.getDefault())
                    idx to tokens.count { low.contains(it) }
                }.filter { it.second > 0 }
                    .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenByDescending { pool[it.first].timestamp })
                    .take(limit)
                // include the message right before each hit so the answer has context
                scored.flatMap { (idx, _) -> listOfNotNull(pool.getOrNull(idx - 1), pool[idx]) }
                    .distinctBy { it.id }.sortedBy { it.timestamp }
            }

            result.put("status", "success")
            result.put("total_saved_messages", count())
            result.put("found", picked.size)
            result.put("messages", JSONArray().apply {
                picked.forEach { put(JSONObject().apply {
                    put("time", stampFmt.format(Date(it.timestamp)))
                    put("who", if (it.who == "USER") "user" else "myra")
                    put("text", it.text)
                }) }
            })
            if (picked.isEmpty()) result.put("message", "Nothing matching was found in the saved conversations.")
        } catch (e: Exception) {
            result.put("status", "error"); result.put("message", "Recall failed: ${e.message}")
        }
        return result
    }

    /** Copies the single readable file to Downloads/Myra/. Returns a user-visible path, or null on failure. */
    fun exportToDownloads(): String? {
        return try {
            val src = File(dir, TXT_NAME)
            if (!src.exists()) return null
            val name = "Myra_Conversations_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.txt"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Myra")
                }
                val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
                appContext.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                "Downloads/Myra/$name"
            } else {
                val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Myra").apply { mkdirs() }
                src.copyTo(File(outDir, name), overwrite = true)
                "Downloads/Myra/$name"
            }
        } catch (e: Exception) {
            Log.e(TAG, "export failed: ${e.message}", e)
            null
        }
    }

    /** Deletes the whole archive (only used from the explicit "Delete archive" button). */
    fun deleteAll(): Boolean = synchronized(lock) {
        seen.clear(); seenLoaded = true
        dir.listFiles()?.all { it.delete() } ?: true
    }
}
