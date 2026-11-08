package com.github.mytv.myearthquakealert.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    val schema: String = "",
    val channel: String = "stable",
    val versionCode: Long = 0,
    val versionName: String = "",
    val tag: String = "",
    val publishedAt: String = "",
    val minSdk: Int = 0,
    val apk: UpdateApk = UpdateApk(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class UpdateApk(
    val url: String = "",
    val size: Long = 0,
    val sha256: String = "",
)

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val tag: String,
    val publishedAt: String,
    val size: Long,
    val url: String,
    val sha256: String,
    val notes: List<String>,
)
