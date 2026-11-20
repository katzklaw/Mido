package app.mido.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mido.data.TaskEntity
import app.mido.ui.theme.Accent
import app.mido.ui.theme.OverdueRed
import java.time.LocalDateTime
import java.time.ZoneId

private val TimeColumnWidth = 72.dp
private val LineColumnWidth = 32.dp
private val NodeCenterY = 24.dp

@Composable
fun TimelineScreen(
    entries: List<TimelineEntry>,
    now: LocalDateTime,
    onToggle: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    onPin: ((TaskEntity) -> Unit)? = null,
    onToggleHabit: (Long, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is TimelineEntry.Header -> HeaderRow(entry)
                is TimelineEntry.Item -> ItemRow(entry, now, onToggle, onDelete, onEdit, onPin)
                is TimelineEntry.Event -> EventRow(entry, now)
                is TimelineEntry.Drive -> DriveRow(entry)
                is TimelineEntry.Habit -> HabitRow(entry, onToggleHabit)
                is TimelineEntry.Now -> NowRow(entry)
            }
        }
    }
}

/** The vertical line (and optional node) that runs down the middle of the timeline. */
@Composable
private fun LineColumn(
    nodeColor: Color?,
    hollow: Boolean,
    modifier: Modifier = Modifier,
    nodeRadius: Float = 6f,
) {
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier
            .width(LineColumnWidth)
            .fillMaxHeight()
            .drawBehind {
                val x = size.width / 2
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                if (nodeColor != null) {
                    val center = Offset(x, NodeCenterY.toPx())
                    drawCircle(nodeColor, radius = nodeRadius.dp.toPx(), center = center)
                    if (hollow) drawCircle(background, radius = (nodeRadius - 2.5f).dp.toPx(), center = center)
                }
            },
    )
}

@Composable
private fun HeaderRow(entry: TimelineEntry.Header) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(TimeColumnWidth))
        LineColumn(nodeColor = null, hollow = false)
        Box(Modifier.padding(top = 14.dp, bottom = 8.dp)) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (entry.overdue) OverdueRed else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ItemRow(
    entry: TimelineEntry.Item,
    now: LocalDateTime,
    onToggle: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    onPin: ((TaskEntity) -> Unit)? = null,
) {
    val task = entry.task
    val due = TimelineBuilder.dueOf(task)
    val timeLabel = when {
        due == null -> ""
        task.allDay -> "All day"
        else -> Fmt.time(due)
    }
    val nodeColor = when {
        task.completed -> MaterialTheme.colorScheme.onSurfaceVariant
        entry.overdue -> OverdueRed
        else -> MaterialTheme.colorScheme.primary
    }

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier.width(TimeColumnWidth).padding(top = 17.dp, end = 6.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LineColumn(nodeColor = nodeColor, hollow = !task.completed)
        Box(Modifier.weight(1f).padding(end = 12.dp, bottom = 10.dp)) {
            TaskCard(
                task = task,
                now = now,
                overdue = entry.overdue,
                showDue = entry.overdue,
                onToggle = { onToggle(task) },
                onDelete = { onDelete(task) },
                onEdit = { onEdit(task) },
                onPin = onPin?.let { pin -> { pin(task) } },
            )
        }
    }
}

@Composable
private fun HabitRow(entry: TimelineEntry.Habit, onToggleHabit: (Long, Long) -> Unit) {
    val day = entry.day
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(TimeColumnWidth).padding(top = 17.dp, end = 6.dp), contentAlignment = Alignment.TopEnd) {
            Text("Habit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LineColumn(nodeColor = MaterialTheme.colorScheme.tertiary, hollow = !day.checked)
        Box(Modifier.weight(1f).padding(end = 12.dp, bottom = 10.dp)) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = day.checked,
                        enabled = day.canCheck,
                        onCheckedChange = { onToggleHabit(day.habit.id, day.date.toEpochDay()) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(day.habit.name, style = MaterialTheme.typography.bodyLarge)
                        day.note?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowRow(entry: TimelineEntry.Now) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.width(TimeColumnWidth).padding(top = 14.dp, end = 6.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                text = Fmt.time(entry.at),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Accent,
            )
        }
        LineColumn(nodeColor = Accent, hollow = false, nodeRadius = 8f)
        Box(
            Modifier
                .weight(1f)
                .padding(top = NodeCenterY - 1.dp, end = 12.dp, bottom = 14.dp)
                .height(2.dp)
                .background(Accent, RoundedCornerShape(1.dp)),
        )
    }
}

/** A calendar event: filled node, card with the time range and place. */
@Composable
private fun EventRow(entry: TimelineEntry.Event, now: LocalDateTime) {
    val e = entry.event
    val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val over = !e.allDay && e.endMs < nowMs
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier.width(TimeColumnWidth).padding(top = 17.dp, end = 6.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                text = if (e.allDay) "All day" else Fmt.time(e.startMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LineColumn(nodeColor = if (over) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary, hollow = false)
        Box(Modifier.weight(1f).padding(end = 12.dp, bottom = 10.dp)) {
            Card(
                onClick = { openInCalendar(context, e) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "📅 ${e.title}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (over) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        Fmt.eventTime(e),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    e.location?.let {
                        Text(
                            "📍 $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** "Leave by 4:25 PM · 25 min drive" marker that sits just before the event it is for. */
@Composable
private fun DriveRow(entry: TimelineEntry.Drive) {
    val leg = entry.leg
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier.width(TimeColumnWidth).padding(top = 13.dp, end = 6.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                text = Fmt.time(leg.leaveByMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LineColumn(nodeColor = MaterialTheme.colorScheme.onSurfaceVariant, hollow = false, nodeRadius = 4f)
        Column(Modifier.weight(1f).padding(top = 10.dp, bottom = 10.dp, end = 12.dp)) {
            Text(
                "🚗 Leave by ${Fmt.time(leg.leaveByMs)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${leg.driveMinutes} min drive with traffic · to ${leg.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
