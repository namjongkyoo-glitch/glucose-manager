package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.health.ExternalGlucose
import com.jadennam.glucose.domain.health.GlucoseImport
import com.jadennam.glucose.domain.health.MealRelation
import com.jadennam.glucose.domain.medication.RegimenChange
import com.jadennam.glucose.domain.medication.RegimenItem
import com.jadennam.glucose.domain.medication.RegimenTimeline
import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.model.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RegimenAndImportTest {
    private val d = LocalDate.of(2026, 3, 1)
    private val a = RegimenItem("더미약 A", 2, 1.0)
    private val b = RegimenItem("더미약 B", 1, 0.5, " 식후 ")

    private fun med(id: Long, name: String, start: LocalDate, end: LocalDate?) =
        Medication(id, name, 1, 1.0, start, end)

    @Test
    fun newRegimenEndsActiveMedsDayBefore() {
        val existing = listOf(
            med(1, "old1", d.minusMonths(2), null),
            med(2, "old2", d.minusMonths(2), d.plusDays(10)), // would overlap
            med(3, "older", d.minusMonths(6), d.minusMonths(3)), // already ended — untouched
        )
        val r = RegimenTimeline.apply(existing, listOf(a, b), d, null) as RegimenChange.Ok
        assertEquals(setOf(1L, 2L), r.ended.map { it.id }.toSet())
        assertTrue(r.ended.all { it.endDate == d.minusDays(1) })
        assertEquals(listOf("더미약 A", "더미약 B"), r.added.map { it.name })
        assertTrue(r.added.all { it.startDate == d && it.endDate == null && it.id == 0L })
        assertEquals("식후", r.added[1].note)
    }

    @Test
    fun pastRegimenEnteredLaterEndsBeforeNextOne() {
        val existing = listOf(med(1, "current", d, null))
        val r = RegimenTimeline.apply(existing, listOf(a), d.minusMonths(1), null) as RegimenChange.Ok
        assertEquals(emptyList<Medication>(), r.ended)
        assertEquals(d.minusDays(1), r.added.single().endDate)
    }

    @Test
    fun explicitEndKeptWhenEarlier() {
        val r = RegimenTimeline.apply(emptyList(), listOf(a), d, d.plusDays(5)) as RegimenChange.Ok
        assertEquals(d.plusDays(5), r.added.single().endDate)
    }

    @Test
    fun rejections() {
        assertTrue(RegimenTimeline.apply(emptyList(), emptyList(), d, null) is RegimenChange.Rejected)
        assertTrue(RegimenTimeline.apply(emptyList(), listOf(a), d, d.minusDays(1)) is RegimenChange.Rejected)
        assertTrue(RegimenTimeline.apply(listOf(med(1, "x", d, null)), listOf(a), d, null) is RegimenChange.Rejected)
    }

    @Test
    fun groupsNewestFirst() {
        val g = RegimenTimeline.groups(listOf(med(1, "a", d.minusDays(9), d.minusDays(1)), med(2, "b", d, null), med(3, "c", d, null)))
        assertEquals(listOf(d, d.minusDays(9)), g.map { it.first })
        assertEquals(2, g.first().second.size)
    }

    private val zone = ZoneId.of("America/Chicago")
    private fun t(h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun importDedupsAndMapsContext() {
        val existing = listOf(GlucoseReading(valueMgDl = 110, measuredAt = t(7), zoneId = zone.id, context = MeasureContext.FASTING))
        val incoming = listOf(
            ExternalGlucose(t(7) + 30_000, zone.id, 110.2, MealRelation.FASTING),   // duplicate of existing
            ExternalGlucose(t(13), zone.id, 165.0, MealRelation.AFTER_MEAL),
            ExternalGlucose(t(13), zone.id, 165.4, MealRelation.AFTER_MEAL),       // duplicate within batch
            ExternalGlucose(t(6), zone.id, 98.0, MealRelation.UNKNOWN),            // -> fasting
            ExternalGlucose(t(22), zone.id, 140.0, MealRelation.GENERAL),          // -> bedtime
            ExternalGlucose(t(15), zone.id, 130.0, MealRelation.UNKNOWN),          // -> before meal
            ExternalGlucose(t(16), zone.id, 5.0, MealRelation.UNKNOWN),            // implausible
        )
        val p = GlucoseImport.plan(existing, incoming)
        assertEquals(2, p.duplicates)
        assertEquals(1, p.outOfRange)
        assertEquals(
            listOf(98 to MeasureContext.FASTING, 165 to MeasureContext.AFTER_MEAL, 130 to MeasureContext.BEFORE_MEAL, 140 to MeasureContext.BEDTIME),
            p.toInsert.map { it.valueMgDl to it.context },
        )
        assertTrue(p.toInsert.all { it.note == GlucoseImport.SOURCE_NOTE })
    }

    @Test
    fun reimportIsIdempotent() {
        val incoming = listOf(ExternalGlucose(t(8), zone.id, 120.0, MealRelation.BEFORE_MEAL))
        val first = GlucoseImport.plan(emptyList(), incoming)
        val second = GlucoseImport.plan(first.toInsert, incoming)
        assertEquals(0, second.toInsert.size)
        assertEquals(1, second.duplicates)
    }
}
