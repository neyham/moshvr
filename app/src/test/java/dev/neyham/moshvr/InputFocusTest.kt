package dev.neyham.moshvr

import dev.neyham.moshvr.ui.InputGate
import org.junit.Assert.*
import org.junit.Test

class InputFocusTest {
    @Test fun inputRequiresBothResumeAndFocus() {
        val gate = InputGate()
        assertFalse(gate.allowDispatch())
        gate.resumed = true
        assertFalse(gate.allowDispatch())
        gate.focused = true
        assertTrue(gate.allowDispatch())
        gate.resumed = false
        assertFalse(gate.allowDispatch())
        gate.resumed = true
        gate.focused = false
        assertFalse(gate.allowDispatch())
    }
    @Test fun lateOldActivityCallbacksCannotDisableNewActivity() {
        val old = InputGate().apply { resumed = true; focused = true }
        val fresh = InputGate().apply { resumed = true; focused = true }
        old.resumed = false
        old.focused = false
        assertTrue(fresh.allowDispatch())
        assertFalse(old.allowDispatch())
    }
}
