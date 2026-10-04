package dev.neyham.moshvr

import dev.neyham.moshvr.session.SessionEnding
import dev.neyham.moshvr.session.SshKeepAlive
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
