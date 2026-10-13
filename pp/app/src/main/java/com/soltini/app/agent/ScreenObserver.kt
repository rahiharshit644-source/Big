package com.soltini.app.agent

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

/**
 * ScreenObserver - gives the agent eyes.
 *
 *  - [signature]: cheap fingerprint to prove whether an action changed the screen
 *  - [digest]: compact numbered list of what is tappable / typeable / readable right now
 *  - [screenshotBase64]: real pixels for apps whose UI is not exposed to accessibility
 *  - [waitForSettle]: waits until animations / loading stop before the next look
 */
object ScreenObserver {

    private const val MAX_NODES = 450

    private fun <T> withRoots(block: (List<AccessibilityNodeInfo>) -> T): T? {
        val svc = SoltiniAccessibilityService.getInstance() ?: return null
        return svc.withRootNodes(block)
    }

    fun isAvailable(): Boolean = SoltiniAccessibilityService.getInstance() != null

    fun foregroundPackage(): String =
        withRoots { roots -> roots.firstOrNull()?.packageName?.toString().orEmpty() } ?: ""

    fun signature(): ScreenSignature {
        return withRoots { roots ->
            var pkg = ""
            var cls = ""
            var count = 0
            var hash = 1125899906842597L
            val texts = ArrayList<String>()
            val rect = Rect()

            fun walk(n: AccessibilityNodeInfo, depth: Int) {
                if (count >= MAX_NODES || depth > 40) return
                if (!n.isVisibleToUser) return
                count++
                val t = n.text?.toString()?.trim().orEmpty()
                val d = n.contentDescription?.toString()?.trim().orEmpty()
                n.getBoundsInScreen(rect)
                if (t.isNotEmpty() && texts.size < 15) texts.add(t)
                else if (d.isNotEmpty() && texts.size < 15) texts.add(d)
                hash = 31 * hash + t.hashCode()
                hash = 31 * hash + d.hashCode()
                hash = 31 * hash + (n.viewIdResourceName?.hashCode() ?: 0)
                hash = 31 * hash + rect.centerX()
                hash = 31 * hash + rect.centerY()
                if (n.isClickable) hash = hash * 31 + 1
                for (i in 0 until n.childCount) {
                    val c = n.getChild(i) ?: continue
                    walk(c, depth + 1)
                    try { c.recycle() } catch (_: Exception) {}
                }
            }

            for ((i, r) in roots.withIndex()) {
                if (i == 0) {
                    pkg = r.packageName?.toString().orEmpty()
                    cls = r.className?.toString().orEmpty()
                }
                walk(r, 0)
            }
            ScreenSignature(pkg, cls, count, hash, texts)
        } ?: ScreenSignature("", "", 0, 0L, emptyList())
    }

    fun digest(maxInteractive: Int = 50, maxText: Int = 35): ScreenDigest {
        val lines = ArrayList<String>()
        val textOnly = LinkedHashSet<String>()
        var pkg = ""
        var total = 0

        withRoots { roots ->
            pkg = roots.firstOrNull()?.packageName?.toString().orEmpty()
            val rect = Rect()

            fun walk(n: AccessibilityNodeInfo, depth: Int) {
                if (depth > 40 || total > 700) return
                if (!n.isVisibleToUser) return
                total++
                val t = n.text?.toString()?.trim().orEmpty().replace("\n", " ")
                val d = n.contentDescription?.toString()?.trim().orEmpty().replace("\n", " ")
                var label = (if (t.isNotEmpty()) t else d).take(70)
                if (label.isEmpty() && n.isEditable) label = n.hintText?.toString().orEmpty().take(50)
                val interactive = n.isClickable || n.isEditable || n.isCheckable || n.isScrollable
                if (interactive && (label.isNotEmpty() || n.isEditable || n.isScrollable) && lines.size < maxInteractive) {
                    n.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        val kind = when {
                            n.isEditable -> "input"
                            n.isCheckable -> if (n.isChecked) "checkbox:on" else "checkbox:off"
                            n.isScrollable && !n.isClickable -> "scroll-area"
                            else -> "button"
                        }
                        lines.add("[${lines.size + 1}] $kind \"$label\" @(${rect.centerX()},${rect.centerY()})")
                    }
                } else if (label.isNotEmpty() && textOnly.size < maxText) {
                    textOnly.add(label)
                }
                for (i in 0 until n.childCount) {
                    val c = n.getChild(i) ?: continue
                    walk(c, depth + 1)
                    try { c.recycle() } catch (_: Exception) {}
                }
            }
            roots.forEach { walk(it, 0) }
        }

        val sb = StringBuilder()
        sb.append("APP: ").append(pkg.ifEmpty { "unknown" }).append('\n')
        if (lines.isEmpty()) {
            sb.append("(no interactive elements exposed - this may be a game, video, canvas or a loading screen; use look_at_screen)\n")
        } else {
            sb.append("INTERACTIVE:\n")
            lines.forEach { sb.append(it).append('\n') }
        }
        if (textOnly.isNotEmpty()) sb.append("TEXT: ").append(textOnly.joinToString(" | ")).append('\n')
        return ScreenDigest(sb.toString().take(7000), pkg, lines.size)
    }

    /** True if any visible node's text/description contains [needle] (case-insensitive). */
    fun containsText(needle: String): Boolean {
        val q = needle.trim().lowercase()
        if (q.isEmpty()) return false
        return withRoots { roots ->
            var found = false
            fun walk(n: AccessibilityNodeInfo, depth: Int) {
                if (found || depth > 40) return
                val t = n.text?.toString()?.lowercase().orEmpty()
                val d = n.contentDescription?.toString()?.lowercase().orEmpty()
                if (t.contains(q) || d.contains(q)) { found = true; return }
                for (i in 0 until n.childCount) {
                    val c = n.getChild(i) ?: continue
                    walk(c, depth + 1)
                    try { c.recycle() } catch (_: Exception) {}
                    if (found) return
                }
            }
            roots.forEach { walk(it, 0) }
            found
        } ?: false
    }

    /** Waits until the screen stops changing (or [maxMs] passes) and returns the final signature. */
    suspend fun waitForSettle(maxMs: Long = 2000, stepMs: Long = 250): ScreenSignature {
        val end = System.currentTimeMillis() + maxMs
        var prev = signature()
        while (System.currentTimeMillis() < end) {
            delay(stepMs)
            val cur = signature()
            if (cur.sameAs(prev)) return cur
            prev = cur
        }
        return prev
    }

    /** Down-scaled JPEG of the current screen as base64, or null if unavailable (needs Android 11+). */
    suspend fun screenshotBase64(maxWidth: Int = 720, quality: Int = 60): String? {
        val svc = SoltiniAccessibilityService.getInstance() ?: return null
        return try {
            val bmp: Bitmap = svc.takeScreenshotBitmap() ?: return null
            val scaled = if (bmp.width > maxWidth) {
                Bitmap.createScaledBitmap(bmp, maxWidth, (bmp.height * maxWidth.toFloat() / bmp.width).toInt(), true)
            } else bmp
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
}
