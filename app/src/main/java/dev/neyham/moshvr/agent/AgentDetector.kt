package dev.neyham.moshvr.agent

enum class AgentKind(val displayName: String) {
    CLAUDE_CODE("Claude Code"),
    CODEX("Codex"),
    OPENCODE("OpenCode"),
    GEMINI_CLI("Gemini CLI"),
    AIDER("Aider"),
}

/**
 * Heuristic detection of AI coding agents from the visible terminal screen.
 * Transport-independent: works over plain SSH, mosh, and inside tmux.
 */
object AgentDetector {

    private data class Signature(val kind: AgentKind, val patterns: List<Regex>)

    private val signatures = listOf(
        Signature(
            AgentKind.CLAUDE_CODE,
            listOf(
                Regex("esc to interrupt", RegexOption.IGNORE_CASE),
                Regex("claude code", RegexOption.IGNORE_CASE),
                Regex("""\? for shortcuts"""),
                Regex("""[✻✳✶✽·] (Thinking|Percolating|Musing|Working|Running)""", RegexOption.IGNORE_CASE),
            ),
        ),
        Signature(
            AgentKind.CODEX,
            listOf(
                Regex("openai codex", RegexOption.IGNORE_CASE),
                Regex("""codex (v?\d|session|is working)""", RegexOption.IGNORE_CASE),
                Regex("ctrl\\+c to exit \\| \"/\" to see commands", RegexOption.IGNORE_CASE),
            ),
        ),
        Signature(
            AgentKind.OPENCODE,
            listOf(Regex("""opencode(\s+v?\d|\.ai| session)""", RegexOption.IGNORE_CASE)),
        ),
        Signature(
            AgentKind.GEMINI_CLI,
            listOf(Regex("gemini-2|gemini cli", RegexOption.IGNORE_CASE)),
        ),
        Signature(
            AgentKind.AIDER,
            listOf(Regex("""aider v\d""", RegexOption.IGNORE_CASE)),
        ),
    )

    fun detect(screenText: String): AgentKind? {
        for (sig in signatures) {
            if (sig.patterns.any { it.containsMatchIn(screenText) }) return sig.kind
        }
        return null
    }

    /** A short "what is the agent doing" line for the status chip. */
    fun statusLine(screenText: String): String? {
        val lines = screenText.lines().map { it.trim() }.filter { it.isNotBlank() }
        // Prefer spinner/status lines; fall back to the last non-blank line.
        val status = lines.lastOrNull { line ->
            line.contains("esc to interrupt", ignoreCase = true) ||
                line.contains("interrupt", ignoreCase = true) ||
                Regex("""^[✻✳✶✽·⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏]""").containsMatchIn(line)
        } ?: lines.lastOrNull()
        return status?.take(100)
    }
}
