package com.github.mytv.myearthquakealert.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Resumable APK download (design §8 下载). Bytes go to [part]; [meta] remembers which
 * release they belong to and the response validators, so a later run continues with
 * `Range` + `If-Range`. A 200 answer (validator changed, or the server ignores ranges)
 * restarts cleanly from byte 0. Each URL in turn is a mirror of the same asset; a mirror
 * that fails hands over to the next one, keeping what was downloaded.
 *
 * Plain JVM code (no Android types), unit tested with MockWebServer.
 */
class ApkDownloader(private val http: OkHttpClient, private val userAgent: String) {

    /** The file being downloaded: identity and size from the manifest (size 0 = unknown). */
    data class Target(val versionCode: Long, val sha256: String, val size: Long)

    fun interface Progress {
        fun onProgress(downloaded: Long, total: Long)
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Downloads [target] into [part] from the first of [urls] that works. Returns when the part
     * holds the complete file (its content is verified by the caller). Throws
     * [UpdateException] (or the last [IOException]) when every mirror failed, and
     * [CancellationException] when stopped — the part is kept for the next run.
     */
    suspend fun download(urls: List<String>, part: File, meta: File, target: Target, progress: Progress) {
        require(urls.isNotEmpty())
        part.parentFile?.mkdirs()
        discardForeignPart(part, meta, target)
        var lastError: IOException? = null
        for (url in urls) {
            coroutineContext.ensureActive()
            try {
                fetch(url, part, meta, target, progress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateException) {
                if (!e.failure.retryable && e.failure != UpdateFailure.HTTP) throw e
                lastError = e
            } catch (e: IOException) {
                coroutineContext.ensureActive()
                // A reason from a server beats a bare connection error from another mirror.
                if (lastError !is UpdateException) lastError = e
            }
        }
        throw lastError ?: IOException("download failed")
    }

    /** Drops a part file left by another release (or one without a readable sidecar). */
    private fun discardForeignPart(part: File, meta: File, target: Target) {
        if (!part.exists()) {
            meta.delete()
            return
        }
        val stored = readMeta(meta)
        val tooLong = target.size > 0 && part.length() > target.size
        if (stored == null || !stored.belongsTo(target.versionCode, target.sha256) || tooLong) {
            part.delete()
            meta.delete()
        }
    }

    private suspend fun fetch(url: String, part: File, meta: File, target: Target, progress: Progress) {
        var resets = 0
        while (true) {
            var partLength = if (part.exists()) part.length() else 0L
            if (target.size > 0 && partLength > target.size) {
                part.delete()
                meta.delete()
                partLength = 0L
            }
            if (target.size > 0 && partLength == target.size) {
                progress.onProgress(partLength, target.size)
                return
            }
            val stored = readMeta(meta)
            val request = Request.Builder().url(url).header("User-Agent", userAgent).apply {
                ResumePlan.requestHeaders(partLength, stored, url).forEach { (k, v) -> header(k, v) }
            }.build()
            val outcome = execute(request) { response ->
                val decision = ResumePlan.decide(
                    code = response.code,
                    partLength = partLength,
                    contentRange = response.header("Content-Range"),
                    contentLength = response.body?.contentLength() ?: -1L,
                    expectedTotal = target.size,
                )
                if ((decision is ResumeDecision.Append || decision is ResumeDecision.Restart) && isErrorPage(response)) {
                    // A mirror's HTML error page served with 200: never mix it into the part.
                    throw UpdateException(UpdateFailure.INCOMPLETE)
                }
                when (decision) {
                    is ResumeDecision.Append, is ResumeDecision.Restart -> {
                        val append = decision is ResumeDecision.Append
                        val total = (decision as? ResumeDecision.Append)?.total ?: (decision as ResumeDecision.Restart).total
                        if (!append) part.delete()
                        // Validators of the response that now produced (or continues) the part.
                        val etag = response.header("ETag")
                        val lastModified = response.header("Last-Modified")
                        val keep = if (append && stored != null && stored.source == url) stored else null
                        writeMeta(
                            meta,
                            PartMeta(
                                versionCode = target.versionCode,
                                sha256 = target.sha256,
                                source = url,
                                etag = etag ?: keep?.etag,
                                lastModified = lastModified ?: keep?.lastModified,
                                total = total,
                            ),
                        )
                        val body = response.body ?: throw IOException("empty body")
                        copy(body.byteStream(), part, append, total, progress)
                        Outcome.Written(total)
                    }
                    is ResumeDecision.Complete -> Outcome.Done
                    ResumeDecision.Reset -> Outcome.Reset
                    ResumeDecision.SizeMismatch -> throw UpdateException(UpdateFailure.INCOMPLETE)
                    is ResumeDecision.Fail -> throw httpFailure(decision.code)
                }
            }
            when (outcome) {
                Outcome.Done -> return
                Outcome.Reset -> {
                    part.delete()
                    meta.delete()
                    if (++resets > 1) throw UpdateException(UpdateFailure.INCOMPLETE)
                }
                is Outcome.Written -> {
                    val length = part.length()
                    val expected = if (target.size > 0) target.size else outcome.total
                    if (expected > 0 && length != expected) {
                        if (length > expected) {
                            part.delete()
                            meta.delete()
                        }
                        // Shorter: the connection ended early; the next mirror (or run) resumes.
                        throw UpdateException(UpdateFailure.INCOMPLETE)
                    }
                    return
                }
            }
        }
    }

    private sealed interface Outcome {
        data object Done : Outcome
        data object Reset : Outcome
        data class Written(val total: Long) : Outcome
    }

    /**
     * Runs the blocking call on the IO dispatcher. Cancelling the coroutine cancels the call,
     * which unblocks the socket read; everything written so far stays in the part file.
     */
    private suspend fun <T> execute(request: Request, block: suspend (okhttp3.Response) -> T): T = coroutineScope {
        val call = http.newCall(request)
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                try {
                    call.execute().use { block(it) }
                } catch (e: IOException) {
                    ensureActive()
                    throw e
                }
            }
        } finally {
            watcher.cancel()
        }
    }

    private suspend fun copy(input: java.io.InputStream, part: File, append: Boolean, total: Long, progress: Progress) {
        val context = coroutineContext
        var written = if (append) part.length() else 0L
        progress.onProgress(written, total)
        input.use { source ->
            FileOutputStream(part, append).use { output ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    context.ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    written += read
                    if (total > 0 && written > total) throw UpdateException(UpdateFailure.INCOMPLETE)
                    progress.onProgress(written, total)
                }
                output.fd.sync()
            }
        }
    }

    private fun isErrorPage(response: okhttp3.Response): Boolean {
        val type = response.body?.contentType() ?: return false
        return type.type == "text" || (type.type == "application" && type.subtype in setOf("json", "xml", "xhtml+xml"))
    }

    private fun readMeta(meta: File): PartMeta? =
        if (!meta.exists()) null else runCatching { json.decodeFromString<PartMeta>(meta.readText()) }.getOrNull()

    private fun writeMeta(meta: File, value: PartMeta) {
        val tmp = File(meta.parentFile, meta.name + ".tmp")
        tmp.writeText(json.encodeToString(PartMeta.serializer(), value))
        if (!tmp.renameTo(meta)) {
            meta.delete()
            tmp.renameTo(meta)
        }
    }

    private fun httpFailure(code: Int): UpdateException = when (code) {
        403, 429 -> UpdateException(UpdateFailure.HTTP, code.toString())
        in 500..599 -> UpdateException(UpdateFailure.SERVER, code.toString())
        else -> UpdateException(UpdateFailure.HTTP, code.toString())
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
