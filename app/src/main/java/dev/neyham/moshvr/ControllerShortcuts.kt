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
 *        stick click = Ctrl+C (on release, and only for a click that never moved the stick).
 * Left:  X = keyboard, Y = Esc, stick ↑↓ = scroll, grip + stick = arrow keys (repeat while
 *        held), stick click = Tab. Stick ←→ alone does nothing, so a sweeping scroll that passes
 *        sideways can never type a key.
 */
class ControllerShortcuts {
    private var prev = 0
    private var heldArrow = 0
    private var nextRepeatAt = 0L
    private var rightDirs = 0
    private var rightSince = 0L
    private var rightDone = false
    private var rightMovedAt = Long.MIN_VALUE / 2
    private var clickClean = false
    private var scrollSince = 0L

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
        rightStick(state, nowMs)?.let { out += it }
        rightClick(state, pressed, released, nowMs)?.let { out += it }

        if (edge(Pad.X)) out += ControllerAction.ToggleKeyboard
        if (edge(Pad.Y)) out += ControllerAction.Esc
        if (edge(Pad.L_CLICK)) out += ControllerAction.Tab

        leftStick(state, pressed, nowMs)?.let { out += it }
        return out
    }

    /**
     * Right stick: one herdr switch per push. A push rarely starts on one axis (the other one
     * crosses its threshold a frame later), so the direction must settle on a single axis first,
     * and nothing more fires until the stick is back in the centre. A push that stays diagonal
     * does nothing rather than guess.
     */
    private fun rightStick(state: Int, nowMs: Long): ControllerAction? {
        val dirs = state and RIGHT_DIRS
        if (dirs != rightDirs) {
            rightDirs = dirs
            rightSince = nowMs
        }
        if (dirs == 0) {
            rightDone = false
            return null
        }
        if (rightDone) return null
        if (Integer.bitCount(dirs) != 1 || nowMs - rightSince < RIGHT_SETTLE_MS) return null
        rightDone = true
        return when (dirs) {
            Pad.R_LEFT -> ControllerAction.HerdrTab(-1)
            Pad.R_RIGHT -> ControllerAction.HerdrTab(1)
            Pad.R_UP -> ControllerAction.HerdrWorkspace(-1)
            else -> ControllerAction.HerdrWorkspace(1)
        }
    }

    /**
     * Ctrl+C can end an agent, so the stick click must be deliberate. Pushing the stick hard
     * often clicks it too: a click counts only if the stick stayed centred from shortly before
     * the press until the release, and it fires on release.
     */
    private fun rightClick(state: Int, pressed: Int, released: Int, nowMs: Long): ControllerAction? {
        if (state and RIGHT_DIRS != 0) {
            rightMovedAt = nowMs
            clickClean = false
        }
        if (pressed and Pad.R_CLICK != 0) clickClean = nowMs - rightMovedAt >= CLICK_QUIET_MS && state and RIGHT_DIRS == 0
        if (released and Pad.R_CLICK == 0) return null
        val fire = clickClean
        clickClean = false
        return if (fire) ControllerAction.CtrlC else null
    }

    /**
     * Left stick. Alone, ↑↓ scrolls; with the grip held it is an arrow key: one press on push,
     * then auto-repeat while held. Any key reaching the terminal snaps it back to the bottom,
     * so scrolling must never emit one.
     */
    private fun leftStick(state: Int, pressed: Int, nowMs: Long): ControllerAction? {
        val dir = LEFT_DIRS.firstOrNull { state and it != 0 } ?: 0
        val arrows = state and Pad.GRIP_L != 0
        val scrolling = !arrows && (dir == Pad.L_UP || dir == Pad.L_DOWN)
        if (dir == 0 || !arrows && !scrolling) {
            heldArrow = 0
            return null
        }
        // Grip changes start a fresh gesture too, so scroll never turns into a repeating arrow.
        val held = if (arrows) dir or ARROW_MODE else dir
        val fresh = pressed and dir != 0 || held != heldArrow
        var rows = 1
        if (fresh) {
            heldArrow = held
            scrollSince = nowMs
            nextRepeatAt = nowMs + if (scrolling) SCROLL_FIRST_MS else ARROW_FIRST_REPEAT_MS
        } else {
            if (nowMs < nextRepeatAt) return null
            nextRepeatAt = nowMs + if (scrolling) SCROLL_REPEAT_MS else ARROW_REPEAT_MS
            // A long hold speeds up, so a deep scrollback is not a slog.
            if (scrolling && nowMs - scrollSince >= SCROLL_FAST_AFTER_MS) rows = SCROLL_FAST_ROWS
        }
        return if (scrolling) {
            ControllerAction.Scroll(if (dir == Pad.L_UP) -rows else rows)
        } else {
            ControllerAction.Arrow(ARROW_KEYS.getValue(dir))
        }
    }

    companion object {
        const val ARROW_FIRST_REPEAT_MS = 400L
        const val ARROW_REPEAT_MS = 110L
        /**
         * Scrolling is ours alone, at an even pace: one row on push, then repeating, faster after
         * a long hold. (The SDK's own stick scroll varied with how far the stick was pushed and
         * landed wherever the ray pointed, so it is dropped.)
         */
        const val SCROLL_FIRST_MS = 250L
        const val SCROLL_REPEAT_MS = 80L
        const val SCROLL_FAST_AFTER_MS = 1_200L
        const val SCROLL_FAST_ROWS = 3
        /** The right stick must be centred this long before a click counts as Ctrl+C. */
        const val CLICK_QUIET_MS = 300L
        private const val ARROW_MODE = 1 shl 30
        /** How long a right-stick push must sit on one single axis before it counts. */
        const val RIGHT_SETTLE_MS = 50L
        private const val RIGHT_DIRS = Pad.R_LEFT or Pad.R_RIGHT or Pad.R_UP or Pad.R_DOWN

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
