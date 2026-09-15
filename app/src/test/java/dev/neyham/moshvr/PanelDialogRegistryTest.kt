package dev.neyham.moshvr

import dev.neyham.moshvr.ui.PanelDialogRegistry
import org.junit.Assert.*
import org.junit.Test

class PanelDialogRegistryTest {
    @Test fun disposingPreviousDialogKeepsReplacementVisible() {
        val state = PanelDialogRegistry<Any>()
        val previous = Any()
        val replacement = Any()
        state.add(previous)
        state.add(replacement)
        state.remove(previous)
        assertSame(replacement, state.top)
        state.remove(replacement)
        assertNull(state.top)
    }

    @Test fun closingTopRestoresUnderlyingDialogWithoutDuplicatingIt() {
        val state = PanelDialogRegistry<Any>()
        val first = Any()
        val second = Any()
        state.add(first)
        state.add(first)
        state.add(second)
        state.remove(second)
        assertSame(first, state.top)
        state.remove(first)
        assertNull(state.top)
    }
}
