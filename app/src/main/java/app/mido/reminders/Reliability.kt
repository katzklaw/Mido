package app.mido.reminders

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Checks the things that decide whether a reminder shows up when the phone is idle or Mido is closed. */
object Reliability {
    /** Task id used by the "send me a test reminder" button; never a real task. */
    const val TEST_TASK_ID = 999_999_999L

    data class Status(
        val notificationsAllowed: Boolean,
        val exactAlarmsAllowed: Boolean,
        val batteryUnrestricted: Boolean,
    ) {
        val allGood: Boolean get() = notificationsAllowed && exactAlarmsAllowed && batteryUnrestricted
    }

    fun status(context: Context): Status {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return Status(
            notificationsAllowed = Notifications.canPost(context),
            exactAlarmsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms(),
            batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
        )
    }

    /** The system's "let this app ignore battery optimisations?" prompt. */
    fun batteryPrompt(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun notificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarmSettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))

    /** Rings through the real alarm path in [seconds] seconds, so you can lock the phone and see if it arrives. */
    fun scheduleTest(context: Context, seconds: Long = 30) {
        AlarmReminderScheduler(context).scheduleAt(TEST_TASK_ID, System.currentTimeMillis() + seconds * 1000)
    }

    fun showTest(context: Context) {
        Notifications.showSimple(
            context, 2_000_000, "Mido test reminder",
            "This arrived on time, so reminders are working with the app closed.",
        )
    }
}
