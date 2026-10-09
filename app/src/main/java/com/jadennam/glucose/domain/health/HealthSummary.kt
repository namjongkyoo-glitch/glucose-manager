package com.jadennam.glucose.domain.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Read-only data from Samsung Health via Health Connect. Never stored in the app DB. */
data class HealthSleep(val start: Long, val end: Long)

data class HealthExercise(val start: Long, val end: Long, val typeLabel: String, val title: String?) {
    val minutes: Int get() = ((end - start) / 60_000L).toInt()
}

data class HealthWeight(val time: Long, val kg: Double)

data class HealthData(
    val stepsByDate: Map<LocalDate, Long>,
    val sleeps: List<HealthSleep>,
    val exercises: List<HealthExercise>,
    val weights: List<HealthWeight>,
)

data class HealthPeriodSummary(
    val totalSteps: Long?,
    val avgDailySteps: Long?,
    /** Average per night, nights attributed to the date they ended (wake-up date). */
    val avgSleepMinutes: Int?,
    val sleepNights: Int,
    val exercises: List<HealthExercise>,
    val latestWeight: HealthWeight?,
)

object HealthSummarizer {

    /** Merges overlapping/touching intervals so watch + phone duplicates are not double counted. */
    fun mergeIntervals(sleeps: List<HealthSleep>): List<HealthSleep> {
        val sorted = sleeps.filter { it.end > it.start }.sortedBy { it.start }
        val out = mutableListOf<HealthSleep>()
        for (s in sorted) {
            val last = out.lastOrNull()
            if (last != null && s.start <= last.end) out[out.lastIndex] = last.copy(end = maxOf(last.end, s.end))
            else out += s
        }
        return out
    }

    /** Sleep minutes per wake-up date (local date of the session end). */
    fun sleepMinutesByWakeDate(sleeps: List<HealthSleep>, zone: ZoneId): Map<LocalDate, Int> =
        mergeIntervals(sleeps)
            .groupBy { Instant.ofEpochMilli(it.end).atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { ((it.end - it.start) / 60_000L).toInt() } }

    fun summarize(data: HealthData, start: LocalDate, endExclusive: LocalDate, zone: ZoneId): HealthPeriodSummary {
        fun inRange(d: LocalDate) = !d.isBefore(start) && d.isBefore(endExclusive)
        val fromMs = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

        val steps = data.stepsByDate.filterKeys(::inRange).filterValues { it > 0 }
        val sleep = sleepMinutesByWakeDate(data.sleeps, zone).filterKeys(::inRange)
        return HealthPeriodSummary(
            totalSteps = steps.values.takeIf { it.isNotEmpty() }?.sum(),
            avgDailySteps = steps.values.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
            avgSleepMinutes = sleep.values.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
            sleepNights = sleep.size,
            exercises = data.exercises.filter { it.start in fromMs until toMs }.sortedBy { it.start },
            latestWeight = data.weights.filter { it.time < toMs }.maxByOrNull { it.time },
        )
    }

    fun formatMinutes(min: Int): String = if (min >= 60) "${min / 60}시간 ${min % 60}분" else "${min}분"
}
