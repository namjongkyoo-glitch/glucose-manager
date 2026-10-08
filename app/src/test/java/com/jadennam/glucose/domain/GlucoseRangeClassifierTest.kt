package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.model.GlucoseRanges
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.range.GlucoseLevel
import com.jadennam.glucose.domain.range.GlucoseRangeClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseRangeClassifierTest {
    private val r = GlucoseRanges()
    private fun c(v: Int, ctx: MeasureContext = MeasureContext.AFTER_MEAL) = GlucoseRangeClassifier.classify(v, ctx, r)

    @Test
    fun boundaries() {
        assertEquals(GlucoseLevel.LOW, c(69))
        assertEquals(GlucoseLevel.IN_TARGET, c(70))
        assertEquals(GlucoseLevel.IN_TARGET, c(180))
        assertEquals(GlucoseLevel.HIGH, c(181))
        assertEquals(GlucoseLevel.HIGH, c(249))
        assertEquals(GlucoseLevel.VERY_HIGH, c(250))
    }

    @Test
    fun fastingUsesFastingUpperBound() {
        assertEquals(GlucoseLevel.IN_TARGET, c(130, MeasureContext.FASTING))
        assertEquals(GlucoseLevel.HIGH, c(131, MeasureContext.FASTING))
        assertEquals(GlucoseLevel.LOW, c(69, MeasureContext.FASTING))
    }

    @Test
    fun validation() {
        assertTrue(GlucoseRangeClassifier.isValid(r))
        assertFalse(GlucoseRangeClassifier.isValid(r.copy(targetHigh = 100)))
        assertFalse(GlucoseRangeClassifier.isValid(r.copy(veryHigh = 150)))
    }
}
