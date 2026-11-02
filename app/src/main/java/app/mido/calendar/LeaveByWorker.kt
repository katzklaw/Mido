package app.mido.calendar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Every ~15 minutes: re-check traffic for upcoming events and move the "time to leave" alarms. */
class LeaveByWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Self-healing: re-arm every alarm and geofence, and flag any reminder that never showed up.
        runCatching { app.mido.reminders.Reminders.repository(applicationContext).rescheduleAll() }
        runCatching { app.mido.reminders.MissedSweep.run(applicationContext) }

        if (!app.mido.pro.Entitlements.isPro(applicationContext)) {
            LeaveAlarms(applicationContext).sync(emptyList())
            return Result.success()
        }
        val calendar = CalendarRepository(applicationContext)
        if (!calendar.hasPermission()) return Result.success()
        val legs = LeavePlanner(
            RoutesTravelTime(), LastKnownOrigin(applicationContext),
            bufferMin = app.mido.settings.AppSettings(applicationContext).bufferMin,
        )
            .plan(calendar.upcoming(), System.currentTimeMillis())
        LeaveAlarms(applicationContext).sync(legs)
        app.mido.widget.WidgetUpdater.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LeaveByWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "leave-by-refresh", ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }
    }
}
