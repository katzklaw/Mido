package app.mido.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasBackground(context: Context) =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

/**
 * Arrive/leave reminders only work with precise location, allowed "all the time".
 * Android makes that a two-step ask, so this walks through it. Shown only when a task needs it.
 */
@Composable
fun LocationPermissionBanner(needed: Boolean, onGranted: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var fine by remember { mutableStateOf(granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var background by remember { mutableStateOf(hasBackground(context)) }

    // Re-check when coming back from the system settings screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        fine = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        background = hasBackground(context)
        if (fine && background) onGranted()
    }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        background = ok
        if (ok) onGranted()
    }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fine) {
            onGranted()
            if (!background && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    if (!needed || (fine && background)) return

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (!fine) {
                    "Location reminders need your location."
                } else {
                    "Almost there: choose \"Allow all the time\" so Mido can notice when you arrive or leave while the app is closed."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row {
                Button(onClick = {
                    if (!fine) {
                        fineLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    } else {
                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }) { Text(if (!fine) "Allow location" else "Allow all the time") }
            }
        }
    }
}
