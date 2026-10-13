package com.soltini.app.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.soltini.app.BuildConfig
import java.security.MessageDigest

/**
 * AppIdentityVerifier - "is this really my official Myra build?"
 *
 * Compares the SHA-256 of the certificate this APK is signed with against the certificate pinned at
 * build time (BuildConfig.PINNED_RELEASE_CERT_SHA256, from -PMYRA_RELEASE_CERT_SHA256). A re-signed or
 * tampered copy has a different certificate and is refused.
 *
 * Behaviour:
 *  - debug builds                         -> allowed (never pinned)
 *  - release + pin set + matches          -> allowed
 *  - release + pin set + MISMATCH         -> BLOCKED (app and voice service refuse to run)
 *  - release + no pin configured          -> allowed with a warning in the report, unless the build was made
 *                                            with -PMYRA_REQUIRE_PIN=true (then it is blocked: fail closed)
 */
object AppIdentityVerifier {

    enum class Status { DEBUG_BUILD, TRUSTED, MISMATCH, NO_PIN_CONFIGURED }

    fun currentSignerSha256(context: Context): List<String> = runCatching {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        }
        signatures.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { b -> "%02X".format(b) }
        }.distinct().sorted()
    }.getOrDefault(emptyList())

    private fun pinned(): Set<String> =
        BuildConfig.PINNED_RELEASE_CERT_SHA256
            .split(',', ';', '\n')
            .map { it.filter(Char::isLetterOrDigit).uppercase() }
            .filter { it.length == 64 }
            .toSet()

    fun status(context: Context): Status {
        if (BuildConfig.DEBUG) return Status.DEBUG_BUILD
        val expected = pinned()
        if (expected.isEmpty()) return Status.NO_PIN_CONFIGURED
        return if (currentSignerSha256(context).any { it in expected }) Status.TRUSTED else Status.MISMATCH
    }

    /** True when the app may run normally. */
    fun isAllowed(context: Context): Boolean = when (status(context)) {
        Status.DEBUG_BUILD, Status.TRUSTED -> true
        Status.MISMATCH -> false
        Status.NO_PIN_CONFIGURED -> !BuildConfig.REQUIRE_RELEASE_PIN
    }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun installer(context: Context): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrNull() ?: "unknown / sideloaded"

    fun report(context: Context): String {
        val signers = currentSignerSha256(context).ifEmpty { listOf("Unavailable") }
        val st = when (status(context)) {
            Status.DEBUG_BUILD -> "Debug build (not publisher verified)"
            Status.TRUSTED -> "OK - matches the pinned release certificate"
            Status.MISMATCH -> "NOT VERIFIED - signed with a different certificate than the pinned one. This copy may be tampered with."
            Status.NO_PIN_CONFIGURED ->
                if (BuildConfig.REQUIRE_RELEASE_PIN) "BLOCKED - strict mode is on but no certificate pin was configured"
                else "Unverified - no release certificate pin was configured at build time"
        }
        return buildString {
            appendLine("Developer: ${BuildConfig.DEVELOPER_NAME}")
            appendLine("Copyright: ${BuildConfig.COPYRIGHT_OWNER}")
            appendLine("App ID: ${context.packageName}")
            appendLine("Version: ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"}")
            appendLine("Installed from: ${installer(context)}")
            appendLine("Debuggable: ${if (isDebuggable(context)) "YES" else "no"}")
            appendLine("Signing certificate SHA-256:")
            signers.forEach { appendLine(it) }
            append("Status: $st")
        }
    }
}
