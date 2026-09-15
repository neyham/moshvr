package dev.neyham.moshvr

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.composePanel
import com.meta.spatial.core.Entity
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.runtime.LayerConfig
import com.meta.spatial.runtime.MicrogestureBits
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.MicrogesturesSystem
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.vr.VRFeature
import dev.neyham.moshvr.ui.ControllerMotion
import dev.neyham.moshvr.ui.InputGate
import dev.neyham.moshvr.ui.MicPermission
import dev.neyham.moshvr.ui.StickChrome
import dev.neyham.moshvr.ui.VoicePtt
import dev.neyham.moshvr.ui.MatrixRain
import dev.neyham.moshvr.ui.MoshVrColors
import dev.neyham.moshvr.ui.ChromeText
import dev.neyham.moshvr.ui.MoshVrRoot
import dev.neyham.moshvr.voice.VoiceSession

/**
 * Immersive mode: a cinema-sized terminal wall over passthrough, or inside
 * the "construct" — a black void with digital rain. Grab the wall to place it.
 */
class ImmersiveActivity : AppSystemActivity() {
    private val inputGate = InputGate()
    private var spatialDialogOpen = false


    private val matrixMode = mutableStateOf(false)
    private val recenterNote = mutableStateOf<String?>(null)
    private val matrixPanels = mutableListOf<Entity>()
    private var mainPanel: Entity? = null
    private var microgesturesBound = false

    override fun registerFeatures(): List<SpatialFeature> {
        return listOf(VRFeature(this), ComposeFeature())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        inputGate.resumed = true
        inputGate.focused = hasWindowFocus()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!inputGate.allowDispatch()) return true
        return super.dispatchTouchEvent(event)
    }

    override fun onPause() {
        inputGate.resumed = false;
        VoiceSession.abandonFrom(this)
        super.onPause()
    }

    override fun onStop() {
        VoiceSession.abandonFrom(this)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        inputGate.focused = hasFocus
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) VoiceSession.abandonFrom(this)
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        MicPermission.onRequestPermissionsResult(this, requestCode, grantResults)
    }

    fun recenterView(): Boolean {
        val panel = mainPanel
        if (panel == null) {
            recenterNote.value = "Recenter failed: panel not ready"
            return false
        }
        return try {
            panel.setComponent(Transform(ImmersiveLayout.pose()))
            matrixPanels.forEachIndexed { index, entity ->
                entity.setComponent(Transform(ImmersiveLayout.matrixPose(index)))
            }
            scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
            recenterNote.value = "View reset"
            true
        } catch (_: Exception) {
            recenterNote.value = "Recenter failed"
            false
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!inputGate.allowDispatch()) return true
        if (spatialDialogOpen) return super.dispatchGenericMotionEvent(event)
        val view = (application as MoshVrApp).sessionManager.active?.client?.view
        if (ControllerMotion.dispatch(view, event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!inputGate.allowDispatch()) return true
        if (spatialDialogOpen) return super.dispatchKeyEvent(event)
        val down = event.action == KeyEvent.ACTION_DOWN
        if ((down || event.action == KeyEvent.ACTION_UP) &&
            VoicePtt.dispatch(event.source, event.keyCode, down, event.repeatCount)
        ) {
            return true
        }
        val view = (application as MoshVrApp).sessionManager.active?.client?.view
        if (StickChrome.dispatchKey(
                event.source, event.keyCode, down, event.repeatCount, view,
            )
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onSceneReady() {
        super.onSceneReady()
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
        scene.enablePassthrough(true)

        mainPanel = Entity.create(
            Panel(R.id.panel_main),
            Transform(ImmersiveLayout.pose()),
            Grabbable(),
        )

        // The construct: rain panels surrounding the user, hidden until Matrix mode.
        matrixPanels += Entity.create(
            Panel(R.id.panel_matrix_back),
            Transform(ImmersiveLayout.matrixBack()),
            Visible(false),
        )
        matrixPanels += Entity.create(
            Panel(R.id.panel_matrix_left),
            Transform(ImmersiveLayout.matrixLeft()),
            Visible(false),
        )
        matrixPanels += Entity.create(
            Panel(R.id.panel_matrix_right),
            Transform(ImmersiveLayout.matrixRight()),
            Visible(false),
        )

        runCatching { registerMicrogestures() }
    }

    /**
     * Hand microgestures (thumb on index finger, no controller needed):
     * swipe left/right cycles session tabs, thumb tap toggles the construct.
     */
    private fun registerMicrogestures() {
        // VRFeature already registers MicrogesturesSystem; a second register() crashes on resume.
        if (!ImmersiveGestures.shouldBindListener(microgesturesBound)) return
        val system = systemManager.tryFindSystem<MicrogesturesSystem>() ?: return
        microgesturesBound = true
        val bits = MicrogestureBits
        val swipeLeft = bits.LeftMicrogestureSwipeLeft or bits.RightMicrogestureSwipeLeft
        val swipeRight = bits.LeftMicrogestureSwipeRight or bits.RightMicrogestureSwipeRight
        val tapThumb = bits.LeftMicrogestureTapThumb or bits.RightMicrogestureTapThumb
        system.addListener { changed, pressed ->
            if (!inputGate.allowDispatch() || spatialDialogOpen) return@addListener
            val app = application as MoshVrApp
            when (ImmersiveGestures.interpret(changed, pressed, swipeLeft, swipeRight, tapThumb)) {
                ImmersiveGestures.Action.CyclePrev -> app.sessionManager.cycleActive(-1)
                ImmersiveGestures.Action.CycleNext -> app.sessionManager.cycleActive(1)
                ImmersiveGestures.Action.ToggleMatrix -> setMatrixMode(!matrixMode.value)
                null -> Unit
            }
        }
    }

    private fun setMatrixMode(enabled: Boolean) {
        matrixMode.value = enabled
        scene.enablePassthrough(!enabled)
        matrixPanels.forEach { it.setComponent(Visible(enabled)) }
    }

    override fun registerPanels(): List<PanelRegistration> {
        val app = application as MoshVrApp
        val rainPanel: (Int) -> PanelRegistration = { panelId ->
            PanelRegistration(panelId) {
                config {
                    themeResourceId = R.style.PanelAppThemeTransparent
                    width = 5.4f
                    height = 3.2f
                    layoutWidthInDp = 1080f
                    layoutHeightInDp = 640f
                    enableTransparent = false
                    includeGlass = false
                }
                composePanel {
                    setContent { MatrixRain(Modifier.fillMaxSize()) }
                }
            }
        }
        return listOf(
            PanelRegistration(R.id.panel_main) {
                config {
                    themeResourceId = R.style.PanelAppThemeTransparent
                    width = ImmersiveLayout.WIDTH_M
                    height = ImmersiveLayout.HEIGHT_M
                    layoutDpi = ImmersiveLayout.LAYOUT_DPI
                    layoutWidthInPx = ImmersiveLayout.LAYOUT_WIDTH_PX
                    layoutHeightInPx = ImmersiveLayout.LAYOUT_HEIGHT_PX
                    layoutWidthInDp = ImmersiveLayout.LAYOUT_WIDTH_DP
                    layoutHeightInDp = ImmersiveLayout.LAYOUT_HEIGHT_DP
                    layerConfig = LayerConfig()
                    enableTransparent = true
                    includeGlass = false
                }
                composePanel {
                    setContent {
                        MoshVrRoot(
                            spatialDialogs = true,
                            onSpatialDialogChanged = { spatialDialogOpen = it },
                            inputAllowed = { inputGate.allowDispatch() },
                sessionManager = app.sessionManager,
                            profileStore = app.profileStore,
                            settingsStore = app.settingsStore,
                            startOnHome = app.sessionManager.active == null,
                            initialFontSizePx = ImmersiveLayout.DEFAULT_FONT_PX,
                            modeSwitchLabel = "[ 2D ]",
                            onModeSwitch = { HybridHandoff.leaveImmersiveForPanel(this@ImmersiveActivity) },
                            extraTopBarContent = {
                                ChromeText("[ RECENTER ]", MoshVrColors.TextSecondary) {
                                    recenterView()
                                }
                                recenterNote.value?.let { note ->
                                    ChromeText(note, MoshVrColors.TextSecondary) { recenterNote.value = null }
                                }
                                ChromeText(
                                    if (matrixMode.value) "[ REALITY ]" else "[ CONSTRUCT ]",
                                    MoshVrColors.Green,
                                ) { setMatrixMode(!matrixMode.value) }
                            },
                        )
                    }
                }
            },
            rainPanel(R.id.panel_matrix_back),
            rainPanel(R.id.panel_matrix_left),
            rainPanel(R.id.panel_matrix_right),
        )
    }

}
