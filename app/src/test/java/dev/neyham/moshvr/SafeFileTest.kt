package dev.neyham.moshvr

import dev.neyham.moshvr.data.SafeFile
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeFileTest {

    private fun dir(): File = Files.createTempDirectory("safefile").toFile()

    @Test
    fun writeReplacesWholeFileAndLeavesNoTemp() {
        val d = dir()
        val f = SafeFile(File(d, "profiles.json"))
        f.writeText("one")
        f.writeText("two")
        assertEquals("two", f.load({ "" }) { it })
        assertEquals(listOf("profiles.json"), d.list()!!.toList())
    }

    @Test
    fun unreadableFileIsKeptAsideNotOverwritten() {
        val d = dir()
        val file = File(d, "profiles.json").apply { writeText("{half a fi") }
        val f = SafeFile(file)
        assertEquals("default", f.load({ "default" }) { error("bad json: $it") })
        assertFalse(file.exists())
        val kept = d.listFiles()!!.single { it.name.startsWith("profiles.json.corrupt-") }
        assertEquals("{half a fi", kept.readText())
        f.writeText("fresh")
        assertTrue(kept.exists())
    }
}
