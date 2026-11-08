package com.github.mytv.myearthquakealert.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * HTTP fallback for one feed (design §3 退避与降级): after three consecutive WebSocket
 * failures the monitor polls `https://api.wolfx.jp/<feed>.json` every [INTERVAL_MS] until
 * the socket is back. The body is the newest report of that feed, without a `type`, and
 * goes through the same [EewParser] as WebSocket frames.
 */
class FallbackPoller(
    private val source: EewSource,
    private val client: OkHttpClient = defaultClient,
) {
    sealed interface Result {
        data class Ok(val body: String, val roundTripMs: Long, val receivedAt: Long) : Result
        data class Error(val message: String, val httpCode: Int?) : Result
    }

    /** One request; never throws. */
    suspend fun fetchOnce(): Result = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(source.httpUrl)
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                val now = System.currentTimeMillis()
                if (!response.isSuccessful) {
                    Result.Error("HTTP ${response.code}", response.code)
                } else {
                    Result.Ok(response.body?.string().orEmpty(), now - startedAt, now)
                }
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: e.javaClass.simpleName, null)
        }
    }

    companion object {
        const val INTERVAL_MS = 10_000L

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(9, TimeUnit.SECONDS)
                .build()
        }
    }
}
