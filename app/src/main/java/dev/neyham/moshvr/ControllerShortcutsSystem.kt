package dev.neyham.moshvr

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.ControllerType
import dev.neyham.moshvr.ui.StickChrome

/**
 * Reads both Touch controllers every frame and turns button edges into [ControllerAction]s.
 * Hands are ignored: pinches are clicks and microgestures have their own listener.
 */
class ControllerShortcutsSystem(
    private val enabled: () -> Boolean,
    private val onAction: (ControllerAction) -> Unit,
) : SystemBase() {
    private val shortcuts = ControllerShortcuts()
    private val main = Handler(Looper.getMainLooper())

    override fun execute() {
        var state = 0
        Query.where { has(Controller.id) }.eval().forEach { entity ->
            val controller = entity.getComponent<Controller>()
            if (controller.isActive && controller.type == ControllerType.CONTROLLER) {
                state = state or controller.buttonState
            }
        }
        StickChrome.nativeScrollAllowed = ControllerShortcuts.nativeScrollAllowed(state)
        // Keep edge tracking current even while disabled, so nothing fires on re-enable.
        val actions = shortcuts.update(state, SystemClock.uptimeMillis())
        if (!enabled()) return
        actions.forEach { action ->
            runCatching { android.util.Log.d("moshvr-keys", "vr pad $action state=0x${Integer.toHexString(state)}") }
            // Actions touch views and Compose state: always run them on the main thread.
            if (Looper.myLooper() == Looper.getMainLooper()) onAction(action) else main.post { onAction(action) }
        }
    }
}
