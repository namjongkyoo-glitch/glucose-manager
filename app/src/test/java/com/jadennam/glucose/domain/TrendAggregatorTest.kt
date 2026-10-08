package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.trend.TrendAggregator
import com.jadennam.glucose.domain.trend.TrendPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TrendAggregatorTest {
    private val zone = ZoneId.of("Asia/Seoul")

    private fun r(date: LocalDate, hour: Int, v: Int) = GlucoseReading(
        valueMgDl = v, measuredAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        zoneId = zone.id, context = MeasureContext.FASTING,
    )

    @Test
    fun bucketCountsPerPeriod() {
        val anchor = LocalDate.of(2026, 10, 8)
        assertEquals(7, TrendAggregator.buckets(emptyList(), TrendPeriod.WEEK, anchor).size)
        assertEquals(30, TrendAggregator.buckets(emptyList(), TrendPeriod.MONTH, anchor).size)
        val year = TrendAggregator.buckets(emptyList(), TrendPeriod.YEAR, anchor)
        assertEquals(52, year.size)
        assertEquals(anchor.plusDays(1), year.last().endExclusive)
    }

    @Test
    fun dailyStatsUseRecordedZoneLocalDate() {
        val day = LocalDate.of(2026, 10, 8)
        // 00:30 Seoul is still the previous day in UTC — must land on Oct 8.
        val data = listOf(r(day, 0, 100), r(day, 12, 140), r(day.minusDays(1), 9, 120))
        val week = TrendAggregator.buckets(data, TrendPeriod.WEEK, day)
        val last = week.last()
        assertEquals(2, last.count)
        assertEquals(120.0, last.avgMgDl!!, 1e-9)
        assertEquals(100, last.minMgDl)
        assertEquals(140, last.maxMgDl)
        assertEquals(1, week[5].count)
        assertNull(week[0].avgMgDl)
    }

    @Test
    fun yearBucketsGroupSevenDays() {
        val anchor = LocalDate.of(2026, 10, 8)
        val data = (0 until 7).map { r(anchor.minusDays(it.toLong()), 8, 100 + it) }
        val last = TrendAggregator.buckets(data, TrendPeriod.YEAR, anchor).last()
        assertEquals(7, last.count)
        assertEquals(103.0, last.avgMgDl!!, 1e-9)
    }
}
