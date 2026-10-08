package com.jadennam.glucose.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(p: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun clear()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Upsert
    suspend fun upsert(s: SettingsEntity)

    @Query("DELETE FROM app_settings")
    suspend fun clear()
}

@Dao
interface RecordDao {
    // Glucose
    @Query("SELECT * FROM glucose_reading WHERE measured_at >= :from AND measured_at < :to ORDER BY measured_at")
    fun observeReadings(from: Long, to: Long): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM glucose_reading WHERE measured_at >= :from AND measured_at < :to ORDER BY measured_at")
    suspend fun readings(from: Long, to: Long): List<ReadingEntity>

    @Query("SELECT * FROM glucose_reading ORDER BY measured_at")
    fun observeAllReadings(): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM glucose_reading ORDER BY measured_at")
    suspend fun allReadings(): List<ReadingEntity>

    @Query("SELECT COUNT(*) FROM glucose_reading WHERE measured_at >= :from")
    suspend fun countReadingsSince(from: Long): Int

    @Insert
    suspend fun insertReading(r: ReadingEntity): Long

    @Insert
    suspend fun insertReadings(r: List<ReadingEntity>)

    @Query("DELETE FROM glucose_reading WHERE id = :id")
    suspend fun deleteReading(id: Long)

    // Meals
    @Query("SELECT * FROM meal WHERE eaten_at >= :from AND eaten_at < :to ORDER BY eaten_at")
    fun observeMeals(from: Long, to: Long): Flow<List<MealEntity>>

    @Query("SELECT * FROM meal WHERE eaten_at >= :from AND eaten_at < :to ORDER BY eaten_at")
    suspend fun meals(from: Long, to: Long): List<MealEntity>

    @Query("SELECT * FROM meal ORDER BY eaten_at")
    suspend fun allMeals(): List<MealEntity>

    @Insert
    suspend fun insertMeal(m: MealEntity): Long

    @Insert
    suspend fun insertMeals(m: List<MealEntity>)

    @Query("DELETE FROM meal WHERE id = :id")
    suspend fun deleteMeal(id: Long)

    // Exercise
    @Query("SELECT * FROM exercise WHERE performed_at >= :from AND performed_at < :to ORDER BY performed_at")
    fun observeExercises(from: Long, to: Long): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE performed_at >= :from AND performed_at < :to ORDER BY performed_at")
    suspend fun exercises(from: Long, to: Long): List<ExerciseEntity>

    @Query("SELECT * FROM exercise ORDER BY performed_at")
    suspend fun allExercises(): List<ExerciseEntity>

    @Insert
    suspend fun insertExercise(e: ExerciseEntity): Long

    @Insert
    suspend fun insertExercises(e: List<ExerciseEntity>)

    @Query("DELETE FROM exercise WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    // Weight
    @Query("SELECT * FROM weight_entry ORDER BY measured_at")
    fun observeAllWeights(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weight_entry ORDER BY measured_at")
    suspend fun allWeights(): List<WeightEntity>

    @Query("SELECT * FROM weight_entry WHERE measured_at < :before ORDER BY measured_at DESC LIMIT 1")
    suspend fun latestWeightBefore(before: Long): WeightEntity?

    @Insert
    suspend fun insertWeight(w: WeightEntity): Long

    @Insert
    suspend fun insertWeights(w: List<WeightEntity>)

    @Query("DELETE FROM weight_entry WHERE id = :id")
    suspend fun deleteWeight(id: Long)

    // Medication
    @Query("SELECT * FROM medication ORDER BY start_date DESC, id DESC")
    fun observeMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medication ORDER BY start_date, id")
    suspend fun allMedications(): List<MedicationEntity>

    @Upsert
    suspend fun upsertMedication(m: MedicationEntity): Long

    @Insert
    suspend fun insertMedications(m: List<MedicationEntity>)

    @Query("DELETE FROM medication WHERE id = :id")
    suspend fun deleteMedication(id: Long)

    @Query("DELETE FROM medication")
    suspend fun clearMedications()

    @Query("DELETE FROM glucose_reading")
    suspend fun clearReadings()

    @Query("DELETE FROM meal")
    suspend fun clearMeals()

    @Query("DELETE FROM exercise")
    suspend fun clearExercises()

    @Query("DELETE FROM weight_entry")
    suspend fun clearWeights()
}
