package dev.neyham.moshvr.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.view.TerminalView

/**
 * Quest controller clicks arrive as SOURCE_MOUSE *and* as confirm keys
 * (DPAD_CENTER / ENTER / BUTTON_A). Mouse CSI must only go out when the
 * remote asked for it; controller keys must never be typed into the PTY.
 */
object TerminalPointer {
    fun shouldReportClick(mouseTrackingActive: Boolean): Boolean = mouseTrackingActive

    fun shouldShowImeOnTap(reportClick: Boolean): Boolean = !reportClick

    fun shouldSwallowControllerKey(source: Int, keyCode: Int): Boolean {
        // isFromSource: (source & mask) == mask. A bitwise OR check is wrong
        // because KEYBOARD and GAMEPAD share SOURCE_CLASS_BUTTON.
        val fromController =
            (source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (!fromController) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE,
            -> true
            else -> false
        }
    }
}

/**
 * Stick / DPAD: up-down scrolls the terminal, left-right cycles session tabs.
 * Must run at the Activity so Compose tab buttons cannot steal focus navigation.
 */
object StickChrome {
    const val MIN_TAB_MS = 350L
    @Volatile var onCycleTab: ((Int) -> Unit)? = null
    private var latchedTabDir = 0
    private var lastTabAt = 0L

    fun edgeTab(dir: Int): Int {
        if (dir == 0) {
            latchedTabDir = 0
            return 0
        }
        if (dir == latchedTabDir) return 0
        latchedTabDir = dir
        return dir
    }

    fun maybeCycle(dir: Int) {
        val edge = edgeTab(dir)
        if (edge == 0) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTabAt < MIN_TAB_MS) return
        lastTabAt = now
        onCycleTab?.invoke(edge)
    }

    fun dispatchKey(source: Int, keyCode: Int, down: Boolean, repeat: Int, view: TerminalView?): Boolean {
        val rows = com.termux.view.StickScroll.rowsForDpad(keyCode)
        val tabs = com.termux.view.StickScroll.tabsForDpad(keyCode)
        if (rows == 0 && tabs == 0) return false
        // Quest 2D focus-nav synthesizes DPAD with SOURCE_KEYBOARD after a chrome click.
        if (!down || repeat > 0) return true
        if (rows != 0) view?.scrollLines(rows)
        else maybeCycle(tabs)
        return true
    }

    fun dispatchMotion(view: TerminalView?, event: MotionEvent): Boolean {
        val hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (com.termux.view.StickScroll.isHorizontalScroll(hscroll, vscroll)) {
                val signed = com.termux.view.StickScroll.strongestHorizontal(
                    com.termux.view.StickScroll.axisIfStick(event.getAxisValue(MotionEvent.AXIS_X)),
                    event.getAxisValue(MotionEvent.AXIS_HAT_X),
                    hscroll,
                )
                maybeCycle(com.termux.view.StickScroll.tabsForAxis(signed))
                return true
            }
            if (view?.onGenericMotionEvent(event) == true) return true
            if (view != null && vscroll != 0f) {
                view.scrollLines(if (vscroll > 0f) -1 else 1)
                return true
            }
            return view != null
        }
        val x = com.termux.view.StickScroll.strongestHorizontal(
            com.termux.view.StickScroll.axisIfStick(event.getAxisValue(MotionEvent.AXIS_X)),
            event.getAxisValue(MotionEvent.AXIS_HAT_X),
            com.termux.view.StickScroll.strongestHorizontal(
                hscroll,
                event.getAxisValue(MotionEvent.AXIS_Z),
                event.getAxisValue(MotionEvent.AXIS_RX),
            ),
        )
        val y = com.termux.view.StickScroll.strongestVertical(
            com.termux.view.StickScroll.axisIfStick(event.getAxisValue(MotionEvent.AXIS_Y)),
            event.getAxisValue(MotionEvent.AXIS_RZ),
            event.getAxisValue(MotionEvent.AXIS_RY),
            event.getAxisValue(MotionEvent.AXIS_HAT_Y),
        )
        val dir = com.termux.view.StickScroll.tabsForAxis(x)
        if (com.termux.view.StickScroll.preferVertical(x, y)) {
            if (dir == 0) edgeTab(0)
            return view?.onGenericMotionEvent(event) == true
        }
        if (dir != 0) {
            maybeCycle(dir)
            return true
        }
        edgeTab(0)
        return view?.onGenericMotionEvent(event) == true
    }
}

/** Quest stick events often land on the Activity, not the AndroidView. */
object ControllerMotion {
    fun dispatch(view: TerminalView?, event: MotionEvent): Boolean =
        StickChrome.dispatchMotion(view, event)
}

/** Cloud STT is opt-in. No key → MIC opens the system keyboard. */
object VoicePolicy {
    fun useCloudStt(hasApiKey: Boolean): Boolean = hasApiKey

    /** 20s cap stops and discards. Never auto-upload / auto-send. */
    fun timeoutUploads(): Boolean = false
}

/** Tap-to-toggle talk from the MIC key or a controller face/grip button. */
object VoicePtt {
    @Volatile var onToggle: (() -> Unit)? = null
    @Volatile var onCancel: (() -> Unit)? = null
    @Volatile private var owner: Any? = null

    fun bind(token: Any, toggle: () -> Unit, cancel: () -> Unit) {
        owner = token
        onToggle = toggle
        onCancel = cancel
    }

    fun unbind(token: Any) {
        if (owner === token) {
            owner = null
            onToggle = null
            onCancel = null
        }
    }

    fun isHoldKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_BUTTON_X ||
            keyCode == KeyEvent.KEYCODE_BUTTON_L1 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_R1

    fun isCancelKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_BUTTON_B

    fun dispatch(source: Int, keyCode: Int, down: Boolean, repeat: Int): Boolean {
        if (!TerminalPointer.shouldSwallowControllerKey(source, keyCode)) return false
        if (isCancelKey(keyCode)) {
            if (down && repeat == 0) onCancel?.invoke()
            return true
        }
        if (!isHoldKey(keyCode)) return false
        if (down && repeat == 0) onToggle?.invoke()
        return true
    }
}
