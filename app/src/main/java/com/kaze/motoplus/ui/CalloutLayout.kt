package com.kaze.motoplus.ui

internal data class CalloutMetric(val width: Float, val anchor: Float)
internal data class CalloutPlacement(val index: Int, val left: Float, val width: Float)
internal data class CalloutRow(val labels: List<CalloutPlacement>, val hidden: Int, val summary: CalloutPlacement?)

/** Fit the most urgent prefix at readable size, reserving space for a count of hidden zones. */
internal fun layoutCalloutRow(
    metrics: List<CalloutMetric>, width: Float, padding: Float, gap: Float, summaryWidth: (Int) -> Float,
): CalloutRow {
    if (metrics.isEmpty()) return CalloutRow(emptyList(), 0, null)
    val available = (width - 2 * padding).coerceAtLeast(0f)
    var count = metrics.size
    while (count > 0) {
        val hidden = metrics.size - count
        val needed = metrics.take(count).sumOf { it.width.toDouble() }.toFloat() +
            (count - 1) * gap + if (hidden > 0) gap + summaryWidth(hidden) else 0f
        if (needed <= available) break
        count--
    }
    // An unusually long translation gets one ellipsized label, with the hidden count beside it.
    if (count == 0) count = 1
    val hidden = metrics.size - count
    val summary = if (hidden > 0) {
        val w = summaryWidth(hidden).coerceAtMost(available)
        CalloutPlacement(-1, width - padding - w, w)
    } else null
    val right = summary?.let { (it.left - gap).coerceAtLeast(padding) } ?: (padding + available)
    val selected = metrics.take(count).mapIndexed { i, m ->
        i to m.copy(width = m.width.coerceAtMost((right - padding).coerceAtLeast(0f)))
    }.sortedBy { it.second.anchor }
    var cursor = padding
    val labels = selected.mapIndexed { i, (index, metric) ->
        val remaining = selected.drop(i).sumOf { it.second.width.toDouble() }.toFloat() + (count - i - 1) * gap
        val left = maxOf(cursor, minOf(metric.anchor - metric.width / 2, right - remaining))
        cursor = left + metric.width + gap
        CalloutPlacement(index, left, metric.width)
    }
    return CalloutRow(labels, hidden, summary)
}
