package com.github.mytv.myearthquakealert.data.repository

import android.content.Context
import android.os.Build
import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.data.model.UpdateManifest
import com.github.mytv.myearthquakealert.util.ApkInstaller
import com.github.mytv.myearthquakealert.util.InstallResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class UpdateRepository(
    private val context: Context,
    okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val http = okHttpClient.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 线路测速结果按目标地址缓存 5 分钟，清单检查和下载各用各的。 */
    private val probeCache = mutableMapOf<String, Pair<Long, List<String>>>()

    suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val manifest = fetchManifest() ?: throw IOException("Unable to reach the update server")
            if (manifest.minSdk > 0 && Build.VERSION.SDK_INT < manifest.minSdk) {
                throw IOException("This update requires Android ${manifest.minSdk} or newer")
            }
            if (manifest.versionCode <= BuildConfig.VERSION_CODE.toLong()) {
                return@withContext Result.success(null)
            }
            if (manifest.apk.url.isBlank() || manifest.apk.sha256.isBlank()) {
                throw IOException("Update manifest is missing the APK URL or checksum")
            }
            Result.success(
                UpdateInfo(
                    versionCode = manifest.versionCode,
                    versionName = manifest.versionName,
                    tag = manifest.tag,
                    publishedAt = manifest.publishedAt,
                    size = manifest.apk.size,
                    url = manifest.apk.url,
                    sha256 = manifest.apk.sha256,
                    notes = manifest.notes,
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun downloadApk(
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "myEarthquakeAlert-${info.versionCode}.apk")
        target.delete()
        if (info.size > 0 && directory.usableSpace < info.size + 8L * 1024 * 1024) {
            return@withContext Result.failure(
                IOException("Not enough storage, at least ${ApkInstaller.formatFileSize(info.size)} is required")
            )
        }
        try {
            var lastError: Exception? = null
            for (mirror in mirrorOrder(info.url)) {
                try {
                    downloadFrom(mirrorUrl(mirror, info.url), target, info, onProgress)
                    lastError = null
                    break
                } catch (error: CancellationException) {
                    target.delete()
                    throw error
                } catch (error: Exception) {
                    target.delete()
                    lastError = error
                }
            }
            if (lastError != null || !target.exists()) {
                throw lastError ?: IOException("Download failed")
            }
            verifyApk(target, info)
            Result.success(target)
        } catch (error: CancellationException) {
            target.delete()
            throw error
        } catch (error: Exception) {
            target.delete()
            Result.failure(error)
        }
    }

    suspend fun install(file: File): InstallResult = ApkInstaller.installApk(context, file)

    private suspend fun downloadFrom(
        url: String,
        target: File,
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            throw IOException("Download failed (HTTP ${resp.code})")
                        }
                        val body = resp.body ?: throw IOException("Empty update response")
                        val total = info.size.takeIf { it > 0 } ?: body.contentLength()
                        val digest = MessageDigest.getInstance("SHA-256")
                        var copied = 0L
                        var lastReportAt = 0L
                        val buffer = ByteArray(64 * 1024)
                        target.outputStream().use { output ->
                            body.byteStream().use { input ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    digest.update(buffer, 0, read)
                                    copied += read
                                    val now = System.nanoTime()
                                    if (now - lastReportAt >= PROGRESS_INTERVAL_NS ||
                                        (total > 0 && copied >= total)
                                    ) {
                                        lastReportAt = now
                                        onProgress(copied, total)
                                    }
                                }
                            }
                        }
                        if (info.size > 0 && copied != info.size) {
                            throw IOException("Downloaded size does not match the update manifest")
                        }
                        val actual = digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
                        if (!actual.equals(info.sha256, ignoreCase = true)) {
                            throw IOException("Update package SHA-256 verification failed")
                        }
                    }
                    continuation.resume(Unit)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(file: File, info: UpdateInfo) {
        if (info.versionCode <= BuildConfig.VERSION_CODE.toLong()) {
            throw IOException("The update is not newer than the installed version")
        }
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IOException("Unable to read the update package")
        if (archive.packageName != context.packageName) {
            throw IOException("The update package does not belong to this app")
        }
        val archiveCode = if (Build.VERSION.SDK_INT >= 28) {
            archive.longVersionCode
        } else {
            archive.versionCode.toLong()
        }
        if (archiveCode != info.versionCode) {
            throw IOException("The update package version does not match the manifest")
        }
    }

    private suspend fun fetchManifest(): UpdateManifest? {
        for (mirror in mirrorOrder(MANIFEST_URL)) {
            val body = fetchText(mirrorUrl(mirror, MANIFEST_URL)) ?: continue
            val manifest = runCatching { json.decodeFromString<UpdateManifest>(body) }.getOrNull()
                ?: continue
            if (manifest.schema == SCHEMA) return manifest
        }
        return null
    }

    private suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val call = http.newCall(
                Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", USER_AGENT)
                    .build()
            )
            call.timeout().timeout(MANIFEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            call.execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    /**
     * GitHub 直连在国内经常超时，内置多个加速镜像 + 源站直连兜底。
     * 并发测速后按耗时升序使用，测速结果缓存 5 分钟；不可达的线路留在末尾作为兜底。
     */
    private suspend fun mirrorOrder(target: String): List<String> {
        val now = System.currentTimeMillis()
        synchronized(probeCache) {
            probeCache[target]?.takeIf { now < it.first }?.let { return it.second }
        }
        return probeMirrors(target).also { order ->
            synchronized(probeCache) {
                probeCache[target] = (now + PROBE_TTL_MS) to order
            }
        }
    }

    private suspend fun probeMirrors(target: String): List<String> = coroutineScope {
        val results = MIRRORS.map { mirror ->
            async(Dispatchers.IO) {
                val startedAt = System.nanoTime()
                val reachable = try {
                    val call = http.newCall(
                        Request.Builder()
                            .url(mirrorUrl(mirror, target))
                            .head()
                            .header("User-Agent", USER_AGENT)
                            .build()
                    )
                    call.timeout().timeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    call.execute().use { it.isSuccessful || it.code == 405 }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
                mirror to if (reachable) System.nanoTime() - startedAt else Long.MAX_VALUE
            }
        }.awaitAll()
        results.sortedWith(compareBy({ it.second }, { it.first })).map { it.first }
    }

    private fun mirrorUrl(mirror: String, target: String): String =
        if (mirror.isEmpty()) target else mirror + target

    companion object {
        private const val SCHEMA = "myearthquakealert.update"
        private const val USER_AGENT = "myEarthquakeAlert-Android"
        private const val PROBE_TTL_MS = 5 * 60 * 1000L
        private const val PROBE_TIMEOUT_MS = 3_000L
        private const val MANIFEST_TIMEOUT_MS = 15_000L
        private const val PROGRESS_INTERVAL_NS = 200_000_000L

        const val MANIFEST_URL =
            "https://github.com/mytv-android/myEarthquakeAlert/releases/latest/download/update.json"
        const val RELEASES_URL = "https://github.com/mytv-android/myEarthquakeAlert/releases"

        /** GitHub 加速镜像；空字符串表示源站直连。与 myMusic 的列表保持一致。 */
        private val MIRRORS = listOf(
            "https://gh-proxy.org/",
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
            "https://gh.cc/",
            "https://mirror.ghproxy.com/",
            "https://github.moeyy.xyz/",
            "https://ghproxy.cn/",
            "https://gh.api.99988866.xyz/",
            "https://gh-proxy.ygxz.in/",
            "",
        )
    }
}
