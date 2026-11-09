package com.github.mytv.myearthquakealert.update

import kotlinx.serialization.Serializable

/**
 * Sidecar of a partial download (`<apk>.part.json`): which release the bytes belong to and
 * the validators of the response that produced them, for `If-Range` on the next resume.
 */
@Serializable
data class PartMeta(
    val versionCode: Long,
    val sha256: String,
    /** URL (mirror) the stored validators came from. */
    val source: String,
    val etag: String? = null,
    val lastModified: String? = null,
    val total: Long = 0,
) {
    fun belongsTo(versionCode: Long, sha256: String): Boolean =
        this.versionCode == versionCode && this.sha256.equals(sha256, ignoreCase = true)
}

/** `Content-Range: bytes <start>-<end>/<total>` (or `bytes * /<total>` on 416). */
data class ContentRange(val start: Long?, val end: Long?, val total: Long?)

/** What to do with a response to a (possibly ranged) download request. */
sealed interface ResumeDecision {
    /** 206 continuing exactly where the part file ends: append the body. */
    data class Append(val total: Long) : ResumeDecision
    /** 200 (validator changed, or no range sent): truncate the part and write the body from 0. */
    data class Restart(val total: Long) : ResumeDecision
    /** 416 and the part already holds the whole file. */
    data class Complete(val total: Long) : ResumeDecision
    /** The server answered a range we did not ask for: drop the part and ask again without one. */
    data object Reset : ResumeDecision
    /** The body is not the size the manifest announces (e.g. a mirror's error page). */
    data object SizeMismatch : ResumeDecision
    data class Fail(val code: Int) : ResumeDecision
}

/**
 * HTTP Range bookkeeping for [ApkDownloader] (design §8 下载: 断点续传). Pure, unit tested.
 */
object ResumePlan {

    /**
     * Headers for a request when [partLength] bytes are already on disk. `If-Range` is only
     * sent with validators that came from the same [url]; a strong ETag is preferred (weak
     * ETags are not allowed in If-Range), Last-Modified otherwise. When the mirror changed,
     * the range is sent unconditionally: the total size is still checked against the
     * manifest and the whole file is verified with SHA-256 afterwards.
     */
    fun requestHeaders(partLength: Long, meta: PartMeta?, url: String): Map<String, String> {
        if (partLength <= 0) return emptyMap()
        val headers = linkedMapOf("Range" to "bytes=$partLength-")
        if (meta != null && meta.source == url) {
            val etag = meta.etag?.takeIf { it.isNotBlank() && !it.startsWith("W/") }
            val validator = etag ?: meta.lastModified?.takeIf { it.isNotBlank() }
            if (validator != null) headers["If-Range"] = validator
        }
        return headers
    }

    /**
     * Interprets the response [code] given the [partLength] that was requested from.
     * [expectedTotal] is the manifest size (0 when unknown); [contentLength] the body length.
     */
    fun decide(code: Int, partLength: Long, contentRange: String?, contentLength: Long, expectedTotal: Long): ResumeDecision {
        return when (code) {
            200 -> {
                val total = if (contentLength > 0) contentLength else expectedTotal
                if (expectedTotal > 0 && contentLength > 0 && contentLength != expectedTotal) {
                    ResumeDecision.SizeMismatch
                } else {
                    ResumeDecision.Restart(total)
                }
            }
            206 -> {
                val range = parseContentRange(contentRange) ?: return ResumeDecision.Reset
                if (partLength <= 0 || range.start != partLength) return ResumeDecision.Reset
                val total = range.total ?: expectedTotal
                if (expectedTotal > 0 && total > 0 && total != expectedTotal) return ResumeDecision.Reset
                ResumeDecision.Append(total)
            }
            416 -> {
                val total = parseContentRange(contentRange)?.total ?: expectedTotal
                if (partLength > 0 && total > 0 && partLength == total && (expectedTotal <= 0 || total == expectedTotal)) {
                    ResumeDecision.Complete(total)
                } else {
                    ResumeDecision.Reset
                }
            }
            else -> ResumeDecision.Fail(code)
        }
    }

    fun parseContentRange(header: String?): ContentRange? {
        val value = header?.trim() ?: return null
        if (!value.startsWith("bytes", ignoreCase = true)) return null
        val spec = value.substring(5).trim()
        val slash = spec.indexOf('/')
        if (slash < 0) return null
        val rangePart = spec.substring(0, slash).trim()
        val totalPart = spec.substring(slash + 1).trim()
        val total = if (totalPart == "*") null else totalPart.toLongOrNull() ?: return null
        if (rangePart == "*") return ContentRange(null, null, total)
        val dash = rangePart.indexOf('-')
        if (dash <= 0) return null
        val start = rangePart.substring(0, dash).toLongOrNull() ?: return null
        val end = rangePart.substring(dash + 1).toLongOrNull() ?: return null
        if (end < start) return null
        return ContentRange(start, end, total)
    }
}
