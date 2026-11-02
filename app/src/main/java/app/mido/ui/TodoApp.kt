package app.mido.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mido.data.TaskEntity
import app.mido.data.parsedTrigger
import app.mido.ui.theme.Accent
import app.mido.ui.theme.HeaderDark
import kotlinx.coroutines.delay
import app.mido.calendar.LeaveMath
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ViewMode(val label: String) { Timeline("Timeline"), Calendar("Calendar"), Tasks("List") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp(vm: TodoViewModel = viewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val now by rememberNow()
    var showAdd by remember { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ViewMode.Timeline) }

    // Ask once for permission to show reminder notifications (Android 13+).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val events by vm.events.collectAsStateWithLifecycle()
    val drives by vm.drives.collectAsStateWithLifecycle()

    // Location is needed for arrive/leave tasks and for "when to leave" on events with a place.
    val needsLocation = remember(tasks, events) {
        tasks.any { !it.completed && it.parsedTrigger != null } ||
            events.any { LeaveMath.isPhysicalPlace(it.location) }
    }

    // Events Mido itself wrote into the calendar are already shown as tasks, so hide the duplicates.
    val shownEvents = remember(tasks, events) {
        val mine = tasks.mapNotNull { it.calendarEventId }.toSet()
        events.filter { it.id !in mine }
    }

    val entries = remember(tasks, shownEvents, drives, now) {
        TimelineBuilder.build(tasks, now, events = shownEvents, drives = drives)
    }

    val month by vm.visibleMonth.collectAsStateWithLifecycle()
    val monthEvents by vm.monthEvents.collectAsStateWithLifecycle()
    val shownMonthEvents = remember(tasks, monthEvents) {
        val mine = tasks.mapNotNull { it.calendarEventId }.toSet()
        monthEvents.filter { it.id !in mine }
    }
    var selectedEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)

    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                NextUpBanner(TimelineBuilder.nextUp(tasks, now, events = shownEvents, drives = drives), now)
                CalendarPermissionBanner(onGranted = vm::rearm)
                LocationPermissionBanner(needed = needsLocation, onGranted = vm::rearm)
                ReliabilityBanner(onClick = { showSettings = true })
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        ViewMode.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = mode == option,
                                onClick = { mode = option },
                                shape = SegmentedButtonDefaults.itemShape(index, ViewMode.entries.size),
                            ) { Text(option.label) }
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (mode) {
                ViewMode.Timeline -> TimelineScreen(
                    entries = entries,
                    now = now,
                    onToggle = vm::toggle,
                    onDelete = vm::delete,
                    onEdit = { editing = it },
                )
                ViewMode.Calendar -> CalendarScreen(
                    month = month,
                    selected = selectedDate,
                    tasks = tasks,
                    events = shownMonthEvents,
                    now = now,
                    onMonthChange = vm::setMonth,
                    onSelect = { d ->
                        selectedEpochDay = d.toEpochDay()
                        if (YearMonth.from(d) != month) vm.setMonth(YearMonth.from(d))
                    },
                    onToggle = vm::toggle,
                    onDelete = vm::delete,
                    onEdit = { editing = it },
                )
                ViewMode.Tasks -> ListScreen(
                    tasks = tasks,
                    now = now,
                    onToggle = vm::toggle,
                    onDelete = vm::delete,
                    onEdit = { editing = it },
                )
            }
        }
    }

    editing?.let { task ->
        EditTaskSheet(
            task = task,
            onDismiss = { editing = null },
            onSave = {
                vm.update(it, task)
                editing = null
            },
        )
    }

    if (showSettings) {
        SettingsSheet(
            state = settings,
            calendars = calendars,
            onLoadCalendars = vm::loadCalendars,
            onBufferChange = vm::setBuffer,
            onSyncCalendar = vm::setSyncCalendar,
            onRearm = vm::rearm,
            onDismiss = { showSettings = false },
        )
    }

    if (showAdd) {
        QuickAddSheet(
            onDismiss = { showAdd = false },
            onAdd = {
                vm.add(it, fallbackDate = if (mode == ViewMode.Calendar) selectedDate else null)
                showAdd = false
            },
        )
    }
}

/** Dark header in the spirit of Midu's "Design Review starts in 20 mins" banner. */
@Composable
private fun NextUpBanner(next: NextUp?, now: LocalDateTime) {
    val line = NextUpText.of(next, now)
    Surface(color = HeaderDark, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(line.headline, style = MaterialTheme.typography.titleMedium, color = Accent)
            Text(line.sub, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun rememberNow(): State<LocalDateTime> = produceState(initialValue = LocalDateTime.now()) {
    while (true) {
        delay(30_000)
        value = LocalDateTime.now()
    }
}
