package dev.neyham.moshvr.ui

/** Each Activity owns its gate: a late pause/focus callback cannot disable its successor. */
class InputGate {
    @Volatile var resumed: Boolean = false
    @Volatile var focused: Boolean = false
    fun allowDispatch(): Boolean = resumed && focused
}
