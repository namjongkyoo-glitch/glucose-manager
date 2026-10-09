package com.jadennam.glucose.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.jadennam.glucose.domain.health.HealthData
import com.jadennam.glucose.domain.health.HealthExercise
import com.jadennam.glucose.domain.health.HealthSleep
import com.jadennam.glucose.domain.health.HealthWeight
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

enum class HealthAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

/**
 * Read-only access to Samsung Health data synced into Health Connect.
 * Nothing is written to Health Connect or copied into the app database.
 */
class HealthConnectSource(private val context: Context) {

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        val CORE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
        )
        const val HISTORY_PERMISSION = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
    }

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
        else -> HealthAvailability.NOT_INSTALLED
    }

    private fun client(): HealthConnectClient? =
        if (availability() == HealthAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    /** Core read permissions plus history access when this Health Connect version supports it. */
    fun requestedPermissions(): Set<String> {
        val c = client() ?: return CORE_PERMISSIONS
        val historySupported = c.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        return if (historySupported) CORE_PERMISSIONS + HISTORY_PERMISSION else CORE_PERMISSIONS
    }

    suspend fun grantedPermissions(): Set<String> =
        runCatching { client()?.permissionController?.getGrantedPermissions() }.getOrNull() ?: emptySet()

    /** Returns null when Health Connect is unavailable or no read permission is granted. */
    suspend fun read(start: LocalDate, endExclusive: LocalDate, zone: ZoneId): HealthData? {
        val c = client() ?: return null
        val granted = grantedPermissions()
        if (granted.none { it in CORE_PERMISSIONS }) return null
        fun has(k: KClass<out Record>) = HealthPermission.getReadPermission(k) in granted

        val from = start.atStartOfDay(zone).toInstant()
        val to = endExclusive.atStartOfDay(zone).toInstant()
        return runCatching {
            val steps = if (has(StepsRecord::class)) {
                // Aggregation de-duplicates phone + watch sources.
                c.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), endExclusive.atStartOfDay()),
                        timeRangeSlicer = Period.ofDays(1),
                    ),
                ).associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
            } else emptyMap()

            // Sleep that started the evening before the first day still counts for that day.
            val sleeps = if (has(SleepSessionRecord::class)) {
                readAll(c, SleepSessionRecord::class, TimeRangeFilter.between(from.minusSeconds(18 * 3600), to))
                    .map { HealthSleep(it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }
            } else emptyList()

            val exercises = if (has(ExerciseSessionRecord::class)) {
                readAll(c, ExerciseSessionRecord::class, TimeRangeFilter.between(from, to))
                    .map { HealthExercise(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), exerciseLabel(it.exerciseType), it.title) }
            } else emptyList()

            // Look back 60 days so a period still shows the latest known weight.
            val weights = if (has(WeightRecord::class)) {
                readAll(c, WeightRecord::class, TimeRangeFilter.between(from.minusSeconds(60L * 86_400), to))
                    .map { HealthWeight(it.time.toEpochMilli(), it.weight.inKilograms) }
            } else emptyList()

            HealthData(steps, sleeps, exercises, weights)
        }.getOrNull()
    }

    private suspend fun <T : Record> readAll(c: HealthConnectClient, type: KClass<T>, filter: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val resp = c.readRecords(ReadRecordsRequest(type, filter, pageSize = 1000, pageToken = token))
            out += resp.records
            token = resp.pageToken
        } while (token != null)
        return out
    }

    private fun exerciseLabel(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "걷기"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "달리기"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "등산"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "자전거"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "수영"
        ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "골프"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "근력 운동"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "요가"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "필라테스"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "일립티컬"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING, ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "계단 오르기"
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "테니스"
        ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "배드민턴"
        else -> "운동"
    }
}
