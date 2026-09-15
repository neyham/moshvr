package dev.neyham.moshvr.voice

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient

/** Shared one-shot WAV transcription transport for the selected speech provider. */
object WhisperClient {

    internal val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // OkHttp can follow a 503 + Retry-After: 0 independently of its
            // connection-retry flag. Reject errors before that follow-up layer.
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                throw IOException("Transcription failed (HTTP $code). Check your endpoint, key and provider account.")
            }
            response
        }
        .build()

    private val generation = AtomicLong(0)

    @Volatile
    private var cancelled = false

    @Volatile
    private var inFlight: Call? = null

    @Synchronized
    fun nextGeneration(): Long {
        cancelled = false
        inFlight?.cancel()
        inFlight = null
        return generation.incrementAndGet()
    }

    @Synchronized
    fun cancel(expectedGeneration: Long? = null) {
        if (expectedGeneration != null && generation.get() != expectedGeneration) return
        cancelled = true
        generation.incrementAndGet()
        inFlight?.cancel()
        inFlight = null
    }

    fun isCancelled(): Boolean = cancelled

    fun isCurrent(expectedGeneration: Long): Boolean = !stale(expectedGeneration)

    suspend fun transcribe(
        wav: ByteArray,
        apiKey: String,
        model: String,
        baseUrl: String,
        expectedGeneration: Long,
        client: OkHttpClient = http,
        provider: SpeechProvider = SpeechProvider.OPENAI_COMPATIBLE,
        appId: String = "",
    ): String = withContext(Dispatchers.IO) {
        if (!SttPolicy.isHttps(baseUrl)) {
            throw IOException("Speech URL must be HTTPS")
        }
        if (stale(expectedGeneration)) throw IOException("Canceled")
        val request = SpeechProtocol.request(provider, wav, apiKey, model, baseUrl, appId)
        var last: IOException? = null
        repeat(SttPolicy.MAX_ATTEMPTS) {
            if (stale(expectedGeneration)) throw IOException("Canceled")
            val call = client.newCall(request)
            registerCall(call, expectedGeneration)
            try {
                call.execute().use { response ->
                    if (stale(expectedGeneration)) throw IOException("Canceled")
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("Transcription failed (HTTP ${response.code}). Check your endpoint, key and provider account.")
                    }
                    val spoken = SpeechProtocol.transcript(provider, response, text)
                    if (stale(expectedGeneration)) throw IOException("Canceled")
                    return@withContext spoken
                }
            } catch (e: IOException) {
                last = e
                if (call.isCanceled() || stale(expectedGeneration)) throw e
            } finally {
                clearCall(call)
            }
        }
        throw last ?: IOException("Transcription failed")
    }

    @Synchronized
    private fun registerCall(call: Call, expectedGeneration: Long) {
        if (stale(expectedGeneration)) {
            call.cancel()
            throw IOException("Canceled")
        }
        inFlight = call
    }

    @Synchronized
    private fun clearCall(call: Call) {
        if (inFlight === call) inFlight = null
    }

    private fun stale(expectedGeneration: Long): Boolean =
        cancelled || generation.get() != expectedGeneration
}
