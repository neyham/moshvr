package dev.neyham.moshvr

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import kotlin.math.atan
import kotlin.math.PI

/**
 * Immersive terminal is a cinema wall, not a floating tablet.
 * Old panel was 1.55×0.95 m at 1.35 m (~47×10 cells at 42 px). That wasted VR.
 */
object ImmersiveLayout {
    /** Physical wall, meters. ~145" diagonal; grab to move if the room is tight. */
    const val WIDTH_M = 3.2f
    const val HEIGHT_M = 1.8f
    const val DISTANCE_M = 2.0f
    const val CENTER_Y_M = 1.50f

    /**
     * Quest default panel DPI is 288. 2048 dp at 288 is ~3686 px, past the
     * 2064 eye-buffer, so hits and text no longer line up. Pin 160 dpi + 1920 px.
     */
    const val LAYOUT_DPI = 160
    const val LAYOUT_WIDTH_PX = 1920
    const val LAYOUT_HEIGHT_PX = 1080
    const val LAYOUT_WIDTH_DP = 1920f
    const val LAYOUT_HEIGHT_DP = 1080f
    const val EYEBUFFER_WIDTH_PX = 2064

    /** Cinema wall default. 2D uses [dev.neyham.moshvr.ui.PanelLayout.DEFAULT_FONT_PX]. */
    const val DEFAULT_FONT_PX = 26

    const val LEGACY_WIDTH_M = 1.55f
    const val LEGACY_HEIGHT_M = 0.95f
    const val LEGACY_LAYOUT_WIDTH_DP = 1180f
    const val LEGACY_LAYOUT_HEIGHT_DP = 720f

    fun pose(): Pose = Pose(Vector3(0f, CENTER_Y_M, DISTANCE_M), Quaternion())

    fun matrixBack(): Pose = Pose(Vector3(0f, 1.9f, 3.4f), Quaternion())
    fun matrixLeft(): Pose = Pose(Vector3(-2.9f, 1.9f, 1.3f), Quaternion(pitch = 0f, yaw = 65f, roll = 0f))
    fun matrixRight(): Pose = Pose(Vector3(2.9f, 1.9f, 1.3f), Quaternion(pitch = 0f, yaw = -65f, roll = 0f))

    fun matrixPose(index: Int): Pose = when (index) {
        0 -> matrixBack()
        1 -> matrixLeft()
        else -> matrixRight()
    }

    fun horizontalFovDegrees(): Float {
        val half = atan((WIDTH_M / 2.0) / DISTANCE_M.toDouble())
        return ((2.0 * half) * 180.0 / PI).toFloat()
    }

    fun layoutPixels(): Int = LAYOUT_WIDTH_PX * LAYOUT_HEIGHT_PX

    fun legacyLayoutPixels(): Int =
        (LEGACY_LAYOUT_WIDTH_DP * LEGACY_LAYOUT_HEIGHT_DP).toInt()

    fun textureFitsEyeBuffer(): Boolean =
        LAYOUT_WIDTH_PX <= EYEBUFFER_WIDTH_PX && LAYOUT_HEIGHT_PX <= EYEBUFFER_WIDTH_PX
}
