package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.Base64

@Serializable
enum class Level { low, medium, high }

@Serializable
data class Cause(val cause: String, val likelihood: Level = Level.medium, val zone: String? = null)

/** Saved v2 reports and v3 AI reports share additive, defaulted fields. */
@Serializable
data class ReportData(
    val v: Int,
    val title: String = "",
    val urgency: Level = Level.medium,
    val summary: String,
    val causes: List<Cause>,
    val checks: List<String> = emptyList(),
    val advice: String = "",
    val kind: String = "report",
    val bikeId: String? = null,
    val parts: List<String> = emptyList(),
)

const val REPORT_VERSION = 2

private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

enum class ReportError { notFound, invalid, incomplete }

sealed interface ParseResult {
    data class Ok(val report: ReportData) : ParseResult
    data class Error(val error: ReportError) : ParseResult
}

/** One parser for deep links, Android shares and pasted answers, including legacy v2 reports. */
@Serializable
data class InvoiceJob(val partId: String, val cost: Double? = null)

@Serializable
data class ExtraJob(val name: String, val cost: Double? = null)

@Serializable
data class InvoiceData(
    val v: Int = 3,
    val kind: String = "invoice",
    val bikeId: String? = null,
    val currency: String = "COP",
    val jobs: List<InvoiceJob> = emptyList(),
    val extras: List<ExtraJob> = emptyList(),
    val date: String? = null,
    val km: Int? = null,
)

sealed interface AiResult {
    data class Report(val data: ReportData) : AiResult
    data class Invoice(val data: InvoiceData) : AiResult
    data class Error(val error: ReportError) : AiResult
}

private val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun reportLink(report: ReportData): String = aiLink(wireJson.encodeToString(report))
fun invoiceLink(invoice: InvoiceData): String = aiLink(wireJson.encodeToString(invoice))
private fun aiLink(raw: String) = "https://motoplus.app/r?d=" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))

fun parseAiResponse(text: String): AiResult {
    if (text.length > 100_000) return AiResult.Error(ReportError.invalid)
    val links = Regex("(?:https://motoplus\\.app/r\\?d=|motoplus://r\\?d=|https://motoplus\\.app/i#r=)([A-Za-z0-9_=-]+)").findAll(text).toList()
    val candidates = buildList {
        links.forEach { link ->
            runCatching { String(Base64.getUrlDecoder().decode(link.groupValues[1]), Charsets.UTF_8) }.getOrNull()?.let(::add)
        }
        Regex("```(?:motoplus|json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).findAll(text).forEach { add(it.groupValues[1]) }
        addAll(jsonObjects(text))
    }
    var error: AiResult.Error = AiResult.Error(if (links.isEmpty() && candidates.isEmpty()) ReportError.notFound else ReportError.invalid)
    for (raw in candidates.distinct()) {
        when (val result = parsePayload(raw)) {
            is AiResult.Error -> error = result
            else -> return result
        }
    }
    return error
}

/** Balanced objects allow surrounding prose, braces inside strings and multiple code blocks. */
private fun jsonObjects(text: String): List<String> {
    val objects = mutableListOf<String>()
    var start = -1
    var depth = 0
    var quoted = false
    var escaped = false
    text.forEachIndexed { index, c ->
        if (depth == 0) {
            if (c == '{') { start = index; depth = 1; quoted = false; escaped = false }
        } else if (quoted) {
            if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
        } else when (c) {
            '"' -> quoted = true
            '{' -> depth++
            '}' -> { depth--; if (depth == 0) objects.add(text.substring(start, index + 1)) }
        }
    }
    return objects
}

private fun parsePayload(raw: String): AiResult {
    return try {
        val root = wireJson.parseToJsonElement(raw).jsonObject
        val version = root["v"]?.jsonPrimitive?.intOrNull
        val kind = root["kind"]?.jsonPrimitive?.content ?: if (version == 2) "report" else ""
        when {
            version !in listOf(2, 3) -> AiResult.Error(ReportError.incomplete)
            version == 3 && root["bikeId"]?.jsonPrimitive?.contentOrNull.isNullOrBlank() -> AiResult.Error(ReportError.incomplete)
            kind == "report" -> {
                val r = (if (version == 2) json else wireJson).decodeFromJsonElement<ReportData>(root)
                if (r.summary.isBlank() || r.summary.length > 1200 || r.title.length > 100 || r.causes.size > 60 || r.checks.size > 60 || r.parts.size > 60 || r.advice.length > 800 || r.causes.any { it.cause.isBlank() || it.cause.length > 500 } || r.checks.any { it.length > 500 }) AiResult.Error(ReportError.incomplete)
                else AiResult.Report(r.copy(causes = r.causes.map { if (it.zone in Zone.ids) it else it.copy(zone = null) }))
            }
            kind == "invoice" && version == 3 -> {
                val r = wireJson.decodeFromJsonElement<InvoiceData>(root)
                fun validCost(n: Double?) = n == null || (n.isFinite() && n >= 0 && n <= 999999999)
                if (r.jobs.size + r.extras.size !in 1..60 || r.jobs.map { it.partId }.distinct().size != r.jobs.size || r.jobs.any { it.partId.isBlank() || !validCost(it.cost) } || r.extras.any { it.name.isBlank() || it.name.length > 80 || !validCost(it.cost) } || r.km?.let { it < 0 } == true || r.date?.let { runCatching { java.time.LocalDate.parse(it) }.isFailure } == true) AiResult.Error(ReportError.invalid)
                else AiResult.Invoice(r)
            }
            else -> AiResult.Error(ReportError.invalid)
        }
    } catch (_: Exception) { AiResult.Error(ReportError.invalid) }
}

/** Legacy callers still receive only reports. */
fun parseReport(text: String): ParseResult = when (val result = parseAiResponse(text)) {
    is AiResult.Report -> ParseResult.Ok(result.data)
    is AiResult.Invoice -> ParseResult.Error(ReportError.invalid)
    is AiResult.Error -> ParseResult.Error(result.error)
}

/** Picks the odometer from OCR text: the digit group (3–6 digits) closest to the current estimate. */
fun pickOdometer(ocr: String, estimate: Double): Int? =
    Regex("(?<![\\d.,])\\d{1,3}(?:[.,\\s]\\d{3})+(?![\\d])|(?<![\\d.,])\\d{3,6}(?![\\d])").findAll(ocr)
        .mapNotNull { it.value.filter(Char::isDigit).toIntOrNull() }
        .minByOrNull { kotlin.math.abs(it - estimate) }
