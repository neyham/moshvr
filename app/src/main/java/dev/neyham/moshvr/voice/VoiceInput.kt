package dev.neyham.moshvr.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.neyham.moshvr.data.CryptoStore
import dev.neyham.moshvr.data.SettingsStore
import dev.neyham.moshvr.ui.MicPermission
import dev.neyham.moshvr.ui.VoicePolicy
import dev.neyham.moshvr.ui.VoicePtt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Tap MIC to record. Tap STOP to transcribe (audio goes to the STT endpoint).
 * Activity pause / focus loss / 20s cap discard audio and cancel any upload.
 */
class VoiceInput(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    private val onResult: (String) -> Unit,
    private val onStatus: (String?) -> Unit,
    private val onRecording: (Boolean) -> Unit,
    private val onBusy: (Boolean) -> Unit,
    private val onFallbackToKeyboard: () -> Unit = {},
    private val onNeedConsent: () -> Unit = {},
) {
    private val recorder = VoiceRecorder()
    private val main = Handler(Looper.getMainLooper())
    private val stopAtMax = Runnable {
        if (recorder.isRecording) {
            if (VoicePolicy.timeoutUploads()) stop() else abandon("Time limit. Recording discarded, not uploaded.")
        }
    }
    private var transcribeJob: Job? = null
    private var recordedWith: dev.neyham.moshvr.data.AppSettings? = null
    private var generation: Long = -1L

    val hasMicPermission: Boolean
        get() = MicPermission.hasPermission(context)

    val usesCloudStt: Boolean
        get() = VoicePolicy.useCloudStt(settingsStore.load().hasApiKey)

    fun start() {
        if (recorder.isRecording) return
        if (!usesCloudStt) {
            onStatus(null)
            onFallbackToKeyboard()
            return
        }
        val settings = settingsStore.load()
        if (!settings.hasSpeechConsent) {
            onNeedConsent()
            return
        }
        if (!hasMicPermission) {
            onStatus(statusForDenied())
            return
        }
        recorder.start()
        recordedWith = settings
        onRecording(true)
        onStatus("Recording… tap STOP to upload. Leave before STOP to discard.")
        main.removeCallbacks(stopAtMax)
        main.postDelayed(stopAtMax, MAX_MS)
    }

    /** User tapped STOP: end the take and upload for transcription. */
    fun stop() {
        main.removeCallbacks(stopAtMax)
        if (recorder.isRecording) finishCloud()
    }

    fun cancel() {
        abandon("Cancelled. Leave before STOP discards; after STOP only the local call stops.")
    }

    fun abandon(status: String? = "Left before STOP. No transcription POST started.") {
        val owner = VoiceSession.isActive(this)
        val gen = generation
        main.removeCallbacks(stopAtMax)
        transcribeJob?.cancel()
        transcribeJob = null
        if (recorder.isRecording) recorder.stop()
        recordedWith = null
        generation = -1L
        if (!owner) return
        if (gen > 0) WhisperClient.cancel(gen)
        onRecording(false)
        onBusy(false)
        onStatus(status)
    }

    fun release() {
        VoicePtt.unbind(this)
        val owner = VoiceSession.isActive(this)
        val gen = generation
        VoiceSession.detach(this)
        main.removeCallbacks(stopAtMax)
        transcribeJob?.cancel()
        transcribeJob = null
        if (recorder.isRecording) recorder.stop()
        recordedWith = null
        generation = -1L
        if (!owner) return
        if (gen > 0) WhisperClient.cancel(gen)
        onRecording(false)
        onBusy(false)
        onStatus(null)
    }

    private fun statusForDenied(): String = when (MicPermission.lastDecision) {
        MicPermission.Decision.DontAskAgain ->
            MicPermission.DONT_ASK_AGAIN
        MicPermission.Decision.Denied ->
            MicPermission.DENIED
        else -> MicPermission.NEEDED
    }

    private fun finishCloud() {
        onRecording(false)
        val wav = recorder.stop()
        if (wav == null) {
            onStatus("Recording too short")
            return
        }
        val approvedRecording = recordedWith
        recordedWith = null
        val settings = settingsStore.load()
        // Consent may have been revoked or the endpoint changed during the recording.
        if (!settings.mayUploadRecording(approvedRecording)) {
            onStatus("Speech consent changed. Recording discarded; tap MIC to review consent.")
            return
        }
        val key = SttPolicy.decryptStored(settings.encApiKey) { CryptoStore.decrypt(it) }
        if (key == null) {
            onFallbackToKeyboard()
            return
        }
        if (!SttPolicy.isHttps(settings.apiBaseUrl)) {
            onStatus("Speech URL must be HTTPS")
            return
        }
        val gen = WhisperClient.nextGeneration()
        generation = gen
        onBusy(true)
        onStatus("Sending audio to speech endpoint… review the text, then send to the host.")
        transcribeJob = scope.launch {
            try {
                if (generation != gen) return@launch
                val result = WhisperClient.transcribe(
                    wav,
                    key,
                    settings.transcriptionModel,
                    settings.apiBaseUrl,
                    gen,
                    provider = settings.speechProvider,
                    appId = settings.speechAppId,
                )
                if (generation != gen) return@launch
                onStatus(null)
                if (result.isNotBlank()) onResult(result)
            } catch (e: Exception) {
                if (generation != gen) return@launch
                val msg = e.message.orEmpty()
                onStatus(
                    if (msg.contains("Canceled", ignoreCase = true) || msg.contains("closed", ignoreCase = true)) {
                        "Cancelled"
                    } else {
                        msg.take(120).ifBlank { "Transcription failed" }
                    },
                )
            } finally {
                if (generation == gen) onBusy(false)
            }
        }
    }

    companion object {
        const val MAX_MS = 20_000L
    }
}
