package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.model.Exercise
import com.jadennam.glucose.domain.model.ExerciseType
import com.jadennam.glucose.domain.model.Meal
import com.jadennam.glucose.domain.model.MealType
import com.jadennam.glucose.domain.model.Medication
import com.jadennam.glucose.domain.trend.ActivityAggregator
import com.jadennam.glucose.domain.trend.TrendAggregator
import com.jadennam.glucose.domain.trend.TrendPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ActivityAggregatorTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val day = LocalDate.of(2026, 10, 13)
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun weekBucketsMarkMealsExerciseAndMeds() {
        val buckets = TrendAggregator.buckets(emptyList(), TrendPeriod.WEEK, day)
        val meals = listOf(
            Meal(eatenAt = at(day, 0), zoneId = zone.id, types = listOf(MealType.RICE)), // 00:00 Seoul still same local day
            Meal(eatenAt = at(day, 12), zoneId = zone.id, types = listOf(MealType.NOODLE)),
        )
        val ex = listOf(
            Exercise(performedAt = at(day, 7), zoneId = zone.id, type = ExerciseType.WALKING, durationMinutes = 30),
            Exercise(performedAt = at(day, 18), zoneId = zone.id, type = ExerciseType.GOLF, durationMinutes = 240),
            Exercise(performedAt = at(day.minusDays(1), 18), zoneId = zone.id, type = ExerciseType.SWIMMING, durationMinutes = 45),
        )
        val meds = listOf(
            Medication(name = "더미약", timesPerDay = 1, unitsPerDose = 1.0, startDate = day.minusDays(2), endDate = null),
            Medication(name = "더미약2", timesPerDay = 1, unitsPerDose = 1.0, startDate = day.minusDays(10), endDate = day.minusDays(3)),
        )
        val a = ActivityAggregator.activity(buckets, meals, ex, meds)
        assertEquals(7, a.size)
        assertEquals(2, a.last().mealCount)
        assertEquals(ExerciseType.GOLF, a.last().mainExercise)
        assertEquals(270, a.last().exerciseMinutes)
        assertEquals(ExerciseType.SWIMMING, a[5].mainExercise)
        assertEquals(1, a.last().medicationCount)
        assertEquals(1, a[3].medicationCount) // day-3: old one still active (end inclusive)
        assertEquals(0, a[0].mealCount)
        assertNull(a[0].mainExercise)
    }

    @Test
    fun yearBucketCountsWholeWeek() {
        val buckets = TrendAggregator.buckets(emptyList(), TrendPeriod.YEAR, day)
        val meals = (0L until 7L).map { Meal(eatenAt = at(day.minusDays(it), 12), zoneId = zone.id, types = listOf(MealType.RICE)) }
        assertEquals(7, ActivityAggregator.activity(buckets, meals, emptyList(), emptyList()).last().mealCount)
    }
}
