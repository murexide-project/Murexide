package com.juhao.murexide.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAY_MILLIS = 86_400_000L

fun formatTimestamp(
    timestamp: Long,
    alwaysFullDate: Boolean = false
): String {
    return try {
        val date = Date(timestamp)
        val now = Date()
        val todayCalendar = Calendar.getInstance().apply {
            time = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateCalendar = Calendar.getInstance().apply { time = date }

        if (alwaysFullDate) {
            return SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault()).format(date)
        }

        when {
            date.after(todayCalendar.time) ->
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            date.after(Date(todayCalendar.timeInMillis - DAY_MILLIS)) ->
                "昨天 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            dateCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) ->
                SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(date)
            else ->
                SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        ""
    }
}