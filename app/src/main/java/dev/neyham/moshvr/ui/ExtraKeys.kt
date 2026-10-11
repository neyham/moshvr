package dev.neyham.moshvr.ui

sealed class ExtraKeyAction {
    data class Write(val payload: String) : ExtraKeyAction()
    data class KeyCode(val code: Int) : ExtraKeyAction()
    data object ToggleCtrl : ExtraKeyAction()
    data object ToggleAlt : ExtraKeyAction()
    data object Mic : ExtraKeyAction()
    data object Keyboard : ExtraKeyAction()
    data object CancelVoice : ExtraKeyAction()
    data class Scroll(val rows: Int) : ExtraKeyAction()
}

data class ExtraKeySpec(
    val id: String,
    val label: String,
    val action: ExtraKeyAction,
)

/** Fixed extra-key row. MIC is first so voice is a shortcut, not only the composer icon. */
object ExtraKeys {
    const val PAGE_ROWS = 15

    val catalog: List<ExtraKeySpec> = listOf(
        ExtraKeySpec("mic", "MIC", ExtraKeyAction.Mic),
        ExtraKeySpec("enter", "⏎ ENTER", ExtraKeyAction.Write("\r")),
        ExtraKeySpec("keyb", "KEYB", ExtraKeyAction.Keyboard),
        ExtraKeySpec("esc", "ESC", ExtraKeyAction.Write("\u001b")),
        ExtraKeySpec("slash", "/", ExtraKeyAction.Write("/")),
        ExtraKeySpec("tab", "TAB", ExtraKeyAction.Write("\t")),
        ExtraKeySpec("ctrl", "CTRL", ExtraKeyAction.ToggleCtrl),
        ExtraKeySpec("alt", "ALT", ExtraKeyAction.ToggleAlt),
        ExtraKeySpec("left", "◀TAB", ExtraKeyAction.Write(HerdrKeys.prevTab)),
        // The stick now switches workspaces, so these scroll a page at a time.
        ExtraKeySpec("down", "PgDn", ExtraKeyAction.Scroll(ExtraKeys.PAGE_ROWS)),
        ExtraKeySpec("up", "PgUp", ExtraKeyAction.Scroll(-ExtraKeys.PAGE_ROWS)),
        ExtraKeySpec("right", "TAB▶", ExtraKeyAction.Write(HerdrKeys.nextTab)),
        ExtraKeySpec("ctrl-c", "^C", ExtraKeyAction.Write("\u0003")),
    )

    /** The keys used most in a headset; pinned large at the right edge so they never scroll away. */
    val primaryIds = listOf("mic", "keyb", "enter")

    val primary: List<ExtraKeySpec> = primaryIds.map { id -> catalog.first { it.id == id } }

    val secondary: List<ExtraKeySpec> = catalog.filter { it.id !in primaryIds }

    /** Without any speech recognizer MIC just opens the keyboard, so one key covers both. */
    fun visiblePrimary(micVoice: Boolean): List<ExtraKeySpec> =
        if (micVoice) primary else primary.filter { it.id != "mic" }

    fun primaryLabel(spec: ExtraKeySpec, micVoice: Boolean, recording: Boolean, transcribing: Boolean): String =
        when (spec.action) {
            ExtraKeyAction.Mic -> "🎤 " + micLabel(recording, transcribing)
            ExtraKeyAction.Keyboard -> if (micVoice) "⌨ KEYB" else "⌨ KEYB / 🎤"
            else -> spec.label
        }

    fun micLabel(recording: Boolean, transcribing: Boolean): String = when {
        transcribing -> "WAIT"
        recording -> "STOP"
        else -> "MIC"
    }
}

/** Default herdr prefix is ctrl+b, then p/n for previous/next tab. */
object HerdrKeys {
    const val PREFIX = "\u0002"
    const val prevTab = PREFIX + "p"
    const val nextTab = PREFIX + "n"

    fun tabChord(direction: Int): String = if (direction < 0) prevTab else nextTab

    /** herdr binds prefix+up / prefix+down to previous / next workspace. */
    const val prevWorkspace = PREFIX + "\u001b[A"
    const val nextWorkspace = PREFIX + "\u001b[B"

    fun workspaceChord(direction: Int): String = if (direction < 0) prevWorkspace else nextWorkspace
}

/** 2D home panel (~1180×720). 42 px looked like a poster; 22 is a normal terminal. */
object HostOnboarding {
    const val HEADLINE = "moshVR"
    const val LINE1 = "Add a host your headset can reach over SSH."
    const val LINE2 = "mosh needs mosh-server on that machine. Plain SSH works without it."
    const val NETWORK = "Internet and a reachable SSH host are required."
    const val RETRY = "If a connect fails, check Wi-Fi and tap CONNECT again."
}

object PanelLayout {
    const val DEFAULT_FONT_PX = 22
    const val LEGACY_FONT_PX = 42
    /** Manifest 2D panel floor; matches Meta-style usable panel minimums. */
    const val MIN_WIDTH_DP = 640
    const val MIN_HEIGHT_DP = 400
    const val DEFAULT_WIDTH_DP = 1180
    const val DEFAULT_HEIGHT_DP = 720
}

object SessionChrome {
    fun tabTitle(title: String, finished: Boolean): String =
        if (finished) "[dead] $title" else title

    fun cycleIndex(currentIndex: Int, size: Int, direction: Int): Int {
        if (size <= 0) return -1
        val start = if (currentIndex < 0) 0 else currentIndex
        return ((start + direction) % size + size) % size
    }
}
