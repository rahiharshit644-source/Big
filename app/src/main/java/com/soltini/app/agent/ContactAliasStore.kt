package com.soltini.app.agent

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Spoken nicknames for contacts ("mummy" -> "Mom", "boss" -> "Rahul Sharma" or a number).
 * Resolved before any call / SMS / WhatsApp lookup.
 */
class ContactAliasStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("myra_contact_aliases", Context.MODE_PRIVATE)

    private fun load(): JSONObject = try { JSONObject(prefs.getString("aliases", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }

    private fun key(s: String) = s.trim().lowercase(Locale.ROOT)

    /** Returns the real contact name/number for a nickname, or the input unchanged. */
    fun resolve(nameOrAlias: String): String {
        val v = load().optString(key(nameOrAlias), "")
        return if (v.isNotBlank()) v else nameOrAlias
    }

    fun set(alias: String, target: String) {
        val o = load(); o.put(key(alias), target.trim())
        prefs.edit().putString("aliases", o.toString()).apply()
    }

    fun remove(alias: String): Boolean {
        val o = load()
        val existed = o.has(key(alias))
        o.remove(key(alias))
        prefs.edit().putString("aliases", o.toString()).apply()
        return existed
    }

    fun all(): Map<String, String> {
        val o = load()
        return o.keys().asSequence().associateWith { o.optString(it) }
    }
}
