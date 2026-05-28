# README, About Page, and Online Update Feature - Design Specification

**Date:** 2026-05-29
**Project:** myEarthQuakeAlert
**Package:** com.github.mytv.myearthquakealert

## Overview

Add three components to the earthquake alert app:
1. **README.md** - Project documentation in simplified style (Chinese primary, English secondary)
2. **About Screen** - Material 3 card-based UI showing app info, version, data sources, license, and acknowledgments
3. **Online Update Feature** - In-app update checker with APK download and installation via GitHub Releases API

## Section 1: README.md Structure

### Content Organization

Following the kanameishi reference style, create a concise README with these sections:

```markdown
# myEarthQuakeAlert

## 简介 (Introduction)
- Brief description: Android earthquake early warning app
- Supported platforms: Android phones, tablets, Android TV (minSdk 21, targetSdk 36)
- Tech stack: Jetpack Compose + Material 3 Expressive
- Download links: GitHub Releases

## 主要功能 (Main Features)
- Real-time earthquake early warning (EEW) via WebSocket
- Multiple data sources: CENC, Sichuan, Fujian, Chongqing earthquake bureaus
- System overlay alert with countdown and mini map
- Adaptive UI for phone/tablet/TV with unified D-pad support
- Customizable alert thresholds (magnitude, intensity)
- Earthquake history list

## 注意事项 (Important Notes)
- Educational/personal use only
- Uses unofficial data sources - official sources are authoritative
- Free and open-source project
- Not for commercial use

## 数据来源 (Data Sources)
- Earthquake Early Warning & History: [Wolfx Open API](https://wolfx.jp/apidoc)
- Map tiles: OpenStreetMap (via OSMDroid)
- Seismic calculation algorithms: Ported from [kanameishi](https://github.com/Lipomoea/kanameishi)

## 致谢 (Acknowledgments)
- Wolfx Project for API access
- kanameishi project for seismic calculation reference
- OpenStreetMap contributors

## 开放源代码许可 (Open Source License)
- Licensed under Apache License 2.0
```

### Bilingual Approach

- Section headers: Chinese with English in parentheses
- Body text: Primarily Chinese, with English for technical terms
- Code examples and links: English
- Tone: Informative and concise, similar to kanameishi's style

**Why:** Matches the target audience (Chinese users) while remaining accessible to international developers.

## Section 2: About Screen UI Design

### Navigation Integration

Add "About" entry to the main screen:
- **Phone:** Add overflow menu (three-dot icon) in TopAppBar with "About" menu item
- **Tablet/TV:** Add "About" button in settings pane (bottom of the column)

Navigation uses Compose Navigation to `AboutScreen` composable.

### AboutScreen Layout

**Adaptive card grid:**
- **Phone (compact width):** Single column, vertical scroll
- **Tablet (medium width):** Two columns with equal width
- **TV (expanded width):** Two columns with larger spacing and focus indicators

### Card Components

#### 1. App Info Card
```
┌─────────────────────────────┐
│  [App Icon]                 │
│  myEarthQuakeAlert          │
│  地震预警 · 实时监测          │
│                             │
│  基于 Jetpack Compose 开发   │
│  的 Android 地震预警应用      │
└─────────────────────────────┘
```
- App icon (mipmap launcher icon)
- App name from `stringResource(R.string.app_name)`
- Tagline: "地震预警 · 实时监测"
- Brief description (2-3 sentences)

#### 2. Version Info Card
```
┌─────────────────────────────┐
│  版本信息                    │
│                             │
│  当前版本: 1.0.0             │
│  版本代码: 1                 │
│  构建时间: 2026-05-29        │
│                             │
│  [检查更新]  [查看更新日志]   │
└─────────────────────────────┘
```
- Current version: `BuildConfig.VERSION_NAME`
- Version code: `BuildConfig.VERSION_CODE`
- Build date: hardcoded string or generated at build time
- "检查更新" button triggers update check (see Section 3)
- "查看更新日志" button opens browser to GitHub Releases page

#### 3. Data Sources Card
```
┌─────────────────────────────┐
│  数据来源                    │
│                             │
│  • 地震预警数据              │
│    Wolfx Open API           │
│                             │
│  • 地图服务                  │
│    OpenStreetMap            │
│                             │
│  • 算法参考                  │
│    kanameishi 项目          │
└─────────────────────────────┘
```
- Bulleted list with clickable links
- Links open in external browser via `Intent.ACTION_VIEW`

#### 4. License Card
```
┌─────────────────────────────┐
│  开源协议                    │
│                             │
│  Apache License 2.0         │
│                             │
│  [查看完整协议]              │
└─────────────────────────────┘
```
- License name
- Button opens browser to LICENSE file on GitHub

#### 5. Acknowledgments Card
```
┌─────────────────────────────┐
│  致谢                        │
│                             │
│  • Wolfx Project            │
│  • kanameishi 项目          │
│  • OpenStreetMap 贡献者      │
│  • 所有提供帮助的开发者       │
└─────────────────────────────┘
```
- Simple text list, no links needed

### Material 3 Styling

- Use `Card` with default `CardDefaults.cardColors()`
- Title: `MaterialTheme.typography.titleMedium`
- Body: `MaterialTheme.typography.bodyMedium`
- Buttons: `FilledTonalButton` for primary actions, `TextButton` for secondary
- Spacing: `EeqSpacing.md` between cards, `EeqSpacing.sm` within cards
- TV focus: Apply `Modifier.handleUserKey` to all interactive elements

## Section 3: Online Update Feature

### Architecture

**Components:**
1. `UpdateChecker` - Repository class for checking and downloading updates
2. `UpdateInfo` - Data class for release information
3. `UpdateViewModel` - State management for update UI
4. `UpdateDialog` - Composable dialog for update prompts
5. `DownloadService` - Foreground service for APK download (optional, can use coroutine in ViewModel)

### Update Check Flow

```
User taps "检查更新"
  → Show loading indicator
  → Call GitHub Releases API: GET /repos/mytv-android/myEarthquakeAlert/releases/latest
  → Parse JSON response
  → Compare remote version with BuildConfig.VERSION_NAME
  → If newer version exists:
      → Show UpdateDialog with version info and changelog
      → User taps "立即更新"
      → Download APK to app cache directory
      → Show download progress (percentage, speed)
      → On completion: trigger system installer
  → If already latest:
      → Show "已是最新版本" toast
  → On error (network failure, API rate limit):
      → Show error message with retry option
```

### GitHub Releases API Integration

**Endpoint:** `https://api.github.com/repos/mytv-android/myEarthquakeAlert/releases/latest`

**Response structure:**
```json
{
  "tag_name": "v1.1.0",
  "name": "Version 1.1.0",
  "body": "## 更新内容\n- 新增功能A\n- 修复问题B",
  "published_at": "2026-05-29T10:00:00Z",
  "assets": [
    {
      "name": "myEarthquakeAlert-v1.1.0.apk",
      "browser_download_url": "https://github.com/.../myEarthquakeAlert-v1.1.0.apk",
      "size": 12345678
    }
  ]
}
```

**Parsing logic:**
- Extract `tag_name` (e.g., "v1.1.0") and remove "v" prefix
- Compare with `BuildConfig.VERSION_NAME` using semantic versioning
- Find APK asset by filtering `assets` array for `.apk` extension
- Use `browser_download_url` for download

### Version Comparison

```kotlin
fun isNewerVersion(remote: String, local: String): Boolean {
    // Parse "1.2.3" format
    val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
    val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
    
    // Compare major.minor.patch
    for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
        val r = remoteParts.getOrNull(i) ?: 0
        val l = localParts.getOrNull(i) ?: 0
        if (r > l) return true
        if (r < l) return false
    }
    return false
}
```

### APK Download Implementation

**Using OkHttp + Coroutines:**

```kotlin
suspend fun downloadApk(url: String, onProgress: (Int, Long, Long) -> Unit): File {
    val request = Request.Builder().url(url).build()
    val response = okHttpClient.newCall(request).execute()
    
    val contentLength = response.body?.contentLength() ?: -1L
    val outputFile = File(context.cacheDir, "update.apk")
    
    response.body?.byteStream()?.use { input ->
        outputFile.outputStream().use { output ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L
            
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                val progress = if (contentLength > 0) {
                    (totalBytesRead * 100 / contentLength).toInt()
                } else 0
                onProgress(progress, totalBytesRead, contentLength)
            }
        }
    }
    
    return outputFile
}
```

**Progress UI:**
- Show `LinearProgressIndicator` with percentage
- Display download speed (KB/s or MB/s)
- Show downloaded size / total size (e.g., "5.2 MB / 12.3 MB")
- Allow cancellation via "取消" button

### APK Installation

**After download completes:**

```kotlin
fun installApk(context: Context, apkFile: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
    )
    
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    context.startActivity(intent)
}
```

**Required setup:**

1. Add permission to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

2. Add FileProvider in `AndroidManifest.xml`:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

3. Create `res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="apk_cache" path="." />
</paths>
```

### Update Settings (Optional Enhancement)

Add to `UserSettings` data class:
```kotlin
data class UserSettings(
    // ... existing fields
    val autoCheckUpdate: Boolean = true,  // Check on app launch
    val updateCheckInterval: Long = 86400000L,  // 24 hours in milliseconds
    val lastUpdateCheckTime: Long = 0L,
)
```

**Auto-check logic:**
- On app launch (in `MainActivity.onCreate` or `MainScreen` `LaunchedEffect`)
- Check if `autoCheckUpdate == true`
- Check if `System.currentTimeMillis() - lastUpdateCheckTime > updateCheckInterval`
- If both true, silently check for updates in background
- If update available, show non-intrusive notification or badge on About menu item
- User can disable auto-check in settings

### Error Handling

**Network errors:**
- Show toast: "网络连接失败，请检查网络设置"
- Provide "重试" button in dialog

**API rate limit (HTTP 403):**
- GitHub API has rate limit (60 requests/hour for unauthenticated)
- Show message: "请求过于频繁，请稍后再试"
- Cache last check result for 1 hour to avoid repeated requests

**APK download failure:**
- Show error dialog with retry option
- Clean up partial download file

**Installation failure:**
- If user denies `REQUEST_INSTALL_PACKAGES` permission, show explanation dialog
- Provide button to open app settings for permission grant

## Section 4: Implementation Structure

### New Files

```
app/src/main/java/com/github/mytv/myearthquakealert/
├── data/
│   ├── api/
│   │   └── GitHubApi.kt              # Retrofit interface for GitHub Releases API
│   ├── model/
│   │   └── UpdateInfo.kt             # Data class for release info
│   └── repository/
│       └── UpdateRepository.kt       # Update check and download logic
├── ui/
│   ├── about/
│   │   ├── AboutScreen.kt            # Main about screen composable
│   │   ├── AboutViewModel.kt         # State management for about screen
│   │   ├── UpdateDialog.kt           # Update prompt dialog
│   │   └── DownloadProgressDialog.kt # Download progress UI
│   └── main/
│       └── MainScreen.kt             # (Modified) Add About navigation
└── util/
    ├── ApkInstaller.kt               # APK installation helper
    └── VersionComparator.kt          # Version string comparison
```

### Data Models

```kotlin
// UpdateInfo.kt
data class UpdateInfo(
    val version: String,              // e.g., "1.1.0"
    val versionCode: Int,             // Optional, for additional validation
    val releaseNotes: String,         // Markdown changelog
    val apkUrl: String,               // Direct download URL
    val apkSize: Long,                // File size in bytes
    val publishedAt: String,          // ISO 8601 timestamp
)

// GitHubRelease.kt (API response model)
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String,
    val body: String,
    @SerialName("published_at") val publishedAt: String,
    val assets: List<GitHubAsset>,
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long,
)
```

### Retrofit API Interface

```kotlin
interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String = "mytv-android",
        @Path("repo") repo: String = "myEarthquakeAlert",
    ): GitHubRelease
}
```

### UpdateRepository

```kotlin
class UpdateRepository(
    private val githubApi: GitHubApi,
    private val okHttpClient: OkHttpClient,
    private val context: Context,
) {
    suspend fun checkForUpdate(): Result<UpdateInfo?> {
        // Fetch latest release from GitHub
        // Compare with BuildConfig.VERSION_NAME
        // Return UpdateInfo if newer, null if up-to-date
    }
    
    suspend fun downloadApk(
        url: String,
        onProgress: (progress: Int, downloaded: Long, total: Long) -> Unit
    ): Result<File> {
        // Download APK to cache directory
        // Report progress via callback
        // Return File on success
    }
}
```

### AboutViewModel

```kotlin
class AboutViewModel(
    private val updateRepository: UpdateRepository,
) : ViewModel() {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    
    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class Available(val updateInfo: UpdateInfo) : UpdateState()
        object UpToDate : UpdateState()
        data class Downloading(val progress: Int, val downloaded: Long, val total: Long) : UpdateState()
        data class Downloaded(val apkFile: File) : UpdateState()
        data class Error(val message: String) : UpdateState()
    }
    
    fun checkForUpdate() { /* ... */ }
    fun downloadUpdate(updateInfo: UpdateInfo) { /* ... */ }
    fun installUpdate(apkFile: File) { /* ... */ }
}
```

## Section 5: String Resources

### Add to `res/values/strings.xml`

```xml
<!-- About Screen -->
<string name="about">About</string>
<string name="app_tagline">Earthquake Early Warning · Real-time Monitoring</string>
<string name="app_description">An Android earthquake early warning app based on Jetpack Compose, supporting phones, tablets, and Android TV.</string>

<string name="version_info">Version Info</string>
<string name="current_version">Current Version</string>
<string name="version_code">Version Code</string>
<string name="build_date">Build Date</string>
<string name="check_update">Check for Updates</string>
<string name="view_changelog">View Changelog</string>

<string name="data_sources">Data Sources</string>
<string name="data_source_eew">Earthquake Early Warning</string>
<string name="data_source_map">Map Service</string>
<string name="data_source_algorithm">Algorithm Reference</string>

<string name="license">License</string>
<string name="view_full_license">View Full License</string>

<string name="acknowledgments">Acknowledgments</string>
<string name="acknowledgment_wolfx">Wolfx Project</string>
<string name="acknowledgment_kanameishi">kanameishi Project</string>
<string name="acknowledgment_osm">OpenStreetMap Contributors</string>
<string name="acknowledgment_developers">All Helpful Developers</string>

<!-- Update Feature -->
<string name="update_available">Update Available</string>
<string name="update_version">Version %s</string>
<string name="update_size">Size: %s</string>
<string name="update_now">Update Now</string>
<string name="update_later">Later</string>
<string name="update_checking">Checking for updates…</string>
<string name="update_up_to_date">Already up to date</string>
<string name="update_downloading">Downloading update…</string>
<string name="update_download_progress">%d%% (%s / %s)</string>
<string name="update_download_speed">%s/s</string>
<string name="update_install">Install</string>
<string name="update_error">Update check failed</string>
<string name="update_error_network">Network connection failed</string>
<string name="update_error_rate_limit">Too many requests, please try again later</string>
<string name="update_retry">Retry</string>
<string name="cancel">Cancel</string>
```

### Add to `res/values-zh/strings.xml`

```xml
<!-- About Screen -->
<string name="about">关于</string>
<string name="app_tagline">地震预警 · 实时监测</string>
<string name="app_description">基于 Jetpack Compose 开发的 Android 地震预警应用，支持手机、平板和 Android TV。</string>

<string name="version_info">版本信息</string>
<string name="current_version">当前版本</string>
<string name="version_code">版本代码</string>
<string name="build_date">构建时间</string>
<string name="check_update">检查更新</string>
<string name="view_changelog">查看更新日志</string>

<string name="data_sources">数据来源</string>
<string name="data_source_eew">地震预警数据</string>
<string name="data_source_map">地图服务</string>
<string name="data_source_algorithm">算法参考</string>

<string name="license">开源协议</string>
<string name="view_full_license">查看完整协议</string>

<string name="acknowledgments">致谢</string>
<string name="acknowledgment_wolfx">Wolfx Project</string>
<string name="acknowledgment_kanameishi">kanameishi 项目</string>
<string name="acknowledgment_osm">OpenStreetMap 贡献者</string>
<string name="acknowledgment_developers">所有提供帮助的开发者</string>

<!-- Update Feature -->
<string name="update_available">发现新版本</string>
<string name="update_version">版本 %s</string>
<string name="update_size">大小：%s</string>
<string name="update_now">立即更新</string>
<string name="update_later">稍后</string>
<string name="update_checking">正在检查更新…</string>
<string name="update_up_to_date">已是最新版本</string>
<string name="update_downloading">正在下载更新…</string>
<string name="update_download_progress">%d%% (%s / %s)</string>
<string name="update_download_speed">%s/s</string>
<string name="update_install">安装</string>
<string name="update_error">检查更新失败</string>
<string name="update_error_network">网络连接失败，请检查网络设置</string>
<string name="update_error_rate_limit">请求过于频繁，请稍后再试</string>
<string name="update_retry">重试</string>
<string name="cancel">取消</string>
```

## Section 6: Testing Strategy

### Manual Testing Checklist

**README:**
- [ ] Verify all links work (GitHub repo, Wolfx API doc, kanameishi)
- [ ] Check rendering on GitHub (markdown preview)
- [ ] Verify bilingual content is clear and accurate

**About Screen:**
- [ ] Test on phone (compact width) - single column layout
- [ ] Test on tablet (medium width) - two column layout
- [ ] Test on Android TV (expanded width) - two column with D-pad focus
- [ ] Verify all external links open in browser
- [ ] Check card spacing and typography consistency

**Update Feature:**
- [ ] Mock GitHub API response for testing (use MockWebServer)
- [ ] Test version comparison logic with various version strings
- [ ] Test update check with network error (airplane mode)
- [ ] Test download progress UI with large APK file
- [ ] Test download cancellation
- [ ] Test APK installation flow (requires real device, not emulator)
- [ ] Test permission denial scenario for `REQUEST_INSTALL_PACKAGES`
- [ ] Test GitHub API rate limit handling (make 60+ requests)

### Unit Tests

```kotlin
class VersionComparatorTest {
    @Test
    fun `isNewerVersion returns true for higher major version`() {
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
    }
    
    @Test
    fun `isNewerVersion returns false for same version`() {
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
    }
    
    @Test
    fun `isNewerVersion handles missing patch version`() {
        assertTrue(isNewerVersion("1.1", "1.0.5"))
    }
}
```

### Integration Tests

```kotlin
class UpdateRepositoryTest {
    @Test
    fun `checkForUpdate returns UpdateInfo when newer version available`() = runTest {
        // Use MockWebServer to simulate GitHub API
        val mockResponse = """
            {
              "tag_name": "v1.1.0",
              "name": "Version 1.1.0",
              "body": "## Updates\n- Feature A",
              "published_at": "2026-05-29T10:00:00Z",
              "assets": [
                {
                  "name": "app.apk",
                  "browser_download_url": "https://example.com/app.apk",
                  "size": 12345678
                }
              ]
            }
        """
        // ... test implementation
    }
}
```

## Section 7: Deployment Considerations

### GitHub Releases Workflow

When publishing a new version:

1. Update `versionName` and `versionCode` in `app/build.gradle.kts`
2. Build release APK: `./gradlew assembleRelease`
3. Create GitHub release with tag format `vX.Y.Z` (e.g., `v1.1.0`)
4. Upload signed APK as release asset
5. Write release notes in Chinese (markdown format)
6. Publish release

**Release notes template:**
```markdown
## 更新内容

### 新增功能
- 功能描述

### 改进优化
- 优化描述

### 问题修复
- 修复描述

## 注意事项
- 重要提示（如有）
```

### APK Signing

Ensure release APK is signed with the same keystore for update compatibility:
- Users can only install updates signed with the same certificate
- Store keystore securely (not in version control)
- Use GitHub Secrets for CI/CD signing

### Future Enhancements

**Phase 2 (optional):**
- Add Gitee mirror support for faster downloads in China
- Implement delta updates (only download changed parts)
- Add update notification channel for background checks
- Support multiple APK variants (arm64-v8a, armeabi-v7a, x86_64)

**Why:** These are nice-to-have features that can be added later based on user feedback and actual usage patterns. Start with the core functionality first.

## Summary

This design adds three cohesive components:

1. **README.md** - Concise project documentation following kanameishi's style, bilingual (Chinese primary)
2. **About Screen** - Material 3 card-based UI with adaptive layout for all device types
3. **Online Update** - GitHub Releases integration with in-app APK download and installation

The implementation prioritizes simplicity and user experience while maintaining consistency with the existing app architecture. All components follow Material 3 Expressive design guidelines and support the app's multi-device strategy (phone/tablet/TV).
