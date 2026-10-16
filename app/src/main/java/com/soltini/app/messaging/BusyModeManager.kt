package com.soltini.app.messaging

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.soltini.app.MainActivity
import com.soltini.app.notifications.ExtractedReplyAction
import com.soltini.app.security.VoiceProfileManager
import com.soltini.app.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.Bundle
import com.soltini.app.notifications.SoltiniNotificationListener

/**
 * AutoReplyLogItem
 * Records each autonomous auto-reply dispatched by Myra during Busy Mode.
 */
data class AutoReplyLogItem(
    val id: String = UUID.randomUUID().toString().take(8),
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String,
    val appName: String,
    val packageName: String,
    val incomingText: String,
    val replyText: String
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("sender", sender)
        put("appName", appName)
        put("packageName", packageName)
        put("incomingText", incomingText)
        put("replyText", replyText)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): AutoReplyLogItem = AutoReplyLogItem(
            id = json.optString("id", ""),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            sender = json.optString("sender", "Unknown"),
            appName = json.optString("appName", "Chat"),
            packageName = json.optString("packageName", ""),
            incomingText = json.optString("incomingText", ""),
            replyText = json.optString("replyText", "")
        )
    }
}

/**
 * BusyModeSummary
 * Summary of actions taken when the user deactivates Busy Mode ("Main free ho gaya").
 */
data class BusyModeSummary(
    val sessionDurationMinutes: Long,
    val totalRepliedCount: Int,
    val uniqueSenders: List<String>,
    val logs: List<AutoReplyLogItem>,
    val unsentDraftCount: Int = 0
)

/**
 * BusyDraft - a reply that is WRITTEN but NOT sent. It is sent only after the user confirms
 * (notification button "Bhejo" or voice "bhej do").
 */
data class BusyDraft(
    val id: String,
    val contactKey: String,
    val contact: String,
    val appName: String,
    val packageName: String,
    val incoming: List<String>,
    val draftText: String,
    val relation: String?,
    val latestTs: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val replyAction: ExtractedReplyAction? = null
)

/**
 * BusyModeManager (v2 - confirmation based, no spam)
 *
 * What changed and why:
 *  - NOTHING is sent automatically any more. Every reply is a draft that needs the user's OK
 *    (notification button or voice). This ends the "bulk / repeated message" problem.
 *  - GROUPS are never answered (no draft, no send) - college group or any other group.
 *  - Messages are really read: all new messages of one contact are collected for a few seconds and
 *    ONE reply answering every point is drafted by Gemini (old fixed template is only an offline fallback).
 *  - The app + contact that was handled is saved (persisted). A contact gets a new draft only when
 *    that person sends a NEW message after the last handled one (message-timestamp based, plus a
 *    text-hash window for apps without timestamps).
 *  - Family / important contacts (mummy, papa, bhai, behen ...) get a casual tone.
 */
class BusyModeManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BusyModeManager"
        private const val PREFS_NAME = "myra_busy_mode_prefs"
        private const val KEY_BUSY_ACTIVE = "busy_active"
        private const val KEY_BUSY_REASON = "busy_reason"
        private const val KEY_BUSY_START_TIME = "busy_start_time"
        private const val KEY_AUTO_REPLY_HISTORY = "auto_reply_history"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_CONTACT_STATE = "busy_contact_state"
        private const val KEY_IMPORTANT = "busy_important_names"
        private const val NOTIFICATION_ID_BUSY_MODE = 9021
        private const val CHANNEL_ID_BUSY_MODE = "myra_busy_mode_channel"
        private const val CHANNEL_ID_CONFIRM = "myra_busy_confirm_channel"

        const val ACTION_SEND_DRAFT = "com.soltini.app.busy.SEND_DRAFT"
        const val ACTION_SKIP_DRAFT = "com.soltini.app.busy.SKIP_DRAFT"
        const val EXTRA_DRAFT_ID = "busy_draft_id"

        /** Wait this long for more messages of the same person before drafting (one draft, not many). */
        private const val DEBOUNCE_MS = 6_000L
        /** A draft that is not confirmed within this time is dropped (its reply button goes stale). */
        private const val DRAFT_TTL_MS = 30 * 60 * 1000L
        private const val HASH_WINDOW_MS = 10 * 60 * 1000L

        @Volatile
        private var instance: BusyModeManager? = null

        fun getInstance(context: Context): BusyModeManager =
            instance ?: synchronized(this) {
                instance ?: BusyModeManager(context.applicationContext).also { instance = it }
            }
    }

    private data class IncomingMsg(val text: String, val ts: Long)

    private class Buffer(
        val key: String,
        val pkg: String,
        val appName: String,
        val contact: String,
        val relation: String?,
        @Volatile var replyAction: ExtractedReplyAction?,
        val messages: MutableList<IncomingMsg> = mutableListOf(),
        @Volatile var job: Job? = null
    )

    /** Persisted per contact: which app + name we handled and up to which message. */
    private data class ContactState(
        val name: String,
        val appName: String,
        val packageName: String,
        val lastHandledTs: Long,
        val lastSentAt: Long,
        val sentCount: Int
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drafter by lazy { BusyReplyDrafter(context) }

    private val sentReplyFingerprints = CopyOnWriteArrayList<String>()
    private val buffers = ConcurrentHashMap<String, Buffer>()
    private val pendingDrafts = ConcurrentHashMap<String, BusyDraft>()   // contactKey -> draft
    private val contactStates = ConcurrentHashMap<String, ContactState>()
    private val recentHashes = ConcurrentHashMap<String, Long>()
    private val sessionLogs = CopyOnWriteArrayList<AutoReplyLogItem>()

    private val PING_PONG_WORDS = setOf(
        "ok", "k", "okay", "oki", "okey", "kk", "acha", "achha", "achaa",
        "thik", "theek", "thik hai", "theek hai", "cool", "sahi", "badhiya",
        "bye", "tata", "alvida", "good night", "gn", "good morning", "gm",
        "done", "got it", "understood", "samajh gaya", "samajh gayi",
        "thanks", "thank you", "ty", "tysm", "dhanyawad", "shukriya",
        "hmm", "hmmm", "haan", "ha", "yes", "no", "nahi",
        "👍", "👌", "🙏", "❤️", "😊", "👋", "🤝", "done bro", "ok bro"
    )

    init {
        loadHistory()
        loadContactStates()
        createNotificationChannels()
    }

    // ─── State Management ────────────────────────────────────────────────────

    fun isBusyModeActive(): Boolean = prefs.getBoolean(KEY_BUSY_ACTIVE, false)
    fun getBusyReason(): String = prefs.getString(KEY_BUSY_REASON, "busy") ?: "busy"
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "Harshit") ?: "Harshit"
    fun setUserName(name: String) { prefs.edit().putString(KEY_USER_NAME, name.trim()).apply() }

    private fun sessionStart(): Long = prefs.getLong(KEY_BUSY_START_TIME, 0L)

    /** Names the user wants treated as "important" (casual tone) besides the built-in family words. */
    fun getImportantNames(): Set<String> =
        (prefs.getStringSet(KEY_IMPORTANT, emptySet()) ?: emptySet()).toSet()

    fun addImportantName(name: String) {
        val n = name.trim().lowercase()
        if (n.isBlank()) return
        prefs.edit().putStringSet(KEY_IMPORTANT, getImportantNames() + n).apply()
    }

    fun enableBusyMode(reason: String? = null): String {
        val sanitizedReason = cleanReason(reason)
        prefs.edit()
            .putBoolean(KEY_BUSY_ACTIVE, true)
            .putString(KEY_BUSY_REASON, sanitizedReason)
            .putLong(KEY_BUSY_START_TIME, System.currentTimeMillis())
            .apply()

        resetSessionState()
        showBusyModeNotification(sanitizedReason)
        AppLogger.i(TAG, "🟢 Busy Mode ACTIVATED with reason: '$sanitizedReason' (confirmation mode, groups ignored)")
        return sanitizedReason
    }

    fun disableBusyMode(): BusyModeSummary {
        val startTime = prefs.getLong(KEY_BUSY_START_TIME, System.currentTimeMillis())
        val durationMinutes = ((System.currentTimeMillis() - startTime) / (1000 * 60)).coerceAtLeast(1)
        val unsent = pendingDrafts.size

        prefs.edit()
            .putBoolean(KEY_BUSY_ACTIVE, false)
            .putLong(KEY_BUSY_START_TIME, 0L)
            .apply()

        val logsCopy = sessionLogs.toList()
        val uniqueSenders = logsCopy.map { "${it.sender} (${it.appName})" }.distinct()

        cancelAllDraftNotifications()
        buffers.values.forEach { it.job?.cancel() }
        buffers.clear()
        pendingDrafts.clear()
        hideBusyModeNotification()

        AppLogger.i(TAG, "🔴 Busy Mode DEACTIVATED. Sent ${logsCopy.size} confirmed replies, $unsent drafts left unsent.")

        return BusyModeSummary(
            sessionDurationMinutes = durationMinutes,
            totalRepliedCount = logsCopy.size,
            uniqueSenders = uniqueSenders,
            logs = logsCopy,
            unsentDraftCount = unsent
        )
    }

    private fun resetSessionState() {
        cancelAllDraftNotifications()
        buffers.values.forEach { it.job?.cancel() }
        buffers.clear()
        pendingDrafts.clear()
        contactStates.clear()
        recentHashes.clear()
        sessionLogs.clear()
        saveContactStates()
        saveHistory()
    }

    fun getRecentHistory(limit: Int = 20): List<AutoReplyLogItem> =
        sessionLogs.takeLast(limit).reversed()

    // ─── Pending drafts (confirmation) API ───────────────────────────────────

    fun hasPendingDrafts(): Boolean { purgeExpired(); return pendingDrafts.isNotEmpty() }

    fun getPendingDrafts(): List<BusyDraft> { purgeExpired(); return pendingDrafts.values.sortedBy { it.createdAt } }

    /** True if [name] matches a pending draft's contact (used to decide whether to intercept a voice command). */
    fun hasPendingFor(name: String?): Boolean {
        purgeExpired()
        if (name.isNullOrBlank()) return pendingDrafts.isNotEmpty()
        return findByName(name) != null
    }

    private fun findByName(name: String): BusyDraft? {
        val n = name.trim().lowercase()
        return pendingDrafts.values.firstOrNull {
            val c = it.contact.lowercase()
            c == n || c.contains(n) || n.contains(c) || (it.relation != null && it.relation == n)
        }
    }

    /** Voice confirmation. [name] optional ("mummy ko bhej do"). */
    fun confirmLatestOrNamed(name: String?): String {
        purgeExpired()
        val draft = resolveTarget(name) ?: return ambiguityMessage(name)
        val (ok, msg) = confirmDraft(draft.id)
        return if (ok) "Ji Boss, ${draft.contact} ko ${draft.appName} par reply bhej diya: \"${draft.draftText}\"" else msg
    }

    fun skipLatestOrNamed(name: String?): String {
        purgeExpired()
        val draft = resolveTarget(name) ?: return ambiguityMessage(name)
        skipDraft(draft.id)
        return "Theek hai Boss, ${draft.contact} ko reply nahi bheja."
    }

    private fun resolveTarget(name: String?): BusyDraft? {
        if (!name.isNullOrBlank()) return findByName(name)
        return if (pendingDrafts.size == 1) pendingDrafts.values.first() else null
    }

    private fun ambiguityMessage(name: String?): String = when {
        pendingDrafts.isEmpty() -> "Boss, abhi koi reply draft pending nahi hai."
        !name.isNullOrBlank() -> "Boss, ${name} ka koi pending draft nahi mila."
        else -> {
            val names = pendingDrafts.values.joinToString(", ") { it.contact }
            "Boss, ${pendingDrafts.size} drafts pending hain ($names). Kiska bhejun? Naam ke saath bolein, jaise \"${pendingDrafts.values.first().contact} ko bhej do\"."
        }
    }

    /** Sends the drafted reply AFTER the user confirmed. Returns (success, message). */
    fun confirmDraft(id: String): Pair<Boolean, String> {
        val draft = pendingDrafts.values.firstOrNull { it.id == id }
            ?: return false to "Ye draft ab available nahi hai (expire ho gaya ya pehle hi bhej diya)."
        if (System.currentTimeMillis() - draft.createdAt > DRAFT_TTL_MS) {
            dropDraft(draft)
            return false to "Boss, ye draft purana ho gaya tha, isliye nahi bheja."
        }

        // Prefer a FRESH reply action from the live notification; the stored one may be stale.
        val action = findLiveReplyAction(draft) ?: draft.replyAction
            ?: return false to "Boss, ${draft.contact} ki chat ka notification ab nahi hai, isliye direct reply nahi ho paya. Chat kholkar bhej dein."

        recordSentFingerprint(draft.draftText)
        val success = action.sendReply(context, draft.draftText)
        if (!success) return false to "Boss, ${draft.contact} ko reply bhejne me samasya aayi."

        val now = System.currentTimeMillis()
        val prev = contactStates[draft.contactKey]
        contactStates[draft.contactKey] = ContactState(
            name = draft.contact, appName = draft.appName, packageName = draft.packageName,
            lastHandledTs = maxOf(prev?.lastHandledTs ?: 0L, draft.latestTs),
            lastSentAt = now, sentCount = (prev?.sentCount ?: 0) + 1
        )
        saveContactStates()

        sessionLogs.add(
            AutoReplyLogItem(
                sender = draft.contact, appName = draft.appName, packageName = draft.packageName,
                incomingText = draft.incoming.joinToString(" | "), replyText = draft.draftText
            )
        )
        saveHistory()
        pendingDrafts.remove(draft.contactKey)
        cancelDraftNotification(draft)
        updateBusyModeNotification()
        AppLogger.i(TAG, "✅ Confirmed reply sent to ${draft.contact} via ${draft.appName}")
        return true to "Reply bhej diya."
    }

    fun skipDraft(id: String) {
        val draft = pendingDrafts.values.firstOrNull { it.id == id } ?: return
        dropDraft(draft)
        AppLogger.i(TAG, "⏭️ Draft for ${draft.contact} skipped by user")
    }

    private fun dropDraft(draft: BusyDraft) {
        pendingDrafts.remove(draft.contactKey)
        cancelDraftNotification(draft)
    }

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        pendingDrafts.values.filter { now - it.createdAt > DRAFT_TTL_MS }.forEach { dropDraft(it) }
    }

    private fun findLiveReplyAction(draft: BusyDraft): ExtractedReplyAction? {
        return try {
            val listener = SoltiniNotificationListener.getInstance() ?: return null
            val active = listener.activeNotifications ?: return null
            for (sbn in active) {
                if (sbn.packageName != draft.packageName) continue
                val n = sbn.notification ?: continue
                if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
                val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: continue
                if (BusyContactPolicy.cleanContactName(title).equals(draft.contact, ignoreCase = true)) {
                    extractReplyAction(n)?.let { return it }
                }
            }
            null
        } catch (_: Exception) { null }
    }

    // ─── Incoming notification processing (draft only, never sends) ──────────

    fun processIncomingNotification(sbn: StatusBarNotification) {
        if (!isBusyModeActive()) return
        val pkg = sbn.packageName ?: return
        if (pkg == context.packageName) return
        val notif = sbn.notification ?: return
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = notif.extras ?: return

        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString() ?: ""
        val plainText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        // 1. Collect individual messages (with timestamps) from MessagingStyle when available
        val msgs = mutableListOf<IncomingMsg>()
        val senders = mutableListOf<String>()
        @Suppress("DEPRECATION")
        val arr = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (arr != null) {
            for (b in arr) {
                if (b !is Bundle) continue
                val t = b.getCharSequence("text")?.toString() ?: continue
                val ts = b.getLong("time", 0L).let { if (it > 0L) it else sbn.postTime }
                val sender = b.getCharSequence("sender")?.toString()
                    ?: b.getBundle("sender_person")?.getCharSequence("name")?.toString()
                if (!sender.isNullOrBlank()) senders.add(sender)
                if (isSelfSender(sender)) continue
                msgs.add(IncomingMsg(t, ts))
            }
        }
        if (msgs.isEmpty() && plainText.isNotBlank() && !plainText.matches(Regex("^\\d+\\s+new messages?$", RegexOption.IGNORE_CASE))) {
            msgs.add(IncomingMsg(plainText, sbn.postTime))
        }
        if (rawTitle.isBlank() || msgs.isEmpty()) return

        // 2. GROUPS: never draft, never send
        val conversationTitle = extras.getCharSequence("android.conversationTitle")?.toString()
        val groupFlag = extras.getBoolean("android.isGroupConversation", false)
        val latestText = msgs.last().text
        if (BusyContactPolicy.looksLikeGroup(rawTitle, conversationTitle, groupFlag, senders, latestText)) {
            Log.d(TAG, "Ignoring group chat '$rawTitle' (Busy Mode never messages groups)")
            return
        }

        val contact = BusyContactPolicy.cleanContactName(rawTitle)
        if (contact.isBlank() || isSelfSender(contact)) return
        val appName = resolveAppName(pkg)
        val key = "$pkg|${contact.lowercase()}"

        // 3. Reply action must exist (otherwise we could never send after confirmation)
        val replyAction = extractReplyAction(notif) ?: run {
            Log.d(TAG, "No direct-reply support for $appName ($contact)")
            return
        }

        // 4. Keep only NEW, real messages
        val state = contactStates[key]
        val floorTs = maxOf(state?.lastHandledTs ?: 0L, sessionStart() - 2_000L)
        val fresh = msgs.filter { it.ts > floorTs }
            .filter { isEligibleMessage(it.text) }
            .filter { !seenRecently(key, it.text) }
        if (fresh.isEmpty()) return

        // 5. Collect for a few seconds -> ONE draft per contact (not one per notification update)
        val buf = buffers.getOrPut(key) {
            Buffer(key, pkg, appName, contact, BusyContactPolicy.importanceFor(contact, getImportantNames()), replyAction)
        }
        synchronized(buf) {
            buf.replyAction = replyAction
            for (m in fresh) if (buf.messages.none { it.ts == m.ts && it.text == m.text }) buf.messages.add(m)
            buf.job?.cancel()
            buf.job = scope.launch {
                delay(DEBOUNCE_MS)
                try { finalizeBuffer(key) } catch (e: Exception) { Log.w(TAG, "draft failed: ${e.message}") }
            }
        }
    }

    private fun finalizeBuffer(key: String) {
        if (!isBusyModeActive()) return
        val buf = buffers.remove(key) ?: return
        val newMsgs = synchronized(buf) { buf.messages.sortedBy { it.ts } }
        if (newMsgs.isEmpty()) return

        // merge with an unconfirmed draft so ONE updated reply covers everything they wrote
        val existing = pendingDrafts[key]?.takeIf { System.currentTimeMillis() - it.createdAt <= DRAFT_TTL_MS }
        val incoming = (existing?.incoming.orEmpty() + newMsgs.map { it.text }).takeLast(12)
        val latestTs = maxOf(existing?.latestTs ?: 0L, newMsgs.last().ts)

        val previous = sessionLogs.filter { it.sender.equals(buf.contact, true) && it.packageName == buf.pkg }
            .map { it.replyText }
        val text = drafter.draft(getUserName(), getBusyReason(), buf.contact, buf.relation, incoming, previous)

        // mark these messages as handled NOW so notification re-posts never create a second draft
        val prev = contactStates[key]
        contactStates[key] = ContactState(
            name = buf.contact, appName = buf.appName, packageName = buf.pkg,
            lastHandledTs = latestTs, lastSentAt = prev?.lastSentAt ?: 0L, sentCount = prev?.sentCount ?: 0
        )
        saveContactStates()

        val draft = BusyDraft(
            id = Math.abs(key.hashCode()).toString(), contactKey = key, contact = buf.contact,
            appName = buf.appName, packageName = buf.pkg, incoming = incoming, draftText = text,
            relation = buf.relation, latestTs = latestTs, replyAction = buf.replyAction
        )
        pendingDrafts[key] = draft
        postDraftNotification(draft)
        AppLogger.i(TAG, "📝 Draft ready for ${draft.contact} (${draft.appName}) - waiting for confirmation")
    }

    private fun isSelfSender(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name.lowercase().trim()
        val enrolled = try {
            VoiceProfileManager.getInstance(context).getAllProfiles().map { it.name.lowercase().trim() }
        } catch (_: Exception) { emptyList() }
        val self = listOf("harshit", "you", "me", "tu", "myself", "आप", "main", "mein", getUserName().lowercase().trim()) + enrolled
        return self.any { it.isNotBlank() && (n == it || n.startsWith("$it:")) }
    }

    private fun isEligibleMessage(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.isBlank()) return false
        if (lower.startsWith("you:") || lower.startsWith("you :") || lower.startsWith("आप:")) return false
        for (fp in sentReplyFingerprints) {
            val f = fp.lowercase().trim()
            if (f.isNotEmpty() && (lower == f || (f.length >= 10 && (lower.contains(f.take(20)) || lower.contains(f.takeLast(20)))))) return false
        }
        val cleaned = lower.replace(Regex("""[.,!?;:'"~-]"""), "").trim()
        if (PING_PONG_WORDS.contains(cleaned) || cleaned.length <= 2) return false
        if (lower.contains("checking for new messages") || lower.contains("backup in progress") ||
            lower.contains("missed call") || lower.contains("calling") || lower.contains("deleted this message") ||
            lower.contains("typing...") || lower.contains("end-to-end encrypted")
        ) return false
        return true
    }

    /** Text-hash window: for apps whose notifications carry no per-message timestamp. */
    private fun seenRecently(key: String, text: String): Boolean {
        val now = System.currentTimeMillis()
        recentHashes.entries.removeIf { now - it.value > HASH_WINDOW_MS }
        val h = "$key|${text.trim().lowercase().hashCode()}"
        val seen = recentHashes[h]
        recentHashes[h] = now
        return seen != null && now - seen <= HASH_WINDOW_MS
    }

    private fun recordSentFingerprint(replyText: String) {
        sentReplyFingerprints.add(replyText)
        if (sentReplyFingerprints.size > 80) sentReplyFingerprints.removeAt(0)
    }

    private fun extractReplyAction(notification: Notification): ExtractedReplyAction? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val inputs = action.remoteInputs
            if (inputs != null && inputs.isNotEmpty()) {
                val pendingIntent = action.actionIntent ?: continue
                return ExtractedReplyAction(pendingIntent, inputs)
            }
        }
        return null
    }

    private fun resolveAppName(packageName: String): String = when {
        packageName.contains("whatsapp") -> "WhatsApp"
        packageName.contains("instagram") -> "Instagram"
        packageName.contains("telegram") -> "Telegram"
        packageName.contains("messaging") || packageName.contains("mms") -> "SMS"
        packageName.contains("signal") -> "Signal"
        packageName.contains("messenger") -> "Messenger"
        else -> try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) { "Chat" }
    }

    private fun cleanReason(reason: String?): String {
        if (reason.isNullOrBlank()) return "busy"
        return reason.trim()
            .replace(Regex("""^(main|me|abhi|i\s+am)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(me|mein)\s+(hoon|hu|hun)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(hoon|hu|hun)$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { "busy" }
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private fun saveContactStates() {
        val obj = JSONObject()
        contactStates.forEach { (k, v) ->
            obj.put(k, JSONObject().apply {
                put("name", v.name); put("app", v.appName); put("pkg", v.packageName)
                put("handled", v.lastHandledTs); put("sentAt", v.lastSentAt); put("count", v.sentCount)
            })
        }
        prefs.edit().putString(KEY_CONTACT_STATE, obj.toString()).apply()
    }

    private fun loadContactStates() {
        contactStates.clear()
        val raw = prefs.getString(KEY_CONTACT_STATE, null) ?: return
        try {
            val obj = JSONObject(raw)
            for (k in obj.keys()) {
                val o = obj.getJSONObject(k)
                contactStates[k] = ContactState(
                    o.optString("name"), o.optString("app"), o.optString("pkg"),
                    o.optLong("handled"), o.optLong("sentAt"), o.optInt("count")
                )
            }
        } catch (_: Exception) {}
    }

    private fun saveHistory() {
        val array = JSONArray()
        sessionLogs.takeLast(50).forEach { array.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_AUTO_REPLY_HISTORY, array.toString()).apply()
    }

    private fun loadHistory() {
        sessionLogs.clear()
        val raw = prefs.getString(KEY_AUTO_REPLY_HISTORY, null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) sessionLogs.add(AutoReplyLogItem.fromJsonObject(array.getJSONObject(i)))
        } catch (_: Exception) {}
    }

    // ─── Notifications ───────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID_BUSY_MODE, "Myra Busy Mode", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows that Busy Mode is active"
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID_CONFIRM, "Myra reply confirmation", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Asks you before Myra sends any reply while you are busy"
                }
            )
        }
    }

    private fun draftNotificationId(draft: BusyDraft): Int = 9100 + (Math.abs(draft.contactKey.hashCode()) % 5000)

    private fun postDraftNotification(draft: BusyDraft) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            fun pi(action: String, req: Int): PendingIntent = PendingIntent.getBroadcast(
                context, req,
                Intent(context, BusyReplyReceiver::class.java).setAction(action).putExtra(EXTRA_DRAFT_ID, draft.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val big = buildString {
                append("Unhone likha:\n")
                draft.incoming.takeLast(4).forEach { append("• ").append(it.take(120)).append('\n') }
                append("\nMera reply:\n").append(draft.draftText)
            }
            val notif = NotificationCompat.Builder(context, CHANNEL_ID_CONFIRM)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("${draft.contact} (${draft.appName}) - reply bhejun?")
                .setContentText(draft.draftText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(big))
                .setContentIntent(open)
                .addAction(0, "Bhejo", pi(ACTION_SEND_DRAFT, draftNotificationId(draft) * 2))
                .addAction(0, "Skip", pi(ACTION_SKIP_DRAFT, draftNotificationId(draft) * 2 + 1))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .build()
            nm.notify(draftNotificationId(draft), notif)
        } catch (e: Exception) {
            Log.w(TAG, "Could not post draft notification: ${e.message}")
        }
    }

    private fun cancelDraftNotification(draft: BusyDraft) {
        try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(draftNotificationId(draft)) } catch (_: Exception) {}
    }

    private fun cancelAllDraftNotifications() {
        pendingDrafts.values.forEach { cancelDraftNotification(it) }
    }

    private fun showBusyModeNotification(reason: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val pendingIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, CHANNEL_ID_BUSY_MODE)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Myra Busy Mode Active")
                .setContentText("$reason - reply drafts aapki confirmation ke baad hi jayenge, groups me kuch nahi")
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            nm.notify(NOTIFICATION_ID_BUSY_MODE, notif)
        } catch (e: Exception) {
            Log.w(TAG, "Could not post Busy Mode notification: ${e.message}")
        }
    }

    private fun updateBusyModeNotification() {
        if (!isBusyModeActive()) return
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val notif = NotificationCompat.Builder(context, CHANNEL_ID_BUSY_MODE)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Myra Busy Mode Active")
                .setContentText("${sessionLogs.size} replies bheje (aapki confirmation se) - ${getBusyReason()}")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            nm.notify(NOTIFICATION_ID_BUSY_MODE, notif)
        } catch (_: Exception) {}
    }

    private fun hideBusyModeNotification() {
        try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(NOTIFICATION_ID_BUSY_MODE) } catch (_: Exception) {}
    }
}
