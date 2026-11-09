package com.github.mytv.myearthquakealert.widget

import androidx.compose.ui.unit.DpSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizesTest {

    private fun bucketOf(size: DpSize) = WidgetSizes.bucket(size.width.value, size.height.value)

    @Test
    fun breakpoints_fallInTheirOwnBucket() {
        assertEquals(WidgetBucket.COMPACT, bucketOf(WidgetSizes.COMPACT))
        assertEquals(WidgetBucket.SMALL, bucketOf(WidgetSizes.SMALL))
        assertEquals(WidgetBucket.WIDE, bucketOf(WidgetSizes.WIDE))
        assertEquals(WidgetBucket.MEDIUM, bucketOf(WidgetSizes.MEDIUM))
        assertEquals(WidgetBucket.LARGE, bucketOf(WidgetSizes.LARGE))
        assertEquals(WidgetBucket.LARGE, bucketOf(WidgetSizes.TALL))
    }

    @Test
    fun everyBucketHasABreakpoint() {
        assertEquals(WidgetBucket.entries.toSet(), WidgetSizes.all.map { bucketOf(it) }.toSet())
    }

    @Test
    fun typicalLauncherCells() {
        // Portrait phone cells from the Android widget sizing table (4-column grid).
        assertEquals(WidgetBucket.COMPACT, WidgetSizes.bucket(57f, 102f)) // 1x1
        assertEquals(WidgetBucket.COMPACT, WidgetSizes.bucket(73f, 50f)) // 1x1 landscape
        assertEquals(WidgetBucket.SMALL, WidgetSizes.bucket(130f, 102f)) // 2x1
        assertEquals(WidgetBucket.SMALL, WidgetSizes.bucket(130f, 220f)) // 2x2 is still narrow
        assertEquals(WidgetBucket.MEDIUM, WidgetSizes.bucket(203f, 220f)) // 3x2
        assertEquals(WidgetBucket.WIDE, WidgetSizes.bucket(276f, 102f)) // 4x1
        assertEquals(WidgetBucket.LARGE, WidgetSizes.bucket(276f, 220f)) // 4x2
        assertEquals(WidgetBucket.LARGE, WidgetSizes.bucket(276f, 338f)) // 4x3
    }

    @Test
    fun justBelowABreakpoint_fallsBack() {
        assertEquals(WidgetBucket.COMPACT, WidgetSizes.bucket(109.9f, 200f))
        assertEquals(WidgetBucket.SMALL, WidgetSizes.bucket(179.9f, 200f))
        assertEquals(WidgetBucket.SMALL, WidgetSizes.bucket(249.9f, 107.9f))
        assertEquals(WidgetBucket.MEDIUM, WidgetSizes.bucket(249.9f, 300f))
        assertEquals(WidgetBucket.MEDIUM, WidgetSizes.bucket(300f, 199.9f))
    }

    @Test
    fun largeRows_growWithHeightUpToThree() {
        assertEquals(1, WidgetSizes.largeRows(100f))
        assertEquals(2, WidgetSizes.largeRows(WidgetSizes.LARGE.height.value))
        assertEquals(3, WidgetSizes.largeRows(WidgetSizes.TALL.height.value))
        assertEquals(3, WidgetSizes.largeRows(600f))
    }

    @Test
    fun largeRows_fitTheirBreakpoint() {
        for (height in listOf(WidgetSizes.LARGE.height.value, WidgetSizes.TALL.height.value)) {
            val rows = WidgetSizes.largeRows(height)
            assertTrue("height $height rows $rows", WidgetSizes.LARGE_CHROME_DP + rows * WidgetSizes.ROW_DP <= height)
        }
    }
}
