package com.kaze.motoplus.domain

import java.text.NumberFormat
import java.util.Locale

/** Group whole-number inputs without changing the raw digits stored by forms. */
fun formatOdometerInput(value: String, locale: Locale): String {
    return formatIntegerInput(value, locale, 7)
}

fun formatIntegerInput(value: String, locale: Locale, maxDigits: Int = 7, grouping: Boolean = true): String {
    val digits = value.filter(Char::isDigit).take(maxDigits)
    return if (digits.isEmpty()) "" else NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = grouping }.format(digits.toLong())
}

/** Canonical decimal text uses a dot; either keyboard separator is accepted. */
fun decimalInput(value: String, maxFractionDigits: Int = 1): String {
    val pieces = value.replace(',', '.').split('.', limit = 2)
    val whole = pieces[0].filter(Char::isDigit).take(5)
    return whole + if (pieces.size == 2) "." + pieces[1].filter(Char::isDigit).take(maxFractionDigits) else ""
}

/** Round stored/conversion values for display without changing their canonical precision. */
fun formatDecimalInput(value: String, locale: Locale, maxFractionDigits: Int = 1): String {
    if (value.isEmpty()) return ""
    val canonical = value.replace(',', '.')
    val number = canonical.toBigDecimalOrNull() ?: if (canonical == ".") java.math.BigDecimal.ZERO else return ""
    val formatted = NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = maxFractionDigits
        roundingMode = java.math.RoundingMode.HALF_UP
    }.format(number)
    val separator = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val fraction = canonical.substringAfter('.', "")
    return if ('.' in canonical && fraction.length <= maxFractionDigits) {
        canonical.substringBefore('.').ifEmpty { "0" } + separator + fraction
    } else formatted
}

/** Keep the caret beside the same digit when grouping separators appear or disappear. */
fun groupedInputCursor(input: String, cursor: Int, formatted: String): Int {
    val digits = input.take(cursor).count(Char::isDigit)
    if (digits == 0) return 0
    var seen = 0
    return formatted.indexOfFirst { it.isDigit() && ++seen == digits }
        .let { if (it < 0) formatted.length else it + 1 }
}
