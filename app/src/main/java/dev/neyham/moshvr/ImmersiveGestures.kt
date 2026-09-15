package dev.neyham.moshvr

/**
 * Pure mapping for immersive hand microgestures.
 * [ImmersiveActivity] must attach this to the *existing* MicrogesturesSystem
 * (VRFeature already registers one; a second register() crashes on resume).
 */
object ImmersiveGestures {
    enum class Action { CyclePrev, CycleNext, ToggleMatrix }

    fun interpret(
        changed: Int,
        pressed: Boolean,
        swipeLeft: Int,
        swipeRight: Int,
        tapThumb: Int,
    ): Action? {
        if (!pressed || changed == 0) return null
        return when {
            changed and swipeLeft != 0 -> Action.CyclePrev
            changed and swipeRight != 0 -> Action.CycleNext
            changed and tapThumb != 0 -> Action.ToggleMatrix
            else -> null
        }
    }

    /** True once a listener is bound; onSceneReady can fire again on resume. */
    fun shouldBindListener(alreadyBound: Boolean): Boolean = !alreadyBound
}
