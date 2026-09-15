package dev.neyham.moshvr.session

import java.util.ArrayDeque
import java.util.UUID

/**
 * One prompt at a time, each with its own id and owner. Concurrent connects
 * queue; cancel/timeout never complete a different request.
 */
class HostKeyGate(
    val timeoutMs: Long = 120_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    enum class Decision { Accept, Reject, Timeout, Cancelled }

    data class Request(
        val id: String,
        val ownerId: String,
        val host: String,
        val port: Int,
        val algorithm: String,
        val fingerprint: String,
        val isChange: Boolean,
        val expectedFingerprint: String? = null,
    ) {
        companion object {
            fun create(
                ownerId: String,
                host: String,
                port: Int,
                algorithm: String,
                fingerprint: String,
                isChange: Boolean,
                expectedFingerprint: String? = null,
            ) = Request(
                id = UUID.randomUUID().toString(),
                ownerId = ownerId,
                host = host,
                port = port,
                algorithm = algorithm,
                fingerprint = fingerprint,
                isChange = isChange,
                expectedFingerprint = expectedFingerprint,
            )
        }
    }

    private class Pending(
        val request: Request,
        val onDisplayed: (Request?) -> Unit,
    ) {
        var decision: Decision? = null
    }

    private val lock = Object()
    private val queue = ArrayDeque<Pending>()
    private val deadOwners = HashSet<String>()
    private var displayed: Pending? = null
    private var lastListener: ((Request?) -> Unit)? = null

    @Volatile
    var current: Request? = null
        private set

    fun await(request: Request, onDisplayed: (Request?) -> Unit = {}): Decision {
        val pending = Pending(request, onDisplayed)
        synchronized(lock) {
            if (request.ownerId in deadOwners) {
                return Decision.Cancelled
            }
            queue.add(pending)
            promoteLocked()
            val deadline = nowMs() + timeoutMs
            while (pending.decision == null) {
                val remaining = deadline - nowMs()
                if (remaining <= 0L) {
                    pending.decision = Decision.Timeout
                    break
                }
                lock.wait(remaining.coerceAtMost(timeoutMs).coerceAtLeast(1L))
            }
            if (displayed === pending) displayed = null
            queue.remove(pending)
            promoteLocked()
            return pending.decision ?: Decision.Cancelled
        }
    }

    fun respond(requestId: String, accepted: Boolean) {
        synchronized(lock) {
            val pending = displayed ?: return
            if (pending.request.id != requestId) return
            if (pending.decision != null) return
            pending.decision = if (accepted) Decision.Accept else Decision.Reject
            lock.notifyAll()
        }
    }

    fun cancelOwner(ownerId: String) {
        synchronized(lock) {
            deadOwners.add(ownerId)
            var changed = false
            queue.forEach { pending ->
                if (pending.request.ownerId == ownerId && pending.decision == null) {
                    pending.decision = Decision.Cancelled
                    changed = true
                }
            }
            displayed?.let { pending ->
                if (pending.request.ownerId == ownerId && pending.decision == null) {
                    pending.decision = Decision.Cancelled
                    changed = true
                }
            }
            if (changed) lock.notifyAll()
        }
    }

    private fun promoteLocked() {
        if (displayed != null && displayed?.decision == null) {
            publishLocked(displayed!!.request, displayed!!.onDisplayed)
            return
        }
        displayed = queue.firstOrNull { it.decision == null }
        if (displayed != null) {
            publishLocked(displayed!!.request, displayed!!.onDisplayed)
        } else {
            publishLocked(null, lastListener)
        }
    }

    private fun publishLocked(shown: Request?, listener: ((Request?) -> Unit)?) {
        current = shown
        if (listener != null) lastListener = listener
        listener?.invoke(shown)
    }
}
