package com.github.mytv.myearthquakealert.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val AlertRed = Color(0xFFB3261E)
private val AlertRedContainer = Color(0xFFF9DEDC)

private val EeqLightColorScheme = lightColorScheme(
    error = AlertRed,
    errorContainer = AlertRedContainer,
)

private val EeqDarkColorScheme = darkColorScheme(
    error = AlertRed,
    errorContainer = AlertRedContainer,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val EeqShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyEarthQuakeAlertTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> EeqDarkColorScheme
        else -> EeqLightColorScheme
    }

    val colorScheme = baseColorScheme.copy(
        error = AlertRed,
        errorContainer = AlertRedContainer,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EeqTypography,
        shapes = EeqShapes,
        motionScheme = EeqMotionScheme,
        content = content,
    )
}
