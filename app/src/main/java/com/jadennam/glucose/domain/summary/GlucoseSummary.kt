package com.jadennam.glucose.domain.summary

import com.jadennam.glucose.domain.model.GlucoseRanges
import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.range.GlucoseLevel
import com.jadennam.glucose.domain.range.GlucoseRangeClassifier
import kotlin.math.roundToInt

data class GlucoseSummary(
    val count: Int,
    val avgMgDl: Int?,
    val minMgDl: Int?,
    val maxMgDl: Int?,
    /** Percent (0–100) per level; sums to 100 when count > 0 (largest-remainder rounding). */
    val levelPercents: Map<GlucoseLevel, Int>,
    val avgByContext: Map<MeasureContext, Int>,
)

object SummaryCalculator {

    fun glucose(readings: List<GlucoseReading>, ranges: GlucoseRanges): GlucoseSummary {
        if (readings.isEmpty()) {
            return GlucoseSummary(0, null, null, null, GlucoseLevel.entries.associateWith { 0 }, emptyMap())
        }
        val values = readings.map { it.valueMgDl }
        val counts = GlucoseLevel.entries.associateWith { level ->
            readings.count { GlucoseRangeClassifier.classify(it.valueMgDl, it.context, ranges) == level }
        }
        return GlucoseSummary(
            count = readings.size,
            avgMgDl = values.average().roundToInt(),
            minMgDl = values.min(),
            maxMgDl = values.max(),
            levelPercents = percents(counts, readings.size),
            avgByContext = readings.groupBy { it.context }.mapValues { (_, l) -> l.map { it.valueMgDl }.average().roundToInt() },
        )
    }

    /** Largest-remainder rounding so the shown percentages always add up to 100. */
    fun <K> percents(counts: Map<K, Int>, total: Int): Map<K, Int> {
        if (total == 0) return counts.mapValues { 0 }
        val raw = counts.mapValues { it.value * 100.0 / total }
        val floors = raw.mapValues { it.value.toInt() }.toMutableMap()
        var remaining = 100 - floors.values.sum()
        raw.entries.sortedByDescending { it.value - it.value.toInt() }.forEach { e ->
            if (remaining > 0 && counts.getValue(e.key) > 0) { floors[e.key] = floors.getValue(e.key) + 1; remaining-- }
        }
        return floors
    }
}
