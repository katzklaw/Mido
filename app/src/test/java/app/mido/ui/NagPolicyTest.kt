package app.mido.ui

import app.mido.reminders.NagPolicy
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class NagPolicyTest {
    @Test fun nextIsOneIntervalAway() {
        assertEquals(1_000L + 120_000L, NagPolicy.nextAt(0, 1_000L, 2))
    }

    @Test fun capsAtAboutAnHour() {
        assertEquals(30, NagPolicy.maxNags(2))
        assertEquals(6, NagPolicy.maxNags(10))
        assertNull(NagPolicy.nextAt(30, 0, 2))
        assertTrue(NagPolicy.nextAt(29, 0, 2) != null)
    }

    @Test fun lastRepeatIsSwipeable() {
        assertTrue(NagPolicy.isLast(5, 10))
        assertFalse(NagPolicy.isLast(4, 10))
    }

    @Test fun zeroIntervalIsSafe() {
        assertEquals(60, NagPolicy.maxNags(0))
    }
}
