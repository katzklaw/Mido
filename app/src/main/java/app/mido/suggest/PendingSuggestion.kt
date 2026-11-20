package app.mido.suggest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A suggestion the user said "yes" to in a notification, waiting for the app to turn it into a task. */
object PendingSuggestion {
    data class Item(val title: String, val notes: String, val notificationId: Int, val plain: Boolean = false)

    var value by mutableStateOf<Item?>(null)
}
