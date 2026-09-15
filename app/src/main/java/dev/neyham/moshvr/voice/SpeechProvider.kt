package dev.neyham.moshvr.voice

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Base64
import java.util.UUID

@Serializable
enum class SpeechProvider(val label: String, val defaultUrl: String, val defaultModel: String) {
    OPENAI_COMPATIBLE("SiliconFlow / OpenAI compatible", SttPolicy.DEFAULT_BASE_URL, SttPolicy.DEFAULT_MODEL),
    QWEN("Qwen ASR", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3-asr-flash"),
    VOLCENGINE("Volcengine speech", "https://openspeech.bytedance.com/api/v3/auc/bigmodel", "volc.bigasr.auc_turbo"),
}

/** Provider-specific wire format. Shared transport owns cancellation and no-retry policy. */
internal object SpeechProtocol {
    fun request(provider: SpeechProvider, wav: ByteArray, key: String, model: String,
                baseUrl: String, appId: String): Request {
        require(SttPolicy.isHttps(baseUrl)) { "Speech URL must be HTTPS" }
        val url = SttPolicy.normalizeBaseUrl(baseUrl)
        val builder = Request.Builder()
        if (provider == SpeechProvider.OPENAI_COMPATIBLE) {
            return builder.url("$url/audio/transcriptions").header("Authorization", "Bearer $key")
                .post(MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
                    .addFormDataPart("model", model).build()).build()
        }
        val audio = Base64.getEncoder().encodeToString(wav)
        val json = if (provider == SpeechProvider.QWEN) {
            builder.url("$url/chat/completions").header("Authorization", "Bearer $key")
            buildJsonObject {
                put("model", model); put("stream", false)
                putJsonArray("messages") { addJsonObject {
                    put("role", "user")
                    putJsonArray("content") { addJsonObject {
                        put("type", "input_audio")
                        putJsonObject("input_audio") { put("data", "data:audio/wav;base64,$audio") }
                    } }
                } }
                putJsonObject("asr_options") { put("enable_itn", false) }
            }
        } else {
            builder.url("$url/recognize/flash")
                .header("X-Api-Resource-Id", model)
                .header("X-Api-Request-Id", UUID.randomUUID().toString())
                .header("X-Api-Sequence", "-1")
            if (appId.isBlank()) builder.header("X-Api-Key", key)
            else builder.header("X-Api-App-Key", appId).header("X-Api-Access-Key", key)
            buildJsonObject {
                putJsonObject("user") { put("uid", "moshvr") }
                putJsonObject("audio") { put("data", audio) }
                putJsonObject("request") { put("model_name", "bigmodel") }
            }
        }
        return builder.post(json.toString().toRequestBody("application/json".toMediaType())).build()
    }

    fun transcript(provider: SpeechProvider, response: Response, body: String): String {
        if (provider == SpeechProvider.VOLCENGINE && response.header("X-Api-Status-Code") != "20000000") {
            throw IOException("Volcengine speech rejected the request. Check the speech key and resource access.")
        }
        return try {
            val obj = Json.parseToJsonElement(body).jsonObject
            val value = when (provider) {
                SpeechProvider.OPENAI_COMPATIBLE -> obj["text"]
                SpeechProvider.QWEN -> obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("message")?.jsonObject?.get("content")
                SpeechProvider.VOLCENGINE -> obj["result"]?.jsonObject?.get("text")
            } as? JsonPrimitive
            if (value == null || !value.isString) throw IOException("No text in transcription response")
            value.content.trim()
        } catch (_: Exception) {
            throw IOException("Invalid transcription response. Check the provider and model.")
        }
    }
}
