package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.health.HealthData
import com.jadennam.glucose.domain.health.HealthExercise
import com.jadennam.glucose.domain.health.HealthSleep
import com.jadennam.glucose.domain.health.HealthSummarizer
import com.jadennam.glucose.domain.health.HealthWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HealthSummarizerTest {
    private val zone = ZoneId.of("America/Chicago")
    private val day = LocalDate.of(2026, 10, 8)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun overlappingSleepIsMergedAndAttributedToWakeDate() {
        // Phone 23:00-06:30, watch 23:30-07:00 (same night) -> 23:00-07:00 = 480 min on Oct 8
        val sleeps = listOf(
            HealthSleep(at(day.minusDays(1), 23), at(day, 6, 30)),
            HealthSleep(at(day.minusDays(1), 23, 30), at(day, 7)),
            HealthSleep(at(day, 14), at(day, 14, 30)), // nap, same wake date
        )
        assertEquals(2, HealthSummarizer.mergeIntervals(sleeps).size)
        assertEquals(mapOf(day to 510), HealthSummarizer.sleepMinutesByWakeDate(sleeps, zone))
    }

    @Test
    fun invalidSleepIntervalsAreDropped() {
        assertEquals(emptyList<HealthSleep>(), HealthSummarizer.mergeIntervals(listOf(HealthSleep(10, 10), HealthSleep(20, 5))))
    }

    @Test
    fun summarizeWeekRange() {
        val data = HealthData(
            stepsByDate = mapOf(day to 8000L, day.minusDays(1) to 4000L, day.minusDays(7) to 99999L, day.minusDays(2) to 0L),
            sleeps = listOf(HealthSleep(at(day.minusDays(1), 23), at(day, 6)), HealthSleep(at(day.minusDays(2), 22), at(day.minusDays(1), 6))),
            exercises = listOf(
                HealthExercise(at(day, 18), at(day, 18, 45), "걷기", null),
                HealthExercise(at(day.minusDays(10), 8), at(day.minusDays(10), 9), "달리기", null),
            ),
            weights = listOf(HealthWeight(at(day.minusDays(20), 7), 81.0), HealthWeight(at(day.minusDays(3), 7), 80.2), HealthWeight(at(day.plusDays(2), 7), 79.0)),
        )
        val s = HealthSummarizer.summarize(data, day.minusDays(6), day.plusDays(1), zone)
        assertEquals(12000L, s.totalSteps)
        assertEquals(6000L, s.avgDailySteps) // zero-step day ignored
        assertEquals(2, s.sleepNights)
        assertEquals(450, s.avgSleepMinutes) // (420 + 480) / 2
        assertEquals(listOf(45), s.exercises.map { it.minutes })
        assertEquals(80.2, s.latestWeight!!.kg, 1e-9)
    }

    @Test
    fun emptyDataGivesNulls() {
        val s = HealthSummarizer.summarize(HealthData(emptyMap(), emptyList(), emptyList(), emptyList()), day, day.plusDays(1), zone)
        assertNull(s.totalSteps); assertNull(s.avgSleepMinutes); assertNull(s.latestWeight)
        assertEquals(0, s.sleepNights)
    }

    @Test
    fun formatMinutes() {
        assertEquals("7시간 5분", HealthSummarizer.formatMinutes(425))
        assertEquals("45분", HealthSummarizer.formatMinutes(45))
    }
}
