package app.mido.suggest

/**
 * Looks at an email notification (sender, subject and the preview line) for something you may need to do.
 * It reuses the text rules for requests ("please send the form") and adds a few email-style ones
 * ("Reminder: ...", "Action required: ...", "Payment due"). Marketing mail is skipped. Pure logic, on-device.
 */
object EmailTaskExtractor {
    private val opts = setOf(RegexOption.IGNORE_CASE)

    private val promo = Regex(
        "\\b(?:sale|% off|\\d+% off|deal|deals|coupon|promo|newsletter|unsubscribe|webinar|free shipping|" +
            "limited time|last chance|flash|offer ends|save up to|clearance|sweepstakes|winner|digest)\\b",
        opts,
    )

    /** Subjects that are asking you to act. The captured part is the thing to do. */
    private val subjectRules = listOf(
        Regex("^(?:re:\\s*)?(?:final\\s+)?reminder[:\\-–]\\s*(.+)$", opts),
        Regex("^(?:re:\\s*)?action\\s+(?:required|needed)[:\\-–]\\s*(.+)$", opts),
        Regex("^(?:re:\\s*)?(?:response|reply)\\s+(?:required|needed)[:\\-–]?\\s*(.*)$", opts),
        Regex("^(?:re:\\s*)?(?:please|pls)\\s+(.+)$", opts),
        Regex("^(?:re:\\s*)?(?:rsvp)(?:\\s+(?:by|for|to))?[:\\-–]?\\s*(.*)$", opts),
        Regex("^(?:re:\\s*)?(?:don'?t\\s+forget)(?:\\s+to)?\\s+(.+)$", opts),
    )

    private val dueLine = Regex(
        "\\b(?:payment|invoice|bill|rent|balance|renewal|registration|form|application|appointment)\\b.*\\b(?:due|overdue|past\\s+due|expires?|expiring|deadline|tomorrow|today)\\b" +
            "|\\b(?:due|overdue|expires?|deadline)\\b.*\\b(?:payment|invoice|bill|rent|balance|renewal|registration|form|application)\\b",
        opts,
    )

    /** A suggested task title, or null when the email doesn't read like something you need to do. */
    fun suggest(sender: String?, subject: String?, preview: String?): String? {
        val subj = subject?.trim().orEmpty()
        val body = preview?.trim().orEmpty()
        if (subj.isEmpty() && body.isEmpty()) return null
        if (promo.containsMatchIn(subj) || promo.containsMatchIn(sender.orEmpty())) return null

        // 1. A subject that asks for action.
        for (rule in subjectRules) {
            val m = rule.find(subj) ?: continue
            val rest = m.groupValues[1].trim().trimEnd('.', '!', ':')
            if (rest.split(" ").size >= 2) return clean(rest)
            if (rule.pattern.contains("rsvp")) return clean("RSVP" + (sender?.let { " to $it" } ?: ""))
            if (rule.pattern.contains("response|reply")) return clean("Reply to ${sender ?: "email"}: $subj")
        }

        // 2. Bills, deadlines and appointments: the subject itself becomes the task.
        if (dueLine.containsMatchIn(subj) && subj.split(" ").size >= 2) return clean(subj)

        // 3. The preview reads like a request ("Can you send me the report?").
        if (body.isNotEmpty()) TextTaskExtractor.suggest(body, sender)?.let { return it }
        // ...or the subject does.
        if (subj.isNotEmpty()) TextTaskExtractor.suggest(subj, sender)?.let { return it }
        return null
    }

    private fun clean(s: String): String =
        s.replace(Regex("\\s+"), " ").trim().replaceFirstChar { it.uppercase() }
}
