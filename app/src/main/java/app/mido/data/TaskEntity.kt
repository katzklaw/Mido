package app.mido.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** Due moment as epoch millis in the device time zone, or null for "anytime". */
    val dueAt: Long? = null,
    /** True when only the date matters (no specific time). */
    val allDay: Boolean = false,
    /** Encoded [RepeatRule], or null. */
    val repeatRule: String? = null,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Street address of the resolved place, shown as extra info. */
    val locationAddress: String? = null,
    /** Name of a [LocationTrigger], or null when the location is just info. */
    val locationTrigger: String? = null,
    /** Id of the calendar event Mido created for this task, if calendar sync is on. */
    val calendarEventId: Long? = null,
    /** Reminder time we last showed a notification for; lets a late-delivery check spot misses. */
    val notifiedAt: Long? = null,
    /** Arrive/leave reminders that come back every time, instead of finishing once. */
    @ColumnInfo(defaultValue = "0") val locationRepeat: Boolean = false,
    /** When a repeating place reminder last fired (used to ignore GPS jitter). */
    val lastLocationFiredAt: Long? = null,
    /** When a repeating place reminder was last checked off; it resets at the next firing. */
    val lastLocationDoneAt: Long? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

// Kept outside the entity so Room only sees the stored columns.
val TaskEntity.parsedRepeat: RepeatRule? get() = RepeatRule.decode(repeatRule)

val TaskEntity.parsedTrigger: LocationTrigger?
    get() = locationTrigger?.let { runCatching { LocationTrigger.valueOf(it) }.getOrNull() }
