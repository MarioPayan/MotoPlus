package com.kaze.motoplus.domain

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Explicit currency and locale, independent of the phone's region. */
fun formatMoney(value: Double, currency: String = "COP", locale: Locale = Locale.forLanguageTag("es-CO")): String {
    val code = Currency.getInstance(currency)
    val digits = if (currency == "COP") 0 else code.defaultFractionDigits.coerceAtLeast(0)
    val number = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(value)
    return "${currencySymbol(currency, locale)} $number"
}

fun currencySymbol(currency: String, locale: Locale = Locale.forLanguageTag("es-CO")): String =
    when (currency) { "COP" -> "$"; "USD" -> "US$"; "MXN" -> "MX$"; "EUR" -> "EUR"; else -> Currency.getInstance(currency).getSymbol(locale) }

/** Parses the complete amount using the same separators as the displayed currency. */
fun parseAmount(value: String, locale: Locale): Double? {
    val input = value.trim()
    val position = java.text.ParsePosition(0)
    val number = NumberFormat.getNumberInstance(locale).parse(input, position) ?: return null
    return number.toDouble().takeIf { position.index == input.length && it.isFinite() && it >= 0 }
}

/** Groups the integer portion while retaining decimal input and an unfinished decimal separator. */
fun formatAmountInput(value: String, currency: String, locale: Locale): String {
    val symbols = java.text.DecimalFormatSymbols.getInstance(locale)
    val decimal = symbols.decimalSeparator
    val hasDecimals = currency != "COP"
    val pieces = value.split(decimal, limit = 2)
    val digits = pieces[0].filter(Char::isDigit).take(9)
    if (digits.isEmpty()) return ""
    val grouped = NumberFormat.getIntegerInstance(locale).format(digits.toLong())
    return if (hasDecimals && pieces.size == 2) grouped + decimal + pieces[1].filter(Char::isDigit).take(2) else grouped
}
