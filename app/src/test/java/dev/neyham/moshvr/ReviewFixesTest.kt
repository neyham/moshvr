package dev.neyham.moshvr

import android.view.InputDevice
import android.view.KeyEvent
import dev.neyham.moshvr.session.ConnectOwner
import dev.neyham.moshvr.session.HostKeyGate
import dev.neyham.moshvr.ui.KeyOrigin
import dev.neyham.moshvr.ui.StickChrome
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regressions from the 2026-10-11 code review of release-0.1.2. */
class ReviewFixesTest {

    @Test
    fun onlyARealKeyboardCountsAsTyping() {
        assertTrue(KeyOrigin.isTyped(isVirtual = false, keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC))
        // Quest's synthesized focus keys come from the virtual (alphabetic) keyboard device.
        assertFalse(KeyOrigin.isTyped(isVirtual = true, keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC))
        assertFalse(KeyOrigin.isTyped(isVirtual = false, keyboardType = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC))
    }

    @Test
    fun typedArrowKeysReachTheTerminal() {
        for (code in intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT)) {
            assertFalse(StickChrome.dispatchKey(InputDevice.SOURCE_KEYBOARD, code, true, 0, null, typed = true))
            assertFalse(StickChrome.dispatchKey(InputDevice.SOURCE_KEYBOARD, code, false, 0, null, typed = true))
        }
    }

    @Test
    fun releasingATabKeyRearmsTheLatch() {
        StickChrome.edgeTab(0)
        assertEquals(-1, StickChrome.edgeTab(-1))
        assertTrue(StickChrome.dispatchKey(InputDevice.SOURCE_DPAD, KeyEvent.KEYCODE_DPAD_LEFT, false, 0, null))
        assertEquals(-1, StickChrome.edgeTab(-1))
        StickChrome.edgeTab(0)
    }

    @Test
    fun retryAfterCancelledHostKeyPromptsAgain() {
        val gate = HostKeyGate(timeoutMs = 5_000)
        val first = ConnectOwner.next("tab")
        gate.cancelOwner(first)
        assertEquals(
            HostKeyGate.Decision.Cancelled,
            gate.await(HostKeyGate.Request.create(first, "h", 22, "ssh-ed25519", "SHA256:x", false)),
        )

        val retry = ConnectOwner.next("tab")
        assertNotEquals(first, retry)
        val req = HostKeyGate.Request.create(retry, "h", 22, "ssh-ed25519", "SHA256:x", false)
        val shown = CountDownLatch(1)
        val result = AtomicReference<HostKeyGate.Decision>()
        val thread = Thread { result.set(gate.await(req) { if (it?.id == req.id) shown.countDown() }) }
        thread.start()
        assertTrue(shown.await(2, TimeUnit.SECONDS))
        gate.respond(req.id, true)
        thread.join(2_000)
        assertEquals(HostKeyGate.Decision.Accept, result.get())
    }

    @Test
    fun micNeverFallsBackToANetworkRecognizer() {
        val src = read("app/src/main/java/dev/neyham/moshvr/voice/OnDeviceDictation.kt")
        assertFalse(src.contains("createSpeechRecognizer("))
        assertFalse(src.contains("isRecognitionAvailable("))
    }

    @Test
    fun keyEventsAreNotLogged() {
        for (path in listOf(
            "app/src/main/java/dev/neyham/moshvr/ui/TerminalInput.kt",
            "app/src/main/java/dev/neyham/moshvr/PanelActivity.kt",
            "app/src/main/java/dev/neyham/moshvr/ImmersiveActivity.kt",
        )) {
            assertFalse(path, read(path).contains("InputDiag"))
        }
    }

    private fun read(path: String): String =
        listOf(File(path), File(path.removePrefix("app/"))).firstOrNull { it.exists() }?.readText()
            ?: error("missing $path")
}

/** Regressions from the second 2026-10-11 review pass. */
class ConnectFlowReviewTest {

    @Test
    fun handshakeOutlastsTheHostKeyPrompt() {
        val prompt = HostKeyGate().timeoutMs // HostKeyPrompt.gate uses the default
        val kex = dev.neyham.moshvr.session.SshAuth.kexTimeoutMs(prompt)
        assertTrue("kex $kex ms must exceed prompt $prompt ms", kex > prompt)
    }

    @Test
    fun writerStartsBeforeTheStartupCommandAndSizeIsResent() {
        val src = File("src/main/java/dev/neyham/moshvr/session/TransportTerminalSession.kt")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/dev/neyham/moshvr/session/TransportTerminalSession.kt")
        val text = src.readText()
        assertTrue(text.indexOf("startWriterThread(s)") < text.indexOf("StartupCommand.typedLine"))
        assertTrue(text.contains("transport.resize(this.columns, this.rows)"))
    }
}

/** Regressions from the third 2026-10-11 review pass. */
class InputSafetyReviewTest {

    private fun read(path: String): String =
        listOf(File(path), File(path.removePrefix("app/")), File("../$path")).firstOrNull { it.exists() }?.readText()
            ?: error("missing $path")

    @Test
    fun controllerInputNeedsAVisibleTerminal() {
        val ptt = dev.neyham.moshvr.ui.VoicePtt
        val token = Any()
        ptt.unbind(token)
        assertFalse(ptt.hasTerminal())
        ptt.bind(token, toggle = {}, cancel = {})
        assertTrue(ptt.hasTerminal())
        ptt.unbind(token)
        assertFalse(ptt.hasTerminal())
        assertTrue(read("app/src/main/java/dev/neyham/moshvr/ImmersiveActivity.kt").contains("if (!VoicePtt.hasTerminal()) return"))
    }

    @Test
    fun sessionInputNeverBlocksTheCaller() {
        val src = read("terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java")
        assertTrue(src.contains("mInputPump.execute(() -> mTerminalToProcessIOQueue.write("))
    }

    @Test
    fun lateRecognizerCallbacksAreDropped() {
        val src = read("app/src/main/java/dev/neyham/moshvr/voice/OnDeviceDictation.kt")
        assertTrue(src.contains("TakeListener(++take)"))
        assertEquals(3, Regex("if \\(!current\\) return").findAll(src).count())
    }
}
