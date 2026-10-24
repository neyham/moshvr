package dev.neyham.moshvr.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Quest's own speech recognizer (com.oculus.systemintelligence OnDeviceRecognitionService).
 * Audio stays on the headset and never passes through moshVR. Main thread only.
 */
class OnDeviceDictation(
    private val context: Context,
    private val onListening: (Boolean) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    var listening = false
        private set

    /** Each take gets its own listener; callbacks from a cancelled or older take are dropped. */
    private var take = 0
    private var stopping = false
    private val main = Handler(Looper.getMainLooper())

    fun start() {
        if (listening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Hold-to-talk: ask for a longer pause before the take ends (a hint; recognizers may ignore it).
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
        listening = true
        onListening(true)
        // The system speech service can be missing or refuse to bind; report it, don't crash.
        val started = runCatching {
            val r = recognizer ?: create().also { recognizer = it }
            r.setRecognitionListener(TakeListener(++take))
            r.startListening(intent)
        }
        if (started.isFailure) {
            take++
            dropRecognizer()
            finish()
            onError(DictationText.UNAVAILABLE)
        }
    }

    /**
     * End of speech: the recognizer finishes and delivers the final text. A take stopped before
     * speech began often never answers, so give up after [STOP_TIMEOUT_MS]; stopping again
     * while waiting gives up at once.
     */
    fun stop() {
        if (!listening) return
        if (stopping) {
            giveUp()
            return
        }
        stopping = true
        val id = take
        main.postDelayed({ if (listening && take == id) giveUp() }, STOP_TIMEOUT_MS)
        if (runCatching { recognizer?.stopListening() }.isFailure) giveUp()
    }

    /** Drop the take without a result. */
    fun cancel() {
        if (!listening) return
        take++
        runCatching { recognizer?.cancel() }
        finish()
    }

    fun release() {
        cancel()
        dropRecognizer()
    }

    private fun giveUp() {
        cancel()
        onError(DictationText.NO_SPEECH)
    }

    private fun dropRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun finish() {
        if (!listening) return
        listening = false
        stopping = false
        onListening(false)
    }

    // Only the on-device recognizer: a generic one may send audio to a server, which would break
    // what the MIC promises. Without it, MIC falls back to the system keyboard instead.
    private fun create(): SpeechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

    private inner class TakeListener(private val id: Int) : RecognitionListener {
        private val current get() = listening && id == take

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            if (!current) return
            best(partialResults)?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            if (!current) return
            finish()
            best(results)?.let(onFinal) ?: onError(DictationText.NO_SPEECH)
        }

        override fun onError(error: Int) {
            if (!current) return
            finish()
            onError(DictationText.forError(error))
        }
    }

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    companion object {
        const val STOP_TIMEOUT_MS = 4_000L
        const val SILENCE_MS = 3_000L

        fun available(context: Context): Boolean =
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }
}

object DictationText {
    const val LISTENING = "Listening on the headset… tap STOP or pause to finish."
    const val NO_SPEECH = "No speech heard"
    const val UNAVAILABLE = "Headset speech recognition is not available right now"

    fun forError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> NO_SPEECH
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy; try again"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "Headset speech doesn't support the system language; add a cloud speech key in Settings"
        else -> "Speech recognition failed ($error)"
    }
}
