package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable

/**
 * Fixed part categories, in display order. Mi moto, Registrar mantenimiento, Completar visita, Pendientes and the
 * AI report group parts by them; Lo próximo stays sorted by urgency. [zone] is where a custom part of this category
 * sits on the drawing ("" = off the drawing).
 */
@Serializable
enum class PartCategory(val zone: String) {
    tires("rear_wheel"), brakes("front_brake"), engine("engine"), drivetrain("drivetrain"),
    suspension("fork"), electrical("battery"), other(""),
}

private val templateCategory = TEMPLATE_CATEGORIES.flatMap { (c, ids) -> ids.map { it to c } }.toMap()
private val templateOrder = TEMPLATE_CATEGORIES.values.flatten().withIndex().associate { it.value to it.index }

const val CUSTOM_PART_PREFIX = "custom_"

/** Parts the rider added; only these can be deleted. */
val Part.isCustom get() = id.startsWith(CUSTOM_PART_PREFIX)

/** A custom part's own category, else its template's; anything unknown is Otros. */
val Part.partCategory: PartCategory get() = category ?: templateCategory[id] ?: PartCategory.other

/** Category order, then the fixed order inside it; custom parts keep their insertion order after the template ones. */
fun List<Part>.sortedByCategory(): List<Part> =
    sortedWith(compareBy<Part> { it.partCategory.ordinal }.thenBy { templateOrder[it.id] ?: Int.MAX_VALUE })

/** Non-empty categories in display order, each with its parts in order. */
fun List<Part>.byCategory(): List<Pair<PartCategory, List<Part>>> = sortedByCategory().groupBy { it.partCategory }.toList()

val Bike.watchedParts get() = parts.filter { it.watched }

fun Bike.watch(partId: String, on: Boolean) = copy(parts = parts.map { if (it.id == partId) it.copy(watched = on) else it })

/** A category's switch sets every part in it. */
fun Bike.watchCategory(c: PartCategory, on: Boolean) = copy(parts = parts.map { if (it.partCategory == c) it.copy(watched = on) else it })

/** Watch presets of the intro: Lo esencial is tires, brakes, the drivetrain and the oil change. */
enum class WatchPreset { essential, all, none }

fun WatchPreset.includes(p: Part) = when (this) {
    WatchPreset.all -> true
    WatchPreset.none -> false
    WatchPreset.essential -> p.partCategory in setOf(PartCategory.tires, PartCategory.brakes, PartCategory.drivetrain) || p.id in setOf("engine_oil", "oil_filter")
}

fun Bike.withPreset(preset: WatchPreset) = copy(parts = parts.map { it.copy(watched = preset.includes(it)) })

/** The preset the bike's switches match exactly, if any. */
val Bike.preset: WatchPreset? get() = WatchPreset.entries.firstOrNull { pr -> parts.all { it.watched == pr.includes(it) } }

fun Bike.addCustomPart(id: String, name: String, category: PartCategory, km: Int?, days: Int?, timeUnit: IntervalUnit, timeValue: String?): Bike {
    require(id.startsWith(CUSTOM_PART_PREFIX) && name.isNotBlank() && (km != null || days != null))
    return copy(parts = parts + Part(id, category.zone, name.trim(), km, days, timeUnit = timeUnit, timeValue = timeValue, category = category))
}

/** What a drivetrain or cooling change did to the parts, for the notice in Mi moto. */
data class PartSwap(val bike: Bike, val added: List<Part>, val removed: List<Part>)

/**
 * Changing transmission or cooling swaps the template parts: new ones are added, ones that no longer apply leave
 * the list and the Taller list, keeping their history under [label]. Custom parts always stay.
 */
fun Bike.withDrivetrain(drive: Drive, cooling: Cooling, label: (Part) -> String): PartSwap {
    if (drive == this.drive && cooling == this.cooling) return PartSwap(this, emptyList(), emptyList())
    val defaults = defaultParts(category, drive, cooling)
    val removed = parts.filter { p -> !p.isCustom && defaults.none { it.id == p.id } }
    val added = defaults.filter { d -> parts.none { it.id == d.id } }
    val gone = removed.associate { it.id to label(it) }
    val b = copy(
        drive = drive, cooling = cooling,
        parts = parts.filter { it.id !in gone } + added,
        history = history.map { r -> gone[r.partId]?.takeIf { r.partName == null }?.let { r.copy(partName = it) } ?: r },
        orders = orders.map { o -> o.copy(partIds = o.partIds - gone.keys, itemNotes = o.itemNotes - gone.keys) },
        notified = notified - gone.keys,
    )
    return PartSwap(b, added, removed)
}
