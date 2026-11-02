package app.mido.suggest

/**
 * Spots messages that sound like a to-do ("can you buy paper towels?", "we're out of milk")
 * and turns them into a short task title. Simple on-device rules, no network.
 */
object TextTaskExtractor {
    private val opts = setOf(RegexOption.IGNORE_CASE)

    private val leadIn = Regex("^(?:(?:hey|hi|hello|ok|okay|so|also|btw|oh|and|actually)[,!]?\\s+)+", opts)

    private val requestPrefix = Regex(
        "^(?:(?:can|could|would|will)\\s+(?:you|u)\\s+(?:please\\s+|pls\\s+)?(?:also\\s+)?" +
            "|(?:please|pls|plz)\\s+" +
            "|(?:don'?t|dont)\\s+forget(?:\\s+to)?\\s+" +
            "|remember\\s+to\\s+" +
            "|make\\s+sure\\s+(?:you\\s+|to\\s+)" +
            "|(?:i\\s+|we\\s+|you\\s+|u\\s+)?need\\s+(?:you\\s+)?to\\s+" +
            "|(?:reminder|remind\\s+me)\\s*(?:to|:)\\s*)",
        opts,
    )

    private val shoppingNeed = Regex(
        "^(?:we(?:'re|\\s+are)?\\s+(?:out\\s+of|low\\s+on|running\\s+low\\s+on|almost\\s+out\\s+of)" +
            "|we\\s+need(?:\\s+more)?|need\\s+more|(?:i'?m|we'?re)\\s+out\\s+of)\\s+(.+)$",
        opts,
    )

    /** Verbs that make sense right after "can you ...". */
    private val requestVerbs = Regex(
        "^(?:buy|pick\\s+up|grab|bring|call|schedule|book|pay|order|drop\\s+off|return|renew|cancel|get|take|send|text|" +
            "email|water|feed|clean|fix|check|make|sign|submit|mail|pack|wash|walk|stop\\s+by|swing\\s+by)\\b",
        opts,
    )

    /** Verbs strong enough to count on their own, without a "can you". */
    private val bareVerbs = Regex(
        "^(?:buy|pick\\s+up|grab|bring|call|schedule|book|pay|order|drop\\s+off|return|renew)\\b",
        opts,
    )

    private val callMe = Regex("^call\\s+me\\b", opts)
    private val textMe = Regex("^(?:text|message|msg)\\s+me\\b", opts)
    private val trailing = Regex("[\\s,.!?]*(?:\\b(?:please|pls|plz|thanks|thank\\s+you|thx|ty)\\b)?[\\s,.!?]*$", opts)

    /** A suggested task title, or null when the message doesn't read like a to-do. [sender] fills "call me". */
    fun suggest(message: String, sender: String? = null): String? {
        val clauses = message.split(Regex("(?<=[.!?;])\\s+|\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        for (raw in clauses) {
            suggestClause(raw, sender)?.let { return it }
        }
        return null
    }

    private fun suggestClause(raw: String, sender: String?): String? {
        var text = raw.replace(leadIn, "").trim()
        val isQuestion = text.endsWith("?")

        var hadPrefix = false
        repeat(3) {
            val m = requestPrefix.find(text)
            if (m != null) {
                hadPrefix = true
                text = text.substring(m.range.last + 1).trim()
            }
        }

        val title: String? = when {
            callMe.containsMatchIn(text) -> "Call " + (sender?.takeIf { it.isNotBlank() } ?: "them back")
            textMe.containsMatchIn(text) -> "Text " + (sender?.takeIf { it.isNotBlank() } ?: "them back")
            hadPrefix && requestVerbs.containsMatchIn(text) -> text
            !hadPrefix && !isQuestion && bareVerbs.containsMatchIn(text) -> text
            !hadPrefix && !isQuestion -> shoppingNeed.find(text)?.let { "Buy ${it.groupValues[1]}" }
            else -> null
        }
        return title?.let(::tidy)
    }

    private fun tidy(title: String): String? {
        val cleaned = title.replace(trailing, "").replace(Regex("\\s+"), " ").trim()
        if (cleaned.split(" ").size < 2) return null
        return cleaned.replaceFirstChar { it.uppercase() }
    }
}

object PhoneNumbers {
    private val inText = Regex("""\+?\d[\d\-.\s()]{5,}\d""")

    /** The first phone-number-looking thing in [text] (7 to 15 digits), or null. */
    fun find(text: String?): String? {
        val m = inText.find(text ?: return null)?.value?.trim() ?: return null
        val digits = m.count { it.isDigit() }
        return if (digits in 7..15) m else null
    }

    fun looksLikeNumber(s: String): Boolean {
        val t = s.trim()
        return t.count { it.isDigit() } >= 7 && t.all { it.isDigit() || it in "+-(). " }
    }

    /** 10-digit (or +1 11-digit) US numbers become 111-222-3333; anything else is left as typed. */
    fun pretty(s: String): String {
        val d = s.filter { it.isDigit() }
        val ten = when {
            d.length == 10 -> d
            d.length == 11 && d.startsWith("1") -> d.substring(1)
            else -> return s.trim()
        }
        return "${ten.substring(0, 3)}-${ten.substring(3, 6)}-${ten.substring(6)}"
    }
}
