package com.kmp.hook.domain.calc

import androidx.compose.runtime.compositionLocalOf
import com.kmp.hook.domain.model.UnitSystem

/** Provided at the app root from the settings StateFlow; consumed deep in the tree. */
val LocalUnitSystem = compositionLocalOf { UnitSystem.METRIC }

private const val G_PER_OZ = 28.349523125
private const val KG_PER_LB = 0.45359237

/** Formats canonical metric data (grams / kilograms) into the active unit system. */
object UnitFormat {

    /** Individual fish weight stored in grams. */
    fun weight(grams: Double, units: UnitSystem): String = when (units) {
        UnitSystem.METRIC ->
            if (grams >= 1000.0) "${(grams / 1000.0).format(2)} kg" else "${grams.format(0)} g"
        UnitSystem.IMPERIAL -> {
            val oz = grams / G_PER_OZ
            if (oz >= 16.0) "${(oz / 16.0).format(2)} lb" else "${oz.format(1)} oz"
        }
    }

    /** Bulk mass stored in kilograms (biomass, feed). */
    fun mass(kg: Double, units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> "${kg.format(if (kg >= 100) 0 else 1)} kg"
        UnitSystem.IMPERIAL -> "${(kg / KG_PER_LB).format(if (kg >= 50) 0 else 1)} lb"
    }

    fun massUnit(units: UnitSystem): String = if (units == UnitSystem.METRIC) "kg" else "lb"

    fun weightUnit(units: UnitSystem): String = if (units == UnitSystem.METRIC) "g" else "oz"

    /** Numeric-only mass in active unit (no suffix) for compact chart axes. */
    fun massValue(kg: Double, units: UnitSystem): Double =
        if (units == UnitSystem.METRIC) kg else kg / KG_PER_LB
}
