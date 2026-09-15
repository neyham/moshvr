package dev.neyham.moshvr.session

/** Shared startup-command handling for SSH (typed once) and Mosh (server argv). */
object StartupCommand {

    fun typedLine(command: String?): String? =
        command?.trim()?.takeIf { it.isNotBlank() }?.let { "$it\r" }

    fun posixSingleQuote(raw: String): String = buildString {
        append('\'')
        raw.forEach { ch ->
            if (ch == '\'') append("'\\''") else append(ch)
        }
        append('\'')
    }

    fun moshServerExec(command: String?): String {
        val extra = command?.trim()?.takeIf { it.isNotBlank() }
        return if (extra == null) {
            "exec mosh-server new -c 256"
        } else {
            "exec mosh-server new -c 256 -- sh -lc ${posixSingleQuote(extra)}"
        }
    }
}
