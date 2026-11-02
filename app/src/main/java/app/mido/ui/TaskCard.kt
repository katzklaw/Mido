package app.mido.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.mido.data.TaskEntity
import app.mido.data.parsedRepeat
import app.mido.data.parsedTrigger
import app.mido.ui.theme.OverdueRed
import java.time.LocalDateTime

@Composable
fun TaskCard(
    task: TaskEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    overdue: Boolean = false,
    showDue: Boolean = false,
    now: LocalDateTime = LocalDateTime.now(),
) {
    var expanded by remember { mutableStateOf(false) }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val proUnlocked = remember { app.mido.pro.Entitlements.isPro(appContext) }

    val meta = buildList {
        if (showDue) {
            TimelineBuilder.dueOf(task)?.let { add(Fmt.dueLabel(it, task.allDay, now.toLocalDate())) }
        }
        task.parsedRepeat?.let { add("🔁 ${it.describe()}") }
        task.locationName?.let { add("📍 ${Fmt.placeLabel(it, task.parsedTrigger, task.locationRepeat)}") }
        if (task.locationRepeat && task.lastLocationDoneAt != null &&
            task.lastLocationDoneAt >= (task.lastLocationFiredAt ?: 0L)
        ) add("✅ Done this time")
        if (task.locationName != null && task.parsedTrigger != null && !proUnlocked) {
            add("🔒 place reminder needs Pro")
        }
        if (task.locationName != null && task.parsedTrigger != null && task.latitude == null) {
            add("⚠️ place not found yet")
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(end = 12.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
                Column {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (task.completed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                    )
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) OverdueRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (expanded) {
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    task.locationAddress?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (task.notes.isNotBlank()) {
                        Text(task.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row {
                        TextButton(onClick = onEdit) { Text("Edit") }
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
                }
            }
        }
    }
}
