package dev.neyham.moshvr.voice

import java.net.URI

object SttPolicy {
    const val DEFAULT_MODEL = "FunAudioLLM/SenseVoiceSmall"
    const val DEFAULT_BASE_URL = "https://api.siliconflow.cn/v1"
    const val PROVIDER_NAME = "SiliconFlow"
    const val PROVIDER_PRIVACY_URL = "https://docs.siliconflow.cn/en/legals/privacy-policy"
    /** App-level POST attempts (first try plus retries), not “3 retries”. */
    const val MAX_ATTEMPTS = 1
    const val CONSENT_VERSION = "2026-09-07.1"

    fun normalizeBaseUrl(url: String): String = url.trim().trimEnd('/')

    fun isHttps(url: String): Boolean = runCatching {
        val uri = URI(normalizeBaseUrl(url))
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port in 1..65535)
    }.getOrDefault(false)

    fun disclosure(baseUrl: String, model: String): String =
        "Speech endpoint: ${normalizeBaseUrl(baseUrl)}. Model: $model. " +
            "STOP sends your audio, model/resource ID and API credentials to this endpoint once. " +
            "No automatic retries or redirects. Review the transcript, then tap Send to send text to your SSH host. " +
            "Leave before STOP to discard; cancellation after STOP cannot recall audio already received. " +
            "Cloud speech is available only if you confirm you are 18 or older and permitted to use your provider. " +
            "Default provider: $PROVIDER_NAME; processing may occur in China. " +
            "Default retention/deletion policy: $PROVIDER_PRIVACY_URL. " +
            "For a custom endpoint, review its operator’s privacy and retention policy before consenting."


    fun consentValid(consented: Boolean, consentedUrl: String?, currentUrl: String): Boolean {
        if (!consented) return false
        val bound = consentedUrl ?: return false
        return normalizeBaseUrl(bound) == normalizeBaseUrl(currentUrl)
    }

    /** Settings switch: only on when stored consent already matches this URL. */
    fun switchInitiallyOn(consented: Boolean, consentedUrl: String?, currentUrl: String): Boolean =
        consentValid(consented, consentedUrl, currentUrl)

    data class ConsentPersist(val consented: Boolean, val boundUrl: String?)

    /**
     * Ordinary Save must not promote invalid legacy consent (true + missing/mismatched
     * bound URL) to a valid bind. A true switch is either already-valid or an explicit
     * opt-in this session (switch started off).
     */
    fun persistConsent(
        switchOn: Boolean,
        editedUrl: String,
        previousConsented: Boolean,
        previousBoundUrl: String?,
        previousApiUrl: String,
    ): ConsentPersist {
        val url = editedUrl.trim().ifBlank { DEFAULT_BASE_URL }
        if (!switchOn || !isHttps(url)) return ConsentPersist(false, null)
        val alreadyValidForUrl = consentValid(previousConsented, previousBoundUrl, url)
        if (alreadyValidForUrl) return ConsentPersist(true, normalizeBaseUrl(url))
        val startedOn = switchInitiallyOn(previousConsented, previousBoundUrl, previousApiUrl)
        if (!startedOn && switchOn) {
            return ConsentPersist(true, normalizeBaseUrl(url))
        }
        return ConsentPersist(false, null)
    }

    fun isLegacyPlaintextKey(raw: String): Boolean = raw.startsWith("sk-")

    fun decryptStored(raw: String?, decrypt: (String) -> String): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching { decrypt(raw) }.getOrNull()
    }
}
