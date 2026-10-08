package com.jadennam.glucose.data

import com.jadennam.glucose.domain.model.AppSettings
import com.jadennam.glucose.domain.model.Exercise
import com.jadennam.glucose.domain.model.ExerciseType
import com.jadennam.glucose.domain.model.GlucoseRanges
import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.GlucoseUnit
import com.jadennam.glucose.domain.model.Meal
import com.jadennam.glucose.domain.model.MealType
import com.jadennam.glucose.domain.model.Medication
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.model.Profile
import com.jadennam.glucose.domain.model.ScheduleMode
import com.jadennam.glucose.domain.model.UnitSystem
import com.jadennam.glucose.domain.model.WeightEntry
import java.time.LocalDate

fun ProfileEntity.toDomain() = Profile(name, LocalDate.ofEpochDay(birthDateEpochDay), heightCm, UnitSystem.valueOf(unitSystem), GlucoseUnit.valueOf(glucoseUnit))
fun Profile.toEntity() = ProfileEntity(1, name, birthDate.toEpochDay(), heightCm, unitSystem.name, glucoseUnit.name)

fun SettingsEntity.toDomain() = AppSettings(
    scheduleMode = ScheduleMode.valueOf(scheduleMode),
    morningEnabled = morningEnabled, morningMinute = morningMinute,
    lunchEnabled = lunchEnabled, lunchMinute = lunchMinute,
    dinnerEnabled = dinnerEnabled, dinnerMinute = dinnerMinute,
    randomStartMinute = randomStartMinute, randomEndMinute = randomEndMinute,
    randomCount = randomCount, randomMinGapMinutes = randomMinGapMinutes,
    remindDelayMinutes = remindDelayMinutes,
    exerciseCheckEnabled = exerciseCheckEnabled, exerciseCheckMinute = exerciseCheckMinute,
    ranges = GlucoseRanges(rangeLow, rangeTargetHigh, rangeFastingLow, rangeFastingHigh, rangeVeryHigh),
    kakaoEnabled = kakaoEnabled, kakaoOnMeasure = kakaoOnMeasure,
    kakaoOnMissingMeal = kakaoOnMissingMeal, kakaoOnMissingExercise = kakaoOnMissingExercise,
    scheduleSalt = scheduleSalt,
)

fun AppSettings.toEntity() = SettingsEntity(
    1, scheduleMode.name, morningEnabled, morningMinute, lunchEnabled, lunchMinute, dinnerEnabled, dinnerMinute,
    randomStartMinute, randomEndMinute, randomCount, randomMinGapMinutes, remindDelayMinutes,
    exerciseCheckEnabled, exerciseCheckMinute,
    ranges.low, ranges.targetHigh, ranges.fastingLow, ranges.fastingHigh, ranges.veryHigh,
    kakaoEnabled, kakaoOnMeasure, kakaoOnMissingMeal, kakaoOnMissingExercise, scheduleSalt,
)

fun ReadingEntity.toDomain() = GlucoseReading(id, valueMgDl, measuredAt, zoneId, MeasureContext.valueOf(context), hoursAfterMeal, note)
fun GlucoseReading.toEntity() = ReadingEntity(id, valueMgDl, measuredAt, zoneId, context.name, hoursAfterMeal, note)

fun MealEntity.toDomain() = Meal(id, eatenAt, zoneId, types.split(',').filter { it.isNotBlank() }.map { MealType.valueOf(it) }, note)
fun Meal.toEntity() = MealEntity(id, eatenAt, zoneId, types.joinToString(",") { it.name }, note)

fun ExerciseEntity.toDomain() = Exercise(id, performedAt, zoneId, ExerciseType.valueOf(type), durationMinutes)
fun Exercise.toEntity() = ExerciseEntity(id, performedAt, zoneId, type.name, durationMinutes)

fun WeightEntity.toDomain() = WeightEntry(id, measuredAt, zoneId, weightKg)
fun WeightEntry.toEntity() = WeightEntity(id, measuredAt, zoneId, weightKg)

fun MedicationEntity.toDomain() = Medication(id, name, timesPerDay, unitsPerDose, LocalDate.ofEpochDay(startDateEpochDay), endDateEpochDay?.let(LocalDate::ofEpochDay), note)
fun Medication.toEntity() = MedicationEntity(id, name, timesPerDay, unitsPerDose, startDate.toEpochDay(), endDate?.toEpochDay(), note)
