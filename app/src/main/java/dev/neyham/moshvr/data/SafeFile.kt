package dev.neyham.moshvr.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Crash-safe IO for the small JSON stores. A write goes to a temp file that then replaces the
 * real one in a single rename, so a kill mid-write never leaves half a file. A file that no
 * longer parses is moved aside, so the next save cannot overwrite whatever is left in it.
 * Callers hold [lock] around read-modify-write sequences.
 */
class SafeFile(val file: File) {
    val lock = Any()

    fun <T> load(default: () -> T, decode: (String) -> T): T {
        if (!file.exists()) return default()
        return runCatching { decode(file.readText()) }.getOrElse {
            quarantine()
            default()
        }
    }

    fun writeText(text: String) {
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

    private fun quarantine() {
        file.renameTo(File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}"))
    }
}
