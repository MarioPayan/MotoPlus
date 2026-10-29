package com.kaze.motoplus.ui

import com.kaze.motoplus.domain.*
import org.junit.Assert.*
import org.junit.Test

class PromptTest {
    private val bike = Bike("b", "Yamaha", "MT-07", 2021, Category.naked, Drive.chain, Cooling.liquid,
        defaultParts(Category.naked, Drive.chain, Cooling.liquid), listOf(Reading("2026-10-10", 12210)))

    @Test fun reportContractCanReturnThroughTheParser() {
        val contract = promptContract(bike, false, "COP")
        val result = parseAiResponse("```motoplus\n$contract\n```") as AiResult.Report
        assertEquals("b", result.data.bikeId)
        assertEquals(3, result.data.v)
        assertEquals(listOf(bike.parts.first().id), result.data.parts)
    }

    @Test fun invoiceContractIncludesDefaultKindVersionAndCurrency() {
        val contract = promptContract(bike, true, "COP")
        val result = parseAiResponse(contract) as AiResult.Invoice
        assertEquals("invoice", result.data.kind)
        assertEquals("COP", result.data.currency)
        assertEquals(3, result.data.v)
        assertEquals(bike.parts.first().id, result.data.jobs.single().partId)
        assertNull(result.data.jobs.single().cost)
    }
}
