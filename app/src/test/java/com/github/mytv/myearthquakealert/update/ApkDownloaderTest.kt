package com.github.mytv.myearthquakealert.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApkDownloaderTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val downloader = ApkDownloader(OkHttpClient(), "test")
    private val content = ByteArray(10_000) { (it % 251).toByte() }
    private val sha = Sha256.of(content)
    private val target = ApkDownloader.Target(7, sha, content.size.toLong())
    private lateinit var part: File
    private lateinit var meta: File

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        part = File(tmp.root, "a.apk.part")
        meta = File(tmp.root, "a.apk.part.json")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun body(bytes: ByteArray) = Buffer().write(bytes)

    private fun url(path: String = "/app.apk") = server.url(path).toString()

    private fun writeMeta(source: String, etag: String?) {
        meta.writeText("""{"versionCode":7,"sha256":"$sha","source":"$source","etag":${etag?.let { "\"${it.replace("\"", "\\\"")}\"" } ?: "null"},"total":${content.size}}""")
    }

    @Test
    fun `fresh download writes the whole file and the validators`() = runBlocking {
        server.enqueue(MockResponse().setBody(body(content)).setHeader("ETag", "\"v1\""))
        downloader.download(listOf(url()), part, meta, target) { _, _ -> }
        assertArrayEquals(content, part.readBytes())
        assertNull(server.takeRequest().getHeader("Range"))
        assertTrue(meta.readText().contains("v1"))
    }

    @Test
    fun `resume sends Range and If-Range and appends the 206 body`() = runBlocking {
        part.writeBytes(content.copyOfRange(0, 4000))
        writeMeta(url(), "\"v1\"")
        server.enqueue(
            MockResponse().setResponseCode(206)
                .setHeader("Content-Range", "bytes 4000-9999/10000")
                .setHeader("ETag", "\"v1\"")
                .setBody(body(content.copyOfRange(4000, content.size)))
        )
        var last = 0L
        downloader.download(listOf(url()), part, meta, target) { d, _ -> last = d }
        val request = server.takeRequest()
        assertEquals("bytes=4000-", request.getHeader("Range"))
        assertEquals("\"v1\"", request.getHeader("If-Range"))
        assertArrayEquals(content, part.readBytes())
        assertEquals(content.size.toLong(), last)
    }

    @Test
    fun `a 200 answer to a range restarts cleanly`() = runBlocking {
        part.writeBytes(ByteArray(4000) { 1 })
        writeMeta(url(), "\"old\"")
        server.enqueue(MockResponse().setBody(body(content)).setHeader("ETag", "\"new\""))
        downloader.download(listOf(url()), part, meta, target) { _, _ -> }
        assertArrayEquals(content, part.readBytes())
        assertTrue(meta.readText().contains("new"))
    }

    @Test
    fun `a part of another release is discarded before downloading`() = runBlocking {
        part.writeBytes(ByteArray(4000) { 1 })
        meta.writeText("""{"versionCode":6,"sha256":"$sha","source":"${url()}"}""")
        server.enqueue(MockResponse().setBody(body(content)))
        downloader.download(listOf(url()), part, meta, target) { _, _ -> }
        assertNull(server.takeRequest().getHeader("Range"))
        assertArrayEquals(content, part.readBytes())
    }

    @Test
    fun `a failing mirror hands over to the next one, which resumes without If-Range`() = runBlocking {
        part.writeBytes(content.copyOfRange(0, 3000))
        writeMeta(url("/m1/app.apk"), "\"v1\"")
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(
            MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3000-9999/10000")
                .setBody(body(content.copyOfRange(3000, content.size)))
        )
        downloader.download(listOf(url("/m1/app.apk"), url("/m2/app.apk")), part, meta, target) { _, _ -> }
        val first = server.takeRequest()
        assertEquals("\"v1\"", first.getHeader("If-Range"))
        val second = server.takeRequest()
        assertEquals("/m2/app.apk", second.path)
        assertEquals("bytes=3000-", second.getHeader("Range"))
        // The validators belong to the first mirror; the size and SHA-256 still guard the result.
        assertNull(second.getHeader("If-Range"))
        assertArrayEquals(content, part.readBytes())
    }

    @Test
    fun `a dropped connection keeps the download going on the next mirror`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(body(content)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        server.enqueue(MockResponse().setBody(body(content)))
        downloader.download(listOf(url("/m1/app.apk"), url("/m2/app.apk")), part, meta, target) { _, _ -> }
        assertEquals("/m1/app.apk", server.takeRequest().path)
        assertEquals("/m2/app.apk", server.takeRequest().path)
        assertArrayEquals(content, part.readBytes())
    }

    @Test
    fun `416 with a complete part is accepted`() = runBlocking {
        part.writeBytes(content)
        writeMeta(url(), "\"v1\"")
        val unknownSize = target.copy(size = 0)
        server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */10000"))
        downloader.download(listOf(url()), part, meta, unknownSize) { _, _ -> }
        assertArrayEquals(content, part.readBytes())
    }

    @Test
    fun `an html error page is never written into the part`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>busy</html>").setHeader("Content-Type", "text/html"))
        try {
            downloader.download(listOf(url()), part, meta, target) { _, _ -> }
            fail("expected a failure")
        } catch (e: UpdateException) {
            assertEquals(UpdateFailure.INCOMPLETE, e.failure)
        }
        assertFalse(part.exists())
    }

    @Test
    fun `http errors of every mirror surface as HTTP failures`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        try {
            downloader.download(listOf(url("/a"), url("/b")), part, meta, target) { _, _ -> }
            fail("expected a failure")
        } catch (e: UpdateException) {
            assertEquals(UpdateFailure.HTTP, e.failure)
            assertEquals("404", e.detail)
        }
    }

    @Test
    fun `sha256 of the downloaded file matches`() = runBlocking {
        server.enqueue(MockResponse().setBody(body(content)))
        downloader.download(listOf(url()), part, meta, target) { _, _ -> }
        assertNull(Sha256.check(Sha256.of(part), sha, null))
        assertEquals(UpdateFailure.SHA256, Sha256.check(Sha256.of(part), "0".repeat(64), null))
        assertEquals(UpdateFailure.SHA256, Sha256.check(Sha256.of(part), sha, "0".repeat(64)))
        assertNull(Sha256.check(Sha256.of(part), sha.uppercase(), sha))
    }
}
