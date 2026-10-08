package com.jadennam.glucose.domain.model

import java.time.LocalDate

enum class UnitSystem { METRIC, US }

enum class GlucoseUnit { MG_DL, MMOL_L }

enum class MeasureContext { FASTING, BEFORE_MEAL, AFTER_MEAL, BEDTIME }

enum class MealType { RICE, NOODLE, BREAD, MEAT, VEGETABLE, FRUIT, SNACK, DRINK }

enum class ExerciseType { JOGGING, WALKING, SWIMMING, GOLF, GYM, OTHER }

enum class ScheduleMode { FIXED, RANDOM }

data class Profile(
    val name: String,
    val birthDate: LocalDate,
    val heightCm: Double,
    val unitSystem: UnitSystem,
    val glucoseUnit: GlucoseUnit,
)

/** A timestamp is always epoch millis (UTC) plus the zone the entry was recorded in. */
data class GlucoseReading(
    val id: Long = 0,
    val valueMgDl: Int,
    val measuredAt: Long,
    val zoneId: String,
    val context: MeasureContext,
    val hoursAfterMeal: Int? = null,
    val note: String? = null,
)

data class Meal(
    val id: Long = 0,
    val eatenAt: Long,
    val zoneId: String,
    val types: List<MealType>,
    val note: String? = null,
)

data class Exercise(
    val id: Long = 0,
    val performedAt: Long,
    val zoneId: String,
    val type: ExerciseType,
    val durationMinutes: Int,
)

data class WeightEntry(
    val id: Long = 0,
    val measuredAt: Long,
    val zoneId: String,
    val weightKg: Double,
)

data class GlucoseRanges(
    val low: Int = 70,
    val targetHigh: Int = 180,
    val fastingLow: Int = 80,
    val fastingHigh: Int = 130,
    val veryHigh: Int = 250,
)

data class AppSettings(
    val scheduleMode: ScheduleMode = ScheduleMode.FIXED,
    val morningEnabled: Boolean = true,
    val morningMinute: Int = 7 * 60,
    val lunchEnabled: Boolean = false,
    val lunchMinute: Int = 12 * 60 + 30,
    val dinnerEnabled: Boolean = false,
    val dinnerMinute: Int = 19 * 60,
    val randomStartMinute: Int = 8 * 60,
    val randomEndMinute: Int = 21 * 60,
    val randomCount: Int = 2,
    val randomMinGapMinutes: Int = 120,
    val remindDelayMinutes: Int = 15,
    val exerciseCheckEnabled: Boolean = true,
    val exerciseCheckMinute: Int = 20 * 60 + 30,
    val ranges: GlucoseRanges = GlucoseRanges(),
    val kakaoEnabled: Boolean = false,
    val kakaoOnMeasure: Boolean = true,
    val kakaoOnMissingMeal: Boolean = true,
    val kakaoOnMissingExercise: Boolean = true,
    val scheduleSalt: Long = 0,
)

/** Medication record only — the app never calculates or recommends doses. endDate == null means "still taking". */
data class Medication(
    val id: Long = 0,
    val name: String,
    val timesPerDay: Int,
    val unitsPerDose: Double,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val note: String? = null,
) {
    fun isActiveOn(date: LocalDate): Boolean = !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))

    /** True if any day in [from, toExclusive) overlaps the medication period. */
    fun overlaps(from: LocalDate, toExclusive: LocalDate): Boolean =
        startDate.isBefore(toExclusive) && (endDate == null || !endDate.isBefore(from))
}
