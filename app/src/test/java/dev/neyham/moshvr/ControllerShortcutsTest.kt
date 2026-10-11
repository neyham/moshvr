package dev.neyham.moshvr

import android.view.KeyEvent
import com.meta.spatial.runtime.ButtonBits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerShortcutsTest {

    @Test
    fun padBitsMatchTheSdk() {
        assertEquals(ButtonBits.ButtonA, Pad.A)
        assertEquals(ButtonBits.ButtonY, Pad.Y)
        assertEquals(ButtonBits.ButtonTriggerR, Pad.TRIGGER_R)
        assertEquals(ButtonBits.ButtonSqueezeL, Pad.GRIP_L)
        assertEquals(ButtonBits.ButtonSqueezeR, Pad.GRIP_R)
        assertEquals(ButtonBits.ButtonThumbLL, Pad.L_LEFT)
        assertEquals(ButtonBits.ButtonThumbRR, Pad.R_RIGHT)
        assertEquals(ButtonBits.ButtonThumbLU, Pad.L_UP)
        assertEquals(ButtonBits.ButtonThumbRD, Pad.R_DOWN)
        assertEquals(ButtonBits.ButtonThumbLClick, Pad.L_CLICK)
        assertEquals(ButtonBits.ButtonThumbRClick, Pad.R_CLICK)
    }

    @Test
    fun rightHandTalksSendsAndSwitchesHerdr() {
        val c = ControllerShortcuts()
        assertEquals(listOf(ControllerAction.TalkStart), c.update(Pad.GRIP_R, 0))
        assertEquals(emptyList<ControllerAction>(), c.update(Pad.GRIP_R, 16))
        assertEquals(listOf(ControllerAction.TalkEnd), c.update(0, 32))
        assertEquals(listOf(ControllerAction.Enter), c.update(Pad.A, 48))
        assertEquals(emptyList<ControllerAction>(), c.update(Pad.A, 64)) // held: once only
        assertEquals(listOf(ControllerAction.Backspace), c.update(Pad.B, 80))
        assertEquals(listOf(ControllerAction.HerdrTab(-1)), c.update(Pad.R_LEFT, 96))
        assertEquals(listOf(ControllerAction.HerdrWorkspace(1)), c.update(Pad.R_DOWN, 112))
        assertEquals(listOf(ControllerAction.CtrlC), c.update(Pad.R_CLICK, 128))
    }

    @Test
    fun leftHandKeyboardEscTabAndRepeatingArrows() {
        val c = ControllerShortcuts()
        assertEquals(listOf(ControllerAction.ToggleKeyboard), c.update(Pad.X, 0))
        assertEquals(listOf(ControllerAction.Esc), c.update(Pad.Y, 10))
        assertEquals(listOf(ControllerAction.Tab), c.update(Pad.L_CLICK, 20))
        c.update(0, 30)
        assertEquals(listOf(ControllerAction.Arrow(KeyEvent.KEYCODE_DPAD_UP)), c.update(Pad.L_UP, 1000))
        assertTrue(c.update(Pad.L_UP, 1200).isEmpty())
        assertEquals(listOf(ControllerAction.Arrow(KeyEvent.KEYCODE_DPAD_UP)), c.update(Pad.L_UP, 1401))
        assertTrue(c.update(Pad.L_UP, 1450).isEmpty())
        assertEquals(1, c.update(Pad.L_UP, 1512).size)
    }

    @Test
    fun leftGripScrollFallbackIsOneRowAtAnEvenPace() {
        val c = ControllerShortcuts()
        c.update(Pad.GRIP_L, 0)
        // Waits a beat first so the SDK's native stick scroll can take over.
        assertTrue(c.update(Pad.GRIP_L or Pad.L_UP, 10).isEmpty())
        assertTrue(c.update(Pad.GRIP_L or Pad.L_UP, 100).isEmpty())
        var t = 10 + ControllerShortcuts.SCROLL_FIRST_MS
        repeat(5) {
            assertEquals(listOf(ControllerAction.Scroll(-1)), c.update(Pad.GRIP_L or Pad.L_UP, t))
            t += ControllerShortcuts.SCROLL_REPEAT_MS
        }
    }

    @Test
    fun nativeStickScrollOnlyPassesWhileTheLeftGripIsHeld() {
        val sc = dev.neyham.moshvr.ui.StickChrome
        val scroll = android.view.MotionEvent.ACTION_SCROLL
        sc.sticksMapped = true
        sc.nativeScrollAllowed = false
        assertTrue(sc.dropPanelScroll(scroll, 1_000))
        sc.nativeScrollAllowed = true
        assertFalse(sc.dropPanelScroll(scroll, 2_000))
        assertTrue(sc.nativeScrollActive(2_100))
        assertFalse(sc.nativeScrollActive(2_500))
        sc.sticksMapped = false
        sc.nativeScrollAllowed = false
        assertFalse(sc.dropPanelScroll(scroll, 3_000)) // 2D mode: untouched
    }

    @Test
    fun triggersAreNeverShortcuts() {
        val c = ControllerShortcuts()
        assertTrue(c.update(Pad.TRIGGER_L or Pad.TRIGGER_R, 0).isEmpty())
        assertEquals(Pad.TRIGGER_L or Pad.TRIGGER_R, ControllerShortcuts.CLICK_BUTTONS)
    }
}

class ShortcutsCheatsheetTest {
    @Test
    fun cheatsheetExplainsEveryBottomBarKey() {
        val documented = dev.neyham.moshvr.ui.ShortcutsCheatsheet.sections
            .flatMap { it.rows }.joinToString(" ") { it.first }
        dev.neyham.moshvr.ui.ExtraKeys.catalog
            .filter { it.id in setOf("ctrl-c", "up", "down") }
            .forEach { assertTrue(it.label, documented.contains(it.label)) }
    }
}
