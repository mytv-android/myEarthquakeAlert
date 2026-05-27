# Earthquake Early Warning App - Design Specification

**Date:** 2026-05-28
**Project:** myEarthQuakeAlert
**Package:** com.github.mytv.myearthquakealert

## Overview

An Android earthquake early warning (EEW) app with responsive Material 3 Expressive design, compatible with phones, tablets, and Android TV. Uses Jetpack Compose with a unified `Modifier.handleUserKey` for cross-device input. Monitors real-time EEW data via WebSocket and displays a non-fullscreen overlay alert when a matching earthquake is detected.

## Architecture

**Approach: Single Activity + ForegroundService + Overlay Service**

- `MainActivity` — Adaptive Compose UI with WindowSizeClass-based layouts
- `EewMonitorService` — Foreground service with persistent WebSocket connection
- `AlertOverlayService` — System overlay window for earthquake alerts
- Shared domain layer with seismic calculation engine

### Data Flow

```
WebSocket (EewMonitorService)
  → Parse JSON → EewEvent
  → SeismicCalculator: distance, wave arrival, local CSIS
  → AlertEvaluator: check thresholds
  → If match: launch AlertOverlayService with event data
  → If no match: update history list silently
```

## Section 1: Data Models & Domain Logic

### Core Data Models

```kotlin
data class EewEvent(
    val id: String,
    val eventId: String,
    val source: EewSource,
    val reportTime: LocalDateTime,    // UTC+8
    val reportNum: Int,
    val originTime: LocalDateTime,    // UTC+8
    val hypocenter: String,           // e.g. "四川汶川县"
    val latitude: Double,
    val longitude: Double,
    val magnitude: Double,
    val depth: Double?,               // nullable
    val maxIntensity: Int,
    val isFinal: Boolean = false,
)

data class EarthquakeInfo(
    val no: Int,
    val type: String,                 // "automatic" or "reviewed"
    val time: LocalDateTime,
    val location: String,
    val magnitude: String,
    val depth: String,
    val latitude: String,
    val longitude: String,
    val intensity: String,
)

enum class EewSource(
    val label: String,
    val wsUrl: String,
    val httpUrl: String,
) {
    SICHUAN("四川地震局", "wss://ws-api.wolfx.jp/sc_eew", "https://api.wolfx.jp/sc_eew.json"),
    CENC("中国地震台网", "wss://ws-api.wolfx.jp/cenc_eew", "https://api.wolfx.jp/cenc_eew.json"),
    FUJIAN("福建地震局", "wss://ws-api.wolfx.jp/fj_eew", "https://api.wolfx.jp/fj_eew.json"),
    CHONGQING("重庆地震局", "wss://ws-api.wolfx.jp/cq_eew", "https://api.wolfx.jp/cq_eew.json"),
}
```

### Seismic Calculator (ported from kanameishi)

Ported algorithms from the kanameishi project:

1. **JMA2001 Travel-Time Tables** — Bilinear interpolation lookup for P-wave and S-wave travel times based on depth (0-700 km) and distance (0-2000 km). For distances >2000 km, use Jeffreys-Bullen tables.

2. **CSIS Intensity Estimation** (`calcCsis`):
   - Compute chord distance: `lineDis = sqrt(a^2 + R^2 - 2*a*R*cos(dis/R))` where `a = R - depth`, `R = 6371 km`
   - Compute rupture-length correction: `long = 10^((M - 3.821) / 1.86)`
   - Compute effective hypocenter distance: `hypoDis = max(lineDis - 10 - long, dis - long, 0.2*(lineDis - 10), 0)`
   - Two CEA attenuation formulas:
     - `csis1 = 1.297*M - 4.368*log10(dis + 15) + 5.363`
     - `csis2 = 1.297*M - 4.368*log10(hypoDis + 15) + 5.363`
   - **Final: `(csis1 + csis2) / 2`**, clamped to 0-12

3. **Wave Radius Calculation** (`calcWaveDistance`) — Reverse lookup: given elapsed time since origin, returns current P-wave and S-wave radius in km for drawing expanding circles.

```kotlin
object SeismicCalculator {
    fun calcWaveArrival(depth: Double, distanceKm: Double): WaveArrival
    fun calcLocalIntensity(magnitude: Double, depth: Double, distanceKm: Double): Double  // CSIS 0-12
    fun calcWaveRadius(depth: Double, elapsedSeconds: Double, isPWave: Boolean): Double
}
```

### User Location

```kotlin
object LocationProvider {
    suspend fun getLocation(): UserLocation  // GPS first, IP geoip fallback
}
data class UserLocation(val lat: Double, val lon: Double, val source: String)
```

- Primary: Android FusedLocationProvider (GPS/network)
- Fallback: `https://api.wolfx.jp/geoip.php` (city-level, ~10-50km accuracy)
- Cached in DataStore, refreshed periodically

### Settings (DataStore)

```kotlin
data class UserSettings(
    val selectedSource: EewSource = EewSource.CENC,
    val serviceEnabled: Boolean = false,
    val actionMinMagnitude: Double = 0.0,     // 0 = alert on all
    val actionMinIntensity: Int = 0,           // CSIS threshold, 0 = all
    val intenseThreshold: Int = 5,             // CSIS intense threshold
    val allowDismissWithBack: Boolean = true,
)
```

### Alert Threshold Logic (from kanameishi)

```
shouldAction = (actionMinIntensity == 0 OR localCSIS >= actionMinIntensity)
               AND (actionMinMagnitude == 0.0 OR event.magnitude >= actionMinMagnitude)

isIntense = localCSIS >= intenseThreshold
```

## Section 2: Background Service Architecture

### EewMonitorService (ForegroundService)

- Persistent foreground notification ("Monitoring earthquake activity")
- WebSocket connection via OkHttp to the selected EewSource
- Auto-reconnect with exponential backoff (3-10 seconds)
- Heartbeat/ping for connection health monitoring
- On message: parse JSON → normalize to EewEvent → evaluate thresholds → trigger alert if matched
- Starts when user enables service in main screen
- Stops when user disables service

### AlertOverlayService

- Creates `TYPE_APPLICATION_OVERLAY` window via WindowManager
- Inflates ComposeView with alert UI
- Shares data with AlertOverlayService via a singleton `ActiveAlertHolder` (in-memory state holder that the overlay ComposeView observes as Compose state). EewMonitorService writes event + calculated wave data to it; AlertOverlayService reads from it.
- Auto-dismiss: S-wave arrival time + 10 seconds
- Optional dismiss via Back button (controlled by `allowDismissWithBack` setting)
- Plays system alarm sound on alert

### Required Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<!-- foregroundServiceType = "specialUse" declared in service manifest entry, used for continuous real-time earthquake WebSocket monitoring -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<uses-feature android:name="android.hardware.type.tv" android:required="false" />
```

## Section 3: Main Screen UI

### Adaptive Layout Strategy

Uses Compose `WindowSizeClass` for three form factors:

**Phone (Compact width, <600dp):** Single column with vertical scrolling
- TopAppBar with app title
- Service toggle card
- Source selector (dropdown)
- Threshold settings (sliders)
- Simulation test button
- Earthquake history list (from cenc_eqlist API)

**Tablet (Medium width, 600-840dp) / Landscape phone:** Two-pane layout
- Left pane: Settings (service toggle, source, thresholds, simulation)
- Right pane: Earthquake list + detail view

**TV (Expanded width, >840dp):** Three-pane layout with D-pad focus
- Left pane: Settings with large focus targets
- Center pane: Earthquake history list with D-pad navigation
- Right pane: Detail view with large map

### Modifier.handleUserKey

```kotlin
fun Modifier.handleUserKey(
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
): Modifier
```

Unified input modifier that handles:
- Touch/click: standard click handling
- D-pad (TV): focus navigation, Enter key activates
- Keyboard: arrow key navigation, Enter/Esc

Components use this modifier instead of separate `clickable()`/`onKeyEvent()` calls.

### Main Screen Components

1. **ServiceToggleCard** — M3 Switch with confirmation dialog, shows connection status
2. **SourceSelector** — Dropdown to select EEW data source (single source at a time)
3. **ThresholdSettings** — Sliders for minimum magnitude and CSIS intensity threshold
4. **EarthquakeHistoryList** — LazyColumn of recent earthquakes from cenc_eqlist
5. **SimulationButton** — Triggers test alert (simulates M3.0 earthquake at 10-second S-wave distance)
6. **ConnectionStatusChip** — Shows WebSocket connection state (connected/connecting/disconnected)

### Localization

- System locale based: Chinese (zh-CN) as primary, English as fallback
- API data (location names) already in Chinese from Chinese earthquake agencies
- String resources in `values/strings.xml` (default English) and `values-zh/strings.xml` (Chinese)

## Section 4: Alert Overlay UI

### Non-fullscreen Overlay Layout

The overlay is a card that floats above all other apps, sized to approximately 80% of screen width and auto-height based on content.

**Phone (vertical):** Card at top of screen
```
┌─────────────────────────────────┐
│      紧急地震速报 (数据源名)       │  ← Title bar, red/error background
├─────────────────────────────────┤
│  ┌───────────┐                  │
│  │           │  这是紧急地震速报， │
│  │  Mini Map │  请注意强烈摇晃。   │
│  │  ● Epicenter                  │
│  │  ▲ Device │  X发生了Y级地震。   │
│  │  ○ P-wave │                  │
│  │  ○ S-wave │  地震波将在Z秒后   │
│  │           │  到达，预计震级为W。 │
│  └───────────┘                  │
├─────────────────────────────────┤
│  Auto-dismiss after S-wave + 10s │
└─────────────────────────────────┘
```

**TV/Tablet (horizontal):** Wider card with side-by-side map and text
```
┌──────────────────────────────────────────────────────────┐
│             紧急地震速报 (数据源名)                          │
├────────────────────────┬─────────────────────────────────┤
│                        │  这是紧急地震速报，请注意强烈摇晃。  │
│    Mini Map             │                                 │
│    ● Epicenter         │  X发生了Y级地震。                  │
│    ▲ Device            │                                 │
│    ○ P/S-wave circles  │  地震波将在Z秒后到达，              │
│                        │  预计震级为W。                     │
├────────────────────────┴─────────────────────────────────┤
│  Auto-dismiss after S-wave + 10s                          │
└──────────────────────────────────────────────────────────┘
```

### Highlighting

- **X** (location name): Bold, accent color
- **Y** (magnitude): Bold, intensity-mapped color
- **Z** (countdown seconds): Bold, large font, color changes green→yellow→red as time decreases
- **W** (estimated local intensity): Bold, CSIS color-coded badge

### Mini Map (OSMDroid)

- Epicenter marker (red circle with pulse animation)
- Device location marker (blue triangle)
- Expanding P-wave circle (blue, animated from origin time)
- Expanding S-wave circle (red, animated from origin time)
- Circle radii calculated via `calcWaveRadius()` with frame-by-frame updates
- OpenStreetMap tile layer, auto-centered between epicenter and device location

### Auto-Dismiss Behavior

- Default: overlay dismisses 10 seconds after S-wave arrival at user location
- If `allowDismissWithBack = true`: user can press Back to dismiss early
- If `allowDismissWithBack = false`: overlay ignores Back key, only auto-dismisses
- Sound: system alarm tone plays on alert trigger, stops on dismiss

### Simulation Mode

From main screen, user can tap "模拟测试" to trigger a test alert:
- Simulates a M3.0 earthquake at a distance corresponding to 10-second S-wave arrival
- Uses actual user location as the device position
- Shows full alert overlay with simulated data for testing purposes
- Marked as "模拟测试" in the title to distinguish from real alerts

## Section 5: Project Structure & Dependencies

### Package Structure

```
app/src/main/java/com/github/mytv/myearthquakealert/
├── MainActivity.kt
├── MyEarthQuakeAlertApp.kt
├── data/
│   ├── model/          # EewEvent, EarthquakeInfo, UserLocation
│   ├── source/         # EewSource enum, EewWebSocketClient
│   ├── api/            # WolfxApi (Retrofit), GeoIpApi
│   └── repository/     # EewRepository, SettingsRepository
├── domain/
│   ├── SeismicCalculator.kt    # Wave arrival, CSIS intensity
│   ├── TravelTimeTables.kt     # JMA2001 lookup tables
│   └── AlertEvaluator.kt       # Threshold checking
├── service/
│   ├── EewMonitorService.kt    # Foreground WebSocket service
│   └── AlertOverlayService.kt  # System overlay alert
├── ui/
│   ├── theme/          # M3 Expressive theming
│   ├── adaptive/       # AdaptiveLayout, HandleUserKey modifier
│   ├── main/           # Main screen composables
│   ├── alert/          # Alert overlay composables
│   └── components/     # Shared components (IntensityBadge, etc.)
└── util/
    ├── LocationProvider.kt     # GPS + IP fallback
    └── Extensions.kt
```

### Key Dependencies

| Dependency | Purpose |
|-----------|---------|
| Jetpack Compose BOM | UI framework |
| compose-material3 | Material 3 Expressive components |
| Compose Navigation | Screen navigation |
| OkHttp | WebSocket client |
| Retrofit + kotlinx.serialization | HTTP API calls |
| OSMDroid | Map rendering in alert overlay |
| DataStore Preferences | Settings persistence |
| Lifecycle Service | Foreground service lifecycle |
| Accompanist | Adaptive layout utilities |

### Build Configuration

- minSdk: 21
- targetSdk: 36
- Kotlin with Compose
- Single module (app)
- Single APK for all device types (phone, tablet, TV)
