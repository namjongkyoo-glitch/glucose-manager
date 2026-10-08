package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.backup.BackupCodec
import com.jadennam.glucose.domain.backup.BackupData
import com.jadennam.glucose.domain.backup.BackupFormatException
import com.jadennam.glucose.domain.model.AppSettings
import com.jadennam.glucose.domain.model.GlucoseRanges
import com.jadennam.glucose.domain.model.GlucoseUnit
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.model.Medication
import com.jadennam.glucose.domain.model.Profile
import com.jadennam.glucose.domain.model.ScheduleMode
import com.jadennam.glucose.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class BackupCodecTest {

    private val sample = BackupData(
        profile = Profile("테스트 사용자", LocalDate.of(1980, 1, 2), 172.5, UnitSystem.US, GlucoseUnit.MMOL_L),
        settings = AppSettings(scheduleMode = ScheduleMode.RANDOM, randomCount = 3, ranges = GlucoseRanges(65, 170, 75, 125, 240), kakaoEnabled = true, scheduleSalt = -123456789L),
        readings = DummyData.readings(30),
        meals = DummyData.meals(10),
        exercises = DummyData.exercises(10),
        weights = DummyData.weights(30),
        medications = listOf(
            Medication(1, "더미약 A 500mg", 2, 1.0, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31), "식후"),
            Medication(2, "더미약 B", 1, 0.5, LocalDate.of(2025, 4, 1), null),
        ),
    )

    @Test
    fun roundTripIsExact() {
        val text = BackupCodec.encode(sample, exportedAt = 1_760_000_000_000, appVersionName = "1.0.0")
        assertEquals(sample, BackupCodec.decode(text))
    }

    @Test
    fun emptyBackupRoundTrips() {
        val empty = BackupData(null, AppSettings(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty, 0, "1.0.0")))
    }

    @Test
    fun decodesFrozenV1FileWithMissingOptionalFieldsAndUnknownKeys() {
        val v1 = """
            {
              "backupVersion": 1,
              "exportedAt": 1760000000000,
              "appVersionName": "1.0.0",
              "someFutureField": {"x": 1},
              "profile": {"name": "더미", "birthDateEpochDay": 3652, "heightCm": 170.0, "unitSystem": "METRIC", "glucoseUnit": "MG_DL"},
              "settings": {"scheduleMode": "FIXED", "rangeLow": 72},
              "glucoseReadings": [
                {"id": 1, "valueMgDl": 105, "measuredAt": 1759996800000, "zoneId": "Asia/Seoul", "context": "FASTING"}
              ]
            }
        """.trimIndent()
        val d = BackupCodec.decode(v1)
        assertEquals(LocalDate.of(1980, 1, 1), d.profile!!.birthDate)
        assertEquals(72, d.settings.ranges.low)
        assertEquals(180, d.settings.ranges.targetHigh)
        assertEquals(MeasureContext.FASTING, d.readings.single().context)
        assertNull(d.readings.single().hoursAfterMeal)
        assertEquals(emptyList<Any>(), d.meals)
    }

    @Test
    fun rejectsNewerVersionGarbageAndMissingVersion() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"backupVersion": 99}""") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"exportedAt": 1}""") }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("""{"backupVersion":1,"exportedAt":0,"appVersionName":"x","settings":{"scheduleMode":"WHAT"}}""")
        }
    }
}
