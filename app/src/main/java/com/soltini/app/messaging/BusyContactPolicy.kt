package com.soltini.app.messaging

/**
 * BusyContactPolicy
 *
 * Pure (Android-free, unit-testable) rules for Busy Mode:
 *  - which chats are GROUPS (Myra must never message in a group),
 *  - which contacts are IMPORTANT (family -> casual tone),
 *  - how a spoken confirmation ("bhej do" / "skip") is understood.
 */
object BusyContactPolicy {

    /** token in a contact name -> how that person is addressed in the prompt */
    private val RELATION_TOKENS: Map<String, String> = mapOf(
        "mummy" to "mummy", "mumma" to "mummy", "mom" to "mummy", "mother" to "mummy", "maa" to "mummy", "mommy" to "mummy",
        "papa" to "papa", "pappa" to "papa", "dad" to "papa", "daddy" to "papa", "father" to "papa", "pita" to "papa",
        "bhai" to "bhai", "bhaiya" to "bhai", "bhaiyya" to "bhai", "brother" to "bhai", "bhaijaan" to "bhai",
        "behen" to "behen", "bahan" to "behen", "behan" to "behen", "didi" to "behen", "di" to "behen", "sister" to "behen", "sis" to "behen",
        "dada" to "dada", "dadi" to "dadi", "nana" to "nana", "nani" to "nani"
    )

    private fun nameTokens(name: String): List<String> =
        name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }

    /** Strips WhatsApp style "(3 messages)" suffix and anything after a ':' from a notification title. */
    fun cleanContactName(title: String): String =
        title.replace(Regex("\\s*\\(\\d+\\s+(?:new\\s+)?messages?\\)\\s*$", RegexOption.IGNORE_CASE), "")
            .substringBefore(":")
            .trim()

    /**
     * @return relation label (mummy / papa / bhai / behen ...) for family, "important" for names the user
     *         added manually, or null for everybody else.
     */
    fun importanceFor(contactName: String, manualImportant: Set<String> = emptySet()): String? {
        val clean = cleanContactName(contactName)
        val lowerClean = clean.lowercase()
        if (lowerClean.isNotBlank() && manualImportant.any { it.equals(lowerClean, ignoreCase = true) }) return "important"
        for (t in nameTokens(clean)) {
            RELATION_TOKENS[t]?.let { return it }
        }
        return null
    }

    /**
     * Conservative group detection - when in doubt we say "group", because the safe failure mode is
     * "do not draft / do not send anything".
     */
    fun looksLikeGroup(
        title: String,
        conversationTitle: String?,
        isGroupFlag: Boolean,
        messageSenders: List<String>,
        latestText: String
    ): Boolean {
        if (isGroupFlag) return true
        if (!conversationTitle.isNullOrBlank()) return true

        val name = cleanContactName(title).lowercase()
        if (name.isBlank()) return false

        // Telegram / others: "Sender @ Group name"
        if (title.contains(" @ ")) return true

        // messages in this chat were written by somebody other than the chat title
        val otherSender = messageSenders.map { cleanContactName(it).lowercase() }
            .filter { it.isNotBlank() && it != "you" && it != "me" }
            .any { it != name && !name.contains(it) && !it.contains(name) }
        if (otherSender) return true

        // WhatsApp group text arrives as "Sender: message"
        val m = Regex("^([^:\\n]{1,40}):\\s+\\S").find(latestText.trim())
        if (m != null) {
            val prefix = m.groupValues[1].trim().lowercase()
            val words = prefix.split(Regex("\\s+")).size
            if (words <= 4 && prefix != name && !name.contains(prefix) && !prefix.contains(name) &&
                prefix != "you" && !prefix.startsWith("http")
            ) return true
        }
        return false
    }

    // ─── Spoken confirmation ────────────────────────────────────────────────

    enum class ConfirmKind { SEND, SKIP }
    data class ConfirmIntent(val kind: ConfirmKind, val name: String?)

    private val SKIP_PHRASES = listOf(
        "skip", "mat bhejo", "mat bhej", "reply mat", "reply mat karo", "reply mat bhejo", "rehne do", "rahne do", "cancel", "nahi bhejna",
        "mat karo reply", "ignore", "chhod do", "chod do", "drop it", "dont send", "don't send"
    )
    private val SEND_PHRASES = listOf(
        "bhej do", "bhej de", "bhejo", "bhej dena", "send kar do", "send kardo", "send karo", "send it"
    )

    /** Returns null for anything that is not a short confirm / skip command. */
    fun parseConfirmation(input: String): ConfirmIntent? {
        var t = input.lowercase().trim()
            .replace(Regex("^(hey\\s+myra|myra|soltini|please|kripya)\\s*,?\\s*"), "")
            .replace(Regex("[.!?,]+$"), "")
            .trim()
        if (t.isBlank()) return null
        if (t.split(Regex("\\s+")).size > 7) return null
        // anything carrying message content ("ki ...", ":") is a normal send-message command, not a confirmation
        if (Regex("\\s(ki|ke liye|that|saying)\\s").containsMatchIn(" $t ") || t.contains(":")) return null

        t = t.replace(Regex("^(haan|han|ha|yes|ok|okay|theek hai|thik hai|haan ji)\\s+"), "").trim()

        // optional "<name> ko ..." / "<name> ka reply ..."
        var name: String? = null
        val nm = Regex("^(.{1,25}?)\\s+(?:ko|ka|ke)\\s+(.+)$").find(t)
        var rest = t
        if (nm != null) {
            name = nm.groupValues[1].trim()
            rest = nm.groupValues[2].trim()
            rest = rest.replace(Regex("^(reply|jawab|message|msg)\\s+"), "").trim()
        }
        val saidReply = Regex("^(reply|jawab)\\b").containsMatchIn(rest)
        rest = rest.replace(Regex("^(reply|jawab|ye|yeh|wo|vo)\\s+"), "").trim()

        val kind = when {
            SKIP_PHRASES.any { rest == it || rest.endsWith(it) || t == it } -> ConfirmKind.SKIP
            SEND_PHRASES.any { rest == it || rest.endsWith(" $it") } -> ConfirmKind.SEND
            saidReply && (rest == "kar do" || rest == "karo" || rest == "kardo" || rest == "de do") -> ConfirmKind.SEND
            else -> return null
        }
        if (name != null && name.split(Regex("\\s+")).size > 3) return null
        return ConfirmIntent(kind, name?.takeIf { it.isNotBlank() })
    }
}
