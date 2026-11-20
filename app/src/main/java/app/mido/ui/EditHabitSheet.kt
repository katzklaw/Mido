package app.mido.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mido.data.HabitEntity
import app.mido.data.HabitSchedule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private enum class Freq(val label: String) { Daily("Every day"), Days("Certain days"), Weekly("Times a week") }

/** Add or edit a habit. [habit] null means a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHabitSheet(
    habit: HabitEntity?,
    onDismiss: () -> Unit,
    onSave: (HabitEntity) -> Unit,
    onDelete: (HabitEntity) -> Unit,
) {
    var name by remember { mutableStateOf(habit?.name ?: "") }
    var freq by remember {
        mutableStateOf(
            when {
                habit == null -> Freq.Daily
                HabitSchedule.isWeekly(habit) -> Freq.Weekly
                HabitSchedule.isDaily(habit) -> Freq.Daily
                else -> Freq.Days
            },
        )
    }
    var mask by remember { mutableIntStateOf(if (habit != null && !HabitSchedule.isWeekly(habit)) habit.daysMask else HabitSchedule.ALL_DAYS) }
    var target by remember { mutableIntStateOf(habit?.weeklyTarget?.takeIf { it > 0 } ?: 3) }
    var confirmDelete by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && (freq != Freq.Days || (mask and HabitSchedule.ALL_DAYS) != 0)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (habit == null) "New habit" else "Edit habit", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Habit") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Freq.entries.forEach { f ->
                    FilterChip(selected = freq == f, onClick = { freq = f }, label = { Text(f.label) })
                }
            }
            when (freq) {
                Freq.Days -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(
                            selected = (mask and HabitSchedule.bit(d)) != 0,
                            onClick = { mask = mask xor HabitSchedule.bit(d) },
                            label = { Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
                        )
                    }
                }
                Freq.Weekly -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..7).forEach { n ->
                        FilterChip(selected = target == n, onClick = { target = n }, label = { Text("$n") })
                    }
                }
                Freq.Daily -> {}
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (habit != null) {
                    TextButton(onClick = { if (confirmDelete) onDelete(habit) else confirmDelete = true }) {
                        Text(if (confirmDelete) "Tap again to delete" else "Delete", color = MaterialTheme.colorScheme.error)
                    }
                } else Text("")
                Button(
                    enabled = canSave,
                    onClick = {
                        val base = habit ?: HabitEntity(name = "")
                        onSave(
                            base.copy(
                                name = name.trim(),
                                daysMask = if (freq == Freq.Days) mask and HabitSchedule.ALL_DAYS else HabitSchedule.ALL_DAYS,
                                weeklyTarget = if (freq == Freq.Weekly) target else 0,
                            ),
                        )
                    },
                ) { Text("Save") }
            }
            Text("", Modifier.padding(bottom = 16.dp))
        }
    }
}
