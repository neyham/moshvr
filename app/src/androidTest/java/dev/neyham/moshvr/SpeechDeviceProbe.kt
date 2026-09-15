package dev.neyham.moshvr

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import dev.neyham.moshvr.data.CryptoStore
import dev.neyham.moshvr.data.SettingsStore
import dev.neyham.moshvr.data.SpeechProfile
import dev.neyham.moshvr.voice.SpeechProvider
import dev.neyham.moshvr.voice.WhisperClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.util.Base64

/** Explicit fixture upload, never records a microphone or sends terminal commands. */
internal object SpeechDeviceProbe {
    fun run(instrumentation: Instrumentation) {
        val out = Bundle()
        val context = instrumentation.targetContext
        val file = File(context.filesDir, "device-speech-probe.json")
        try {
            check(BuildConfig.DEBUG)
            val input = JSONObject(file.readText())
            check(file.delete())
            val wav = Base64.getDecoder().decode(input.getString("wav"))
            val providers = input.getJSONArray("providers")
            val settingsStore = SettingsStore(context)
            val original = settingsStore.load()
            var saved = original.speechProfiles
            var allPassed = true
            for (i in 0 until providers.length()) {
                val spec = providers.getJSONObject(i)
                val provider = SpeechProvider.valueOf(spec.getString("provider"))
                val key = spec.getString("key")
                val url = spec.getString("baseUrl")
                val model = spec.getString("model")
                val appId = spec.optString("appId", "")
                val start = System.nanoTime()
                try {
                    val transcript = runBlocking {
                        WhisperClient.transcribe(wav, key, model, url, WhisperClient.nextGeneration(),
                            provider = provider, appId = appId)
                    }
                    check(transcript.isNotBlank())
                    out.putString(provider.name, "PASS")
                    out.putInt(provider.name + "_transcript_chars", transcript.length)
                } catch (error: Exception) {
                    allPassed = false
                    val http = Regex("HTTP ([0-9]{3})").find(error.message.orEmpty())?.groupValues?.get(1)
                    out.putString(provider.name, if (http != null) "HTTP_$http" else error.javaClass.simpleName)
                }
                out.putLong(provider.name + "_elapsed_ms", (System.nanoTime() - start) / 1_000_000)
                if (input.optBoolean("saveInactiveProfiles", false)) {
                    check(provider != original.speechProvider) { "Do not replace the active speech profile" }
                    saved = saved + (provider to SpeechProfile(CryptoStore.encrypt(key), model, url, appId))
                }
            }
            if (input.optBoolean("saveInactiveProfiles", false)) {
                settingsStore.save(original.copy(speechProfiles = saved))
                out.putString("inactive_profiles", "SAVED_WITH_ANDROID_KEYSTORE")
            }
            out.putString("result", if (allPassed) "PASS" else "PROVIDER_ACCESS_BLOCKED")
        } catch (error: Exception) {
            out.putString("result", "FAIL")
            out.putString("exception_type", error.javaClass.simpleName)
        } finally {
            WhisperClient.cancel()
            file.delete()
        }
        instrumentation.finish(if (out.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, out)
    }
}
