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
    val symbol = when (currency) { "COP" -> "$"; "USD" -> "US$"; "MXN" -> "MX$"; "EUR" -> "EUR"; else -> code.getSymbol(locale) }
    return "$symbol $number"
}

/** Parses the complete amount using the same separators as the displayed currency. */
fun parseAmount(value: String, locale: Locale): Double? {
    val input = value.trim()
    val position = java.text.ParsePosition(0)
    val number = NumberFormat.getNumberInstance(locale).parse(input, position) ?: return null
    return number.toDouble().takeIf { position.index == input.length && it.isFinite() && it >= 0 }
}
