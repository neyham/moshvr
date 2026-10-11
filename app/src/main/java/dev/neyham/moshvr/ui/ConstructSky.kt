package dev.neyham.moshvr.ui

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Maps a world direction to equirect texture coordinates. The texture wraps the viewer:
 * u runs once around the horizon, v from straight up (0) to straight down (1).
 */
object Equirect {
    fun u(x: Float, z: Float): Float = (atan2(x, z) / (2 * PI) + 0.5).toFloat()

    fun v(y: Float, horizontal: Float): Float = (0.5 - atan2(y, horizontal) / PI).toFloat()

    /** Elevation in degrees for a texture row; 0 is the horizon. */
    fun elevationDeg(v: Float): Float = (0.5f - v) * 180f
}

/**
 * Floor grid lines of the construct, as polylines in normalized equirect space.
 * The floor sits [eyeHeight] below the viewer and runs out to [reach] metres.
 */
object ConstructFloor {
    const val EYE_HEIGHT_M = 1.6f
    const val SPACING_M = 1.5f
    const val REACH_M = 60f

    class Line(val points: List<Pair<Float, Float>>, val distance: Float)

    fun lines(eyeHeight: Float = EYE_HEIGHT_M, spacing: Float = SPACING_M, reach: Float = REACH_M): List<Line> {
        val out = ArrayList<Line>()
        val n = (reach / spacing).toInt()
        for (i in -n..n) {
            val k = i * spacing
            // Lines parallel to z (x = k) and parallel to x (z = k).
            out += line(eyeHeight, reach, abs(k)) { t -> k to t }
            out += line(eyeHeight, reach, abs(k)) { t -> t to k }
        }
        return out
    }

    private fun line(eyeHeight: Float, reach: Float, offset: Float, at: (Float) -> Pair<Float, Float>): Line {
        val pts = ArrayList<Pair<Float, Float>>()
        var t = -reach
        while (t <= reach) {
            val (x, z) = at(t)
            val horizontal = sqrt(x * x + z * z)
            if (horizontal > 0.05f) pts += Equirect.u(x, z) to Equirect.v(-eyeHeight, horizontal)
            // Dense samples near the viewer where the projection bends hardest.
            t += if (abs(t) < 8f) 0.25f else 1f
        }
        return Line(pts, offset)
    }
}

private const val SKY_GLYPHS =
    "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789:・.\"=*+-<>¦｜"

private val GLYPH_CHARS = SKY_GLYPHS.toCharArray()

/** One falling stream in a depth layer; positions are in rows of that layer. */
private class Stream(rows: Int, random: Random) {
    var head = -random.nextInt(rows).toFloat()
    var speed = 0f
    var trail = 0
    val glyphs = IntArray(40) { random.nextInt(SKY_GLYPHS.length) }

    init {
        reset(random)
    }

    fun reset(random: Random) {
        speed = 0.15f + random.nextFloat() * 0.55f
        trail = 8 + random.nextInt(24)
    }

    fun step(rows: Int, random: Random) {
        head += speed
        if (random.nextInt(6) == 0) glyphs[random.nextInt(glyphs.size)] = random.nextInt(SKY_GLYPHS.length)
        if (head - trail > rows) {
            head = -random.nextInt(rows / 2 + 1).toFloat()
            reset(random)
        }
    }
}

/** A depth layer of rain: far layers are smaller, dimmer and sparser. */
private class RainLayer(val fontPx: Float, val brightness: Float, val density: Float) {
    val streams = HashMap<Int, Stream>()
}

/**
 * The construct as a 360° world: digital rain all around, fading toward the zenith,
 * over a glowing floor grid that runs to the horizon. Drawn into an equirect panel.
 */
@Composable
fun ConstructSky(active: Boolean, modifier: Modifier = Modifier) {
    var frameTime by remember { mutableLongStateOf(0L) }
    val random = remember { Random(System.nanoTime()) }
    val layers = remember {
        listOf(
            RainLayer(fontPx = 22f, brightness = 0.35f, density = 0.55f),
            RainLayer(fontPx = 32f, brightness = 0.65f, density = 0.45f),
            RainLayer(fontPx = 46f, brightness = 1f, density = 0.30f),
        )
    }
    val floorLines = remember { ConstructFloor.lines() }
    val textPaint = remember {
        Paint().apply {
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
    }
    val linePaint = remember {
        Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
    }
    val glowPaint = remember { Paint() }
    // Static parts depend only on the texture size; build them once, not every frame.
    val floorCache = remember { arrayOfNulls<Any>(2) }

    // Hidden (reality mode), the panel is invisible: stop animating instead of redrawing 4K at 30 fps.
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
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
        val w = size.width
        val h = size.height
        val canvas = drawContext.canvas.nativeCanvas
        val horizon = h / 2f

        @Suppress("UNCHECKED_CAST")
        var floor = floorCache[1] as List<Pair<Path, Int>>?
        if (floorCache[0] != size || floor == null) {
            glowPaint.shader = LinearGradient(
                0f, horizon - h * 0.08f, 0f, horizon + h * 0.04f,
                intArrayOf(0x00000000, 0x3300FF66, 0x00000000),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
            floor = floorLines.map { line ->
                val path = Path()
                var prevU = Float.NaN
                for ((u, v) in line.points) {
                    // Break the polyline where it wraps around the seam.
                    if (prevU.isNaN() || abs(u - prevU) > 0.5f) path.moveTo(u * w, v * h) else path.lineTo(u * w, v * h)
                    prevU = u
                }
                val fade = (1f - line.distance / ConstructFloor.REACH_M).coerceIn(0f, 1f)
                path to android.graphics.Color.argb((30 + 150 * fade * fade).toInt(), 0, 255, 102)
            }
            floorCache[0] = size
            floorCache[1] = floor
        }

        // Horizon glow: where the floor meets the rain.
        canvas.drawRect(0f, horizon - h * 0.08f, w, horizon + h * 0.04f, glowPaint)

        // Floor grid, brighter near the viewer and fading into the distance.
        linePaint.strokeWidth = 2f
        for ((path, color) in floor) {
            linePaint.color = color
            canvas.drawPath(path, linePaint)
        }

        // Rain: columns around the full horizon, from high above down to the horizon.
        for (layer in layers) {
            val cellW = layer.fontPx * 0.72f
            val cellH = layer.fontPx * 1.05f
            val top = h * 0.12f // ~68° up; above that equirect stretching gets ugly
            val rows = ((horizon - top) / cellH).toInt().coerceAtLeast(1)
            val cols = (w / cellW).toInt()
            textPaint.textSize = layer.fontPx
            for (c in 0 until cols) {
                // Stable sparse subset of columns per layer.
                if (((c * 2654435761L) ushr 7 and 1023L) / 1024f > layer.density) continue
                val s = layer.streams.getOrPut(c) { Stream(rows, random) }
                s.step(rows, random)
                val headRow = s.head.toInt()
                for (t in 0..s.trail) {
                    val row = headRow - t
                    if (row < 0 || row >= rows) continue
                    val y = top + (row + 1) * cellH
                    // Fade in from the top edge and out into the horizon glow.
                    val edge = ((y - top) / (h * 0.10f)).coerceIn(0f, 1f) *
                        ((horizon - y) / (h * 0.03f)).coerceIn(0f, 1f)
                    val tail = if (t == 0) 1f else (1f - t.toFloat() / s.trail) * 0.85f
                    val a = (255 * tail * edge * layer.brightness).toInt().coerceIn(0, 255)
                    if (a < 6) continue
                    textPaint.color = if (t == 0) {
                        android.graphics.Color.argb(a, 210, 255, 225)
                    } else {
                        android.graphics.Color.argb(a, 0, 255, 102)
                    }
                    val glyph = s.glyphs[(row and Int.MAX_VALUE) % s.glyphs.size]
                    canvas.drawText(GLYPH_CHARS, glyph, 1, c * cellW, y, textPaint)
                }
            }
        }
    }
}
