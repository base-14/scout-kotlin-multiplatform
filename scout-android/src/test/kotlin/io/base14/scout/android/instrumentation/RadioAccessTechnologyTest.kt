package io.base14.scout.android.instrumentation

import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import kotlin.test.Test
import kotlin.test.assertEquals

class RadioAccessTechnologyTest {
    @Test
    fun mapsRadioGenerationsToTheSemanticConventionValues() {
        assertEquals("lte", RadioAccessTechnology.subtypeOf(TelephonyManager.NETWORK_TYPE_LTE))
        assertEquals("nr", RadioAccessTechnology.subtypeOf(TelephonyManager.NETWORK_TYPE_NR))
        assertEquals("hspap", RadioAccessTechnology.subtypeOf(TelephonyManager.NETWORK_TYPE_HSPAP))
        assertEquals("edge", RadioAccessTechnology.subtypeOf(TelephonyManager.NETWORK_TYPE_EDGE))
    }

    @Test
    fun readsFiveGNonStandaloneFromTheOverrideRatherThanTheBaseNetworkType() {
        assertEquals(
            "nrnsa",
            RadioAccessTechnology.subtypeOf(
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
            ),
        )
        assertEquals(
            "nr",
            RadioAccessTechnology.subtypeOf(
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
            ),
        )
        assertEquals(
            "lte_ca",
            RadioAccessTechnology.subtypeOf(
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
            ),
        )
    }

    @Test
    fun keepsTheBaseNetworkTypeWhenNoOverrideApplies() {
        assertEquals(
            "lte",
            RadioAccessTechnology.subtypeOf(
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
            ),
        )
    }

    @Test
    fun reportsNothingForAnUnknownRadio() {
        assertEquals("", RadioAccessTechnology.subtypeOf(TelephonyManager.NETWORK_TYPE_UNKNOWN))
    }
}
