package com.soltini.app.agent

import android.os.SystemClock

/**
 * UiWait - "wait until the screen is ready" helpers that replace fixed Thread.sleep() calls.
 *
 * A fixed sleep is wrong both ways: on a slow phone it is too short (the next tap hits a screen that is not
 * there yet), on a fast phone it wastes seconds. These helpers poll the real UI and continue the moment
 * the condition is true, and give up after a timeout so a task can never hang.
 *
 * All functions BLOCK the calling thread, so call them from a background thread (as the automators already do).
 */
object UiWait {

    private const val DEFAULT_STEP_MS = 120L

    /** Polls [cond] until it is true or [timeoutMs] passed. Returns the final truth value. */
    fun until(timeoutMs: Long, stepMs: Long = DEFAULT_STEP_MS, cond: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val ok = try { cond() } catch (_: Exception) { false }
            if (ok) return true
            if (SystemClock.elapsedRealtime() >= end) return false
            try { Thread.sleep(stepMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); return false }
        }
    }

    /** Waits until the screen stops changing for [quietMs] (animations / loading finished). */
    fun settle(maxMs: Long = 1500, quietMs: Long = 250, stepMs: Long = 80): Boolean {
        if (!ScreenObserver.isAvailable()) return false
        val end = SystemClock.elapsedRealtime() + maxMs
        var prev = ScreenObserver.signature()
        var stableSince = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() < end) {
            try { Thread.sleep(stepMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); return false }
            val cur = ScreenObserver.signature()
            val now = SystemClock.elapsedRealtime()
            if (cur.sameAs(prev)) {
                if (now - stableSince >= quietMs) return true
            } else {
                prev = cur
                stableSince = now
            }
        }
        return false
    }

    /** Waits until [pkg] is the app in front. */
    @Suppress("UNUSED_PARAMETER")
    fun foreground(svc: SoltiniAccessibilityService, pkg: String, timeoutMs: Long = 6000): Boolean =
        until(timeoutMs) { ScreenObserver.foregroundPackage() == pkg }

    /** Waits until [needle] is visible on screen (text or content description). */
    fun textVisible(svc: SoltiniAccessibilityService, needle: String, timeoutMs: Long = 3000): Boolean =
        until(timeoutMs) { svc.readScreen().contains(needle, ignoreCase = true) }

    /** Clicks [label] as soon as it exists. */
    fun clickWhenReady(svc: SoltiniAccessibilityService, label: String, timeoutMs: Long = 4000): Boolean =
        until(timeoutMs) { svc.clickByText(label) }

    /** Clicks the first label of [labels] that appears. */
    fun clickAnyWhenReady(svc: SoltiniAccessibilityService, labels: List<String>, timeoutMs: Long = 4000): Boolean =
        until(timeoutMs) { labels.any { svc.clickByText(it) } }

    /** Types [text] as soon as an input field accepts it. */
    fun typeWhenReady(svc: SoltiniAccessibilityService, text: String, timeoutMs: Long = 3000): Boolean =
        until(timeoutMs) { svc.typeText(text) }

    /** Waits until a text box exists on screen (chat opened, compose opened ...). */
    fun editableReady(svc: SoltiniAccessibilityService, timeoutMs: Long = 4000): Boolean =
        until(timeoutMs) { svc.hasEditableField() }

    /**
     * Drop-in replacement for a fixed "sleep(ms)" after an action: returns as soon as the screen has
     * stopped changing (usually much sooner than [ms]) but waits up to ~3x [ms] on a slow phone.
     * Without accessibility info it falls back to a plain sleep of [ms].
     */
    fun pause(ms: Long) {
        if (!ScreenObserver.isAvailable()) {
            try { Thread.sleep(ms) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            return
        }
        settle(maxMs = (ms * 3).coerceIn(300, 1800), quietMs = (ms / 2).coerceIn(80, 200))
    }
}
