package com.jadennam.glucose.data

import androidx.room.withTransaction
import com.jadennam.glucose.domain.backup.BackupData
import com.jadennam.glucose.domain.model.AppSettings
import com.jadennam.glucose.domain.model.Exercise
import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.Meal
import com.jadennam.glucose.domain.model.Medication
import com.jadennam.glucose.domain.model.Profile
import com.jadennam.glucose.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

data class DayRecords(
    val readings: List<GlucoseReading>,
    val meals: List<Meal>,
    val exercises: List<Exercise>,
)

data class PeriodDetail(
    val readings: List<GlucoseReading>,
    val meals: List<Meal>,
    val exercises: List<Exercise>,
    val weight: WeightEntry?,
)

class Repository(private val db: AppDatabase, private val clock: Clock) {
    private val profileDao = db.profileDao()
    private val settingsDao = db.settingsDao()
    private val dao = db.recordDao()

    val profile: Flow<Profile?> = profileDao.observe().map { it?.toDomain() }
    val settings: Flow<AppSettings> = settingsDao.observe().map { it?.toDomain() ?: AppSettings() }
    val allReadings: Flow<List<GlucoseReading>> = dao.observeAllReadings().map { l -> l.map { it.toDomain() } }
    val allWeights: Flow<List<WeightEntry>> = dao.observeAllWeights().map { l -> l.map { it.toDomain() } }
    val medications: Flow<List<Medication>> = dao.observeMedications().map { l -> l.map { it.toDomain() } }

    fun zone(): ZoneId = clock.zone
    fun now(): Long = clock.millis()

    suspend fun getProfile(): Profile? = profileDao.get()?.toDomain()

    /** Creates default settings (with a random per-install schedule salt) on first access. */
    suspend fun getSettings(): AppSettings {
        settingsDao.get()?.let { return it.toDomain() }
        val created = AppSettings(scheduleSalt = Random.nextLong())
        settingsDao.upsert(created.toEntity())
        return created
    }

    suspend fun saveProfile(p: Profile) = profileDao.upsert(p.toEntity())
    suspend fun saveSettings(s: AppSettings) = settingsDao.upsert(s.toEntity())

    suspend fun addReading(r: GlucoseReading) = dao.insertReading(r.toEntity())
    suspend fun addMeal(m: Meal) = dao.insertMeal(m.toEntity())
    suspend fun addExercise(e: Exercise) = dao.insertExercise(e.toEntity())
    suspend fun addWeight(w: WeightEntry) = dao.insertWeight(w.toEntity())
    suspend fun deleteReading(id: Long) = dao.deleteReading(id)
    suspend fun deleteMeal(id: Long) = dao.deleteMeal(id)
    suspend fun deleteExercise(id: Long) = dao.deleteExercise(id)
    suspend fun deleteWeight(id: Long) = dao.deleteWeight(id)
    suspend fun saveMedication(m: Medication) = dao.upsertMedication(m.toEntity())
    suspend fun deleteMedication(id: Long) = dao.deleteMedication(id)
    suspend fun allMedications(): List<Medication> = dao.allMedications().map { it.toDomain() }

    private fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun observeDay(date: LocalDate, zone: ZoneId = zone()): Flow<DayRecords> {
        val (from, to) = dayBounds(date, zone)
        return combine(dao.observeReadings(from, to), dao.observeMeals(from, to), dao.observeExercises(from, to)) { r, m, e ->
            DayRecords(r.map { it.toDomain() }, m.map { it.toDomain() }, e.map { it.toDomain() })
        }
    }

    /** Records in [start, endExclusive) plus the latest weight known at the end of the period. */
    suspend fun periodDetail(start: LocalDate, endExclusive: LocalDate, zone: ZoneId = zone()): PeriodDetail {
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        return PeriodDetail(
            dao.readings(from, to).map { it.toDomain() },
            dao.meals(from, to).map { it.toDomain() },
            dao.exercises(from, to).map { it.toDomain() },
            dao.latestWeightBefore(to)?.toDomain(),
        )
    }

    suspend fun latestWeight(): WeightEntry? = dao.latestWeightBefore(Long.MAX_VALUE)?.toDomain()
    suspend fun countReadingsSince(t: Long): Int = dao.countReadingsSince(t)

    suspend fun hasMealOn(date: LocalDate): Boolean = dayBounds(date, zone()).let { (f, t) -> dao.meals(f, t).isNotEmpty() }
    suspend fun hasExerciseOn(date: LocalDate): Boolean = dayBounds(date, zone()).let { (f, t) -> dao.exercises(f, t).isNotEmpty() }

    suspend fun exportBackup(): BackupData = BackupData(
        profile = getProfile(),
        settings = getSettings(),
        readings = dao.allReadings().map { it.toDomain() },
        meals = dao.allMeals().map { it.toDomain() },
        exercises = dao.allExercises().map { it.toDomain() },
        weights = dao.allWeights().map { it.toDomain() },
        medications = dao.allMedications().map { it.toDomain() },
    )

    /** Replaces everything with the backup contents in a single transaction. */
    suspend fun restore(data: BackupData) = db.withTransaction {
        profileDao.clear(); settingsDao.clear()
        dao.clearReadings(); dao.clearMeals(); dao.clearExercises(); dao.clearWeights(); dao.clearMedications()
        data.profile?.let { profileDao.upsert(it.toEntity()) }
        settingsDao.upsert(data.settings.toEntity())
        dao.insertReadings(data.readings.map { it.toEntity() })
        dao.insertMeals(data.meals.map { it.toEntity() })
        dao.insertExercises(data.exercises.map { it.toEntity() })
        dao.insertWeights(data.weights.map { it.toEntity() })
        dao.insertMedications(data.medications.map { it.toEntity() })
    }
}
