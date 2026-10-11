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

class SafeFileConcurrencyTest {

    @Test
    fun concurrentLoadsAndSavesNeverLoseTheSavedFile() {
        val d = java.nio.file.Files.createTempDirectory("safefile-race").toFile()
        val file = File(d, "known_hosts.json").apply { writeText("not json") }
        val f = SafeFile(file)
        val threads = (1..8).map { i ->
            Thread {
                repeat(50) {
                    synchronized(f.lock) {
                        f.load({ "" }) { if (it.startsWith("ok")) it else error("bad") }
                        f.writeText("ok-$i")
                    }
                    f.load({ "" }) { if (it.startsWith("ok")) it else error("bad") }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }
        assertTrue(f.load({ "" }) { it }.startsWith("ok-"))
        assertEquals(1, d.listFiles()!!.count { it.name.contains(".corrupt-") })
    }
}

class SafeFileBlockedTest {

    @Test
    fun unmovableCorruptFileBlocksSavesLoudlyAndIsCopiedOnce() {
        val d = java.nio.file.Files.createTempDirectory("safefile-blocked").toFile()
        val file = File(d, "profiles.json").apply { writeText("not json") }
        d.setWritable(false) // rename, copy and delete all fail
        try {
            val f = SafeFile(file)
            repeat(3) { assertEquals("default", f.load({ "default" }) { error("bad") }) }
            val thrown = runCatching { f.writeText("new") }.exceptionOrNull()
            assertTrue(thrown is java.io.IOException)
            assertEquals("not json", file.readText())
            assertEquals(1, d.listFiles()!!.size)
        } finally {
            d.setWritable(true)
        }
    }
}
