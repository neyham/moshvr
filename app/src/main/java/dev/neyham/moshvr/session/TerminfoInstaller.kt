package dev.neyham.moshvr.session

import android.content.Context
import java.io.File

/** Copies the bundled terminfo database (xterm-256color) into filesDir for mosh-client. */
object TerminfoInstaller {

    fun ensure(context: Context): String {
        val root = File(context.filesDir, "terminfo")
        if (!root.exists()) {
            copyAssetDir(context, "terminfo", root)
        }
        return root.absolutePath
    }

    private fun copyAssetDir(context: Context, assetPath: String, target: File) {
        val assets = context.assets
        val children = assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            target.mkdirs()
            children.forEach { child ->
                copyAssetDir(context, "$assetPath/$child", File(target, child))
            }
        }
    }
}
