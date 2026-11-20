package app.mido

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Catches crashes so the real error can be read on the phone instead of the app just vanishing.
 * The report is saved to a file and shown by [CrashActivity], which runs in its own process.
 */
class MidoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 28 && getProcessName().orEmpty().endsWith(":crash")) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val report = buildReport(thread, error)
                File(filesDir, CrashActivity.FILE).writeText(report)
                startActivity(
                    Intent(this, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra(CrashActivity.EXTRA_REPORT, report.take(40_000)),
                )
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } catch (e: Throwable) {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    private fun buildReport(thread: Thread, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("Mido ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }
    }
}
