package app.mido.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.mido.data.LocationTrigger
import app.mido.data.RepeatPresets
import app.mido.data.TaskEdit
import app.mido.data.TaskEntity
import app.mido.data.parsedRepeat
import app.mido.data.parsedTrigger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Everything about a task you can change after adding it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskSheet(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dueDateTime = TimelineBuilder.dueOf(task, zone)
    var title by remember { mutableStateOf(task.title) }
    var notes by remember { mutableStateOf(task.notes) }
    var shadePinned by remember { mutableStateOf(task.shadePinned) }
    var date by remember { mutableStateOf(dueDateTime?.toLocalDate()) }
    var time by remember { mutableStateOf(if (task.allDay) null else dueDateTime?.toLocalTime()) }
    val repeatOptions = remember { RepeatPresets.optionsFor(task.parsedRepeat) }
    var repeatIndex by remember { mutableStateOf(RepeatPresets.indexOf(repeatOptions, task.parsedRepeat)) }
    var place by remember { mutableStateOf(task.locationName.orEmpty()) }
    var trigger by remember { mutableStateOf(task.parsedTrigger) }
    var everyTime by remember { mutableStateOf(task.locationRepeat) }
    var pinned by remember { mutableStateOf(task.pinned) }

    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }

    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit task", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text("Title") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = app.mido.data.Checklist.onEdit(notes, it) }, label = { Text("Notes") },
                minLines = 2, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Start a line with [ ] for a checklist item. Enter adds the next one.") },
            )
            TextButton(onClick = { notes = app.mido.data.Checklist.addItem(notes) }) { Text("☑ Add checklist item") }

            Text("When", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDate = true }) { Text(date?.format(dateFmt) ?: "Set date") }
                OutlinedButton(onClick = { showTime = true }, enabled = date != null) {
                    Text(time?.let { Fmt.time(LocalDate.now().atTime(it)) } ?: "All day")
                }
                if (date != null) {
                    TextButton(onClick = { date = null; time = null }) { Text("Clear") }
                }
            }
            if (date != null && time != null) {
                TextButton(onClick = { time = null }) { Text("Make it all day") }
            }

            Text("Repeat", style = MaterialTheme.typography.labelLarge)
            Column {
                OutlinedButton(onClick = { repeatMenu = true }) { Text(repeatOptions[repeatIndex].label) }
                DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                    repeatOptions.forEachIndexed { i, preset ->
                        DropdownMenuItem(text = { Text(preset.label) }, onClick = { repeatIndex = i; repeatMenu = false })
                    }
                }
            }

            Text("Place", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = place, onValueChange = { place = it }, label = { Text("e.g. Planet Fitness") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = trigger == null, onClick = { trigger = null }, label = { Text("Info only") })
                FilterChip(
                    selected = trigger == LocationTrigger.ARRIVE,
                    onClick = { trigger = LocationTrigger.ARRIVE },
                    label = { Text("On arrival") },
                )
                FilterChip(
                    selected = trigger == LocationTrigger.LEAVE,
                    onClick = { trigger = LocationTrigger.LEAVE },
                    label = { Text("When I leave") },
                )
            }

            if (trigger != null && !app.mido.pro.Entitlements.isPro(androidx.compose.ui.platform.LocalContext.current)) {
                Text(
                    "🔒 Arrive/leave reminders only fire with Mido Pro. The place still shows on the task.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (trigger != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Every time", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Checking it off only clears it for now; it comes back the next time you " +
                                if (trigger == LocationTrigger.ARRIVE) "arrive." else "leave.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = everyTime, onCheckedChange = { everyTime = it })
                }
            }

            if (date == null && repeatOptions[repeatIndex].rule == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("📌 Pin to Today", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Keeps this at the top of Today every day until you finish it. For things with no set day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = pinned, onCheckedChange = { pinned = it })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🔔 Keep in notifications", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Shows this task and its checklist in the notification shade and puts it back if swiped away, until you finish or unpin it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = shadePinned, onCheckedChange = { shadePinned = it })
            }

            Button(
                onClick = {
                    onSave(
                        TaskEdit.apply(
                            task, title, notes, date, time, repeatOptions[repeatIndex].rule,
                            place, trigger, zone,
                            everyTime = everyTime,
                            pinned = pinned,
                            shadePinned = shadePinned,
                        ),
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val context = LocalContext.current
        val state = rememberTimePickerState(
            initialHour = time?.hour ?: 9,
            initialMinute = time?.minute ?: 0,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}
