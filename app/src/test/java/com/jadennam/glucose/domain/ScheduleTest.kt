package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.model.AppSettings
import com.jadennam.glucose.domain.model.ScheduleMode
import com.jadennam.glucose.domain.schedule.AlarmKind
import com.jadennam.glucose.domain.schedule.DailyPlanner
import com.jadennam.glucose.domain.schedule.RandomScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class ScheduleTest {

    @Test
    fun randomPlanRespectsRangeCountAndGap() {
        val rnd = Random(1234)
        val configs = listOf(
            Triple(3, 8 * 60 to 21 * 60, 120),
            Triple(2, 9 * 60 to 10 * 60, 60),   // tight: exactly feasible
            Triple(1, 6 * 60 to 6 * 60 + 1, 0),
            Triple(3, 0 to 24 * 60 - 1, 0),
        )
        for ((count, range, gap) in configs) {
            repeat(1000) {
                val times = RandomScheduler.plan(count, range.first, range.second, gap, rnd)
                assertEquals(count, times.size)
                assertTrue(times.all { it in range.first..range.second })
                times.zipWithNext().forEach { (a, b) -> assertTrue("gap $a->$b", b - a >= gap) }
            }
        }
    }

    @Test
    fun randomPlanIsSpreadAcrossRange() {
        val rnd = Random(7)
        val all = (0 until 2000).flatMap { RandomScheduler.plan(1, 600, 1200, 0, rnd) }
        assertTrue(all.min() < 630)
        assertTrue(all.max() > 1170)
    }

    @Test
    fun infeasibleConfigsAreRejected() {
        assertFalse(RandomScheduler.isFeasible(3, 600, 700, 60))
        assertFalse(RandomScheduler.isFeasible(0, 600, 700, 0))
        assertFalse(RandomScheduler.isFeasible(4, 0, 1439, 0))
        assertFalse(RandomScheduler.isFeasible(1, 700, 600, 0))
        assertTrue(RandomScheduler.isFeasible(3, 600, 720, 60))
    }

    private val zone = ZoneId.of("America/Chicago")

    @Test
    fun randomDailyPlanIsDeterministicPerDayAndDiffersAcrossDays() {
        val s = AppSettings(scheduleMode = ScheduleMode.RANDOM, randomCount = 3, scheduleSalt = 99L, exerciseCheckEnabled = false)
        val d = LocalDate.of(2026, 3, 10)
        assertEquals(DailyPlanner.plan(d, zone, s), DailyPlanner.plan(d, zone, s))
        val distinct = (0L until 30L).map { DailyPlanner.plan(d.plusDays(it), zone, s).map { a -> ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(a.triggerAt), zone).toLocalTime() } }.toSet()
        assertTrue(distinct.size > 25)
    }

    @Test
    fun fixedPlanUsesEnabledSlotsAndExerciseCheck() {
        val s = AppSettings(morningEnabled = true, lunchEnabled = false, dinnerEnabled = true)
        val plan = DailyPlanner.plan(LocalDate.of(2026, 5, 1), zone, s)
        assertEquals(listOf(AlarmKind.MEASURE, AlarmKind.MEASURE, AlarmKind.EXERCISE_CHECK), plan.map { it.kind })
        assertEquals(listOf(0, 2, 0), plan.map { it.slot })
    }

    @Test
    fun nextRollsOverToTomorrow() {
        val s = AppSettings(morningEnabled = true, morningMinute = 7 * 60, exerciseCheckEnabled = false)
        val now = ZonedDateTime.of(2026, 5, 1, 23, 0, 0, 0, zone).toInstant()
        val next = DailyPlanner.next(now, zone, s)!!
        val t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(next.triggerAt), zone)
        assertEquals(LocalDate.of(2026, 5, 2), t.toLocalDate())
        assertEquals(7, t.hour)
    }

    @Test
    fun nextIsNullWhenNothingEnabled() {
        val s = AppSettings(morningEnabled = false, lunchEnabled = false, dinnerEnabled = false, exerciseCheckEnabled = false)
        assertEquals(null, DailyPlanner.next(java.time.Instant.parse("2026-05-01T12:00:00Z"), zone, s))
    }
}
