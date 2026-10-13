package com.soltini.app.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.util.Locale

/**
 * PhoneSettingsController - open any system settings page, set screen brightness,
 * and open several websites at once (each in its own Chrome tab).
 *
 * Android does not let apps flip Wi-Fi / Bluetooth / airplane mode silently, so those pages
 * are opened for the user (or driven afterwards with the existing screen-control tools).
 */
class PhoneSettingsController(private val context: Context) {

    companion object {
        private const val TAG = "PhoneSettingsCtl"

        private val PAGES: Map<String, String> = mapOf(
            "wifi" to Settings.ACTION_WIFI_SETTINGS,
            "wi-fi" to Settings.ACTION_WIFI_SETTINGS,
            "internet" to Settings.Panel.ACTION_INTERNET_CONNECTIVITY,
            "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
            "flight" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
            "network" to Settings.ACTION_WIRELESS_SETTINGS,
            "mobile data" to Settings.ACTION_WIRELESS_SETTINGS,
            "data" to Settings.ACTION_WIRELESS_SETTINGS,
            "hotspot" to "android.settings.TETHER_SETTINGS",
            "tethering" to "android.settings.TETHER_SETTINGS",
            "display" to Settings.ACTION_DISPLAY_SETTINGS,
            "brightness" to Settings.ACTION_DISPLAY_SETTINGS,
            "sound" to Settings.ACTION_SOUND_SETTINGS,
            "volume" to Settings.Panel.ACTION_VOLUME,
            "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
            "apps" to Settings.ACTION_APPLICATION_SETTINGS,
            "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            "gps" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            "nfc" to Settings.ACTION_NFC_SETTINGS,
            "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
            "date" to Settings.ACTION_DATE_SETTINGS,
            "time" to Settings.ACTION_DATE_SETTINGS,
            "language" to Settings.ACTION_LOCALE_SETTINGS,
            "keyboard" to Settings.ACTION_INPUT_METHOD_SETTINGS,
            "security" to Settings.ACTION_SECURITY_SETTINGS,
            "privacy" to Settings.ACTION_PRIVACY_SETTINGS,
            "vpn" to Settings.ACTION_VPN_SETTINGS,
            "developer" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            "default apps" to Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            "do not disturb" to Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
            "dnd" to Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
            "cast" to Settings.ACTION_CAST_SETTINGS,
            "about phone" to Settings.ACTION_DEVICE_INFO_SETTINGS,
            "about" to Settings.ACTION_DEVICE_INFO_SETTINGS,
            "settings" to Settings.ACTION_SETTINGS
        )
    }

    private fun ok(vararg pairs: Any): JSONObject = JSONObject().apply {
        var i = 0
        while (i < pairs.size - 1) { put(pairs[i].toString(), pairs[i + 1]); i += 2 }
    }

    private fun err(msg: String): JSONObject = JSONObject().apply { put("status", "error"); put("error", msg) }

    private fun start(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); true
    } catch (e: Exception) { Log.w(TAG, "start failed: ${e.message}"); false }

    fun openSettings(pageRaw: String): JSONObject {
        val page = pageRaw.trim().lowercase(Locale.ROOT).ifBlank { "settings" }
        val action = PAGES[page]
            ?: PAGES.entries.firstOrNull { page.contains(it.key) || it.key.contains(page) }?.value
            ?: return err("I don't know a settings page called '$pageRaw'. Try: wifi, bluetooth, airplane, hotspot, display, sound, battery, apps, storage, location, nfc, accessibility, language, security, vpn, developer, about phone.")
        return if (start(Intent(action)) || start(Intent(Settings.ACTION_SETTINGS)))
            ok("status", "opened", "page", page)
        else err("Could not open the $page settings.")
    }

    /** percent 0-100, or change = "up"/"down" (+/-15%). */
    fun setBrightness(percent: Int?, change: String?): JSONObject {
        if (!Settings.System.canWrite(context)) {
            start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            return ok("status", "needs_permission", "message", "I opened the 'Modify system settings' page. Please allow it for Myra, then ask again.")
        }
        return try {
            val resolver = context.contentResolver
            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255
            val target = when {
                percent != null -> percent
                change?.lowercase(Locale.ROOT) in setOf("up", "increase", "more", "badhao") -> current + 15
                change?.lowercase(Locale.ROOT) in setOf("down", "decrease", "less", "kam") -> current - 15
                else -> return err("Give a percent (0-100) or change = up/down.")
            }.coerceIn(1, 100)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target * 255 / 100)
            ok("status", "success", "brightness_percent", target)
        } catch (e: Exception) {
            err("Could not change brightness: ${e.message}")
        }
    }

    /** Opens several websites, one Chrome tab each. [text] may contain spoken names ("youtube github and gmail"). */
    fun openUrls(urls: List<String>, text: String): JSONObject {
        val list = urls.map { it.trim() }.filter { it.isNotBlank() }.map { DomainNormalizer.normalizeToUrl(it) }
            .ifEmpty { DomainNormalizer.extractMultipleUrls(text) }
        if (list.isEmpty()) return err("No websites found to open.")
        var opened = 0
        for ((i, url) in list.withIndex()) {
            val uri = Uri.parse(url)
            val chrome = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.android.chrome")
                putExtra("com.android.browser.application_id", context.packageName)
            }
            if (start(chrome) || start(Intent(Intent.ACTION_VIEW, uri))) opened++
            if (i < list.size - 1) try { Thread.sleep(450) } catch (_: InterruptedException) {}
        }
        return ok("status", if (opened > 0) "opened" else "error", "opened", opened, "urls", org.json.JSONArray(list))
    }
}
