package com.juhao.murexide.utils

fun parseCommaSeparatedInts(input: String?): List<Int> {
    return try {
        if (input.isNullOrBlank()) return emptyList()

        input.split(",").map { part ->
            part.trim().toInt()
        }
    } catch (_: Exception) {
        emptyList()
    }
}