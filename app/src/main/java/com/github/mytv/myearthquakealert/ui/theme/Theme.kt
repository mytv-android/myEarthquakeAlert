package com.github.mytv.myearthquakealert.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.data.model.ThemeMode
import kotlinx.coroutines.flow.map

// Hand-tuned slate-blue palette. Deliberately not dynamic: the app's identity
// (calm slate + alert red) should not depend on the device wallpaper.
private val EeqLightColorScheme = lightColorScheme(
    primary = Color(0xFF2A6394),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2E4F7),
    onPrimaryContainer = Color(0xFF0A2E4C),
    secondary = Color(0xFF51606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E3F1),
    onSecondaryContainer = Color(0xFF0E1D2B),
    tertiary = Color(0xFF8F5A36),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6DFCB),
    onTertiaryContainer = Color(0xFF331E0B),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FB),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDEE3E9),
    onSurfaceVariant = Color(0xFF41474D),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F8),
    surfaceContainer = Color(0xFFEBEEF2),
    surfaceContainerHigh = Color(0xFFE5E9EE),
    surfaceContainerHighest = Color(0xFFDFE3E9),
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFEFF1F5),
    inversePrimary = Color(0xFF9BCBF9),
)

private val EeqDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9BCBF9),
    onPrimary = Color(0xFF003354),
    primaryContainer = Color(0xFF174A73),
    onPrimaryContainer = Color(0xFFD2E4F7),
    secondary = Color(0xFFB9C8D8),
    onSecondary = Color(0xFF233240),
    secondaryContainer = Color(0xFF394857),
    onSecondaryContainer = Color(0xFFD5E3F1),
    tertiary = Color(0xFFE9C29B),
    onTertiary = Color(0xFF4B2A11),
    tertiaryContainer = Color(0xFF68401F),
    onTertiaryContainer = Color(0xFFF6DFCB),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE1E3E7),
    surface = Color(0xFF0F1419),
    onSurface = Color(0xFFE1E3E7),
    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41474D),
    surfaceContainerLowest = Color(0xFF0A0F13),
    surfaceContainerLow = Color(0xFF171C22),
    surfaceContainer = Color(0xFF1B2027),
    surfaceContainerHigh = Color(0xFF252B33),
    surfaceContainerHighest = Color(0xFF30363E),
    inverseSurface = Color(0xFFE1E3E7),
    inverseOnSurface = Color(0xFF2D3135),
    inversePrimary = Color(0xFF2A6394),
)

/**
 * Shape scale (design §9 形状): ordinary containers stay rounded rectangles; the M3E shape
 * library (MaterialShapes) is reserved for status and emphasis. Cards and the status card
 * use extraLarge (28 dp), segmented-list ends 20 dp, chips and inner items 8 dp.
 */
private val EeqShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * True when the app should render dark, following 设置 › 显示 › 主题 ([ThemeMode]).
 * Outside the app process (previews) it falls back to the system setting.
 */
@Composable
fun rememberAppDarkTheme(): Boolean {
    val system = isSystemInDarkTheme()
    val app = LocalContext.current.applicationContext as? MyEarthQuakeAlertApp ?: return system
    val mode by remember(app) { app.settingsRepository.settings.map { it.themeMode } }
        .collectAsState(initial = ThemeMode.SYSTEM)
    return isDarkFor(mode, system)
}

fun isDarkFor(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * The app theme: fixed slate-blue schemes (never wallpaper colours), MotionScheme.expressive()
 * and [MaterialTheme.status] colours. [tv] switches to the 10-foot type scale.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyEarthQuakeAlertTheme(
    darkTheme: Boolean = rememberAppDarkTheme(),
    dynamicColor: Boolean = false,
    tv: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> EeqDarkColorScheme
        else -> EeqLightColorScheme
    }

    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = if (tv) EeqTvTypography else EeqTypography,
            shapes = EeqShapes,
            motionScheme = EeqMotionScheme,
            content = content,
        )
    }
}
