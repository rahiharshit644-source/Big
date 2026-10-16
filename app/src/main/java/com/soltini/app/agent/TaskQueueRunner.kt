package com.soltini.app.agent

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.soltini.app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * TaskSplitter - pure helpers that decide whether one spoken request is really SEVERAL tasks.
 * (Android-free so it can be unit tested.)
 */
object TaskSplitter {

    const val MAX_TASKS = 12

    /** Strong "do this, THEN that" connectors. Deliberately NOT bare "aur" / "and" (too ambiguous). */
    private val SEQUENCE = Regex(
        "\\s*(?:;|\\n|(?<![\\p{L}\\p{N}])(?:phir|fir|uske\\s+baad|uske\\s+bad|iske\\s+baad|iske\\s+bad|then|and\\s+then|after\\s+that|afterwards)(?![\\p{L}\\p{N}]))\\s*",
        RegexOption.IGNORE_CASE
    )
    private val NUMBERED = Regex("(?m)^\\s*(?:\\d+[.)]|[-*•])\\s+")
    private val LEADING_JUNK = Regex("^(?:aur|and|also|phir|fir|then|,|\\.|-)+\\s*", RegexOption.IGNORE_CASE)
    private val SOFT_CONNECTOR = Regex("(?<![\\p{L}\\p{N}])(aur|and|also|saath\\s+me|plus)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    /** Cheap, offline split on strong sequence words, numbered lists and ';'. */
    fun heuristicSplit(goal: String): List<String> {
        val prepared = goal.trim().replace(NUMBERED, ";")
        return prepared.split(SEQUENCE)
            .map { it.trim().replace(LEADING_JUNK, "").trim().trimEnd(',', ';', '.', ' ') }
            .filter { it.length >= 3 }
            .take(MAX_TASKS)
    }

    /** True when it is worth paying for a model call to decide how to split. */
    fun mightBeMultiTask(goal: String): Boolean {
        val g = goal.trim()
        if (g.isBlank()) return false
        if (heuristicSplit(g).size > 1) return true
        val words = g.split(Regex("\\s+")).size
        return words >= 20 || (words >= 8 && SOFT_CONNECTOR.containsMatchIn(g))
    }

    /** Parses a model reply (JSON array of strings, maybe fenced) into clean sub-tasks. */
    fun parseModelList(raw: String): List<String> {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr = try { JSONArray(cleaned) } catch (_: Exception) { return emptyList() }
        return List(arr.length()) { arr.optString(it).trim() }.filter { it.length >= 3 }.take(MAX_TASKS)
    }
}

enum class QueueItemStatus { PENDING, RUNNING, DONE, FAILED, CANCELLED }

class QueueItem(
    val goal: String,
    var status: QueueItemStatus = QueueItemStatus.PENDING,
    var attempts: Int = 0,
    var result: String = "",
    /** last few steps done while running; used to continue an interrupted task without repeating work */
    var progress: String = ""
)

class TaskQueue(val id: String, val originalGoal: String, val items: List<QueueItem>, val createdAt: Long = System.currentTimeMillis()) {
    fun hasUnfinished(): Boolean = items.any { it.status == QueueItemStatus.PENDING || it.status == QueueItemStatus.RUNNING }
}

/** Saves the task list so progress survives a crash / process kill ("baaki kaam karo" can continue it). */
class TaskQueueStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("myra_task_queue", Context.MODE_PRIVATE)

    fun save(q: TaskQueue) {
        val items = JSONArray()
        q.items.forEach {
            items.put(JSONObject().put("goal", it.goal).put("status", it.status.name).put("attempts", it.attempts).put("result", it.result).put("progress", it.progress))
        }
        val obj = JSONObject().put("id", q.id).put("goal", q.originalGoal).put("createdAt", q.createdAt).put("items", items)
        prefs.edit().putString("queue", obj.toString()).apply()
    }

    fun load(): TaskQueue? {
        val raw = prefs.getString("queue", null) ?: return null
        return try {
            val o = JSONObject(raw)
            val arr = o.getJSONArray("items")
            val items = List(arr.length()) {
                val i = arr.getJSONObject(it)
                QueueItem(
                    i.optString("goal"),
                    runCatching { QueueItemStatus.valueOf(i.optString("status")) }.getOrDefault(QueueItemStatus.PENDING),
                    i.optInt("attempts"), i.optString("result"), i.optString("progress")
                )
            }
            TaskQueue(o.optString("id"), o.optString("goal"), items, o.optLong("createdAt"))
        } catch (_: Exception) { null }
    }

    fun hasUnfinished(): Boolean = load()?.hasUnfinished() == true

    /** Short text for a "you have unfinished work" reminder, or null if nothing (recent) is left. */
    fun unfinishedSummary(maxAgeMs: Long): String? {
        val q = load() ?: return null
        if (!q.hasUnfinished() || System.currentTimeMillis() - q.createdAt > maxAgeMs) return null
        val left = q.items.filter { it.status == QueueItemStatus.PENDING || it.status == QueueItemStatus.RUNNING }
        return "${left.size} kaam adhure hain: " + left.take(3).joinToString("; ") { it.goal.take(40) } +
            ". Phone unlock karke 'baaki kaam karo' bolo."
    }
}

/** Reminder notification for an unfinished task list (never runs anything by itself). */
object TaskQueueNotifier {
    private const val CHANNEL = "myra_task_resume_channel"
    private const val ID = 9015

    fun notifyUnfinished(context: Context, text: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(CHANNEL, "Myra unfinished tasks", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val open = android.app.PendingIntent.getActivity(
                context, 0,
                android.content.Intent(context, com.soltini.app.MainActivity::class.java).apply { flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val n = androidx.core.app.NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("Myra: adhure kaam")
                .setContentText(text)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(ID, n)
        } catch (e: Exception) {
            Log.w("TaskQueueNotifier", "notify failed: ${e.message}")
        }
    }

    fun cancel(context: Context) {
        try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager)?.cancel(ID) } catch (_: Exception) {}
    }
}

/** Lets status / cancel (voice, overlay HUD) reach the running task list. */
object TaskQueueRegistry {
    @Volatile private var current: TaskQueueRunner? = null

    @Synchronized fun tryStart(r: TaskQueueRunner): Boolean {
        if (current != null) return false
        current = r
        return true
    }

    @Synchronized fun finish(r: TaskQueueRunner) { if (current === r) current = null }

    fun isActive(): Boolean = current != null
    fun status(): JSONObject? = current?.snapshotJson()
    fun cancel(): Boolean {
        val r = current ?: return false
        r.cancel()
        return true
    }
}

/**
 * TaskQueueRunner
 *
 * Problem it fixes: one engine run has a fixed step budget (30 / 60), so a request with many tasks
 * ("WhatsApp pe msg bhejo, phir alarm lagao, phir song chalao ...") ran out of steps and failed halfway.
 *
 * Now: the request is split into independent sub-tasks and each gets its OWN fresh [AgentTaskEngine]
 * (own step budget, own time limit). A failed sub-task is retried once with the failure reason, and if it
 * still fails the queue CONTINUES with the next one. At the end Myra reports exactly what was done,
 * what failed (and why) and what is left. Progress is saved so unfinished work can be resumed.
 *
 * A request that is really ONE task goes straight to [AgentTaskEngine], exactly as before.
 */
class TaskQueueRunner(
    private val context: Context,
    private val executor: AgentToolExecutor,
    private val appSettings: AppSettings
) {
    companion object {
        private const val TAG = "TaskQueueRunner"
        private const val URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

        private val ENV_BLOCKERS = listOf(
            "phone is locked", "accessibility service is off", "api key is not set", "another phone task is still running"
        )
    }

    private enum class Outcome { DONE, FAILED, CANCELLED, ABORT }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    private val store = TaskQueueStore(context)

    @Volatile private var cancelled = false
    @Volatile private var currentEngine: AgentTaskEngine? = null
    @Volatile private var queue: TaskQueue? = null
    private var say: (String) -> Unit = { AgentStatusBus.emit(it) }

    fun cancel() {
        cancelled = true
        currentEngine?.cancel()
    }

    fun snapshotJson(): JSONObject {
        val q = queue
        return JSONObject().apply {
            put("queue", true)
            if (q != null) {
                put("total", q.items.size)
                put("done", q.items.count { it.status == QueueItemStatus.DONE })
                put("failed", q.items.count { it.status == QueueItemStatus.FAILED })
                val idx = q.items.indexOfFirst { it.status == QueueItemStatus.RUNNING }
                put("current_index", if (idx >= 0) idx + 1 else JSONObject.NULL)
                put("items", JSONArray().also { a ->
                    q.items.forEach { a.put(JSONObject().put("goal", it.goal).put("status", it.status.name)) }
                })
            }
        }
    }

    // ───────────────────────── entry points ─────────────────────────

    suspend fun run(userGoal: String, onStatus: ((String) -> Unit)? = null): String = withContext(Dispatchers.IO) {
        if (onStatus != null) say = onStatus
        val goal = userGoal.trim()
        if (goal.isBlank()) return@withContext "I did not get a task to do."

        val parts = splitGoal(goal)
        if (parts.size <= 1) {
            // one task: behave exactly like before
            return@withContext AgentTaskEngine(context, executor, appSettings).run(goal, onStatus)
        }
        Log.i(TAG, "Split into ${parts.size} sub-tasks: $parts")
        execute(TaskQueue(UUID.randomUUID().toString().take(8), goal, parts.map { QueueItem(it) }))
    }

    /** Continues a saved, unfinished task list. */
    suspend fun resume(onStatus: ((String) -> Unit)? = null): String = withContext(Dispatchers.IO) {
        if (onStatus != null) say = onStatus
        val q = store.load()?.takeIf { it.hasUnfinished() } ?: return@withContext "Koi adhura task-list nahi mila."
        q.items.filter { it.status == QueueItemStatus.RUNNING }.forEach { it.status = QueueItemStatus.PENDING }
        execute(q)
    }

    // ───────────────────────── queue loop ─────────────────────────

    private suspend fun execute(q: TaskQueue): String {
        if (!TaskQueueRegistry.tryStart(this)) {
            return "Ek task-list pehle se chal rahi hai. 'cancel the task' bolo ya uske khatam hone ka wait karo."
        }
        queue = q
        try {
            store.save(q)
            TaskQueueNotifier.cancel(context)
            val total = q.items.size
            say("[AGENT] $total kaam hain. Ek-ek karke karungi aur bas short me Boss ko batana.")

            // Parallel lane: phone-setting tasks that never touch the screen (alarm, timer, volume ...) run at the
            // same time as the screen tasks. Anything that fails there goes back to the normal queue.
            val silentLane = SilentLaneRunner(context, executor, appSettings)
            val silentItems = if (total > 1)
                q.items.filter { it.status == QueueItemStatus.PENDING && silentLane.isSilent(it.goal) } else emptyList()
            silentItems.forEach { it.status = QueueItemStatus.RUNNING }
            if (silentItems.isNotEmpty()) store.save(q)
            val silentScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
            val silentJobs = silentItems.map { item ->
                silentScope.async {
                    val (ok, msg) = try { silentLane.run(item.goal) } catch (e: Exception) { false to (e.message ?: "failed") }
                    if (ok) {
                        item.status = QueueItemStatus.DONE
                        item.result = msg.take(240)
                        say("[AGENT] (saath me) ${item.goal.take(40)} ho gaya.")
                    } else {
                        Log.i(TAG, "silent lane could not do '${item.goal}' ($msg) -> normal queue")
                        item.status = QueueItemStatus.PENDING
                    }
                }
            }

            var aborted = false
            for (pass in 0..1) {
                if (cancelled || aborted) break
                if (pass == 1) {
                    silentJobs.awaitAll()
                    if (q.items.none { it.status == QueueItemStatus.PENDING }) break
                }
                var first = pass == 0
            for ((idx, item) in q.items.withIndex()) {
                if (cancelled) break
                if (item.status != QueueItemStatus.PENDING) continue

                if (!first) { goHome(); delay(1_000) }
                first = false

                item.status = QueueItemStatus.RUNNING
                store.save(q)
                say("[AGENT] Kaam ${idx + 1}/$total: ${item.goal}")

                val outcome = runItem(item)
                store.save(q)
                when (outcome) {
                    Outcome.ABORT -> { say("[AGENT] Aage nahi badh pa rahi: ${item.result}"); aborted = true; break }
                    Outcome.CANCELLED -> break
                    else -> say("[AGENT] Kaam ${idx + 1}/$total ${if (outcome == Outcome.DONE) "ho gaya" else "nahi ho paya"}.")
                }
            }
            }
            try { silentJobs.awaitAll() } catch (_: Exception) {}
            if (cancelled) q.items.filter { it.status == QueueItemStatus.PENDING }.forEach { it.status = QueueItemStatus.CANCELLED }
            return summary(q)
        } finally {
            try { store.save(q) } catch (_: Exception) {}
            TaskQueueRegistry.finish(this)
        }
    }

    private suspend fun runItem(item: QueueItem): Outcome {
        var goalText = item.goal
        // interrupted earlier (crash / phone locked)? tell the agent what was already done so it continues, not restarts
        val prior = item.progress
        item.progress = ""
        if (prior.isNotBlank()) {
            goalText += "\n(Note: ye kaam pehle beech me ruk gaya tha. Tab tak ye hua tha: ${prior.take(250)}. " +
                "Screen dekhkar wahin se aage badho, jo ho chuka use dobara mat karo.)"
        }
        val tracked: (String) -> Unit = { m ->
            item.progress = (item.progress + " | " + m.take(80)).takeLast(300)
            queue?.let { try { store.save(it) } catch (_: Exception) {} }
            say(m)
        }
        var attempt = 0
        while (attempt < 2) {
            attempt++
            item.attempts = attempt
            val engine = AgentTaskEngine(context, executor, appSettings)
            currentEngine = engine
            val msg = try { engine.run(goalText, tracked) } catch (e: Exception) { "The task crashed: ${e.message}" }
            currentEngine = null
            item.result = msg.take(240)

            when (engine.state) {
                TaskState.COMPLETED -> { item.status = QueueItemStatus.DONE; return Outcome.DONE }
                TaskState.CANCELLED -> { item.status = QueueItemStatus.CANCELLED; return Outcome.CANCELLED }
                else -> {
                    val lower = msg.lowercase()
                    if (ENV_BLOCKERS.any { lower.contains(it) }) {
                        // phone locked / accessibility off / no API key: stop here, keep this item for resume
                        item.status = QueueItemStatus.PENDING
                        return Outcome.ABORT
                    }
                    if (cancelled) { item.status = QueueItemStatus.CANCELLED; return Outcome.CANCELLED }
                    goalText = "${item.goal}\n(Note: pichli koshish me ye problem aayi: ${msg.take(160)}. Is baar alag tareeke se karo.)"
                    if (attempt < 2) { goHome(); delay(800) }
                }
            }
        }
        item.status = QueueItemStatus.FAILED
        return Outcome.FAILED
    }

    private suspend fun goHome() {
        try { executor.executeSuspend("press_home", JSONObject()) } catch (_: Exception) {}
    }

    private fun summary(q: TaskQueue): String {
        val total = q.items.size
        val done = q.items.filter { it.status == QueueItemStatus.DONE }
        val failed = q.items.filter { it.status == QueueItemStatus.FAILED }
        val left = q.items.filter { it.status == QueueItemStatus.PENDING }
        val cancelledItems = q.items.filter { it.status == QueueItemStatus.CANCELLED }
        return buildString {
            append("${done.size}/$total kaam ho gaye.")
            if (done.isNotEmpty()) append(" Ho gaya: ").append(done.joinToString("; ") { it.goal.take(60) }).append('.')
            if (failed.isNotEmpty()) append(" Nahi ho paya: ").append(failed.joinToString("; ") { "${it.goal.take(60)} (${it.result.take(80)})" }).append('.')
            if (cancelledItems.isNotEmpty()) append(" Cancel hue: ").append(cancelledItems.joinToString("; ") { it.goal.take(60) }).append('.')
            if (left.isNotEmpty()) append(" Baaki reh gaye: ").append(left.joinToString("; ") { it.goal.take(60) })
                .append(". Phone unlock karke 'baaki kaam karo' bolo to main aage badhaungi.")
        }
    }

    // ───────────────────────── splitting ─────────────────────────

    private fun splitGoal(goal: String): List<String> {
        if (!TaskSplitter.mightBeMultiTask(goal)) return listOf(goal)
        val heuristic = TaskSplitter.heuristicSplit(goal)
        val fromModel = try { askModelToSplit(goal) } catch (e: Exception) {
            Log.w(TAG, "model split failed: ${e.message}")
            null
        }
        return when {
            fromModel != null && fromModel.isNotEmpty() -> fromModel
            heuristic.size > 1 -> heuristic
            else -> listOf(goal)
        }
    }

    private fun askModelToSplit(goal: String): List<String>? {
        val key = appSettings.effectiveApiKey()
        if (key.isBlank()) return null
        val prompt = "Split this phone request into independent sub-tasks that a phone agent does ONE AFTER ANOTHER.\n" +
            "REQUEST: \"$goal\"\n" +
            "Rules:\n" +
            "- If it is really ONE task, return an array with that single task.\n" +
            "- Keep the original order and the original language (Hinglish stays Hinglish).\n" +
            "- Make every sub-task self-contained: replace words like 'usko', 'wahi', 'it', 'there' with the real thing.\n" +
            "- Do NOT split one action that has several details (e.g. 'search salt and pepper', 'Rahul aur Aman ko same msg' is TWO sends only if they must be sent separately).\n" +
            "- Do not invent tasks. At most ${TaskSplitter.MAX_TASKS}.\n" +
            "Return ONLY a JSON array of strings."
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            put("generationConfig", JSONObject().put("temperature", 0.1).put("responseMimeType", "application/json"))
        }.toString()
        val req = Request.Builder().url("$URL?key=$key").post(body.toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val raw = resp.body?.string() ?: return null
            val text = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: return null
            return TaskSplitter.parseModelList(text)
        }
    }
}
