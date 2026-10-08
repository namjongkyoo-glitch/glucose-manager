package com.jadennam.glucose.domain

import com.jadennam.glucose.domain.model.GlucoseUnit
import com.jadennam.glucose.domain.units.UnitConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConverterTest {

    @Test
    fun mgDlMmolRoundTripIsExactForStoredIntegers() {
        for (mg in 20..600) {
            val shown = UnitConverter.formatGlucose(mg, GlucoseUnit.MMOL_L).toDouble()
            // Display rounds to 0.1 mmol/L (= 1.8 mg/dL), so re-entry is within 1 mg/dL.
            val back = UnitConverter.mmolToMgDl(shown)
            assert(kotlin.math.abs(back - mg) <= 1) { "mg=$mg back=$back" }
            assertEquals(mg, UnitConverter.mmolToMgDl(UnitConverter.mgDlToMmol(mg)))
        }
    }

    @Test
    fun knownGlucoseValues() {
        assertEquals("5.5", UnitConverter.formatGlucose(99, GlucoseUnit.MMOL_L))
        assertEquals("10.0", UnitConverter.formatGlucose(180, GlucoseUnit.MMOL_L))
        assertEquals(126, UnitConverter.parseGlucose("7.0", GlucoseUnit.MMOL_L))
        assertEquals(126, UnitConverter.parseGlucose("7,0", GlucoseUnit.MMOL_L))
        assertEquals(110, UnitConverter.parseGlucose(" 110 ", GlucoseUnit.MG_DL))
        assertNull(UnitConverter.parseGlucose("abc", GlucoseUnit.MG_DL))
        assertNull(UnitConverter.parseGlucose("5", GlucoseUnit.MG_DL))
        assertNull(UnitConverter.parseGlucose("1000", GlucoseUnit.MG_DL))
    }

    @Test
    fun heightRoundTrip() {
        var cm = 120.0
        while (cm <= 220.0) {
            val (ft, inch) = UnitConverter.cmToFeetInches(cm)
            assert(inch in 0.0..11.95) { "inch=$inch" }
            val back = UnitConverter.feetInchesToCm(ft, inch)
            assertEquals(cm, back, 0.13) // 0.05 in rounding = 0.127 cm
            cm += 0.5
        }
        assertEquals(5 to 9.0, UnitConverter.cmToFeetInches(175.26))
    }

    @Test
    fun weightRoundTrip() {
        var kg = 30.0
        while (kg <= 200.0) {
            assertEquals(kg, UnitConverter.lbToKg(UnitConverter.kgToLb(kg)), 1e-9)
            kg += 0.3
        }
        assertEquals(220.462, UnitConverter.kgToLb(100.0), 0.001)
    }
}
