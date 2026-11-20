package app.mido.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import app.mido.Chime
import app.mido.MainActivity
import app.mido.data.AppDatabase
import app.mido.data.HabitCheck
import app.mido.data.HabitEntity
import app.mido.data.HabitRepository
import app.mido.data.HabitSchedule
import app.mido.pro.Entitlements
import app.mido.pro.ProPolicy
import app.mido.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DayStyle
import java.time.temporal.WeekFields
import java.util.Locale

class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
}

object HabitWidgetUpdater {
    fun refresh(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch { runCatching { HabitWidget().updateAll(app) } }
    }
}

private class HabitModel(
    val habits: List<HabitEntity>,
    val checks: Map<Long, Set<Long>>,
    val today: LocalDate,
    val week: List<LocalDate>,
)

val HabitIdKey = ActionParameters.Key<Long>("habit_id")
val EpochDayKey = ActionParameters.Key<Long>("epoch_day")

/** Ticking a box in the widget. */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        val day = parameters[EpochDayKey] ?: return
        val ticked = HabitRepository(AppDatabase.get(context).habitDao()).toggle(id, day)
        HabitWidget().updateAll(context)
        if (ticked) Chime.playDone(context)
    }
}

/** A column of habit names with seven boxes each, for this week. */
class HabitWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val first: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val week = HabitSchedule.weekDates(today, first)
        val dao = AppDatabase.get(context).habitDao()
        val from = week.first().toEpochDay()
        val to = week.last().toEpochDay()
        val firstHabits = runCatching { dao.getHabits() }.getOrDefault(emptyList())
        val firstChecks = runCatching { dao.getChecks(from, to) }.getOrDefault(emptyList())
        val settings = AppSettings(context)
        val pro = Entitlements.isPro(context)
        val palette = Palette(
            ProPolicy.effectiveTheme(settings.widgetTheme, pro),
            ProPolicy.effectiveOpacity(settings.widgetOpacity, pro),
        )
        provideContent {
            // Read inside the composition and follow the database, so a tick shows up at once. Glance may keep an
            // old composition alive between updates, and anything read once outside here would go stale.
            val habits by dao.observeHabits().collectAsState(firstHabits)
            val ticks by dao.observeChecksSince(from).collectAsState(firstChecks)
            val model = HabitModel(
                habits,
                ticks.filter { it.epochDay <= to }.groupBy({ it.habitId }, { it.epochDay }).mapValues { it.value.toSet() },
                today,
                week,
            )
            GlanceTheme { HabitContent(context, model, palette) }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HabitContent(context: Context, model: HabitModel?, p: Palette) {
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    Column(
        GlanceModifier.fillMaxSize().background(p.background).cornerRadius(24.dp)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
    ) {
        if (model == null || model.habits.isEmpty()) {
            Box(GlanceModifier.fillMaxSize().clickable(openApp), contentAlignment = Alignment.Center) {
                Text("No habits yet. Open Mido to add one.", style = TextStyle(fontSize = 12.sp, color = p.muted))
            }
            return@Column
        }
        Row(GlanceModifier.fillMaxWidth().clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
            Text("Habits", modifier = GlanceModifier.defaultWeight(), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.accent))
            model.week.forEach { d ->
                Box(GlanceModifier.width(CELL), contentAlignment = Alignment.Center) {
                    Text(
                        d.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()),
                        style = TextStyle(
                            fontSize = 10.sp, textAlign = TextAlign.Center,
                            fontWeight = if (d == model.today) FontWeight.Bold else FontWeight.Normal,
                            color = if (d == model.today) p.accent else p.muted,
                        ),
                    )
                }
            }
        }
        // Plain rows, not a scrolling list: Glance lists keep showing stale rows after an update.
        Column(GlanceModifier.fillMaxSize()) {
            for (h in model.habits.take(MAX_ROWS)) {
                val done = model.checks[h.id].orEmpty()
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        h.name, modifier = GlanceModifier.defaultWeight().clickable(openApp), maxLines = 1,
                        style = TextStyle(fontSize = 12.sp, color = p.text),
                    )
                    model.week.forEach { d ->
                        val ticked = d.toEpochDay() in done
                        val usable = HabitSchedule.scheduledOn(h, d) && !d.isAfter(model.today)
                        Box(GlanceModifier.width(CELL), contentAlignment = Alignment.Center) {
                            val box = GlanceModifier.size(20.dp).cornerRadius(5.dp)
                                .background(if (ticked) p.accent else p.line)
                            val mod = if (usable || ticked) {
                                box.clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(HabitIdKey to h.id, EpochDayKey to d.toEpochDay())))
                            } else box
                            Box(mod, contentAlignment = Alignment.Center) {
                                if (ticked) Text("✓", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.onAccent))
                                else if (usable) Box(GlanceModifier.size(16.dp).cornerRadius(4.dp).background(p.backgroundSolid)) {}
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CELL = 30.dp
private const val MAX_ROWS = 10
