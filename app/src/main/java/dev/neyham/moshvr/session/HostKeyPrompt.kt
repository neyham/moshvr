package dev.neyham.moshvr.session

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Android UI facade over [HostKeyGate]. Only the gate listener publishes. */
object HostKeyPrompt {
    val gate = HostKeyGate()

    var current by mutableStateOf<HostKeyGate.Request?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())

    internal val publisher = HostKeyPublisher(
        gate = gate,
        post = { action ->
            if (Looper.myLooper() == Looper.getMainLooper()) {
                action()
            } else {
                main.post(action)
            }
        },
        onApplied = { live -> current = live },
    )

    fun await(
        ownerId: String,
        host: String,
        port: Int,
        algorithm: String,
        fingerprint: String,
        isChange: Boolean,
        expectedFingerprint: String? = null,
    ): Boolean {
        val request = HostKeyGate.Request.create(
            ownerId = ownerId,
            host = host,
            port = port,
            algorithm = algorithm,
            fingerprint = fingerprint,
            isChange = isChange,
            expectedFingerprint = expectedFingerprint,
        )
        val decision = gate.await(request) { shown -> publisher.onDisplayed(shown) }
        return decision == HostKeyGate.Decision.Accept
    }

    fun respond(requestId: String, accepted: Boolean) {
        gate.respond(requestId, accepted)
    }

    fun cancelOwner(ownerId: String) {
        gate.cancelOwner(ownerId)
    }
}
