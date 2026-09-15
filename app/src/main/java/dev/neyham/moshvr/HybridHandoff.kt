package dev.neyham.moshvr

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Horizon hybrid switch. If the 2D task stays alive after IMMERSE, Home keeps
 * focus, the XR session goes idle, and the headset looks frozen.
 */
object HybridHandoff {
    fun immersiveIntent(context: Context): Intent =
        Intent(context, ImmersiveActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun leavePanelForImmersive(activity: Activity) {
        activity.startActivity(immersiveIntent(activity))
        activity.finishAndRemoveTask()
    }

    fun homeIntentWithPanel(context: Context): Intent {
        val panelIntent = Intent(context, PanelActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingPanelIntent = PendingIntent.getActivity(
            context,
            0,
            panelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("extra_launch_in_home_pending_intent", pendingPanelIntent)
    }

    fun leaveImmersiveForPanel(activity: Activity) {
        activity.startActivity(homeIntentWithPanel(activity.applicationContext))
        activity.finishAndRemoveTask()
    }
}
