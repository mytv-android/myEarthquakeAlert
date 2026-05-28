package com.github.mytv.myearthquakealert.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class UpdateInfo(
    val version: String,
    val versionCode: Int,
    val releaseNotes: String,
    val apkUrl: String,
    val apkSize: Long,
    val publishedAt: String,
)

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
