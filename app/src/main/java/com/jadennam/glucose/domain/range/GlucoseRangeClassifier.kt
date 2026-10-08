package com.jadennam.glucose.domain.range

import com.jadennam.glucose.domain.model.GlucoseRanges
import com.jadennam.glucose.domain.model.MeasureContext

enum class GlucoseLevel { LOW, IN_TARGET, HIGH, VERY_HIGH }

object GlucoseRangeClassifier {
    fun classify(mgDl: Int, context: MeasureContext, ranges: GlucoseRanges): GlucoseLevel {
        if (mgDl < ranges.low) return GlucoseLevel.LOW
        if (mgDl >= ranges.veryHigh) return GlucoseLevel.VERY_HIGH
        val upper = if (context == MeasureContext.FASTING) ranges.fastingHigh else ranges.targetHigh
        return if (mgDl > upper) GlucoseLevel.HIGH else GlucoseLevel.IN_TARGET
    }

    /** Validates user-edited thresholds: low < fastingLow < fastingHigh <= targetHigh < veryHigh. */
    fun isValid(r: GlucoseRanges): Boolean =
        r.low in 40..100 && r.low <= r.fastingLow && r.fastingLow < r.fastingHigh &&
            r.fastingHigh <= r.targetHigh && r.targetHigh < r.veryHigh && r.veryHigh <= 400
}
