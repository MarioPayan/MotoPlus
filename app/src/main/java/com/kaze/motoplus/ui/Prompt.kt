package com.kaze.motoplus.ui

import android.content.Context
import com.kaze.motoplus.R
import com.kaze.motoplus.domain.*
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val promptJson = Json { encodeDefaults = true }

/** Machine-readable output examples use generated serializers, including in the R8 build. */
fun promptContract(bike: Bike, invoice: Boolean, currency: String): String = if (invoice) {
    promptJson.encodeToString(InvoiceData(bikeId = bike.id, currency = currency,
        jobs = bike.parts.take(1).map { InvoiceJob(it.id) }, extras = listOf(ExtraJob("…"))))
} else {
    promptJson.encodeToString(ReportData(v = 3, kind = "report", bikeId = bike.id, title = "…", summary = "…",
        causes = listOf(Cause("…", zone = "drivetrain")), checks = listOf("…"), advice = "…", parts = bike.parts.take(1).map { it.id }))
}

fun buildPrompt(ctx: Context, bike: Bike, notes: String, today: LocalDate, currency: String, invoice: Boolean): String {
    val est = estimate(bike, today)
    val locale = ctx.resources.configuration.locales[0]
    return buildList {
        add(ctx.getString(if (invoice) R.string.p2_ai_prompt_invoice else R.string.p2_prompt_intro))
        add(ctx.getString(R.string.p2_prompt_context, bike.title, formatKm(est.km.toInt(), locale), notes.ifBlank { ctx.getString(R.string.p2_general_review) }))
        addAll(bike.parts.map { p -> ctx.getString(R.string.p2_part_context, p.id + " · " + partLabel(p, ctx), p.zone, remaining(partState(p, est.km, today), ctx)) })
        if (invoice) add(ctx.getString(R.string.p2_ai_prompt_jobs, bike.openOrder()?.let { o -> (o.partIds + o.customItems.map { it.id + " · " + it.name }).joinToString(", ") }.orEmpty()))
        add(ctx.getString(R.string.p2_ai_prompt_parts, bike.parts.joinToString(", ") { it.id }))
        add(ctx.getString(R.string.p2_ai_prompt_currency, currency))
        add(ctx.getString(R.string.p2_ai_prompt_link))
        add(ctx.getString(R.string.p2_ai_prompt_contract))
        add(promptContract(bike, invoice, currency))
        add(promptJson.encodeToString(Zone.ids.toList()))
    }.joinToString("\n\n")
}
