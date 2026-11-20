package app.mido.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mido.data.CalendarMonth
import app.mido.data.HabitEntity
import app.mido.data.HabitSchedule
import app.mido.data.habitStats
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** A habit's record: streaks, the recent completion rate, and a month grid you can page back through. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitHistorySheet(
    habit: HabitEntity,
    checks: Set<Long>,
    today: LocalDate,
    weekStart: DayOfWeek,
    onToggle: (habitId: Long, epochDay: Long) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val created = Instant.ofEpochMilli(habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val stats = habitStats(habit, checks, today, weekStart, created)
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val grid = CalendarMonth.gridDates(month, weekStart)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(habit.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        HabitSchedule.describe(habit), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onEdit) { Text("Edit") }
            }

            val unit = if (stats.unit == "weeks") "weeks" else "days"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Streak", "🔥 ${stats.currentStreak}", unit, Modifier.weight(1f))
                Stat("Best", "${stats.bestStreak}", unit, Modifier.weight(1f))
                Stat(
                    if (stats.unit == "weeks") "Last 4 weeks" else "Last 30 days",
                    stats.percent?.let { "$it%" } ?: "–",
                    if (stats.possible == 0) "no data yet" else "${stats.done} of ${stats.possible}",
                    Modifier.weight(1f),
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.from(today)) { Text("›") }
            }
            Row(Modifier.fillMaxWidth()) {
                grid.take(7).forEach { d ->
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            grid.chunked(7).forEach { weekRow ->
                // Skip a trailing row that's entirely next month.
                if (weekRow.any { YearMonth.from(it) == month }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        weekRow.forEach { d ->
                            DayCell(
                                date = d,
                                inMonth = YearMonth.from(d) == month,
                                ticked = d.toEpochDay() in checks,
                                due = HabitSchedule.scheduledOn(habit, d),
                                future = d.isAfter(today),
                                isToday = d == today,
                                modifier = Modifier.weight(1f),
                                onClick = { onToggle(habit.id, d.toEpochDay()) },
                            )
                        }
                    }
                }
            }
            Text(
                "Tap a day to fill in or undo it.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun Stat(label: String, big: String, small: String, modifier: Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(big, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(small, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayCell(
    date: LocalDate, inMonth: Boolean, ticked: Boolean, due: Boolean, future: Boolean, isToday: Boolean,
    modifier: Modifier, onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val enabled = inMonth && !future
    val bg = when {
        !inMonth -> Color0
        ticked -> primary
        due && !future -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color0
    }
    val fg = when {
        !inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        ticked -> MaterialTheme.colorScheme.onPrimary
        future || !due -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier.aspectRatio(1f)
            .background(bg, CircleShape)
            .let { if (isToday) it.padding(1.dp) else it }
            .let { if (enabled) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${date.dayOfMonth}", color = fg, style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private val Color0 = androidx.compose.ui.graphics.Color.Transparent
