package com.soltini.app.agent

import org.json.JSONObject
import java.util.Locale

/**
 * RiskGate - the agent never spends money, deletes things or types secrets on its own.
 * Risky steps pause the task and ask the user first.
 */
object RiskGate {

    enum class Kind { NONE, CONFIRM, HANDOVER }

    data class Verdict(val kind: Kind, val key: String = "", val question: String = "")

    private val CONFIRM_PHRASES = listOf(
        "pay now", "pay ₹", "pay rs", "pay $", "proceed to pay", "place order", "place your order", "buy now",
        "confirm order", "confirm purchase", "confirm payment", "complete purchase", "make payment",
        "send money", "transfer now", "confirm transfer", "donate", "delete account", "delete all",
        "factory reset", "erase all", "erase data", "uninstall", "remove account", "clear all data",
        "book now", "confirm booking", "withdraw"
    )

    private val SECRET_FIELD = Regex(
        "(password|passcode|\\bpin\\b|\\bmpin\\b|upi pin|cvv|cvc|card number|card no|security code|secret)",
        RegexOption.IGNORE_CASE
    )

    private val CARD_LIKE = Regex("^[0-9 \\-]{13,23}$")

    private val CLICK_TOOLS = setOf("smart_click", "click_element")
    private val TYPE_TOOLS = setOf("smart_type", "type_text", "fill_single_field", "fill_form", "smart_fill_form")

    fun check(tool: String, args: JSONObject, enabled: Boolean): Verdict {
        if (!enabled) return Verdict(Kind.NONE)

        if (tool in CLICK_TOOLS) {
            val target = args.optString("target", args.optString("text", args.optString("label", ""))).lowercase(Locale.ROOT)
            val hit = CONFIRM_PHRASES.firstOrNull { target.contains(it) }
            if (hit != null) {
                return Verdict(
                    Kind.CONFIRM, "click|$target",
                    "I am about to tap '${args.optString("target", args.optString("text", ""))}'. This can spend money or delete something. Should I go ahead?"
                )
            }
        }

        if (tool in TYPE_TOOLS) {
            val field = args.optString("field", args.optString("label", args.optString("target", "")))
            val text = args.optString("text", args.optString("value", args.optString("query", "")))
            val fieldsJson = args.optJSONArray("fields")?.toString().orEmpty()
            if (SECRET_FIELD.containsMatchIn(field) || SECRET_FIELD.containsMatchIn(fieldsJson) || CARD_LIKE.matches(text.trim())) {
                return Verdict(
                    Kind.HANDOVER, "type|$field",
                    "This step needs a password, PIN or card detail. Please enter it yourself on the phone, then tell me 'done'."
                )
            }
        }
        return Verdict(Kind.NONE)
    }
}
