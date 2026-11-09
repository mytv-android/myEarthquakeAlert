package com.github.mytv.myearthquakealert.data.model

import kotlinx.serialization.Serializable

/**
 * `update.json`, published with every GitHub Release by the CI workflow. Unknown fields
 * (e.g. the `wear` package) are ignored by the parser, so the manifest can grow.
 */
@Serializable
data class UpdateManifest(
    val schema: String = "",
    /** "stable" for master builds, "dev" for prereleases. */
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

/** A release newer than the installed build, as offered by 关于与更新. */
@Serializable
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val tag: String,
    val publishedAt: String,
    val size: Long,
    val url: String,
    val sha256: String,
    val notes: List<String>,
    /** The GitHub Release is a prerelease (only offered on the 预览版 channel). */
    val prerelease: Boolean = false,
    /** The manifest's channel ("stable" / "dev"). */
    val channel: String = "stable",
    /** The Release's `.sha256` asset, checked in addition to [sha256] when present. */
    val sha256Url: String? = null,
)
