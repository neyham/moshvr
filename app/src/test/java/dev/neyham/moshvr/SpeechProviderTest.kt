package dev.neyham.moshvr

import dev.neyham.moshvr.data.AppSettings
import dev.neyham.moshvr.voice.SpeechProvider
import dev.neyham.moshvr.voice.WhisperClient
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class SpeechProviderTest {
    private fun withServer(block: (MockWebServer, OkHttpClient) -> Unit) {
        val cert = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false); server.start()
            block(server, WhisperClient.http.newBuilder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build())
        }
    }
    private fun transcribe(server: MockWebServer, client: OkHttpClient, provider: SpeechProvider, appId: String = "") = runBlocking {
        WhisperClient.transcribe(byteArrayOf(0, 1, 127, -1), "test-only-key", provider.defaultModel,
            server.url("/v1").toString(), WhisperClient.nextGeneration(), client, provider, appId)
    }

    @Test fun qwenUsesAudioMessageAndExtractsDraft() = withServer { server, client ->
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"  测试文本  "}}]}"""))
        assertEquals("测试文本", transcribe(server, client, SpeechProvider.QWEN))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer test-only-key", request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Api-Key"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("qwen3-asr-flash", body["model"]!!.jsonPrimitive.content)
        val audio = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals("input_audio", audio["type"]!!.jsonPrimitive.content)
        val data = audio["input_audio"]!!.jsonObject["data"]!!.jsonPrimitive.content
        assertTrue(data.startsWith("data:audio/wav;base64,"))
        assertArrayEquals(byteArrayOf(0,1,127,-1), Base64.getDecoder().decode(data.substringAfter(',')))
        assertEquals(1, server.requestCount)
    }

    @Test fun volcNewAndLegacyCredentialsStayInCorrectHeaders() = withServer { server, client ->
        for (appId in listOf("", "test-app")) {
            server.enqueue(MockResponse().setHeader("X-Api-Status-Code", "20000000")
                .setBody("""{"result":{"text":"测试"}}"""))
            assertEquals("测试", transcribe(server, client, SpeechProvider.VOLCENGINE, appId))
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/v1/recognize/flash", request.path)
            assertNull(request.getHeader("Authorization"))
            assertEquals("volc.bigasr.auc_turbo", request.getHeader("X-Api-Resource-Id"))
            if (appId.isEmpty()) {
                assertEquals("test-only-key", request.getHeader("X-Api-Key"))
                assertNull(request.getHeader("X-Api-Access-Key"))
            } else {
                assertNull(request.getHeader("X-Api-Key"))
                assertEquals(appId, request.getHeader("X-Api-App-Key"))
                assertEquals("test-only-key", request.getHeader("X-Api-Access-Key"))
            }
            val raw = request.body.readUtf8()
            assertFalse(raw.contains("test-only-key"))
            val body = Json.parseToJsonElement(raw).jsonObject
            assertArrayEquals(byteArrayOf(0,1,127,-1),Base64.getDecoder().decode(body["audio"]!!.jsonObject["data"]!!.jsonPrimitive.content))
            assertEquals("bigmodel", body["request"]!!.jsonObject["model_name"]!!.jsonPrimitive.content)
        }
    }

    @Test fun volcHttp200WithServiceFailureIsNotAValidTranscript() = withServer { server, client ->
        for (status in listOf("45000000", "")) {
            server.enqueue(MockResponse().setHeader("X-Api-Status-Code", status)
                .setBody("""{"result":{"text":"sensitive-response"}}"""))
            val error = runCatching { transcribe(server, client, SpeechProvider.VOLCENGINE) }.exceptionOrNull()
            assertTrue(error is IOException)
            assertFalse(error!!.message.orEmpty().contains("sensitive-response"))
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun newProvidersNeverRetryOrRedirectErrors() = withServer { server, client ->
        for (provider in listOf(SpeechProvider.QWEN, SpeechProvider.VOLCENGINE)) {
            for (code in listOf(307,401,403,503)) {
                server.enqueue(MockResponse().setResponseCode(code).setHeader("Retry-After", "0")
                    .setHeader("Location", server.url("/elsewhere")).setBody("private-error-content"))
                val before = server.requestCount
                val error = runCatching { transcribe(server, client, provider) }.exceptionOrNull()
                assertTrue(error is IOException)
                assertFalse(error!!.message.orEmpty().contains("private-error-content"))
                assertEquals(before + 1, server.requestCount)
            }
        }
    }

    @Test fun switchingKeepsSeparateEncryptedKeysAndRequiresConsent() {
        val original = AppSettings(encApiKey="silicon-cipher").withSpeechConsent(true,100)
        val qwen = original.selectSpeechProvider(SpeechProvider.QWEN)
        assertNull(qwen.encApiKey); assertFalse(qwen.hasSpeechConsent)
        val configured = qwen.copy(encApiKey="qwen-cipher").withSpeechConsent(true,101)
        val back = configured.selectSpeechProvider(SpeechProvider.OPENAI_COMPATIBLE)
        assertEquals("silicon-cipher",back.encApiKey);assertFalse(back.hasSpeechConsent)
        val restored = Json.decodeFromString<AppSettings>(Json.encodeToString(back)).selectSpeechProvider(SpeechProvider.QWEN)
        assertEquals("qwen-cipher",restored.encApiKey)
        assertFalse(restored.hasSpeechConsent)
    }

    @Test fun providerOrLegacyAppIdChangeDiscardsExistingRecording() {
        val recording = AppSettings(encApiKey="cipher").withSpeechConsent(true,100)
        assertFalse(recording.copy(speechProvider=SpeechProvider.QWEN).hasSpeechConsent)
        assertFalse(recording.copy(speechProvider=SpeechProvider.QWEN).withSpeechConsent(true,101).mayUploadRecording(recording))
        assertFalse(recording.copy(speechAppId="different").mayUploadRecording(recording))
    }

    @Test fun cancelledNewProviderGenerationDoesNotUpload() = withServer { server, client ->
        for (provider in listOf(SpeechProvider.QWEN, SpeechProvider.VOLCENGINE)) {
            val generation = WhisperClient.nextGeneration();WhisperClient.cancel(generation)
            val error = runCatching { runBlocking { WhisperClient.transcribe(byteArrayOf(1),"key",provider.defaultModel,
                server.url("/v1").toString(),generation,client,provider) } }.exceptionOrNull()
            assertTrue(error is IOException)
        }
        assertEquals(0,server.requestCount)
    }
}
