package dev.neyham.moshvr

import android.view.KeyEvent

/**
 * Spatial SDK controller button bits (com.meta.spatial.runtime.ButtonBits), mirrored so the
 * mapping below stays plain Kotlin and unit-testable.
 */
object Pad {
    const val A = 1
    const val B = 2
    const val X = 4
    const val Y = 8
    const val TRIGGER_L = 16
    const val TRIGGER_R = 32
    const val GRIP_L = 64
    const val GRIP_R = 128
    const val L_LEFT = 256
    const val L_RIGHT = 512
    const val R_LEFT = 1024
    const val R_RIGHT = 2048
    const val L_UP = 4096
    const val L_DOWN = 8192
    const val R_UP = 16384
    const val R_DOWN = 32768
    const val L_CLICK = 65536
    const val R_CLICK = 131072
}

sealed class ControllerAction {
    data object Enter : ControllerAction()
    data object Backspace : ControllerAction()
    data object Esc : ControllerAction()
    data object Tab : ControllerAction()
    data object CtrlC : ControllerAction()
    data object ToggleKeyboard : ControllerAction()
    data object TalkStart : ControllerAction()
    data object TalkEnd : ControllerAction()
    data class Arrow(val keyCode: Int) : ControllerAction()
    data class Scroll(val rows: Int) : ControllerAction()
    data class HerdrTab(val direction: Int) : ControllerAction()
    data class HerdrWorkspace(val direction: Int) : ControllerAction()
}

/**
 * Both Touch controllers, laid out after the user's voice-remote habits. Triggers stay with the
 * system for pointing and clicking.
 *
 * Right: grip held = talk, A = Enter, B = Backspace, stick ←→ herdr tab, ↑↓ herdr workspace,
 *        stick click = Ctrl+C.
 * Left:  X = keyboard, Y = Esc, stick = arrow keys (repeat while held), grip + stick ↑↓ = scroll,
 *        stick click = Tab.
 */
class ControllerShortcuts {
    private var prev = 0
    private var heldArrow = 0
    private var nextRepeatAt = 0L
    private var scrollTicks = 0

    fun update(state: Int, nowMs: Long): List<ControllerAction> {
        val pressed = state and prev.inv()
        val released = prev and state.inv()
        prev = state
        val out = ArrayList<ControllerAction>(2)
        fun edge(bit: Int) = pressed and bit != 0

        if (edge(Pad.GRIP_R)) out += ControllerAction.TalkStart
        if (released and Pad.GRIP_R != 0) out += ControllerAction.TalkEnd
        if (edge(Pad.A)) out += ControllerAction.Enter
        if (edge(Pad.B)) out += ControllerAction.Backspace
        if (edge(Pad.R_CLICK)) out += ControllerAction.CtrlC
        if (edge(Pad.R_LEFT)) out += ControllerAction.HerdrTab(-1)
        if (edge(Pad.R_RIGHT)) out += ControllerAction.HerdrTab(1)
        if (edge(Pad.R_UP)) out += ControllerAction.HerdrWorkspace(-1)
        if (edge(Pad.R_DOWN)) out += ControllerAction.HerdrWorkspace(1)

        if (edge(Pad.X)) out += ControllerAction.ToggleKeyboard
        if (edge(Pad.Y)) out += ControllerAction.Esc
        if (edge(Pad.L_CLICK)) out += ControllerAction.Tab

        leftStick(state, pressed, nowMs)?.let { out += it }
        return out
    }

    /** Left stick: one action on push, then auto-repeat while held, like a held arrow key. */
    private fun leftStick(state: Int, pressed: Int, nowMs: Long): ControllerAction? {
        val dir = LEFT_DIRS.firstOrNull { state and it != 0 } ?: 0
        if (dir == 0) {
            heldArrow = 0
            return null
        }
        val scrolling = state and Pad.GRIP_L != 0 && (dir == Pad.L_UP || dir == Pad.L_DOWN)
        val fresh = pressed and dir != 0 || dir != heldArrow
        if (fresh) {
            heldArrow = dir
            scrollTicks = 0
            nextRepeatAt = nowMs + if (scrolling) SCROLL_REPEAT_MS else ARROW_FIRST_REPEAT_MS
        } else {
            if (nowMs < nextRepeatAt) return null
            nextRepeatAt = nowMs + if (scrolling) SCROLL_REPEAT_MS else ARROW_REPEAT_MS
        }
        return if (scrolling) {
            // Starts fine-grained, speeds up the longer the stick is held.
            val rows = scrollRows(scrollTicks++)
            ControllerAction.Scroll(if (dir == Pad.L_UP) -rows else rows)
        } else {
            ControllerAction.Arrow(ARROW_KEYS.getValue(dir))
        }
    }

    companion object {
        const val ARROW_FIRST_REPEAT_MS = 400L
        const val ARROW_REPEAT_MS = 110L
        const val SCROLL_REPEAT_MS = 50L
        const val SCROLL_MAX_ROWS = 8

        fun scrollRows(ticksHeld: Int): Int = (1 + ticksHeld / 3).coerceAtMost(SCROLL_MAX_ROWS)

        /** Only triggers click panels, so A and X are free for shortcuts. */
        const val CLICK_BUTTONS = Pad.TRIGGER_L or Pad.TRIGGER_R

        private val LEFT_DIRS = intArrayOf(Pad.L_UP, Pad.L_DOWN, Pad.L_LEFT, Pad.L_RIGHT)
        private val ARROW_KEYS = mapOf(
            Pad.L_UP to KeyEvent.KEYCODE_DPAD_UP,
            Pad.L_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            Pad.L_LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
            Pad.L_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }
}
