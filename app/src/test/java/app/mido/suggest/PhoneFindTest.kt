package app.mido.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneFindTest {
    @Test fun findsNumbersInNotificationText() {
        assertEquals("111-222-3333", PhoneNumbers.find("Missed call from 111-222-3333"))
        assertEquals("+1 (555) 123-4567", PhoneNumbers.find("+1 (555) 123-4567"))
        assertNull(PhoneNumbers.find("Missed call from Dad"))
        assertNull(PhoneNumbers.find("room 12"))
        assertNull(PhoneNumbers.find(null))
    }
}
