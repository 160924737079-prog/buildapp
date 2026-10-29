package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class DateRangeBounds(
    val startDateIso: String, // yyyy-MM-dd inclusive
    val endDateIso: String,   // yyyy-MM-dd inclusive
    val label: String,
    val calendarDaysCount: Int
)

/**
 * Timezone-aware date handling and period calculation engine compatible with API 24+.
 * Uses ISO-8601 (yyyy-MM-dd) strings for lexicographical and SQLite/PostgreSQL range safety.
 */
object DateRangeEngine {

    fun isValidIsoDate(dateIso: String): Boolean {
        if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(dateIso.trim())) return false
        return try {
            val parts = dateIso.trim().split("-")
            val y = parts[0].toInt()
            val m = parts[1].toInt()
            val d = parts[2].toInt()
            if (y !in 1970..2100 || m !in 1..12) return false
            val maxDay = maxDaysInMonth(y, m)
            d in 1..maxDay
        } catch (e: Exception) {
            false
        }
    }

    fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    fun maxDaysInMonth(year: Int, month1Based: Int): Int {
        return when (month1Based) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 30
        }
    }

    fun todayIso(timezoneId: String = "Asia/Kolkata", nowMillis: Long = System.currentTimeMillis()): String {
        val tz = TimeZone.getTimeZone(timezoneId)
        val cal = Calendar.getInstance(tz, Locale.US)
        cal.timeInMillis = nowMillis
        return calendarToIso(cal)
    }

    fun calendarToIso(cal: Calendar): String {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    fun isoToCalendar(dateIso: String, timezoneId: String = "Asia/Kolkata"): Calendar {
        val tz = TimeZone.getTimeZone(timezoneId)
        val cal = Calendar.getInstance(tz, Locale.US)
        cal.firstDayOfWeek = Calendar.MONDAY
        val parts = dateIso.trim().split("-")
        val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val m = (parts.getOrNull(1)?.toIntOrNull() ?: 10) - 1
        val d = parts.getOrNull(2)?.toIntOrNull() ?: 29
        cal.set(y, m, d, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    fun addDays(dateIso: String, days: Int, timezoneId: String = "Asia/Kolkata"): String {
        val cal = isoToCalendar(dateIso, timezoneId)
        cal.add(Calendar.DAY_OF_MONTH, days)
        return calendarToIso(cal)
    }

    fun addMonthsSafe(dateIso: String, months: Int, timezoneId: String = "Asia/Kolkata"): String {
        val parts = dateIso.trim().split("-")
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()

        val totalMonths = (y * 12 + (m - 1)) + months
        val targetYear = totalMonths / 12
        val targetMonth = (totalMonths % 12) + 1
        val clampedDay = d.coerceAtMost(maxDaysInMonth(targetYear, targetMonth))
        return String.format(Locale.US, "%04d-%02d-%02d", targetYear, targetMonth, clampedDay)
    }

    fun addYearsSafe(dateIso: String, years: Int): String {
        val parts = dateIso.trim().split("-")
        val y = parts[0].toInt() + years
        val m = parts[1].toInt()
        val d = parts[2].toInt()
        val clampedDay = d.coerceAtMost(maxDaysInMonth(y, m))
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, clampedDay)
    }

    /**
     * Advances a recurring expense due date while preserving month-end and leap-year rules.
     */
    fun nextRecurringDate(currentDueIso: String, frequency: RecurrenceFrequency): String {
        return when (frequency) {
            RecurrenceFrequency.DAILY -> addDays(currentDueIso, 1)
            RecurrenceFrequency.WEEKLY -> addDays(currentDueIso, 7)
            RecurrenceFrequency.MONTHLY -> addMonthsSafe(currentDueIso, 1)
            RecurrenceFrequency.YEARLY -> addYearsSafe(currentDueIso, 1)
        }
    }

    fun daysBetweenInclusive(startIso: String, endIso: String): Int {
        if (endIso < startIso) return 1
        val startCal = isoToCalendar(startIso, "UTC")
        val endCal = isoToCalendar(endIso, "UTC")
        val diffMillis = endCal.timeInMillis - startCal.timeInMillis
        val days = (diffMillis / (24L * 60L * 60L * 1000L)).toInt() + 1
        return days.coerceAtLeast(1)
    }

    fun formatDisplayDate(dateIso: String, pattern: String = "dd MMM yyyy"): String {
        return try {
            val cal = isoToCalendar(dateIso, "UTC")
            val sdf = SimpleDateFormat(pattern, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.format(cal.time)
        } catch (e: Exception) {
            dateIso
        }
    }

    fun dayOfWeekName(dateIso: String): String {
        return try {
            val cal = isoToCalendar(dateIso, "UTC")
            val sdf = SimpleDateFormat("EEE", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.format(cal.time)
        } catch (e: Exception) {
            ""
        }
    }

    fun monthBounds(year: Int, month1Based: Int): DateRangeBounds {
        val m = month1Based.coerceIn(1, 12)
        val maxD = maxDaysInMonth(year, m)
        val start = String.format(Locale.US, "%04d-%02d-01", year, m)
        val end = String.format(Locale.US, "%04d-%02d-%02d", year, m, maxD)
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(isoToCalendar(start, "UTC").time)
        return DateRangeBounds(start, end, monthName, maxD)
    }

    fun quarterBounds(year: Int, quarter1To4: Int): DateRangeBounds {
        val q = quarter1To4.coerceIn(1, 4)
        val startMonth = (q - 1) * 3 + 1
        val endMonth = startMonth + 2
        val start = String.format(Locale.US, "%04d-%02d-01", year, startMonth)
        val endDay = maxDaysInMonth(year, endMonth)
        val end = String.format(Locale.US, "%04d-%02d-%02d", year, endMonth, endDay)
        return DateRangeBounds(
            startDateIso = start,
            endDateIso = end,
            label = "Q$q $year",
            calendarDaysCount = daysBetweenInclusive(start, end)
        )
    }

    fun halfYearBounds(year: Int, half1Or2: Int): DateRangeBounds {
        val h = if (half1Or2 <= 1) 1 else 2
        val startMonth = if (h == 1) 1 else 7
        val endMonth = if (h == 1) 6 else 12
        val start = String.format(Locale.US, "%04d-%02d-01", year, startMonth)
        val endDay = maxDaysInMonth(year, endMonth)
        val end = String.format(Locale.US, "%04d-%02d-%02d", year, endMonth, endDay)
        val label = if (h == 1) "H1 $year (Jan–Jun)" else "H2 $year (Jul–Dec)"
        return DateRangeBounds(
            startDateIso = start,
            endDateIso = end,
            label = label,
            calendarDaysCount = daysBetweenInclusive(start, end)
        )
    }

    fun yearBounds(year: Int): DateRangeBounds {
        val start = String.format(Locale.US, "%04d-01-01", year)
        val end = String.format(Locale.US, "%04d-12-31", year)
        return DateRangeBounds(
            startDateIso = start,
            endDateIso = end,
            label = "Year $year",
            calendarDaysCount = if (isLeapYear(year)) 366 else 365
        )
    }

    fun weekBoundsContaining(dateIso: String): DateRangeBounds {
        val cal = isoToCalendar(dateIso, "UTC")
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        // Monday as start of week
        val offsetToMonday = if (dow == Calendar.SUNDAY) -6 else (Calendar.MONDAY - dow)
        cal.add(Calendar.DAY_OF_MONTH, offsetToMonday)
        val start = calendarToIso(cal)
        cal.add(Calendar.DAY_OF_MONTH, 6)
        val end = calendarToIso(cal)
        return DateRangeBounds(
            startDateIso = start,
            endDateIso = end,
            label = "${formatDisplayDate(start, "dd MMM")} – ${formatDisplayDate(end, "dd MMM yyyy")}",
            calendarDaysCount = 7
        )
    }

    fun resolvePeriodBounds(
        period: ReportingPeriod,
        referenceDateIso: String,
        customStartIso: String? = null,
        customEndIso: String? = null
    ): DateRangeBounds {
        val parts = referenceDateIso.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 10
        val currentQuarter = ((month - 1) / 3) + 1
        val currentHalf = if (month <= 6) 1 else 2

        return when (period) {
            ReportingPeriod.TODAY -> DateRangeBounds(
                referenceDateIso,
                referenceDateIso,
                "Today (${formatDisplayDate(referenceDateIso)})",
                1
            )
            ReportingPeriod.YESTERDAY -> {
                val yest = addDays(referenceDateIso, -1)
                DateRangeBounds(yest, yest, "Yesterday (${formatDisplayDate(yest)})", 1)
            }
            ReportingPeriod.THIS_WEEK -> weekBoundsContaining(referenceDateIso)
            ReportingPeriod.LAST_WEEK -> weekBoundsContaining(addDays(referenceDateIso, -7))
            ReportingPeriod.THIS_MONTH -> monthBounds(year, month)
            ReportingPeriod.LAST_MONTH -> {
                if (month == 1) monthBounds(year - 1, 12) else monthBounds(year, month - 1)
            }
            ReportingPeriod.CURRENT_QUARTER -> quarterBounds(year, currentQuarter)
            ReportingPeriod.PREVIOUS_QUARTER -> {
                if (currentQuarter == 1) quarterBounds(year - 1, 4) else quarterBounds(year, currentQuarter - 1)
            }
            ReportingPeriod.CURRENT_HALF_YEAR -> halfYearBounds(year, currentHalf)
            ReportingPeriod.PREVIOUS_HALF_YEAR -> {
                if (currentHalf == 1) halfYearBounds(year - 1, 2) else halfYearBounds(year, 1)
            }
            ReportingPeriod.THIS_YEAR -> yearBounds(year)
            ReportingPeriod.LAST_YEAR -> yearBounds(year - 1)
            ReportingPeriod.ALL_TIME -> DateRangeBounds(
                startDateIso = "2000-01-01",
                endDateIso = "2099-12-31",
                label = "All Time",
                calendarDaysCount = 365
            )
            ReportingPeriod.CUSTOM -> {
                val s = if (customStartIso != null && isValidIsoDate(customStartIso)) customStartIso else monthBounds(year, month).startDateIso
                val e = if (customEndIso != null && isValidIsoDate(customEndIso)) customEndIso else referenceDateIso
                val actualStart = minOf(s, e)
                val actualEnd = maxOf(s, e)
                DateRangeBounds(
                    startDateIso = actualStart,
                    endDateIso = actualEnd,
                    label = "${formatDisplayDate(actualStart)} – ${formatDisplayDate(actualEnd)}",
                    calendarDaysCount = daysBetweenInclusive(actualStart, actualEnd)
                )
            }
        }
    }
}
