package com.roinur.booktracker

internal fun calculatedBookPage(currentPage: Int, startText: String, endText: String): Int? {
    val start = startText.trim().toLongOrNull() ?: return null
    val end = endText.trim().toLongOrNull() ?: return null
    if (start !in 0..Int.MAX_VALUE.toLong() || end !in start..Int.MAX_VALUE.toLong()) return null
    val reached = currentPage.coerceAtLeast(0).toLong() + end - start
    return reached.takeIf { it <= Int.MAX_VALUE }?.toInt()
}
