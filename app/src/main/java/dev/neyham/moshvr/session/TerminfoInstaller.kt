package dev.neyham.moshvr.session

import android.content.Context
import java.io.File

/** Copies the bundled terminfo database (xterm-256color) into filesDir for mosh-client. */
object TerminfoInstaller {
    /** Both lookup layouts ncurses uses (letter and hex directory). */
    private val ENTRIES = listOf("x/xterm-256color", "78/xterm-256color")

    /**
     * Copies into a scratch directory and renames it into place, so a copy cut short (process
     * killed, two tabs starting at once) never leaves a half-filled terminfo that looks done.
     */
    @Synchronized
    fun ensure(context: Context): String {
        val root = File(context.filesDir, "terminfo")
        if (!complete(root)) {
            val staging = File(context.filesDir, "terminfo.tmp")
            staging.deleteRecursively()
            copyAssetDir(context, "terminfo", staging)
            root.deleteRecursively()
            if (!staging.renameTo(root)) {
                // Rename failed: fall back to copying in place.
                copyAssetDir(context, "terminfo", root)
                staging.deleteRecursively()
            }
        }
        return root.absolutePath
    }

    internal fun complete(root: File): Boolean = ENTRIES.all { File(root, it).length() > 0 }

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
