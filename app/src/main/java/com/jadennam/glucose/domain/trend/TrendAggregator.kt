package com.jadennam.glucose.domain.trend

import com.jadennam.glucose.domain.model.Exercise
import com.jadennam.glucose.domain.model.ExerciseType
import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.Meal
import com.jadennam.glucose.domain.model.Medication
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TrendPeriod { WEEK, MONTH, YEAR }

/** One chart point: a day (week/month view) or a 7-day block (year view). */
data class TrendBucket(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val count: Int,
    val avgMgDl: Double?,
    val minMgDl: Int?,
    val maxMgDl: Int?,
)

object TrendAggregator {
    const val YEAR_WEEKS = 52

    /** Half-open date range [start, endExclusive) ending on [anchor] inclusive. */
    fun range(period: TrendPeriod, anchor: LocalDate): Pair<LocalDate, LocalDate> {
        val end = anchor.plusDays(1)
        val days = when (period) {
            TrendPeriod.WEEK -> 7L
            TrendPeriod.MONTH -> 30L
            TrendPeriod.YEAR -> YEAR_WEEKS * 7L
        }
        return end.minusDays(days) to end
    }

    /** Each reading is placed on the local date of the zone it was recorded in. */
    fun localDate(r: GlucoseReading): LocalDate =
        Instant.ofEpochMilli(r.measuredAt).atZone(zoneOrDefault(r.zoneId)).toLocalDate()

    fun buckets(readings: List<GlucoseReading>, period: TrendPeriod, anchor: LocalDate): List<TrendBucket> {
        val (start, end) = range(period, anchor)
        val step = if (period == TrendPeriod.YEAR) 7L else 1L
        val byDate = readings.groupBy { localDate(it) }
        val out = mutableListOf<TrendBucket>()
        var d = start
        while (d < end) {
            val next = d.plusDays(step)
            val values = generateSequence(d) { it.plusDays(1) }.takeWhile { it < next }
                .flatMap { byDate[it].orEmpty().asSequence() }.map { it.valueMgDl }.toList()
            out += TrendBucket(d, next, values.size, values.takeIf { it.isNotEmpty() }?.average(), values.minOrNull(), values.maxOrNull())
            d = next
        }
        return out
    }

    private fun zoneOrDefault(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.systemDefault())
}

/** Per-bucket markers shown under the trend chart. */
data class BucketActivity(
    val mealCount: Int,
    /** Exercise type with the most minutes in the bucket, or null when none. */
    val mainExercise: ExerciseType?,
    val exerciseMinutes: Int,
    val medicationCount: Int,
)

object ActivityAggregator {
    fun activity(
        buckets: List<TrendBucket>,
        meals: List<Meal>,
        exercises: List<Exercise>,
        medications: List<Medication>,
    ): List<BucketActivity> {
        fun date(ms: Long, zone: String) = Instant.ofEpochMilli(ms).atZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault())).toLocalDate()
        val mealDates = meals.map { date(it.eatenAt, it.zoneId) }
        val ex = exercises.map { date(it.performedAt, it.zoneId) to it }
        return buckets.map { b ->
            fun inB(d: LocalDate) = !d.isBefore(b.start) && d.isBefore(b.endExclusive)
            val bucketEx = ex.filter { inB(it.first) }.map { it.second }
            BucketActivity(
                mealCount = mealDates.count(::inB),
                mainExercise = bucketEx.groupBy { it.type }.maxByOrNull { (_, l) -> l.sumOf { it.durationMinutes } }?.key,
                exerciseMinutes = bucketEx.sumOf { it.durationMinutes },
                medicationCount = medications.count { it.overlaps(b.start, b.endExclusive) },
            )
        }
    }
}
