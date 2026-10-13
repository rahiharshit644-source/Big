package com.soltini.app.voiceprint.gate

import android.util.Log
import com.soltini.app.voiceprint.domain.VoiceprintVerifier
import org.json.JSONObject

/**
 * SensitiveActionGuard
 *
 * Enforces speaker verification for sensitive smart home operations and critical device commands.
 *
 * SECURITY NOTICE:
 * On-device voice verification is a convenience layer, NOT cryptographic authentication.
 * Physical replay attacks or high-fidelity recordings can potentially mimic acoustic characteristics.
 * Financial transactions and Android lockscreen authentication require system BiometricPrompt.
 */
class SensitiveActionGuard(
    private val verifier: VoiceprintVerifier,
    private val getGateMode: () -> VoiceGateMode
) {
    companion object {
        private const val TAG = "SensitiveActionGuard"
        const val SENSITIVE_WINDOW_MS = 10_000L // 10 seconds recent verification required
    }

    sealed class GuardResult {
        object Allowed : GuardResult()
        data class VerificationRequired(
            val message: String,
            val responseJson: JSONObject
        ) : GuardResult()
    }

    /**
     * Checks if a tool call requires owner voice verification.
     *
     * @param toolName Name of the invoked tool (e.g. "control_device", "manage_files").
     * @param isTargetDeviceSensitive True if the resolved smart home device has sensitive=true or is a LOCK/RELAY.
     * @return [GuardResult.Allowed] or [GuardResult.VerificationRequired].
     */
    fun checkPermission(
        toolName: String,
        isTargetDeviceSensitive: Boolean = false
    ): GuardResult {
        val currentMode = getGateMode()

        // If voice gate is turned off entirely, guard is bypassed
        if (currentMode == VoiceGateMode.OFF) {
            return GuardResult.Allowed
        }

        // No model / no enrolled owner => verification is impossible, don't lock the user out
        if (!verifier.isReady) {
            Log.w(TAG, "VoicePrint not ready (no model or no enrolled profile) - guard bypassed for '$toolName'")
            return GuardResult.Allowed
        }

        // Determine if this specific action is considered sensitive
        val isSensitive = isTargetDeviceSensitive || isInherentlySensitiveTool(toolName)

        if (!isSensitive) {
            return GuardResult.Allowed
        }

        // Check if owner was successfully verified within the last 10 seconds
        val isVerified = verifier.isOwnerVerifiedWithin(SENSITIVE_WINDOW_MS)
        if (isVerified) {
            Log.i(TAG, "Sensitive action '$toolName' approved (Owner verified within ${SENSITIVE_WINDOW_MS / 1000}s)")
            return GuardResult.Allowed
        }

        Log.w(TAG, "Sensitive action '$toolName' BLOCKED: Owner not verified within ${SENSITIVE_WINDOW_MS / 1000}s")

        val explanation = "Owner voice verification required for this sensitive action. Please ask the owner to repeat the command clearly."
        val responseJson = JSONObject().apply {
            put("status", "voice_verification_required")
            put("error", explanation)
            put("tool", toolName)
            put(
                "instruction",
                "Inform the user politely that this is a sensitive action (such as operating locks or high-power relays) and requires the owner's verified voice. Ask the owner to repeat the command."
            )
        }

        return GuardResult.VerificationRequired(explanation, responseJson)
    }

    private fun isInherentlySensitiveTool(toolName: String): Boolean {
        return when (toolName.lowercase()) {
            "lock_door", "unlock_door", "power_relay", "wipe_biometrics", "security_lockdown" -> true
            else -> false
        }
    }
}
