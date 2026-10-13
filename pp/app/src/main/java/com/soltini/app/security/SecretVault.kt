package com.soltini.app.security

/**
 * SecretVault - encrypts small secrets (API keys, saved site passwords) before they touch SharedPreferences.
 *
 * Uses the hardware-backed AES-256-GCM key that [KeystoreCryptoManager] already keeps in the AndroidKeyStore.
 * Stored form: "enc:v1:<base64>". Anything without that prefix is legacy plaintext: it is still read
 * correctly and callers re-save it sealed (transparent migration).
 */
object SecretVault {

    private const val PREFIX = "enc:v1:"

    fun isSealed(stored: String?): Boolean = stored != null && stored.startsWith(PREFIX)

    /** Encrypts [plain]. If the Keystore is unavailable the text is returned unchanged rather than lost. */
    fun seal(plain: String): String {
        if (plain.isEmpty()) return ""
        val enc = KeystoreCryptoManager.encrypt(plain.toByteArray(Charsets.UTF_8)) ?: return plain
        return PREFIX + enc
    }

    /**
     * Returns the plain text, or null if it was sealed but cannot be decrypted (key lost / data corrupt).
     * Legacy plaintext is returned as-is.
     */
    fun open(stored: String): String? {
        if (stored.isEmpty()) return ""
        if (!isSealed(stored)) return stored
        val bytes = KeystoreCryptoManager.decrypt(stored.removePrefix(PREFIX)) ?: return null
        return String(bytes, Charsets.UTF_8)
    }
}
