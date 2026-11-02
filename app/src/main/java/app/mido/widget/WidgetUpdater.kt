package app.mido.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetUpdater {
    private const val TICK_MS = 5 * 60_000L

    /** Fire-and-forget: redraw every Mido widget on the home screen, and keep the countdown text fresh. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { MidoWidget().updateAll(app) }
        }
        scheduleTick(app)
    }

    /** "Starts in 20 min" goes stale, so redraw about every 5 minutes while a widget is on the home screen. */
    fun scheduleTick(context: Context) {
        val app = context.applicationContext
        val ids = AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, MidoWidgetReceiver::class.java))
        if (ids.isEmpty()) return
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            app, 4242, Intent(app, WidgetTickReceiver::class.java).setAction("app.mido.action.WIDGET_TICK"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + TICK_MS, pi) }
    }
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetUpdater.refresh(context)
    }
}
