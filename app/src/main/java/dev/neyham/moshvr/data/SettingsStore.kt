package dev.neyham.moshvr.data

import android.content.Context
import dev.neyham.moshvr.data.CryptoStore
import dev.neyham.moshvr.voice.SttPolicy
import dev.neyham.moshvr.voice.SpeechProvider
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SpeechProfile(
    val encApiKey: String? = null,
    val model: String,
    val baseUrl: String,
    val appId: String = "",
)

@Serializable
data class AppSettings(
    val speechProvider: SpeechProvider = SpeechProvider.OPENAI_COMPATIBLE,
    val speechProfiles: Map<SpeechProvider, SpeechProfile> = emptyMap(),
    val speechAppId: String = "",
    val consentedSpeechProvider: SpeechProvider = SpeechProvider.OPENAI_COMPATIBLE,
    /** Keystore-encrypted API key for the active speech provider. */
    val encApiKey: String? = null,
    val transcriptionModel: String = SttPolicy.DEFAULT_MODEL,
    val apiBaseUrl: String = SttPolicy.DEFAULT_BASE_URL,
    /** When true the foreground terminal asks Android to keep the screen on. */
    val keepScreenOn: Boolean = false,
    /** Explicit consent before any cloud STT upload. */
    val cloudSttConsented: Boolean = false,
    /** Endpoint this consent covers; a URL change requires a new opt-in. */
    val consentedApiBaseUrl: String? = null,
    /** Local evidence of the disclosure accepted; legacy records require a fresh opt-in. */
    val sttConsentVersion: String? = null,
    val sttConsentedAtEpochMs: Long? = null,
    /** Self-attestation, not verified age or a Meta age-group declaration. */
    val cloudSttAdultConfirmed: Boolean = false,
) {
    val hasApiKey: Boolean get() = !encApiKey.isNullOrBlank()

    val hasSpeechConsent: Boolean get() =
        consentedSpeechProvider == speechProvider && SttPolicy.isHttps(apiBaseUrl) &&
            SttPolicy.consentValid(cloudSttConsented, consentedApiBaseUrl, apiBaseUrl) &&
            sttConsentVersion == SttPolicy.CONSENT_VERSION &&
            (sttConsentedAtEpochMs ?: 0) > 0 && cloudSttAdultConfirmed

    fun mayUploadRecording(recordedWith: AppSettings?): Boolean =
        hasSpeechConsent && recordedWith?.hasSpeechConsent == true &&
            speechProvider == recordedWith.speechProvider && speechAppId == recordedWith.speechAppId &&
            apiBaseUrl == recordedWith.apiBaseUrl && transcriptionModel == recordedWith.transcriptionModel &&
            encApiKey == recordedWith.encApiKey && sttConsentedAtEpochMs == recordedWith.sttConsentedAtEpochMs

    /** Keep credentials per provider; switching always requires a fresh upload opt-in. */
    fun selectSpeechProvider(next: SpeechProvider): AppSettings {
        if (next == speechProvider) return this
        val saved = speechProfiles + (speechProvider to SpeechProfile(encApiKey, transcriptionModel, apiBaseUrl, speechAppId))
        val target = saved[next] ?: SpeechProfile(model = next.defaultModel, baseUrl = next.defaultUrl)
        return copy(speechProvider = next, speechProfiles = saved, encApiKey = target.encApiKey,
            transcriptionModel = target.model, apiBaseUrl = target.baseUrl, speechAppId = target.appId)
            .withoutSpeechConsent()
    }

    fun withoutSpeechConsent(): AppSettings = copy(
        cloudSttConsented = false, consentedApiBaseUrl = null,
        sttConsentVersion = null, sttConsentedAtEpochMs = null, cloudSttAdultConfirmed = false,
    )

    fun withSpeechConsent(adultConfirmed: Boolean, nowEpochMs: Long = System.currentTimeMillis()): AppSettings {
        if (!adultConfirmed || !SttPolicy.isHttps(apiBaseUrl) || nowEpochMs <= 0) return withoutSpeechConsent()
        return copy(
            consentedSpeechProvider = speechProvider,
            cloudSttConsented = true, consentedApiBaseUrl = SttPolicy.normalizeBaseUrl(apiBaseUrl),
            sttConsentVersion = SttPolicy.CONSENT_VERSION, sttConsentedAtEpochMs = nowEpochMs,
            cloudSttAdultConfirmed = true,
        )
    }
}

class SettingsStore(context: Context) {
    private val file = File(context.filesDir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): AppSettings {
        if (!file.exists()) return AppSettings()
        val loaded = runCatching { json.decodeFromString<AppSettings>(file.readText()) }
            .getOrDefault(AppSettings())
        val raw = loaded.encApiKey ?: return loaded
        if (!SttPolicy.isLegacyPlaintextKey(raw)) return loaded
        val migrated = runCatching {
            loaded.copy(encApiKey = CryptoStore.encrypt(raw))
        }.getOrElse { loaded.copy(encApiKey = null) }
        save(migrated)
        return migrated
    }

    fun save(settings: AppSettings) {
        file.writeText(json.encodeToString(settings))
    }

    fun clearApiKey() {
        val current = load()
        save(current.copy(encApiKey = null, speechProfiles = current.speechProfiles - current.speechProvider).withoutSpeechConsent())
    }
}
