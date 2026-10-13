package com.soltini.app.util

import android.content.Context
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CrashReporter
 *
 * Saves the stack trace of any uncaught exception to SharedPreferences right before the
 * process dies. On the next app launch MainActivity shows it in a dialog (with a Copy button),
 * so crashes can be diagnosed WITHOUT Android Studio / logcat.
 */
object CrashReporter {

    private const val PREFS = "myra_crash_reporter"
    private const val KEY_LAST_CRASH = "last_crash"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val report = buildString {
                    appendLine("Time: $time")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                    appendLine()
                    append(sw.toString())
                }
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, report.take(30000))
                    .commit() // commit() (not apply) because the process is about to die
            } catch (_: Throwable) {
                // never let the reporter itself crash
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Returns the saved crash report (if any) and clears it so it is shown only once. */
    fun consumeLastCrash(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val report = prefs.getString(KEY_LAST_CRASH, null)
        if (report != null) prefs.edit().remove(KEY_LAST_CRASH).commit()
        return report
    }
}
