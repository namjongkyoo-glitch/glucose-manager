package com.jadennam.glucose.domain.trend

import com.jadennam.glucose.domain.model.GlucoseReading
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
