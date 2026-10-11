package dev.neyham.moshvr

import dev.neyham.moshvr.ui.ConstructFloor
import dev.neyham.moshvr.ui.Equirect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstructSkyTest {

    @Test
    fun horizonIsTheMiddleRowAndFloorIsBelowIt() {
        assertEquals(0.5f, Equirect.v(0f, 10f), 1e-4f)
        assertTrue(Equirect.v(-1.6f, 2f) > 0.5f)
        assertEquals(0f, Equirect.elevationDeg(0.5f), 1e-4f)
    }

    @Test
    fun aroundTheHorizonCoversTheWholeTextureOnce() {
        val forward = Equirect.u(0f, 1f)
        val back = Equirect.u(0f, -1f)
        assertEquals(0.5f, forward, 1e-4f)
        assertEquals(0.5f, kotlin.math.abs(back - forward), 1e-4f)
    }

    @Test
    fun floorGridStaysBelowTheHorizonAndInsideTheTexture() {
        val lines = ConstructFloor.lines()
        assertTrue(lines.size > 100)
        for (line in lines) for ((u, v) in line.points) {
            assertTrue(u in 0f..1f)
            assertTrue("v=$v", v > 0.5f && v <= 1f)
        }
    }
}
