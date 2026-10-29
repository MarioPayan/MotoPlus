package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class AiRoundTripTest {
    private val report = ReportData(3, "Ruido en transmisión", Level.medium, "Revisar tensión y lubricación.",
        listOf(Cause("Cadena floja", Level.high, "drivetrain")), listOf("Medir holgura"), "Revisar antes de salir", bikeId = "b", parts = listOf("chain_tension"))
    private val invoice = InvoiceData(bikeId = "b", jobs = listOf(InvoiceJob("engine_oil", 145000.0), InvoiceJob("oil_filter")), extras = listOf(ExtraJob("Ajuste de embrague", 35000.0)))
    private fun raw(link: String) = String(Base64.getUrlDecoder().decode(link.substringAfter("?d=")), Charsets.UTF_8)

    @Test fun httpsLinkPreservesUnicodeAndAllFields() {
        assertEquals(AiResult.Report(report), parseAiResponse(reportLink(report)))
        assertFalse(reportLink(report).endsWith("="))
    }

    @Test fun customSchemeUsesTheSameParser() {
        assertEquals(AiResult.Report(report), parseAiResponse(reportLink(report).replace("https://motoplus.app/r", "motoplus://r")))
    }

    @Test fun linkInsideProse() {
        assertEquals(AiResult.Report(report), parseAiResponse("Aquí está tu informe: (${reportLink(report)}). Revísalo."))
    }

    @Test fun fencedJsonAnywhere() {
        assertEquals(AiResult.Report(report), parseAiResponse("Explanation\n```json\n${raw(reportLink(report))}\n```\nEnd"))
        assertEquals(AiResult.Report(report), parseAiResponse("```text\nIgnore this\n```\n```motoplus\n${raw(reportLink(report))}\n```"))
    }

    @Test fun rawJsonInsideProseAndQuotedBraces() {
        val r = report.copy(summary = "A {quoted} brace and a \"quote\".")
        assertEquals(AiResult.Report(r), parseAiResponse("Context {irrelevant}. Here: ${raw(reportLink(r))} end."))
    }

    @Test fun truncatedBase64DoesNotThrowOrSave() {
        val link = reportLink(report)
        for (lost in listOf(1, 2, 9, 30)) assertTrue(parseAiResponse(link.dropLast(lost)) is AiResult.Error)
        assertTrue(parseAiResponse("https://motoplus.app/r?d=a") is AiResult.Error)
    }

    @Test fun rejectsWrongKindAndVersion() {
        assertTrue(parseAiResponse(raw(reportLink(report)).replace("\"report\"", "\"unknown\"")) is AiResult.Error)
        assertTrue(parseAiResponse(raw(reportLink(report)).replace("\"v\":3", "\"v\":99")) is AiResult.Error)
        assertTrue(parseAiResponse(raw(reportLink(report)).replace("\"bikeId\":\"b\"", "\"bikeId\":null")) is AiResult.Error)
    }

    @Test fun bothKindsRoundTripIncludingNullCosts() {
        assertEquals(AiResult.Report(report), parseAiResponse(reportLink(report)))
        assertEquals(AiResult.Invoice(invoice), parseAiResponse(invoiceLink(invoice)))
        assertEquals(AiResult.Invoice(invoice), parseAiResponse("```motoplus\n${raw(invoiceLink(invoice))}\n```"))
        assertEquals(AiResult.Invoice(invoice), parseAiResponse(invoiceLink(invoice).replace("https://motoplus.app/r", "motoplus://r")))
    }

    @Test fun invalidLinkCanFallBackToTheCompleteJsonBlock() {
        assertEquals(AiResult.Report(report), parseAiResponse("https://motoplus.app/r?d=a\n```json\n${raw(reportLink(report))}\n```"))
    }

    @Test fun invoiceRejectsNegativeCostsDuplicatesAndEmptyJobs() {
        assertTrue(parseAiResponse(invoiceLink(invoice.copy(jobs = listOf(InvoiceJob("engine_oil", -1.0))))) is AiResult.Error)
        assertTrue(parseAiResponse(invoiceLink(invoice.copy(jobs = invoice.jobs + invoice.jobs.first()))) is AiResult.Error)
        assertTrue(parseAiResponse(invoiceLink(invoice.copy(jobs = emptyList(), extras = emptyList()))) is AiResult.Error)
    }

    @Test fun legacyLinkAndVersionTwoJsonStillRead() {
        val legacy = report.copy(v = 2, bikeId = null, parts = emptyList())
        assertEquals(AiResult.Report(legacy), parseAiResponse(reportLink(legacy).replace("/r?d=", "/i#r=")))
    }
}
