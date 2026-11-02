package app.mido.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mido.data.AgendaItem
import app.mido.data.CalendarEvent
import app.mido.data.CalendarMonth
import app.mido.data.DayAgenda
import app.mido.data.DayCounts
import app.mido.data.TaskEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val TaskDot = Color(0xFFF5A524)

/** A month grid with a dot or two per day, and the selected day's agenda underneath. */
@Composable
fun CalendarScreen(
    month: YearMonth,
    selected: LocalDate,
    tasks: List<TaskEntity>,
    events: List<CalendarEvent>,
    now: LocalDateTime,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onToggle: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val weekStart = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val grid = remember(month, weekStart) { CalendarMonth.gridDates(month, weekStart) }
    val counts = remember(grid, tasks, events) {
        DayAgenda.counts(grid.first(), grid.last(), tasks, events, zone)
    }
    val agenda = remember(selected, tasks, events) { DayAgenda.forDay(selected, tasks, events, zone) }
    val today = now.toLocalDate()

    Column(modifier) {
        // Month header
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onMonthChange(YearMonth.from(today)); onSelect(today) },
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        // Weekday labels
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            for (i in 0 until 7) {
                val dow = DayOfWeek.of(((weekStart.value - 1 + i) % 7) + 1)
                Text(
                    dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Day cells
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            for (week in grid.chunked(7)) {
                Row(Modifier.fillMaxWidth()) {
                    for (date in week) {
                        DayCell(
                            date = date,
                            inMonth = YearMonth.from(date) == month,
                            isToday = date == today,
                            isSelected = date == selected,
                            counts = counts[date],
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Text(
            selected.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
        )

        if (agenda.isEmpty()) {
            Text(
                "Nothing on this day. Tap + to add something.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(agenda, key = {
                when (it) {
                    is AgendaItem.Task -> "t-${it.task.id}-${it.projected}"
                    is AgendaItem.Event -> "e-${it.event.key}"
                }
            }) { item ->
                when (item) {
                    is AgendaItem.Task -> TaskCard(
                        task = item.task,
                        now = now,
                        onToggle = { if (!item.projected) onToggle(item.task) },
                        onDelete = { onDelete(item.task) },
                        onEdit = { onEdit(item.task) },
                        modifier = if (item.projected) Modifier.alpha(0.6f) else Modifier,
                    )
                    is AgendaItem.Event -> EventAgendaCard(item.event)
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    counts: DayCounts?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .height(52.dp)
            .clickable(onClick = onClick)
            .alpha(if (inMonth) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .then(if (isSelected) Modifier.background(primary, CircleShape) else Modifier)
                .then(if (isToday && !isSelected) Modifier.border(1.5.dp, primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(Modifier.height(8.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (counts != null) {
                if (counts.events > 0) Dot(primary)
                if (counts.tasks > 0) Dot(TaskDot)
                if (counts.events + counts.tasks > 2) Dot(MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(5.dp).background(color, CircleShape))
}

@Composable
private fun EventAgendaCard(e: CalendarEvent) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        onClick = { openInCalendar(context, e) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("📅 ${e.title}", style = MaterialTheme.typography.bodyLarge)
            Text(Fmt.eventTime(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            e.location?.let {
                Text("📍 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
