package app.mido.data

/**
 * Checklists live inside a task's notes as plain lines: "[ ] milk" and "[x] eggs". Any other line is ordinary text.
 * Pure logic, no Android.
 */
object Checklist {
    private const val OPEN = "[ ] "
    private const val DONE = "[x] "

    data class Line(val text: String, val isItem: Boolean, val checked: Boolean)

    private fun parseLine(raw: String): Line = when {
        raw.startsWith(OPEN) -> Line(raw.removePrefix(OPEN), true, false)
        raw.startsWith(DONE, ignoreCase = true) -> Line(raw.substring(DONE.length), true, true)
        raw == "[ ]" -> Line("", true, false)
        else -> Line(raw, false, false)
    }

    fun parse(notes: String): List<Line> = if (notes.isEmpty()) emptyList() else notes.split("\n").map(::parseLine)

    /** (done, total) when the notes contain a checklist, else null. */
    fun progress(notes: String): Pair<Int, Int>? {
        val items = parse(notes).filter { it.isItem && it.text.isNotBlank() }
        return if (items.isEmpty()) null else items.count { it.checked } to items.size
    }

    /** Flips the checklist item on line [lineIndex]. Other lines are left alone. */
    fun toggle(notes: String, lineIndex: Int): String {
        val lines = notes.split("\n").toMutableList()
        if (lineIndex !in lines.indices) return notes
        val line = parseLine(lines[lineIndex])
        if (!line.isItem) return notes
        lines[lineIndex] = (if (line.checked) OPEN else DONE) + line.text
        return lines.joinToString("\n")
    }

    /** The line index of the first unticked item with text, or null. */
    fun firstOpenIndex(notes: String): Int? =
        parse(notes).indexOfFirst { it.isItem && !it.checked && it.text.isNotBlank() }.takeIf { it >= 0 }

    /** Adds an empty checklist item on a new line at the end. */
    fun addItem(notes: String): String =
        if (notes.isBlank()) OPEN else notes.trimEnd('\n') + "\n" + OPEN

    /**
     * Makes typing a list natural. Pressing Enter at the end of a checklist item starts the next item; pressing Enter
     * on an empty item ends the list (removing the empty item).
     */
    fun onEdit(old: String, new: String): String {
        if (new.length != old.length + 1 || !new.endsWith("\n") || !new.startsWith(old)) return new
        val lastLine = old.substringAfterLast("\n")
        val line = parseLine(lastLine)
        if (!line.isItem) return new
        return if (line.text.isBlank()) {
            old.dropLast(lastLine.length) // drop the empty item, leaving a plain new line
        } else {
            new + OPEN
        }
    }

    /** Notes as shown in a notification: items get ☐ / ☑, other lines stay as they are. */
    fun forDisplay(notes: String): String = parse(notes).joinToString("\n") {
        when {
            it.isItem -> (if (it.checked) "☑ " else "☐ ") + it.text
            else -> it.text
        }
    }
}
