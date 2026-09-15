package dev.neyham.moshvr

import dev.neyham.moshvr.voice.SttPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SttPolicyTest {

    @Test
    fun defaultModelIsDocumentedSiliconFlowStt() {
        assertEquals("FunAudioLLM/SenseVoiceSmall", SttPolicy.DEFAULT_MODEL)
        assertTrue(SttPolicy.isHttps(SttPolicy.DEFAULT_BASE_URL))
        assertFalse(SttPolicy.isHttps("http://api.siliconflow.cn/v1"))
    }

    @Test
    fun consentIsBoundToEndpoint() {
        val url = "https://api.siliconflow.cn/v1"
        assertTrue(SttPolicy.consentValid(true, url, "$url/"))
        assertFalse(SttPolicy.consentValid(true, url, "https://example.com/v1"))
        assertFalse(SttPolicy.consentValid(false, url, url))
        assertFalse(SttPolicy.consentValid(true, null, url))
    }

    @Test
    fun storedKeyIsNeverReturnedAsLegacyPlaintext() {
        assertTrue(SttPolicy.isLegacyPlaintextKey("sk-test"))
        assertNull(SttPolicy.decryptStored("sk-test") { error("must not treat as plaintext") })
        assertEquals("plain", SttPolicy.decryptStored("enc") { "plain" })
    }
}
