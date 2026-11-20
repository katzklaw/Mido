package app.mido.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailTaskExtractorTest {
    @Test fun reminderSubjectBecomesTheTask() {
        assertEquals("Dentist appointment tomorrow at 3pm", EmailTaskExtractor.suggest("Dr. Smith", "Reminder: dentist appointment tomorrow at 3pm", null))
    }

    @Test fun actionRequiredSubject() {
        assertEquals("Verify your insurance info", EmailTaskExtractor.suggest("Blue Cross", "Action required: verify your insurance info", null))
    }

    @Test fun pleaseSubject() {
        assertEquals("Sign the permission slip", EmailTaskExtractor.suggest("School", "Please sign the permission slip", null))
    }

    @Test fun billDueSubject() {
        assertEquals("Your electric bill is due Friday", EmailTaskExtractor.suggest("Power Co", "Your electric bill is due Friday", null))
    }

    @Test fun previewRequestUsesTextRules() {
        assertEquals("Send me the report", EmailTaskExtractor.suggest("Boss", "Quick question", "Can you send me the report?"))
    }

    @Test fun marketingIsIgnored() {
        assertNull(EmailTaskExtractor.suggest("Store", "Reminder: 50% off sale ends tonight", null))
        assertNull(EmailTaskExtractor.suggest("News", "Your weekly digest", "Please enjoy this week's top stories"))
    }

    @Test fun plainMailIsIgnored() {
        assertNull(EmailTaskExtractor.suggest("Mom", "Photos from the weekend", "Here are the pictures we took"))
        assertNull(EmailTaskExtractor.suggest(null, "", ""))
    }

    @Test fun rsvpWithNoDetailNamesTheSender() {
        assertEquals("RSVP to Sam", EmailTaskExtractor.suggest("Sam", "RSVP", null))
    }
}
