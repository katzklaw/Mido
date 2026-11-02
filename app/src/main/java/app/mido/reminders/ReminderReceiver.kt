package app.mido.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mido.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when a task's alarm goes off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (id < 0) return
        if (id == Reliability.TEST_TASK_ID) {
            Reliability.showTest(context)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).taskDao()
                val task = dao.getById(id)
                if (task != null && !task.completed) {
                    ReminderAlert.fire(context, task)
                    dao.update(task.copy(notifiedAt = System.currentTimeMillis()))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "app.mido.action.FIRE_REMINDER"
        const val EXTRA_TASK_ID = "task_id"
    }
}
