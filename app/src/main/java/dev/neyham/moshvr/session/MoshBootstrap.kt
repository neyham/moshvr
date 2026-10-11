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

    /**
     * Drains stdout and stderr while waiting, returning as soon as MOSH CONNECT shows up or the
     * command ends. Draining keeps sshlib's channel window open (a chatty login script could
     * otherwise fill it and stall the server before EOF); the deadline stops a shell rc that
     * hangs or prompts from keeping us in "bootstrapping" forever.
     */
    private fun readServerAnswer(session: Session): Pair<String, String> {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + SERVER_ANSWER_TIMEOUT_MS
        val buffer = ByteArray(8192)
        fun drain(stream: InputStream, into: ByteArrayOutputStream) {
            while (stream.available() > 0) {
                val n = stream.read(buffer)
                if (n <= 0) return
                into.write(buffer, 0, n)
            }
        }
        fun text(b: ByteArrayOutputStream) = b.toString(Charsets.UTF_8.name())
        while (true) {
            drain(session.stdout, out)
            drain(session.stderr, err)
            if (CONNECT_RE.containsMatchIn(text(out)) || CONNECT_RE.containsMatchIn(text(err))) break
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                throw IOException(
                    "mosh-server did not answer within ${SERVER_ANSWER_TIMEOUT_MS / 1000} s." +
                        "\r\nDoes a login script on the host wait for input?",
                )
            }
            val state = session.waitForCondition(
                ChannelCondition.STDOUT_DATA or ChannelCondition.STDERR_DATA or
                    ChannelCondition.EOF or ChannelCondition.CLOSED,
                remaining,
            )
            val data = ChannelCondition.STDOUT_DATA or ChannelCondition.STDERR_DATA
            if (state and (ChannelCondition.EOF or ChannelCondition.CLOSED) != 0 && state and data == 0) {
                drain(session.stdout, out)
                drain(session.stderr, err)
                break
            }
        }
        return text(out) to text(err)
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
                val (stdout, stderr) = readServerAnswer(session)
                val combined = "$stdout\n$stderr"

                val match = CONNECT_RE.find(combined)
                    ?: throw IOException(
                        buildString {
                            append("mosh-server did not answer.")
                            if (stderr.isNotBlank()) append("\r\n${stderr.trim()}")
                            if (stdout.isNotBlank()) append("\r\n${stdout.trim()}")
                            append("\r\nIs mosh installed on the host?")
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
