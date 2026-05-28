# 地震预警弹窗重新设计规格

**日期：** 2026-05-29  
**目标：** 将现有预警弹窗完全替换为日本气象厅风格的横向布局设计

## 概述

重新设计 `AlertOverlay` 组件，采用日本气象厅紧急地震速报的经典样式：左侧地图，右侧红色标题栏 + 蓝色信息区。这种设计具有强烈的视觉冲击力，信息层次清晰，适合电视和移动设备。

## 设计原则

1. **视觉冲击力优先**：红色标题栏立即抓住注意力
2. **信息清晰度**：蓝色背景提供良好的可读性
3. **空间利用**：横向布局充分利用宽屏空间
4. **保持简洁**：移除不必要的动画和装饰效果

## 整体架构

### 布局结构

```
┌─────────────────────────────────────────────────┐
│  地图区域  │  红色标题栏：紧急地震速报（数据源） │
│  (240dp)   ├─────────────────────────────────────┤
│            │                                     │
│  震源标记  │         蓝色信息区域                │
│  P波圆圈   │                                     │
│  S波圆圈   │  • 倒计时大数字                     │
│            │  • 震源信息（位置、震级）            │
│            │  • 预警信息（剩余时间、预计烈度）    │
│            │                                     │
└────────────┴─────────────────────────────────────┘
    40%                    60%
```

### 尺寸规格

- **整体宽度**：600dp（保持不变）
- **整体高度**：自适应内容
- **左侧地图**：240dp 宽（固定）
- **右侧信息区**：360dp 宽（固定）
- **标题栏高度**：56dp（固定）
- **形状**：矩形，无圆角（符合日式预警风格）

## 配色方案

### 标题栏
- **背景色**：`Color(0xFFE60012)` - 日本警报标准红
- **文字颜色**：白色 `Color.White`
- **字体**：粗体，MaterialTheme.typography.titleLarge

### 主信息区
- **背景色**：`Color(0xFF1565C0)` - 深蓝色
- **主文字颜色**：白色 `Color.White`
- **倒计时数字**：白色，超大号字体（displayLarge）
- **强调文字**：使用现有的 CSIS 颜色系统（震级、烈度）

### 地图区域
- **背景色**：`Color(0xFF212121)` - 深灰/黑色
- **保留现有**：P波蓝色、S波红色、震源标记

## 组件设计

### 1. AlertOverlay（主容器）

**修改内容：**
- 移除现有的圆角背景 `MaterialTheme.shapes.extraLarge`
- 移除半透明效果 `copy(alpha = 0.87f)`
- 改为 Row 横向布局，左右分栏
- 移除整体 padding

**新结构：**
```kotlin
Row(modifier = modifier.width(600.dp)) {
    // 左侧：地图区域
    Box(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(Color(0xFF212121))
    ) {
        AlertMap(alertData = alertData)
    }
    
    // 右侧：信息区域
    Column(modifier = Modifier.width(360.dp)) {
        RedTitleBar(...)
        BlueInfoArea(...)
    }
}
```

### 2. RedTitleBar（新组件）

**功能：** 显示红色标题栏和数据源信息

**布局：**
```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .background(Color(0xFFE60012))
        .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text(
        text = "紧急地震速报（${sourceName}）",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    if (isSimulation) {
        Text(
            text = stringResource(R.string.simulation_label),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
    }
}
```

**参数：**
- `sourceName: String` - 数据源名称
- `isSimulation: Boolean` - 是否为模拟测试

### 3. BlueInfoArea（新组件）

**功能：** 显示蓝色背景的主要预警信息

**布局：**
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF1565C0))
        .padding(EeqSpacing.md),
    verticalArrangement = Arrangement.spacedBy(EeqSpacing.md)
) {
    CountdownSection(...)
    EpicenterSection(...)
    WarningSection(...)
}
```

**子组件：**

#### 3.1 CountdownSection（倒计时区域）
```kotlin
Column(horizontalAlignment = Alignment.Start) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = remainingSeconds.toInt().toString(),
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            fontSize = 72.sp
        )
        Text(
            text = "秒",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "CSIS ${localCsis.toInt()}",
        style = MaterialTheme.typography.titleLarge,
        color = csisColor(localCsis),
        fontWeight = FontWeight.Bold
    )
}
```

#### 3.2 EpicenterSection（震源信息）
```kotlin
Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
        text = buildAnnotatedString {
            append("震源：")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(hypocenter)
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White
    )
    Text(
        text = buildAnnotatedString {
            append("震级：")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFFEB3B))) {
                append("M${magnitude}")
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White
    )
}
```

#### 3.3 WarningSection（预警信息）
```kotlin
Text(
    text = buildAnnotatedString {
        append("地震波将在 ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFFEB3B))) {
            append("${remainingSeconds.toInt()}")
        }
        append(" 秒后到达")
    },
    style = MaterialTheme.typography.bodyLarge,
    color = Color.White
)
Text(
    text = buildAnnotatedString {
        append("预计烈度：")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = csisColor(localCsis))) {
            append("CSIS ${localCsis.toInt()}")
        }
    },
    style = MaterialTheme.typography.bodyLarge,
    color = Color.White
)
```

### 4. AlertMap（地图区域 - 调整）

**修改内容：**
- 背景色改为深灰/黑色 `Color(0xFF212121)`
- 移除 `Modifier.weight(1f)`，改为固定宽度 240dp
- 保留现有的震源标记、P波/S波圆圈逻辑
- 确保地图内容在深色背景上清晰可见

**注意：** AlertMap 组件本身的内部实现保持不变，只调整外部容器的样式。

## 动画效果

### 移除的动画
- ~~倒计时的弹簧动画~~ (`animateFloatAsState` with spring)
- ~~脉冲动画~~ (`rememberInfiniteTransition` with pulse alpha)
- ~~圆角方框背景动画~~

### 保留的动画
- 倒计时数字的自然更新（通过 `LaunchedEffect` 每秒更新）

### 可选的入场动画
- 整个弹窗从顶部滑入（使用 `slideInVertically`）
- 实现位置：在 `AlertOverlayService` 中添加，或在 `AlertOverlay` 组件中使用 `AnimatedVisibility`

## 数据流与状态管理

### 保持不变的部分
- `AlertData` 数据模型
- `ActiveAlertHolder` 状态管理
- `AlertOverlayService` 服务层逻辑
- 倒计时计算逻辑（`elapsedSeconds`、`remainingSeconds`）
- 返回键处理（`backHandler`）

### 修改的部分
- 仅修改 `AlertOverlay.kt` 的 UI 层实现
- 重构现有的 `AlertHeader`、`AlertCountdown`、`AlertDescription` 组件
- 新增 `RedTitleBar`、`BlueInfoArea` 及其子组件

## 错误处理与边界情况

### 数据异常处理
1. **倒计时为负数**：使用 `max(0.0, alertData.sWaveSeconds - elapsedSeconds)` 确保显示 0
2. **震级/烈度异常值**：依赖现有的 `csisColor()` 函数处理边界情况
3. **地图数据缺失**：AlertMap 组件内部已有处理逻辑
4. **长文本溢出**：使用 `maxLines = 1` 和 `overflow = TextOverflow.Ellipsis`

### 无障碍支持
- 保留现有的语义化文本内容
- 确保颜色对比度符合 WCAG AA 标准：
  - 红底白字：对比度 > 4.5:1 ✓
  - 蓝底白字：对比度 > 4.5:1 ✓
- 保留 `contentDescription` 用于屏幕阅读器

## 实现细节

### 文件修改清单
1. **app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertOverlay.kt**
   - 完全重写 `AlertOverlay` 组件
   - 移除 `AlertHeader`、`AlertCountdown`、`AlertDescription`
   - 新增 `RedTitleBar`、`BlueInfoArea`、`CountdownSection`、`EpicenterSection`、`WarningSection`
   - 更新 Preview 函数

2. **app/src/main/java/com/github/mytv/myearthquakealert/ui/alert/AlertMap.kt**
   - 调整背景色为深灰/黑色（如果当前是透明或浅色）
   - 确保在深色背景上可见性

3. **app/src/main/java/com/github/mytv/myearthquakealert/ui/theme/Color.kt**
   - 新增颜色常量：
     ```kotlin
     val AlertRed = Color(0xFFE60012)
     val AlertBlue = Color(0xFF1565C0)
     val AlertMapBackground = Color(0xFF212121)
     ```

### 字符串资源
保持现有的字符串资源不变，继续使用：
- `R.string.alert_title` - 用于标题栏
- `R.string.simulation_label` - 用于模拟标签

### 测试要点
1. **视觉测试**：使用 Preview 函数验证布局和配色
2. **倒计时测试**：确认倒计时从正数到 0 的过渡
3. **模拟标签测试**：验证模拟模式下标签显示
4. **长文本测试**：测试超长震源名称的显示
5. **不同烈度测试**：验证 CSIS 0-12 的颜色显示

## 成功标准

1. ✓ 布局符合日本气象厅风格（左地图右信息）
2. ✓ 红色标题栏醒目，蓝色信息区清晰
3. ✓ 倒计时数字大而清晰（72sp）
4. ✓ 所有现有功能保持正常（倒计时、地图、返回键）
5. ✓ 无动画卡顿或性能问题
6. ✓ 在 600dp 宽度下显示完整
7. ✓ 颜色对比度符合无障碍标准

## 未来扩展

以下功能不在本次实现范围内，但可作为未来改进方向：
- 多语言支持的标题栏文字
- 可配置的配色主题
- 更复杂的入场/出场动画
- 震源位置的文字列表（如参考图片中的"九州 中国 四国"）
