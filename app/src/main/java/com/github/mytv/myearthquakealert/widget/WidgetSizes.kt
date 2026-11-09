package com.github.mytv.myearthquakealert.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Layout of the status widget for the space the launcher gives it. */
enum class WidgetBucket {
    /** 1×1: status shape and the short phase text under it. */
    COMPACT,

    /** 2×1: shape and short phase text side by side. */
    SMALL,

    /** 4×1: status on the left, the latest earthquake on the right. */
    WIDE,

    /** 3×2: status header and the latest earthquake. */
    MEDIUM,

    /** 4×2 and taller: header, up to three recent earthquakes and 暂停 / 恢复 / 开启. */
    LARGE,
}

/**
 * Breakpoints of [StatusWidget]'s `SizeMode.Responsive` and the bucket each size falls in.
 * Glance renders one layout per breakpoint and the launcher picks the largest that fits, so
 * the content only ever sees these exact sizes.
 */
object WidgetSizes {
    val COMPACT = DpSize(48.dp, 48.dp)
    val SMALL = DpSize(110.dp, 48.dp)
    val WIDE = DpSize(250.dp, 48.dp)
    val MEDIUM = DpSize(180.dp, 108.dp)
    val LARGE = DpSize(250.dp, 200.dp)
    val TALL = DpSize(250.dp, 240.dp)

    val all: Set<DpSize> = setOf(COMPACT, SMALL, WIDE, MEDIUM, LARGE, TALL)

    /** Height of one earthquake row in the large layout. */
    const val ROW_DP = 38f

    /** Large layout chrome: padding, status header and the 最近预警 / action row. */
    const val LARGE_CHROME_DP = 114f

    fun bucket(widthDp: Float, heightDp: Float): WidgetBucket = when {
        widthDp >= LARGE.width.value && heightDp >= LARGE.height.value -> WidgetBucket.LARGE
        widthDp >= MEDIUM.width.value && heightDp >= MEDIUM.height.value -> WidgetBucket.MEDIUM
        widthDp >= WIDE.width.value -> WidgetBucket.WIDE
        widthDp >= SMALL.width.value -> WidgetBucket.SMALL
        else -> WidgetBucket.COMPACT
    }

    /** Earthquake rows that fit the large layout at [heightDp] (1–3). */
    fun largeRows(heightDp: Float): Int = ((heightDp - LARGE_CHROME_DP) / ROW_DP).toInt().coerceIn(1, 3)
}
