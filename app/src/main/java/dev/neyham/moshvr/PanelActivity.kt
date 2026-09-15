package dev.neyham.moshvr

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.neyham.moshvr.ui.ControllerMotion
import dev.neyham.moshvr.ui.InputGate
import dev.neyham.moshvr.ui.MicPermission
import dev.neyham.moshvr.ui.MoshVrRoot
import dev.neyham.moshvr.ui.StickChrome
import dev.neyham.moshvr.ui.VoicePtt
import dev.neyham.moshvr.voice.VoiceSession

/** 2D panel mode: a comfortable flat window floating in home/passthrough. */
class PanelActivity : ComponentActivity() {
    private val inputGate = InputGate()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        val app = application as MoshVrApp
        setContent {
            MoshVrRoot(
                inputAllowed = { inputGate.allowDispatch() },
                sessionManager = app.sessionManager,
                profileStore = app.profileStore,
                settingsStore = app.settingsStore,
                clipCorners = false,
                modeSwitchLabel = "[ IMMERSE ]",
                onModeSwitch = { HybridHandoff.leavePanelForImmersive(this@PanelActivity) },
            )
        }
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

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!inputGate.allowDispatch()) return true
        val view = (application as MoshVrApp).sessionManager.active?.client?.view
        if (ControllerMotion.dispatch(view, event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    // AndroidX exposes the inherited dispatch implementation with a library-group
    // annotation; forwarding unhandled events is required for normal keyboard/IME input.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!inputGate.allowDispatch()) return true
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
}
