package app.mido.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.mido.calendar.CalendarInfo
import app.mido.settings.WidgetTheme
import app.mido.widget.WidgetUpdater

private fun has(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    state: TodoViewModel.SettingsState,
    calendars: List<CalendarInfo>,
    onLoadCalendars: () -> Unit,
    onBufferChange: (Int) -> Unit,
    onSyncCalendar: (Long) -> Unit,
    onRearm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingEnable by remember { mutableStateOf(false) }

    val syncOn = state.syncCalendarId >= 0

    // Pick a sensible default the first time sync is switched on: the calendar named after the account.
    fun enableWith(list: List<CalendarInfo>) {
        val choice = list.firstOrNull { it.name == it.account } ?: list.firstOrNull()
        if (choice != null) onSyncCalendar(choice.id)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.WRITE_CALENDAR] == true) {
            pendingEnable = true
            onLoadCalendars()
        }
    }

    LaunchedEffect(Unit) { if (has(context, Manifest.permission.READ_CALENDAR)) onLoadCalendars() }
    LaunchedEffect(calendars, pendingEnable) {
        if (pendingEnable && calendars.isNotEmpty()) {
            pendingEnable = false
            enableWith(calendars)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)

            var pro by remember { mutableStateOf(app.mido.pro.Entitlements.isPro(context)) }
            ProSection(onChanged = { pro = app.mido.pro.Entitlements.isPro(context); onRearm() })

            val appSettings = remember { app.mido.settings.AppSettings(context) }
            var chime by remember { mutableStateOf(appSettings.chimeEnabled) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Completion chime", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "A little ding when you check a task off. Silent when your phone is on silent or vibrate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = chime, onCheckedChange = { chime = it; appSettings.chimeEnabled = it })
            }

            var loudLeave by remember { mutableStateOf(appSettings.loudLeaveAlert) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Loud \"time to leave\" alert", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Sounds an alarm tone when it's time to leave for an event with a drive, even on vibrate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = loudLeave, onCheckedChange = { loudLeave = it; appSettings.loudLeaveAlert = it })
            }

            var persistent by remember { mutableStateOf(appSettings.persistentAlerts) }
            var nagInterval by remember { mutableStateOf(appSettings.nagIntervalMin) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep alerting until I respond", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Reminders sound an alarm tone and repeat until you tap Done or Snooze. It stops on its own " +
                                "after about an hour. Plays even on vibrate, but Do Not Disturb can still silence it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = persistent, onCheckedChange = { persistent = it; appSettings.persistentAlerts = it })
                }
                if (persistent) {
                    Text("Repeat every", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        app.mido.reminders.NagPolicy.INTERVAL_CHOICES.forEach { m ->
                            FilterChip(
                                selected = nagInterval == m,
                                onClick = { nagInterval = m; appSettings.nagIntervalMin = m },
                                label = { Text("$m min") },
                            )
                        }
                    }
                }
            }

            var pin by remember { mutableStateOf(appSettings.pinCountdown) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pin leave-by countdown${if (pro) "" else " 🔒 Pro"}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Keeps a \"Leave for X\" notification with a live countdown while you have a drive coming up. " +
                            "Android may still let you swipe it away; it comes back at the next update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = pin,
                    onCheckedChange = {
                        if (it && !pro) {
                            proLockedToast(context)
                        } else {
                            pin = it
                            appSettings.pinCountdown = it
                            if (!it) app.mido.calendar.LeaveCountdown.stop(context) else onRearm()
                        }
                    },
                )
            }

            var widgetTheme by remember { mutableStateOf(appSettings.widgetTheme) }
            var widgetOpacity by remember { mutableStateOf(appSettings.widgetOpacity) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Home screen widget", style = MaterialTheme.typography.titleSmall)
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = widgetTheme == theme,
                            onClick = {
                                if (!app.mido.pro.ProPolicy.themeAllowed(theme, pro)) {
                                    proLockedToast(context)
                                } else {
                                    widgetTheme = theme
                                    appSettings.widgetTheme = theme
                                    WidgetUpdater.refresh(context)
                                }
                            },
                            label = { Text(theme.label + if (app.mido.pro.ProPolicy.themeAllowed(theme, pro)) "" else " 🔒") },
                        )
                    }
                }
                Text("Background opacity", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetTheme.OPACITIES.forEach { pct ->
                        FilterChip(
                            selected = widgetOpacity == pct,
                            onClick = {
                                if (!pro && pct != 100) {
                                    proLockedToast(context)
                                } else {
                                    widgetOpacity = pct
                                    appSettings.widgetOpacity = pct
                                    WidgetUpdater.refresh(context)
                                }
                            },
                            label = { Text("$pct%" + if (pro || pct == 100) "" else " 🔒") },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Leave-by buffer", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Extra minutes added on top of the drive time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onBufferChange(state.bufferMin - 5) }, enabled = state.bufferMin > 0) { Text("−") }
                    Text("${state.bufferMin} min", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { onBufferChange(state.bufferMin + 5) }, enabled = state.bufferMin < 30) { Text("+") }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Add my tasks to Google Calendar${if (pro) "" else " 🔒 Pro"}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Tasks with a date become calendar events. One-way: changes made in the calendar app don't come back to Mido.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = syncOn,
                        onCheckedChange = { on ->
                            if (on && !pro) {
                                proLockedToast(context)
                            } else if (!on) {
                                onSyncCalendar(-1L)
                            } else if (has(context, Manifest.permission.WRITE_CALENDAR) && has(context, Manifest.permission.READ_CALENDAR)) {
                                if (calendars.isNotEmpty()) enableWith(calendars) else { pendingEnable = true; onLoadCalendars() }
                            } else {
                                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                            }
                        },
                    )
                }
                if (syncOn) {
                    Text("Add them to:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    calendars.forEach { cal ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSyncCalendar(cal.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = cal.id == state.syncCalendarId, onClick = { onSyncCalendar(cal.id) })
                            Column {
                                Text(cal.name, style = MaterialTheme.typography.bodyMedium)
                                if (cal.account != cal.name) {
                                    Text(cal.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Text(
                        "Turning this off leaves events already created in your calendar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SuggestionsSection()

            ReliabilitySection()
        }
    }
}
