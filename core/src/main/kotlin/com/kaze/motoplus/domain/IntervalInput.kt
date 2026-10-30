package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

@Serializable
enum class IntervalUnit(val dayFactor: Double) { days(1.0), months(30.4), years(365.0) }

/** Estimates derive from the last typed value; revisiting a unit restores its exact text. */
data class IntervalInput(
    val text: String = "",
    val unit: IntervalUnit = IntervalUnit.days,
    private val remembered: Map<IntervalUnit, String> = mapOf(unit to text),
    private val anchorDays: Double? = text.toDoubleOrNull()?.times(unit.dayFactor),
) {
    fun type(value: String) = IntervalInput(value, unit)

    fun switchTo(next: IntervalUnit): IntervalInput {
        val value = remembered[next] ?: anchorDays?.let {
            BigDecimal.valueOf(it / next.dayFactor).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }.orEmpty()
        return copy(text = value, unit = next, remembered = remembered + (unit to text) + (next to value))
    }

    fun toDays(): Int? = anchorDays?.takeIf { it.isFinite() && it > 0 && it <= Int.MAX_VALUE }?.roundToInt()?.coerceAtLeast(1)

    companion object {
        fun from(part: Part): IntervalInput {
            part.timeValue?.let { return IntervalInput(it, part.timeUnit, anchorDays = part.days?.toDouble()) }
            if (part.timeUnit != IntervalUnit.days) return IntervalInput(part.days?.toString().orEmpty()).switchTo(part.timeUnit)
            val days = part.days ?: return IntervalInput()
            // Old/template intervals have no chosen display unit. Keep their exact day limit.
            val (unit, factor) = when {
                days > 0 && days % 365 == 0 -> IntervalUnit.years to 365
                days > 0 && days % 30 == 0 -> IntervalUnit.months to 30
                else -> IntervalUnit.days to 1
            }
            return IntervalInput((days / factor).toString(), unit, anchorDays = days.toDouble())
        }
    }
}
