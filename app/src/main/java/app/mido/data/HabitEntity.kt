package app.mido.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A habit. [daysMask] has one bit per weekday (Monday = bit 0 ... Sunday = bit 6); all seven bits means daily.
 * When [weeklyTarget] is above 0 the habit is "N times a week" on any days and [daysMask] is ignored.
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val daysMask: Int = HabitSchedule.ALL_DAYS,
    val weeklyTarget: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One tick: [habitId] was done on the day [epochDay] (a LocalDate's toEpochDay). */
@Entity(tableName = "habit_checks", primaryKeys = ["habitId", "epochDay"])
data class HabitCheck(val habitId: Long, val epochDay: Long)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAt ASC, id ASC")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY createdAt ASC, id ASC")
    suspend fun getHabits(): List<HabitEntity>

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabit(id: Long)

    @Query("DELETE FROM habit_checks WHERE habitId = :id")
    suspend fun deleteChecksFor(id: Long)

    @Query("SELECT * FROM habit_checks WHERE epochDay >= :sinceDay")
    fun observeChecksSince(sinceDay: Long): Flow<List<HabitCheck>>

    @Query("SELECT * FROM habit_checks WHERE epochDay BETWEEN :from AND :to")
    suspend fun getChecks(from: Long, to: Long): List<HabitCheck>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCheck(check: HabitCheck)

    @Query("DELETE FROM habit_checks WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun removeCheck(habitId: Long, epochDay: Long)

    @Query("SELECT COUNT(*) FROM habit_checks WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun countCheck(habitId: Long, epochDay: Long): Int
}

/** Ticking and un-ticking habits. Shared by the app and the widget. */
class HabitRepository(private val dao: HabitDao) {
    /** Flips the tick for [habitId] on [epochDay]. Returns true if it is now ticked. */
    suspend fun toggle(habitId: Long, epochDay: Long): Boolean {
        return if (dao.countCheck(habitId, epochDay) > 0) {
            dao.removeCheck(habitId, epochDay)
            false
        } else {
            dao.addCheck(HabitCheck(habitId, epochDay))
            true
        }
    }

    suspend fun delete(habitId: Long) {
        dao.deleteChecksFor(habitId)
        dao.deleteHabit(habitId)
    }
}
