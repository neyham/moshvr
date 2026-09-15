package dev.neyham.moshvr.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.util.Log

object TerminalFonts {
    private const val TAG = "moshVR.Fonts"
    private const val NERD_MONO = "fonts/JetBrainsMonoNerdFontMono-Regular.ttf"
    private const val PLAIN_MONO = "fonts/JetBrainsMono-Regular.ttf"

    @Volatile
    private var cached: Typeface? = null

    fun load(context: Context): Typeface {
        cached?.let { return it }
        val typeface = loadAsset(context, NERD_MONO)
            ?: loadAsset(context, PLAIN_MONO)
            ?: Typeface.MONOSPACE
        Log.i(TAG, "loaded typeface=${if (typeface === Typeface.MONOSPACE) "system-monospace" else "bundled"}")
        cached = typeface
        return typeface
    }

    private fun loadAsset(context: Context, path: String): Typeface? {
        return runCatching {
            val font = Font.Builder(context.assets, path).build()
            Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
                .setSystemFallback("monospace")
                .build()
        }.getOrElse {
            runCatching { Typeface.createFromAsset(context.assets, path) }.getOrNull()
        }
    }
}
