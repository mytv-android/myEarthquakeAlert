package com.github.mytv.myearthquakealert.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumePlanTest {

    private val url = "https://github.com/x/releases/download/v1/app.apk"
    private val meta = PartMeta(1, "abc", url, etag = "\"e1\"", lastModified = "Sat, 07 Nov 2026 10:00:00 GMT", total = 1000)

    @Test
    fun `no range for an empty part`() {
        assertTrue(ResumePlan.requestHeaders(0, meta, url).isEmpty())
    }

    @Test
    fun `range with a strong etag as If-Range`() {
        val headers = ResumePlan.requestHeaders(400, meta, url)
        assertEquals("bytes=400-", headers["Range"])
        assertEquals("\"e1\"", headers["If-Range"])
    }

    @Test
    fun `weak etags fall back to Last-Modified`() {
        val headers = ResumePlan.requestHeaders(400, meta.copy(etag = "W/\"e1\""), url)
        assertEquals("Sat, 07 Nov 2026 10:00:00 GMT", headers["If-Range"])
    }

    @Test
    fun `no If-Range for validators of another mirror or without validators`() {
        assertNull(ResumePlan.requestHeaders(400, meta, "https://mirror.example/$url")["If-Range"])
        assertNull(ResumePlan.requestHeaders(400, meta.copy(etag = null, lastModified = null), url)["If-Range"])
        assertEquals("bytes=400-", ResumePlan.requestHeaders(400, null, url)["Range"])
    }

    @Test
    fun `206 continuing at the part end appends`() {
        assertEquals(ResumeDecision.Append(1000), ResumePlan.decide(206, 400, "bytes 400-999/1000", 600, 1000))
    }

    @Test
    fun `206 at another offset or of another size resets`() {
        assertEquals(ResumeDecision.Reset, ResumePlan.decide(206, 400, "bytes 0-999/1000", 1000, 1000))
        assertEquals(ResumeDecision.Reset, ResumePlan.decide(206, 400, "bytes 400-1999/2000", 1600, 1000))
        assertEquals(ResumeDecision.Reset, ResumePlan.decide(206, 400, null, 600, 1000))
    }

    @Test
    fun `200 restarts from byte zero`() {
        assertEquals(ResumeDecision.Restart(1000), ResumePlan.decide(200, 400, null, 1000, 1000))
        assertEquals(ResumeDecision.Restart(1000), ResumePlan.decide(200, 0, null, -1, 1000))
        assertEquals(ResumeDecision.Restart(777), ResumePlan.decide(200, 0, null, 777, 0))
    }

    @Test
    fun `200 of another size is a mismatch`() {
        assertEquals(ResumeDecision.SizeMismatch, ResumePlan.decide(200, 0, null, 5000, 1000))
    }

    @Test
    fun `416 with a complete part is complete, otherwise reset`() {
        assertEquals(ResumeDecision.Complete(1000), ResumePlan.decide(416, 1000, "bytes */1000", 0, 1000))
        assertEquals(ResumeDecision.Reset, ResumePlan.decide(416, 1200, "bytes */1000", 0, 1000))
        assertEquals(ResumeDecision.Reset, ResumePlan.decide(416, 1000, "bytes */1000", 0, 900))
    }

    @Test
    fun `other codes fail`() {
        assertEquals(ResumeDecision.Fail(404), ResumePlan.decide(404, 0, null, 0, 1000))
        assertEquals(ResumeDecision.Fail(503), ResumePlan.decide(503, 400, null, 0, 1000))
    }

    @Test
    fun `content range parsing`() {
        assertEquals(ContentRange(400, 999, 1000), ResumePlan.parseContentRange("bytes 400-999/1000"))
        assertEquals(ContentRange(0, 9, null), ResumePlan.parseContentRange("bytes 0-9/*"))
        assertEquals(ContentRange(null, null, 1000), ResumePlan.parseContentRange("bytes */1000"))
        assertNull(ResumePlan.parseContentRange("items 0-9/10"))
        assertNull(ResumePlan.parseContentRange("bytes 9-0/10"))
        assertNull(ResumePlan.parseContentRange("bytes 0-9"))
        assertNull(ResumePlan.parseContentRange(null))
    }

    @Test
    fun `part meta identity`() {
        assertTrue(meta.belongsTo(1, "ABC"))
        assertTrue(!meta.belongsTo(2, "abc"))
    }
}
