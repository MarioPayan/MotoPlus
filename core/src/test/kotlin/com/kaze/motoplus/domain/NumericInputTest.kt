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

    @Test fun typingInsertingAndDeletingKeepsCaretBesideTheSameDigit() {
        assertEquals(6, groupedInputCursor("72500", 5, "72.500"))
        assertEquals(3, groupedInputCursor("729.500", 3, "729.500"))
        assertEquals(3, groupedInputCursor("72.00", 2, "7.200"))
        assertEquals(0, groupedInputCursor("", 0, ""))
    }
}
