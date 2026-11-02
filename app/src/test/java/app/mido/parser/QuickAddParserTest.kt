package app.mido.parser

import app.mido.data.LocationTrigger
import app.mido.data.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class QuickAddParserTest {
    // Monday, Oct 26 2026, 10:00 PM
    private val now = LocalDateTime.of(2026, 10, 26, 22, 0)

    private fun parse(s: String) = QuickAddParser.parse(s, now)

    @Test fun plainTitleHasNoDate() {
        val r = parse("call dentist")
        assertEquals("Call dentist", r.title)
        assertNull(r.due)
        assertNull(r.repeat)
        assertNull(r.locationName)
    }

    @Test fun tomorrowAtTime() {
        val r = parse("buy milk tomorrow at 5pm")
        assertEquals("Buy milk", r.title)
        assertEquals(LocalDateTime.of(2026, 10, 27, 17, 0), r.due)
        assertTrue(!r.allDay)
    }

    @Test fun weekdayIsAllDay() {
        val r = parse("call mom friday")
        assertEquals("Call mom", r.title)
        assertEquals(LocalDateTime.of(2026, 10, 30, 0, 0), r.due)
        assertTrue(r.allDay)
    }

    @Test fun nextWeekdayWithinThisWeekJumpsAWeek() {
        val r = parse("report next friday")
        assertEquals(LocalDateTime.of(2026, 11, 6, 0, 0), r.due)
    }

    @Test fun timeAlreadyPassedRollsToTomorrow() {
        val r = parse("workout at 6am")
        assertEquals(LocalDateTime.of(2026, 10, 27, 6, 0), r.due)
    }

    @Test fun weeklyRepeatOnDay() {
        val r = parse("take out trash every tuesday at 7pm")
        assertEquals("Take out trash", r.title)
        assertEquals(RepeatRule(RepeatRule.Freq.WEEKLY, 1, setOf(DayOfWeek.TUESDAY)), r.repeat)
        assertEquals(LocalDateTime.of(2026, 10, 27, 19, 0), r.due)
    }

    @Test fun multipleRepeatDays() {
        val r = parse("gym every mon, wed and fri")
        assertEquals("Gym", r.title)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            r.repeat?.days,
        )
        // Monday is today but no time given, so today counts.
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0), r.due)
    }

    @Test fun everyTwoWeeks() {
        val r = parse("water plants every 2 weeks")
        assertEquals("Water plants", r.title)
        assertEquals(RepeatRule(RepeatRule.Freq.WEEKLY, 2), r.repeat)
    }

    @Test fun everyWeekday() {
        val r = parse("standup every weekday at 9am")
        assertEquals(RepeatRule.WEEKDAYS, r.repeat?.days)
        assertEquals(LocalDateTime.of(2026, 10, 27, 9, 0), r.due)
    }

    @Test fun monthlyOnOrdinal() {
        val r = parse("pay rent on the 1st every month")
        assertEquals("Pay rent", r.title)
        assertEquals(RepeatRule.Freq.MONTHLY, r.repeat?.freq)
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), r.due)
    }

    @Test fun monthDayTimeAndAtMarkLocation() {
        val r = parse("dentist oct 30 at 2:30pm @Smile Dental")
        assertEquals("Dentist", r.title)
        assertEquals(LocalDateTime.of(2026, 10, 30, 14, 30), r.due)
        assertEquals("Smile Dental", r.locationName)
        assertNull(r.locationTrigger)
    }

    @Test fun leaveTrigger() {
        val r = parse("pick up gym bag when I leave work")
        assertEquals("Pick up gym bag", r.title)
        assertEquals("Work", r.locationName)
        assertEquals(LocationTrigger.LEAVE, r.locationTrigger)
        assertNull(r.due)
    }

    @Test fun arriveTrigger() {
        val r = parse("buy stamps when I get to Post Office")
        assertEquals("Buy stamps", r.title)
        assertEquals("Post Office", r.locationName)
        assertEquals(LocationTrigger.ARRIVE, r.locationTrigger)
    }

    @Test fun arriveHome() {
        val r = parse("take meds when I get home")
        assertEquals("Take meds", r.title)
        assertEquals("Home", r.locationName)
        assertEquals(LocationTrigger.ARRIVE, r.locationTrigger)
    }

    @Test fun capitalisedAtPlaceIsLocationInfo() {
        val r = parse("buy batteries at Costco tomorrow")
        assertEquals("Buy batteries", r.title)
        assertEquals("Costco", r.locationName)
        assertNull(r.locationTrigger)
        assertEquals(LocalDateTime.of(2026, 10, 27, 0, 0), r.due)
    }

    @Test fun lowercaseAtIsNotALocation() {
        val r = parse("look at the sky")
        assertEquals("Look at the sky", r.title)
        assertNull(r.locationName)
    }

    @Test fun lowercasePlaceAfterTimeIsLocation() {
        val r = parse("pick up prescription at 9am at costco")
        assertEquals("Costco", r.locationName)
        assertEquals("Pick up prescription", r.title)
        val d = parse("dentist tomorrow at smile dental")
        assertEquals("Smile Dental", d.locationName)
    }

    @Test fun ordinaryLowercaseAtStaysText() {
        assertNull(parse("look at that").locationName)
        assertNull(parse("call mom at night").locationName)
        assertNull(parse("finish at least two pages").locationName)
    }

    @Test fun everyTimeArrivalRepeats() {
        val a = parse("text husband i made it every time i get to work")
        assertEquals("Work", a.locationName)
        assertEquals(LocationTrigger.ARRIVE, a.locationTrigger)
        assertTrue(a.locationRepeat)
        assertTrue(a.title.startsWith("Text husband"))
        val b = parse("lock up whenever I leave the shop")
        assertEquals(LocationTrigger.LEAVE, b.locationTrigger)
        assertTrue(b.locationRepeat)
        val once = parse("text husband when i get to work")
        assertEquals(LocationTrigger.ARRIVE, once.locationTrigger)
        assertFalse(once.locationRepeat)
    }

    @Test fun relativeHours() {
        val r = parse("check oven in 2 hours")
        assertEquals("Check oven", r.title)
        assertEquals(LocalDateTime.of(2026, 10, 27, 0, 0), r.due)
        assertTrue(!r.allDay)
    }

    @Test fun remindMePrefixIsStripped() {
        val r = parse("remind me to call Sam tomorrow at noon")
        assertEquals("Call Sam", r.title)
        assertEquals(LocalDateTime.of(2026, 10, 27, 12, 0), r.due)
    }

    @Test fun tonightDefaultsToEvening() {
        val r = parse("laundry tonight")
        assertEquals(LocalDateTime.of(2026, 10, 26, 20, 0), r.due)
    }

    @Test fun repeatRuleNextOccurrence() {
        val tue = RepeatRule(RepeatRule.Freq.WEEKLY, 1, setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        assertEquals(LocalDateTime.of(2026, 10, 29, 19, 0), tue.nextAfter(LocalDateTime.of(2026, 10, 27, 19, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 3, 19, 0), tue.nextAfter(LocalDateTime.of(2026, 10, 29, 19, 0)))
        val biweekly = RepeatRule(RepeatRule.Freq.WEEKLY, 2, setOf(DayOfWeek.MONDAY))
        assertEquals(LocalDateTime.of(2026, 11, 9, 8, 0), biweekly.nextAfter(LocalDateTime.of(2026, 10, 26, 8, 0)))
    }

    @Test fun repeatRuleEncodeDecodeRoundTrip() {
        val rule = RepeatRule(RepeatRule.Freq.WEEKLY, 2, setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))
        assertEquals(rule, RepeatRule.decode(rule.encode()))
    }
}
