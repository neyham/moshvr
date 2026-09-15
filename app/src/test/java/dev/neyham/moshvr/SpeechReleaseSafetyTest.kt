package dev.neyham.moshvr

import dev.neyham.moshvr.data.AppSettings
import dev.neyham.moshvr.voice.SttPolicy
import dev.neyham.moshvr.voice.WhisperClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class SpeechReleaseSafetyTest {
    @Test fun legacyAndIncompleteEvidenceRequireFreshConsent() {
        val legacy = Json.decodeFromString<AppSettings>(
            """{"cloudSttConsented":true,"consentedApiBaseUrl":"https://api.siliconflow.cn/v1"}""",
        )
        assertFalse(legacy.hasSpeechConsent)
        val valid = legacy.withSpeechConsent(true, 100)
        assertTrue(valid.hasSpeechConsent)
        assertFalse(valid.copy(sttConsentVersion = "old").hasSpeechConsent)
        assertFalse(valid.copy(sttConsentedAtEpochMs = null).hasSpeechConsent)
        assertFalse(valid.copy(cloudSttAdultConfirmed = false).hasSpeechConsent)
        assertFalse(valid.withSpeechConsent(false, 100).hasSpeechConsent)
    }

    @Test fun recordingCannotFollowRevocationEndpointKeyOrModelChange() {
        val recording = AppSettings(encApiKey = "ciphertext").withSpeechConsent(true, 100)
        assertTrue(recording.mayUploadRecording(recording))
        assertTrue(recording.copy(keepScreenOn = true).mayUploadRecording(recording))
        assertFalse(recording.withoutSpeechConsent().mayUploadRecording(recording))
        assertFalse(recording.copy(apiBaseUrl = "https://example.com/v1").withSpeechConsent(true, 101).mayUploadRecording(recording))
        assertFalse(recording.copy(encApiKey = "another").mayUploadRecording(recording))
        assertFalse(recording.copy(transcriptionModel = "another").mayUploadRecording(recording))
        assertFalse(recording.mayUploadRecording(null))
    }

    @Test fun endpointRequiresHttpsHostAndNoEmbeddedCredentialsOrQuery() {
        listOf("http://example.com", "https://", "https:///v1", "https://user:secret@example.com/v1",
            "https://example.com/v1?token=secret", "https://example.com/#fragment", "https://example.com:0/v1",
            "https://example.com:65536/v1", "https://bad host/v1").forEach {
            assertFalse(it, SttPolicy.isHttps(it))
        }
        assertTrue(SttPolicy.isHttps(" https://example.com:8443/v1/ "))
        assertTrue(SttPolicy.isHttps("https://[::1]:8443/v1"))
    }

    private fun withServer(block: (MockWebServer, okhttp3.OkHttpClient) -> Unit) {
        val cert = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            val client = WhisperClient.http.newBuilder()
                .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
                .build()
            block(server, client)
        }
    }

    @Test fun successfulHttpsPostReturnsDraftAndSendsOnlyExpectedFields() = withServer { server, client ->
        server.enqueue(MockResponse().setBody("""{"text":"  echo hello  "}"""))
        val result = runBlocking {
            WhisperClient.transcribe("RIFF-test-audio".toByteArray(), "test-only-key", "test-model",
                server.url("/v1").toString(), WhisperClient.nextGeneration(), client)
        }
        assertEquals("echo hello", result)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/v1/audio/transcriptions", request.path)
        assertEquals("Bearer test-only-key", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("RIFF-test-audio"))
        assertTrue(body.contains("test-model"))
        assertEquals(1, server.requestCount)
    }

    @Test fun redirectsDoNotForwardAudioAndHttpErrorsDoNotRetryOrExposeBody() = withServer { server, client ->
        for (code in listOf(307, 401, 408, 429, 500, 503)) {
            server.enqueue(MockResponse().setResponseCode(code).setHeader("Location", server.url("/elsewhere"))
                .setHeader("Retry-After", "0").setBody("sensitive-provider-response"))
            val before = server.requestCount
            val error = runCatching {
                runBlocking {
                    WhisperClient.transcribe(byteArrayOf(1), "test-only-key", "test-model",
                        server.url("/v1").toString(), WhisperClient.nextGeneration(), client)
                }
            }.exceptionOrNull()
            assertTrue(error is IOException)
            assertFalse(error!!.message.orEmpty().contains("sensitive-provider-response"))
            assertEquals(before + 1, server.requestCount)
            assertEquals("/v1/audio/transcriptions", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        }
        assertFalse(client.retryOnConnectionFailure)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test fun cancelledGenerationNeverSendsRequest() = withServer { server, client ->
        val gen = WhisperClient.nextGeneration()
        WhisperClient.cancel(gen)
        val error = runCatching {
            runBlocking { WhisperClient.transcribe(byteArrayOf(1), "test", "test",
                server.url("/v1").toString(), gen, client) }
        }.exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals(0, server.requestCount)
    }
}
