package app.mido.ui

import androidx.compose.foundation.layout.Arrangement
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
    modifier: Modifier = Modifier,
) {
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
        items(tasks, key = { it.id }) { task ->
            TaskCard(
                task = task,
                now = now,
                showDue = true,
                onToggle = { onToggle(task) },
                onDelete = { onDelete(task) },
                onEdit = { onEdit(task) },
            )
        }
    }
}
