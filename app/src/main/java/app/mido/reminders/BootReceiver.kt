package app.mido.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms are wiped on reboot, app update and time-zone change, so re-arm them all. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.repository(context).rescheduleAll()
                // Anything that came due while the phone was off.
                MissedSweep.run(context)
                app.mido.widget.WidgetUpdater.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
