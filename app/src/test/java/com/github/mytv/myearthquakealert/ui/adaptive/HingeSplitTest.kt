package com.github.mytv.myearthquakealert.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class HingeSplitTest {

    @Test
    fun occludingHingeBecomesTheGap() {
        // Dual-screen device: 84 px hinge in the middle of 2784 px.
        assertEquals(SplitSpan(1350, 1434), splitAtHinge(2784, 1350f, 1434f, minGap = 0, fallbackFraction = 0.5f))
    }

    @Test
    fun zeroWidthFoldGetsACentredGutter() {
        assertEquals(SplitSpan(1068, 1092), splitAtHinge(2160, 1080f, 1080f, minGap = 24, fallbackFraction = 0.5f))
    }

    @Test
    fun hingeOutsideTheWindowFallsBack() {
        assertEquals(SplitSpan(988, 1012), splitAtHinge(2000, 2400f, 2400f, minGap = 24, fallbackFraction = 0.5f))
        assertEquals(SplitSpan(988, 1012), splitAtHinge(2000, -40f, -40f, minGap = 24, fallbackFraction = 0.5f))
    }

    @Test
    fun withoutHingeTheFallbackFractionApplies() {
        assertEquals(SplitSpan(600, 600), splitAtHinge(1000, null, null, minGap = 0, fallbackFraction = 0.6f))
        assertEquals(SplitSpan(390, 414), splitAtHinge(1000, null, null, minGap = 24, fallbackFraction = 0.4f))
    }

    @Test
    fun hingeAtTheEdgeFallsBack() {
        // A fold flush with the window edge would leave one pane empty.
        assertEquals(SplitSpan(500, 500), splitAtHinge(1000, 0f, 0f, minGap = 0, fallbackFraction = 0.5f))
    }
}
