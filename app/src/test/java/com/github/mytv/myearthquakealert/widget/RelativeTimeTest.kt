package com.github.mytv.myearthquakealert.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeTimeTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun of_picksTheUnitForTheAge() {
        assertEquals(RelativeTime.Age(RelativeTime.Unit.NOW, 0), RelativeTime.of(-5_000L))
        assertEquals(RelativeTime.Age(RelativeTime.Unit.NOW, 0), RelativeTime.of(59_999L))
        assertEquals(RelativeTime.Age(RelativeTime.Unit.MINUTES, 1), RelativeTime.of(minute))
        assertEquals(RelativeTime.Age(RelativeTime.Unit.MINUTES, 59), RelativeTime.of(hour - 1))
        assertEquals(RelativeTime.Age(RelativeTime.Unit.HOURS, 1), RelativeTime.of(hour))
        assertEquals(RelativeTime.Age(RelativeTime.Unit.HOURS, 23), RelativeTime.of(day - 1))
        assertEquals(RelativeTime.Age(RelativeTime.Unit.DAYS, 3), RelativeTime.of(3 * day + 5 * hour))
    }

    @Test
    fun untilChange_pointsAtTheNextLabel() {
        assertEquals(minute, RelativeTime.untilChange(0L))
        assertEquals(20_000L, RelativeTime.untilChange(100_000L))
        assertEquals(hour, RelativeTime.untilChange(hour))
        assertEquals(30 * minute, RelativeTime.untilChange(90 * minute))
        assertEquals(day, RelativeTime.untilChange(day))
        for (age in listOf(0L, 59_999L, 30 * minute, 59 * minute + 1, 5 * hour + 7, 2 * day + 3)) {
            val wait = RelativeTime.untilChange(age)
            assertTrue(wait > 0)
            assertEquals("age $age", RelativeTime.of(age), RelativeTime.of(age + wait - 1))
            assertNotEquals("age $age", RelativeTime.of(age), RelativeTime.of(age + wait))
        }
    }
}
