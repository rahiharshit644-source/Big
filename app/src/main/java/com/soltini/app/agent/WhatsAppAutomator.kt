package com.soltini.app.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONObject

/**
 * WhatsAppAutomator
 *
 * Automates WhatsApp using AccessibilityService UI automation.
 *
 * Tools:
 *  - whatsapp_send_message
 *  - whatsapp_call
 *  - whatsapp_video_call
 */
class WhatsAppAutomator(private val context: Context) {

    companion object {
        private const val TAG = "WhatsAppAutomator"
        private const val WHATSAPP_PKG = "com.whatsapp"
    }

    /**
     * Opens WhatsApp and the chat of [contactName], then returns the accessibility service when the chat
     * (message box) is really open, or an error JSON. Every step waits for the UI instead of sleeping.
     */
    private fun openChat(svc: SoltiniAccessibilityService, contactName: String, needEditable: Boolean): JSONObject? {
        if (!launchWhatsApp()) return error("WhatsApp not installed")
        if (!UiWait.foreground(svc, WHATSAPP_PKG, 8000)) return error("WhatsApp did not come to the front")
        UiWait.settle(1500)

        // 1. Search icon
        if (!UiWait.clickWhenReady(svc, "Search", 5000)) return error("Could not find WhatsApp search button")

        // 2. Type the contact name once a search field accepts text
        if (!UiWait.typeWhenReady(svc, contactName, 4000)) return error("Could not type in search field")
        UiWait.settle(2000)   // let the result list render

        // 3. Tap the contact
        if (!UiWait.clickWhenReady(svc, contactName, 6000)) return error("Contact '$contactName' not found in search results")

        // 4. Verify the CHAT opened (message box). If the tap hit the avatar, WhatsApp opens the contact-info
        //    screen, which has no message box: back out and tap the row again once.
        if (needEditable && !UiWait.editableReady(svc, 4000)) {
            Log.w(TAG, "No message box after tapping '$contactName' - probably the profile screen. Backing out and retrying once.")
            svc.pressBack()
            UiWait.settle(1200)
            if (!UiWait.clickWhenReady(svc, contactName, 4000)) return error("Contact '$contactName' select nahi ho paaya")
            if (!UiWait.editableReady(svc, 4000)) {
                return error("Chat screen open nahi hua - profile/info page khul gaya hai, message box nahi mila")
            }
        } else if (!needEditable) {
            UiWait.settle(1500)
        }
        return null
    }

    /**
     * Sends a message to a contact by automating the UI.
     */
    fun sendMessage(contactName: String, message: String): JSONObject {
        if (contactName.isBlank() || message.isBlank()) {
            return error("contact_name and message are required")
        }

        val svc = SoltiniAccessibilityService.getInstance()
            ?: return error("Accessibility Service not active")

        openChat(svc, contactName, needEditable = true)?.let { return it }

        // 5. Type the message, then press Send as soon as the Send button exists
        if (!UiWait.typeWhenReady(svc, message, 3000)) {
            return error("Could not type message in chat")
        }
        if (!UiWait.clickWhenReady(svc, "Send", 3000)) {
            return error("Could not find Send button")
        }

        // 6. Verify: the sent text should now be visible in the chat
        val snippet = message.trim().take(20)
        UiWait.settle(1200)
        val verified = UiWait.textVisible(svc, snippet, 2500)
        return result("status", "message_sent", "contact", contactName, "verified", verified.toString())
    }

    /**
     * Starts a voice or video call.
     */
    fun makeCall(contactName: String, isVideo: Boolean): JSONObject {
        if (contactName.isBlank()) return error("contact_name is required")

        com.soltini.app.telephony.CallNotificationManager.getInstance(context).markOutgoingCallInitiated(contactName)

        val svc = SoltiniAccessibilityService.getInstance()
            ?: return error("Accessibility Service not active")

        // A call button lives inside the chat, so we do not require the message box here.
        openChat(svc, contactName, needEditable = false)?.let { return it }

        val callButton = if (isVideo) "Video call" else "Voice call"
        val labels = listOf(callButton, "Call")
        if (!UiWait.clickAnyWhenReady(svc, labels, 4000)) {
            // the tap may have landed on the avatar (profile screen): back out and try the row once more
            svc.pressBack()
            UiWait.settle(1200)
            if (!UiWait.clickWhenReady(svc, contactName, 4000)) return error("Contact '$contactName' select nahi ho paaya")
            UiWait.settle(1500)
            if (!UiWait.clickAnyWhenReady(svc, labels, 4000)) {
                return error("Could not find $callButton button in chat")
            }
        }

        // Verify: the call screen shows "Calling" / "Ringing" / "Connecting" / an end-call control
        val onCallScreen = UiWait.until(5000) {
            val t = svc.readScreen().lowercase()
            t.contains("calling") || t.contains("ringing") || t.contains("connecting") ||
                t.contains("end call") || t.contains("end_call") || t.contains("call ended")
        }
        return if (onCallScreen) {
            result("status", "call_started", "contact", contactName, "video", isVideo.toString(), "verified", "true")
        } else {
            result(
                "status", "call_unverified", "contact", contactName, "video", isVideo.toString(),
                "note", "Call button dabaya par call screen nahi dikhi - phone par check karein."
            )
        }
    }

    private fun launchWhatsApp(): Boolean {
        val pm: PackageManager = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(WHATSAPP_PKG) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    private fun result(vararg pairs: Any): JSONObject = JSONObject().apply {
        var i = 0
        while (i < pairs.size - 1) {
            put(pairs[i].toString(), pairs[i + 1])
            i += 2
        }
    }

    private fun error(msg: String): JSONObject = JSONObject().apply { put("error", msg) }
}
