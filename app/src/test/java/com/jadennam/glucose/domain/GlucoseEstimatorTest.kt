package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.estimation.Backtester
import com.jadennam.glucose.domain.estimation.Estimate
import com.jadennam.glucose.domain.estimation.GlucoseEstimator
import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.MeasureContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseEstimatorTest {
    private val estimator = GlucoseEstimator()
    private val day = GlucoseEstimator.DAY_MS

    private fun reading(dayIndex: Int, value: Int, ctx: MeasureContext = MeasureContext.FASTING) =
        GlucoseReading(valueMgDl = value, measuredAt = dayIndex * day, zoneId = "UTC", context = ctx)

    @Test
    fun emptyDataIsInsufficient() {
        val e = estimator.estimate(emptyList(), MeasureContext.FASTING, 100 * day)
        assertTrue(e is Estimate.InsufficientData)
    }

    @Test
    fun fewerThan14DaysIsInsufficient() {
        val data = (0 until 30).map { reading(it % 10, 110 + it) }
        val e = estimator.estimate(data, MeasureContext.FASTING, 13 * day)
        assertEquals(Estimate.InsufficientData(Estimate.Reason.TOO_FEW_DAYS), e)
    }

    @Test
    fun fewerThan20ReadingsIsInsufficient() {
        val data = (0 until 19).map { reading(it, 110 + it) }
        val e = estimator.estimate(data, MeasureContext.FASTING, 30 * day)
        assertEquals(Estimate.InsufficientData(Estimate.Reason.TOO_FEW_READINGS), e)
    }

    @Test
    fun contextWithTooFewReadingsIsInsufficientWhileOthersWork() {
        val data = (0 until 25).map { reading(it, 100 + it % 7) } +
            listOf(reading(20, 160, MeasureContext.AFTER_MEAL), reading(21, 170, MeasureContext.AFTER_MEAL))
        val all = estimator.estimateAll(data, 30 * day)
        assertTrue(all.getValue(MeasureContext.FASTING) is Estimate.Available)
        assertEquals(Estimate.InsufficientData(Estimate.Reason.TOO_FEW_FOR_CONTEXT), all.getValue(MeasureContext.AFTER_MEAL))
    }

    @Test
    fun intervalContainsPointAndIsOrdered() {
        val data = DummyData.readings(60)
        val now = data.maxOf { it.measuredAt } + 1
        estimator.estimateAll(data, now).values.filterIsInstance<Estimate.Available>().forEach {
            assertTrue(it.lowerMgDl <= it.pointMgDl && it.pointMgDl <= it.upperMgDl)
        }
    }

    @Test
    fun futureReadingsAreIgnored() {
        val data = (0 until 30).map { reading(it, 110) }
        val withFuture = data + reading(100, 400)
        assertEquals(estimator.estimateAll(data, 40 * day), estimator.estimateAll(withFuture, 40 * day))
    }

    @Test
    fun backtestCoverageMeetsNinetyFivePercentOnDummyYear() {
        for (seed in listOf(42, 7, 2026)) {
            val result = Backtester.run(DummyData.readings(365, seed), estimator)
            assertTrue("evaluated=${result.evaluated}", result.evaluated > 500)
            assertTrue("seed=$seed coverage=${result.coverage}", result.coverage >= 0.95)
        }
    }
}
