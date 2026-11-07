package dev.neyham.moshvr

import dev.neyham.moshvr.session.SessionEnding
import dev.neyham.moshvr.session.SshKeepAlive
import dev.neyham.moshvr.session.SshResizer
import java.io.IOException
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshKeepAliveTest {

    @Test
    fun answeredPingKeepsTheLink() {
        assertTrue(SshKeepAlive.pingWithin(1_000) {})
    }

    @Test
    fun failedPingReportsDead() {
        assertFalse(SshKeepAlive.pingWithin(1_000) { throw IOException("reset") })
    }

    @Test
    fun hungPingIsAbandonedAfterTheTimeout() {
        val never = CountDownLatch(1)
        val started = System.nanoTime()
        assertFalse(SshKeepAlive.pingWithin(200) { never.await() })
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("took $elapsedMs ms", elapsedMs < 2_000)
    }
}

class SessionEndingTest {

    @Test
    fun droppedLinkOffersReconnectInsteadOfProcessCompleted() {
        val banner = SessionEnding.Disconnected.banner()
        assertTrue(banner.contains("disconnected"))
        assertTrue(banner.contains("press Enter to reconnect"))
        assertFalse(banner.contains("Process completed"))
    }

    @Test
    fun remoteExitShowsItsCode() {
        assertFalse(SessionEnding.RemoteExit(0).banner().contains("code"))
        assertTrue(SessionEnding.RemoteExit(130).banner().contains("code 130"))
        assertTrue(SessionEnding.ConnectFailed.banner().contains("press Enter to retry"))
    }
}

class SshResizerTest {

    @Test
    fun resizeIsSentOffTheCallingThread() {
        val caller = Thread.currentThread()
        val sentOn = java.util.concurrent.atomic.AtomicReference<Thread>()
        val done = CountDownLatch(1)
        SshResizer().post(100, 30) { _, _ -> sentOn.set(Thread.currentThread()); done.countDown() }
        assertTrue(done.await(2, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(sentOn.get() !== caller)
    }

    @Test
    fun burstOfResizesEndsOnTheNewestSize() {
        val sizes = java.util.concurrent.CopyOnWriteArrayList<Pair<Int, Int>>()
        val gate = CountDownLatch(1)
        val resizer = SshResizer()
        resizer.post(80, 24) { c, r -> gate.await(); sizes.add(c to r) }
        for (c in 81..90) resizer.post(c, 24) { col, r -> sizes.add(col to r) }
        gate.countDown()
        val deadline = System.nanoTime() + 2_000_000_000L
        while (sizes.lastOrNull() != (90 to 24) && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("sent $sizes", sizes.last() == (90 to 24))
        assertTrue("sent $sizes", sizes.size <= 3)
    }
}
