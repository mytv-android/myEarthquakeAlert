# myEarthQuakeAlert

## 简介 (Introduction)

myEarthQuakeAlert 是一个基于 Jetpack Compose 开发的 Android 地震预警应用，支持手机、平板和 Android TV。

An Android earthquake early warning app built with Jetpack Compose, supporting phones, tablets, and Android TV.

- **支持平台 (Platforms):** Android 5.0+ (API 21+)
- **技术栈 (Tech Stack):** Jetpack Compose + Material 3 Expressive
- **下载 (Download):** [GitHub Releases](https://github.com/mytv-android/myEarthquakeAlert/releases)

## 主要功能 (Main Features)

- 实时地震预警 (Real-time earthquake early warning via WebSocket)
- 多数据源支持：中国地震台网、四川地震局、福建地震局、重庆地震局
- 系统悬浮窗预警，带倒计时和迷你地图
- 自适应 UI，支持手机/平板/TV 的统一 D-pad 操作
- 可自定义预警阈值（震级、烈度）
- 地震历史记录列表

## 注意事项 (Important Notes)

- 本应用程序仅作为学习和个人使用
- 本应用程序使用非官方数据源，可能出现错误。一切信息请以官方发布为准
- 本应用程序为永久免费的开源项目
- 严禁将本应用用于商业场合

## 数据来源 (Data Sources)

- **地震预警与历史数据:** [Wolfx Open API](https://wolfx.jp/apidoc)
- **地图服务:** OpenStreetMap (via OSMDroid)
- **地震计算算法:** 参考 [kanameishi](https://github.com/Lipomoea/kanameishi) 项目

## 致谢 (Acknowledgments)

- Wolfx Project - 提供 API 接口
- kanameishi 项目 - 地震计算算法参考
- OpenStreetMap 贡献者

## 开放源代码许可 (Open Source License)

本项目基于 [Apache License 2.0](LICENSE) 协议授权。
