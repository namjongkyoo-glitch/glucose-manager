package com.jadennam.glucose.domain.health

import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.MeasureContext
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Health Connect relation-to-meal, mirrored so the domain stays Android-free. */
enum class MealRelation { FASTING, BEFORE_MEAL, AFTER_MEAL, GENERAL, UNKNOWN }

/** A glucose sample read from Samsung Health via Health Connect. */
data class ExternalGlucose(val time: Long, val zoneId: String, val mgDl: Double, val relation: MealRelation)

data class ImportPlan(val toInsert: List<GlucoseReading>, val duplicates: Int, val outOfRange: Int)

object GlucoseImport {
    const val SOURCE_NOTE = "삼성 헬스"
    private const val SAME_TIME_MS = 60_000L

    /** Unknown relation: before 09:00 → fasting, from 21:00 → bedtime, otherwise before meal. */
    fun contextFor(g: ExternalGlucose): MeasureContext = when (g.relation) {
        MealRelation.FASTING -> MeasureContext.FASTING
        MealRelation.BEFORE_MEAL -> MeasureContext.BEFORE_MEAL
        MealRelation.AFTER_MEAL -> MeasureContext.AFTER_MEAL
        MealRelation.GENERAL, MealRelation.UNKNOWN -> {
            val zone = runCatching { ZoneId.of(g.zoneId) }.getOrDefault(ZoneId.systemDefault())
            val hour = Instant.ofEpochMilli(g.time).atZone(zone).hour
            when {
                hour < 9 -> MeasureContext.FASTING
                hour >= 21 -> MeasureContext.BEDTIME
                else -> MeasureContext.BEFORE_MEAL
            }
        }
    }

    /**
     * Skips samples already in the app (same mg/dL within ±1 minute) — including ones imported
     * earlier — and implausible values. Never modifies existing readings.
     */
    fun plan(existing: List<GlucoseReading>, incoming: List<ExternalGlucose>): ImportPlan {
        val accepted = mutableListOf<GlucoseReading>()
        var dup = 0
        var bad = 0
        for (g in incoming.sortedBy { it.time }) {
            val mg = g.mgDl.roundToInt()
            if (mg !in 20..600) { bad++; continue }
            val clash = (existing + accepted).any { it.valueMgDl == mg && abs(it.measuredAt - g.time) <= SAME_TIME_MS }
            if (clash) { dup++; continue }
            accepted += GlucoseReading(valueMgDl = mg, measuredAt = g.time, zoneId = g.zoneId, context = contextFor(g), note = SOURCE_NOTE)
        }
        return ImportPlan(accepted, dup, bad)
    }
}
