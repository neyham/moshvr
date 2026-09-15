package dev.neyham.moshvr.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.random.Random

private const val GLYPHS =
    "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789:・.\"=*+-<>¦｜"

private class RainColumn(rows: Int, random: Random) {
    var head: Float = -random.nextInt(rows).toFloat()
    var speed: Float = 0.25f + random.nextFloat() * 0.75f
    var trail: Int = 6 + random.nextInt(18)

    fun step(rows: Int, random: Random) {
        head += speed
        if (head - trail > rows) {
            head = -random.nextInt(rows / 2).toFloat()
            speed = 0.25f + random.nextFloat() * 0.75f
            trail = 6 + random.nextInt(18)
        }
    }
}

/** The classic digital-rain effect, cheap enough to run on several large panels. */
@Composable
fun MatrixRain(modifier: Modifier = Modifier, fontSizePx: Float = 34f) {
    var frameTime by remember { mutableLongStateOf(0L) }
    val random = remember { Random(System.nanoTime()) }
    val columns = remember { mutableMapOf<Int, RainColumn>() }
    val paint = remember {
        Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = fontSizePx
            isAntiAlias = true
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                // ~30fps is plenty and saves GPU for the terminal.
                if (now - last > 33_000_000L) {
                    frameTime = now
                    last = now
                }
            }
        }
    }

    Canvas(modifier = modifier.background(Color.Black)) {
        @Suppress("UNUSED_EXPRESSION")
        frameTime // invalidate on each tick

        val cellW = fontSizePx * 0.72f
        val cellH = fontSizePx * 1.02f
        val colCount = (size.width / cellW).toInt().coerceAtLeast(1)
        val rowCount = (size.height / cellH).toInt().coerceAtLeast(1)
        val canvas = drawContext.canvas.nativeCanvas

        for (c in 0 until colCount) {
            val col = columns.getOrPut(c) { RainColumn(rowCount, random) }
            col.step(rowCount, random)
            val x = c * cellW
            val headRow = col.head.toInt()
            for (t in 0..col.trail) {
                val row = headRow - t
                if (row < 0 || row >= rowCount) continue
                val alpha = when (t) {
                    0 -> 255
                    else -> (220 * (1f - t.toFloat() / col.trail)).toInt().coerceIn(10, 220)
                }
                paint.color = if (t == 0) {
                    android.graphics.Color.argb(255, 210, 255, 225)
                } else {
                    android.graphics.Color.argb(alpha, 0, 255, 102)
                }
                val glyph = GLYPHS[random.nextInt(GLYPHS.length)].toString()
                canvas.drawText(glyph, x, (row + 1) * cellH, paint)
            }
        }
    }
}
