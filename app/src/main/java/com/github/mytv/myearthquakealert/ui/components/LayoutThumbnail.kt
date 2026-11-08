package com.github.mytv.myearthquakealert.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.data.model.LayoutOverride

/**
 * Miniature of a layout for the 界面布局 choosers (design thumb()): drawn on a 60 × 34
 * grid and scaled to 68 × 40 dp.
 */
@Composable
fun LayoutThumbnail(layout: LayoutOverride, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.size(68.dp, 40.dp)) {
        val k = size.width / 60f
        fun r(x: Float, y: Float, w: Float, h: Float, rad: Float, color: Color, alpha: Float = 1f) =
            drawRoundRect(color, Offset(x * k, y * k), Size(w * k, h * k), CornerRadius(rad * k), alpha = alpha)
        when (layout) {
            LayoutOverride.AUTO -> {
                r(2f, 4f, 34f, 24f, 3f, surface)
                r(40f, 2f, 18f, 30f, 3f, surface)
                line(14f, 16f, 24f, 16f, primary, k)
                line(50f, 12f, 50f, 22f, primary, k)
            }
            LayoutOverride.PHONE -> {
                r(20f, 1f, 20f, 32f, 4f, surface)
                r(23f, 5f, 14f, 6f, 2f, primary)
                r(23f, 13f, 14f, 10f, 2f, outline)
                r(22f, 28f, 16f, 3f, 1.5f, outline)
            }
            LayoutOverride.TABLET -> {
                r(1f, 3f, 58f, 28f, 4f, surface)
                r(4f, 6f, 5f, 22f, 2f, outline)
                r(11f, 6f, 30f, 22f, 2f, primary, 0.7f)
                r(43f, 6f, 13f, 22f, 2f, outline)
            }
            LayoutOverride.TV -> {
                r(1f, 2f, 58f, 26f, 3f, Color(0xFF1B2129))
                r(5f, 6f, 4f, 18f, 2f, Color(0xFF4A5560))
                r(12f, 6f, 28f, 18f, 2f, primary, 0.8f)
                r(42f, 6f, 14f, 5f, 2f, Color(0xFFE4E7EB))
                r(42f, 13f, 14f, 5f, 2f, Color(0xFF4A5560))
                r(24f, 29f, 12f, 3f, 1.5f, outline)
            }
            LayoutOverride.KIOSK -> {
                r(1f, 2f, 58f, 30f, 3f, primary, 0.75f)
                r(5f, 6f, 16f, 8f, 2f, Color(0xFF0E1217))
                r(5f, 24f, 50f, 5f, 2f, Color(0xFF0E1217), 0.8f)
            }
        }
    }
}

private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, k: Float) =
    drawLine(color, Offset(x1 * k, y1 * k), Offset(x2 * k, y2 * k), strokeWidth = 2.4f * k, cap = StrokeCap.Round)
