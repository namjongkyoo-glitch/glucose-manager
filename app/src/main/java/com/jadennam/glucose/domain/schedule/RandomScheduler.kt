package com.jadennam.glucose.domain.schedule

import kotlin.random.Random

object RandomScheduler {
    const val MAX_COUNT = 3

    fun isFeasible(count: Int, startMinute: Int, endMinute: Int, minGapMinutes: Int): Boolean =
        count in 1..MAX_COUNT && startMinute in 0 until 24 * 60 && endMinute in 1 until 24 * 60 &&
            startMinute < endMinute && minGapMinutes >= 0 &&
            (count - 1).toLong() * minGapMinutes <= endMinute - startMinute

    /**
     * Picks [count] minute-of-day values in [startMinute, endMinute] with at least
     * [minGapMinutes] between consecutive values. Draws uniformly in the slack range
     * (span minus mandatory gaps), sorts, then re-inserts the gaps.
     */
    fun plan(count: Int, startMinute: Int, endMinute: Int, minGapMinutes: Int, random: Random): List<Int> {
        require(isFeasible(count, startMinute, endMinute, minGapMinutes)) { "Infeasible random schedule" }
        val slack = (endMinute - startMinute) - (count - 1) * minGapMinutes
        return List(count) { random.nextInt(slack + 1) }
            .sorted()
            .mapIndexed { i, offset -> startMinute + offset + i * minGapMinutes }
    }

    /** Deterministic per-day seed so a reboot reproduces the same times for that day. */
    fun seedFor(salt: Long, epochDay: Long): Long = salt xor (epochDay * 0x9E3779B97F4A7C15uL.toLong())
}
