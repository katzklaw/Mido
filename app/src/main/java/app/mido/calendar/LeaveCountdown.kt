package app.mido.calendar

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.mido.reminders.Notifications
import app.mido.settings.AppSettings

/** Packs a [DriveLeg] into an Intent so alarms and the service can rebuild the notification. */
object LegIntent {
    private const val K = "leg_key"
    private const val T = "leg_title"
    private const val L = "leg_location"
    private const val S = "leg_start"
    private const val B = "leg_leave_by"
    private const val M = "leg_minutes"

    fun put(intent: Intent, leg: DriveLeg): Intent = intent
        .putExtra(K, leg.eventKey).putExtra(T, leg.title).putExtra(L, leg.location)
        .putExtra(S, leg.startMs).putExtra(B, leg.leaveByMs).putExtra(M, leg.driveMinutes)

    fun read(intent: Intent?): DriveLeg? {
        intent ?: return null
        val key = intent.getStringExtra(K) ?: return null
        return DriveLeg(
            key, intent.getStringExtra(T).orEmpty(), intent.getStringExtra(L).orEmpty(),
            intent.getLongExtra(S, 0L), intent.getLongExtra(B, 0L), intent.getIntExtra(M, 0),
        )
    }
}

/**
 * A "Leave for X" notification with a live countdown that stays pinned while you have a drive coming up.
 * It runs as a foreground service, which is the strongest "can't be swiped away" Android offers.
 */
object LeaveCountdown {
    const val NOTIFICATION_ID = 7001

    /** Show the countdown for the leg that matters now, or take it down if there isn't one. */
    fun sync(context: Context, legs: List<DriveLeg>) {
        val leg = if (AppSettings(context).pinCountdown) Countdown.pick(legs, System.currentTimeMillis()) else null
        if (leg == null) stop(context) else start(context, leg)
    }

    fun start(context: Context, leg: DriveLeg) {
        val app = context.applicationContext
        if (!AppSettings(app).pinCountdown || !Notifications.canPost(app)) return
        Notifications.ensureChannels(app)
        try {
            ContextCompat.startForegroundService(app, LegIntent.put(Intent(app, LeaveCountdownService::class.java), leg))
        } catch (e: Exception) {
            // Android blocks starting a foreground service from the background in some situations;
            // fall back to a normal ongoing notification (it can be swiped on newer Android versions).
            Notifications.notifyCountdown(app, leg)
        }
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        runCatching { app.stopService(Intent(app, LeaveCountdownService::class.java)) }
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
    }
}

class LeaveCountdownService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stopper = Runnable {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val leg = LegIntent.read(intent)
        val now = System.currentTimeMillis()
        if (leg == null || leg.startMs <= now) {
            // Android crashes the app if a service started with startForegroundService() never calls
            // startForeground(), so show a placeholder for an instant before stopping.
            val placeholder = androidx.core.app.NotificationCompat.Builder(this, Notifications.CHANNEL_COUNTDOWN)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle("Mido")
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(LeaveCountdown.NOTIFICATION_ID, placeholder, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(LeaveCountdown.NOTIFICATION_ID, placeholder)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = Notifications.buildCountdown(this, leg, now)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(LeaveCountdown.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(LeaveCountdown.NOTIFICATION_ID, notification)
        }
        // Take it down a minute after the event starts.
        handler.removeCallbacks(stopper)
        handler.postDelayed(stopper, (leg.startMs - now + 60_000L).coerceAtLeast(60_000L))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopper)
        super.onDestroy()
    }
}
