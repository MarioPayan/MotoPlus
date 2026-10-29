package com.kaze.motoplus.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import com.kaze.motoplus.R
import com.kaze.motoplus.domain.*

@Composable
fun statusLabel(status: Status) = stringResource(when (status) {
    Status.ok -> R.string.ok; Status.warn -> R.string.warn; Status.bad -> R.string.bad; Status.none -> R.string.none
})

@Composable
fun remaining(state: PartState): String = remaining(state, androidx.compose.ui.platform.LocalContext.current)

@Composable
fun estimateSource(est: OdoEstimate): String = when (est.basis) {
    EstimateBasis.todayReading -> stringResource(R.string.reading_today)
    EstimateBasis.lastReading -> stringResource(R.string.last_reading)
    EstimateBasis.average -> stringResource(R.string.average_source, "%.1f".format(est.kmPerDay))
    EstimateBasis.rides -> if (est.rides == 1) stringResource(R.string.one_ride_source) else stringResource(R.string.ride_source, est.rides)
}

@Composable
fun rideSource(ride: Ride): String = when (ride.trigger) {
    RideTrigger.manual -> stringResource(R.string.manual)
    RideTrigger.motion -> stringResource(R.string.motion)
    RideTrigger.bluetooth -> stringResource(R.string.bluetooth) + (ride.device?.let { ": $it" } ?: "")
}

@Composable
fun currencyMoney(value: Double, currency: String) = formatMoney(value, currency, LocalConfiguration.current.locales[0])

@Composable
fun partLabel(part: Part): String = partResource(part.id)?.let { stringResource(it) } ?: part.name

@Composable
fun zoneName(zone: String) = when (zone) {
    "rear_wheel" -> stringResource(R.string.zones_rear_wheel)
    "rear_brake" -> stringResource(R.string.zones_rear_brake)
    "drivetrain" -> stringResource(R.string.zones_drivetrain)
    "rear_shock" -> stringResource(R.string.zones_rear_shock)
    "battery" -> stringResource(R.string.zones_battery)
    "intake" -> stringResource(R.string.zones_intake)
    "engine" -> stringResource(R.string.zones_engine)
    "cooling" -> stringResource(R.string.zones_cooling)
    "fork" -> stringResource(R.string.zones_fork)
    "front_brake" -> stringResource(R.string.zones_front_brake)
    "front_wheel" -> stringResource(R.string.zones_front_wheel)
    "lights" -> stringResource(R.string.zones_lights)
    else -> zone
}

@Composable
fun categoryLabel(value: Category) = stringResource(when (value) {
    Category.naked -> R.string.categories_naked
    Category.sport -> R.string.categories_sport
    Category.trail -> R.string.categories_trail
    Category.scooter -> R.string.categories_scooter
    Category.custom -> R.string.categories_custom
    Category.touring -> R.string.categories_touring
    Category.offroad -> R.string.categories_offroad
})

@Composable
fun driveLabel(value: Drive) = stringResource(when (value) {
    Drive.chain -> R.string.drives_chain
    Drive.belt -> R.string.drives_belt
    Drive.shaft -> R.string.drives_shaft
    Drive.cvt -> R.string.drives_cvt
})

@Composable
fun coolingLabel(value: Cooling) = stringResource(when (value) {
    Cooling.liquid -> R.string.coolings_liquid
    Cooling.air -> R.string.coolings_air
})

fun remaining(state: PartState, ctx: android.content.Context): String = when (state.measure) {
    null -> ctx.getString(R.string.no_record)
    Measure.km -> ctx.getString(if (state.left < 0) R.string.km_over else R.string.km_left, formatKm(kotlin.math.abs(state.left), ctx.resources.configuration.locales[0]))
    Measure.days -> when {
        state.left < 0 -> ctx.getString(R.string.days_over, -state.left)
        state.left >= 60 -> ctx.getString(R.string.months_left, Math.round(state.left / 30.4))
        else -> ctx.getString(R.string.days_left, state.left)
    }
}

fun documentLabel(doc: Document, ctx: android.content.Context): String = doc.title.ifBlank {
    ctx.getString(when (doc.type) {
        DocType.transit_license -> R.string.doc_types_transit_license
        DocType.soat -> R.string.doc_types_mandatory_insurance
        DocType.inspection -> R.string.doc_types_inspection
        DocType.insurance -> R.string.doc_types_insurance
        DocType.tax -> R.string.doc_types_tax
        DocType.driving_license -> R.string.doc_types_driving_license
        DocType.other -> R.string.doc_types_other
    })
}

fun documentExpiry(doc: Document, state: DocState, ctx: android.content.Context): String {
    val date = doc.expires ?: return ctx.getString(R.string.no_expiry)
    val formatted = java.time.LocalDate.parse(date).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(ctx.resources.configuration.locales[0]))
    return ctx.getString(if (state.status == Status.bad) R.string.expired else R.string.expires, formatted)
}

fun partLabel(part: Part, ctx: android.content.Context): String = partResource(part.id)?.let { ctx.getString(it) } ?: part.name

private fun partResource(id: String): Int? = when (id) {
    "engine_oil" -> R.string.part_names_engine_oil
    "oil_filter" -> R.string.part_names_oil_filter
    "spark_plugs" -> R.string.part_names_spark_plugs
    "valve_clearance" -> R.string.part_names_valve_clearance
    "air_filter" -> R.string.part_names_air_filter
    "front_pads" -> R.string.part_names_front_pads
    "brake_fluid" -> R.string.part_names_brake_fluid
    "rear_pads" -> R.string.part_names_rear_pads
    "front_tire" -> R.string.part_names_front_tire
    "rear_tire" -> R.string.part_names_rear_tire
    "tire_pressure" -> R.string.part_names_tire_pressure
    "fork_oil" -> R.string.part_names_fork_oil
    "shock_service" -> R.string.part_names_shock_service
    "battery_check" -> R.string.part_names_battery_check
    "lights_check" -> R.string.part_names_lights_check
    "coolant" -> R.string.part_names_coolant
    "chain_lube" -> R.string.part_names_chain_lube
    "chain_tension" -> R.string.part_names_chain_tension
    "chain_kit" -> R.string.part_names_chain_kit
    "belt_tension" -> R.string.part_names_belt_tension
    "drive_belt" -> R.string.part_names_drive_belt
    "shaft_oil" -> R.string.part_names_shaft_oil
    "cvt_belt" -> R.string.part_names_cvt_belt
    "cvt_rollers" -> R.string.part_names_cvt_rollers
    "gear_oil" -> R.string.part_names_gear_oil
    else -> null
}

/** View-model messages use the same per-app locale as activity resources. */
fun localizedString(ctx: android.content.Context, id: Int, vararg args: Any): String {
    val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    val configuration = android.content.res.Configuration(ctx.resources.configuration)
    if (!locales.isEmpty) configuration.setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
    return ctx.createConfigurationContext(configuration).getString(id, *args)
}

@Composable
fun localizedDate(iso: String): String = java.time.LocalDate.parse(iso).format(
    java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]),
)

val LocalCurrency = androidx.compose.runtime.staticCompositionLocalOf { "COP" }

@Composable
fun distance(n: Int): String = formatKm(n, LocalConfiguration.current.locales[0])

@Composable
fun zoneAbbreviation(zone: String, drive: Drive): String = if (zone == "drivetrain") driveLabel(drive).uppercase() else when (zone) {
    "rear_wheel" -> stringResource(R.string.zone_short_rear_wheel)
    "rear_brake" -> stringResource(R.string.zone_short_rear_brake)
    "drivetrain" -> stringResource(R.string.zone_short_drivetrain)
    "rear_shock" -> stringResource(R.string.zone_short_rear_shock)
    "battery" -> stringResource(R.string.zone_short_battery)
    "intake" -> stringResource(R.string.zone_short_intake)
    "engine" -> stringResource(R.string.zone_short_engine)
    "cooling" -> stringResource(R.string.zone_short_cooling)
    "fork" -> stringResource(R.string.zone_short_fork)
    "front_brake" -> stringResource(R.string.zone_short_front_brake)
    "front_wheel" -> stringResource(R.string.zone_short_front_wheel)
    "lights" -> stringResource(R.string.zone_short_lights)
    else -> zone.uppercase()
}

/** The application context may retain the system locale when AppCompat overrides the activity. */
fun localizedLocale(ctx: android.content.Context): java.util.Locale =
    androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().get(0) ?: ctx.resources.configuration.locales[0]


fun rideSource(ride: Ride, ctx: android.content.Context): String = ctx.getString(when (ride.trigger) {
    RideTrigger.manual -> R.string.manual
    RideTrigger.motion -> R.string.motion
    RideTrigger.bluetooth -> R.string.bluetooth
}) + (ride.device?.let { ": $it" } ?: "")


@Composable
fun knownAiSource(source: String?): String? = listOf(
    stringResource(R.string.p2_source_chatgpt), stringResource(R.string.p2_source_gemini),
    stringResource(R.string.p2_source_claude), stringResource(R.string.p3_source_copilot),
    stringResource(R.string.p3_source_perplexity),
).firstOrNull { it == source }
