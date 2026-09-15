package dev.neyham.moshvr.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Spatial compose panels have no [androidx.activity.compose.LocalActivityResultRegistryOwner].
 * [androidx.activity.compose.rememberLauncherForActivityResult] crashes there on first compose.
 */
object MicPermission {
    const val RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
    const val REQUEST_CODE = 7101
    const val RATIONALE =
        "The in-app microphone records a short clip so cloud speech-to-text can turn it into text. " +
            "Audio, the model/resource ID, and your API credentials are sent to the HTTPS endpoint you configured. " +
            "The terminal works without a microphone."
    const val NEEDED = "Microphone permission needed"
    const val DENIED = "Microphone denied. Tap MIC to see why and try again."
    const val DONT_ASK_AGAIN =
        "Microphone is blocked. Open system Settings → Apps → moshVR → Permissions, enable Microphone, then tap MIC."

    enum class Decision { Idle, Granted, Denied, DontAskAgain }

    var lastDecision by mutableStateOf(Decision.Idle)
        private set

    @Volatile
    var askedOnce: Boolean = false
        private set

    fun findActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? Activity
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun classify(granted: Boolean, shouldShowRationale: Boolean, asked: Boolean): Decision = when {
        granted -> Decision.Granted
        asked && !shouldShowRationale -> Decision.DontAskAgain
        else -> Decision.Denied
    }

    fun request(context: Context): Boolean {
        val activity = findActivity(context) ?: return false
        askedOnce = true
        lastDecision = Decision.Idle
        ActivityCompat.requestPermissions(activity, arrayOf(RECORD_AUDIO), REQUEST_CODE)
        return true
    }

    fun onRequestPermissionsResult(
        activity: Activity,
        requestCode: Int,
        grantResults: IntArray,
    ) {
        if (requestCode != REQUEST_CODE) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        val rationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, RECORD_AUDIO)
        lastDecision = classify(granted, rationale, askedOnce)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
