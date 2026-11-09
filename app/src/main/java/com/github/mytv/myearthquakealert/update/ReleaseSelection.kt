package com.github.mytv.myearthquakealert.update

import com.github.mytv.myearthquakealert.data.model.UpdateChannel
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.data.model.UpdateManifest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One entry of `GET /repos/{owner}/{repo}/releases` (only the fields the channels need). */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
)

/** A release whose `update.json` is worth fetching. */
data class ReleaseCandidate(
    val tag: String,
    val prerelease: Boolean,
    val manifestUrl: String,
    /** `.sha256` assets of the release by name. */
    val checksumAssets: Map<String, String>,
)

/** A fetched manifest together with the release it came from (null: the `latest` shortcut). */
data class ManifestCandidate(val manifest: UpdateManifest, val release: ReleaseCandidate?)

/** Outcome of comparing the candidates with the installed build. */
sealed interface Selection {
    data object UpToDate : Selection
    data class Update(val info: UpdateInfo) : Selection
    /** Only releases that need a newer Android than this device are newer. */
    data class Incompatible(val minSdk: Int) : Selection
}

/**
 * The update channels (design §8 渠道): 稳定版 only looks at the latest formal Release,
 * 预览版 also at prereleases. Everything here is pure, so it is unit tested.
 */
object ReleaseSelection {
    const val SCHEMA = "myearthquakealert.update"
    const val MANIFEST_ASSET = "update.json"
    const val LEGACY_CHECKSUM_ASSET = "update.apk.sha256"

    /** How many of the newest releases have their `update.json` fetched on 预览版. */
    const val MAX_CANDIDATES = 4

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun parseReleases(body: String): List<GithubRelease> = json.decodeFromString(body)

    fun parseManifest(body: String): UpdateManifest? =
        runCatching { json.decodeFromString<UpdateManifest>(body) }.getOrNull()?.takeIf { it.schema == SCHEMA }

    /**
     * Releases that can carry an update on [channel], newest first: never drafts, prereleases
     * only on [UpdateChannel.PREVIEW], and only releases that publish an `update.json`.
     */
    fun candidates(releases: List<GithubRelease>, channel: UpdateChannel, limit: Int = MAX_CANDIDATES): List<ReleaseCandidate> =
        releases.asSequence()
            .filter { !it.draft }
            .filter { channel == UpdateChannel.PREVIEW || !it.prerelease }
            .mapNotNull { release ->
                val manifest = release.assets.firstOrNull { it.name == MANIFEST_ASSET && it.url.isNotBlank() } ?: return@mapNotNull null
                ReleaseCandidate(
                    tag = release.tagName,
                    prerelease = release.prerelease,
                    manifestUrl = manifest.url,
                    checksumAssets = release.assets
                        .filter { it.name.endsWith(".sha256") && it.url.isNotBlank() }
                        .associate { it.name to it.url },
                ) to (release.publishedAt ?: "")
            }
            // ISO-8601 UTC timestamps sort lexicographically.
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
            .toList()

    /**
     * The newest usable manifest that is newer than [installedCode], by versionCode. A newer
     * manifest that needs a higher [sdkInt] is skipped in favour of an older compatible one;
     * when only such manifests are newer the result is [Selection.Incompatible].
     */
    fun pickNewest(candidates: List<ManifestCandidate>, installedCode: Long, sdkInt: Int, releaseBase: String): Selection {
        val newer = candidates
            .filter { it.manifest.schema == SCHEMA }
            .filter { it.manifest.versionCode > installedCode }
            .filter { it.manifest.apk.url.isNotBlank() && it.manifest.apk.sha256.isNotBlank() }
            .sortedByDescending { it.manifest.versionCode }
        if (newer.isEmpty()) return Selection.UpToDate
        val compatible = newer.firstOrNull { it.manifest.minSdk <= 0 || sdkInt >= it.manifest.minSdk }
            ?: return Selection.Incompatible(newer.minOf { it.manifest.minSdk })
        return Selection.Update(toInfo(compatible, releaseBase))
    }

    fun toInfo(candidate: ManifestCandidate, releaseBase: String): UpdateInfo {
        val m = candidate.manifest
        val release = candidate.release
        return UpdateInfo(
            versionCode = m.versionCode,
            versionName = m.versionName,
            tag = m.tag.ifBlank { release?.tag.orEmpty() },
            publishedAt = m.publishedAt,
            size = m.apk.size,
            url = m.apk.url,
            sha256 = m.apk.sha256.lowercase(),
            notes = m.notes,
            prerelease = release?.prerelease ?: (m.channel != "stable"),
            channel = m.channel,
            sha256Url = checksumUrl(m, release, releaseBase),
        )
    }

    /**
     * The Release's checksum asset: `<apk name>.sha256` when published, else the CI's
     * `update.apk.sha256`. Without the release listing (stable `latest` path) the URL is
     * derived from the manifest's tag.
     */
    fun checksumUrl(manifest: UpdateManifest, release: ReleaseCandidate?, releaseBase: String): String? {
        val apkName = apkName(manifest.apk.url)
        if (release != null) {
            return apkName?.let { release.checksumAssets["$it.sha256"] }
                ?: release.checksumAssets[LEGACY_CHECKSUM_ASSET]
        }
        val tag = manifest.tag.takeIf { it.isNotBlank() } ?: return null
        return "$releaseBase/download/$tag/$LEGACY_CHECKSUM_ASSET"
    }

    fun apkName(url: String): String? =
        url.substringBefore('?').substringAfterLast('/').takeIf { it.endsWith(".apk") }

    /**
     * Reads a `sha256sum` style file ("<hex>  <name>" lines, or a bare hash). With several
     * lines the one naming [fileName] wins; a single hash line is accepted as is.
     */
    fun parseChecksumFile(body: String, fileName: String?): String? {
        val entries = body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val hash = line.substringBefore(' ').substringBefore('\t').lowercase()
                if (!HEX64.matches(hash)) return@mapNotNull null
                hash to line.substring(hash.length).trim().removePrefix("*").trim()
            }
            .toList()
        if (entries.isEmpty()) return null
        if (fileName != null) entries.firstOrNull { it.second == fileName }?.let { return it.first }
        return entries.singleOrNull()?.first
    }

    private val HEX64 = Regex("[0-9a-f]{64}")
}
