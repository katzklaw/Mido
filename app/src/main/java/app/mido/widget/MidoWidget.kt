package app.mido.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import androidx.glance.unit.ColorProvider
import app.mido.MainActivity
import app.mido.QuickAddActivity
import app.mido.data.AppDatabase
import app.mido.data.parsedRepeat
import app.mido.data.parsedTrigger
import app.mido.reminders.Reminders
import app.mido.settings.AppSettings
import app.mido.settings.WidgetTheme
import app.mido.ui.Fmt
import app.mido.ui.NextUpLine
import app.mido.ui.TimelineBuilder
import app.mido.ui.TimelineEntry
import java.time.LocalDateTime

class MidoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MidoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.scheduleTick(context)
    }
}

/** The colours the widget draws with, from the chosen theme and background opacity. */
internal class Palette(theme: WidgetTheme, opacityPercent: Int) {
    val background = ColorProvider(Color(theme.background).copy(alpha = opacityPercent / 100f))
    val backgroundSolid = ColorProvider(Color(theme.background))
    val text = ColorProvider(Color(theme.text))
    val muted = ColorProvider(Color(theme.muted))
    val accent = ColorProvider(Color(theme.accent))
    val onAccent = ColorProvider(Color(theme.onAccent))
    val line = ColorProvider(Color(theme.accent).copy(alpha = 0.55f))
    val overdue = ColorProvider(Color(0xFFFF6B70))
}

/** A timeline for the home screen: the next-up line on top, then the scrollable timeline, a + to add. */
class MidoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = LocalDateTime.now()
        val model = runCatching { WidgetData.load(context, now) }.getOrNull()
        val settings = AppSettings(context)
        val pro = app.mido.pro.Entitlements.isPro(context)
        val palette = Palette(
            app.mido.pro.ProPolicy.effectiveTheme(settings.widgetTheme, pro),
            app.mido.pro.ProPolicy.effectiveOpacity(settings.widgetOpacity, pro),
        )
        provideContent {
            GlanceTheme { WidgetContent(context, model, now, palette) }
        }
    }
}

val TaskIdKey = ActionParameters.Key<Long>("task_id")

/** Checking a task in the widget completes it (repeating tasks roll to their next date). */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        val task = AppDatabase.get(context).taskDao().getById(id) ?: return
        // Chime once the change is saved (not before), so what you hear matches what the widget will show.
        Reminders.repository(context).toggleComplete(task, onSaved = { if (!task.completed) app.mido.Chime.playDone(context) })
        MidoWidget().update(context, glanceId)
    }
}

private val TimeWidth = 48.dp
private val LineWidth = 18.dp

@Composable
private fun WidgetContent(context: Context, model: WidgetModel?, now: LocalDateTime, p: Palette) {
    // The + is the only thing that adds a task; everything else opens the app.
    val addAction = actionStartActivity(Intent(context, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val entries = model?.entries.orEmpty()
    val nextUp = model?.nextUp ?: NextUpLine("Mido", "Tap to open")

    Column(
        GlanceModifier
            .fillMaxSize()
            .background(p.background)
            .cornerRadius(24.dp)
            .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 4.dp)
            .clickable(openApp),
    ) {
        // Top strip: what's next ("Leave for X in 39 mins"), instead of a "Today" title.
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    nextUp.headline,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.accent),
                    maxLines = 1,
                )
                Text(nextUp.sub, style = TextStyle(fontSize = 10.sp, color = p.muted), maxLines = 1)
            }
            Spacer(GlanceModifier.width(6.dp))
            Box(
                GlanceModifier
                    .size(30.dp)
                    .background(p.accent)
                    .cornerRadius(15.dp)
                    .clickable(addAction),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = p.onAccent))
            }
        }
        Spacer(GlanceModifier.height(3.dp))

        if (entries.none { it !is TimelineEntry.Header && it !is TimelineEntry.Now }) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing scheduled. Tap + to add.", style = TextStyle(fontSize = 12.sp, color = p.muted))
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(entries) { entry -> EntryRow(entry, p, openApp) }
            }
        }
    }
}

/** The vertical timeline line with an optional node, like the one in the app. */
@Composable
private fun LineColumn(p: Palette, node: (@Composable () -> Unit)?) {
    Box(GlanceModifier.width(LineWidth).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
        Box(GlanceModifier.width(2.dp).fillMaxHeight().background(p.line)) {}
        if (node != null) node()
    }
}

@Composable
private fun Dot(p: Palette, size: Int, color: ColorProvider, hollow: Boolean, topPad: Int = 4) {
    Box(GlanceModifier.padding(top = topPad.dp)) {
        Box(
            GlanceModifier.size(size.dp).background(color).cornerRadius((size / 2f).dp),
            contentAlignment = Alignment.Center,
        ) {
            if (hollow) {
                Box(
                    GlanceModifier.size((size - 5).dp).background(p.backgroundSolid).cornerRadius(((size - 5) / 2f).dp),
                ) {}
            }
        }
    }
}

@Composable
private fun TimeText(text: String, color: ColorProvider, bold: Boolean = false) {
    Text(
        text,
        modifier = GlanceModifier.width(TimeWidth).padding(end = 5.dp, top = 2.dp),
        style = TextStyle(
            fontSize = 10.sp,
            color = color,
            textAlign = TextAlign.End,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
        maxLines = 1,
    )
}

@Composable
private fun EntryRow(entry: TimelineEntry, p: Palette, openApp: Action) {
    val rowMod = GlanceModifier.fillMaxWidth().clickable(openApp)

    when (entry) {
        is TimelineEntry.Header -> Row(rowMod) {
            Spacer(GlanceModifier.width(TimeWidth))
            LineColumn(p, null)
            Text(
                entry.label,
                modifier = GlanceModifier.padding(top = 8.dp, bottom = 3.dp, start = 4.dp),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (entry.overdue) p.overdue else p.text,
                ),
            )
        }

        is TimelineEntry.Now -> Row(rowMod) {
            TimeText(Fmt.time(entry.at), p.accent, bold = true)
            LineColumn(p) { Dot(p, 10, p.accent, hollow = false, topPad = 3) }
            Box(GlanceModifier.defaultWeight().padding(top = 7.dp, start = 4.dp, end = 4.dp, bottom = 7.dp)) {
                Box(GlanceModifier.fillMaxWidth().height(2.dp).background(p.accent)) {}
            }
        }

        is TimelineEntry.Habit -> Unit // habits have their own widget

        is TimelineEntry.Item -> {
            val task = entry.task
            val due = TimelineBuilder.dueOf(task)
            val time = when {
                due == null -> ""
                task.allDay -> "All day"
                else -> Fmt.time(due)
            }
            val nodeColor = when {
                task.completed -> p.muted
                entry.overdue -> p.overdue
                else -> p.accent
            }
            val meta = buildList {
                task.parsedRepeat?.let { add("🔁 ${it.describe()}") }
                task.locationName?.let { add("📍 ${Fmt.placeLabel(it, task.parsedTrigger)}") }
            }.joinToString("  ·  ")
            Row(rowMod, verticalAlignment = Alignment.Top) {
                TimeText(time, p.muted)
                LineColumn(p) { Dot(p, 9, nodeColor, hollow = !task.completed) }
                CheckBox(
                    checked = task.completed,
                    onCheckedChange = actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to task.id)),
                    colors = CheckboxDefaults.colors(checkedColor = p.accent, uncheckedColor = p.muted),
                )
                Column(GlanceModifier.defaultWeight().padding(top = 3.dp, bottom = 3.dp)) {
                    Text(
                        task.title,
                        style = TextStyle(fontSize = 12.sp, color = if (task.completed) p.muted else p.text),
                        maxLines = 2,
                    )
                    if (meta.isNotEmpty()) {
                        Text(meta, style = TextStyle(fontSize = 10.sp, color = p.muted), maxLines = 1)
                    }
                }
            }
        }

        is TimelineEntry.Event -> {
            val e = entry.event
            Row(rowMod, verticalAlignment = Alignment.Top) {
                TimeText(if (e.allDay) "All day" else Fmt.time(e.startMs), p.muted)
                LineColumn(p) { Dot(p, 9, p.accent, hollow = false) }
                Column(GlanceModifier.defaultWeight().padding(start = 4.dp, top = 3.dp, bottom = 5.dp)) {
                    Text("📅 ${e.title}", style = TextStyle(fontSize = 12.sp, color = p.text), maxLines = 2)
                    Text(
                        listOfNotNull(Fmt.eventTime(e).takeIf { !e.allDay }, e.location).joinToString("  ·  "),
                        style = TextStyle(fontSize = 10.sp, color = p.muted),
                        maxLines = 1,
                    )
                }
            }
        }

        is TimelineEntry.Drive -> {
            val leg = entry.leg
            Row(rowMod, verticalAlignment = Alignment.Top) {
                TimeText(Fmt.time(leg.leaveByMs), p.muted)
                LineColumn(p) { Dot(p, 6, p.muted, hollow = false, topPad = 6) }
                Text(
                    "🚗 Leave by ${Fmt.time(leg.leaveByMs)} · ${leg.driveMinutes} min drive",
                    modifier = GlanceModifier.defaultWeight().padding(start = 4.dp, top = 3.dp, bottom = 3.dp),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = p.text),
                    maxLines = 1,
                )
            }
        }
    }
}
