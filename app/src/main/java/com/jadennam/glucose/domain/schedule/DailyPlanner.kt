package com.jadennam.glucose.domain.schedule

import com.jadennam.glucose.domain.model.AppSettings
import com.jadennam.glucose.domain.model.ScheduleMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

enum class AlarmKind { MEASURE, EXERCISE_CHECK }

/** [slot]: 0/1/2 = morning/lunch/dinner in FIXED mode, order index in RANDOM mode. */
data class PlannedAlarm(val triggerAt: Long, val kind: AlarmKind, val slot: Int, val mode: ScheduleMode)

object DailyPlanner {

    fun plan(date: LocalDate, zone: ZoneId, s: AppSettings): List<PlannedAlarm> {
        fun at(minute: Int): Long =
            date.atTime(LocalTime.of(minute / 60 % 24, minute % 60)).atZone(zone).toInstant().toEpochMilli()

        val measures = when (s.scheduleMode) {
            ScheduleMode.FIXED -> listOf(
                Triple(0, s.morningEnabled, s.morningMinute),
                Triple(1, s.lunchEnabled, s.lunchMinute),
                Triple(2, s.dinnerEnabled, s.dinnerMinute),
            ).filter { it.second }.map { PlannedAlarm(at(it.third), AlarmKind.MEASURE, it.first, s.scheduleMode) }

            ScheduleMode.RANDOM -> {
                if (!RandomScheduler.isFeasible(s.randomCount, s.randomStartMinute, s.randomEndMinute, s.randomMinGapMinutes)) {
                    emptyList()
                } else {
                    val rnd = Random(RandomScheduler.seedFor(s.scheduleSalt, date.toEpochDay()))
                    RandomScheduler.plan(s.randomCount, s.randomStartMinute, s.randomEndMinute, s.randomMinGapMinutes, rnd)
                        .mapIndexed { i, m -> PlannedAlarm(at(m), AlarmKind.MEASURE, i, s.scheduleMode) }
                }
            }
        }
        val exercise = if (s.exerciseCheckEnabled) {
            listOf(PlannedAlarm(at(s.exerciseCheckMinute), AlarmKind.EXERCISE_CHECK, 0, s.scheduleMode))
        } else emptyList()
        return (measures + exercise).sortedBy { it.triggerAt }
    }

    /** Earliest alarm strictly after [now], looking at today and the next two days. */
    fun next(now: Instant, zone: ZoneId, s: AppSettings): PlannedAlarm? {
        val today = now.atZone(zone).toLocalDate()
        val nowMs = now.toEpochMilli()
        for (d in 0L..2L) {
            plan(today.plusDays(d), zone, s).firstOrNull { it.triggerAt > nowMs }?.let { return it }
        }
        return null
    }
}
