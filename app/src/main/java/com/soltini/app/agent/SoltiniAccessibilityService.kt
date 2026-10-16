package com.soltini.app.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * SoltiniAccessibilityService
 *
 * Full automation service that gives Soltini the ability to:
 *
 *  ┌── Global Actions ──────────────────────────────────────────────────────┐
 *  │  lockScreen()     — GLOBAL_ACTION_LOCK_SCREEN (Android 9+)            │
 *  │  pressBack()      — GLOBAL_ACTION_BACK                                │
 *  │  pressHome()      — GLOBAL_ACTION_HOME                                │
 *  │  pressRecents()   — GLOBAL_ACTION_RECENTS                             │
 *  └────────────────────────────────────────────────────────────────────────┘
 *  ┌── Screen Reading ───────────────────────────────────────────────────────┐
 *  │  readScreen()     — walks the a11y tree, returns all visible text      │
 *  │  findNodes(text)  — finds nodes whose text/desc matches a query        │
 *  └────────────────────────────────────────────────────────────────────────┘
 *  ┌── Click / Tap ──────────────────────────────────────────────────────────┐
 *  │  clickByText(text)     — perform ACTION_CLICK on matching node         │
 *  │  tapAt(x, y)           — dispatch a tap gesture at pixel coords        │
 *  └────────────────────────────────────────────────────────────────────────┘
 *  ┌── Typing ───────────────────────────────────────────────────────────────┐
 *  │  typeText(text)        — ACTION_SET_TEXT on focused / editable node    │
 *  │  clearText()           — clears the focused input field                │
 *  └────────────────────────────────────────────────────────────────────────┘
 *  ┌── Scroll ───────────────────────────────────────────────────────────────┐
 *  │  scrollDown() / scrollUp() — find first scrollable view and scroll     │
 *  └────────────────────────────────────────────────────────────────────────┘
 *
 * Requires the user to enable this service in:
 *   Settings → Accessibility → Soltini
 *
 * On Android 13+ sideloaded APKs:
 *   App Info → ⋮ → "Allow restricted settings" must be enabled first.
 *
 * ── Stability Design ──────────────────────────────────────────────────────
 * We deliberately NEVER modify serviceInfo at runtime. Previous code called
 * serviceInfo = info to toggle event types, which caused Android to treat the
 * service as unstable and auto-disable it when the app crashed.
 *
 * Instead, the XML config subscribes to a fixed minimal set of events and we
 * use an internal @Volatile flag (isToolCallActive) to gate whether we act on
 * those events. Zero risk of Android disabling the permission.
 */
class SoltiniAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SoltiniA11y"

        @SuppressLint("StaticFieldLeak")
        private var instance: SoltiniAccessibilityService? = null

        fun getInstance(): SoltiniAccessibilityService? = instance
        fun isActive(): Boolean = instance != null
    }

    /**
     * Set to true only while a Gemini tool call is actively executing.
     * Controls whether onAccessibilityEvent tracks editable nodes.
     *
     * @Volatile ensures cross-thread visibility without a full lock.
     * We NEVER touch serviceInfo — that caused the auto-disable bug.
     */
    @Volatile
    var isToolCallActive: Boolean = false
        set(value) {
            field = value
            Log.i(TAG, "isToolCallActive = $value")
        }

    /**
     * Lite Pause Mode flag.
     *
     * When true the accessibility service stays ENABLED in the Android system list
     * (so no user interaction is needed to resume), but:
     *   • onAccessibilityEvent() returns immediately without doing anything
     *   • All content/window queries return null / empty
     *
     * This makes Soltini functionally invisible to banking apps that check what
     * the accessibility service is actually reading. The service appears "on" but
     * is completely silent — meeting the spirit of most banking app checks which
     * look for services actively monitoring content (canRetrieveWindowContent
     * reads, active window captures, etc.).
     *
     * For stricter banking apps that check the raw enabled-services list,
     * use Hard Pause (disableSelf()) via enterHardPauseMode().
     */
    @Volatile
    var isPausedLite: Boolean = false
        set(value) {
            field = value
            Log.i(TAG, "Lite Pause = $value")
        }

    // Last focused editable node — updated only when a tool call is active
    private var lastFocusedEditable: AccessibilityNodeInfo? = null

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            ensureServicesRunning()
            watchdogHandler.postDelayed(this, 30_000L) // heartbeat every 30 seconds
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isToolCallActive = false
        Log.i(TAG, "Accessibility Service connected — ready for tool calls")

        // Revive background services immediately and keep watching
        ensureServicesRunning()
        watchdogHandler.removeCallbacks(watchdogRunnable)
        watchdogHandler.postDelayed(watchdogRunnable, 10_000L)
    }

    fun ensureServicesRunning() {
        if (isPausedLite || isAutomatingForceStop) return
        try {
            if (!com.soltini.app.services.BackgroundVoiceService.isRunning()) {
                Log.i(TAG, "Watchdog: BackgroundVoiceService is not running — reviving")
                val voiceIntent = Intent(this, com.soltini.app.services.BackgroundVoiceService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(voiceIntent)
                } else {
                    startService(voiceIntent)
                }
            }
            if (Settings.canDrawOverlays(this) && com.soltini.app.overlay.OverlayService.getInstance() == null) {
                Log.i(TAG, "Watchdog: OverlayService is not running — reviving")
                val overlayIntent = Intent(this, com.soltini.app.overlay.OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(overlayIntent)
                } else {
                    startService(overlayIntent)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Watchdog failed to ensure services running: ${e.message}")
        }
    }

    @Volatile
    var isAutomatingForceStop: Boolean = false

    private val appSettings by lazy { com.soltini.app.settings.AppSettings(this) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Lite Pause: service is alive but completely silent — return immediately
        if (isPausedLite) return
        try {
            event ?: return
            val eventType = event.eventType

            if (isAutomatingForceStop) {
                // We are looking for "Force stop" or "OK" buttons
                val root = rootInActiveWindow
                if (root != null) {
                    // Look for "Force stop"
                    var forceStopNode = findNodeByQuery(root, "force stop", requireClickable = true)
                    // Some UIs use "Force closed" or just "Force"
                    if (forceStopNode == null) forceStopNode = findNodeByQuery(root, "force", requireClickable = true)
                    
                    if (forceStopNode != null) {
                        Log.i(TAG, "Found Force Stop button, clicking it!")
                        forceStopNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        forceStopNode.recycle()
                        // Don't return yet, we might also see the OK button immediately on some UIs
                    }

                    // Look for confirmation button ("OK" or "Force stop" again)
                    val okNode = findNodeByQuery(root, "ok", requireClickable = true)
                    if (okNode != null) {
                        Log.i(TAG, "Found OK confirmation, clicking it!")
                        okNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        okNode.recycle()
                        isAutomatingForceStop = false // Automation complete
                    }
                    root.recycle()
                }
            }

            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val packageName = event.packageName?.toString()
                if (!packageName.isNullOrBlank()) {
                    if (appSettings.isBankingProtectionEnabled && appSettings.isBankingApp(packageName)) {
                        Log.i(TAG, "Banking app in foreground ($packageName) — hiding overlay & pausing A11y")
                        isToolCallActive = false
                        com.soltini.app.overlay.OverlayService.getInstance()?.setHiddenForBanking(true)
                    } else if (appSettings.isBankingProtectionEnabled) {
                        // Switched back to a normal app — restore overlay
                        com.soltini.app.overlay.OverlayService.getInstance()?.setHiddenForBanking(false)
                    }
                }
            }

            // Screen Companion Hook: Pass event for proactive human-like suggestions & companion awareness
            try {
                com.soltini.app.companion.ScreenCompanionManager.getInstance(applicationContext).onScreenEventDetected(event)
            } catch (t: Throwable) {
                Log.w(TAG, "Screen companion event hook error: ${t.message}")
            }

            // Always track focused or clicked editable views so typing tool knows the active field
            if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED || eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val node = event.source
                if (node != null && (node.isEditable || node.isFocused)) {
                    lastFocusedEditable?.recycle()
                    lastFocusedEditable = AccessibilityNodeInfo.obtain(node)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Suppressed crash in onAccessibilityEvent: ${t.message}")
        }
    }

    override fun onInterrupt() {
        // Wrap in try/catch — any uncaught exception here causes Android to mark
        // the service as crashed and may auto-disable the permission.
        try {
            Log.w(TAG, "Accessibility Service interrupted")
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        try {
            watchdogHandler.removeCallbacks(watchdogRunnable)
            // Clear instance BEFORE super so no in-flight tool call fires against
            // a destroyed service context.
            instance = null
            isToolCallActive = false
            lastFocusedEditable?.recycle()
            lastFocusedEditable = null
        } catch (_: Throwable) {}
        super.onDestroy()
        Log.i(TAG, "Accessibility Service destroyed")
    }

    // ─── Global Actions ───────────────────────────────────────────────────────

    fun lockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            Log.w(TAG, "Lock screen not supported below Android 9")
            false
        }
    }

    fun takeScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            Log.w(TAG, "Screenshot not supported below Android 9")
            false
        }
    }

    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun openPowerDialog(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

    fun toggleSplitScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        } else {
            false
        }
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /**
     * Checks whether an onscreen soft keyboard window is currently shown.
     */
    fun isSoftKeyboardVisible(): Boolean {
        return try {
            windows?.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Dismisses the onscreen soft keyboard if visible without leaving the current activity.
     */
    fun hideSoftKeyboard(): Boolean {
        return try {
            if (isSoftKeyboardVisible()) {
                pressBack()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // ─── Unified Root Retrieval ───────────────────────────────────────────────

    /**
     * Safely retrieves all available root nodes on the device.
     * Always inspects rootInActiveWindow first (which is 100% reliable on all Android versions
     * when an app is in foreground), and then complements it with any separate interactive windows.
     */
    fun getAllRootNodes(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        val seenWindowIds = mutableSetOf<Int>()

        // 1. Primary: rootInActiveWindow
        try {
            val active = rootInActiveWindow
            if (active != null) {
                roots.add(active)
                seenWindowIds.add(active.windowId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching rootInActiveWindow: ${e.message}")
        }

        // 2. Interactive windows (dialogs, popups, split-screen, keyboard)
        try {
            val wins = windows
            if (!wins.isNullOrEmpty()) {
                for (w in wins) {
                    if (!seenWindowIds.contains(w.id)) {
                        val r = w.root
                        if (r != null) {
                            roots.add(r)
                            seenWindowIds.add(w.id)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching windows: ${e.message}")
        }

        return roots
    }

    /**
     * Executes a block using all active root nodes and guarantees all roots are safely recycled afterwards.
     */
    inline fun <T> withRootNodes(block: (List<AccessibilityNodeInfo>) -> T): T {
        val roots = getAllRootNodes()
        return try {
            block(roots)
        } finally {
            for (root in roots) {
                try {
                    root.recycle()
                } catch (_: Exception) {}
            }
        }
    }

    // ─── Screen Reading ───────────────────────────────────────────────────────

    /**
     * Walks the full accessibility tree of all visible windows and collects
     * all visible text into a structured string.
     *
     * Clickable elements include their on-screen center coordinates in the format
     * @(cx,cy) so Gemini can pass those exact values to tap_screen().
     */
    fun readScreen(): String {
        val sb = StringBuilder()
        try {
            withRootNodes { roots ->
                for (root in roots) {
                    collectNodeText(root, sb, depth = 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "readScreen failed: ${e.message}")
            return "Error reading screen: ${e.message}"
        }
        val result = sb.toString().trim()
        return result.ifEmpty { "Screen appears empty or content is not accessible." }
    }

    private fun collectNodeText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth.coerceAtMost(8))

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val hint = node.hintText?.toString()?.trim()

        val displayed = when {
            !text.isNullOrEmpty() -> text
            !desc.isNullOrEmpty() -> "[$desc]"
            !hint.isNullOrEmpty() -> "(hint: $hint)"
            else -> null
        }

        if (displayed != null) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val cx = bounds.centerX()
            val cy = bounds.centerY()

            val interactMarker = when {
                node.isClickable && node.isEditable -> " ✏ tap@($cx,$cy)"
                node.isClickable                    -> " ◆ tap@($cx,$cy)"
                node.isEditable                    -> " ✏ tap@($cx,$cy)"
                else                               -> ""
            }
            sb.appendLine("$indent$displayed$interactMarker")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodeText(child, sb, depth + 1)
            child.recycle()
        }
    }

    // ─── Click / Tap ──────────────────────────────────────────────────────────

    fun clickByText(query: String): Boolean {
        if (query.isBlank()) return false
        val q = query.lowercase().trim()

        return withRootNodes { roots ->
            // FIX: two-pass search — prefer nodes whose actual visible TEXT matches (e.g. the
            // contact's name label), and only fall back to contentDescription/hint/resId matches
            // if no text match exists. Without this, clicking a contact by name after searching
            // could match their profile-picture ImageView instead — WhatsApp sets the avatar's
            // contentDescription to the contact's display name for accessibility, and that
            // ImageView sits before the name TextView in the row, so the old single-pass
            // depth-first search would hit the DP first and open the contact's profile info
            // screen instead of the chat (with no message box, so typing afterward would fail).
            for (root in roots) {
                val textMatch = findNodeByQuery(root, q, requireClickable = false, excludeEditable = true, textOnly = true)
                if (textMatch != null) {
                    val clicked = clickNodeOrParentOrTap(textMatch, query)
                    if (clicked) return@withRootNodes true
                }
            }
            for (root in roots) {
                // Search with requireClickable = false so we find text inside buttons/containers.
                val match = findNodeByQuery(root, q, requireClickable = false, excludeEditable = true)
                if (match != null) {
                    val clicked = clickNodeOrParentOrTap(match, query)
                    if (clicked) return@withRootNodes true
                }
            }
            Log.w(TAG, "clickByText('$query') -> no matching node found or clicked")
            false
        }
    }

    /**
     * Shared click-fallback chain: direct click -> clickable parent -> physical coordinate tap.
     * Recycles [node] in every path. Extracted so both the text-preferring pass and the
     * fallback pass in clickByText() share identical, tested click behavior.
     */
    private fun clickNodeOrParentOrTap(node: AccessibilityNodeInfo, query: String): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            node.recycle()
            Log.i(TAG, "clickByText('$query') -> direct click succeeded")
            return true
        }

        val parent = findClickableParent(node)
        if (parent != null) {
            val parentClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            parent.recycle()
            if (parentClicked) {
                node.recycle()
                Log.i(TAG, "clickByText('$query') -> parent click succeeded")
                return true
            }
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        node.recycle()
        if (bounds.width() > 0 && bounds.height() > 0) {
            val tapped = tapAt(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            Log.i(TAG, "clickByText('$query') -> physical tap fallback at (${bounds.centerX()}, ${bounds.centerY()}) = $tapped")
            return tapped
        }
        return false
    }

    fun tapAt(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "tapAt requires Android 7+")
            return false
        }
        return try {
            Log.i(TAG, "tapAt dispatching gesture at raw screen coords ($x, $y)")
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val dispatched = dispatchGesture(gesture, null, null)
            Log.i(TAG, "tapAt dispatchGesture returned: $dispatched")
            dispatched
        } catch (e: Exception) {
            Log.e(TAG, "tapAt($x,$y) failed: ${e.message}")
            false
        }
    }

    // ─── Phase 2 primitives (exact-text click, view-id lookup, long press) ───────

    /** Long-press gesture at raw screen coordinates. */
    fun longPressAt(x: Float, y: Float, durationMs: Long = 800L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "longPressAt failed: ${e.message}")
            false
        }
    }

    private fun findExactNode(node: AccessibilityNodeInfo, wanted: List<String>): AccessibilityNodeInfo? {
        val t = node.text?.toString()?.trim()?.lowercase()
        val d = node.contentDescription?.toString()?.trim()?.lowercase()
        if (node.isVisibleToUser && ((t != null && t in wanted) || (d != null && d in wanted))) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findExactNode(child, wanted)
            try { child.recycle() } catch (_: Exception) {}
            if (found != null) return found
        }
        return null
    }

    /**
     * Clicks the first visible node whose text or content-description EQUALS one of [labels]
     * (case-insensitive). Safer than [clickByText] for short words like "Install", "OK", "Send".
     */
    fun clickExactText(labels: List<String>): Boolean {
        val wanted = labels.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return false
        return withRootNodes { roots ->
            for (root in roots) {
                val node = findExactNode(root, wanted)
                if (node != null) return@withRootNodes clickNodeOrParentOrTap(node, wanted.first())
            }
            false
        }
    }

    /** Finds nodes by view-id resource name (e.g. "com.whatsapp:id/send") across all windows. Caller owns the nodes. */
    fun findNodesByViewId(ids: List<String>): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        val roots = getAllRootNodes()
        for (r in roots) {
            for (id in ids) {
                try { out.addAll(r.findAccessibilityNodeInfosByViewId(id)) } catch (_: Exception) {}
            }
            try { r.recycle() } catch (_: Exception) {}
        }
        return out
    }

    /** Clicks [node] (or its clickable parent, or taps its centre). Recycles [node]. */
    fun clickNode(node: AccessibilityNodeInfo): Boolean = clickNodeOrParentOrTap(node, "node")

    /** Long-presses [node] (ACTION_LONG_CLICK, falling back to a gesture). Recycles [node]. */
    fun longPressNode(node: AccessibilityNodeInfo): Boolean {
        if (node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            try { node.recycle() } catch (_: Exception) {}
            return true
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        try { node.recycle() } catch (_: Exception) {}
        return r.width() > 0 && r.height() > 0 && longPressAt(r.centerX().toFloat(), r.centerY().toFloat())
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val dispatched = dispatchGesture(gesture, null, null)
            Log.i(TAG, "swipe dispatchGesture returned: $dispatched")
            dispatched
        } catch (e: Exception) {
            Log.e(TAG, "swipe failed: ${e.message}")
            false
        }
    }

    // ─── Typing ───────────────────────────────────────────────────────────────

    /** Result of a typing attempt: [ok] = some action was accepted, [verified] = text was read back from the field. */
    data class TypeOutcome(val ok: Boolean, val verified: Boolean, val fieldText: String = "")

    private fun setClipboardText(text: String) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("soltini_type", text))
            } else {
                val latch = java.util.concurrent.CountDownLatch(1)
                Handler(Looper.getMainLooper()).post {
                    try {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("soltini_type", text))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to set clipboard on main thread: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
                latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard set error: ${e.message}")
        }
    }

    /** Reads the REAL text currently in a field (refreshes the node; ignores hint/placeholder text). */
    fun readFieldText(node: AccessibilityNodeInfo): String {
        return try {
            node.refresh()
            if (node.isPassword) return "(hidden)"
            val showingHint = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && node.isShowingHintText
            if (showingHint) "" else (node.text?.toString() ?: "")
        } catch (_: Exception) { "" }
    }

    private fun readbackMatches(node: AccessibilityNodeInfo, expected: String): Boolean {
        return try {
            node.refresh()
            if (node.isPassword) return true // masked field: cannot be read back, trust the action
            val norm = { v: String -> v.replace(Regex("\\s+"), " ").trim() }
            val current = norm(readFieldText(node))
            val want = norm(expected)
            if (want.isEmpty()) current.isEmpty()
            else current.equals(want, ignoreCase = true) || current.contains(want, ignoreCase = true)
        } catch (_: Exception) { false }
    }

    /**
     * Puts [text] into [node] and CHECKS that it really landed.
     *  Tier 1: ACTION_SET_TEXT (+ read-back)
     *  Tier 2: tap the field to focus it, ACTION_SET_TEXT again (+ read-back)
     *  Tier 3: clipboard + select-all + ACTION_PASTE (+ read-back)
     * Returns 2 = verified, 1 = an action was accepted but the text could not be confirmed, 0 = failed.
     */
    fun inputWithVerification(node: AccessibilityNodeInfo, text: String): Int {
        var accepted = false
        try {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        } catch (_: Exception) {}

        fun setTextAction(): Boolean {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return try { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) } catch (_: Exception) { false }
        }

        // Tier 1
        if (setTextAction()) {
            accepted = true
            Thread.sleep(90)
            if (readbackMatches(node, text)) {
                Log.i(TAG, "input: Tier1 SET_TEXT verified")
                return 2
            }
            Log.w(TAG, "input: SET_TEXT returned true but text not visible in field - trying next tier")
        }

        // Tier 2: tap to focus, then SET_TEXT again
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (node.isVisibleToUser && bounds.width() > 0 && bounds.height() > 0) {
                tapAt(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                Thread.sleep(220)
                if (setTextAction()) {
                    accepted = true
                    Thread.sleep(90)
                    if (readbackMatches(node, text)) {
                        Log.i(TAG, "input: Tier2 tap+SET_TEXT verified")
                        return 2
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "input: Tier2 error: ${e.message}")
        }

        // Tier 3: clipboard paste (replace existing content)
        if (text.isNotEmpty()) {
            try {
                setClipboardText(text)
                val existing = readFieldText(node)
                if (existing.isNotEmpty()) {
                    val sel = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, existing.length)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
                }
                val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                if (pasted) accepted = true
                Thread.sleep(160)
                if (readbackMatches(node, text)) {
                    Log.i(TAG, "input: Tier3 PASTE verified")
                    return 2
                }
                Log.w(TAG, "input: PASTE result=$pasted, not verified")
            } catch (e: Exception) {
                Log.w(TAG, "input: Tier3 error: ${e.message}")
            }
        }
        return if (accepted) 1 else 0
    }

    /**
     * Backward-compatible: true when any tier was accepted. Use [inputWithVerification]
     * when you need to know whether the text really appeared.
     */
    fun setTextOrPaste(node: AccessibilityNodeInfo, text: String): Boolean =
        inputWithVerification(node, text) > 0

    /**
     * Submits or presses the Enter / Search key on the onscreen keyboard or focused node.
     */
    fun pressImeEnter(node: AccessibilityNodeInfo? = null): Boolean {
        try {
            // 1. Try ACTION_IME_ENTER on provided node
            if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val ok = node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                if (ok) {
                    Log.i(TAG, "pressImeEnter: ACTION_IME_ENTER succeeded on provided node")
                    return true
                }
            }

            // 2. Try ACTION_IME_ENTER on focused input
            withRootNodes { roots ->
                for (root in roots) {
                    val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (focused != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val ok = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                            focused.recycle()
                            if (ok) {
                                Log.i(TAG, "pressImeEnter: ACTION_IME_ENTER succeeded on focused input")
                                return@withRootNodes true
                            }
                        } else {
                            focused.recycle()
                        }
                    }
                }
                false
            }

            // 3. Physical tap on bottom-right corner where Enter/Search key is on standard soft keyboard
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val enterX = metrics.widthPixels * 0.90f
            val enterY = metrics.heightPixels * 0.94f
            Log.i(TAG, "pressImeEnter: Tapping soft keyboard bottom-right search/enter key at ($enterX, $enterY)")
            return tapAt(enterX, enterY)
        } catch (e: Exception) {
            Log.e(TAG, "pressImeEnter failed: ${e.message}")
            return false
        }
    }

    /**
     * Types [text] into the focused / last-focused / first editable field and reports
     * whether it was actually confirmed in the field (read-back), so callers never
     * claim "typed" when the text is only drafted.
     */
    fun typeTextDetailed(text: String): TypeOutcome {
        try {
            return withRootNodes { roots ->
                var unverified: TypeOutcome? = null

                fun attempt(node: AccessibilityNodeInfo): TypeOutcome? {
                    val r = inputWithVerification(node, text)
                    return when (r) {
                        2 -> TypeOutcome(true, true, readFieldText(node))
                        1 -> TypeOutcome(true, false, readFieldText(node))
                        else -> null
                    }
                }

                // 1. Focused input / accessibility-focused node across windows
                for (root in roots) {
                    val focusedInput = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (focusedInput != null) {
                        val o = attempt(focusedInput)
                        focusedInput.recycle()
                        if (o != null) { if (o.verified) return@withRootNodes o else if (unverified == null) unverified = o }
                    }
                    val a11yFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                    if (a11yFocused != null && (a11yFocused.isEditable || a11yFocused.className?.toString()?.contains("EditText", true) == true)) {
                        val o = attempt(a11yFocused)
                        a11yFocused.recycle()
                        if (o != null) { if (o.verified) return@withRootNodes o else if (unverified == null) unverified = o }
                    }
                }
                if (unverified != null) return@withRootNodes unverified!!

                // 2. Cached last focused editable
                val cached = lastFocusedEditable
                if (cached != null) {
                    try {
                        cached.refresh()
                        val o = attempt(cached)
                        if (o != null) return@withRootNodes o
                    } catch (_: Exception) {
                        lastFocusedEditable = null
                    }
                }

                // 3. Any editable field on screen
                for (root in roots) {
                    val editable = findFirstEditable(root)
                    if (editable != null) {
                        val o = attempt(editable)
                        editable.recycle()
                        if (o != null) return@withRootNodes o
                    }
                }

                Log.w(TAG, "typeText: no editable field found on screen")
                TypeOutcome(false, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "typeText failed: ${e.message}")
            return TypeOutcome(false, false)
        }
    }

    fun typeText(text: String): Boolean = typeTextDetailed(text).ok

    fun clearText(): Boolean = typeText("")

    // ─── Scroll ───────────────────────────────────────────────────────────────

    fun scrollDown(): Boolean = scroll(forward = true)

    fun scrollUp(): Boolean = scroll(forward = false)

    private fun scroll(forward: Boolean): Boolean {
        val action = if (forward)
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD

        val scrolled = withRootNodes { roots ->
            for (root in roots) {
                val scrollable = findFirstScrollable(root)
                if (scrollable != null) {
                    val success = scrollable.performAction(action)
                    scrollable.recycle()
                    if (success) {
                        Log.i(TAG, "scroll(forward=$forward) via a11y action -> true")
                        return@withRootNodes true
                    }
                }
            }
            false
        }

        if (scrolled) return true

        // Fallback to gesture swipe
        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val cx = metrics.widthPixels / 2f
            val startY = if (forward) metrics.heightPixels * 0.75f else metrics.heightPixels * 0.25f
            val endY = if (forward) metrics.heightPixels * 0.25f else metrics.heightPixels * 0.75f
            swipe(cx, startY, cx, endY, 350)
        } catch (e: Exception) {
            Log.e(TAG, "Gesture swipe fallback failed: ${e.message}")
            false
        }
    }

    // ─── Node Search Helpers ──────────────────────────────────────────────────

    private fun findNodeByQuery(
        node: AccessibilityNodeInfo,
        query: String,
        requireClickable: Boolean,
        excludeEditable: Boolean = false,
        textOnly: Boolean = false
    ): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val hint = node.hintText?.toString()?.lowercase() ?: ""
        val resId = node.viewIdResourceName?.lowercase() ?: ""
        // FIX: when textOnly is set, only the node's actual visible `text` counts as a match —
        // an ImageView's contentDescription (e.g. a WhatsApp avatar labeled with the contact's
        // name for screen readers) is deliberately ignored in this pass, so a real text label
        // is always preferred over an icon that merely happens to share the same description.
        val matches = if (textOnly) {
            text.contains(query)
        } else {
            text.contains(query) || desc.contains(query) || hint.contains(query) || resId.contains(query)
        }
        val clickOk = !requireClickable || node.isClickable
        // FIX: when excludeEditable is set, skip EditText/editable nodes — these are input
        // fields, not tappable targets, and matching them by their current typed-in text was
        // causing clickByText() to click back into a search box instead of the intended result.
        val editableOk = !excludeEditable || !node.isEditable

        if (matches && clickOk && editableOk) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByQuery(child, query, requireClickable, excludeEditable, textOnly)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    /**
     * Returns true if the current screen has a visible editable text field (e.g. a message
     * input box). Used to verify a chat screen actually opened before typing/sending continue —
     * e.g. after tapping a WhatsApp search result, this confirms the chat (not a profile/info
     * screen) is what's on screen now.
     */
    fun hasEditableField(): Boolean {
        return withRootNodes { roots ->
            for (root in roots) {
                val editable = findFirstEditable(root)
                if (editable != null) {
                    editable.recycle()
                    return@withRootNodes true
                }
            }
            false
        }
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {        if (node.isEditable || node.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstScrollable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    /**
     * Searches for incoming call answer or decline buttons on the active screen and performs click.
     */
    fun clickCallButton(isAnswer: Boolean): Boolean {
        val keywords = if (isAnswer) {
            listOf("answer", "accept", "receive", "pick up", "attend", "uthao")
        } else {
            listOf("decline", "reject", "dismiss", "end call", "hang up", "kaat")
        }
        return withRootNodes { roots ->
            for (root in roots) {
                for (kw in keywords) {
                    val node = findNodeByQuery(root, kw, requireClickable = false)
                    if (node != null) {
                        val clickableNode = if (node.isClickable) node else findClickableParent(node) ?: node
                        var clicked = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (!clicked) {
                            val bounds = Rect()
                            clickableNode.getBoundsInScreen(bounds)
                            if (bounds.width() > 0 && bounds.height() > 0) {
                                clicked = tapAt(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                            }
                        }
                        clickableNode.recycle()
                        Log.i(TAG, "clickCallButton(isAnswer=$isAnswer, keyword=$kw) -> $clicked")
                        if (clicked) return@withRootNodes true
                    }
                }
            }
            false
        }
    }

    fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) return parent
            val nextParent = parent.parent
            parent.recycle()
            parent = nextParent
        }
        return null
    }

    // ─── Realtime Screen Capture ──────────────────────────────────────────────

    /**
     * Captures a software Bitmap screenshot of the device screen using Android 11+ (API 30+)
     * AccessibilityService.takeScreenshot API.
     * Returns null if API < 30 or if screenshot fails / times out.
     */
    suspend fun takeScreenshotBitmap(): Bitmap? = withTimeoutOrNull(2500L) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "takeScreenshot requires Android 11 (API 30)+")
            return@withTimeoutOrNull null
        }

        suspendCancellableCoroutine { cont ->
            try {
                val executor = Executors.newSingleThreadExecutor()
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                            try {
                                val hwBuffer = screenshotResult.hardwareBuffer
                                val colorSpace = screenshotResult.colorSpace
                                val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                                val swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                hwBuffer.close()
                                executor.shutdown()
                                if (cont.isActive) cont.resume(swBitmap)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed converting screenshot buffer: ${e.message}")
                                executor.shutdown()
                                if (cont.isActive) cont.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "takeScreenshot failed with errorCode: $errorCode")
                            executor.shutdown()
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "takeScreenshot exception: ${e.message}")
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    // ─── Universal Form Automation Helpers ─────────────────────────────────────

    fun getScreenAnalyzer(): ScreenAnalyzer = ScreenAnalyzer(this)

    fun getFormAutomator(): FormAutomator = FormAutomator(this)

    /**
     * Attempts to fill [text] into [node] using ACTION_SET_TEXT or clipboard paste,
     * and performs readback verification to ensure the text actually registered.
     */
    fun setTextWithVerification(node: AccessibilityNodeInfo, text: String): Boolean {
        val success = setTextOrPaste(node, text)
        if (!success) return false
        val current = node.text?.toString() ?: ""
        return current.equals(text, ignoreCase = true) || current.contains(text, ignoreCase = true)
    }

    /**
     * Clears all content from an editable field.
     */
    fun clearField(node: AccessibilityNodeInfo): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (_: Exception) {
            false
        }
    }
}
