package app.mido.ui

import app.mido.pro.Entitlements
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mido.Chime
import app.mido.calendar.CalendarInfo
import app.mido.calendar.CalendarRepository
import app.mido.calendar.DriveLeg
import app.mido.calendar.LastKnownOrigin
import app.mido.calendar.LeaveAlarms
import app.mido.calendar.LeaveByWorker
import app.mido.calendar.LeavePlanner
import app.mido.calendar.RoutesTravelTime
import app.mido.data.CalendarEvent
import app.mido.data.CalendarMonth
import app.mido.reminders.MissedSweep
import app.mido.data.TaskEntity
import app.mido.data.toEntity
import app.mido.parser.ParsedTask
import app.mido.reminders.Notifications
import app.mido.reminders.Reminders
import app.mido.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = Reminders.repository(app)
    private val calendar = CalendarRepository(app)
    private val travel = RoutesTravelTime()
    private val origin = LastKnownOrigin(app)
    private val settings = AppSettings(app)

    data class SettingsState(val bufferMin: Int, val syncCalendarId: Long)

    val settingsState = MutableStateFlow(SettingsState(settings.bufferMin, settings.syncCalendarId))
    val calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())

    /** Bump to re-read the calendar and recompute leave-by times (permissions, resume, etc.). */
    private val refresh = MutableStateFlow(0)

    init {
        Notifications.ensureChannels(app)
        LeaveByWorker.schedule(app)
        viewModelScope.launch {
            repo.rescheduleAll()
            runCatching { MissedSweep.run(app) }
        }
    }

    val tasks: StateFlow<List<TaskEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<CalendarEvent>> = calendar.observe(refresh.map { })
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The month shown in the calendar view, and that month's events (including the greyed-out edge days). */
    val visibleMonth = MutableStateFlow(YearMonth.now())

    val monthEvents: StateFlow<List<CalendarEvent>> = combine(visibleMonth, refresh) { m, _ -> m }
        .mapLatest { month ->
            val zone = ZoneId.systemDefault()
            val grid = CalendarMonth.gridDates(month, WeekFields.of(Locale.getDefault()).firstDayOfWeek)
            calendar.query(
                grid.first().atStartOfDay(zone).toInstant().toEpochMilli(),
                grid.last().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                zone,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMonth(month: YearMonth) { visibleMonth.value = month }

    private val fiveMinuteTick = flow {
        while (true) {
            emit(Unit)
            delay(5 * 60_000L)
        }
    }

    /** "Leave by" legs for the next few events that have a place, using live traffic. */
    val drives: StateFlow<List<DriveLeg>> = combine(events, fiveMinuteTick, refresh, settingsState) { e, _, _, s -> e to s.bufferMin }
        .mapLatest { (list, buffer) ->
            // Live-traffic leave-by is a Pro feature (it uses the Maps key); without Pro, clear any old legs.
            val legs = if (Entitlements.isPro(app)) {
                LeavePlanner(travel, origin, bufferMin = buffer).plan(list, System.currentTimeMillis())
            } else {
                emptyList()
            }
            legs.also { LeaveAlarms(app).sync(it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Re-arm alarms and geofences and re-read the calendar, e.g. after a permission was just granted. */
    fun rearm() {
        refresh.value++
        viewModelScope.launch { repo.rescheduleAll() }
    }

    fun setBuffer(minutes: Int) {
        settings.bufferMin = minutes
        settingsState.update { it.copy(bufferMin = settings.bufferMin) }
    }

    fun loadCalendars() {
        viewModelScope.launch { calendars.value = calendar.writableCalendars() }
    }

    /** Pass -1 to stop adding tasks to the calendar (events already created are left alone). */
    fun setSyncCalendar(id: Long) {
        settings.syncCalendarId = id
        settingsState.update { it.copy(syncCalendarId = id) }
        viewModelScope.launch { repo.rescheduleAll() }
    }

    fun update(edited: TaskEntity, previous: TaskEntity) {
        viewModelScope.launch { repo.update(edited, previous) }
    }

    /**
     * Adds a task. [fallbackDate] is used when the text had no date of its own (e.g. adding from a
     * tapped day in the calendar view); [notes] carries extra context like the text of a message.
     */
    fun add(parsed: ParsedTask, fallbackDate: LocalDate? = null, notes: String = "") {
        val p = if (parsed.due == null && fallbackDate != null) {
            parsed.copy(due = fallbackDate.atStartOfDay(), allDay = true)
        } else {
            parsed
        }
        viewModelScope.launch { repo.add(p.toEntity(notes)) }
    }

    fun toggle(task: TaskEntity) {
        // Checking something off (including a repeating task rolling forward) gets a chime; un-checking doesn't.
        if (!task.completed) Chime.playDone(app)
        viewModelScope.launch { repo.toggleComplete(task) }
    }

    fun delete(task: TaskEntity) {
        viewModelScope.launch { repo.delete(task) }
    }
}
