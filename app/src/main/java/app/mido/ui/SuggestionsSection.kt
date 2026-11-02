package app.mido.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.mido.settings.AppSettings
import app.mido.suggest.MidoNotificationListener

/** Switches for the "call them back?" and "turn this text into a task?" suggestions. */
@Composable
fun SuggestionsSection() {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var access by remember { mutableStateOf(MidoNotificationListener.isEnabled(context)) }
    var callbacks by remember { mutableStateOf(settings.suggestCallbacks) }
    var texts by remember { mutableStateOf(settings.suggestFromTexts) }
    var contacts by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var lastText by remember { mutableStateOf(settings.lastTextSeen) }
    var recent by remember { mutableStateOf(settings.recentNotifications) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = MidoNotificationListener.isEnabled(context)
        lastText = settings.lastTextSeen
        recent = settings.recentNotifications
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contacts = it }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Suggestions", style = MaterialTheme.typography.titleSmall)
        Text(
            "Mido can offer to call someone back after a missed call, or turn a text like \"can you buy paper towels?\" " +
                "into a task. It reads notifications on your phone only; nothing is sent anywhere.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!access) {
            Button(onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                    context.startActivity(Reliability_appDetails(context))
                }
            }) { Text("Turn on notification access") }
            Text(
                "If Mido is greyed out there: Settings → Apps → Mido → ⋮ (top right) → Allow restricted settings, then try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ToggleRow("Missed calls: offer to call back", callbacks) { callbacks = it; settings.suggestCallbacks = it }
        ToggleRow("Texts: offer to make a task", texts) { texts = it; settings.suggestFromTexts = it }

        Text(
            if (lastText.isBlank()) "Last text Mido saw: none yet. Texts that start with a request (\"call me\", \"can you buy milk?\") get a suggestion."
            else "Last text Mido saw: $lastText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!app.mido.pro.Entitlements.isPro(context)) {
            Text(
                "Free: ${app.mido.pro.ProPolicy.freeTextSuggestionsLeft(settings.textSuggestionsUsed)} of " +
                    "${app.mido.pro.ProPolicy.FREE_TEXT_SUGGESTIONS} text-to-task suggestions left. Missed-call suggestions are always free.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (recent.isNotEmpty()) {
            Text(
                "Recent notifications Mido was shown (app · category):\n" + recent.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!contacts) {
            OutlinedButton(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                Text("Let Mido find numbers for contact names")
            }
        }
    }
}

private fun Reliability_appDetails(context: android.content.Context) = app.mido.reminders.Reliability.appDetails(context)

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
