package app.mido.suggest

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.mido.QuickAddActivity
import app.mido.reminders.Notifications
import app.mido.settings.AppSettings

/**
 * Watches other apps' notifications (only after you grant Notification access) to offer
 * "call them back?" after a missed call and "make this a task?" for texts that sound like a
 * to-do. All of it happens on the phone; nothing is sent anywhere.
 */
class MidoNotificationListener : NotificationListenerService() {
    private val handled = LinkedHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        val settings = AppSettings(this)
        val extras = n.extras ?: return
        val isSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (n.flags and Notification.FLAG_ONGOING_EVENT == 0) {
            settings.addRecentNotification("${sbn.packageName} · ${n.category ?: "no category"}" + if (isSummary) " · group summary" else "")
        }

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.trim()

        val messaging = n.category == Notification.CATEGORY_MESSAGE || sbn.packageName in MESSAGING_APPS
        // Group summaries are skipped for other apps (they repeat the same text), but some messaging apps
        // only post the summary, so those are read too; duplicates are filtered by sender and text.
        if (isSummary && !messaging) return

        when {
            settings.suggestCallbacks && isMissedCall(sbn, n, title, text) -> onMissedCall(sbn, title, text, extras)
            settings.suggestFromTexts && messaging ->
                onMessage(sbn, title, latestMessageText(extras) ?: text)
        }
    }

    // ---- missed calls ------------------------------------------------------------------------

    private fun isMissedCall(sbn: StatusBarNotification, n: Notification, title: String?, text: String?): Boolean {
        if (n.category == Notification.CATEGORY_MISSED_CALL) return true
        // Some dialers don't set the category, so also accept their "Missed call" wording.
        return sbn.packageName in DIALER_APPS &&
            listOfNotNull(title, text).any { it.contains("missed", ignoreCase = true) }
    }

    private fun onMissedCall(sbn: StatusBarNotification, title: String?, text: String?, extras: Bundle) {
        if (!firstTime("call:${sbn.key}:$title:$text")) return
        val parts = listOfNotNull(title, text, extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
            .filter { it.isNotBlank() }
        val number = parts.firstNotNullOfOrNull { PhoneNumbers.find(it) }
        // The name is whatever isn't the "Missed call" wording or the number itself.
        val name = parts.firstOrNull {
            !it.contains("missed", ignoreCase = true) && !PhoneNumbers.looksLikeNumber(it) && PhoneNumbers.find(it) == null
        }
        val dialNumber = number ?: name?.let { ContactLookup.numberForName(this, it) }
        val label = name ?: number?.let { PhoneNumbers.pretty(it) } ?: "a missed call"

        val actions = mutableListOf<Pair<String, PendingIntent>>()
        val dial = Intent(Intent.ACTION_DIAL, dialNumber?.let { Uri.fromParts("tel", it.filter { c -> c.isDigit() || c == '+' }, null) } ?: Uri.parse("tel:"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        actions += "Call back" to PendingIntent.getActivity(this, sbn.key.hashCode(), dial, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        actions += "Remind me in 1h" to SuggestionReceiver.pending(
            this, SuggestionReceiver.ACTION_CALLBACK_TASK, sbn.key.hashCode() + 1,
            SuggestionReceiver.EXTRA_TITLE to "Call $label back",
            SuggestionReceiver.EXTRA_NOTES to (dialNumber?.let { "Phone: ${PhoneNumbers.pretty(it)}" } ?: ""),
        )

        Notifications.showSuggestion(
            this, 3_000_000 + (sbn.key.hashCode() and 0xFFFFF),
            "Missed call from $label",
            "Do you want to call them back?",
            android.R.drawable.sym_call_missed,
            actions,
        )
    }

    // ---- texts -------------------------------------------------------------------------------

    private fun onMessage(sbn: StatusBarNotification, sender: String?, message: String?) {
        if (message.isNullOrBlank()) return
        if (!firstTime("msg:${sbn.packageName}:$sender:$message")) return
        val suggestion = TextTaskExtractor.suggest(message, sender)
        AppSettings(this).lastTextSeen =
            "${sbn.packageName}: “${message.take(60)}” → " + (suggestion?.let { "suggested “$it”" } ?: "didn't read as a to-do")
        if (suggestion == null) return
        val settings = AppSettings(this)
        if (!app.mido.pro.ProPolicy.textSuggestionAllowed(settings.textSuggestionsUsed, app.mido.pro.Entitlements.isPro(this))) return
        settings.textSuggestionsUsed = settings.textSuggestionsUsed + 1
        val who = sender?.takeIf { it.isNotBlank() } ?: "a text"
        val notes = "From $who: “${message.take(200)}”"

        val edit = PendingIntent.getActivity(
            this, sbn.key.hashCode() + 2,
            Intent(this, QuickAddActivity::class.java)
                .putExtra(QuickAddActivity.EXTRA_TEXT, suggestion)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val actions = listOf(
            "Add task" to SuggestionReceiver.pending(
                this, SuggestionReceiver.ACTION_ADD_TASK, sbn.key.hashCode() + 3,
                SuggestionReceiver.EXTRA_TITLE to suggestion,
                SuggestionReceiver.EXTRA_NOTES to notes,
            ),
            "Edit first" to edit,
        )
        Notifications.showSuggestion(
            this, 4_000_000 + (sbn.key.hashCode() and 0xFFFFF),
            "Text from $who",
            "Turn this into a task?\n“$suggestion”",
            android.R.drawable.ic_menu_agenda,
            actions,
            contentIntent = edit,
        )
    }

    /** Messaging apps put the whole conversation in a list; the newest line is the one we want. */
    @Suppress("DEPRECATION")
    private fun latestMessageText(extras: Bundle): String? {
        val msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = msgs.lastOrNull() as? Bundle ?: return null
        return last.getCharSequence("text")?.toString()
    }

    /** Messaging notifications get re-posted as they update; only react once per message. */
    private fun firstTime(key: String): Boolean {
        if (handled.containsKey(key)) return false
        handled[key] = System.currentTimeMillis()
        if (handled.size > 200) handled.remove(handled.keys.first())
        return true
    }

    companion object {
        val DIALER_APPS = setOf(
            "com.google.android.dialer", "com.samsung.android.incallui", "com.samsung.android.dialer",
            "com.android.server.telecom", "com.android.dialer", "com.android.phone",
        )
        val MESSAGING_APPS = setOf(
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
            "com.whatsapp", "org.thoughtcrime.securesms", "org.telegram.messenger",
        )

        /** Whether the user has switched on Notification access for Mido. */
        fun isEnabled(context: Context): Boolean =
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
