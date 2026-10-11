package dev.neyham.moshvr.session

import com.trilead.ssh2.ChannelCondition
import com.trilead.ssh2.Session
import java.io.ByteArrayOutputStream
import java.io.InputStream
import dev.neyham.moshvr.data.HostProfile
import dev.neyham.moshvr.data.KnownHostsStore
import java.io.IOException
import java.net.InetAddress

/**
 * The classic mosh handshake: SSH in, run `mosh-server new`, parse
 * "MOSH CONNECT <port> <key>", drop the SSH connection, hand the coordinates
 * to the local mosh-client which takes over on UDP.
 */
object MoshBootstrap {
    /** Generous: mosh-server answers in well under a second on a healthy host. */
    const val SERVER_ANSWER_TIMEOUT_MS = 20_000L
    private const val WATCHDOG_GRACE_MS = 2_000L
    private const val OUTPUT_TAIL_CHARS = 2_000

    data class Endpoint(val ip: String, val port: Int, val key: String)

    internal val CONNECT_RE = Regex("""MOSH CONNECT (\d+) ([A-Za-z0-9+/]{22})""")

    /** Non-login SSH has locale `C`; mosh-server refuses that and dumps LC_*. */
    internal val SERVER_START_COMMAND = """
        export PATH="${'$'}PATH:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin"
        if locale -a 2>/dev/null | grep -qiE 'en_US.utf'; then
          export LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 LC_CTYPE=en_US.UTF-8
        else
          export LANG=C.UTF-8 LC_ALL=C.UTF-8 LC_CTYPE=C.UTF-8
        fi
        ${StartupCommand.moshServerExec(null)}
    """.trimIndent()

    fun serverStartCommand(startupCommand: String?): String {
        val exec = StartupCommand.moshServerExec(startupCommand)
        return SERVER_START_COMMAND.replace(StartupCommand.moshServerExec(null), exec)
    }

    internal fun parseConnect(combined: String): Endpoint? {
        val match = CONNECT_RE.find(combined) ?: return null
        return Endpoint("", match.groupValues[1].toInt(), match.groupValues[2])
    }

    internal class ServerAnswer(val stdout: String, val stderr: String, val timedOut: Boolean)

    /**
     * Drains stdout and stderr while waiting, returning as soon as MOSH CONNECT shows up, the
     * command ends, the deadline passes or the link fails. Draining keeps sshlib's channel
     * window open (a chatty login script could otherwise fill it and stall the server before
     * EOF). Reads go one chunk at a time with the match and deadline checked in between, and
     * whatever was read is always returned for parsing, never dropped by an error.
     * A read can still block inside sshlib's window-adjust send; [run]'s watchdog covers that.
     */
    private fun readServerAnswer(session: Session): ServerAnswer {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + SERVER_ANSWER_TIMEOUT_MS
        val buffer = ByteArray(8192)
        fun text(b: ByteArrayOutputStream) = b.toString(Charsets.UTF_8.name())
        fun answered() = CONNECT_RE.containsMatchIn(text(out)) || CONNECT_RE.containsMatchIn(text(err))
        fun answer(timedOut: Boolean) = ServerAnswer(text(out), text(err), timedOut)
        /** One chunk from whichever stream has data; false when neither has any. */
        fun readChunk(): Boolean {
            for ((stream, into) in listOf(session.stdout to out, session.stderr to err)) {
                if (stream.available() > 0) {
                    val n = stream.read(buffer)
                    if (n > 0) into.write(buffer, 0, n)
                    return true
                }
            }
            return false
        }
        try {
            while (true) {
                while (readChunk()) {
                    if (answered()) return answer(false)
                    if (System.currentTimeMillis() >= deadline) return answer(true)
                }
                if (answered()) return answer(false)
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return answer(true)
                val state = session.waitForCondition(
                    ChannelCondition.STDOUT_DATA or ChannelCondition.STDERR_DATA or
                        ChannelCondition.EOF or ChannelCondition.CLOSED,
                    remaining,
                )
                val data = ChannelCondition.STDOUT_DATA or ChannelCondition.STDERR_DATA
                if (state and (ChannelCondition.EOF or ChannelCondition.CLOSED) != 0 && state and data == 0) {
                    while (readChunk()) Unit
                    return answer(false)
                }
            }
        } catch (_: IOException) {
            // Link failed mid-read (or the watchdog closed it): parse what arrived.
            return answer(System.currentTimeMillis() >= deadline)
        }
    }

    fun run(profile: HostProfile, knownHosts: KnownHostsStore, ownerId: String): Endpoint {
        val ip = InetAddress.getByName(profile.host).hostAddress
            ?: throw IOException("Could not resolve ${profile.host}")

        val conn = SshAuth.connectAndAuthenticate(profile, knownHosts, ownerId)
        try {
            val session = conn.openSession()
            try {
                // sshlib sessions have no LANG; mosh-server refuses US-ASCII and dumps LC_*.
                // macOS non-login PATH lacks Homebrew: append it, keeping the host's own PATH
                // (Nix, Linuxbrew, ~/.local/bin, snap).
                session.execCommand(serverStartCommand(profile.startupCommand))
                // Hard stop: a send blocked inside sshlib ignores our deadline, but closing the
                // connection makes it fail, and the reader returns what it already has.
                val watchdog = Thread({
                    try {
                        Thread.sleep(SERVER_ANSWER_TIMEOUT_MS + WATCHDOG_GRACE_MS)
                        runCatching { conn.close() }
                    } catch (_: InterruptedException) {
                        // Answered in time.
                    }
                }, "MoshBootstrapWatchdog").apply {
                    isDaemon = true
                    start()
                }
                val answer = try {
                    readServerAnswer(session)
                } finally {
                    watchdog.interrupt()
                }
                val stdout = answer.stdout
                val stderr = answer.stderr
                val combined = "$stdout\n$stderr"

                val match = CONNECT_RE.find(combined)
                    ?: throw IOException(
                        buildString {
                            if (answer.timedOut) {
                                append("mosh-server did not answer within ${SERVER_ANSWER_TIMEOUT_MS / 1000} s.")
                            } else {
                                append("mosh-server did not answer.")
                            }
                            if (stderr.isNotBlank()) append("\r\n${stderr.trim().takeLast(OUTPUT_TAIL_CHARS)}")
                            if (stdout.isNotBlank()) append("\r\n${stdout.trim().takeLast(OUTPUT_TAIL_CHARS)}")
                            if (answer.timedOut) {
                                append("\r\nDoes a login script on the host wait for input?")
                            } else {
                                append("\r\nIs mosh installed on the host?")
                            }
                        },
                    )
                return Endpoint(ip, match.groupValues[1].toInt(), match.groupValues[2])
            } finally {
                runCatching { session.close() }
            }
        } finally {
            runCatching { conn.close() }
        }
    }
}
