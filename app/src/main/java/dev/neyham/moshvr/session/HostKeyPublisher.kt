package dev.neyham.moshvr.session

import java.util.concurrent.atomic.AtomicLong

/**
 * Single UI publish path for [HostKeyGate]. Gate callbacks enqueue work;
 * apply always reads live [HostKeyGate.current] and drops superseded generations
 * so a stale null cannot hide a newer request (or revive a finished one).
 */
class HostKeyPublisher(
    val gate: HostKeyGate,
    private val post: (() -> Unit) -> Unit,
    private val onApplied: (HostKeyGate.Request?) -> Unit = {},
) {
    @Volatile
    var displayed: HostKeyGate.Request? = null
        private set

    private val generation = AtomicLong(0)

    val publishedGeneration: Long
        get() = generation.get()

    fun onDisplayed(shown: HostKeyGate.Request?) {
        val gen = generation.incrementAndGet()
        post { applyIfCurrent(gen) }
    }

    fun applyIfCurrent(gen: Long) {
        if (gen != generation.get()) return
        val live = gate.current
        displayed = live
        onApplied(live)
    }
}
