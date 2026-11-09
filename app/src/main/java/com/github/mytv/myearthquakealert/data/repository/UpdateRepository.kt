package com.github.mytv.myearthquakealert.data.repository

import android.os.Build
import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.data.model.UpdateChannel
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.update.ManifestCandidate
import com.github.mytv.myearthquakealert.update.ReleaseCandidate
import com.github.mytv.myearthquakealert.update.ReleaseSelection
import com.github.mytv.myearthquakealert.update.Selection
import com.github.mytv.myearthquakealert.update.UpdateException
import com.github.mytv.myearthquakealert.update.UpdateFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * GitHub Releases access for 应用内更新 (design §8 检查 / 渠道): the `update.json` manifests,
 * the Releases API for 预览版, the `.sha256` assets, and mirror ordering for every GitHub
 * URL (direct access is often slow or blocked in mainland China).
 */
class UpdateRepository(okHttpClient: OkHttpClient) {

    /** Client for the update pipeline; the APK download reads with a longer timeout. */
    val http: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Probe results per probe key, valid for [PROBE_TTL_MS]. */
    private val probeCache = mutableMapOf<String, Pair<Long, List<String>>>()

    /**
     * The newest release newer than this build on [channel], or null when up to date.
     * 稳定版 reads the latest formal Release's `update.json`; 预览版 also lists the releases
     * (prereleases included) and takes the highest versionCode. Fails with [UpdateException].
     */
    suspend fun checkForUpdate(channel: UpdateChannel): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val candidates = when (channel) {
                UpdateChannel.STABLE -> listOfNotNull(fetchLatestManifest())
                UpdateChannel.PREVIEW -> previewManifests()
            }
            if (candidates.isEmpty()) throw lastCheckFailure ?: UpdateException(UpdateFailure.SERVER)
            when (val selection = ReleaseSelection.pickNewest(candidates, BuildConfig.VERSION_CODE.toLong(), Build.VERSION.SDK_INT, RELEASES_URL)) {
                Selection.UpToDate -> Result.success(null)
                is Selection.Update -> Result.success(selection.info)
                is Selection.Incompatible -> throw UpdateException(UpdateFailure.MIN_SDK, selection.minSdk.toString())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            lastCheckFailure = null
        }
    }

    /** Reason of the last failed request of the running check (rate limit vs. no network). */
    @Volatile private var lastCheckFailure: UpdateException? = null

    /**
     * 预览版: the stable `latest` manifest plus the manifests of the newest releases with an
     * `update.json`. When the API is unreachable (or rate limited) the stable manifest alone
     * still answers, so the channel never offers less than 稳定版.
     */
    private suspend fun previewManifests(): List<ManifestCandidate> = coroutineScope {
        val latest = async { fetchLatestManifest() }
        val releases = fetchReleases()
        val fromReleases = ReleaseSelection.candidates(releases, UpdateChannel.PREVIEW)
            .map { candidate -> async { fetchManifest(candidate.manifestUrl, candidate) } }
            .awaitAll()
            .filterNotNull()
        fromReleases + listOfNotNull(latest.await())
    }

    private suspend fun fetchLatestManifest(): ManifestCandidate? =
        fetchManifest(MANIFEST_URL, release = null)

    private suspend fun fetchManifest(url: String, release: ReleaseCandidate?): ManifestCandidate? {
        for (mirror in mirrorOrder(url, PROBE_MANIFEST)) {
            val body = fetchText(mirrorUrl(mirror, url)) ?: continue
            val manifest = ReleaseSelection.parseManifest(body) ?: continue
            return ManifestCandidate(manifest, release)
        }
        return null
    }

    private suspend fun fetchReleases(): List<com.github.mytv.myearthquakealert.update.GithubRelease> {
        for (mirror in mirrorOrder(RELEASES_API_URL, PROBE_API)) {
            val body = fetchText(mirrorUrl(mirror, RELEASES_API_URL), api = true) ?: continue
            val releases = runCatching { ReleaseSelection.parseReleases(body) }.getOrNull() ?: continue
            return releases
        }
        return emptyList()
    }

    /** The hash published in the Release's `.sha256` asset, or null when it has none. */
    suspend fun fetchReleaseSha256(info: UpdateInfo): String? {
        val url = info.sha256Url ?: return null
        for (mirror in mirrorOrder(url, PROBE_MANIFEST)) {
            val body = fetchText(mirrorUrl(mirror, url)) ?: continue
            ReleaseSelection.parseChecksumFile(body, ReleaseSelection.apkName(info.url))?.let { return it }
        }
        return null
    }

    /** Mirror URLs of the APK, fastest first (probed against the APK itself). */
    suspend fun downloadUrls(url: String): List<String> = mirrorOrder(url, url).map { mirrorUrl(it, url) }.distinct()

    private suspend fun fetchText(url: String, api: Boolean = false): String? = withContext(Dispatchers.IO) {
        try {
            val call = http.newCall(
                Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", USER_AGENT)
                    .apply { if (api) header("Accept", "application/vnd.github+json") }
                    .build()
            )
            call.timeout().timeout(MANIFEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            call.execute().use { response ->
                when {
                    response.isSuccessful -> response.body?.string()
                    api && (response.code == 403 || response.code == 429) &&
                        (response.header("X-RateLimit-Remaining") == "0" || response.code == 429) -> {
                        lastCheckFailure = UpdateException(UpdateFailure.RATE_LIMITED)
                        null
                    }
                    else -> null
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (lastCheckFailure == null) lastCheckFailure = UpdateException(UpdateFailure.NETWORK)
            null
        }
    }

    /**
     * GitHub direct access often times out in mainland China: several acceleration mirrors
     * plus the origin are probed concurrently and used fastest first. Probes are cached for
     * five minutes per [probeKey]; unreachable routes stay at the end as a last resort.
     */
    private suspend fun mirrorOrder(target: String, probeKey: String): List<String> {
        val now = System.currentTimeMillis()
        synchronized(probeCache) {
            probeCache[probeKey]?.takeIf { now < it.first }?.let { return it.second }
        }
        return probeMirrors(target).also { order ->
            synchronized(probeCache) {
                probeCache[probeKey] = (now + PROBE_TTL_MS) to order
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
        const val USER_AGENT = "myEarthquakeAlert-Android"
        private const val PROBE_TTL_MS = 5 * 60 * 1000L
        private const val PROBE_TIMEOUT_MS = 3_000L
        private const val MANIFEST_TIMEOUT_MS = 15_000L
        private const val PROBE_MANIFEST = "manifest"
        private const val PROBE_API = "api"

        private const val REPO = "mytv-android/myEarthquakeAlert"
        const val RELEASES_URL = "https://github.com/$REPO/releases"
        const val MANIFEST_URL = "$RELEASES_URL/latest/download/${ReleaseSelection.MANIFEST_ASSET}"
        private const val RELEASES_API_URL = "https://api.github.com/repos/$REPO/releases?per_page=15"

        /** GitHub acceleration mirrors; the empty string is the origin. Same list as myMusic. */
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
