package com.kaze.motoplus.domain

import java.text.NumberFormat
import java.util.Locale

/** Group whole-number inputs without changing the raw digits stored by forms. */
fun formatOdometerInput(value: String, locale: Locale): String {
    val digits = value.filter(Char::isDigit).take(7)
    return if (digits.isEmpty()) "" else NumberFormat.getIntegerInstance(locale).format(digits.toLong())
}

/** Keep the caret beside the same digit when grouping separators appear or disappear. */
fun groupedInputCursor(input: String, cursor: Int, formatted: String): Int {
    val digits = input.take(cursor).count(Char::isDigit)
    if (digits == 0) return 0
    var seen = 0
    return formatted.indexOfFirst { it.isDigit() && ++seen == digits }
        .let { if (it < 0) formatted.length else it + 1 }
}
