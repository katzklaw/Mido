package app.mido

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.mido.ui.TodoApp
import app.mido.ui.theme.MidoTheme

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSuggestion(intent)
    }

    /** Picks up a "Make task" tap from a suggestion notification; the app then creates the task and opens its editor. */
    private fun handleSuggestion(intent: android.content.Intent?) {
        val title = intent?.getStringExtra(EXTRA_SUGGEST_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val id = intent.getIntExtra(EXTRA_SUGGEST_ID, 0)
        intent.removeExtra(EXTRA_SUGGEST_TITLE) // don't redo it if the activity is recreated
        androidx.core.app.NotificationManagerCompat.from(this).cancel(id)
        app.mido.suggest.PendingSuggestion.value =
            app.mido.suggest.PendingSuggestion.Item(title, intent.getStringExtra(EXTRA_SUGGEST_NOTES).orEmpty(), id, intent.getBooleanExtra(EXTRA_SUGGEST_PLAIN, false))
    }

    companion object {
        const val EXTRA_SUGGEST_TITLE = "suggest_title"
        const val EXTRA_SUGGEST_NOTES = "suggest_notes"
        const val EXTRA_SUGGEST_ID = "suggest_id"
        const val EXTRA_SUGGEST_PLAIN = "suggest_plain"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.mido.ui.theme.ThemeState.load(this)
        enableEdgeToEdge()
        handleSuggestion(intent)
        setContent {
            MidoTheme {
                TodoApp()
            }
        }
    }
}
