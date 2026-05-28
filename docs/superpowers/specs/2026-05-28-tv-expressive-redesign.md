# TV-Friendly + Material 3 Expressive 改造设计

日期: 2026-05-28
方案: A（渐进式Expressive改造）

## 背景

myEarthQuakeAlert 是一个地震预警 Android 应用，需同时支持手机和 Android TV。当前问题：
1. 使用标准 Button/Switch/RadioButton/Slider，TV 遥控器操作困难
2. UI 组件使用稳定版 Material3 API，未采用 Expressive 特性
3. Focus 状态用 onFocusChanged 检测，缺少 TV 专用交互工具
4. 硬编码 dp 值和 Color.White 散布各组件

## 核心约束

- 手机 + TV 并重，两种体验同等重要
- 不用任何 Button composable，遥控器无法操作
- 不引入 tv-material 库，仅替换 Button 为其他组件
- 保留现有 handleUserKey 机制的思路，替换为更完善的实现
- 使用最新 BOM (2025.05.01) + @OptIn 解锁 Expressive API

## Section 1: Button → 替代组件映射

| 现有组件 | 文件 | 替换为 | TV 交互方式 |
|---------|------|--------|------------|
| SimulationButton (FilledTonalButton) | SimulationButton.kt | Card + handleKeyEvents(onSelect) + collectIsFocusedAsState | D-pad focus → Enter 触发 |
| Switch (直接点击) | ServiceToggleCard.kt | 保留 Switch，Card 加 handleKeyEvents(onSelect 切换) | D-pad focus 到 Card → Enter 切换 |
| RadioButton 行 | SourceSelector.kt | 可 focus Card 行 | D-pad focus → Enter 选中 |
| Slider (直接拖动) | ThresholdSettings.kt | 保留 Slider，Card 加 handleKeyEvents(onLeft/Right 步进) | ←→ 键步进 |
| IconButton | AlertOverlay.kt | Icon + clickableNoIndication + handleKeyEvents(onSelect) | D-pad focus → Enter 触发 |
| EarthquakeHistoryList item Card | EarthquakeHistoryList.kt | 加 handleKeyEvents(onSelect) | D-pad focus → Enter 触发 |

**关键原则：**
- 不用任何 Button composable
- 所有可交互元素必须可通过 D-pad focus 到达
- Focus 状态用 `collectIsFocusedAsState` 驱动视觉变化
- Switch/Slider 保留，但必须被可 focus 的 Card 包裹

## Section 2: TV 交互系统 (handleKeyEvents 替换)

替换现有 HandleUserKey.kt 为基于 mytv 项目的 TV 工具体系。

**核心工具：**

| 工具 | 作用 | 需要 |
|------|------|------|
| `handleKeyEvents(onLeft/Right/Up/Down/Select/LongSelect/ContinuousLong...)` | D-pad 导航 + 确认 + 长按 + 连续长按 | 是 |
| `collectIsFocusedAsState` | Focus 状态驱动视觉变化 | 是 |
| `clickableNoIndication` | 无 ripple 的点击（TV 不需要 ripple） | 是 |
| `dpadSpeedLimit` | 限制 D-pad 重复速率防止卡顿 | 是 |
| `focusOnLaunched` | 自动请求焦点 | 是 |
| `backHandler` | Back 键处理 | 是 |
| `ifElse` | 条件 Modifier | 可选 |
| `handleDragGestures` | 滑动手势 | 不需要 |
| `handleAppRemoteKeys` | 电视专用功能键 | 不需要 |
| `NavHostController` 相关 | 导航 | 不需要 |

**适配原则：**
- 移除 LAYOUT_GRID_WIDTH/SPACING 引用，改用本项目的 8dp spacing tokens
- 移除 NavHostController 相关工具
- `handleKeyEvents` 的 `onSelect` 回调替代现有 `handleUserKey` 的 `onConfirm`
- 所有可交互组件用 `interactionSource: MutableInteractionSource` + `collectIsFocusedAsState` 驱动 focus 视觉
- 包含触觉反馈 (`HapticFeedbackConstantsCompat`) 和音效反馈 (`SoundEffectConstants`)

**Focus 视觉策略：**
- 未 focus → 正常态
- Focused → scale 微增 1.02 + shape morphing（Expressive）+ tonal surface 变化
- Pressed → shape morphing 动画

## Section 3: Expressive Shape 系统

Theme.kt 的 Shapes 重写：

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val EeqShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),       // chips
    small = RoundedCornerShape(8.dp),            // text fields, menus
    medium = RoundedCornerShape(16.dp),          // cards (原 12dp → 16dp)
    large = RoundedCornerShape(20.dp),           // FAB (原 16dp → 20dp large-increased)
    extraLarge = RoundedCornerShape(32.dp),      // dialogs, overlay (原 28dp → 32dp)
)
```

**组件 shape 映射：**

| Shape Token | 值 | 用途 |
|------------|-----|------|
| medium (Card) | 16dp | EarthquakeHistoryItem Card, ServiceToggleCard |
| large-increased | 20dp | 模拟测试 Card |
| extra-large-increased | 32dp | AlertOverlay 容器 |
| extra-extra-large | 48dp | 主操作元素（模拟测试、overlay countdown 区域） |
| full | 9999dp | IntensityBadge, ConnectionStatusChip |

**Shape Morphing：**
- Card focus 时从 medium morph 到 large（16dp → 20dp）
- 模拟测试 Card pressed 时从 large-increased morph 到 extra-large-increased（20dp → 32dp）
- AlertCountdown 区域用 extra-extra-large (48dp)，脉冲动画时 shape 保持只变 alpha

**移除硬编码 shape：**
- ConnectionStatusChip 的 `RoundedCornerShape(12.dp)` → full 或 extraSmall
- AlertOverlay 无 shape → 加 extraLarge
- IntensityBadge 的 CircleShape → 保留（full token 语义）

## Section 4: Expressive Motion / Spring 动画

### MotionScheme 定义（Motion.kt）

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val EeqMotionScheme = MotionScheme(
    defaultSpatial = spring(dampingRatio = 0.8f, stiffness = 400f),
    defaultEffects = spring(dampingRatio = 0.6f, stiffness = 500f),
    expressiveSpatial = spring(dampingRatio = 0.5f, stiffness = 600f),
    expressiveEffects = spring(dampingRatio = 0.4f, stiffness = 700f),
)
```

注：具体参数名以 2025.05.01 BOM 中的 MotionScheme API 签名为准。

### 组件动画替换

| 组件 | 当前 | 替换为 |
|------|------|--------|
| AlertCountdown 脉冲 | `infiniteRepeatable(tween(600))` alpha | `animateFloat` + expressive spring 做 alpha 脉冲 |
| AlertCountdown 倒计时 | `animateFloatAsState(tween(300))` | `animateFloatAsState(spring(expressive))` |
| Card focus scale | 无 | `animateFloatAsState(spring(default))` scale 1.0 → 1.02 |
| Card focus shape morph | 无 | `animateDpAsState(spring(default))` cornerRadius |
| IntensityBadge 出现 | 无 | `animateFloatAsState(spring(expressive))` alpha 0→1 + scale 0.8→1.0 |
| EarthquakeHistoryList item | 无 | 入场时 `animateFloatAsState(spring(default))` alpha + offset |

### 保留 tween 的场景

- AlertOverlay 进入/退出 transition → emphasized easing（400ms enter / 200ms exit）
- LazyColumn item 动画 → `animateItem()`，不需要自定义 spring

### 不添加动画的场景

- Switch 切换（M3 内置动画已足够）
- Slider 拖动（M3 内置）
- ConnectionStatusChip（状态变化太频繁，动画会干扰）

## Section 5: Emphasized Typography + 8dp Spacing

### 5A. Typography 重写（Type.kt）

重写为 M3 标准 type scale + emphasized 变体：

| 标准样式 | 当前 | M3 标准 | 用途 |
|---------|------|--------|------|
| displayLarge | 无 | 57sp/400 | AlertOverlay countdown 数字 |
| headlineLarge | 32sp/Bold | 32sp/400 | AlertHeader 标题 |
| headlineMedium | 28sp/Bold | 28sp/400 | 大标题 |
| titleLarge | 22sp/SemiBold | 22sp/400 | App bar 标题 |
| titleMedium | 16sp/SemiBold | 16sp/500 | Card 标题 |
| bodyLarge | 16sp/Normal | 16sp/400 | 描述文本 |
| bodyMedium | 14sp/Normal | 14sp/400 | 副文本 |
| labelLarge | 14sp/Medium | 14sp/500 | 按钮标签 |
| labelMedium | 无 | 12sp/500 | Navigation 标签 |
| labelSmall | 无 | 11sp/500 | 小标注 |

**Emphasized 变体（新增）：**

| 变体 | Weight 变化 | 用途 |
|------|-----------|------|
| emphasizedHeadlineLarge | 400→700 | Focus 状态下的 AlertHeader |
| emphasizedTitleMedium | 500→700 | Focus/selected 状态的 Card 标题 |
| emphasizedBodyLarge | 400→600 | 重要描述文本 |
| emphasizedLabelLarge | 500→700 | Focus 状态的交互标签 |

**AlertOverlay 字体调整：**
- 48sp Bold countdown → displayLarge (57sp/400)，倒计时到 0 时用 emphasized 变体 (57sp/700)
- 不再硬编码 FontWeight.Bold，统一从 Typography 取

### 5B. 8dp Spacing System（Spacing.kt）

```kotlin
object EeqSpacing {
    val xs = 4.dp    // 内间距、小间距（icon 与 text 间）
    val sm = 8.dp    // 列表项内间距
    val md = 16.dp   // Card 内 padding、组件间间距
    val lg = 24.dp   // 区块间间距
    val xl = 32.dp   // 大区块间距
    val xxl = 48.dp  // AlertOverlay 内大间距
}
```

**映射现有硬编码值：**

| 当前值 | 文件 | Token |
|--------|------|-------|
| 4.dp | SourceSelector | xs |
| 8.dp | ThresholdSettings, EarthquakeHistoryList | sm |
| 12.dp | AlertOverlay, EarthquakeHistoryList padding | → sm（8dp 系统，统一到 8dp） |
| 16.dp | ServiceToggleCard, MainScreen, AlertOverlay | md |
| 40.dp | IntensityBadge 尺寸 | → xxl (48dp)（badge 从 40→48，更 Expressive） |
| 600.dp | AlertOverlay 宽度 | 不改（overlay 宽度，非 spacing） |

**ConnectionStatusChip padding 调整：** horizontal=12.dp → md, vertical=4.dp → xs

## Section 6: Button 替换详细方案

### 6.1 SimulationButton → SimulationCard

- Card (filled tonal) + Icon + Text
- handleKeyEvents(onSelect = onSimulate)
- Focus: isFocused → scale 1.02 + shape medium→large + title emphasizedTitleMedium
- 长按：无

### 6.2 ServiceToggleCard → 保留 Switch + Card handleKeyEvents

- Card 整体加 handleKeyEvents(onSelect = { enabled = !enabled })
- Switch 改为 enabled=false，由 Card handleKeyEvents 控制
- Focus: isFocused → border + Switch track 高亮

### 6.3 SourceSelector → 可 focus Card 行替代 RadioButton

- 每个数据源项是一个可 focus 的 Card
- handleKeyEvents(onSelect = { 选中该项 })
- 选中状态：filled tonal + emphasizedTitleMedium
- 未选中状态：outlined + titleMedium
- Focus 状态：独立于 selected，用 border 表示

### 6.4 ThresholdSettings → 保留 Slider + handleKeyEvents 步进

- 外层 Card 加 handleKeyEvents
- onLeft → slider 值 -0.5
- onRight → slider 值 +0.5
- onContinuousLongLeft/Right → 连续步进
- Slider 自身 enabled=false，不允许触摸直接拖动
- 手机端可通过触摸 Card 后左右滑动（handleDragGestures，可选实现，不阻塞核心）

### 6.5 AlertOverlay IconButton → FocusableIcon

- Icon + clickableNoIndication(onClick=onDismiss) + handleKeyEvents(onSelect=onDismiss)
- Focus: isFocused → background tonal circle + scale 1.05
- backHandler(onDismiss) 也加上

### 6.6 EarthquakeHistoryList → 加 handleKeyEvents

- item Card 加 handleKeyEvents(onSelect = onClick)
- 其余不变

## Section 7: Theme.kt 完整重写 + Color 方案

### Color.kt 重构

**保留：**
- 13 个 CSIS intensity 色值 (Csis0-Csis12) + csisColor() → 地震烈度标准色
- PWaveBlue, SWaveRed → 地图专用语义色

**移除/迁移：**
- AlertRed → 迁入 Theme 的 error role
- AlertRedContainer → 迁入 Theme 的 errorContainer role

**新增语义色：**
- ConnectionGreen, ConnectingYellow, DisconnectedRed

### Theme.kt ColorScheme

- error = AlertRed, errorContainer = AlertRedContainer
- 其余全部走 dynamicLightColorScheme (API31+) 或默认 lightColorScheme
- 不自定义 primary/secondary/tertiary，让 dynamic color 发挥

### 硬编码 Color.White 清理

| 文件 | 当前 | 替换为 |
|------|------|--------|
| IntensityBadge | Color.White | MaterialTheme.colorScheme.onPrimary |
| ConnectionStatusChip | Color.White | Color.White 保留（业务色 bg，白字正确对比度） |
| AlertOverlay | Color.White | MaterialTheme.colorScheme.inverseOnSurface |
| AlertOverlay bg | Color(0xDD000000) | MaterialTheme.colorScheme.inverseSurface.copy(alpha=0.87f) |

## Section 8: 改动范围汇总

### 改动文件清单

| 文件 | 改动类型 |
|------|---------|
| ui/adaptive/HandleUserKey.kt | 重写 → TV 交互工具集 |
| ui/theme/Theme.kt | 重写 → Expressive Shapes + MotionScheme + error 覆盖 |
| ui/theme/Type.kt | 重写 → M3 标准 type scale + emphasized 变体 |
| ui/theme/Color.kt | 重构 → 移除 AlertRed/AlertRedContainer（迁入 Theme） |
| ui/main/SimulationButton.kt | 重写 → SimulationCard，无 Button |
| ui/main/ServiceToggleCard.kt | 修改 → Switch 由 Card handleKeyEvents 控制 |
| ui/main/SourceSelector.kt | 重写 → 可 focus Card 行替代 RadioButton |
| ui/main/ThresholdSettings.kt | 修改 → Slider 步进由 handleKeyEvents 控制 |
| ui/main/ConnectionStatusChip.kt | 微调 → spacing tokens |
| ui/main/IntensityBadge.kt | 微调 → 尺寸 40→48dp，Color.White→onPrimary |
| ui/main/EarthquakeHistoryList.kt | 微调 → item 加 handleKeyEvents，spacing tokens |
| ui/main/MainScreen.kt | 修改 → Button→Card，spacing tokens |
| ui/alert/AlertOverlay.kt | 修改 → spring 动画，Color.White→inverseOnSurface，shape/spacing tokens |

### 新增文件

| 文件 | 内容 |
|------|------|
| ui/theme/Spacing.kt | EeqSpacing 对象，8dp spacing tokens |
| ui/theme/Motion.kt | EeqMotionScheme，spring 参数定义 |

### 不改动的部分

- 数据层（api/model/repository/source/websocket）
- 业务逻辑（domain/AlertEvaluator/SeismicCalculator）
- 服务层（service/）
- AndroidManifest
- build.gradle（BOM 2025.05.01 已包含 Expressive API）
- AlertMap.kt（osmdroid View 层）
- AdaptiveLayout.kt（三窗格布局逻辑不变）

### 不在本 scope 内的问题（记录但不处理）

- ThresholdSettings 的 intenseThreshold 未使用参数
- AlertOverlay 的硬编码中文字符串（未走 stringResource）
- 项目没有 Navigation 库使用
