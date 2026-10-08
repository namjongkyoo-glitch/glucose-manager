package com.jadennam.glucose.ui

import com.jadennam.glucose.domain.model.ExerciseType
import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.GlucoseUnit
import com.jadennam.glucose.domain.model.MealType
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.model.UnitSystem
import com.jadennam.glucose.domain.range.GlucoseLevel
import com.jadennam.glucose.domain.units.UnitConverter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Labels {
    fun context(c: MeasureContext) = when (c) {
        MeasureContext.FASTING -> "공복"
        MeasureContext.BEFORE_MEAL -> "식전"
        MeasureContext.AFTER_MEAL -> "식후"
        MeasureContext.BEDTIME -> "취침 전"
    }

    fun contextOf(r: GlucoseReading) =
        if (r.context == MeasureContext.AFTER_MEAL && r.hoursAfterMeal != null) "식후 ${r.hoursAfterMeal}시간" else context(r.context)

    fun meal(t: MealType) = when (t) {
        MealType.RICE -> "밥"
        MealType.NOODLE -> "면"
        MealType.BREAD -> "빵"
        MealType.MEAT -> "고기"
        MealType.VEGETABLE -> "채소"
        MealType.FRUIT -> "과일"
        MealType.SNACK -> "간식"
        MealType.DRINK -> "음료"
    }

    fun exercise(t: ExerciseType) = when (t) {
        ExerciseType.JOGGING -> "조깅"
        ExerciseType.WALKING -> "산책"
        ExerciseType.SWIMMING -> "수영"
        ExerciseType.GOLF -> "골프"
        ExerciseType.GYM -> "헬스"
        ExerciseType.OTHER -> "기타"
    }

    fun level(l: GlucoseLevel) = when (l) {
        GlucoseLevel.LOW -> "낮음"
        GlucoseLevel.IN_TARGET -> "목표 범위"
        GlucoseLevel.HIGH -> "높음"
        GlucoseLevel.VERY_HIGH -> "매우 높음"
    }

    fun unitSystem(u: UnitSystem) = if (u == UnitSystem.METRIC) "한국 (cm·kg)" else "미국 (ft·in·lb)"
    fun glucoseUnit(u: GlucoseUnit) = UnitConverter.unitLabel(u)

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFmt = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val shortDateFmt = DateTimeFormatter.ofPattern("M/d")

    fun time(epochMs: Long, zoneId: String): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneOf(zoneId)).format(timeFmt)

    fun minute(m: Int) = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
    fun date(d: LocalDate): String = d.format(dateFmt)
    fun shortDate(d: LocalDate): String = d.format(shortDateFmt)
    fun dateTime(epochMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).let { "${date(it.toLocalDate())} ${it.format(timeFmt)}" }

    fun weight(kg: Double, system: UnitSystem): String = when (system) {
        UnitSystem.METRIC -> String.format(Locale.US, "%.1f kg", kg)
        UnitSystem.US -> String.format(Locale.US, "%.1f lb", UnitConverter.kgToLb(kg))
    }

    fun height(cm: Double, system: UnitSystem): String = when (system) {
        UnitSystem.METRIC -> String.format(Locale.US, "%.1f cm", cm)
        UnitSystem.US -> UnitConverter.cmToFeetInches(cm).let { (ft, inch) -> String.format(Locale.US, "%d ft %.1f in", ft, inch) }
    }

    private fun zoneOf(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.systemDefault())
}
