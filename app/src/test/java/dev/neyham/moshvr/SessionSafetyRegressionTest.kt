package dev.neyham.moshvr

import dev.neyham.moshvr.session.MoshBootstrap
import dev.neyham.moshvr.session.MoshLifecycle
import dev.neyham.moshvr.session.StartupCommand
import dev.neyham.moshvr.ui.ComposerDraft
import dev.neyham.moshvr.ui.VoicePtt
import dev.neyham.moshvr.voice.SttPolicy
import dev.neyham.moshvr.voice.VoiceSession
import dev.neyham.moshvr.voice.WhisperClient
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupCommandTest {

    @Test
    fun sshAndMoshHonorTheSameCommand() {
        val cmd = "tmux new -As main"
        assertEquals("tmux new -As main\r", StartupCommand.typedLine(cmd))
        assertNull(StartupCommand.typedLine("  "))
        val mosh = MoshBootstrap.serverStartCommand(cmd)
        assertTrue(mosh.contains("LANG="))
        assertTrue(mosh.contains("exec mosh-server new -c 256 -- sh -lc 'tmux new -As main'"))
        assertFalse(MoshBootstrap.SERVER_START_COMMAND.contains("sh -lc"))
        val quoted = MoshBootstrap.serverStartCommand("echo it's")
        assertTrue(quoted.contains("sh -lc 'echo it'\\''s'"))
    }

    @Test
    fun moshReconnectDoesNotResendTypedLine() {
        assertNull(StartupCommand.typedLine(null))
        val once = MoshBootstrap.serverStartCommand("herdr")
        val again = MoshBootstrap.serverStartCommand("herdr")
        assertEquals(once, again)
        assertTrue(once.contains("-- sh -lc 'herdr'"))
    }
}

class ComposerDraftTest {

    @Test
    fun mergeDoesNotSendAndKeepsTypedPlusSpoken() {
        assertEquals("ls", ComposerDraft.mergeSpoken("", "ls"))
        assertEquals("ls -la", ComposerDraft.mergeSpoken("ls", "-la"))
        assertEquals("hello", ComposerDraft.mergeSpoken("hello", "  "))
    }
}

class SttConsentPersistTest {

    private val url = SttPolicy.DEFAULT_BASE_URL

    @Test
    fun legacyTrueWithoutBoundUrlDoesNotActivateOnOrdinarySave() {
        assertFalse(SttPolicy.switchInitiallyOn(true, null, url))
        val saved = SttPolicy.persistConsent(
            switchOn = false,
            editedUrl = url,
            previousConsented = true,
            previousBoundUrl = null,
            previousApiUrl = url,
        )
        assertFalse(saved.consented)
        assertNull(saved.boundUrl)
        assertFalse(SttPolicy.consentValid(saved.consented, saved.boundUrl, url))
    }

    @Test
    fun changingUrlClearsUnlessUserOptsInAgain() {
        val other = "https://example.com/v1"
        assertFalse(SttPolicy.switchInitiallyOn(true, url, other))
        val saved = SttPolicy.persistConsent(
            switchOn = false,
            editedUrl = other,
            previousConsented = true,
            previousBoundUrl = url,
            previousApiUrl = url,
        )
        assertFalse(saved.consented)
        assertNull(saved.boundUrl)
    }

    @Test
    fun otherSettingsKeepAlreadyValidConsent() {
        val saved = SttPolicy.persistConsent(
            switchOn = true,
            editedUrl = "$url/",
            previousConsented = true,
            previousBoundUrl = url,
            previousApiUrl = url,
        )
        assertTrue(saved.consented)
        assertEquals(url, saved.boundUrl)
        assertTrue(SttPolicy.consentValid(saved.consented, saved.boundUrl, url))
    }

    @Test
    fun explicitSwitchOnAfterLegacyIsNewOptIn() {
        val saved = SttPolicy.persistConsent(
            switchOn = true,
            editedUrl = url,
            previousConsented = true,
            previousBoundUrl = null,
            previousApiUrl = url,
        )
        assertTrue(saved.consented)
        assertEquals(url, saved.boundUrl)
        assertFalse(SttPolicy.consentValid(true, null, url))
    }
}

class VoiceOwnerIsolationTest {

    @Test
    fun staleWhisperCancelDoesNotAdvanceNewerGeneration() {
        val first = WhisperClient.nextGeneration()
        val second = WhisperClient.nextGeneration()
        WhisperClient.cancel(first)
        assertTrue(WhisperClient.isCurrent(second))
        WhisperClient.cancel(second)
        assertFalse(WhisperClient.isCurrent(second))
    }

    @Test
    fun oldPttUnbindDoesNotClearNewOwner() {
        val old = Any()
        val fresh = Any()
        var n = 0
        VoicePtt.bind(old, toggle = { error("old") }, cancel = {})
        VoicePtt.bind(fresh, toggle = { n++ }, cancel = {})
        VoicePtt.unbind(old)
        val src = android.view.InputDevice.SOURCE_GAMEPAD
        val x = android.view.KeyEvent.KEYCODE_BUTTON_X
        assertTrue(VoicePtt.dispatch(src, x, true, 0))
        assertEquals(1, n)
        VoicePtt.unbind(fresh)
    }

    @Test
    fun handoffDisposeKeepsTheNewTabCycleHandler() {
        var cycled = 0
        val panelRoot: (Int) -> Unit = { error("disposed 2D root") }
        val immersiveRoot: (Int) -> Unit = { cycled++ }
        dev.neyham.moshvr.ui.StickChrome.bindCycleTab(panelRoot)
        dev.neyham.moshvr.ui.StickChrome.bindCycleTab(immersiveRoot)
        dev.neyham.moshvr.ui.StickChrome.unbindCycleTab(panelRoot)
        dev.neyham.moshvr.ui.StickChrome.onCycleTab?.invoke(1)
        assertEquals(1, cycled)
        dev.neyham.moshvr.ui.StickChrome.unbindCycleTab(immersiveRoot)
        assertNull(dev.neyham.moshvr.ui.StickChrome.onCycleTab)
    }

    @Test
    fun heldStickSwitchesOneWorkspacePerPush() {
        val sc = dev.neyham.moshvr.ui.StickChrome
        val t0 = 1_000_000L
        assertEquals(-1, sc.verticalEdge(-1, t0))
        assertEquals(0, sc.verticalEdge(-1, t0 + 50)) // still held: scroll events keep coming
        assertEquals(0, sc.verticalEdge(-1, t0 + 100))
        assertEquals(-1, sc.verticalEdge(-1, t0 + 100 + sc.WORKSPACE_GAP_MS + 1)) // released, pushed again
        assertEquals(1, sc.verticalEdge(1, t0 + 700))
        assertEquals(0, sc.verticalEdge(0, t0 + 720))
        assertEquals(1, sc.verticalEdge(1, t0 + 740))
        assertEquals("\u0002\u001b[A", dev.neyham.moshvr.ui.HerdrKeys.workspaceChord(-1))
    }

    @Test
    fun controllerBSendsEnterUnlessVoiceIsBusy() {
        val token = Any()
        var enters = 0
        var cancels = 0
        var busy = false
        VoicePtt.bind(token, toggle = {}, cancel = { cancels++ }, enter = { enters++ }, busy = { busy })
        val src = android.view.InputDevice.SOURCE_GAMEPAD
        val b = android.view.KeyEvent.KEYCODE_BUTTON_B
        val y = android.view.KeyEvent.KEYCODE_BUTTON_Y
        assertTrue(VoicePtt.dispatch(src, b, true, 0))
        assertTrue(VoicePtt.dispatch(src, b, true, 1)) // auto-repeat must not send a second Enter
        assertTrue(VoicePtt.dispatch(src, y, true, 0))
        assertEquals(2, enters)
        busy = true
        assertTrue(VoicePtt.dispatch(src, b, true, 0))
        assertEquals(2, enters)
        assertEquals(1, cancels)
        VoicePtt.unbind(token)
    }

    @Test
    fun abandonFromOldHostDoesNotTouchNewOwner() {
        val oldHost = Any()
        val newHost = Any()
        assertFalse(VoiceSession.sameHost(oldHost, newHost))
        assertTrue(VoiceSession.sameHost(newHost, newHost))
    }
}

class MoshLifecycleTest {
    @Test fun duplicateStartsAndInvalidProcessesAreRejected() {
        val life = MoshLifecycle()
        assertFalse(life.adopt(123, 4))
        assertTrue(life.beginStart())
        assertFalse(life.beginStart())
        assertFalse(life.adopt(-1, -1))
        assertTrue(life.adopt(123, 4))
        assertFalse(life.adopt(456, 5))
        assertEquals(123, life.close().running!!.pid)
        assertNull(life.close().running)
    }


    @Test
    fun closeBeforeAdoptForcesCallerToReclaim() {
        val life = MoshLifecycle()
        assertTrue(life.beginStart())
        assertNull(life.close().running)
        assertTrue(life.closed)
        assertFalse(life.adopt(4242, 7))
        assertFalse(life.beginStart())
    }

    @Test
    fun closeAfterAdoptReturnsPidToKill() {
        val life = MoshLifecycle()
        assertTrue(life.beginStart())
        assertTrue(life.adopt(99, 3))
        val closed = life.close()
        assertNotNull(closed.running)
        assertEquals(99, closed.running!!.pid)
        assertEquals(3, closed.running.ptyFd)
        assertTrue(life.closed)
    }

    @Test
    fun concurrentCloseAndAdoptAlwaysReclaims() {
        repeat(40) {
            val life = MoshLifecycle()
            assertTrue(life.beginStart())
            val barrier = CyclicBarrier(2)
            val kept = AtomicBoolean(true)
            val closeResult = AtomicReference<MoshLifecycle.CloseResult?>()
            val adopt = Thread {
                barrier.await(2, TimeUnit.SECONDS)
                kept.set(life.adopt(4242, 7))
            }
            val closer = Thread {
                barrier.await(2, TimeUnit.SECONDS)
                closeResult.set(life.close())
            }
            adopt.start()
            closer.start()
            adopt.join(2_000)
            closer.join(2_000)
            val reclaimedByAdopt = !kept.get()
            val reclaimedByClose = closeResult.get()?.running?.pid == 4242
            assertTrue(reclaimedByAdopt || reclaimedByClose)
            assertFalse(reclaimedByAdopt && reclaimedByClose)
            assertTrue(life.closed)
        }
    }
}
