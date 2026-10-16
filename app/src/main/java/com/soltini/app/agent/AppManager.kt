package com.soltini.app.agent

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * AppManager - universal app control.
 *
 *  - openApp: opens ANY installed app by name, alias, package or even a website; if the app
 *    is not installed it takes the user to Google Play and taps "Install".
 *  - installApp / uninstallApp / listApps / openAppSettings.
 *
 * Fuzzy name matching and the popular-app alias map are ported from the Jarvis project.
 * Blocking (uses short sleeps while driving Play Store / system dialogs): call off the main thread.
 */
class AppManager(private val context: Context) {

    companion object {
        private const val TAG = "AppManager"
        private const val PLAY_STORE_PKG = "com.android.vending"

        private val POPULAR_APP_PACKAGES = mapOf(
                // Social Media
                "instagram" to "com.instagram.android",
                "insta" to "com.instagram.android",
                "whatsapp" to "com.whatsapp",
                "whats app" to "com.whatsapp",
                "facebook" to "com.facebook.katana",
                "fb" to "com.facebook.katana",
                "messenger" to "com.facebook.orca",
                "fb messenger" to "com.facebook.orca",
                "twitter" to "com.twitter.android",
                "x" to "com.twitter.android",
                "snapchat" to "com.snapchat.android",
                "snap" to "com.snapchat.android",
                "telegram" to "org.telegram.messenger",
                "tg" to "org.telegram.messenger",
                "linkedin" to "com.linkedin.android",
                "pinterest" to "com.pinterest",
                "reddit" to "com.reddit.frontpage",
                "threads" to "com.instagram.barcelona",

                // AI Apps
                "chatgpt" to "com.openai.chatgpt",
                "chat gpt" to "com.openai.chatgpt",
                "openai" to "com.openai.chatgpt",
                "gemini" to "com.google.android.apps.bard",
                "google gemini" to "com.google.android.apps.bard",
                "bard" to "com.google.android.apps.bard",
                "canva" to "com.canva.editor",
                "copilot" to "com.microsoft.copilot",
                "perplexity" to "ai.perplexity.app.android",

                // Google Apps
                "chrome" to "com.android.chrome",
                "google chrome" to "com.android.chrome",
                "browser" to "com.android.chrome",
                "gmail" to "com.google.android.gm",
                "mail" to "com.google.android.gm",
                "email" to "com.google.android.gm",
                "google maps" to "com.google.android.apps.maps",
                "maps" to "com.google.android.apps.maps",
                "map" to "com.google.android.apps.maps",
                "google pay" to "com.google.android.apps.nbu.paisa.user",
                "google pe" to "com.google.android.apps.nbu.paisa.user",
                "gpay" to "com.google.android.apps.nbu.paisa.user",
                "g pay" to "com.google.android.apps.nbu.paisa.user",
                "gpe" to "com.google.android.apps.nbu.paisa.user",
                "gp" to "com.google.android.apps.nbu.paisa.user",
                "google play" to "com.google.android.apps.nbu.paisa.user",
                "pay" to "com.google.android.apps.nbu.paisa.user",
                "youtube" to "com.google.android.youtube",
                "yt" to "com.google.android.youtube",
                "youtube music" to "com.google.android.apps.youtube.music",
                "yt music" to "com.google.android.apps.youtube.music",
                "google photos" to "com.google.android.apps.photos",
                "photos" to "com.google.android.apps.photos",
                "google drive" to "com.google.android.apps.docs",
                "drive" to "com.google.android.apps.docs",
                "google docs" to "com.google.android.apps.docs.editors.docs",
                "docs" to "com.google.android.apps.docs.editors.docs",
                "google sheets" to "com.google.android.apps.docs.editors.sheets",
                "sheets" to "com.google.android.apps.docs.editors.sheets",
                "google slides" to "com.google.android.apps.docs.editors.slides",
                "slides" to "com.google.android.apps.docs.editors.slides",
                "google translate" to "com.google.android.apps.translate",
                "translate" to "com.google.android.apps.translate",
                "google meet" to "com.google.android.apps.tachyon",
                "meet" to "com.google.android.apps.tachyon",
                "google lens" to "com.google.ar.lens",
                "lens" to "com.google.ar.lens",
                "google calendar" to "com.google.android.calendar",
                "calendar" to "com.google.android.calendar",
                "google keep" to "com.google.android.keep",
                "keep" to "com.google.android.keep",
                "notes" to "com.google.android.keep",
                "google assistant" to "com.google.android.apps.googleassistant",
                "assistant" to "com.google.android.apps.googleassistant",
                "playstore" to "com.android.vending",
                "play store" to "com.android.vending",
                "google play store" to "com.android.vending",
                "app store" to "com.android.vending",

                // Entertainment
                "spotify" to "com.spotify.music",
                "netflix" to "com.netflix.mediaclient",
                "amazon prime" to "com.amazon.avod.thirdpartyclient",
                "prime video" to "com.amazon.avod.thirdpartyclient",
                "prime" to "com.amazon.avod.thirdpartyclient",
                "hotstar" to "in.startv.hotstar",
                "disney hotstar" to "in.startv.hotstar",
                "jio cinema" to "com.jio.media.ondemand",
                "jiocinema" to "com.jio.media.ondemand",
                "zee5" to "com.graymatrix.did",
                "sony liv" to "com.sonyliv",

                // Finance & Payments
                "phonepe" to "com.phonepe.app",
                "phone pe" to "com.phonepe.app",
                "paytm" to "net.one97.paytm",
                "amazon" to "in.amazon.mShop.android.shopping",
                "amazon shopping" to "in.amazon.mShop.android.shopping",
                "flipkart" to "com.flipkart.android",
                "cred" to "com.dreamplug.androidapp",
                "myntra" to "com.myntra.android",

                // Productivity
                "calculator" to "com.google.android.calculator",
                "calc" to "com.google.android.calculator",
                "clock" to "com.google.android.deskclock",
                "alarm" to "com.google.android.deskclock",
                "timer" to "com.google.android.deskclock",
                "files" to "com.google.android.apps.nbu.files",
                "file manager" to "com.google.android.apps.nbu.files",
                "contacts" to "com.google.android.contacts",
                "dialer" to "com.google.android.dialer",
                "phone" to "com.google.android.dialer",

                // Communication
                "zoom" to "us.zoom.videomeetings",
                "teams" to "com.microsoft.teams",
                "microsoft teams" to "com.microsoft.teams",
                "skype" to "com.skype.raider",
                "signal" to "org.thoughtcrime.securesms",

                // Games
                "pubg" to "com.pubg.imobile",
                "bgmi" to "com.pubg.imobile",
                "free fire" to "com.dts.freefireth",
                "call of duty" to "com.activision.callofduty.shooter",
                "cod" to "com.activision.callofduty.shooter",

                // Utility
                "uber" to "com.ubercab",
                "ola" to "com.olacabs.customer",
                "swiggy" to "in.swiggy.android",
                "zomato" to "com.application.zomato",
                "weather" to "com.google.android.apps.weather"
        )
    }

    private data class AppEntry(val label: String, val pkg: String)

    private val pm: PackageManager get() = context.packageManager

    // ───────────────────────── Helpers ─────────────────────────

    private fun ok(vararg pairs: Any): JSONObject = JSONObject().apply {
        var i = 0
        while (i < pairs.size - 1) { put(pairs[i].toString(), pairs[i + 1]); i += 2 }
    }

    private fun err(msg: String): JSONObject = JSONObject().apply {
        put("status", "error"); put("error", msg)
    }

    fun isInstalled(pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0); true
    } catch (_: Exception) { false }

    private fun looksLikePackage(s: String) =
        s.contains('.') && !s.contains(' ') && s.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$"))

    private fun aliasLookup(clean: String): String? =
        POPULAR_APP_PACKAGES[clean]
            ?: POPULAR_APP_PACKAGES[clean.replace(" ", "")]
            ?: POPULAR_APP_PACKAGES[clean.replace("-", "")]

    private fun launcherApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val list = try { pm.queryIntentActivities(intent, 0) } catch (_: Exception) { emptyList() }
        return list.map { AppEntry(it.loadLabel(pm).toString().trim(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }

    private fun labelOf(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    private fun levenshtein(a: CharSequence, b: CharSequence): Int {
        var cost = IntArray(a.length + 1) { it }
        var next = IntArray(a.length + 1)
        for (i in 1..b.length) {
            next[0] = i
            for (j in 1..a.length) {
                val m = if (a[j - 1] == b[i - 1]) 0 else 1
                next[j] = minOf(cost[j - 1] + m, cost[j] + 1, next[j - 1] + 1)
            }
            val t = cost; cost = next; next = t
        }
        return cost[a.length]
    }

    private fun similarity(a: String, b: String): Float {
        val longer = if (a.length > b.length) a else b
        val shorter = if (a.length > b.length) b else a
        if (longer.isEmpty()) return 1f
        return (longer.length - levenshtein(longer, shorter)).toFloat() / longer.length
    }

    /** Best installed, launchable package for a spoken name (exact -> normalized -> contains -> prefix -> fuzzy). */
    fun resolveInstalledPackage(query: String): String? {
        val q = query.lowercase(Locale.ROOT).trim()
        if (q.isEmpty()) return null
        val apps = launcherApps().map { AppEntry(it.label.lowercase(Locale.ROOT), it.pkg) }
        apps.firstOrNull { it.label == q }?.let { return it.pkg }
        val nq = q.replace(Regex("[\\s._-]+"), "")
        apps.firstOrNull { it.label.replace(Regex("[\\s._-]+"), "") == nq }?.let { return it.pkg }
        apps.firstOrNull { it.label.contains(q) || (it.label.length >= 3 && q.contains(it.label)) }?.let { return it.pkg }
        apps.firstOrNull { e ->
            e.label.split(" ", ".", "_", "-").any { w -> w.length >= 3 && (w.startsWith(q) || q.startsWith(w)) }
        }?.let { return it.pkg }
        var best: String? = null
        var bestScore = 0.65f
        for (e in apps) {
            val s = similarity(q, e.label)
            if (s > bestScore) { bestScore = s; best = e.pkg }
        }
        return best
    }

    private fun startSafely(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent); true
    } catch (e: Exception) {
        Log.w(TAG, "startActivity failed: ${e.message}"); false
    }

    private fun launchPackage(pkg: String): Boolean {
        val i = pm.getLaunchIntentForPackage(pkg) ?: return false
        return startSafely(i)
    }

    private fun waitUntil(timeoutMs: Long, stepMs: Long = 400, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (cond()) return true
            try { Thread.sleep(stepMs) } catch (_: InterruptedException) { return false }
        }
        return cond()
    }

    // ───────────────────────── Open ─────────────────────────

    fun openApp(nameOrPackage: String, installIfMissing: Boolean = true): JSONObject {
        val clean = nameOrPackage.trim().lowercase(Locale.ROOT)
        if (clean.isBlank()) return err("app_name is required")
        Log.i(TAG, "openApp('$clean')")

        // A website instead of an app ("open github.com")
        val domain = Regex("^(https?://\\S+|www\\.\\S+|[a-z0-9-]+\\.(com|org|net|in|io|co|ai|edu|gov|app|dev|me)(/\\S*)?)$")
        if (domain.matches(clean)) {
            val url = DomainNormalizer.normalizeToUrl(clean)
            return if (startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
                ok("status", "opened", "url", url) else err("Could not open $url")
        }

        when (clean) {
            "camera", "cam" -> {
                return if (startSafely(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)))
                    ok("status", "opened", "app", "Camera") else err("Could not open Camera")
            }
            "settings", "setting" -> {
                return if (startSafely(Intent(Settings.ACTION_SETTINGS)))
                    ok("status", "opened", "app", "Settings") else err("Could not open Settings")
            }
            "gallery", "photo gallery" -> {
                val pkg = resolveInstalledPackage("gallery") ?: resolveInstalledPackage("photos")
                if (pkg != null && launchPackage(pkg)) return ok("status", "opened", "app", labelOf(pkg), "package", pkg)
                val view = Intent(Intent.ACTION_VIEW).apply { type = "image/*" }
                return if (startSafely(view)) ok("status", "opened", "app", "Gallery") else err("No gallery app found")
            }
        }

        // 1) literal package name
        if (looksLikePackage(clean) && isInstalled(clean) && launchPackage(clean)) {
            return ok("status", "opened", "app", labelOf(clean), "package", clean)
        }

        // 2) alias map ("insta", "gpay", "yt" ...), then fuzzy installed-app search
        val aliasPkg = if (clean == "google play") {
            if (isInstalled("com.google.android.apps.nbu.paisa.user")) "com.google.android.apps.nbu.paisa.user" else PLAY_STORE_PKG
        } else aliasLookup(clean)

        if (aliasPkg != null && isInstalled(aliasPkg) && launchPackage(aliasPkg)) {
            return ok("status", "opened", "app", labelOf(aliasPkg), "package", aliasPkg)
        }
        val fuzzy = resolveInstalledPackage(clean)
        if (fuzzy != null && launchPackage(fuzzy)) {
            return ok("status", "opened", "app", labelOf(fuzzy), "package", fuzzy)
        }

        // 3) not installed
        if (!installIfMissing) return err("'$nameOrPackage' is not installed on this phone.")
        Log.i(TAG, "'$clean' not installed - going to Play Store")
        val res = installApp(aliasPkg ?: nameOrPackage.trim())
        res.put("note", "App was not installed, so I went to Google Play.")
        return res
    }

    // ───────────────────────── Install (Google Play) ─────────────────────────

    fun installApp(nameOrPackage: String): JSONObject {
        val query = nameOrPackage.trim()
        if (query.isBlank()) return err("app_name is required")
        val pkg: String? = when {
            looksLikePackage(query) -> query
            else -> aliasLookup(query.lowercase(Locale.ROOT))
        }

        if (pkg != null && isInstalled(pkg)) {
            launchPackage(pkg)
            return ok("status", "already_installed", "app", labelOf(pkg), "package", pkg)
        }

        val uri = if (pkg != null) Uri.parse("market://details?id=$pkg")
                  else Uri.parse("market://search?q=${Uri.encode(query)}&c=apps")
        val storeIntent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(PLAY_STORE_PKG) }
        val opened = startSafely(storeIntent) || startSafely(
            Intent(Intent.ACTION_VIEW, Uri.parse(
                if (pkg != null) "https://play.google.com/store/apps/details?id=$pkg"
                else "https://play.google.com/store/search?q=${Uri.encode(query)}&c=apps"
            ))
        )
        if (!opened) return err("Could not open Google Play Store.")

        val svc = SoltiniAccessibilityService.getInstance()
            ?: return ok(
                "status", "play_store_opened",
                "message", "Google Play is open. Accessibility is off, so please tap Install yourself."
            )

        UiWait.foreground(svc, "com.android.vending", 6000)
        UiWait.settle(2500)

        // Search results: open the best matching result first (details page).
        if (pkg == null) {
            val clickedResult = waitUntil(12_000) { svc.clickExactText(listOf(query)) || svc.clickByText(query) }
            if (!clickedResult) {
                return ok(
                    "status", "play_store_search_opened",
                    "message", "Search for '$query' is open on Google Play but I could not pick a result. Ask the user which app, or use smart_click."
                )
            }
            UiWait.settle(2500)
            // Safety: confirm we really landed on the app the user asked for.
            val firstToken = query.lowercase(Locale.ROOT).split(" ").firstOrNull { it.length >= 3 } ?: query.lowercase(Locale.ROOT)
            val onRightPage = waitUntil(6_000) { svc.readScreen().lowercase(Locale.ROOT).contains(firstToken) }
            if (!onRightPage) {
                return ok("status", "play_store_opened", "message", "I opened Google Play but could not confirm it is the right app page. Please check and tap Install.")
            }
        }

        val tapped = waitUntil(15_000) { svc.clickExactText(listOf("Install", "Get", "INSTALL")) }
        if (!tapped) {
            return ok(
                "status", "install_button_not_found",
                "message", "I opened the Google Play page but there was no Install button. It may be paid, need sign-in, or already be installing."
            )
        }

        // Small apps finish quickly; otherwise it keeps downloading in the background.
        val done = pkg != null && waitUntil(8_000, 800) { isInstalled(pkg) }
        return if (done) {
            ok("status", "installed", "app", labelOf(pkg!!), "package", pkg)
        } else {
            ok(
                "status", "install_started",
                "package", pkg ?: "",
                "message", "Install tapped. The download is running in the background; ask me to open the app once it finishes."
            )
        }
    }

    // ───────────────────────── Uninstall ─────────────────────────

    fun uninstallApp(nameOrPackage: String, confirmed: Boolean): JSONObject {
        val query = nameOrPackage.trim()
        if (query.isBlank()) return err("app_name is required")

        val pkg: String = when {
            looksLikePackage(query) && isInstalled(query) -> query
            else -> aliasLookup(query.lowercase(Locale.ROOT))?.takeIf { isInstalled(it) }
                ?: resolveInstalledPackage(query)
                ?: return err("No installed app matches '$query'.")
        }
        val label = labelOf(pkg)

        if (pkg == context.packageName) return err("I cannot uninstall myself.")
        val flags = try { pm.getApplicationInfo(pkg, 0).flags } catch (_: Exception) { 0 }
        val isPureSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        if (isPureSystem) return err("$label is a protected system app and cannot be uninstalled.")

        if (!confirmed) {
            return ok(
                "status", "confirmation_required",
                "app", label, "package", pkg,
                "message", "Ask the user: 'Do you really want to uninstall $label?' Only after a clear yes, call uninstall_app again with confirmed=true."
            )
        }

        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
        if (!startSafely(intent)) return err("Could not open the uninstall dialog for $label.")

        val svc = SoltiniAccessibilityService.getInstance()
        if (svc != null) {
            waitUntil(8_000) { svc.clickExactText(listOf("OK", "Uninstall", "UNINSTALL", "Uninstall app")) }
        }
        val gone = waitUntil(8_000, 600) { !isInstalled(pkg) }
        return if (gone) ok("status", "uninstalled", "app", label, "package", pkg)
        else ok("status", "uninstall_dialog_shown", "app", label, "message", "The system uninstall dialog is open; the user may need to tap OK.")
    }

    // ───────────────────────── List / settings ─────────────────────────

    fun listApps(query: String, limit: Int): JSONObject {
        val q = query.trim().lowercase(Locale.ROOT)
        val all = launcherApps()
            .filter { q.isEmpty() || it.label.lowercase(Locale.ROOT).contains(q) || it.pkg.lowercase(Locale.ROOT).contains(q) }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
        val arr = JSONArray()
        all.take(limit.coerceIn(1, 200)).forEach {
            arr.put(JSONObject().apply { put("name", it.label); put("package", it.pkg) })
        }
        return ok("status", "success", "total", all.size, "apps", arr)
    }

    fun openAppSettings(nameOrPackage: String): JSONObject {
        val q = nameOrPackage.trim()
        val pkg = if (looksLikePackage(q) && isInstalled(q)) q
                  else aliasLookup(q.lowercase(Locale.ROOT))?.takeIf { isInstalled(it) } ?: resolveInstalledPackage(q)
                  ?: return err("No installed app matches '$q'.")
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        return if (startSafely(i)) ok("status", "opened", "app", labelOf(pkg), "package", pkg)
        else err("Could not open app settings.")
    }
}
