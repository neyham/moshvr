package dev.neyham.moshvr.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Records 16 kHz mono PCM16 and returns it as a WAV blob for transcription APIs. */
class VoiceRecorder {

    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()

    @Volatile
    private var recording = false

    val isRecording: Boolean get() = recording

    @SuppressLint("MissingPermission") // caller checks RECORD_AUDIO
    fun start() {
        if (recording) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            maxOf(minBuf, 8192),
        )
        pcm.reset()
        recorder = rec
        recording = true
        rec.startRecording()
        thread = Thread({
            val buffer = ByteArray(4096)
            while (recording) {
                val read = rec.read(buffer, 0, buffer.size)
                if (read > 0) synchronized(pcm) { pcm.write(buffer, 0, read) }
            }
        }, "VoiceRecorder").also { it.start() }
    }

    /** Stops recording and returns a WAV file as bytes (null if nothing captured). */
    fun stop(): ByteArray? {
        if (!recording) return null
        recording = false
        thread?.join(1000)
        thread = null
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        val data = synchronized(pcm) { pcm.toByteArray() }
        if (data.size < SAMPLE_RATE / 4) return null // < ~125ms of audio: ignore
        return wavHeader(data.size) + data
    }

    private fun wavHeader(dataSize: Int): ByteArray {
        val byteRate = SAMPLE_RATE * 2
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // PCM chunk size
            putShort(1) // PCM format
            putShort(1) // mono
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort(2) // block align
            putShort(16) // bits per sample
            put("data".toByteArray())
            putInt(dataSize)
        }.array()
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
