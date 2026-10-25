package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

@Serializable
enum class Level { low, medium, high }

@Serializable
data class Cause(val cause: String, val likelihood: Level = Level.medium, val zone: String? = null)

/** What the rider's AI sends back, inside a ```motoplus block or as https://motoplus.app/i#r=<base64url>. */
@Serializable
data class ReportData(
    val v: Int,
    val title: String = "",
    val urgency: Level = Level.medium,
    val summary: String,
    val causes: List<Cause>,
    val checks: List<String> = emptyList(),
    val advice: String = "",
)

const val REPORT_VERSION = 2

private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

enum class ReportError { notFound, invalid, incomplete }

sealed interface ParseResult {
    data class Ok(val report: ReportData) : ParseResult
    data class Error(val error: ReportError) : ParseResult
}

fun parseReport(text: String): ParseResult {
    val raw = Regex("```motoplus\\s*([\\s\\S]*?)```").find(text)?.groupValues?.get(1)
        ?: Regex("#r=([A-Za-z0-9_-]+)").find(text)?.groupValues?.get(1)?.let { decodeLink(it) }
        ?: Regex("\\{[\\s\\S]*\"v\"\\s*:\\s*$REPORT_VERSION[\\s\\S]*\\}").find(text)?.value
        ?: return ParseResult.Error(ReportError.notFound)
    val r = try {
        json.decodeFromString<ReportData>(raw)
    } catch (e: Exception) {
        return ParseResult.Error(ReportError.invalid)
    }
    if (r.v != REPORT_VERSION || r.summary.isBlank()) return ParseResult.Error(ReportError.incomplete)
    return ParseResult.Ok(r.copy(causes = r.causes.filter { it.cause.isNotBlank() }.map { if (it.zone in Zone.ids) it else it.copy(zone = null) }))
}

private fun decodeLink(b64: String): String? =
    try { String(Base64.getUrlDecoder().decode(b64), Charsets.UTF_8) } catch (e: IllegalArgumentException) { null }

/** Picks the odometer from OCR text: the digit group (3–6 digits) closest to the current estimate. */
fun pickOdometer(ocr: String, estimate: Double): Int? =
    Regex("(?<![\\d.,])\\d{1,3}(?:[.,\\s]\\d{3})+(?![\\d])|(?<![\\d.,])\\d{3,6}(?![\\d])").findAll(ocr)
        .mapNotNull { it.value.filter(Char::isDigit).toIntOrNull() }
        .minByOrNull { kotlin.math.abs(it - estimate) }
