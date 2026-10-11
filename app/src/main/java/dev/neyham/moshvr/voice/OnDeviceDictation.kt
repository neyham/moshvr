package dev.neyham.moshvr.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
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

    fun start() {
        if (listening) return
        val r = recognizer ?: create().also { recognizer = it }
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        listening = true
        onListening(true)
        r.startListening(intent)
    }

    /** End of speech: the recognizer finishes and delivers the final text. */
    fun stop() {
        if (listening) recognizer?.stopListening()
    }

    /** Drop the take without a result. */
    fun cancel() {
        if (!listening) return
        recognizer?.cancel()
        finish()
    }

    fun release() {
        cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun finish() {
        if (!listening) return
        listening = false
        onListening(false)
    }

    private fun create(): SpeechRecognizer =
        if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            best(partialResults)?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            finish()
            best(results)?.let(onFinal) ?: onError(DictationText.NO_SPEECH)
        }

        override fun onError(error: Int) {
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
        fun available(context: Context): Boolean =
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ||
                SpeechRecognizer.isRecognitionAvailable(context)
    }
}

object DictationText {
    const val LISTENING = "Listening on the headset… tap STOP or pause to finish."
    const val NO_SPEECH = "No speech heard"

    fun forError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> NO_SPEECH
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy; try again"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "This language is not available for on-device speech"
        else -> "Speech recognition failed ($error)"
    }
}
