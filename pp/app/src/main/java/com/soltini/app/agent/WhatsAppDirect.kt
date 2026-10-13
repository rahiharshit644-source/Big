package com.soltini.app.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.soltini.app.settings.AppSettings
import com.soltini.app.telephony.CallNotificationManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * WhatsAppDirect - WhatsApp messaging and calling by NAME or by ANY NUMBER (saved or not).
 *
 *  - Numbers go through the official wa.me / api.whatsapp.com deep link, so the contact does not
 *    need to be saved. WhatsApp Business (com.whatsapp.w4b) is used if normal WhatsApp is missing.
 *  - Names are resolved via spoken nicknames + the phone's contacts to a number first (much more
 *    reliable than searching inside WhatsApp's UI); the old UI-search route is only a fallback.
 *  - Messages can be sent straight away or drafted -> read back -> confirmed / cancelled.
 *  - Voice and video calls, and "delete my last message" (for me / for everyone).
 *
 * Blocking (waits for screens): call off the main thread.
 */
class WhatsAppDirect(private val context: Context) {

    companion object {
        private const val TAG = "WhatsAppDirect"
        private const val WA = "com.whatsapp"
        private const val W4B = "com.whatsapp.w4b"
        private val SEND_IDS = listOf("com.whatsapp:id/send", "com.whatsapp.w4b:id/send")
        private val BUBBLE_IDS = listOf(
            "com.whatsapp:id/message_text", "com.whatsapp.w4b:id/message_text",
            "com.whatsapp:id/conversation_text_row", "com.whatsapp.w4b:id/conversation_text_row"
        )
        private val DELETE_IDS = listOf("com.whatsapp:id/menuitem_delete", "com.whatsapp.w4b:id/menuitem_delete")
    }

    private data class Draft(val label: String, val phone: String?, val name: String?, val message: String)

    private sealed class Target {
        data class Number(val phone: String, val label: String) : Target()
        data class Name(val name: String) : Target()               // not in contacts -> UI fallback
        data class Ambiguous(val options: List<String>) : Target()
    }

    @Volatile private var pending: Draft? = null
    private val aliases = ContactAliasStore(context)

    // ───────────────────────── Public API ─────────────────────────

    fun sendMessage(targetRaw: String, message: String, confirmFirst: Boolean): JSONObject {
        if (targetRaw.isBlank() || message.isBlank()) return err("A contact name or number and a message are required.")
        return when (val t = resolveTarget(targetRaw)) {
            is Target.Ambiguous -> clarify(t, targetRaw)
            is Target.Name -> {
                if (confirmFirst) {
                    pending = Draft(t.name, null, t.name, message)
                    return draftReady(t.name, message)
                }
                WhatsAppAutomator(context).sendMessage(t.name, message)
            }
            is Target.Number -> {
                if (confirmFirst) {
                    pending = Draft(t.label, t.phone, null, message)
                    return draftReady(t.label, message)
                }
                deliver(t.phone, t.label, message)
            }
        }
    }

    fun confirmSend(): JSONObject {
        val d = pending ?: return err("There is no WhatsApp draft waiting to be sent.")
        pending = null
        return if (d.phone != null) deliver(d.phone, d.label, d.message)
        else WhatsAppAutomator(context).sendMessage(d.name ?: d.label, d.message)
    }

    fun cancelDraft(): JSONObject {
        val d = pending
        pending = null
        return if (d != null) ok("status", "cancelled", "message", "Draft for ${d.label} cancelled. Nothing was sent.")
        else ok("status", "nothing_to_cancel")
    }

    fun call(targetRaw: String, video: Boolean): JSONObject {
        if (targetRaw.isBlank()) return err("A contact name or number is required.")
        val kind = if (video) "video" else "voice"
        return when (val t = resolveTarget(targetRaw)) {
            is Target.Ambiguous -> clarify(t, targetRaw)
            is Target.Name -> WhatsAppAutomator(context).makeCall(t.name, video)
            is Target.Number -> {
                try { CallNotificationManager.getInstance(context).markOutgoingCallInitiated(t.phone) } catch (_: Exception) {}
                if (!openChat(t.phone, null)) return err("WhatsApp is not installed.")
                val svc = SoltiniAccessibilityService.getInstance()
                    ?: return ok("status", "chat_opened", "message", "Chat with ${t.label} is open. Accessibility is off, so tap the $kind call button yourself.")

                // Wait for the chat screen (message box visible).
                if (!waitUntil(9_000) { svc.hasEditableField() }) {
                    return err("The chat did not open. The number may not be on WhatsApp.")
                }
                val label = if (video) listOf("Video call") else listOf("Voice call")
                var tapped = svc.clickExactText(label)
                if (!tapped) {
                    // Newer WhatsApp: one "Call" icon that opens a Voice/Video menu.
                    if (svc.clickExactText(listOf("Call", "Calls"))) {
                        sleep(700)
                        tapped = waitUntil(3_000) { svc.clickExactText(label) }
                    }
                }
                if (!tapped) return err("I could not find the $kind call button in the chat.")

                // Some versions ask "Start a call?" - confirm it.
                sleep(1_200)
                val screen = svc.readScreen().lowercase(Locale.ROOT)
                if (screen.contains("cancel") && (screen.contains("call"))) {
                    svc.clickExactText(listOf("Call", "CALL", "Video call", "Voice call", "Start", "OK"))
                }
                ok("status", "call_started", "type", kind, "to", t.label)
            }
        }
    }

    /** Deletes the last message the user SENT in a chat (for me / for everyone). */
    fun deleteLastMessage(targetRaw: String?, forEveryone: Boolean, confirmed: Boolean): JSONObject {
        if (!confirmed) {
            return ok(
                "status", "confirmation_required",
                "message", "Ask the user: 'Delete your last WhatsApp message" +
                    (if (forEveryone) " for everyone" else " for you only") +
                    (if (!targetRaw.isNullOrBlank()) " in the chat with $targetRaw" else "") +
                    "?' Only after a clear yes call whatsapp_delete_message again with confirmed=true."
            )
        }
        val svc = SoltiniAccessibilityService.getInstance() ?: return err("Accessibility Service is not active.")

        if (!targetRaw.isNullOrBlank()) {
            when (val t = resolveTarget(targetRaw)) {
                is Target.Number -> if (!openChat(t.phone, null)) return err("WhatsApp is not installed.")
                is Target.Ambiguous -> return clarify(t, targetRaw)
                is Target.Name -> return err("I could not find '${t.name}' in your contacts. Give me their number or open the chat first.")
            }
            if (!waitUntil(9_000) { svc.hasEditableField() }) return err("The chat did not open.")
            sleep(800)
        }

        // 1) long-press the newest outgoing bubble
        val width = context.resources.displayMetrics.widthPixels
        var pressed = false
        waitUntil(5_000) {
            val nodes = svc.findNodesByViewId(BUBBLE_IDS)
            if (nodes.isEmpty()) return@waitUntil false
            val rect = android.graphics.Rect()
            // outgoing bubbles sit on the right edge; take the lowest one
            val outgoing = nodes.filter { n -> n.getBoundsInScreen(rect); rect.right >= width * 0.80 }
            val pick = (outgoing.ifEmpty { nodes }).maxByOrNull { n -> n.getBoundsInScreen(rect); rect.top }
            pressed = pick != null && svc.longPressNode(android.view.accessibility.AccessibilityNodeInfo.obtain(pick))
            pressed
        }
        if (!pressed) return err("I could not find a message to select.")
        sleep(700)

        // 2) trash icon
        val trash = waitUntil(4_000) {
            val ids = svc.findNodesByViewId(DELETE_IDS)
            if (ids.isNotEmpty()) svc.clickNode(ids.first()) else svc.clickExactText(listOf("Delete"))
        }
        if (!trash) return err("I could not find the delete button.")
        sleep(700)

        // 3) option
        val optionOk = if (forEveryone) {
            waitUntil(4_000) { svc.clickExactText(listOf("Delete for everyone")) }
        } else {
            waitUntil(4_000) { svc.clickExactText(listOf("Delete for me")) }
        }
        if (!optionOk) {
            return err(if (forEveryone) "'Delete for everyone' was not offered - the message is probably too old or was not sent by you."
                       else "Could not find 'Delete for me'.")
        }
        sleep(500)
        // some versions show a last confirmation
        svc.clickExactText(listOf("Delete"))
        return ok("status", "deleted", "for_everyone", forEveryone)
    }

    // ───────────────────────── Internals ─────────────────────────

    private fun deliver(phone: String, label: String, message: String): JSONObject {
        if (!openChat(phone, message)) return err("WhatsApp is not installed.")
        val svc = SoltiniAccessibilityService.getInstance()
            ?: return ok("status", "chat_opened", "message", "Chat with $label is open with your text ready. Accessibility is off, so tap Send yourself.")

        val sent = waitUntil(10_000) { tapSend(svc) }
        return if (sent) ok("status", "message_sent", "to", label)
        else err("The chat opened but I could not press Send. The number may not be on WhatsApp.")
    }

    private fun tapSend(svc: SoltiniAccessibilityService): Boolean {
        val byId = svc.findNodesByViewId(SEND_IDS)
        if (byId.isNotEmpty() && svc.clickNode(byId.first())) return true
        return svc.clickExactText(listOf("Send"))
    }

    private fun installedPkg(): String? = when {
        isInstalled(WA) -> WA
        isInstalled(W4B) -> W4B
        else -> null
    }

    private fun isInstalled(pkg: String) = try { context.packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }

    private fun openChat(phone: String, text: String?): Boolean {
        val pkg = installedPkg() ?: return false
        val url = buildString {
            append("https://api.whatsapp.com/send?phone=").append(phone)
            if (!text.isNullOrBlank()) append("&text=").append(URLEncoder.encode(text, "UTF-8"))
        }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(i); true } catch (e: Exception) {
            Log.w(TAG, "openChat failed: ${e.message}"); false
        }
    }

    private fun looksLikeNumber(s: String): Boolean {
        val stripped = s.replace(Regex("[\\s\\-().]"), "")
        return stripped.matches(Regex("^\\+?\\d{7,15}$"))
    }

    /** Digits only, with the default country code added to local 10-digit numbers. */
    fun normalizeNumber(raw: String): String {
        val t = raw.trim()
        val digits = t.filter { it.isDigit() }
        if (t.startsWith("+")) return digits
        if (digits.startsWith("00")) return digits.drop(2)
        val cc = try { AppSettings(context).defaultCountryCode } catch (_: Exception) { "91" }
        return when {
            digits.length == 10 -> cc + digits
            digits.length == 11 && digits.startsWith("0") -> cc + digits.drop(1)
            else -> digits
        }
    }

    private fun resolveTarget(raw: String): Target {
        val name = aliases.resolve(raw.trim())
        if (looksLikeNumber(name)) return Target.Number(normalizeNumber(name), name.trim())

        val matches = try { CallNotificationManager.getInstance(context).searchContacts(name) } catch (_: Exception) { emptyList() }
        if (matches.isEmpty()) return Target.Name(name)
        val exact = matches.filter { it.name.equals(name, ignoreCase = true) }
        val pick = when {
            exact.size == 1 -> exact.first()
            matches.size == 1 -> matches.first()
            else -> return Target.Ambiguous(matches.take(4).map { "${it.name} (${it.phoneNumber})" })
        }
        return Target.Number(normalizeNumber(pick.phoneNumber), pick.name)
    }

    private fun clarify(t: Target.Ambiguous, raw: String): JSONObject = ok(
        "status", "clarification_needed",
        "message", "Several contacts match '$raw': ${t.options.joinToString(", ")}. Ask the user which one."
    )

    private fun draftReady(label: String, message: String): JSONObject = ok(
        "status", "draft_ready", "to", label, "draft", message,
        "message", "Read the draft back to the user and ask whether to send it. Then call whatsapp_confirm_send, or whatsapp_cancel_draft."
    )

    private fun waitUntil(timeoutMs: Long, stepMs: Long = 450, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (cond()) return true
            sleep(stepMs)
        }
        return cond()
    }

    private fun sleep(ms: Long) { try { Thread.sleep(ms) } catch (_: InterruptedException) {} }

    private fun ok(vararg pairs: Any): JSONObject = JSONObject().apply {
        var i = 0
        while (i < pairs.size - 1) { put(pairs[i].toString(), pairs[i + 1]); i += 2 }
    }

    private fun err(msg: String): JSONObject = JSONObject().apply { put("status", "error"); put("error", msg) }
}
