package com.soltini.app.agent

import org.json.JSONObject

/** Where the Live voice model reaches the running task: status, pause, resume, cancel, reply. */
object AgentTaskRegistry {

    @Volatile private var current: AgentTaskEngine? = null
    @Volatile private var lastSnapshot: TaskSnapshot? = null

    @Synchronized
    fun tryStart(engine: AgentTaskEngine): Boolean {
        if (current?.isActive == true) return false
        current = engine
        return true
    }

    @Synchronized
    fun finish(engine: AgentTaskEngine) {
        lastSnapshot = engine.snapshot()
        if (current === engine) current = null
    }

    /** True while an engine run or a task list is executing (used to keep new work / screen peeks from interrupting it). */
    fun isBusy(): Boolean = current?.isActive == true || TaskQueueRegistry.isActive()

    /** Snapshot of the running task, else of the last finished one, else null. Used by the floating HUD. */
    fun currentOrLast(): TaskSnapshot? = current?.snapshot() ?: lastSnapshot

    private fun none(): JSONObject = JSONObject().apply {
        put("status", "no_task")
        put("message", "No automation task is running right now.")
    }

    fun status(): JSONObject {
        val e = current
        val q = TaskQueueRegistry.status()
        if (e != null) return JSONObject().apply {
            put("status", "running"); put("task", e.snapshot().toJson())
            if (q != null) put("task_list", q)
        }
        // between two sub-tasks of a task list there is briefly no engine
        if (q != null) return JSONObject().apply { put("status", "running"); put("task_list", q) }
        val last = lastSnapshot ?: return none()
        return JSONObject().apply { put("status", "finished"); put("task", last.toJson()) }
    }

    fun cancel(): JSONObject {
        val queueCancelled = TaskQueueRegistry.cancel()   // stops the remaining sub-tasks too
        val e = current
        if (e == null && !queueCancelled) return none()
        e?.cancel()
        return JSONObject().apply { put("status", "cancelling"); put("message", "The task is being stopped.") }
    }

    fun pause(): JSONObject {
        val e = current ?: return none()
        e.pause()
        return JSONObject().apply { put("status", "paused") }
    }

    fun resume(): JSONObject {
        val e = current ?: return none()
        e.resume()
        return JSONObject().apply { put("status", "resumed") }
    }

    fun reply(answer: String): JSONObject {
        val e = current ?: return none()
        return if (e.reply(answer)) JSONObject().apply { put("status", "delivered"); put("message", "The task continues with the user's answer.") }
        else JSONObject().apply { put("status", "not_waiting"); put("message", "The task is not waiting for an answer.") }
    }
}

/**
 * One-way channel from background engines to the live voice model
 * (BackgroundVoiceService installs the listener while Gemini Live is connected).
 */
object AgentStatusBus {
    @Volatile var listener: ((String) -> Unit)? = null
    fun emit(message: String) {
        try { listener?.invoke(message) } catch (_: Exception) {}
    }
}
