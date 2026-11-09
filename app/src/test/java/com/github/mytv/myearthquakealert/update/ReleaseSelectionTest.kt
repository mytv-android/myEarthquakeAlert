package com.github.mytv.myearthquakealert.update

import com.github.mytv.myearthquakealert.data.model.UpdateApk
import com.github.mytv.myearthquakealert.data.model.UpdateChannel
import com.github.mytv.myearthquakealert.data.model.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSelectionTest {

    private val base = "https://github.com/mytv-android/myEarthquakeAlert/releases"
    private val sha = "a".repeat(64)

    private val releasesJson = """
        [
          {"tag_name":"v1.0.40","draft":false,"prerelease":true,"published_at":"2026-11-08T10:00:00Z",
           "assets":[{"name":"app-release.apk","browser_download_url":"$base/download/v1.0.40/app-release.apk","size":10},
                     {"name":"update.json","browser_download_url":"$base/download/v1.0.40/update.json","size":1},
                     {"name":"app-release.apk.sha256","browser_download_url":"$base/download/v1.0.40/app-release.apk.sha256","size":1},
                     {"name":"update.apk.sha256","browser_download_url":"$base/download/v1.0.40/update.apk.sha256","size":1}],
           "author":{"login":"ci"},"body":"ignored"},
          {"tag_name":"v1.0.41","draft":true,"prerelease":false,"published_at":"2026-11-09T10:00:00Z",
           "assets":[{"name":"update.json","browser_download_url":"$base/download/v1.0.41/update.json"}]},
          {"tag_name":"v1.0.38","draft":false,"prerelease":false,"published_at":"2026-11-06T10:00:00Z",
           "assets":[{"name":"update.json","browser_download_url":"$base/download/v1.0.38/update.json"},
                     {"name":"update.apk.sha256","browser_download_url":"$base/download/v1.0.38/update.apk.sha256"}]},
          {"tag_name":"v1.0.30","draft":false,"prerelease":false,"published_at":"2026-10-01T10:00:00Z",
           "assets":[{"name":"app-release.apk","browser_download_url":"$base/download/v1.0.30/app-release.apk"}]}
        ]
    """.trimIndent()

    private fun manifest(code: Long, minSdk: Int = 23, channel: String = "stable", tag: String = "v1.0.$code") = UpdateManifest(
        schema = ReleaseSelection.SCHEMA,
        channel = channel,
        versionCode = code,
        versionName = "1.0",
        tag = tag,
        minSdk = minSdk,
        apk = UpdateApk("$base/download/$tag/app-release.apk", 100, sha),
    )

    @Test
    fun `stable candidates skip drafts, prereleases and releases without update json`() {
        val candidates = ReleaseSelection.candidates(ReleaseSelection.parseReleases(releasesJson), UpdateChannel.STABLE)
        assertEquals(listOf("v1.0.38"), candidates.map { it.tag })
    }

    @Test
    fun `preview candidates include prereleases, newest first`() {
        val candidates = ReleaseSelection.candidates(ReleaseSelection.parseReleases(releasesJson), UpdateChannel.PREVIEW)
        assertEquals(listOf("v1.0.40", "v1.0.38"), candidates.map { it.tag })
        assertTrue(candidates[0].prerelease)
        assertEquals("$base/download/v1.0.40/update.json", candidates[0].manifestUrl)
    }

    @Test
    fun `candidates are limited`() {
        val many = (1..10).joinToString(",", "[", "]") { i ->
            """{"tag_name":"v$i","published_at":"2026-01-${"%02d".format(i)}T00:00:00Z","assets":[{"name":"update.json","browser_download_url":"u$i"}]}"""
        }
        val candidates = ReleaseSelection.candidates(ReleaseSelection.parseReleases(many), UpdateChannel.STABLE, limit = 3)
        assertEquals(listOf("v10", "v9", "v8"), candidates.map { it.tag })
    }

    @Test
    fun `newest by versionCode wins and carries the prerelease flag and checksum asset`() {
        val releases = ReleaseSelection.candidates(ReleaseSelection.parseReleases(releasesJson), UpdateChannel.PREVIEW)
        val list = listOf(
            ManifestCandidate(manifest(38), releases[1]),
            ManifestCandidate(manifest(40, channel = "dev"), releases[0]),
            ManifestCandidate(manifest(38), null),
        )
        val selection = ReleaseSelection.pickNewest(list, installedCode = 35, sdkInt = 34, releaseBase = base)
        val info = (selection as Selection.Update).info
        assertEquals(40, info.versionCode)
        assertTrue(info.prerelease)
        assertEquals("dev", info.channel)
        // The apk-named checksum is preferred over the legacy update.apk.sha256.
        assertEquals("$base/download/v1.0.40/app-release.apk.sha256", info.sha256Url)
    }

    @Test
    fun `up to date when nothing is newer`() {
        val list = listOf(ManifestCandidate(manifest(38), null))
        assertEquals(Selection.UpToDate, ReleaseSelection.pickNewest(list, 38, 34, base))
        assertEquals(Selection.UpToDate, ReleaseSelection.pickNewest(emptyList(), 38, 34, base))
    }

    @Test
    fun `manifests without url or checksum are ignored`() {
        val broken = manifest(50).copy(apk = UpdateApk("", 0, ""))
        val list = listOf(ManifestCandidate(broken, null), ManifestCandidate(manifest(45), null))
        assertEquals(45, (ReleaseSelection.pickNewest(list, 40, 34, base) as Selection.Update).info.versionCode)
    }

    @Test
    fun `an older compatible release is offered when the newest needs a newer android`() {
        val list = listOf(ManifestCandidate(manifest(50, minSdk = 35), null), ManifestCandidate(manifest(45, minSdk = 23), null))
        assertEquals(45, (ReleaseSelection.pickNewest(list, 40, 30, base) as Selection.Update).info.versionCode)
        val onlyIncompatible = listOf(ManifestCandidate(manifest(50, minSdk = 35), null))
        assertEquals(Selection.Incompatible(35), ReleaseSelection.pickNewest(onlyIncompatible, 40, 30, base))
    }

    @Test
    fun `stable latest manifest derives the checksum url from its tag`() {
        val info = (ReleaseSelection.pickNewest(listOf(ManifestCandidate(manifest(42), null)), 1, 34, base) as Selection.Update).info
        assertEquals("$base/download/v1.0.42/update.apk.sha256", info.sha256Url)
        assertFalse(info.prerelease)
    }

    @Test
    fun `manifest parsing ignores unknown fields such as the wear package`() {
        val body = """
            {"schema":"myearthquakealert.update","channel":"stable","versionCode":42,"versionName":"1.0","tag":"v1.0.42",
             "publishedAt":"2026-11-08T00:00:00Z","minSdk":23,
             "apk":{"url":"$base/download/v1.0.42/app-release.apk","size":123,"sha256":"$sha"},
             "wear":{"url":"$base/download/v1.0.42/myEarthquakeAlert-wear-1.0.42.apk","size":45,"sha256":"$sha"},
             "notes":["Fixes"]}
        """.trimIndent()
        val manifest = ReleaseSelection.parseManifest(body)!!
        assertEquals(42, manifest.versionCode)
        assertEquals(123, manifest.apk.size)
        assertEquals(listOf("Fixes"), manifest.notes)
    }

    @Test
    fun `manifest with another schema or invalid json is rejected`() {
        assertNull(ReleaseSelection.parseManifest("""{"schema":"other","versionCode":1}"""))
        assertNull(ReleaseSelection.parseManifest("<html>rate limited</html>"))
    }

    @Test
    fun `checksum files in sha256sum format`() {
        val b = "b".repeat(64)
        assertEquals(sha, ReleaseSelection.parseChecksumFile("$sha  app-release.apk\n", "app-release.apk"))
        assertEquals(sha, ReleaseSelection.parseChecksumFile(sha.uppercase(), null))
        assertEquals(b, ReleaseSelection.parseChecksumFile("$sha  other.apk\n$b *app-release.apk\n", "app-release.apk"))
        assertNull(ReleaseSelection.parseChecksumFile("$sha  a.apk\n$b  b.apk", "c.apk"))
        assertNull(ReleaseSelection.parseChecksumFile("not a hash", null))
        assertEquals(sha, ReleaseSelection.parseChecksumFile("$sha  renamed.apk", "app-release.apk"))
    }

    @Test
    fun `apk name from url`() {
        assertEquals("app-release.apk", ReleaseSelection.apkName("$base/download/v1/app-release.apk?x=1"))
        assertNull(ReleaseSelection.apkName("$base/download/v1/update.json"))
    }
}
