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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mido.data.HabitEntity
import app.mido.data.HabitSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Each habit is one small line of text with its seven day boxes underneath. Tap the name for its history. */
@Composable
fun HabitsScreen(
    habits: List<HabitEntity>,
    checks: Map<Long, Set<Long>>,
    today: LocalDate,
    weekStart: DayOfWeek,
    onToggle: (habitId: Long, epochDay: Long) -> Unit,
    onOpen: (HabitEntity) -> Unit,
) {
    val week = HabitSchedule.weekDates(today, weekStart)
    if (habits.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No habits yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap + to add one. Daily habits show up in your timeline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                week.forEach { d ->
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                        color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(habits, key = { it.id }) { h ->
            val done = checks[h.id].orEmpty()
            val sub = if (HabitSchedule.isWeekly(h)) {
                "${HabitSchedule.doneThisWeek(done, today, weekStart)}/${h.weeklyTarget} this week"
            } else HabitSchedule.describe(h)
            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(h) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        h.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    week.forEach { d ->
                        val ticked = d.toEpochDay() in done
                        val usable = ticked || (HabitSchedule.scheduledOn(h, d) && !d.isAfter(today))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            DayBox(ticked = ticked, usable = usable, isToday = d == today) { onToggle(h.id, d.toEpochDay()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBox(ticked: Boolean, usable: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = when {
        isToday -> primary
        usable -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    }
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.size(30.dp).clip(shape)
            .background(if (ticked) primary else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.5.dp, if (ticked) primary else outline, shape)
            .let { if (usable) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (ticked) Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
