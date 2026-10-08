package com.jadennam.glucose.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    @ColumnInfo(name = "birth_date") val birthDateEpochDay: Long,
    @ColumnInfo(name = "height_cm") val heightCm: Double,
    @ColumnInfo(name = "unit_system") val unitSystem: String,
    @ColumnInfo(name = "glucose_unit") val glucoseUnit: String,
)

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "schedule_mode") val scheduleMode: String,
    @ColumnInfo(name = "morning_enabled") val morningEnabled: Boolean,
    @ColumnInfo(name = "morning_minute") val morningMinute: Int,
    @ColumnInfo(name = "lunch_enabled") val lunchEnabled: Boolean,
    @ColumnInfo(name = "lunch_minute") val lunchMinute: Int,
    @ColumnInfo(name = "dinner_enabled") val dinnerEnabled: Boolean,
    @ColumnInfo(name = "dinner_minute") val dinnerMinute: Int,
    @ColumnInfo(name = "random_start_minute") val randomStartMinute: Int,
    @ColumnInfo(name = "random_end_minute") val randomEndMinute: Int,
    @ColumnInfo(name = "random_count") val randomCount: Int,
    @ColumnInfo(name = "random_min_gap_minutes") val randomMinGapMinutes: Int,
    @ColumnInfo(name = "remind_delay_minutes") val remindDelayMinutes: Int,
    @ColumnInfo(name = "exercise_check_enabled") val exerciseCheckEnabled: Boolean,
    @ColumnInfo(name = "exercise_check_minute") val exerciseCheckMinute: Int,
    @ColumnInfo(name = "range_low") val rangeLow: Int,
    @ColumnInfo(name = "range_target_high") val rangeTargetHigh: Int,
    @ColumnInfo(name = "range_fasting_low") val rangeFastingLow: Int,
    @ColumnInfo(name = "range_fasting_high") val rangeFastingHigh: Int,
    @ColumnInfo(name = "range_very_high") val rangeVeryHigh: Int,
    @ColumnInfo(name = "kakao_enabled") val kakaoEnabled: Boolean,
    @ColumnInfo(name = "kakao_on_measure") val kakaoOnMeasure: Boolean,
    @ColumnInfo(name = "kakao_on_missing_meal") val kakaoOnMissingMeal: Boolean,
    @ColumnInfo(name = "kakao_on_missing_exercise") val kakaoOnMissingExercise: Boolean,
    @ColumnInfo(name = "schedule_salt") val scheduleSalt: Long,
)

@Entity(tableName = "glucose_reading", indices = [Index("measured_at")])
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "value_mgdl") val valueMgDl: Int,
    @ColumnInfo(name = "measured_at") val measuredAt: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    val context: String,
    @ColumnInfo(name = "hours_after_meal") val hoursAfterMeal: Int?,
    val note: String?,
)

@Entity(tableName = "meal", indices = [Index("eaten_at")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "eaten_at") val eatenAt: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    /** Comma-separated MealType names. */
    val types: String,
    val note: String?,
)

@Entity(tableName = "exercise", indices = [Index("performed_at")])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "performed_at") val performedAt: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    val type: String,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
)

@Entity(tableName = "weight_entry", indices = [Index("measured_at")])
data class WeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "measured_at") val measuredAt: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
)

@Entity(tableName = "medication")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "times_per_day") val timesPerDay: Int,
    @ColumnInfo(name = "units_per_dose") val unitsPerDose: Double,
    @ColumnInfo(name = "start_date") val startDateEpochDay: Long,
    @ColumnInfo(name = "end_date") val endDateEpochDay: Long?,
    val note: String?,
)
