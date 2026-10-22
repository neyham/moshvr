package dev.neyham.moshvr

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.ControllerType

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
        // Keep edge tracking current even while disabled, so nothing fires on re-enable.
        val actions = shortcuts.update(state, SystemClock.uptimeMillis())
        actions.forEach { action ->
            // Releasing talk must always end the take, even with a dialog or [ MOVE ] up.
            val run = { if (enabled() || action == ControllerAction.TalkEnd) onAction(action) }
            // Actions touch views and Compose state: always run them on the main thread,
            // re-checking there so a dialog that opened meanwhile gets no keystrokes.
            if (Looper.myLooper() == Looper.getMainLooper()) run() else main.post(run)
        }
    }
}
