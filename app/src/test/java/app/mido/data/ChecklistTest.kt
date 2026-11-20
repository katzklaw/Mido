package app.mido.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistTest {
    private val list = "Grocery run\n[ ] milk\n[x] eggs\n[ ] bread"

    @Test fun parsesItemsAndPlainLines() {
        val p = Checklist.parse(list)
        assertEquals(listOf(false, true, true, true), p.map { it.isItem })
        assertEquals(listOf(false, false, true, false), p.map { it.checked })
        assertEquals("milk", p[1].text)
    }

    @Test fun progressCountsOnlyItems() {
        assertEquals(1 to 3, Checklist.progress(list))
        assertNull(Checklist.progress("just a note"))
        assertNull(Checklist.progress(""))
    }

    @Test fun toggleFlipsOneLineAndIgnoresPlainText() {
        assertEquals("Grocery run\n[x] milk\n[x] eggs\n[ ] bread", Checklist.toggle(list, 1))
        assertEquals("Grocery run\n[ ] milk\n[ ] eggs\n[ ] bread", Checklist.toggle(list, 2))
        assertEquals(list, Checklist.toggle(list, 0))
        assertEquals(list, Checklist.toggle(list, 9))
    }

    @Test fun firstOpenSkipsDoneAndEmpty() {
        assertEquals(1, Checklist.firstOpenIndex(list))
        assertEquals(3, Checklist.firstOpenIndex("[x] a\n[ ] \n[x] b\n[ ] c"))
        assertNull(Checklist.firstOpenIndex("[x] a\n[x] b"))
    }

    @Test fun addItemStartsOrAppends() {
        assertEquals("[ ] ", Checklist.addItem(""))
        assertEquals("note\n[ ] ", Checklist.addItem("note"))
        assertEquals("[ ] a\n[ ] ", Checklist.addItem("[ ] a\n"))
    }

    @Test fun enterContinuesTheListAndEndsItOnAnEmptyItem() {
        assertEquals("[ ] milk\n[ ] ", Checklist.onEdit("[ ] milk", "[ ] milk\n"))
        assertEquals("[ ] milk\n", Checklist.onEdit("[ ] milk\n[ ] ", "[ ] milk\n[ ] \n"))
        // Plain text lines and edits that aren't a lone Enter are untouched.
        assertEquals("note\n", Checklist.onEdit("note", "note\n"))
        assertEquals("[ ] milks", Checklist.onEdit("[ ] milk", "[ ] milks"))
    }

    @Test fun displayUsesBallotBoxes() {
        assertEquals("Grocery run\n☐ milk\n☑ eggs\n☐ bread", Checklist.forDisplay(list))
    }
}
