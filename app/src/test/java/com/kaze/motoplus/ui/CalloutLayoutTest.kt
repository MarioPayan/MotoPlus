package com.kaze.motoplus.ui

import org.junit.Assert.*
import org.junit.Test

class CalloutLayoutTest {
    @Test fun fitsLabelsWithoutOverlapAcrossPhoneWidthsAndFontScales() {
        for (width in listOf(240f, 320f, 432f, 720f, 1440f)) {
            for (fontScale in listOf(1f, 1.3f, 2f, 3f)) {
                // Long es-CO labels, passed in urgency order, with nearby wheel/chain/engine anchors.
                val metrics = listOf(180f, 160f, 165f, 100f, 130f).mapIndexed { i, w ->
                    CalloutMetric(w * fontScale, width * (.3f + i * .08f))
                }
                val row = layoutCalloutRow(metrics, width, 4f, 8f) { 25f * fontScale }
                val placements = (row.labels + listOfNotNull(row.summary)).sortedBy { it.left }
                assertEquals(metrics.size, row.labels.size + row.hidden)
                assertEquals((0 until row.labels.size).toSet(), row.labels.map { it.index }.toSet())
                placements.forEach {
                    assertTrue(it.left >= 4f)
                    assertTrue(it.left + it.width <= width - 4f + .01f)
                }
                placements.zipWithNext().forEach { (a, b) -> assertTrue(a.left + a.width + 8f <= b.left + .01f) }
                if (row.hidden > 0) assertNotNull(row.summary)
            }
        }
    }

    @Test fun keepsAllLabelsWhenTheyFitAndPositionsThemByTheirAnchors() {
        val row = layoutCalloutRow(listOf(CalloutMetric(100f, 500f), CalloutMetric(100f, 150f)), 720f, 4f, 8f) { 25f }
        assertEquals(0, row.hidden)
        assertNull(row.summary)
        assertEquals(listOf(1, 0), row.labels.map { it.index })
    }

    @Test fun longFirstLabelIsConstrainedAndEmptyMarginHasNoSummary() {
        val row = layoutCalloutRow(listOf(CalloutMetric(2000f, 100f), CalloutMetric(80f, 200f)), 320f, 4f, 8f) { 25f }
        assertEquals(1, row.labels.size)
        assertEquals(1, row.hidden)
        assertEquals(279f, row.labels.single().width, .01f)
        assertEquals(CalloutRow(emptyList(), 0, null), layoutCalloutRow(emptyList(), 320f, 4f, 8f) { 25f })
    }
}
