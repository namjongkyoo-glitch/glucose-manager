package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.medication.RegimenEdit
import com.jadennam.glucose.domain.medication.RegimenItem
import com.jadennam.glucose.domain.medication.RegimenTimeline
import com.jadennam.glucose.domain.model.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RegimenEditTest {
    private val d = LocalDate.of(2026, 3, 1)
    private val group = listOf(
        Medication(10, "더미약 A", 2, 1.0, d, null),
        Medication(11, "더미약 B", 1, 1.0, d, null),
    )

    @Test
    fun updateAddAndRemoveDrugsInOnePrescription() {
        val r = RegimenTimeline.edit(
            group,
            listOf(10L to RegimenItem("더미약 A", 3, 1.5, "식후"), 0L to RegimenItem(" 더미약 C ", 1, 0.5)),
            d.plusDays(30),
        ) as RegimenEdit.Ok
        assertEquals(listOf(Medication(10, "더미약 A", 3, 1.5, d, d.plusDays(30), "식후")), r.updated)
        assertEquals(listOf(Medication(0, "더미약 C", 1, 0.5, d, d.plusDays(30), null)), r.added)
        assertEquals(listOf(11L), r.deletedIds)
    }

    @Test
    fun unknownIdIsTreatedAsNewAndStartIsKept() {
        val r = RegimenTimeline.edit(group, listOf(99L to RegimenItem("X", 1, 1.0)), null) as RegimenEdit.Ok
        assertEquals(emptyList<Medication>(), r.updated)
        assertEquals(d, r.added.single().startDate)
        assertEquals(listOf(10L, 11L), r.deletedIds)
    }

    @Test
    fun rejections() {
        assertTrue(RegimenTimeline.edit(emptyList(), listOf(0L to RegimenItem("X", 1, 1.0)), null) is RegimenEdit.Rejected)
        assertTrue(RegimenTimeline.edit(group, emptyList(), null) is RegimenEdit.Rejected)
        assertTrue(RegimenTimeline.edit(group, listOf(10L to RegimenItem("X", 1, 1.0)), d.minusDays(1)) is RegimenEdit.Rejected)
    }
}
