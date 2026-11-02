package app.mido

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import app.mido.data.toEntity
import app.mido.reminders.Reminders
import app.mido.ui.QuickAddSheet
import app.mido.ui.theme.MidoTheme
import kotlinx.coroutines.launch

/**
 * A small floating "add a task" box, opened from the home screen widget (and from message
 * suggestions). It shows only the quick-add sheet over whatever you were looking at.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefill = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        setContent {
            MidoTheme {
                QuickAddSheet(
                    initialText = prefill,
                    onDismiss = { finish() },
                    onAdd = { parsed ->
                        lifecycleScope.launch {
                            Reminders.repository(applicationContext).add(parsed.toEntity())
                            finish()
                        }
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_TEXT = "prefill_text"
    }
}
