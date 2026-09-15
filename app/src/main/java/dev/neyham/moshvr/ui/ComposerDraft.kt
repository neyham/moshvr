package dev.neyham.moshvr.ui

/** Composer text lives on the session, not Compose remember. */
object ComposerDraft {
    fun mergeSpoken(current: String, spoken: String): String =
        listOf(current.trim(), spoken.trim()).filter { it.isNotBlank() }.joinToString(" ")
}
