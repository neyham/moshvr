package dev.neyham.moshvr

import dev.neyham.moshvr.session.HostKeyGate
import dev.neyham.moshvr.session.HostKeyPublisher
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostKeyPublisherTest {

    private fun request(owner: String) =
        HostKeyGate.Request.create(owner, owner, 22, "ssh-ed25519", "SHA256:abc", false)

    @Test
    fun staleNullGenerationDoesNotHideNewerRequest() {
        val gate = HostKeyGate(timeoutMs = 10_000)
        val queue = ArrayDeque<() -> Unit>()
        val pub = HostKeyPublisher(gate, post = { queue.add(it) })
        val a = request("a")
        val b = request("b")
        val aReady = CountDownLatch(1)
        val bReady = CountDownLatch(1)
        val tA = Thread {
            gate.await(a) {
                pub.onDisplayed(it)
                if (it?.id == a.id) aReady.countDown()
            }
        }
        tA.start()
        assertTrue(aReady.await(2, TimeUnit.SECONDS))
        drain(queue)
        assertEquals(a.id, pub.displayed?.id)
        gate.respond(a.id, true)
        tA.join(2_000)
        val tB = Thread {
            gate.await(b) {
                pub.onDisplayed(it)
                if (it?.id == b.id) bReady.countDown()
            }
        }
        tB.start()
        assertTrue(bReady.await(2, TimeUnit.SECONDS))
        assertEquals(b.id, gate.current?.id)
        assertEquals(2, queue.size)
        val staleNull = queue.removeFirst()
        val liveB = queue.removeFirst()
        liveB()
        assertEquals(b.id, pub.displayed?.id)
        staleNull()
        assertEquals(b.id, pub.displayed?.id)
        assertEquals(b.id, gate.current?.id)
        gate.respond(b.id, true)
        tB.join(2_000)
        drain(queue)
        assertNull(pub.displayed)
        assertNull(gate.current)
    }

    @Test
    fun queuedStaleNullSkippedWhenFlushedInOrder() {
        val gate = HostKeyGate(timeoutMs = 10_000)
        val queue = ArrayDeque<() -> Unit>()
        val pub = HostKeyPublisher(gate, post = { queue.add(it) })
        val a = request("a")
        val b = request("b")
        val aReady = CountDownLatch(1)
        val bReady = CountDownLatch(1)
        val tA = Thread {
            gate.await(a) {
                pub.onDisplayed(it)
                if (it?.id == a.id) aReady.countDown()
            }
        }
        tA.start()
        assertTrue(aReady.await(2, TimeUnit.SECONDS))
        drain(queue)
        gate.respond(a.id, true)
        tA.join(2_000)
        val tB = Thread {
            gate.await(b) {
                pub.onDisplayed(it)
                if (it?.id == b.id) bReady.countDown()
            }
        }
        tB.start()
        assertTrue(bReady.await(2, TimeUnit.SECONDS))
        drain(queue)
        assertEquals(b.id, pub.displayed?.id)
        gate.respond(b.id, true)
        tB.join(2_000)
        drain(queue)
        assertNull(pub.displayed)
    }

    @Test
    fun applyUsesLiveGateCurrentNotNullSnapshot() {
        val gate = HostKeyGate(timeoutMs = 10_000)
        val queue = ArrayDeque<() -> Unit>()
        val pub = HostKeyPublisher(gate, post = { queue.add(it) })
        val live = request("live")
        val ready = CountDownLatch(1)
        val t = Thread {
            gate.await(live) {
                pub.onDisplayed(it)
                if (it?.id == live.id) ready.countDown()
            }
        }
        t.start()
        assertTrue(ready.await(2, TimeUnit.SECONDS))
        drain(queue)
        val staleGen = pub.publishedGeneration
        pub.onDisplayed(null)
        assertTrue(pub.publishedGeneration > staleGen)
        drain(queue)
        assertEquals(live.id, pub.displayed?.id)
        assertEquals(live.id, gate.current?.id)
        pub.applyIfCurrent(staleGen)
        assertEquals(live.id, pub.displayed?.id)
        gate.respond(live.id, false)
        t.join(2_000)
        drain(queue)
        assertNull(pub.displayed)
    }

    private fun drain(queue: ArrayDeque<() -> Unit>) {
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
    }
}
