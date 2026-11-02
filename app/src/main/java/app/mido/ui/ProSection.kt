package app.mido.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.mido.pro.Entitlements

/** Tells the user a control needs Pro. */
fun proLockedToast(context: Context) {
    Toast.makeText(context, "That's a Pro feature. Enter your key under Mido Pro at the top of Settings.", Toast.LENGTH_LONG).show()
}

/** Shows whether Pro is unlocked and lets the user enter a licence key. */
@Composable
fun ProSection(onChanged: () -> Unit) {
    val context = LocalContext.current
    var license by remember { mutableStateOf(Entitlements.license(context)) }
    var key by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Mido Pro", style = MaterialTheme.typography.titleSmall)
        when {
            Entitlements.isDeveloperBuild -> Text("✅ Unlocked (developer build)", style = MaterialTheme.typography.bodyMedium)
            license != null -> {
                Text("✅ Unlocked for ${license?.name}. Thank you!", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    Entitlements.deactivate(context)
                    license = null
                    message = null
                    onChanged()
                }) { Text("Remove key") }
            }
            else -> {
                Text(
                    "Pro unlocks live-traffic leave-by and the pinned countdown, arrive/leave place reminders, adding tasks " +
                        "to Google Calendar, widget colours and transparency, and unlimited text-to-task suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; message = null },
                    label = { Text("Licence key") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val found = Entitlements.activate(context, key)
                        if (found != null) {
                            license = found
                            key = ""
                            message = null
                            onChanged()
                        } else {
                            message = "That key isn't valid. Paste the whole key, including the MIDO1 at the start."
                        }
                    },
                    enabled = key.isNotBlank(),
                ) { Text("Unlock Pro") }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
