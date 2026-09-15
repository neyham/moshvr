package dev.neyham.moshvr

import dev.neyham.moshvr.session.HostKeyGate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostKeyGateTest {

    private fun request(owner: String, host: String = owner) =
        HostKeyGate.Request.create(owner, host, 22, "ssh-ed25519", "SHA256:abc", false)

    @Test
    fun rejectCompletesOnlyThatRequest() {
        val gate = HostKeyGate(timeoutMs = 5_000)
        val req = request("a")
        val result = AtomicReference<HostKeyGate.Decision>()
        val started = CountDownLatch(1)
        val thread = Thread {
            result.set(gate.await(req) { if (it?.id == req.id) started.countDown() })
        }
        thread.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        gate.respond("wrong-id", true)
        assertTrue(thread.isAlive)
        gate.respond(req.id, false)
        thread.join(2_000)
        assertEquals(HostKeyGate.Decision.Reject, result.get())
    }

    @Test
    fun cancelWhileWaitingUnblocks() {
        val gate = HostKeyGate(timeoutMs = 30_000)
        val req = request("tab-1")
        val result = AtomicReference<HostKeyGate.Decision>()
        val started = CountDownLatch(1)
        val thread = Thread {
            result.set(gate.await(req) { if (it?.id == req.id) started.countDown() })
        }
        thread.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        gate.cancelOwner("tab-1")
        thread.join(2_000)
        assertEquals(HostKeyGate.Decision.Cancelled, result.get())
    }

    @Test
    fun twoPromptsQueueAndDoNotCrossWire() {
        val gate = HostKeyGate(timeoutMs = 10_000)
        val first = request("one", "one.example")
        val second = request("two", "two.example")
        val firstDecision = AtomicReference<HostKeyGate.Decision>()
        val secondDecision = AtomicReference<HostKeyGate.Decision>()
        val firstShown = CountDownLatch(1)
        val secondShown = CountDownLatch(1)
        val t1 = Thread {
            firstDecision.set(gate.await(first) { if (it?.id == first.id) firstShown.countDown() })
        }
        val t2 = Thread {
            secondDecision.set(gate.await(second) { if (it?.id == second.id) secondShown.countDown() })
        }
        t1.start()
        assertTrue(firstShown.await(2, TimeUnit.SECONDS))
        t2.start()
        Thread.sleep(80)
        assertEquals(first.id, gate.current?.id)
        gate.respond(first.id, false)
        t1.join(2_000)
        assertEquals(HostKeyGate.Decision.Reject, firstDecision.get())
        assertTrue(secondShown.await(2, TimeUnit.SECONDS))
        assertEquals(second.id, gate.current?.id)
        gate.respond(second.id, true)
        t2.join(2_000)
        assertEquals(HostKeyGate.Decision.Accept, secondDecision.get())
        assertNotEquals(firstDecision.get(), secondDecision.get())
        assertNull(gate.current)
    }

    @Test
    fun lastAcceptPublishesNullToOnDisplayed() {
        val gate = HostKeyGate(timeoutMs = 5_000)
        val req = request("a")
        val shown = CopyOnWriteArrayList<HostKeyGate.Request?>()
        val started = CountDownLatch(1)
        val result = AtomicReference<HostKeyGate.Decision>()
        val thread = Thread {
            result.set(
                gate.await(req) {
                    shown.add(it)
                    if (it?.id == req.id) started.countDown()
                },
            )
        }
        thread.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        gate.respond(req.id, true)
        thread.join(2_000)
        assertEquals(HostKeyGate.Decision.Accept, result.get())
        assertNull(gate.current)
        assertTrue(shown.any { it == null })
        assertNull(shown.last())
    }

    @Test
    fun lastRejectPublishesNullToOnDisplayed() {
        val gate = HostKeyGate(timeoutMs = 5_000)
        val req = request("a")
        val shown = CopyOnWriteArrayList<HostKeyGate.Request?>()
        val started = CountDownLatch(1)
        val result = AtomicReference<HostKeyGate.Decision>()
        val thread = Thread {
            result.set(
                gate.await(req) {
                    shown.add(it)
                    if (it?.id == req.id) started.countDown()
                },
            )
        }
        thread.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        gate.respond(req.id, false)
        thread.join(2_000)
        assertEquals(HostKeyGate.Decision.Reject, result.get())
        assertNull(gate.current)
        assertNull(shown.last())
    }

    @Test
    fun timeoutClearsCurrentAndPublishesNull() {
        val gate = HostKeyGate(timeoutMs = 40)
        val req = request("a")
        val shown = CopyOnWriteArrayList<HostKeyGate.Request?>()
        val started = CountDownLatch(1)
        val result = AtomicReference<HostKeyGate.Decision>()
        val thread = Thread {
            result.set(
                gate.await(req) {
                    shown.add(it)
                    if (it?.id == req.id) started.countDown()
                },
            )
        }
        thread.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        thread.join(2_000)
        assertEquals(HostKeyGate.Decision.Timeout, result.get())
        assertNull(gate.current)
        assertNull(shown.last())
    }

    @Test
    fun ownerCancelClearsCurrentAndPublishesNull() {
        val gate = HostKeyGate(timeoutMs = 30_000)
        val req = request("tab-1")
        val shown = CopyOnWriteArrayList<HostKeyGate.Request?>()
        val started = CountDownLatch(1)
        val result = AtomicReference<HostKeyGate.Decision>()
        val thread = Thread {
            result.set(
                gate.await(req) {
                    shown.add(it)
                    if (it?.id == req.id) started.countDown()
                },
            )
        }
        thread.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        gate.cancelOwner("tab-1")
        thread.join(2_000)
        assertEquals(HostKeyGate.Decision.Cancelled, result.get())
        assertNull(gate.current)
        assertNull(shown.last())
    }

    @Test
    fun cancelOwnerBeforeAwaitIsCancelledAndNotDisplayed() {
        val gate = HostKeyGate(timeoutMs = 5_000)
        val req = request("dead")
        val shown = CopyOnWriteArrayList<HostKeyGate.Request?>()
        gate.cancelOwner("dead")
        val decision = gate.await(req) { shown.add(it) }
        assertEquals(HostKeyGate.Decision.Cancelled, decision)
        assertTrue(shown.isEmpty())
        assertNull(gate.current)
    }

    @Test
    fun lateArrivalDoesNotClearAnotherOwnersPrompt() {
        val gate = HostKeyGate(timeoutMs = 5_000)
        val live = request("live")
        val dead = request("dead")
        val liveShown = CountDownLatch(1)
        val liveDecision = AtomicReference<HostKeyGate.Decision>()
        val t = Thread {
            liveDecision.set(gate.await(live) { if (it?.id == live.id) liveShown.countDown() })
        }
        t.start()
        assertTrue(liveShown.await(2, TimeUnit.SECONDS))
        gate.cancelOwner("dead")
        val lateShown = CopyOnWriteArrayList<HostKeyGate.Request?>()
        val late = gate.await(dead) { lateShown.add(it) }
        assertEquals(HostKeyGate.Decision.Cancelled, late)
        assertTrue(lateShown.isEmpty())
        assertEquals(live.id, gate.current?.id)
        gate.respond(live.id, true)
        t.join(2_000)
        assertEquals(HostKeyGate.Decision.Accept, liveDecision.get())
        assertNull(gate.current)
    }
}
