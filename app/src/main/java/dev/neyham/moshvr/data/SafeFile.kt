package dev.neyham.moshvr.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Crash-safe IO for the small JSON stores. A write goes to a temp file that then replaces the
 * real one in a single rename, so a kill mid-write never leaves half a file. A file that no
 * longer parses is moved aside, so the next save cannot overwrite whatever is left in it.
 * Every read and write takes [lock] (re-entrant), and callers also hold it around
 * read-modify-write sequences, so a quarantine can never rename a file another thread just saved.
 */
class SafeFile(val file: File) {
    val lock = Any()

    /** Set when an unreadable file could not be moved aside: never write over it. */
    @Volatile private var blocked = false

    fun <T> load(default: () -> T, decode: (String) -> T): T = synchronized(lock) {
        if (!file.exists()) return default()
        runCatching { decode(file.readText()) }.getOrElse {
            quarantine()
            default()
        }
    }

    fun writeText(text: String) = synchronized(lock) {
        if (blocked) return
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("could not replace ${file.name}")
        }
    }

    /** Keeps the unreadable file aside; if that fails, block writes rather than overwrite it. */
    private fun quarantine() {
        val aside = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
        if (file.renameTo(aside)) return
        val copied = runCatching { file.copyTo(aside, overwrite = true) }.isSuccess
        if (!copied || !file.delete()) blocked = true
    }
}
