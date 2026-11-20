package app.mido.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mido.data.TaskEntity
import java.time.LocalDateTime

@Composable
fun ListScreen(
    tasks: List<TaskEntity>,
    now: LocalDateTime,
    onToggle: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    onPin: ((TaskEntity) -> Unit)? = null,
    onClearCompleted: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val finished = tasks.count { it.completed }
    if (tasks.isEmpty()) {
        Text(
            "Nothing here yet. Tap + to add your first task.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(24.dp),
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (finished > 0 && onClearCompleted != null) {
            item(key = "clear-finished") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { confirmClear = true }) { Text("Clear $finished finished") }
                }
            }
        }
        items(tasks, key = { it.id }) { task ->
            TaskCard(
                task = task,
                now = now,
                showDue = true,
                onToggle = { onToggle(task) },
                onDelete = { onDelete(task) },
                onEdit = { onEdit(task) },
                onPin = onPin?.let { pin -> { pin(task) } },
            )
        }
    }

    if (confirmClear && onClearCompleted != null) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear finished tasks?") },
            text = { Text("This permanently deletes the $finished finished task${if (finished == 1) "" else "s"}. Open and repeating tasks are not touched.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClearCompleted() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
