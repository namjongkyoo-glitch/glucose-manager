package com.jadennam.glucose.domain.backup

import com.jadennam.glucose.domain.model.AppSettings
import com.jadennam.glucose.domain.model.Exercise
import com.jadennam.glucose.domain.model.GlucoseRanges
import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.Meal
import com.jadennam.glucose.domain.model.MealType
import com.jadennam.glucose.domain.model.Medication
import com.jadennam.glucose.domain.model.Profile
import com.jadennam.glucose.domain.model.ScheduleMode
import com.jadennam.glucose.domain.model.WeightEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/** Everything a backup restores. Ids are kept so a round trip is exact. */
data class BackupData(
    val profile: Profile?,
    val settings: AppSettings,
    val readings: List<GlucoseReading>,
    val meals: List<Meal>,
    val exercises: List<Exercise>,
    val weights: List<WeightEntry>,
    val medications: List<Medication> = emptyList(),
)

class BackupFormatException(message: String) : Exception(message)

object BackupCodec {
    const val CURRENT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(data: BackupData, exportedAt: Long, appVersionName: String): String =
        json.encodeToString(BackupFileV1.serializer(), BackupFileV1.from(data, exportedAt, appVersionName))

    fun decode(text: String): BackupData {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw BackupFormatException("백업 파일 형식이 올바르지 않습니다.")
        }
        val version = root["backupVersion"]?.jsonPrimitive?.int
            ?: throw BackupFormatException("backupVersion이 없는 파일입니다.")
        return when {
            version > CURRENT_VERSION -> throw BackupFormatException("더 새로운 버전의 앱에서 만든 백업입니다. 앱을 업데이트하세요.")
            version == 1 -> try {
                json.decodeFromJsonElement(BackupFileV1.serializer(), root).toData()
            } catch (e: BackupFormatException) {
                throw e
            } catch (e: Exception) {
                throw BackupFormatException("백업 파일을 읽을 수 없습니다.")
            }
            else -> throw BackupFormatException("지원하지 않는 백업 버전입니다: $version")
        }
    }
}

@Serializable
internal data class BackupFileV1(
    val backupVersion: Int = 1,
    val exportedAt: Long,
    val appVersionName: String,
    val profile: ProfileV1? = null,
    val settings: SettingsV1,
    val glucoseReadings: List<ReadingV1> = emptyList(),
    val meals: List<MealV1> = emptyList(),
    val exercises: List<ExerciseV1> = emptyList(),
    val weights: List<WeightV1> = emptyList(),
    val medications: List<MedicationV1> = emptyList(),
) {
    fun toData() = BackupData(
        profile = profile?.let {
            Profile(it.name, LocalDate.ofEpochDay(it.birthDateEpochDay), it.heightCm, enumOf(it.unitSystem), enumOf(it.glucoseUnit))
        },
        settings = settings.toDomain(),
        readings = glucoseReadings.map {
            GlucoseReading(it.id, it.valueMgDl, it.measuredAt, it.zoneId, enumOf(it.context), it.hoursAfterMeal, it.note)
        },
        meals = meals.map { m -> Meal(m.id, m.eatenAt, m.zoneId, m.types.map { enumOf<MealType>(it) }, m.note) },
        exercises = exercises.map { Exercise(it.id, it.performedAt, it.zoneId, enumOf(it.type), it.durationMinutes) },
        weights = weights.map { WeightEntry(it.id, it.measuredAt, it.zoneId, it.weightKg) },
        medications = medications.map {
            Medication(it.id, it.name, it.timesPerDay, it.unitsPerDose, LocalDate.ofEpochDay(it.startDateEpochDay), it.endDateEpochDay?.let(LocalDate::ofEpochDay), it.note)
        },
    )

    companion object {
        fun from(d: BackupData, exportedAt: Long, appVersionName: String) = BackupFileV1(
            exportedAt = exportedAt,
            appVersionName = appVersionName,
            profile = d.profile?.let {
                ProfileV1(it.name, it.birthDate.toEpochDay(), it.heightCm, it.unitSystem.name, it.glucoseUnit.name)
            },
            settings = SettingsV1.from(d.settings),
            glucoseReadings = d.readings.map {
                ReadingV1(it.id, it.valueMgDl, it.measuredAt, it.zoneId, it.context.name, it.hoursAfterMeal, it.note)
            },
            meals = d.meals.map { MealV1(it.id, it.eatenAt, it.zoneId, it.types.map(MealType::name), it.note) },
            exercises = d.exercises.map { ExerciseV1(it.id, it.performedAt, it.zoneId, it.type.name, it.durationMinutes) },
            weights = d.weights.map { WeightV1(it.id, it.measuredAt, it.zoneId, it.weightKg) },
            medications = d.medications.map {
                MedicationV1(it.id, it.name, it.timesPerDay, it.unitsPerDose, it.startDate.toEpochDay(), it.endDate?.toEpochDay(), it.note)
            },
        )
    }
}

private inline fun <reified E : Enum<E>> enumOf(name: String): E =
    enumValues<E>().firstOrNull { it.name == name }
        ?: throw BackupFormatException("알 수 없는 값: $name")

@Serializable
internal data class ProfileV1(
    val name: String,
    val birthDateEpochDay: Long,
    val heightCm: Double,
    val unitSystem: String,
    val glucoseUnit: String,
)

@Serializable
internal data class ReadingV1(
    val id: Long,
    val valueMgDl: Int,
    val measuredAt: Long,
    val zoneId: String,
    val context: String,
    val hoursAfterMeal: Int? = null,
    val note: String? = null,
)

@Serializable
internal data class MealV1(val id: Long, val eatenAt: Long, val zoneId: String, val types: List<String>, val note: String? = null)

@Serializable
internal data class ExerciseV1(val id: Long, val performedAt: Long, val zoneId: String, val type: String, val durationMinutes: Int)

@Serializable
internal data class WeightV1(val id: Long, val measuredAt: Long, val zoneId: String, val weightKg: Double)

/** Defaults let older/partial files still decode when settings fields are added later. */
@Serializable
internal data class SettingsV1(
    val scheduleMode: String = ScheduleMode.FIXED.name,
    val morningEnabled: Boolean = true,
    val morningMinute: Int = 420,
    val lunchEnabled: Boolean = false,
    val lunchMinute: Int = 750,
    val dinnerEnabled: Boolean = false,
    val dinnerMinute: Int = 1140,
    val randomStartMinute: Int = 480,
    val randomEndMinute: Int = 1260,
    val randomCount: Int = 2,
    val randomMinGapMinutes: Int = 120,
    val remindDelayMinutes: Int = 15,
    val exerciseCheckEnabled: Boolean = true,
    val exerciseCheckMinute: Int = 1230,
    @SerialName("rangeLow") val low: Int = 70,
    @SerialName("rangeTargetHigh") val targetHigh: Int = 180,
    @SerialName("rangeFastingLow") val fastingLow: Int = 80,
    @SerialName("rangeFastingHigh") val fastingHigh: Int = 130,
    @SerialName("rangeVeryHigh") val veryHigh: Int = 250,
    val kakaoEnabled: Boolean = false,
    val kakaoOnMeasure: Boolean = true,
    val kakaoOnMissingMeal: Boolean = true,
    val kakaoOnMissingExercise: Boolean = true,
    val scheduleSalt: Long = 0,
) {
    fun toDomain() = AppSettings(
        scheduleMode = enumOf(scheduleMode),
        morningEnabled = morningEnabled, morningMinute = morningMinute,
        lunchEnabled = lunchEnabled, lunchMinute = lunchMinute,
        dinnerEnabled = dinnerEnabled, dinnerMinute = dinnerMinute,
        randomStartMinute = randomStartMinute, randomEndMinute = randomEndMinute,
        randomCount = randomCount, randomMinGapMinutes = randomMinGapMinutes,
        remindDelayMinutes = remindDelayMinutes,
        exerciseCheckEnabled = exerciseCheckEnabled, exerciseCheckMinute = exerciseCheckMinute,
        ranges = GlucoseRanges(low, targetHigh, fastingLow, fastingHigh, veryHigh),
        kakaoEnabled = kakaoEnabled, kakaoOnMeasure = kakaoOnMeasure,
        kakaoOnMissingMeal = kakaoOnMissingMeal, kakaoOnMissingExercise = kakaoOnMissingExercise,
        scheduleSalt = scheduleSalt,
    )

    companion object {
        fun from(s: AppSettings) = SettingsV1(
            s.scheduleMode.name, s.morningEnabled, s.morningMinute, s.lunchEnabled, s.lunchMinute,
            s.dinnerEnabled, s.dinnerMinute, s.randomStartMinute, s.randomEndMinute, s.randomCount,
            s.randomMinGapMinutes, s.remindDelayMinutes, s.exerciseCheckEnabled, s.exerciseCheckMinute,
            s.ranges.low, s.ranges.targetHigh, s.ranges.fastingLow, s.ranges.fastingHigh, s.ranges.veryHigh,
            s.kakaoEnabled, s.kakaoOnMeasure, s.kakaoOnMissingMeal, s.kakaoOnMissingExercise, s.scheduleSalt,
        )
    }
}


@Serializable
internal data class MedicationV1(
    val id: Long,
    val name: String,
    val timesPerDay: Int,
    val unitsPerDose: Double,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long? = null,
    val note: String? = null,
)
