package com.showtimeplayer.util

fun Long.formatDurationMs(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:%02d".format(seconds)
}
