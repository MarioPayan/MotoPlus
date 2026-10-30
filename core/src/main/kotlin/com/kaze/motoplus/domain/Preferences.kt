package com.kaze.motoplus.domain

/**
 * Features the rider can hide in the intro or Personalizar. Hiding one removes every entry point (tabs, buttons,
 * menu items, routes); its data stays. AI is not here: it is Próximamente and stays off.
 */
enum class Feature { checks, fuel, papers, workshop, kmAuto }

enum class Density { comfortable, compact }

/** Pre-ride check steps in the order Antes de salir shows them. */
val CHECK_STEPS = listOf("tires", "lights", "chain", "brakes", "oil", "papers")

/** What the app shows. Lives in the phone's preferences, not in the garage, so exports carry only the rider's data. */
data class Preferences(
    val onboarded: Boolean = false,
    val features: Set<Feature> = Feature.entries.toSet(),
    val checks: Set<String> = CHECK_STEPS.toSet(),
    /** Plano de la moto on the Moto tab. */
    val drawing: Boolean = true,
    val density: Density = Density.comfortable,
    /** Off stops maintenance and paper notifications only; nothing else changes. */
    val reminders: Boolean = true,
) {
    fun on(f: Feature) = f in features

    /** The quiet line on the Moto tab counts hidden features and a hidden blueprint. */
    val hidden: Int get() = Feature.entries.count { it !in features } + if (drawing) 0 else 1

    /** Antes de salir steps the rider picked; the papers step also needs Papeles. */
    fun checkSteps(available: List<String>) = available.filter { it in checks && (it != "papers" || on(Feature.papers)) }

    /** Volver a mostrar todo: every feature, check, the blueprint and reminders back on. */
    fun showAll() = copy(features = Feature.entries.toSet(), checks = CHECK_STEPS.toSet(), drawing = true, reminders = true)

    /** Modo mínimo: only Moto and KM. The interface choices stay. */
    fun minimal() = copy(features = emptySet())
}

/** Volver a mostrar todo watches every part of every bike; Modo mínimo watches Lo esencial. */
fun Garage.watchAll(preset: WatchPreset) = copy(bikes = bikes.map { it.withPreset(preset) })
