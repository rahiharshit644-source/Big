package com.soltini.app.agent

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.soltini.app.settings.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * AgentTaskEngine - closed-loop phone agent.
 *
 *   plan -> LOOK at the screen -> ONE action -> wait for the UI to settle -> VERIFY it changed
 *        -> (stuck? recover) -> ... -> finish -> independent final check
 *
 * Why it is more reliable than a plain "call tools in a loop":
 *  - it looks at the real screen before every step (element list, plus a screenshot when needed)
 *  - it proves each action had an effect (screen fingerprint before/after) and tells the model when it did not
 *  - stuck / loop detection with a recovery ladder (dismiss pop-ups, vision, back, re-plan, give up cleanly)
 *  - bulk tasks ("sab", "all") only finish after the end of the list is proven
 *  - an independent final check before it claims success
 *  - risky steps (payments, deletes, passwords/PINs) pause and ask the user
 *  - status / pause / resume / cancel / reply are reachable by voice (AgentTaskRegistry)
 */
class AgentTaskEngine(
    private val context: Context,
    private val executor: AgentToolExecutor,
    private val appSettings: AppSettings
) {

    companion object {
        private const val TAG = "AgentTaskEngine"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val MODELS = listOf("gemini-2.5-flash", "gemini-2.0-flash")
        private const val MAX_WALL_MS = 8 * 60_000L
        private const val MAX_WALL_BULK_MS = 15 * 60_000L
        private const val USER_WAIT_MS = 3 * 60_000L
        private const val PAUSE_WAIT_MS = 10 * 60_000L
        private const val MAX_HISTORY = 21           // goal turn + 10 model/user pairs

        private val BULK_REGEX = Regex(
            "(?<![\\p{L}\\p{N}])(all|every|each|everyone|sab|sabhi|saare|saari|sare|sari)(?![\\p{L}\\p{N}])|unlike all|delete all|clear all|meri liked|until none",
            RegexOption.IGNORE_CASE
        )

        private val BENIGN_DISMISS = listOf(
            "Not now", "No thanks", "No, thanks", "Skip", "Maybe later", "Later", "Remind me later",
            "Got it", "Dismiss", "Close", "Not Now", "SKIP", "NO THANKS", "Cancel"
        )

        private val YES_WORDS = listOf("yes", "yeah", "yep", "haan", "han", "ha ", "kar do", "karo", "ok", "okay", "sure", "go ahead", "proceed", "theek", "thik", "confirm", "done", "kar de", "chalo")
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val taskId: String = UUID.randomUUID().toString().take(8)

    @Volatile var state: TaskState = TaskState.PLANNING
        private set
    val isActive: Boolean
        get() = state == TaskState.PLANNING || state == TaskState.RUNNING ||
            state == TaskState.WAITING_USER || state == TaskState.PAUSED

    @Volatile private var cancelled = false
    @Volatile private var paused = false
    @Volatile private var pendingQuestion: String? = null
    @Volatile private var waitingAnswer: CompletableDeferred<String>? = null

    private var goal = ""
    private var plan = TaskPlan("", "single", emptyList(), "")
    private var step = 0
    private var maxSteps = 30
    private var lastAction = ""
    private var lastResult = ""
    private val notes = LinkedHashMap<String, String>()
    private val processed = LinkedHashSet<String>()
    private val approved = HashSet<String>()
    private val actionLog = ArrayList<String>()
    private var recoveries = 0
    private var queuedResponses: JSONArray? = null

    // learned workflows (macros) + checkpoint/resume
    private val recorded = ArrayList<MacroStep>()
    private var macroUnsafe = false
    private var resumeMode = false
    private var replayedSteps = 0
    private var replayHint = ""
    private var say: (String) -> Unit = { AgentStatusBus.emit(it) }

    // ───────────────────────── control from outside ─────────────────────────

    fun cancel() {
        cancelled = true
        waitingAnswer?.complete("CANCELLED")
    }

    fun pause() { paused = true }
    fun resume() { paused = false }

    /** Delivers the user's answer to a pending ask_user / confirmation. */
    fun reply(answer: String): Boolean = waitingAnswer?.complete(answer) ?: false

    fun snapshot(): TaskSnapshot = TaskSnapshot(
        taskId, goal, state, step, maxSteps, lastAction, lastResult.take(160),
        plan.steps, LinkedHashMap(notes), processed.size, recoveries, pendingQuestion
    )

    // ───────────────────────── main entry ─────────────────────────

    suspend fun run(userGoal: String, onStatus: ((String) -> Unit)? = null): String = withContext(Dispatchers.IO) {
        if (onStatus != null) say = onStatus
        goal = userGoal.trim()
        if (goal.isBlank()) return@withContext "I did not get a task to do."

        val apiKey = appSettings.effectiveApiKey()
        if (apiKey.isBlank()) return@withContext "Gemini API key is not set in Settings, so I cannot run phone tasks."
        if (!ScreenObserver.isAvailable()) {
            return@withContext "Accessibility Service is off. Please enable Myra in Android Accessibility settings so I can control the screen."
        }
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km?.isKeyguardLocked == true) return@withContext "The phone is locked. Please unlock it first and ask me again."
        if (!AgentTaskRegistry.tryStart(this@AgentTaskEngine)) {
            return@withContext "Another phone task is still running. Say 'cancel the task' first or wait for it to finish."
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Soltini::AgentTask").apply {
            setReferenceCounted(false)
            acquire(MAX_WALL_BULK_MS + 60_000L)
        }

        try {
            state = TaskState.PLANNING
            plan = makePlan(apiKey)
            val bulk = plan.scope == "all"
            maxSteps = if (bulk) 60 else 30
            val deadline = System.currentTimeMillis() + if (bulk) MAX_WALL_BULK_MS else MAX_WALL_MS
            Log.i(TAG, "[$taskId] scope=${plan.scope} plan=${plan.steps}")
            say("[AGENT] Task started: ${plan.goal}. Do not narrate every step; tell Boss only briefly.")

            state = TaskState.RUNNING
            val contents = JSONArray()
            val system = systemPrompt()

            var lastSig = ScreenObserver.signature()
            var noProgress = 0
            var repeatKey = ""
            var repeatCount = 0
            var endOfList = false
            var uiActions = 0
            var finishRejections = 0
            var screenChanged = true
            var hint = ""
            var shotQuestion: String? = null       // non-null = attach a screenshot (text = the question, may be empty)
            var firstTurn = true

            // learned workflow: same request done before? replay it without the LLM (falls back safely)
            if (!resumeMode && plan.scope == "single") {
                val macro = MacroStore.find(context, goal)
                if (macro != null) {
                    val done = tryReplayMacro(apiKey, macro)
                    if (done != null) return@withContext end(TaskState.COMPLETED, done)
                    if (cancelled) return@withContext end(TaskState.CANCELLED, "Task cancelled.")
                    uiActions += replayedSteps
                    macroUnsafe = true            // do not overwrite the saved routine from a mixed run
                    lastSig = ScreenObserver.signature()
                    if (replayHint.isNotEmpty()) hint = replayHint
                }
            }

            while (step < maxSteps) {
                if (cancelled) return@withContext end(TaskState.CANCELLED, "Task cancelled.")
                if (System.currentTimeMillis() > deadline) {
                    return@withContext end(TaskState.FAILED, "I ran out of time on this task. ${progressText()}")
                }
                if (!waitWhilePaused()) return@withContext end(TaskState.CANCELLED, "The task stayed paused too long, so I stopped it.")
                step++

                // ── recovery ladder ──
                if (noProgress >= 6) return@withContext end(TaskState.FAILED, "I got stuck and could not make progress. ${progressText()}")
                if (noProgress >= 2 || repeatCount >= 3) {
                    recoveries++
                    hint = when {
                        noProgress >= 4 -> {
                            callTool("press_back", JSONObject())
                            "RECOVERY: several actions had no effect, so I pressed Back once. Re-read the screen and choose a DIFFERENT approach."
                        }
                        noProgress == 3 -> {
                            if (shotQuestion == null) shotQuestion = "Why are my actions not working?"
                            "RECOVERY: still no progress. A screenshot is attached - use it, or call tap_by_vision(description) to tap what you can see."
                        }
                        else -> if (dismissPopup()) "RECOVERY: I closed a pop-up that may have been blocking the screen."
                                else "RECOVERY: your last actions had no effect. Try a different target, scroll, or look_at_screen."
                    }
                    if (repeatCount >= 3) hint += " You repeated the same action 3 times - do not repeat it again."
                    repeatCount = 0
                    delay(300)
                }

                // ── look ──
                val digest = ScreenObserver.digest()
                val shot = if (shotQuestion != null || digest.interactiveCount < 4) ScreenObserver.screenshotBase64() else null
                val obs = buildString {
                    append("STEP ").append(step).append('/').append(maxSteps)
                    if (recoveries > 0) append(" | recoveries: ").append(recoveries)
                    append('\n')
                    if (hint.isNotEmpty()) append(hint).append('\n')
                    if (!firstTurn) append(if (screenChanged) "Screen CHANGED after your last action.\n" else "Screen did NOT change after your last action.\n")
                    if (!shotQuestion.isNullOrBlank()) append("You asked about the picture: ").append(shotQuestion).append('\n')
                    append("CURRENT SCREEN:\n").append(digest.text)
                    if (shot != null) append("(A screenshot of this screen is attached.)\n")
                    if (notes.isNotEmpty()) append("NOTES: ").append(notes.entries.joinToString("; ") { "${it.key}=${it.value}" }).append('\n')
                    if (plan.scope == "all") append("BULK TASK: items processed so far: ").append(processed.size).append(". End of list proven: ").append(endOfList).append(".\n")
                    if (actionLog.isNotEmpty()) append("RECENT ACTIONS: ").append(actionLog.takeLast(8).joinToString(" -> ")).append('\n')
                    append("Choose exactly ONE next tool call.")
                }
                hint = ""
                shotQuestion = null

                val userParts = JSONArray()
                if (firstTurn) {
                    userParts.put(JSONObject().put("text",
                        "GOAL: ${plan.goal}\nSCOPE: ${plan.scope}\nPLAN: " +
                            plan.steps.mapIndexed { i, s -> "${i + 1}) $s" }.joinToString(" ") +
                            "\nDONE WHEN: ${plan.successCriteria}\n\n$obs"))
                    firstTurn = false
                } else {
                    queuedResponses?.let { q -> for (i in 0 until q.length()) userParts.put(q.get(i)) }
                    queuedResponses = null
                    userParts.put(JSONObject().put("text", obs))
                }
                if (shot != null) {
                    userParts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", shot)))
                }
                contents.put(JSONObject().put("role", "user").put("parts", userParts))

                // ── think ──
                val response = callModel(apiKey, contents, system, withTools = true)
                    ?: return@withContext end(TaskState.FAILED, "I could not reach the AI service. Please check the internet and try again.")
                val modelContent = response.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                val parts = modelContent?.optJSONArray("parts")
                val calls = ArrayList<JSONObject>()
                if (parts != null) {
                    for (i in 0 until parts.length()) parts.optJSONObject(i)?.optJSONObject("functionCall")?.let { calls.add(it) }
                }
                if (modelContent == null || parts == null || calls.isEmpty()) {
                    hint = "You must answer with a tool call (use finish_task when done)."
                    noProgress++
                    trimHistory(contents)
                    continue
                }
                contents.put(JSONObject().put("role", "model").put("parts", parts))

                val first = calls.first()
                val name = first.optString("name", "")
                val args = first.optJSONObject("args") ?: JSONObject()
                lastAction = "$name ${compactArgs(args)}".trim()
                actionLog.add(lastAction.take(70))
                Log.i(TAG, "[$taskId] step $step: $lastAction")

                // ── act ──
                val payload = JSONObject()
                var isUi = false
                var executed: Pair<String, JSONObject>? = null
                var executedOk = false

                when (name) {
                    "finish_task" -> {
                        val success = args.optBoolean("success", true)
                        val summary = args.optString("summary", "Done.")
                        if (!success) return@withContext end(TaskState.FAILED, summary)

                        if (plan.scope == "all" && !endOfList && finishRejections < 2) {
                            finishRejections++
                            payload.put("accepted", false).put("reason",
                                "This is a bulk task and the end of the list is not proven yet. Keep going until scrolling shows no new items, then finish.")
                        } else {
                            val check = if (uiActions > 0 && finishRejections < 3) verifyCompletion(apiKey, summary) else null
                            if (check != null && !check.first) {
                                finishRejections++
                                payload.put("accepted", false).put("reason", "Final check says the goal is NOT visibly done: ${check.second}. Fix it, then finish again.")
                            } else {
                                saveMacroIfEligible()
                                return@withContext end(TaskState.COMPLETED, summary)
                            }
                        }
                    }

                    "ask_user" -> {
                        macroUnsafe = true
                        val answer = askUser(args.optString("question", "Boss, I need your help to continue."))
                        if (answer == "CANCELLED") return@withContext end(TaskState.CANCELLED, "Task cancelled.")
                        payload.put("user_answer", answer)
                    }

                    "note" -> {
                        notes[args.optString("key", "note${notes.size + 1}")] = args.optString("value", "").take(200)
                        payload.put("saved", true)
                    }

                    "record_item" -> {
                        val item = args.optString("item", "").trim().lowercase()
                        val already = item.isNotEmpty() && !processed.add(item)
                        payload.put("already_processed", already).put("total_processed", processed.size)
                    }

                    "wait_seconds" -> {
                        delay(args.optInt("seconds", 2).coerceIn(1, 10) * 1000L)
                        payload.put("waited", true)
                    }

                    "wait_for_text" -> {
                        val text = args.optString("text", "")
                        val until = System.currentTimeMillis() + args.optInt("timeout_seconds", 10).coerceIn(1, 30) * 1000L
                        var found = ScreenObserver.containsText(text)
                        while (!found && System.currentTimeMillis() < until) { delay(500); found = ScreenObserver.containsText(text) }
                        payload.put("found", found)
                    }

                    "find_on_screen" -> {
                        val r = findOnScreen(args.optString("text", ""), args.optInt("max_scrolls", 8).coerceIn(1, 20))
                        payload.put("found", r.first).put("end_of_content", r.second)
                        if (r.second && !r.first) endOfList = true
                        isUi = true
                    }

                    "look_at_screen" -> {
                        shotQuestion = args.optString("question", "")
                        payload.put("screenshot", "attached on the next step")
                    }

                    "dismiss_popup" -> {
                        payload.put("closed", dismissPopup())
                        isUi = true
                    }

                    else -> {
                        val verdict = RiskGate.check(name, args, appSettings.isAgentRiskConfirmEnabled)
                        var skip = false
                        if (verdict.kind != RiskGate.Kind.NONE) macroUnsafe = true
                        if (verdict.kind != RiskGate.Kind.NONE && verdict.key !in approved) {
                            val answer = askUser(verdict.question)
                            if (answer == "CANCELLED") return@withContext end(TaskState.CANCELLED, "Task cancelled.")
                            if (verdict.kind == RiskGate.Kind.HANDOVER) {
                                payload.put("handed_over", true).put("user_said", answer)
                                skip = true
                            } else if (isYes(answer)) {
                                approved.add(verdict.key)
                            } else {
                                payload.put("blocked", "The user declined ($answer). Do not do this action. Pick another way or finish_task.")
                                skip = true
                            }
                        }
                        if (!skip) {
                            val result = callTool(name, args)
                            executed = name to JSONObject(args.toString())
                            executedOk = !result.has("error") && result.optString("status") != "error"
                            payload.put("result", trimResult(result))
                            isUi = name in AgentToolCatalog.UI_TOOLS
                            if (name in AgentToolCatalog.TYPE_TOOLS) {
                                val typed = args.optString("text", "").take(24)
                                if (typed.isNotBlank() && !isSecretLike(args)) {
                                    payload.put("typed_text_visible_on_screen", ScreenObserver.containsText(typed))
                                }
                            }
                            if (isUi || name in AgentToolCatalog.TYPE_TOOLS) uiActions++
                        }
                    }
                }

                // ── verify ──
                val sig = ScreenObserver.waitForSettle()
                screenChanged = !sig.sameAs(lastSig)
                val note = StringBuilder()
                if (isUi) {
                    if (screenChanged) noProgress = 0 else {
                        noProgress++; note.append("NO_VISIBLE_EFFECT ")
                        if (name !in AgentToolCatalog.SCROLL_TOOLS) AgentFailureLog.add(context, goal, name, "No visible change on screen after ${compactArgs(args)}")
                    }
                    if (name in AgentToolCatalog.SCROLL_TOOLS && !screenChanged) { endOfList = true; note.append("END_OF_CONTENT ") }
                } else if (name !in AgentToolCatalog.CONTROL_TOOLS && screenChanged) {
                    noProgress = 0
                }
                val key = name + args.toString()
                if (key == repeatKey && !screenChanged) repeatCount++ else { repeatKey = key; repeatCount = if (isUi && !screenChanged) 1 else 0 }
                lastSig = sig

                executed?.let { (n, a) ->
                    when {
                        isSecretLike(a) -> macroUnsafe = true
                        n in AgentToolCatalog.CONTROL_TOOLS -> {}
                        n !in MacroStore.REPLAY_SAFE_TOOLS -> macroUnsafe = true
                        executedOk && screenChanged -> recorded.add(MacroStep(n, a))
                        else -> {}
                    }
                    Unit
                }

                payload.put("screen_changed", screenChanged)
                if (note.isNotEmpty()) payload.put("verification", note.toString().trim())
                lastResult = payload.toString()
                saveCheckpoint()

                val responses = JSONArray()
                responses.put(functionResponse(name, payload))
                for (extra in calls.drop(1)) {
                    responses.put(functionResponse(extra.optString("name", ""), JSONObject().put("skipped", "Only one action per step.")))
                }
                queuedResponses = responses
                trimHistory(contents)
            }

            end(TaskState.FAILED, "I used all $maxSteps steps without finishing. ${progressText()}")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // service killed / coroutine cancelled: keep the checkpoint so the task can be resumed
            state = TaskState.FAILED
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[$taskId] engine crashed: ${e.message}", e)
            end(TaskState.FAILED, "The task failed: ${e.message}")
        } finally {
            try { if (wake.isHeld) wake.release() } catch (_: Exception) {}
            AgentTaskRegistry.finish(this@AgentTaskEngine)
        }
    }

    // ───────────────────────── resume / macros ─────────────────────────

    /** Loads a saved checkpoint so [run] continues an interrupted task instead of starting from zero. */
    fun restore(cp: TaskCheckpoint) {
        resumeMode = true
        notes.clear(); notes.putAll(cp.notes)
        processed.clear(); processed.addAll(cp.processed)
        actionLog.clear(); actionLog.addAll(cp.actionLog)
    }

    private fun saveCheckpoint() {
        try {
            TaskCheckpointStore.save(
                context,
                TaskCheckpoint(
                    taskId, goal, plan.scope, plan.steps, plan.successCriteria, step,
                    LinkedHashMap(notes), processed.toList(), actionLog.toList(), System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}
    }

    /** Saves the screen-changing steps of a clean, successful run so the same request can be replayed later. */
    private fun saveMacroIfEligible() {
        try {
            if (macroUnsafe || resumeMode || plan.scope != "single") return
            val steps = recorded.toList()
            if (steps.size !in 2..25) return
            if (steps.first().tool != "open_app") return   // must be self-contained from any starting screen
            MacroStore.save(context, goal, steps)
            Log.i(TAG, "[$taskId] saved macro with ${steps.size} steps")
        } catch (_: Exception) {}
    }

    /**
     * Replays a saved routine step by step. Every step must visibly change the screen (one retry for slow apps).
     * Returns the final summary when the routine finished the task, or null when the normal loop must take over
     * from the current screen ([replayHint] then explains where the routine stopped).
     */
    private suspend fun tryReplayMacro(apiKey: String, macro: Macro): String? {
        Log.i(TAG, "[$taskId] replaying macro (${macro.steps.size} steps) for \"${macro.goal}\"")
        replayedSteps = 0
        for ((i, st) in macro.steps.withIndex()) {
            if (cancelled) return null
            if (!waitWhilePaused()) return null
            if (RiskGate.check(st.tool, st.args, appSettings.isAgentRiskConfirmEnabled).kind != RiskGate.Kind.NONE) {
                replayHint = "A saved routine stopped before a risky step (${st.tool}). Continue carefully from the current screen."
                return null
            }
            var ok = false
            for (attempt in 0..1) {
                val before = ScreenObserver.signature()
                val res = callTool(st.tool, JSONObject(st.args.toString()))
                val noError = !res.has("error") && res.optString("status") != "error"
                val sig = ScreenObserver.waitForSettle()
                if (noError && !sig.sameAs(before)) { ok = true; break }
                if (attempt == 0) delay(1500)
            }
            actionLog.add("macro:${st.tool}".take(70))
            if (!ok) {
                MacroStore.recordFailure(context, macro.key)
                replayHint = "A saved routine for this task stopped at step ${i + 1}/${macro.steps.size} (${st.tool}) because the screen did not respond as before. " +
                    "Look at the CURRENT screen and continue with your own judgment; do not blindly redo earlier steps."
                return null
            }
            replayedSteps++
        }
        val check = verifyCompletion(apiKey, "Replayed a saved routine for exactly this task")
        if (check != null && !check.first) {
            MacroStore.recordFailure(context, macro.key)
            replayHint = "A saved routine ran but the result does not look right (${check.second}). Check the CURRENT screen and fix it."
            return null
        }
        MacroStore.recordSuccess(context, macro.key)
        return "Ho gaya Boss: ${plan.goal}"
    }

    // ───────────────────────── helpers ─────────────────────────

    private fun end(s: TaskState, message: String): String {
        state = s
        pendingQuestion = null
        TaskCheckpointStore.clear(context)   // ended on purpose -> nothing to resume
        if (s == TaskState.FAILED) AgentFailureLog.add(context, goal, "task", message)
        Log.i(TAG, "[$taskId] ended: $s - $message")
        return message
    }

    private fun progressText(): String =
        if (actionLog.isEmpty()) "Nothing was done yet." else "Last actions: " + actionLog.takeLast(4).joinToString(" -> ")

    private fun functionResponse(name: String, payload: JSONObject): JSONObject =
        JSONObject().put("functionResponse", JSONObject().put("name", name).put("response", JSONObject().put("output", payload)))

    private suspend fun callTool(name: String, args: JSONObject): JSONObject {
        val r = try {
            executor.executeSuspend(name, args)
        } catch (e: Exception) {
            Log.w(TAG, "tool $name failed: ${e.message}")
            JSONObject().put("error", e.message ?: "tool failed")
        }
        if (r.has("error") || r.optString("status") == "error") {
            AgentFailureLog.add(context, goal, name, r.optString("error", "tool reported an error"))
        }
        return r
    }

    private fun trimResult(r: JSONObject): JSONObject {
        val s = r.toString()
        return if (s.length <= 900) r else JSONObject().put("truncated", s.take(900))
    }

    private fun compactArgs(a: JSONObject): String {
        val s = a.toString()
        return if (s.length <= 60) s else s.take(57) + "..."
    }

    private fun isYes(answer: String): Boolean {
        val a = " " + answer.lowercase().trim() + " "
        if (a.contains(" no ") || a.contains("nahi") || a.contains("mat ") || a.contains("cancel") || a.contains("stop") || a.contains("don't") || a.contains("dont")) return false
        return YES_WORDS.any { a.contains(it) }
    }

    private fun isSecretLike(args: JSONObject): Boolean {
        val s = args.toString().lowercase()
        return s.contains("password") || s.contains("pin") || s.contains("cvv") || s.contains("otp")
    }

    private suspend fun waitWhilePaused(): Boolean {
        if (!paused) return true
        state = TaskState.PAUSED
        val end = System.currentTimeMillis() + PAUSE_WAIT_MS
        while (paused && !cancelled && System.currentTimeMillis() < end) delay(300)
        if (cancelled || paused) return false
        state = TaskState.RUNNING
        return true
    }

    private suspend fun askUser(question: String): String {
        val deferred = CompletableDeferred<String>()
        waitingAnswer = deferred
        pendingQuestion = question
        state = TaskState.WAITING_USER
        say("[AGENT NEEDS INPUT] Ask Boss this out loud right now, wait for his answer, then call task_reply with exactly what he said: $question")
        val answer = withTimeoutOrNull(USER_WAIT_MS) { deferred.await() } ?: "TIMEOUT: the user did not answer, assume NO"
        waitingAnswer = null
        pendingQuestion = null
        if (state == TaskState.WAITING_USER) state = TaskState.RUNNING
        return answer
    }

    private fun dismissPopup(): Boolean {
        val svc = SoltiniAccessibilityService.getInstance() ?: return false
        return svc.clickExactText(BENIGN_DISMISS)
    }

    /** Scrolls down (then back up) until [text] is visible. Returns (found, endOfContentReached). */
    private suspend fun findOnScreen(text: String, maxScrolls: Int): Pair<Boolean, Boolean> {
        if (text.isBlank()) return false to false
        if (ScreenObserver.containsText(text)) return true to false
        var last = ScreenObserver.signature()
        var end = false
        for (i in 0 until maxScrolls) {
            callTool("scroll_down", JSONObject())
            val sig = ScreenObserver.waitForSettle(1500)
            if (ScreenObserver.containsText(text)) return true to false
            if (sig.sameAs(last)) { end = true; break }
            last = sig
        }
        // not below - look above
        for (i in 0 until maxScrolls / 2 + 1) {
            callTool("scroll_up", JSONObject())
            val sig = ScreenObserver.waitForSettle(1500)
            if (ScreenObserver.containsText(text)) return true to false
            if (sig.sameAs(last)) break
            last = sig
        }
        return false to end
    }

    private fun trimHistory(contents: JSONArray) {
        while (contents.length() > MAX_HISTORY) {
            // keep contents[0] (goal); drop the oldest model/user pair so call/response pairing stays valid
            if (contents.length() < 4) return
            contents.remove(1)
            contents.remove(1)
        }
    }

    // ───────────────────────── model calls ─────────────────────────

    private fun systemPrompt(): String = """You are Myra's phone agent. You operate this Android phone like a careful human, one verified action at a time.

HOW YOU WORK
- Every step you receive the CURRENT SCREEN (numbered elements with @(x,y) positions, plus visible text, plus a screenshot when needed) and whether your last action changed the screen.
- Reply with exactly ONE tool call per step. Never describe, never chat: only tool calls.
- Prefer a dedicated tool when one exists (open_app, whatsapp_send_message, send_sms, device_files, spotify_play, set_alarm, maps_navigate, open_settings ...). Use screen tapping/typing only for what no dedicated tool covers.
- To tap, use smart_click with the visible label. If that fails, use tap_screen with the @(x,y) from the list. If the screen has no usable elements (game, Flutter, canvas, image buttons), use tap_by_vision(description) - it looks at the screenshot and taps what you describe.
- To type, use smart_type with the field label (set press_enter=true to submit a search). Check typed_text_visible_on_screen in the result; if false, click the field first and try again.
- If you see NO_VISIBLE_EFFECT or the screen did not change, do NOT repeat the same action: pick a different element, scroll, dismiss_popup, or look_at_screen.
- Content below the fold: use find_on_screen(text). END_OF_CONTENT means there is nothing more in that direction.
- Wait for slow pages with wait_for_text or wait_seconds. Close annoying pop-ups, ads and tips with dismiss_popup.
- Missing information (which contact, which size, an address, a choice) -> ask_user. Never guess important details.
- Money, orders, bookings, deletions, uninstalling -> ask_user for a clear yes BEFORE the final tap. Never enter passwords, PINs, CVV or card numbers: ask_user to let the user do it.
- One-time codes: read_otp (if it says the reader is off, ask_user instead), then smart_type the code.
- Use note(key,value) to remember facts across apps (prices, names, codes).
- BULK tasks (scope=all): process every matching item. Call record_item(item) for each one so none is handled twice, scroll to reach more, and only finish after scrolling shows END_OF_CONTENT with nothing left.
- When the goal is truly done AND visible on screen, call finish_task(success=true, summary=...). If it is impossible or blocked, call finish_task(success=false, summary=why).
- summary: one short friendly sentence in Hinglish, addressed to Boss.

You do not have a keyboard or mouse; only the tools. You never perform actions the user did not ask for."""

    private fun callModel(apiKey: String, contents: JSONArray, system: String, withTools: Boolean, json: Boolean = false): JSONObject? {
        val body = JSONObject().apply {
            put("contents", contents)
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            if (withTools) {
                put("tools", JSONArray().put(JSONObject().put("functionDeclarations", AgentToolCatalog.build())))
                put("toolConfig", JSONObject().put("functionCallingConfig", JSONObject().put("mode", "ANY")))
            }
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                if (json) put("responseMimeType", "application/json")
            })
        }
        for (model in MODELS) {
            for (attempt in 0..1) {
                try {
                    val req = Request.Builder()
                        .url("$BASE_URL/$model:generateContent?key=$apiKey")
                        .post(body.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    var retry = false
                    http.newCall(req).execute().use { resp ->
                        val text = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) return JSONObject(text)
                        Log.w(TAG, "model $model HTTP ${resp.code}: ${text.take(200)}")
                        retry = resp.code == 429 || resp.code >= 500
                    }
                    if (!retry) break
                    Thread.sleep(1500)
                } catch (e: Exception) {
                    Log.w(TAG, "model $model failed: ${e.message}")
                    break
                }
            }
        }
        return null
    }

    private fun textOf(resp: JSONObject?): String {
        val parts = resp?.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) parts.optJSONObject(i)?.optString("text")?.let { sb.append(it) }
        return sb.toString()
    }

    private fun makePlan(apiKey: String): TaskPlan {
        val heuristicScope = if (BULK_REGEX.containsMatchIn(goal)) "all" else "single"
        val fallback = TaskPlan(goal, heuristicScope, listOf(goal), "The goal is visibly achieved on screen.")
        return try {
            val prompt = "You plan tasks for an Android phone agent. The user said: \"$goal\"\n" +
                "Return ONLY JSON: {\"goal\": restated clearly in English, \"scope\": \"single\" | \"all\" | \"until_found\", " +
                "\"steps\": [at most 8 short steps], \"success_criteria\": what must be visible/true when finished}.\n" +
                "scope=all only if the user wants EVERY matching item handled (sab, all, every, saare)."
            val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))
            val resp = callModel(apiKey, contents, "You output strict JSON only.", withTools = false, json = true) ?: return fallback
            val j = JSONObject(textOf(resp).trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            val steps = j.optJSONArray("steps")?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
            var scope = j.optString("scope", heuristicScope).lowercase()
            if (scope != "single" && scope != "all" && scope != "until_found") scope = heuristicScope
            if (heuristicScope == "all") scope = "all"
            TaskPlan(j.optString("goal", goal).ifBlank { goal }, scope, steps.ifEmpty { listOf(goal) }, j.optString("success_criteria", fallback.successCriteria).ifBlank { fallback.successCriteria })
        } catch (e: Exception) {
            Log.w(TAG, "planning failed: ${e.message}")
            fallback
        }
    }

    /** Independent look at the final screen. Returns (done, reason) or null if it could not be checked. */
    private suspend fun verifyCompletion(apiKey: String, claimed: String): Pair<Boolean, String>? {
        return try {
            ScreenObserver.waitForSettle(1500)
            val digest = ScreenObserver.digest()
            val shot = ScreenObserver.screenshotBase64()
            val prompt = "A phone agent claims it finished this task.\nTASK: ${plan.goal}\nDONE WHEN: ${plan.successCriteria}\nAGENT SAYS: $claimed\n\n" +
                "CURRENT SCREEN:\n${digest.text}\n\n" +
                "Is there any clear evidence on screen that the task is NOT done or went wrong? If the claim is plausible and nothing contradicts it, answer done=true.\n" +
                "Return ONLY JSON: {\"done\": true|false, \"reason\": \"short\"}"
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            if (shot != null) parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", shot)))
            val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
            val resp = callModel(apiKey, contents, "You are a strict but fair QA checker. Output strict JSON only.", withTools = false, json = true) ?: return null
            val j = JSONObject(textOf(resp).trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            j.optBoolean("done", true) to j.optString("reason", "")
        } catch (e: Exception) {
            Log.w(TAG, "final check failed: ${e.message}")
            null
        }
    }
}
