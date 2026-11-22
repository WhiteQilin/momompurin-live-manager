package ph.edu.umak.momompurin.ui.theme

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Money is stored as [Long] cents throughout the data layer and converted to
 * text only here. Two decimals are always shown because the client's price list
 * quotes two decimals (P35.00, not P35) and the pricing chain's transparency
 * line in G1.0 depends on the reader seeing P380.00 / 18 units.
 */
object MomoMoney {

    val PESO = "\u20B1"

    /** P880.00 - the form every screen uses for an order total. */
    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = abs(cents)
        return "$sign$PESO${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    /** 880.00 - the bare number, for table cells that carry their own symbol. */
    fun plain(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = abs(cents)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    /**
     * Full precision, for the G1.0 transparency line that proves the rounding is
     * display-only: `P380.00 / 18 units = P21.1111...`.
     */
    fun divideAtFullPrecision(totalCents: Long, divisor: Int): String {
        if (divisor == 0) return "0"
        val whole = totalCents / 100
        val value = whole.toDouble() / divisor
        return trimToRepeating(value)
    }

    private fun trimToRepeating(value: Double): String {
        val rounded = (value * 10000).roundToLong() / 10000.0
        val asString = String.format(Locale.US, "%.4f", rounded).trimEnd('0')
        return if (asString.endsWith('.')) "${asString}0" else asString
    }

    /** Parse "380.00" or "880" from seed/settings text into cents. */
    fun parse(text: String): Long? {
        val cleaned = text.replace(PESO, "").replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()
            ?.movePointRight(2)
            ?.let { it.setScale(0, java.math.RoundingMode.HALF_UP).toLong() }
    }
}

/** Dates render in one format app-wide so a tester can compare screens. */
object MomoDate {
    private val dateTime = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US)
    private val dateOnly = SimpleDateFormat("d MMM yyyy", Locale.US)

    fun formatDateTime(epochMillis: Long): String = dateTime.format(java.util.Date(epochMillis))
    fun formatDate(epochMillis: Long): String = dateOnly.format(java.util.Date(epochMillis))
    /** B1.0's Claim Timestamp shows the clock time only, e.g. "7:42 PM". */
    private val timeOnly = SimpleDateFormat("h:mm a", Locale.US)
    fun formatTime(epochMillis: Long): String = timeOnly.format(java.util.Date(epochMillis))

    /**
     * B-02 c2: read an owner-typed clock time such as "7:42 PM" back onto the
     * claim's own day, so the saved claim carries the time that was entered.
     *
     * Returns null when the text is not a time, letting the caller fall back to the
     * current moment rather than silently writing a wrong timestamp. Accepted
     * shapes: "7:42 PM", "7:42PM", "19:42", "7 PM", "7pm".
     */
    fun parseTimeToEpoch(text: String, onDayOf: Long): Long? {
        val normalised = text.trim().uppercase().replace(".", "")
            // "7:42PM" -> "7:42 PM" so the 12-hour pattern can match it.
            .replace(Regex("(\\d)(AM|PM)"), "$1 $2")
        if (normalised.isEmpty()) return null
        val cleaned = normalised

        // Each pattern must consume the WHOLE string. Without that, "H:mm" happily
        // parses "7:42 PM" as 07:42 and throws the PM away (12 hours wrong), and
        // "h:mm a" parses "19:42" as 7 PM (also 12 hours wrong). Trying patterns in
        // order is therefore not enough - the match has to be exact.
        //
        // NOTE: there is no "h:mmA" pattern. 'A' is a reserved pattern letter, so
        // SimpleDateFormat throws on it; a compact "7:42PM" is normalised to
        // "7:42 PM" before parsing instead of adding a pattern that cannot compile.
        for (pattern in listOf("h:mm a", "H:mm", "h a", "ha")) {
            val parsed = runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val pos = java.text.ParsePosition(0)
                val date = fmt.parse(cleaned, pos)
                // A full match means the parser stopped at the very end.
                if (date == null || pos.index != cleaned.length) null else date
            }.getOrNull() ?: continue
            val hm = java.util.Calendar.getInstance().apply { time = parsed }
            return java.util.Calendar.getInstance().apply {
                timeInMillis = onDayOf
                set(java.util.Calendar.HOUR_OF_DAY, hm.get(java.util.Calendar.HOUR_OF_DAY))
                set(java.util.Calendar.MINUTE, hm.get(java.util.Calendar.MINUTE))
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        return null
    }
}
