# Alert Overlay Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the earthquake alert overlay to match Japanese Meteorological Agency style with horizontal layout (left map, right info panel).

**Architecture:** Replace the current rounded, semi-transparent overlay with a rectangular design split into two sections: 240dp map area on the left with dark background, and 360dp info area on the right with red title bar + blue content area. Remove animations and simplify the visual hierarchy.

**Tech Stack:** Jetpack Compose, Kotlin, existing AlertData model, osmdroid for maps

---

## File Structure

### Files to Modify
1. **app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Color.kt**
   - Add new color constants for alert red, blue, and map background

2. **app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt**
   - Complete rewrite of AlertOverlay composable
   - Remove AlertHeader, AlertCountdown, AlertDescription
   - Add RedTitleBar, BlueInfoArea, CountdownSection, EpicenterSection, WarningSection
   - Update preview functions

### Files to Keep Unchanged
- `AlertMap.kt` - Internal implementation stays the same, only container styling changes
- `AlertOverlayService.kt` - Service layer unchanged
- `AlertData.kt` - Data model unchanged
- String resources - Reuse existing strings

---

## Task 1: Add Color Constants

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Color.kt:18`

- [ ] **Step 1: Add new color constants**

Add these constants after the existing color definitions:

```kotlin
val AlertRed = Color(0xFFE60012)
val AlertBlue = Color(0xFF1565C0)
val AlertMapBackground = Color(0xFF212121)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Color.kt
git commit -m "feat: add color constants for alert overlay redesign

Add AlertRed, AlertBlue, and AlertMapBackground colors for
Japanese meteorological agency-style alert overlay.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 2: Create RedTitleBar Component

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt:88-112`

- [ ] **Step 1: Add RedTitleBar composable**

Replace the existing `AlertHeader` function (lines 88-112) with:

```kotlin
@Composable
private fun RedTitleBar(
    sourceName: String,
    isSimulation: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(AlertRed)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.alert_title) + "（${sourceName}）",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        if (isSimulation) {
            Text(
                text = stringResource(R.string.simulation_label),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}
```

- [ ] **Step 2: Add import for AlertRed**

Add at the top of the file with other color imports:

```kotlin
import com.github.mytv.myearthquakealert.ui.theme.AlertRed
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt
git commit -m "feat: add RedTitleBar component for alert overlay

Replace AlertHeader with RedTitleBar using Japanese alert red
background and white text.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 3: Create CountdownSection Component

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt:114-163`

- [ ] **Step 1: Replace AlertCountdown with CountdownSection**

Replace the existing `AlertCountdown` function (lines 114-163) with:

```kotlin
@Composable
private fun CountdownSection(
    remainingSeconds: Double,
    localCsis: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = remainingSeconds.toInt().toString(),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontSize = 72.sp,
            )
            Text(
                text = "秒",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "CSIS ${localCsis.toInt()}",
            style = MaterialTheme.typography.titleLarge,
            color = csisColor(localCsis),
            fontWeight = FontWeight.Bold,
        )
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt
git commit -m "feat: add CountdownSection component

Replace AlertCountdown with simplified CountdownSection without
animations. Display large countdown number (72sp) with CSIS label.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 4: Create EpicenterSection Component

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt` (after CountdownSection)

- [ ] **Step 1: Add EpicenterSection composable**

Add this new function after `CountdownSection`:

```kotlin
@Composable
private fun EpicenterSection(
    hypocenter: String,
    magnitude: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append("震源：")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(hypocenter)
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        Text(
            text = buildAnnotatedString {
                append("震级：")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFFEB3B))) {
                    append("M${magnitude}")
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt
git commit -m "feat: add EpicenterSection component

Display epicenter location and magnitude with yellow highlight
for magnitude value.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 5: Create WarningSection Component

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt` (after EpicenterSection)

- [ ] **Step 1: Add WarningSection composable**

Add this new function after `EpicenterSection`:

```kotlin
@Composable
private fun WarningSection(
    remainingSeconds: Double,
    localCsis: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append("地震波将在 ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFFEB3B))) {
                    append("${remainingSeconds.toInt()}")
                }
                append(" 秒后到达")
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        Text(
            text = buildAnnotatedString {
                append("预计烈度：")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = csisColor(localCsis))) {
                    append("CSIS ${localCsis.toInt()}")
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt
git commit -m "feat: add WarningSection component

Display warning information with remaining time and expected
intensity using color-coded CSIS values.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 6: Create BlueInfoArea Component

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt:165-215`

- [ ] **Step 1: Replace AlertDescription with BlueInfoArea**

Replace the existing `AlertDescription` function (lines 165-215) with:

```kotlin
@Composable
private fun BlueInfoArea(
    alertData: AlertData,
    remainingSeconds: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AlertBlue)
            .padding(EeqSpacing.md),
        verticalArrangement = Arrangement.spacedBy(EeqSpacing.md),
    ) {
        CountdownSection(
            remainingSeconds = remainingSeconds,
            localCsis = alertData.localCsis,
        )
        EpicenterSection(
            hypocenter = alertData.event.hypocenter,
            magnitude = alertData.event.magnitude,
        )
        WarningSection(
            remainingSeconds = remainingSeconds,
            localCsis = alertData.localCsis,
        )
    }
}
```

- [ ] **Step 2: Add import for AlertBlue**

Add at the top of the file with other color imports:

```kotlin
import com.github.mytv.myearthquakealert.ui.theme.AlertBlue
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt
git commit -m "feat: add BlueInfoArea component

Compose countdown, epicenter, and warning sections into blue
background info area.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 7: Rewrite AlertOverlay Main Component

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt:30-86`

- [ ] **Step 1: Replace AlertOverlay implementation**

Replace the existing `AlertOverlay` function (lines 30-86) with:

```kotlin
@Composable
fun AlertOverlay(
    alertData: AlertData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(alertData) {
        while (true) {
            delay(1000)
            elapsedSeconds += 1f
        }
    }

    val remainingSeconds = max(0.0, alertData.sWaveSeconds - elapsedSeconds)

    backHandler(onBack = onDismiss)

    Row(modifier = modifier.width(600.dp)) {
        Box(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(AlertMapBackground)
        ) {
            AlertMap(
                alertData = alertData,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(modifier = Modifier.width(360.dp)) {
            RedTitleBar(
                sourceName = alertData.event.source,
                isSimulation = alertData.isSimulation,
            )
            BlueInfoArea(
                alertData = alertData,
                remainingSeconds = remainingSeconds,
            )
        }
    }
}
```

- [ ] **Step 2: Add import for AlertMapBackground**

Add at the top of the file with other color imports:

```kotlin
import com.github.mytv.myearthquakealert.ui.theme.AlertMapBackground
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt
git commit -m "feat: rewrite AlertOverlay with horizontal layout

Replace rounded overlay with rectangular horizontal layout:
- Left: 240dp map with dark background
- Right: 360dp info area with red title bar + blue content

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 8: Update Preview Functions

**Files:**
- Modify: `app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt:240-268`

- [ ] **Step 1: Update AlertOverlayPreview**

Replace the existing preview function (lines 240-248) with:

```kotlin
@Preview(name = "Alert Overlay", device = "spec:width=600dp,height=400dp")
@Composable
private fun AlertOverlayPreview() {
    MyEarthQuakeAlertTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AlertOverlay(alertData = sampleAlertData, onDismiss = {})
        }
    }
}
```

- [ ] **Step 2: Remove old preview functions**

Delete the `AlertCountdownPreview` and `AlertDescriptionPreview` functions (lines 250-268) as they reference removed components.

- [ ] **Step 3: Add new component previews**

Add these preview functions after `AlertOverlayPreview`:

```kotlin
@Preview(name = "Red Title Bar")
@Composable
private fun RedTitleBarPreview() {
    MyEarthQuakeAlertTheme {
        RedTitleBar(sourceName = "中国地震台网", isSimulation = true)
    }
}

@Preview(name = "Blue Info Area")
@Composable
private fun BlueInfoAreaPreview() {
    MyEarthQuakeAlertTheme {
        BlueInfoArea(alertData = sampleAlertData, remainingSeconds = 25.0)
    }
}

@Preview(name = "Countdown Section")
@Composable
private fun CountdownSectionPreview() {
    MyEarthQuakeAlertTheme {
        Box(
            modifier = Modifier
                .background(AlertBlue)
                .padding(EeqSpacing.md)
        ) {
            CountdownSection(remainingSeconds = 25.0, localCsis = 4.0)
        }
    }
}
```

- [ ] **Step 4: Verify previews render**

Open the file in Android Studio and check that all previews render correctly in the preview pane.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt
git commit -m "feat: update preview functions for new components

Remove old component previews and add new ones for RedTitleBar,
BlueInfoArea, and CountdownSection.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 9: Visual Verification

**Files:**
- Test: Preview functions in Android Studio

- [ ] **Step 1: Check AlertOverlay preview**

Open `AlertOverlay.kt` in Android Studio and verify the main preview shows:
- Left side: 240dp dark map area
- Right side: Red title bar at top, blue info area below
- Countdown shows large white numbers (72sp)
- All text is white on colored backgrounds

- [ ] **Step 2: Check color contrast**

Verify:
- Red title bar (0xFFE60012) with white text is readable
- Blue info area (0xFF1565C0) with white text is readable
- CSIS colors are visible against blue background

- [ ] **Step 3: Check layout proportions**

Verify:
- Total width is 600dp
- Map area is 240dp (40%)
- Info area is 360dp (60%)
- Title bar height is 56dp

- [ ] **Step 4: Test with different CSIS values**

Modify `sampleAlertData` to test different CSIS values (0, 4, 8, 12) and verify colors display correctly.

- [ ] **Step 5: Document verification**

Create a note documenting that visual verification passed:

```bash
echo "Visual verification completed on $(date)" > docs/superpowers/verification-2026-05-29.txt
git add docs/superpowers/verification-2026-05-29.txt
git commit -m "docs: visual verification of alert overlay redesign

Confirmed layout proportions, color contrast, and CSIS color
display across different intensity values.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 10: Build and Runtime Test

**Files:**
- Test: Full app build and runtime behavior

- [ ] **Step 1: Clean build**

Run: `./gradlew clean :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Install on device/emulator**

Run: `./gradlew :app:installDebug`
Expected: Installation successful

- [ ] **Step 3: Test simulation mode**

1. Launch the app
2. Navigate to simulation test
3. Trigger a test alert
4. Verify:
   - Alert overlay appears with new design
   - Countdown updates every second
   - Map shows epicenter and wave circles
   - "模拟测试" label appears in title bar
   - Back button dismisses the alert

- [ ] **Step 4: Test countdown to zero**

Let the countdown run to 0 and verify:
- Countdown displays 0 (not negative numbers)
- Alert auto-dismisses after the configured time

- [ ] **Step 5: Test long text handling**

Modify test data to use a very long epicenter name and verify text doesn't overflow the layout.

- [ ] **Step 6: Document test results**

```bash
echo "Runtime testing completed successfully on $(date)" >> docs/superpowers/verification-2026-05-29.txt
git add docs/superpowers/verification-2026-05-29.txt
git commit -m "docs: runtime testing verification

Confirmed countdown behavior, simulation mode, auto-dismiss,
and long text handling.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Success Criteria Checklist

After completing all tasks, verify these criteria:

- [ ] Layout matches Japanese meteorological agency style (left map, right info)
- [ ] Red title bar is prominent and attention-grabbing
- [ ] Blue info area provides clear readability
- [ ] Countdown numbers are large and clear (72sp)
- [ ] All existing functionality works (countdown, map, back button)
- [ ] No animation stuttering or performance issues
- [ ] Display is complete within 600dp width
- [ ] Color contrast meets WCAG AA standards (verified in Task 9)
- [ ] Simulation label displays correctly
- [ ] Countdown handles zero correctly (no negative values)

---

## Rollback Plan

If issues are discovered after implementation:

1. **Revert all commits:**
   ```bash
   git log --oneline --grep="alert overlay redesign"
   git revert <commit-hash-range>
   ```

2. **Restore from backup:**
   The original `AlertOverlay.kt` is preserved in git history at commit `184336f`.

3. **Incremental rollback:**
   If only specific components have issues, revert individual commits rather than the entire redesign.
