package com.jadennam.glucose.domain.units

import com.jadennam.glucose.domain.model.GlucoseUnit
import java.util.Locale
import kotlin.math.roundToInt

/** All conversions happen at the display layer; storage is always mg/dL, cm and kg. */
object UnitConverter {
    const val MGDL_PER_MMOL = 18.0
    const val CM_PER_INCH = 2.54
    const val KG_PER_LB = 0.45359237

    fun mgDlToMmol(mgDl: Int): Double = mgDl / MGDL_PER_MMOL
    fun mmolToMgDl(mmol: Double): Int = (mmol * MGDL_PER_MMOL).roundToInt()

    /** Returns (feet, inches) with inches rounded to one decimal. */
    fun cmToFeetInches(cm: Double): Pair<Int, Double> {
        val totalInches = cm / CM_PER_INCH
        var feet = (totalInches / 12).toInt()
        var inches = ((totalInches - feet * 12) * 10).roundToInt() / 10.0
        if (inches >= 12.0) { feet += 1; inches -= 12.0 }
        return feet to inches
    }

    fun feetInchesToCm(feet: Int, inches: Double): Double = (feet * 12 + inches) * CM_PER_INCH

    fun kgToLb(kg: Double): Double = kg / KG_PER_LB
    fun lbToKg(lb: Double): Double = lb * KG_PER_LB

    fun formatGlucose(mgDl: Int, unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_DL -> mgDl.toString()
        GlucoseUnit.MMOL_L -> String.format(Locale.US, "%.1f", mgDlToMmol(mgDl))
    }

    fun unitLabel(unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_DL -> "mg/dL"
        GlucoseUnit.MMOL_L -> "mmol/L"
    }

    /** Parses user input in the given unit into mg/dL, or null when not a plausible value. */
    fun parseGlucose(input: String, unit: GlucoseUnit): Int? {
        val v = input.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val mg = when (unit) {
            GlucoseUnit.MG_DL -> v.roundToInt()
            GlucoseUnit.MMOL_L -> mmolToMgDl(v)
        }
        return mg.takeIf { it in 20..600 }
    }
}
