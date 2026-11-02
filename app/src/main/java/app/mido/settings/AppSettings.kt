package app.mido.settings

import android.content.Context

/** Small user preferences, kept in SharedPreferences. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mido_settings", Context.MODE_PRIVATE)

    /** Minutes of cushion added on top of the drive time. */
    var bufferMin: Int
        get() = prefs.getInt(KEY_BUFFER, 5)
        set(v) = prefs.edit().putInt(KEY_BUFFER, v.coerceIn(0, 30)).apply()

    /** Write timed tasks into this calendar; -1 means calendar sync is off. */
    var syncCalendarId: Long
        get() = prefs.getLong(KEY_CALENDAR, -1L)
        set(v) = prefs.edit().putLong(KEY_CALENDAR, v).apply()

    /** Offer to call back after a missed call. */
    var suggestCallbacks: Boolean
        get() = prefs.getBoolean(KEY_CALLBACKS, true)
        set(v) = prefs.edit().putBoolean(KEY_CALLBACKS, v).apply()

    /** Offer to turn a text that sounds like a to-do into a task. */
    var suggestFromTexts: Boolean
        get() = prefs.getBoolean(KEY_TEXTS, true)
        set(v) = prefs.edit().putBoolean(KEY_TEXTS, v).apply()

    /** Play a short chime when a task is checked off. */
    var chimeEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHIME, true)
        set(v) = prefs.edit().putBoolean(KEY_CHIME, v).apply()

    /** Colour scheme of the home screen widget. */
    var widgetTheme: WidgetTheme
        get() = WidgetTheme.fromName(prefs.getString(KEY_WIDGET_THEME, null))
        set(v) = prefs.edit().putString(KEY_WIDGET_THEME, v.name).apply()

    /** How solid the widget background is, in percent (100 = fully opaque). */
    var widgetOpacity: Int
        get() = prefs.getInt(KEY_WIDGET_OPACITY, 100)
        set(v) = prefs.edit().putInt(KEY_WIDGET_OPACITY, v.coerceIn(10, 100)).apply()

    /** Keep a "leave for X in N min" notification pinned while you have a drive coming up. */
    var pinCountdown: Boolean
        get() = prefs.getBoolean(KEY_PIN, true)
        set(v) = prefs.edit().putBoolean(KEY_PIN, v).apply()

    /** Keep re-alerting (alarm sound) until a reminder is marked done or snoozed. */
    var persistentAlerts: Boolean
        get() = prefs.getBoolean(KEY_PERSIST, true)
        set(v) = prefs.edit().putBoolean(KEY_PERSIST, v).apply()

    /** Sound an alarm tone (not a normal notification ding) at "time to leave". */
    var loudLeaveAlert: Boolean
        get() = prefs.getBoolean(KEY_LOUD_LEAVE, true)
        set(v) = prefs.edit().putBoolean(KEY_LOUD_LEAVE, v).apply()

    /** Minutes between repeat alerts. */
    var nagIntervalMin: Int
        get() = prefs.getInt(KEY_NAG, 2)
        set(v) = prefs.edit().putInt(KEY_NAG, v.coerceIn(1, 30)).apply()

    /** Diagnostic: what the last text notification Mido saw looked like, shown in Settings. */
    var lastTextSeen: String
        get() = prefs.getString(KEY_LAST_TEXT, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LAST_TEXT, v).apply()

    /** Diagnostic: the app and category of the last few notifications Mido was shown (no message text). */
    val recentNotifications: List<String>
        get() = (prefs.getString(KEY_RECENT, "") ?: "").split('\n').filter { it.isNotBlank() }

    fun addRecentNotification(line: String) {
        val list = (listOf(line) + recentNotifications.filter { it != line }).take(6)
        prefs.edit().putString(KEY_RECENT, list.joinToString("\n")).apply()
    }

    /** How many text-to-task suggestions have been shown (the free version allows a few). */
    var textSuggestionsUsed: Int
        get() = prefs.getInt(KEY_TEXT_USED, 0)
        set(v) = prefs.edit().putInt(KEY_TEXT_USED, v).apply()

    val syncEnabled: Boolean get() = syncCalendarId >= 0

    private companion object {
        const val KEY_BUFFER = "buffer_min"
        const val KEY_CALENDAR = "sync_calendar_id"
        const val KEY_CALLBACKS = "suggest_callbacks"
        const val KEY_TEXTS = "suggest_texts"
        const val KEY_CHIME = "chime_enabled"
        const val KEY_WIDGET_THEME = "widget_theme"
        const val KEY_WIDGET_OPACITY = "widget_opacity"
        const val KEY_LAST_TEXT = "last_text_seen"
        const val KEY_RECENT = "recent_notifications"
        const val KEY_LOUD_LEAVE = "loud_leave_alert"
        const val KEY_TEXT_USED = "text_suggestions_used"
        const val KEY_PIN = "pin_countdown"
        const val KEY_PERSIST = "persistent_alerts"
        const val KEY_NAG = "nag_interval_min"
    }
}
