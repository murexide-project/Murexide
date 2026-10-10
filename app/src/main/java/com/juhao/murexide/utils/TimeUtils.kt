package com.juhao.murexide.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val DAY_MILLIS = 86_400_000L

private val SERVER_TIME_ZONE: TimeZone = TimeZone.getTimeZone("GMT+08:00")

private fun normalizeTimestamp(timestamp: Long): Long {
    return if (timestamp in 1..999_999_999_999L) {
        timestamp * 1000L
    } else {
        timestamp
    }
}

private fun sdf(pattern: String): SimpleDateFormat =
    SimpleDateFormat(pattern, Locale.getDefault()).apply {
        timeZone = SERVER_TIME_ZONE
    }

fun formatTimestamp(
    timestamp: Long,
    alwaysFullDate: Boolean = false
): String {
    return try {
        val ts = normalizeTimestamp(timestamp)
        val date = Date(ts)
        val now = Date()

        val todayCalendar = Calendar.getInstance(SERVER_TIME_ZONE).apply {
            time = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateCalendar = Calendar.getInstance(SERVER_TIME_ZONE).apply { time = date }

        if (alwaysFullDate) {
            return sdf("yyyy/M/d HH:mm").format(date)
        }

        when {
            date.after(todayCalendar.time) ->
                sdf("HH:mm").format(date)

            date.after(Date(todayCalendar.timeInMillis - DAY_MILLIS)) ->
                "昨天 " + sdf("HH:mm").format(date)

            dateCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) ->
                sdf("M/d HH:mm").format(date)

            else ->
                sdf("yyyy/M/d HH:mm").format(date)
        }
    } catch (_: Exception) {
        ""
    }
}