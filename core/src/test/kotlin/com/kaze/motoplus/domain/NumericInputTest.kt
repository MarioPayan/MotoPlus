package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class NumericInputTest {
    @Test fun odometersGroupWhileTypingInEverySupportedLocale() {
        for (tag in listOf("es-CO", "es-ES", "en-US")) {
            val locale = Locale.forLanguageTag(tag)
            val expected = if (tag == "en-US") "72,500" else "72.500"
            assertEquals(expected, formatOdometerInput("72500", locale))
            assertEquals(expected, formatOdometerInput(expected, locale))
            assertEquals("", formatOdometerInput("", locale))
            assertEquals("72500", formatOdometerInput("72500", locale).filter(Char::isDigit))
        }
    }

    @Test fun decimalConversionsUseLocaleAndAtMostOneDecimalWithoutLosingDayMemory() {
        val months = IntervalInput("45").switchTo(IntervalUnit.months)
        for (tag in listOf("es-CO", "es-ES", "en-US")) {
            val locale = Locale.forLanguageTag(tag)
            val separator = if (tag == "en-US") "." else ","
            assertEquals("1${separator}5", formatDecimalInput(months.text, locale))
            assertEquals("0${separator}1", formatDecimalInput("0.12", locale))
            assertEquals("12${separator}5", formatDecimalInput(decimalInput("12,5"), locale))
            assertEquals("12${separator}5", formatDecimalInput(decimalInput("12.5"), locale))
            assertEquals("12$separator", formatDecimalInput(decimalInput("12,"), locale))
            assertEquals("12${separator}05", formatDecimalInput(decimalInput("12.05", 2), locale, 2))
            assertEquals("0$separator", formatDecimalInput(decimalInput("."), locale))
        }
        assertEquals(45, months.toDays())
        assertEquals("45", months.switchTo(IntervalUnit.days).text)
    }

    @Test fun storedAndTypedIntegersUseTheSameGrouping() {
        val co = Locale.forLanguageTag("es-CO")
        assertEquals("9.000", formatIntegerInput("9000", co))
        assertEquals("1.825", formatIntegerInput("1825", co))
        assertEquals("210.000", formatAmountInput("210000", "COP", co))
        assertEquals("210.000", formatAmountInput("210.000", "COP", co))
        assertEquals(210000.0, parseAmount("210.000", co)!!, 0.0)
    }

    @Test fun typingInsertingAndDeletingKeepsCaretBesideTheSameDigit() {
        assertEquals(6, groupedInputCursor("72500", 5, "72.500"))
        assertEquals(3, groupedInputCursor("729.500", 3, "729.500"))
        assertEquals(3, groupedInputCursor("72.00", 2, "7.200"))
        assertEquals(0, groupedInputCursor("", 0, ""))
    }
}
