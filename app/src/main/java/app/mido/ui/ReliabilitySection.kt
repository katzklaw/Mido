package app.mido.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.mido.reminders.Reliability

private fun open(context: Context, intent: Intent, fallback: Intent? = null) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        runCatching { fallback?.let { context.startActivity(it) } }
    }
}

@Composable
private fun CheckRow(ok: Boolean, label: String, fixLabel: String, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✅" else "⚠️", modifier = Modifier.padding(end = 10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (!ok) TextButton(onClick = onFix) { Text(fixLabel) }
    }
}

/** Everything needed to make reminders arrive on time, including the Samsung-specific bits. */
@Composable
fun ReliabilitySection() {
    val context = LocalContext.current
    var status by remember { mutableStateOf(Reliability.status(context)) }
    var testSent by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = Reliability.status(context) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Reliable reminders", style = MaterialTheme.typography.titleSmall)
        Text(
            "Reminders are set as exact alarms, which fire even when Mido is closed. Samsung's battery saver is the " +
                "usual reason one goes missing, so check these:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CheckRow(status.notificationsAllowed, "Notifications are allowed", "Fix") {
            open(context, Reliability.notificationSettings(context), Reliability.appDetails(context))
        }
        CheckRow(status.exactAlarmsAllowed, "Exact alarms are allowed", "Fix") {
            open(context, Reliability.exactAlarmSettings(context), Reliability.appDetails(context))
        }
        CheckRow(status.batteryUnrestricted, "Battery usage is unrestricted", "Fix") {
            open(context, Reliability.batteryPrompt(context), Reliability.appDetails(context))
        }

        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("On a Samsung (Galaxy Z Flip) also do this once:", style = MaterialTheme.typography.labelLarge)
                Text(
                    "1. Settings → Apps → Mido → Battery → choose Unrestricted.\n" +
                        "2. Settings → Battery → Background usage limits: turn off \"Put unused apps to sleep\", and make sure " +
                        "Mido is not listed under Sleeping apps or Deep sleeping apps (add it to Never sleeping apps).\n" +
                        "3. To see reminders on the cover screen: Settings → Cover screen → Notifications, allow Mido.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { open(context, Reliability.appDetails(context)) }) { Text("Open Mido's app settings") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                Reliability.scheduleTest(context, 30)
                testSent = true
            }) { Text("Send a test reminder") }
        }
        if (testSent) {
            Text(
                "A test reminder will arrive in 30 seconds. Close Mido and lock the phone to see if it gets through.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A thin warning strip on the home screen while something could make reminders late. */
@Composable
fun ReliabilityBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(Reliability.status(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = Reliability.status(context) }
    if (status.allGood) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(
                "⚠️ Reminders might arrive late. Tap to fix.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
