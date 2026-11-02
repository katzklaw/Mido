package app.mido.widget

import android.content.Context
import app.mido.calendar.CalendarRepository
import app.mido.calendar.LegCache
import app.mido.data.AppDatabase
import app.mido.ui.NextUpLine
import app.mido.ui.NextUpText
import app.mido.ui.TimelineBuilder
import app.mido.ui.TimelineEntry
import app.mido.ui.WidgetTimeline
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

class WidgetModel(val entries: List<TimelineEntry>, val nextUp: NextUpLine)

object WidgetData {
    const val MAX_ROWS = 250

    /** The same timeline as the app, trimmed to overdue + today + the next two weeks, plus the next-up line. */
    suspend fun load(context: Context, now: LocalDateTime = LocalDateTime.now()): WidgetModel {
        val tasks = AppDatabase.get(context).taskDao().observeAll().first()
        val calendar = CalendarRepository(context)
        val mine = tasks.mapNotNull { it.calendarEventId }.toSet()
        val events = calendar.upcoming(days = WidgetTimeline.DEFAULT_DAYS.toLong() + 1).filter { it.id !in mine }
        val legs = LegCache(context).load()

        val entries = WidgetTimeline.build(tasks, events, legs, now).take(MAX_ROWS)
        val next = TimelineBuilder.nextUp(tasks, now, events = events, drives = legs)
        return WidgetModel(entries, NextUpText.of(next, now))
    }
}
