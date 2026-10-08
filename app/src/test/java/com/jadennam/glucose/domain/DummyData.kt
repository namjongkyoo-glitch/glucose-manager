package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.model.Exercise
import com.jadennam.glucose.domain.model.ExerciseType
import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.Meal
import com.jadennam.glucose.domain.model.MealType
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.model.WeightEntry
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.random.Random

/** Seeded synthetic data only — never real health data. */
object DummyData {
    val ZONE: ZoneId = ZoneId.of("America/Chicago")
    val START: LocalDate = LocalDate.of(2025, 1, 1)

    private val baseMgDl = mapOf(
        MeasureContext.FASTING to 115.0,
        MeasureContext.BEFORE_MEAL to 125.0,
        MeasureContext.AFTER_MEAL to 165.0,
        MeasureContext.BEDTIME to 140.0,
    )

    fun readings(days: Int, seed: Int = 42): List<GlucoseReading> {
        val rnd = Random(seed)
        val out = mutableListOf<GlucoseReading>()
        for (d in 0 until days) {
            val date = START.plusDays(d.toLong())
            val trend = 1.0 - 0.05 * d / 365.0
            val weekday = if (date.dayOfWeek.value >= 6) 1.04 else 1.0
            val perDay = 1 + rnd.nextInt(3)
            val slots = listOf(
                7 * 60 to MeasureContext.FASTING,
                12 * 60 to MeasureContext.BEFORE_MEAL,
                14 * 60 to MeasureContext.AFTER_MEAL,
                22 * 60 to MeasureContext.BEDTIME,
            ).shuffled(rnd).take(perDay).sortedBy { it.first }
            for ((minute, ctx) in slots) {
                val noise = gaussian(rnd) * 0.15
                val outlier = if (rnd.nextDouble() < 0.01) 1.4 else 1.0
                val value = (baseMgDl.getValue(ctx) * trend * weekday * exp(noise) * outlier).roundToInt().coerceIn(40, 450)
                val at = date.atTime(minute / 60, minute % 60 + rnd.nextInt(20)).atZone(ZONE).toInstant().toEpochMilli()
                out += GlucoseReading(
                    id = out.size + 1L, valueMgDl = value, measuredAt = at, zoneId = ZONE.id, context = ctx,
                    hoursAfterMeal = if (ctx == MeasureContext.AFTER_MEAL) 2 else null,
                )
            }
        }
        return out
    }

    fun meals(days: Int): List<Meal> = (0 until days).map { d ->
        val at = START.plusDays(d.toLong()).atTime(12, 40).atZone(ZONE).toInstant().toEpochMilli()
        Meal(id = d + 1L, eatenAt = at, zoneId = ZONE.id, types = listOf(MealType.RICE, MealType.VEGETABLE), note = if (d % 2 == 0) "더미" else null)
    }

    fun exercises(days: Int): List<Exercise> = (0 until days).map { d ->
        val at = START.plusDays(d.toLong()).atTime(18, 0).atZone(ZONE).toInstant().toEpochMilli()
        Exercise(id = d + 1L, performedAt = at, zoneId = ZONE.id, type = ExerciseType.entries[d % ExerciseType.entries.size], durationMinutes = 30)
    }

    fun weights(days: Int): List<WeightEntry> = (0 until days step 7).mapIndexed { i, d ->
        val at = START.plusDays(d.toLong()).atTime(7, 0).atZone(ZONE).toInstant().toEpochMilli()
        WeightEntry(id = i + 1L, measuredAt = at, zoneId = ZONE.id, weightKg = 80.0 - i * 0.1)
    }

    private fun gaussian(rnd: Random): Double {
        // Box-Muller
        val u1 = rnd.nextDouble().coerceAtLeast(1e-12)
        val u2 = rnd.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * Math.PI * u2)
    }
}
