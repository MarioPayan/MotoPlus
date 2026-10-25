package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.Base64

@Serializable
data class Cause(val causa: String, val probabilidad: String = "", val zona: String? = null)

/** What the rider's AI sends back, inside a ```motoplus block or as https://motoplus.app/i#r=<base64url>. */
@Serializable
data class ReportData(
    val v: Int,
    val titulo: String = "Informe",
    val urgencia: String = "media",
    val resumen: String,
    val causas: List<Cause>,
    val comprobaciones: List<String> = emptyList(),
    val recomendacion: String = "",
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

sealed interface ParseResult {
    data class Ok(val report: ReportData) : ParseResult
    data class Error(val message: String) : ParseResult
}

fun parseReport(text: String): ParseResult {
    val raw = Regex("```motoplus\\s*([\\s\\S]*?)```").find(text)?.groupValues?.get(1)
        ?: Regex("#r=([A-Za-z0-9_-]+)").find(text)?.groupValues?.get(1)?.let { decodeLink(it) }
        ?: Regex("\\{[\\s\\S]*\"v\"\\s*:\\s*1[\\s\\S]*\\}").find(text)?.value
        ?: return ParseResult.Error("No encuentro el bloque motoplus ni el enlace. Copia la respuesta completa de la IA.")
    val r = try {
        json.decodeFromString<ReportData>(raw)
    } catch (e: Exception) {
        return ParseResult.Error("El bloque está incompleto o no es JSON válido. Pide a la IA que repita solo el bloque motoplus.")
    }
    if (r.v != 1 || r.resumen.isBlank()) return ParseResult.Error("El bloque está incompleto: falta el resumen.")
    return ParseResult.Ok(
        r.copy(
            urgencia = r.urgencia.takeIf { it in setOf("baja", "media", "alta") } ?: "media",
            causas = r.causas.filter { it.causa.isNotBlank() }.map { if (it.zona in ZONES) it else it.copy(zona = null) },
        )
    )
}

private fun decodeLink(b64: String): String? =
    try { String(Base64.getUrlDecoder().decode(b64), Charsets.UTF_8) } catch (e: IllegalArgumentException) { null }

fun buildPrompt(bike: Bike, symptom: String, today: LocalDate): String {
    val est = estimate(bike, today)
    val parts = bike.parts.joinToString("\n") { p ->
        val s = partState(p, est.km, today)
        val last = if (p.lastDate == null) "sin registro" else "última a ${p.lastKm?.let { fmtKm(it) + " km" } ?: "?"} el ${p.lastDate}"
        "- ${p.name} [${p.zone}]: $last → ${s.text}"
    }
    val rides = bike.rides.takeLast(4).joinToString("\n") { "- ${it.date}: ${it.km.toInt()} km${if (it.wet) " con lluvia" else ""}" }
        .ifEmpty { "- (sin salidas registradas)" }
    return """
        Eres un mecánico de motos con experiencia. Te paso el estado de mi moto desde la app MotoPlus. Explícame en lenguaje sencillo qué puede estar pasando y qué revisar. Sé claro si debo dejar de usarla. No sustituyes una revisión en taller.

        MOTO: ${bike.title} (${bike.category.label}, ${bike.drive.label.lowercase()}, refrigeración ${bike.cooling.label.lowercase()}). Odómetro: ~${fmtKm(est.km.toInt())} km (± ${est.margin}).
        SÍNTOMA: ${symptom.ifBlank { "(sin síntoma: revisión general)" }}

        MANTENIMIENTO:
        $parts

        SALIDAS RECIENTES:
        $rides

        FORMATO: primero la explicación para mí. Al final, un bloque de código con la etiqueta motoplus que contenga solo este JSON:
        {"v":1,"titulo":"…","urgencia":"baja|media|alta","resumen":"…","causas":[{"causa":"…","probabilidad":"alta|media|baja","zona":"…"}],"comprobaciones":["…"],"recomendacion":"…"}
        Zonas válidas: ${ZONES.keys.joinToString(", ")}.
        Si puedes ejecutar código, añade debajo un enlace https://motoplus.app/i#r=<ese JSON en base64url>. Si no puedes, no lo inventes.
    """.trimIndent()
}

/** Picks the odometer from OCR text: the digit group (3–6 digits) closest to the current estimate. */
fun pickOdometer(ocr: String, estimate: Double): Int? =
    Regex("(?<![\\d.,])\\d{1,3}(?:[.,\\s]\\d{3})+(?![\\d])|(?<![\\d.,])\\d{3,6}(?![\\d])").findAll(ocr)
        .mapNotNull { it.value.filter(Char::isDigit).toIntOrNull() }
        .minByOrNull { kotlin.math.abs(it - estimate) }
