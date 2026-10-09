package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.model.GlucoseRanges
import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.range.GlucoseLevel
import com.jadennam.glucose.domain.summary.SummaryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SummaryCalculatorTest {
    private fun r(v: Int, ctx: MeasureContext) = GlucoseReading(valueMgDl = v, measuredAt = 0, zoneId = "UTC", context = ctx)

    @Test
    fun emptyReadings() {
        val s = SummaryCalculator.glucose(emptyList(), GlucoseRanges())
        assertEquals(0, s.count)
        assertNull(s.avgMgDl)
        assertEquals(0, s.levelPercents.values.sum())
    }

    @Test
    fun statsLevelsAndContexts() {
        val data = listOf(
            r(60, MeasureContext.FASTING),     // LOW
            r(120, MeasureContext.FASTING),    // IN_TARGET
            r(140, MeasureContext.FASTING),    // HIGH (fasting upper 130)
            r(170, MeasureContext.AFTER_MEAL), // IN_TARGET
            r(260, MeasureContext.AFTER_MEAL), // VERY_HIGH
            r(200, MeasureContext.AFTER_MEAL), // HIGH
        )
        val s = SummaryCalculator.glucose(data, GlucoseRanges())
        assertEquals(6, s.count)
        assertEquals(158, s.avgMgDl)
        assertEquals(60, s.minMgDl)
        assertEquals(260, s.maxMgDl)
        assertEquals(mapOf(GlucoseLevel.LOW to 17, GlucoseLevel.IN_TARGET to 33, GlucoseLevel.HIGH to 33, GlucoseLevel.VERY_HIGH to 17), s.levelPercents)
        assertEquals(100, s.levelPercents.values.sum())
        assertEquals(mapOf(MeasureContext.FASTING to 107, MeasureContext.AFTER_MEAL to 210), s.avgByContext)
    }

    @Test
    fun percentsAlwaysSumTo100() {
        for (total in 1..50) {
            for (a in 0..total) {
                val b = (total - a) / 2
                val c = total - a - b
                val p = SummaryCalculator.percents(mapOf("a" to a, "b" to b, "c" to c), total)
                assertEquals("total=$total a=$a", 100, p.values.sum())
                p.forEach { (k, v) -> if (mapOf("a" to a, "b" to b, "c" to c).getValue(k) == 0) assertEquals(0, v) }
            }
        }
    }
}
