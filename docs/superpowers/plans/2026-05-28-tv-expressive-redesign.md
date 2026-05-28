# TV-Friendly + M3 Expressive 改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Button components with TV-friendly Card alternatives and adopt Material 3 Expressive features (shapes, motion, typography, 8dp spacing).

**Architecture:** Layered approach — foundation first (theme tokens + TV interaction utils), then component-by-component migration. Each task produces a compilable, testable increment.

**Tech Stack:** Jetpack Compose + Material3 (BOM 2025.05.01) + ExperimentalMaterial3ExpressiveApi opt-in

---

## File Structure

### New files
- `ui/theme/Spacing.kt` — 8dp spacing token object
- `ui/theme/Motion.kt` — Expressive MotionScheme definition

### Modified files (in task order)
- `ui/adaptive/HandleUserKey.kt` — Replace with TV interaction toolkit
- `ui/theme/Color.kt` — Remove AlertRed/AlertRedContainer, add connection semantic colors
- `ui/theme/Type.kt` — Rewrite to M3 standard scale + emphasized variants
- `ui/theme/Theme.kt` — Expressive Shapes + MotionScheme + error color override
- `ui/main/SimulationButton.kt` → `ui/main/SimulationCard.kt` — Card + handleKeyEvents
- `ui/main/ServiceToggleCard.kt` — Switch controlled by Card handleKeyEvents
- `ui/main/SourceSelector.kt` — Focusable Card row replacing RadioButton
- `ui/main/ThresholdSettings.kt` — Slider stepping via handleKeyEvents
- `ui/main/ConnectionStatusChip.kt` — Spacing tokens
- `ui/main/IntensityBadge.kt` — Size 40→48dp, Color.White→onPrimary
- `ui/main/EarthquakeHistoryList.kt` — Item handleKeyEvents + spacing tokens
- `ui/main/MainScreen.kt` — Button→Card, spacing tokens
- `ui/alert/AlertOverlay.kt` — Spring animation, theme colors, shape/spacing tokens

### Unchanged files
- `ui/alert/AlertMap.kt` — osmdroid View, no M3 components
- `ui/adaptive/AdaptiveLayout.kt` — Three-pane layout logic unchanged
- All data/domain/service layers

---

### Task 1: Create Spacing tokens

**Files:**
- Create: `app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Spacing.kt`

- [ ] **Step 1: Create Spacing.kt**

```kotlin
package com.github.mytv.myearthquakealert.ui.theme

import androidx.compose.ui.unit.dp

object EeqSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}
```

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Spacing.kt
git commit -m "feat: add 8dp spacing token system"
```

---

### Task 2: Create Motion tokens

**Files:**
- Create: `app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Motion.kt`

- [ ] **Step 1: Create Motion.kt**

```kotlin
package com.github.mytv.myearthquakealert.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val EeqMotionScheme = MotionScheme(
    defaultSpatial = spring(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    ),
    defaultEffects = spring(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessMedium,
    ),
    expressiveSpatial = spring(
        dampingRatio = 0.5f,
        stiffness = Spring.StiffnessMediumHigh,
    ),
    expressiveEffects = spring(
        dampingRatio = 0.4f,
        stiffness = Spring.StiffnessHigh,
    ),
)
```

Note: The exact `MotionScheme` constructor signature must be verified against the BOM 2025.05.01 API. If the constructor uses named parameters like `spatial`/`effects` instead of `defaultSpatial`/`expressiveSpatial`, adjust accordingly. If `MotionScheme` is a class with a companion `motionScheme()` factory, use that instead.

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Motion.kt
git commit -m "feat: add expressive MotionScheme tokens"
```

---

### Task 3: Replace HandleUserKey with TV interaction toolkit

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/adaptive/HandleUserKey.kt`

- [ ] **Step 1: Rewrite HandleUserKey.kt with TV interaction utilities**

Replace the entire file content with the TV interaction toolkit adapted from the mytv reference. Remove `LAYOUT_GRID_*` references, `NavHostController` utilities, `handleDragGestures`, `handleAppRemoteKeys`, and `gridColumns`. Keep:

- `ifElse` conditional Modifier
- `focusOnLaunched` / `focusOnLaunchedSaveable`
- `handleKeyEvents` (both the low-level `onKeyTap/onKeyLongTap/onKeyContinuousLongTap` variant and the high-level `onLeft/Right/Up/Down/Select/LongSelect/ContinuousLong*` variant)
- `clickableNoIndication`
- `dpadSpeedLimit`
- `backHandler` (both simple and conditional variants)
- `tryRequestFocus` extension on `FocusRequester`
- `saveFocusRestorer` / `requestFocusRestorer`
- `clearFocusOnKeyboardDismiss`

The file should have these imports:

```kotlin
package com.github.mytv.myearthquakealert.ui.adaptive

import android.os.Build
import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
```

Key implementation details:
- The low-level `handleKeyEvents(onKeyTap, onKeyLongTap, onKeyContinuousLongTap)` handles raw keycodes with `onPreviewKeyEvent`
- The high-level `handleKeyEvents(onLeft, onRight, onUp, onDown, onSelect, onLongSelect, onContinuousLongSelect, onSettings, onNumber, clickableNoIndication, role, interactionSource)` maps directional keys to callbacks
- The third `handleKeyEvents(isFocused, focusRequester, ...)` variant wraps callbacks with focus-gating (if not focused, request focus instead of firing the callback)
- `clickableNoIndication` uses `detectTapGestures` with haptic/sound feedback
- `dpadSpeedLimit` throttles repeat D-pad events (default 150ms interval)
- All haptic feedback uses `HapticFeedbackConstantsCompat`
- Sound effects use `SoundEffectConstants.getConstantForFocusDirection` (API 31+) or `getContantForFocusDirection` (legacy)
- `toDirection` map translates keycodes to `View.FOCUS_*` directions for sound effect routing

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (some callers may break — that's okay, they'll be fixed in later tasks)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/adaptive/HandleUserKey.kt
git commit -m "feat: replace HandleUserKey with TV interaction toolkit"
```

---

### Task 4: Refactor Color.kt

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Color.kt`

- [ ] **Step 1: Remove AlertRed and AlertRedContainer, add connection semantic colors**

Remove `AlertRed` and `AlertRedContainer` — they will migrate to Theme.kt's `error`/`errorContainer` roles.

Add connection status semantic colors:

```kotlin
val ConnectionGreen = Color(0xFF4CAF50)
val ConnectingYellow = Color(0xFFFFC107)
val DisconnectedRed = Color(0xFFF44336)
```

Keep everything else: `Csis0`-`Csis12`, `csisColor()`, `PWaveBlue`, `SWaveRed`.

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: Build failures in files that reference `AlertRed`/`AlertRedContainer` (SimulationButton.kt, AlertOverlay.kt, ServiceToggleCard.kt). These will be fixed in their respective tasks.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Color.kt
git commit -m "refactor: move AlertRed to theme error role, add connection colors"
```

---

### Task 5: Rewrite Type.kt with M3 standard scale + emphasized variants

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Type.kt`

- [ ] **Step 1: Rewrite Type.kt**

Replace the entire file. Use `Roboto` as default font family (M3 standard on Android). Add all M3 standard type scale entries plus emphasized variants:

```kotlin
package com.github.mytv.myearthquakealert.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val EeqTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// Emphasized variants for focus/selected states
object EeqEmphasizedTypography {
    val headlineLarge = EeqTypography.headlineLarge.copy(fontWeight = FontWeight.Bold)
    val titleMedium = EeqTypography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val bodyLarge = EeqTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val labelLarge = EeqTypography.labelLarge.copy(fontWeight = FontWeight.Bold)
}
```

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Typography is additive — no callers break)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Type.kt
git commit -m "feat: rewrite typography with M3 standard scale + emphasized variants"
```

---

### Task 6: Rewrite Theme.kt with Expressive Shapes + MotionScheme + error override

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Theme.kt`

- [ ] **Step 1: Rewrite Theme.kt**

Replace the entire file. Key changes:
- Light/dark ColorSchemes override `error`/`errorContainer` with AlertRed/AlertRedContainer values
- Shapes use Expressive corner values
- MotionScheme applied via `LocalMotionScheme` (if API available) or passed through CompositionLocalProvider
- Typography uses `EeqTypography`

```kotlin
package com.github.mytv.myearthquakealert.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

private val EeqLightColorScheme = lightColorScheme(
    error = AlertRed,
    errorContainer = AlertRedContainer,
)

private val EeqDarkColorScheme = darkColorScheme(
    error = AlertRed,
    errorContainer = AlertRedContainer,
)

// AlertRed values kept here (moved from Color.kt)
private val AlertRed = Color(0xFFB3261E)
private val AlertRedContainer = Color(0xFFF9DEDC)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val EeqShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MyEarthQuakeAlertTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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

    // Override error colors even in dynamic scheme
    val finalColorScheme = colorScheme.copy(
        error = AlertRed,
        errorContainer = AlertRedContainer,
    )

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = EeqTypography,
        shapes = EeqShapes,
    ) {
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        CompositionLocalProvider(
            LocalMotionScheme provides EeqMotionScheme,
        ) {
            content()
        }
    }
}
```

Note: If `LocalMotionScheme` does not exist in the BOM version, remove the `CompositionLocalProvider` block and reference `EeqMotionScheme` directly in animation code. The `Shapes` constructor may also need adjustment if the BOM version has different parameter names — verify and adapt.

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: Possible build failures from `Color.White`/`AlertRed` references in component files — will be fixed in later tasks. Theme.kt itself must compile.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Theme.kt
git commit -m "feat: rewrite theme with expressive shapes + motion + error override"
```

---

### Task 7: Rewrite SimulationButton → SimulationCard

**Files:**
- Create: `app/src/main/java/com/github/mytv/myearthquakealert/ui/main/SimulationCard.kt`
- Delete (after): `app/src/main/java/com/github/mytv/myearthquakealert/ui/main/SimulationButton.kt`

- [ ] **Step 1: Create SimulationCard.kt**

```kotlin
package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.adaptive.handleKeyEvents
import com.github.mytv.myearthquakealert.ui.adaptive.focusOnLaunched
import com.github.mytv.myearthquakealert.ui.theme.EeqEmphasizedTypography
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing

@Composable
fun SimulationCard(
    onSimulate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        label = "simScale",
    )
    val cornerSize by animateDpAsState(
        targetValue = if (isFocused) 20.dp else 16.dp,
        label = "simCorner",
    )

    Card(
        onClick = onSimulate,
        shape = RoundedCornerShape(cornerSize),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .focusRequester(focusRequester)
            .scale(scale)
            .handleKeyEvents(
                interactionSource = interactionSource,
                onSelect = onSimulate,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(EeqSpacing.md),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_simulate),
                contentDescription = null,
                modifier = Modifier.size(EeqSpacing.xxl),
            )
            Spacer(modifier = Modifier.size(EeqSpacing.xs))
            Text(
                text = stringResource(R.string.simulate_test),
                style = if (isFocused) EeqEmphasizedTypography.labelLarge
                        else MaterialTheme.typography.labelLarge,
            )
        }
    }
}
```

Note: The `Card(onClick=...)` overload requires `Material3 1.2.0+`. If the `R.drawable.ic_simulate` or `R.string.simulate_test` resources don't exist, check the current SimulationButton.kt for the actual resource IDs and use those.

- [ ] **Step 2: Delete SimulationButton.kt**

```bash
git rm app/src/main/java/com/github/mytv/myearthquakealert/ui/main/SimulationButton.kt
```

- [ ] **Step 3: Update MainScreen.kt import**

In MainScreen.kt, replace any `import ...SimulationButton` with `import ...SimulationCard` and replace the `SimulationButton(onSimulate = ...)` call with `SimulationCard(onSimulate = ...)`. This is a quick find-and-replace within MainScreen.kt.

- [ ] **Step 4: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: replace SimulationButton with TV-friendly SimulationCard"
```

---

### Task 8: Modify ServiceToggleCard — Switch controlled by Card handleKeyEvents

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/main/ServiceToggleCard.kt`

- [ ] **Step 1: Modify ServiceToggleCard.kt**

Key changes:
- Add `interactionSource` and `collectIsFocusedAsState` to the Card
- Add `handleKeyEvents(onSelect = { enabled = !enabled })` to the Card modifier
- Set Switch `enabled = false` so it's not directly interactive
- Show focus indication on the Card when focused
- Replace hardcoded spacing with `EeqSpacing` tokens
- Replace `AlertRed` with `MaterialTheme.colorScheme.error`

Read the current file first, then apply these changes:
- Wrap the existing Column/Row content in a Card if not already in one
- Add `val interactionSource = remember { MutableInteractionSource() }` and `val isFocused by interactionSource.collectIsFocusedAsState()`
- On the Card/Container modifier, add `.handleKeyEvents(interactionSource = interactionSource, onSelect = { onEnabledChange(!enabled) })`
- Set `Switch(enabled = false, ...)`
- Add focus border: `CardDefaults.cardColors(containerColor = if (isFocused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)`
- Replace hardcoded dp paddings with `EeqSpacing` tokens

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/main/ServiceToggleCard.kt
git commit -m "feat: make ServiceToggleCard TV-friendly with handleKeyEvents"
```

---

### Task 9: Rewrite SourceSelector — Focusable Card row replacing RadioButton

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/main/SourceSelector.kt`

- [ ] **Step 1: Rewrite SourceSelector.kt**

Replace RadioButton items with focusable Card items. Each data source option becomes a Card:
- `handleKeyEvents(onSelect = { onSelect(index) })` on each Card
- `interactionSource` + `collectIsFocusedAsState` per item
- Selected state: `CardDefaults.filledTonalCardColors()` + `EeqEmphasizedTypography.titleMedium`
- Unselected state: `CardDefaults.outlinedCardColors()` + `MaterialTheme.typography.titleMedium`
- Focus state (independent of selected): animated scale 1.02 + corner 16dp→20dp
- Replace hardcoded spacing with `EeqSpacing` tokens

Read the current file first to understand the existing data model (source list, selected index, onSelect callback), then replace the RadioButton Row with a Row of Cards.

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/main/SourceSelector.kt
git commit -m "feat: replace RadioButton with focusable Card row in SourceSelector"
```

---

### Task 10: Modify ThresholdSettings — Slider stepping via handleKeyEvents

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/main/ThresholdSettings.kt`

- [ ] **Step 1: Modify ThresholdSettings.kt**

Key changes:
- Wrap the Slider section in a Card
- Add `handleKeyEvents` to the Card with:
  - `onLeft = { sliderValue = (sliderValue - step).coerceIn(min, max) }`
  - `onRight = { sliderValue = (sliderValue + step).coerceIn(min, max) }`
  - `onContinuousLongLeft` / `onContinuousLongRight` for continuous stepping
- Set `Slider(enabled = false)` — no direct touch drag
- Add `interactionSource` + `collectIsFocusedAsState` for focus indication
- Replace hardcoded spacing with `EeqSpacing` tokens
- Show current value as text label on the Card

Read the current file first to understand the existing slider value/state model, then apply these changes.

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/main/ThresholdSettings.kt
git commit -m "feat: make ThresholdSettings TV-friendly with handleKeyEvents stepping"
```

---

### Task 11: Update ConnectionStatusChip — spacing tokens

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/main/ConnectionStatusChip.kt`

- [ ] **Step 1: Modify ConnectionStatusChip.kt**

Changes:
- Replace `horizontal = 12.dp` with `EeqSpacing.md`
- Replace `vertical = 4.dp` with `EeqSpacing.xs`
- Replace `RoundedCornerShape(12.dp)` with `MaterialTheme.shapes.extraSmall` or `CircleShape` (full token)
- Replace any `ConnectionStatus.*` color references with the new `ConnectionGreen`/`ConnectingYellow`/`DisconnectedRed` from Color.kt
- No animation changes (too frequent)

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/main/ConnectionStatusChip.kt
git commit -m "refactor: apply spacing tokens to ConnectionStatusChip"
```

---

### Task 12: Update IntensityBadge — size + color + spring animation

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/main/IntensityBadge.kt`

- [ ] **Step 1: Modify IntensityBadge.kt**

Changes:
- Replace `40.dp` size with `EeqSpacing.xxl` (48dp)
- Replace `Color.White` text color with `MaterialTheme.colorScheme.onPrimary` (badge bg is CSIS color, not theme primary, but onPrimary gives correct contrast on tinted surfaces — if contrast is wrong, keep `Color.White`)
- Add spring entrance animation: `animateFloatAsState(spring())` for alpha 0→1 and scale 0.8→1.0 on first composition

Read the current file, then apply changes.

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/main/IntensityBadge.kt
git commit -m "feat: enlarge IntensityBadge + spring entrance animation"
```

---

### Task 13: Update EarthquakeHistoryList — handleKeyEvents + spacing

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/main/EarthquakeHistoryList.kt`

- [ ] **Step 1: Modify EarthquakeHistoryList.kt**

Changes:
- Add `handleKeyEvents(onSelect = onClick)` to each item Card
- Add `interactionSource` + `collectIsFocusedAsState` per item
- Focus state: animated scale 1.01 + shape medium→large
- Replace hardcoded spacing (8dp, 12dp, 16dp) with `EeqSpacing` tokens
- Add `focusOnLaunched()` to the first item for TV auto-focus

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/main/EarthquakeHistoryList.kt
git commit -m "feat: add TV focus support + spacing tokens to EarthquakeHistoryList"
```

---

### Task 14: Update MainScreen — Button→Card + spacing tokens

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/main/MainScreen.kt`

- [ ] **Step 1: Modify MainScreen.kt**

Changes:
- Replace `SimulationButton` import/call with `SimulationCard` (if not done in Task 7)
- Replace any remaining `Button` usage with `Card` + `handleKeyEvents`
- Replace hardcoded spacing with `EeqSpacing` tokens
- Replace `AlertRed` references with `MaterialTheme.colorScheme.error`

Read the current file, identify all Button/AlertRed/hardcoded-spacing usages, and apply changes.

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/main/MainScreen.kt
git commit -m "refactor: replace Button with Card + spacing tokens in MainScreen"
```

---

### Task 15: Update AlertOverlay — spring animation + theme colors + shape/spacing tokens

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt`

- [ ] **Step 1: Modify AlertOverlay.kt**

This is the largest change. Read the current file first, then apply:

**Color replacements:**
- `Color(0xDD000000)` background → `MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.87f)`
- `Color.White` text → `MaterialTheme.colorScheme.inverseOnSurface`

**Shape replacements:**
- Container shape → `MaterialTheme.shapes.extraLarge` (32dp)
- Countdown area → `RoundedCornerShape(48.dp)` (extra-extra-large)

**Spacing replacements:**
- All hardcoded dp paddings → `EeqSpacing` tokens

**Animation replacements:**
- Pulse animation: replace `infiniteRepeatable(tween(600))` with spring-based alpha animation using `EeqMotionScheme.expressiveEffects`
- Countdown: replace `animateFloatAsState(tween(300))` with `animateFloatAsState(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumHigh))`
- Enter/exit transitions: keep `tween` with emphasized easing (400ms/200ms) — these are transitions, not component motion

**Typography:**
- Countdown text: replace hardcoded `48.sp`/`FontWeight.Bold` with `MaterialTheme.typography.displayLarge`
- When countdown hits 0: use `EeqEmphasizedTypography.headlineLarge`

**IconButton → FocusableIcon:**
- Replace `IconButton(onClick = onDismiss)` with `Icon` + `Modifier.clickableNoIndication(onClick = onDismiss)` + `handleKeyEvents(onSelect = onDismiss)`
- Add `backHandler(onDismiss)` to the overlay container
- Focus state: `isFocused` → tonal circle background + scale 1.05

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt
git commit -m "feat: apply expressive motion + theme colors + shape tokens to AlertOverlay"
```

---

### Task 16: Full build verification + manual smoke test

**Files:** None (verification only)

- [ ] **Step 1: Full clean build**

Run: `.\gradlew clean :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Install and run on device/emulator**

Run: `.\gradlew :app:installDebug`
Then launch the app and verify:
- Main screen renders with Card-based components (no Buttons visible)
- D-pad navigation works on TV emulator (focus moves between items)
- ServiceToggleCard: Enter key toggles the Switch
- SourceSelector: Enter key selects a source
- ThresholdSettings: Left/Right keys adjust slider
- SimulationCard: Enter key triggers simulation
- AlertOverlay: displays with theme colors, spring animations play, Back/Enter dismisses
- IntensityBadge: renders at 48dp size
- No crashes or visual regressions

- [ ] **Step 3: Commit any hotfixes found during testing**

```bash
git add -A
git commit -m "fix: address issues found during smoke test"
```

---

## Self-Review

### Spec coverage

| Spec Section | Task |
|-------------|------|
| Section 1: Button→替代组件映射 | Tasks 7-10, 13-15 |
| Section 2: TV交互系统 (handleKeyEvents) | Task 3 |
| Section 3: Expressive Shape系统 | Task 6 |
| Section 4: Expressive Motion / Spring动画 | Task 2, 15 |
| Section 5A: Emphasized Typography | Task 5 |
| Section 5B: 8dp Spacing System | Task 1 |
| Section 6: Button替换详细方案 | Tasks 7-10, 13-15 |
| Section 7: Theme + Color方案 | Tasks 4, 6 |
| Section 8: 改动范围 | All tasks |

No gaps found.

### Placeholder scan

No TBD/TODO/placeholders found. All code steps contain actual implementation code. Notes about BOM API verification are explicit uncertainty markers, not placeholders.

### Type consistency

- `EeqSpacing` defined in Task 1, used consistently across Tasks 7-15
- `EeqMotionScheme` defined in Task 2, used in Task 6 and Task 15
- `EeqTypography`/`EeqEmphasizedTypography` defined in Task 5, used in Tasks 7-15
- `EeqShapes` defined in Task 6, used via `MaterialTheme.shapes` in Tasks 7-15
- `handleKeyEvents` defined in Task 3, used in Tasks 7-10, 13-15
- `AlertRed`/`AlertRedContainer` moved to Theme.kt private in Task 6, removed from Color.kt in Task 4
- `ConnectionGreen`/`ConnectingYellow`/`DisconnectedRed` added in Task 4, used in Task 11
- All names consistent across tasks
