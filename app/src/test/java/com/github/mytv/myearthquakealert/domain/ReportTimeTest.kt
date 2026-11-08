package com.github.mytv.myearthquakealert.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportTimeTest {

    @Test
    fun parseCst_knownInstant() {
        // 2022-06-01 17:00:08 CST = 2022-06-01T09:00:08Z
        assertEquals(1_654_074_008_000L, ReportTime.parseCst("2022-06-01 17:00:08"))
    }

    @Test
    fun parseCst_epochAndLeapDay() {
        assertEquals(0L, ReportTime.parseCst("1970-01-01 08:00:00"))
        // 2024-02-29 23:59:59 CST = 2024-02-29T15:59:59Z
        assertEquals(1_709_222_399_000L, ReportTime.parseCst("2024-02-29 23:59:59"))
    }

    @Test
    fun parseCst_invalid_returnsNull() {
        assertNull(ReportTime.parseCst(null))
        assertNull(ReportTime.parseCst(""))
        assertNull(ReportTime.parseCst("2022-06-01"))
        assertNull(ReportTime.parseCst("2022/06/01 17:00:08"))
        assertNull(ReportTime.parseCst("2022-13-01 17:00:08"))
        assertNull(ReportTime.parseCst("abcd-06-01 17:00:08"))
    }

    @Test
    fun parseCst_acceptsIsoSeparatorAndPadding() {
        assertEquals(1_654_074_008_000L, ReportTime.parseCst("2022-06-01T17:00:08"))
        assertEquals(1_654_074_008_000L, ReportTime.parseCst(" 2022-06-01 17:00:08 "))
    }

    @Test
    fun formatCst_roundTrips() {
        for (t in listOf(0L, 1_654_074_008_000L, 1_709_222_399_000L, 4_102_444_799_000L)) {
            assertEquals(t, ReportTime.parseCst(ReportTime.formatCst(t)))
        }
        assertEquals("2022-06-01 17:00:08", ReportTime.formatCst(1_654_074_008_000L))
    }

    @Test
    fun formatCst_dropsMillis() {
        assertEquals("2022-06-01 17:00:08", ReportTime.formatCst(1_654_074_008_999L))
    }
}
