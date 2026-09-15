package dev.neyham.moshvr

import android.view.KeyEvent
import dev.neyham.moshvr.agent.AgentDetector
import dev.neyham.moshvr.agent.AgentKind
import dev.neyham.moshvr.session.MoshBootstrap
import dev.neyham.moshvr.ui.ExtraKeyAction
import dev.neyham.moshvr.ui.ExtraKeys
import dev.neyham.moshvr.ui.HerdrKeys
import dev.neyham.moshvr.ui.MicPermission
import dev.neyham.moshvr.ui.SessionChrome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraKeysRegressionTest {

    @Test
    fun micIsFirstShortcut() {
        assertEquals("mic", ExtraKeys.catalog.first().id)
        assertTrue(ExtraKeys.catalog.first().action is ExtraKeyAction.Mic)
    }

    @Test
    fun catalogHasCoreTerminalKeys() {
        val ids = ExtraKeys.catalog.map { it.id }
        assertTrue(ids.containsAll(listOf("enter", "keyb", "esc", "tab", "ctrl", "alt", "ctrl-c", "pipe", "tilde", "minus")))
        assertEquals(18, ExtraKeys.catalog.size)
        assertEquals(ExtraKeys.catalog.size, ExtraKeys.catalog.map { it.id }.distinct().size)
    }

    @Test
    fun writesEscAndCtrlC() {
        val enter = ExtraKeys.catalog.first { it.id == "enter" }.action as ExtraKeyAction.Write
        val esc = ExtraKeys.catalog.first { it.id == "esc" }.action as ExtraKeyAction.Write
        val ctrlC = ExtraKeys.catalog.first { it.id == "ctrl-c" }.action as ExtraKeyAction.Write
        assertEquals("\r", enter.payload)
        assertEquals("\u001b", esc.payload)
        assertEquals("\u0003", ctrlC.payload)
    }

    @Test
    fun arrowsUseDpadKeycodes() {
        val left = ExtraKeys.catalog.first { it.id == "left" }.action as ExtraKeyAction.Write
        val right = ExtraKeys.catalog.first { it.id == "right" }.action as ExtraKeyAction.Write
        assertEquals("\u0002p", left.payload)
        assertEquals("\u0002n", right.payload)
        assertEquals("\u0002p", HerdrKeys.tabChord(-1))
        assertEquals("\u0002n", HerdrKeys.tabChord(1))
        val up = ExtraKeys.catalog.first { it.id == "up" }.action as ExtraKeyAction.Scroll
        val down = ExtraKeys.catalog.first { it.id == "down" }.action as ExtraKeyAction.Scroll
        assertEquals(-1, up.rows)
        assertEquals(1, down.rows)
    }

    @Test
    fun micLabelReflectsRecordingState() {
        assertEquals("MIC", ExtraKeys.micLabel(recording = false, transcribing = false))
        assertEquals("STOP", ExtraKeys.micLabel(recording = true, transcribing = false))
        assertEquals("WAIT", ExtraKeys.micLabel(recording = false, transcribing = true))
    }

    @Test
    fun emptyHostsExplainSshAndMosh() {
        assertTrue(dev.neyham.moshvr.ui.HostOnboarding.LINE1.contains("SSH"))
        assertTrue(dev.neyham.moshvr.ui.HostOnboarding.LINE2.contains("mosh-server"))
        assertTrue(dev.neyham.moshvr.ui.HostOnboarding.NETWORK.contains("Internet"))
        assertTrue(dev.neyham.moshvr.ui.HostOnboarding.RETRY.contains("CONNECT"))
    }
}

class SessionChromeRegressionTest {

    @Test
    fun deadTabIsReadableAscii() {
        assertEquals("[dead] Sim Host", SessionChrome.tabTitle("Sim Host", finished = true))
        assertEquals("Sim Host", SessionChrome.tabTitle("Sim Host", finished = false))
        assertFalse(SessionChrome.tabTitle("x", true).contains("✝"))
    }

    @Test
    fun cycleWrapsAndHandlesEmpty() {
        assertEquals(-1, SessionChrome.cycleIndex(0, 0, 1))
        assertEquals(1, SessionChrome.cycleIndex(0, 3, 1))
        assertEquals(2, SessionChrome.cycleIndex(0, 3, -1))
        assertEquals(0, SessionChrome.cycleIndex(2, 3, 1))
    }
}

class ImmersiveGestureRegressionTest {

    @Test
    fun doesNotRebindListenerOnResume() {
        assertTrue(ImmersiveGestures.shouldBindListener(alreadyBound = false))
        assertFalse(ImmersiveGestures.shouldBindListener(alreadyBound = true))
    }

    @Test
    fun mapsSwipeAndTap() {
        assertEquals(
            ImmersiveGestures.Action.CyclePrev,
            ImmersiveGestures.interpret(changed = 1, pressed = true, swipeLeft = 1, swipeRight = 2, tapThumb = 4),
        )
        assertEquals(
            ImmersiveGestures.Action.CycleNext,
            ImmersiveGestures.interpret(changed = 2, pressed = true, swipeLeft = 1, swipeRight = 2, tapThumb = 4),
        )
        assertEquals(
            ImmersiveGestures.Action.ToggleMatrix,
            ImmersiveGestures.interpret(changed = 4, pressed = true, swipeLeft = 1, swipeRight = 2, tapThumb = 4),
        )
        assertNull(
            ImmersiveGestures.interpret(changed = 1, pressed = false, swipeLeft = 1, swipeRight = 2, tapThumb = 4),
        )
    }
}

class MoshBootstrapRegressionTest {

    @Test
    fun startCommandForcesUtf8Locale() {
        assertTrue(MoshBootstrap.SERVER_START_COMMAND.contains("LANG="))
        assertTrue(MoshBootstrap.SERVER_START_COMMAND.contains("LC_ALL="))
        assertTrue(MoshBootstrap.SERVER_START_COMMAND.contains("exec mosh-server new -c 256"))
        assertTrue(MoshBootstrap.SERVER_START_COMMAND.contains("/opt/homebrew/bin"))
    }

    @Test
    fun parseConnectReadsHandshake() {
        val ep = MoshBootstrap.parseConnect("noise\nMOSH CONNECT 60001 8Yu9wVVpYgIAQr05nu8J5Q\n")
        assertNotNull(ep)
        assertEquals(60001, ep!!.port)
        assertEquals("8Yu9wVVpYgIAQr05nu8J5Q", ep.key)
        assertNull(MoshBootstrap.parseConnect("mosh-server needs a UTF-8 native locale"))
    }
}

class TerminalPointerRegressionTest {

    @Test
    fun herdrClicksMustNotOpenKeyboard() {
        assertTrue(dev.neyham.moshvr.ui.TerminalPointer.shouldReportClick(true))
        // alt screen without mouse tracking must not emit CSI (that was the garbage)
        assertFalse(dev.neyham.moshvr.ui.TerminalPointer.shouldReportClick(false))
        assertFalse(
            dev.neyham.moshvr.ui.TerminalPointer.shouldShowImeOnTap(
                dev.neyham.moshvr.ui.TerminalPointer.shouldReportClick(true),
            ),
        )
        assertTrue(
            dev.neyham.moshvr.ui.TerminalPointer.shouldShowImeOnTap(
                dev.neyham.moshvr.ui.TerminalPointer.shouldReportClick(false),
            ),
        )
    }

    @Test
    fun controllerConfirmMustNotTypeIntoPty() {
        val mouse = android.view.InputDevice.SOURCE_MOUSE
        val keyboard = android.view.InputDevice.SOURCE_KEYBOARD
        assertTrue(
            dev.neyham.moshvr.ui.TerminalPointer.shouldSwallowControllerKey(
                mouse, android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            ),
        )
        assertTrue(
            dev.neyham.moshvr.ui.TerminalPointer.shouldSwallowControllerKey(
                mouse, android.view.KeyEvent.KEYCODE_ENTER,
            ),
        )
        assertTrue(
            dev.neyham.moshvr.ui.TerminalPointer.shouldSwallowControllerKey(
                android.view.InputDevice.SOURCE_GAMEPAD, android.view.KeyEvent.KEYCODE_BUTTON_A,
            ),
        )
        assertFalse(
            dev.neyham.moshvr.ui.TerminalPointer.shouldSwallowControllerKey(
                keyboard, android.view.KeyEvent.KEYCODE_ENTER,
            ),
        )
        assertFalse(
            dev.neyham.moshvr.ui.TerminalPointer.shouldSwallowControllerKey(
                keyboard, android.view.KeyEvent.KEYCODE_A,
            ),
        )
        assertTrue(dev.neyham.moshvr.ui.VoicePtt.isHoldKey(android.view.KeyEvent.KEYCODE_BUTTON_X))
        assertFalse(dev.neyham.moshvr.ui.VoicePtt.isHoldKey(android.view.KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(dev.neyham.moshvr.ui.VoicePtt.isCancelKey(android.view.KeyEvent.KEYCODE_BUTTON_B))
    }

    @Test
    fun voicePttTogglesOnPressNotRelease() {
        var n = 0
        dev.neyham.moshvr.ui.VoicePtt.onToggle = { n++ }
        try {
            val src = android.view.InputDevice.SOURCE_GAMEPAD
            val x = android.view.KeyEvent.KEYCODE_BUTTON_X
            assertTrue(dev.neyham.moshvr.ui.VoicePtt.dispatch(src, x, true, 0))
            assertTrue(dev.neyham.moshvr.ui.VoicePtt.dispatch(src, x, false, 0))
            assertTrue(dev.neyham.moshvr.ui.VoicePtt.dispatch(src, x, true, 1))
            assertEquals(1, n)
        } finally {
            dev.neyham.moshvr.ui.VoicePtt.onToggle = null
        }
    }
}

class StickScrollRegressionTest {

    @Test
    fun stickUpScrollsUp() {
        assertEquals(0, com.termux.view.StickScroll.rowsForAxis(0.1f))
        assertEquals(-1, com.termux.view.StickScroll.rowsForAxis(-0.4f))
        assertEquals(1, com.termux.view.StickScroll.rowsForAxis(0.95f))
        assertEquals(-1, com.termux.view.StickScroll.rowsForDpad(android.view.KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(1, com.termux.view.StickScroll.rowsForDpad(android.view.KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(0, com.termux.view.StickScroll.rowsForDpad(android.view.KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(com.termux.view.StickScroll.acceptTick(1000L, 1040L))
        assertTrue(com.termux.view.StickScroll.acceptTick(1000L, 1050L))
        assertEquals(
            -0.9f,
            com.termux.view.StickScroll.strongestVertical(-0.9f, 0.1f, 0f, 0f),
            0.001f,
        )
        assertEquals(-1, com.termux.view.StickScroll.tabsForDpad(android.view.KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(1, com.termux.view.StickScroll.tabsForDpad(android.view.KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(0, com.termux.view.StickScroll.tabsForDpad(android.view.KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(-1, com.termux.view.StickScroll.tabsForAxis(-0.5f))
        assertTrue(com.termux.view.StickScroll.preferVertical(0.2f, -0.8f))
        assertFalse(com.termux.view.StickScroll.preferVertical(0.8f, 0.2f))
        assertEquals(0f, com.termux.view.StickScroll.axisIfStick(400f), 0.001f)
        assertEquals(-0.7f, com.termux.view.StickScroll.axisIfStick(-0.7f), 0.001f)
        assertTrue(com.termux.view.StickScroll.isHorizontalScroll(-0.6f, 0.3f))
        assertFalse(com.termux.view.StickScroll.isHorizontalScroll(0.1f, 0.8f))
        assertEquals(-1, dev.neyham.moshvr.ui.StickChrome.edgeTab(-1))
        assertEquals(0, dev.neyham.moshvr.ui.StickChrome.edgeTab(-1))
        assertEquals(0, dev.neyham.moshvr.ui.StickChrome.edgeTab(0))
        assertEquals(1, dev.neyham.moshvr.ui.StickChrome.edgeTab(1))
        assertEquals(350L, dev.neyham.moshvr.ui.StickChrome.MIN_TAB_MS)
        dev.neyham.moshvr.ui.StickChrome.edgeTab(0)
        assertTrue(
            dev.neyham.moshvr.ui.StickChrome.dispatchKey(
                android.view.InputDevice.SOURCE_KEYBOARD,
                android.view.KeyEvent.KEYCODE_DPAD_UP,
                true,
                0,
                null,
            ),
        )
        assertTrue(
            dev.neyham.moshvr.ui.StickChrome.dispatchKey(
                android.view.InputDevice.SOURCE_KEYBOARD,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                false,
                0,
                null,
            ),
        )
    }
}

class VoicePolicyRegressionTest {

    @Test
    fun cloudSttIsOptIn() {
        assertFalse(dev.neyham.moshvr.ui.VoicePolicy.useCloudStt(hasApiKey = false))
        assertTrue(dev.neyham.moshvr.ui.VoicePolicy.useCloudStt(hasApiKey = true))
        assertFalse(dev.neyham.moshvr.ui.VoicePolicy.timeoutUploads())
    }
}

class MicPermissionRegressionTest {

    @Test
    fun immersiveMustNotUseActivityResultLauncher() {
        // Spatial composePanel has no LocalActivityResultRegistryOwner.
        // rememberLauncherForActivityResult crashed IMMERSE on first TerminalPane compose.
        assertEquals(android.Manifest.permission.RECORD_AUDIO, MicPermission.RECORD_AUDIO)
        assertTrue(MicPermission.RATIONALE.contains("API credentials"))
        assertEquals(MicPermission.Decision.Granted, MicPermission.classify(true, false, true))
        assertEquals(MicPermission.Decision.Denied, MicPermission.classify(false, true, true))
        assertEquals(MicPermission.Decision.DontAskAgain, MicPermission.classify(false, false, true))
        assertEquals(MicPermission.Decision.Denied, MicPermission.classify(false, false, false))
    }
}

class ImmersiveLayoutRegressionTest {

    @Test
    fun terminalIsCinemaWallNotTablet() {
        assertTrue(ImmersiveLayout.WIDTH_M >= 3.0f)
        assertTrue(ImmersiveLayout.HEIGHT_M >= 1.6f)
        assertTrue(ImmersiveLayout.WIDTH_M * ImmersiveLayout.HEIGHT_M >
            ImmersiveLayout.LEGACY_WIDTH_M * ImmersiveLayout.LEGACY_HEIGHT_M * 3f)
        assertTrue(ImmersiveLayout.horizontalFovDegrees() >= 70f)
    }

    @Test
    fun layoutHasRoomForARealTerminal() {
        assertTrue(ImmersiveLayout.layoutPixels() > ImmersiveLayout.legacyLayoutPixels())
        assertTrue(ImmersiveLayout.DEFAULT_FONT_PX < dev.neyham.moshvr.ui.PanelLayout.LEGACY_FONT_PX)
        assertTrue(dev.neyham.moshvr.ui.PanelLayout.DEFAULT_FONT_PX < dev.neyham.moshvr.ui.PanelLayout.LEGACY_FONT_PX)
        assertTrue(dev.neyham.moshvr.ui.PanelLayout.DEFAULT_FONT_PX <= 24)
        assertEquals(1920, ImmersiveLayout.LAYOUT_WIDTH_PX)
        assertEquals(1080, ImmersiveLayout.LAYOUT_HEIGHT_PX)
        assertTrue(ImmersiveLayout.textureFitsEyeBuffer())
        assertEquals(160, ImmersiveLayout.LAYOUT_DPI)
    }
}

class AgentDetectorRegressionTest {

    @Test
    fun detectsClaudeAndIgnoresNoise() {
        assertEquals(AgentKind.CLAUDE_CODE, AgentDetector.detect("esc to interrupt"))
        assertEquals(AgentKind.CODEX, AgentDetector.detect("OpenAI Codex session"))
        assertNull(AgentDetector.detect("ls -la\nfile.txt"))
    }
}
