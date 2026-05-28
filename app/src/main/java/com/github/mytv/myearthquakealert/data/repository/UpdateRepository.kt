package com.github.mytv.myearthquakealert.data.repository

import android.content.Context
import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.data.api.GitHubApi
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.util.isNewerVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class UpdateRepository(
    private val githubApi: GitHubApi,
    private val okHttpClient: OkHttpClient,
    private val context: Context,
) {
    suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val release = githubApi.getLatestRelease()
            val remoteVersion = release.tagName.removePrefix("v")
            val localVersion = BuildConfig.VERSION_NAME

            if (!isNewerVersion(remoteVersion, localVersion)) {
                return@withContext Result.success(null)
            }

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@withContext Result.failure(Exception("No APK found in release"))

            val updateInfo = UpdateInfo(
                version = remoteVersion,
                versionCode = 0,
                releaseNotes = release.body,
                apkUrl = apkAsset.browserDownloadUrl,
                apkSize = apkAsset.size,
                publishedAt = release.publishedAt,
            )

            Result.success(updateInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(
        url: String,
        onProgress: (progress: Int, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: ${response.code}"))
            }

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

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
