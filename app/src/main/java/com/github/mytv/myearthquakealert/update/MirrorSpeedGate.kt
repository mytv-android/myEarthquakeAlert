package com.github.mytv.myearthquakealert.update

/**
 * 应用内用镜像时的速度门槛：前 [WINDOW_MS] 毫秒拉不到 [MIN_BYTES] 就踢掉，换下一个站。
 *
 * 放在 `update` 包而不是 `data.repository`，是因为 [ApkDownloader] 是纯 JVM 代码
 * （没有 Android 类型，用 MockWebServer 单测），两边要共用同一组数字。
 */
object MirrorSpeedGate {
    const val WINDOW_MS = 5_000L
    const val MIN_BYTES = 1L shl 20
}
