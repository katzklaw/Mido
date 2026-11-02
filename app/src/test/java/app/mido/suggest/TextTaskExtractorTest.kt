package app.mido.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextTaskExtractorTest {
    private fun s(m: String, who: String? = null) = TextTaskExtractor.suggest(m, who)

    @Test fun requestsBecomeTasks() {
        assertEquals("Buy paper towels on your way home", s("Can you buy paper towels on your way home?"))
        assertEquals("Pick up milk", s("Hey, could you please pick up milk?"))
        assertEquals("Grab stamps", s("Thanks! Also can you grab stamps"))
    }

    @Test fun remindersAndNeeds() {
        assertEquals("Call the vet tomorrow", s("Don't forget to call the vet tomorrow"))
        assertEquals("Pay rent on the 1st", s("Remember to pay rent on the 1st"))
        assertEquals("Buy paper towels", s("We're out of paper towels"))
        assertEquals("Buy milk", s("we need milk"))
        assertEquals("Buy milk", s("Buy milk"))
    }

    @Test fun callMeUsesSenderName() {
        assertEquals("Call Sam", s("Call me when you can", "Sam"))
        assertEquals("Call them back", s("Call me when you can", null))
        assertEquals("Call Sam", s("call me", "Sam"))
        assertEquals("Text Sam", s("text me", "Sam"))
    }

    @Test fun ordinaryChatIsIgnored() {
        assertNull(s("lol that's hilarious"))
        assertNull(s("What time is dinner?"))
        assertNull(s("I'll get it"))
        assertNull(s("Can you be home by 5?"))
        assertNull(s("Sounds good!"))
        assertNull(s("did you buy milk?"))
    }

    @Test fun phoneNumbers() {
        assertEquals("111-222-3333", PhoneNumbers.pretty("(111) 222-3333"))
        assertEquals("111-222-3333", PhoneNumbers.pretty("+1 111 222 3333"))
        assertEquals("+44 20 7946 0958", PhoneNumbers.pretty("+44 20 7946 0958"))
        assertEquals(true, PhoneNumbers.looksLikeNumber("111-222-3333"))
        assertEquals(false, PhoneNumbers.looksLikeNumber("Husband"))
        assertEquals(false, PhoneNumbers.looksLikeNumber("12345"))
    }
}
