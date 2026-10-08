package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.model.Medication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedicationTest {
    private val d = LocalDate.of(2026, 3, 10)
    private val ended = Medication(name = "더미약", timesPerDay = 2, unitsPerDose = 1.0, startDate = d, endDate = d.plusDays(9))
    private val ongoing = ended.copy(endDate = null)

    @Test
    fun activeOnIsInclusiveOfStartAndEnd() {
        assertFalse(ended.isActiveOn(d.minusDays(1)))
        assertTrue(ended.isActiveOn(d))
        assertTrue(ended.isActiveOn(d.plusDays(9)))
        assertFalse(ended.isActiveOn(d.plusDays(10)))
        assertTrue(ongoing.isActiveOn(d.plusYears(5)))
    }

    @Test
    fun overlapsHalfOpenRange() {
        assertTrue(ended.overlaps(d.plusDays(9), d.plusDays(16)))
        assertFalse(ended.overlaps(d.plusDays(10), d.plusDays(17)))
        assertFalse(ended.overlaps(d.minusDays(7), d))          // range ends the day before start
        assertTrue(ended.overlaps(d.minusDays(7), d.plusDays(1)))
        assertTrue(ongoing.overlaps(d.plusYears(1), d.plusYears(1).plusDays(7)))
    }
}
