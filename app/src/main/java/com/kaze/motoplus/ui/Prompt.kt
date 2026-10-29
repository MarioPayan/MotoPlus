package com.kaze.motoplus.ui

// TODO(i18n): phase 2 rewrites the AI handoff.

import com.kaze.motoplus.domain.*
import java.time.LocalDate

/** The message the rider pastes into their own AI: bike state in Spanish plus the JSON contract for the reply. */
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

        FORMATO: primero la explicación para mí, en español. Al final, un bloque de código con la etiqueta motoplus que contenga solo este JSON (claves en inglés, textos en español):
        {"v":$REPORT_VERSION,"title":"…","urgency":"low|medium|high","summary":"…","causes":[{"cause":"…","likelihood":"low|medium|high","zone":"…"}],"checks":["…"],"advice":"…"}
        Zonas válidas: ${Zone.ids.joinToString(", ")}.
        Si puedes ejecutar código, añade debajo un enlace https://motoplus.app/i#r=<ese JSON en base64url>. Si no puedes, no lo inventes.
    """.trimIndent()
}
