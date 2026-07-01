package com.kmp.hook.domain.calc

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Central clock + date helpers. Uses epoch-millis everywhere to dodge the
 * epoch-day Int/Long API churn between kotlinx-datetime versions.
 * Per project memory: with kotlinx-datetime 0.7.x, `Clock` lives in `kotlin.time`.
 */
object AppClock {

    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    fun toDate(millis: Long): LocalDate =
        Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

    /** Whole days elapsed between two instants (floored). */
    fun daysBetween(startMillis: Long, endMillis: Long): Long =
        (endMillis - startMillis) / DAY_MS

    fun daysAgoMillis(days: Int): Long = nowMillis() - days * DAY_MS

    const val DAY_MS: Long = 86_400_000L

    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    /** e.g. "12 Jun 2026". */
    fun formatDate(millis: Long): String {
        val d = toDate(millis)
        val m = MONTHS.getOrElse(d.monthNumber - 1) { "?" }
        return "${d.dayOfMonth} $m ${d.year}"
    }

    /** Short form e.g. "12 Jun". */
    fun formatDayMonth(millis: Long): String {
        val d = toDate(millis)
        val m = MONTHS.getOrElse(d.monthNumber - 1) { "?" }
        return "${d.dayOfMonth} $m"
    }

    /** Human relative time e.g. "2h ago", "3d ago", "just now". */
    fun relative(millis: Long): String {
        val diff = nowMillis() - millis
        if (diff < 60_000) return "just now"
        val mins = diff / 60_000
        if (mins < 60) return "${mins}m ago"
        val hours = mins / 60
        if (hours < 24) return "${hours}h ago"
        val days = hours / 24
        if (days < 30) return "${days}d ago"
        val months = days / 30
        if (months < 12) return "${months}mo ago"
        return "${months / 12}y ago"
    }
}
