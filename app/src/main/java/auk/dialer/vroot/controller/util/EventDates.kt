package auk.dialer.vroot.controller.util

import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

/**
 * A contact's dates (birthday, anniversary...). Android stores them as text in whatever form the app
 * or account that wrote them chose: the standard "1990-03-12", "--03-12" for a date without its year,
 * but also "19900312", "12.03.1990", or the American "03/12/1990" next to the European "12/03/1990".
 * They are read in all those forms, written back in the standard one, and shown in the user's own
 * order ("12 mars 1990", "March 12, 1990").
 */
object EventDates {

    /** A day of the year, with its year when known. */
    data class Day(val year: Int?, val month: Int, val day: Int)

    fun parse(raw: String, locale: Locale = Locale.getDefault()): Day? {
        val text = raw.trim().substringBefore('T').substringBefore(' ')
        Regex("""--(\d{2})-?(\d{2})""").matchEntire(text)?.let { m ->
            return valid(null, m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
        Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""").matchEntire(text)?.let { m ->
            return valid(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        Regex("""(\d{4})(\d{2})(\d{2})""").matchEntire(text)?.let { m ->
            return valid(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        Regex("""(\d{1,2})[-/.](\d{1,2})(?:[-/.](\d{4}))?""").matchEntire(text)?.let { m ->
            val first = m.groupValues[1].toInt()
            val second = m.groupValues[2].toInt()
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            // Which comes first, the month or the day: told by a number past 12, else by the
            // order the user's own language writes dates in.
            val monthFirst = when {
                first > 12 -> false
                second > 12 -> true
                else -> writesMonthFirst(locale)
            }
            return if (monthFirst) valid(year, first, second) else valid(year, second, first)
        }
        return null
    }

    /** The standard form Android and Google store: "1990-03-12", or "--03-12" without a year. */
    fun store(day: Day): String =
        if (day.year != null) "%04d-%02d-%02d".format(Locale.ROOT, day.year, day.month, day.day)
        else "--%02d-%02d".format(Locale.ROOT, day.month, day.day)

    /** [raw] as the user's language writes a date, or as it is when it cannot be read. */
    fun format(raw: String, locale: Locale = Locale.getDefault()): String {
        val day = parse(raw, locale) ?: return raw
        val pattern = DateFormat.getBestDateTimePattern(locale, if (day.year != null) "yMMMMd" else "MMMMd")
        val calendar = Calendar.getInstance().apply {
            clear()
            set(day.year ?: LEAP_YEAR, day.month - 1, day.day)
        }
        return SimpleDateFormat(pattern, locale).format(calendar.time)
    }

    /** How old someone born on [day] is today, when the year is known. */
    fun age(day: Day, today: LocalDate = LocalDate.now()): Int? {
        val year = day.year ?: return null
        val hadBirthday = today.monthValue > day.month || (today.monthValue == day.month && today.dayOfMonth >= day.day)
        return (today.year - year - if (hadBirthday) 0 else 1).takeIf { it >= 0 }
    }

    /** Whether two stored dates are the same day, whatever form each was written in. */
    fun sameDay(a: String, b: String): Boolean {
        val first = parse(a)
        return if (first != null) first == parse(b) else a.trim() == b.trim()
    }

    private fun writesMonthFirst(locale: Locale): Boolean {
        val pattern = DateFormat.getBestDateTimePattern(locale, "Md")
        return pattern.indexOf('M') < pattern.indexOf('d')
    }

    private fun valid(year: Int?, month: Int, day: Int): Day? {
        if (month !in 1..12 || day !in 1..31) return null
        // A date without a year may be February 29th.
        val ok = runCatching { LocalDate.of(year ?: LEAP_YEAR, month, day) }.isSuccess
        return if (ok) Day(year, month, day) else null
    }

    private const val LEAP_YEAR = 2000
}
