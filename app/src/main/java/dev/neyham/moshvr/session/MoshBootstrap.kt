package dev.neyham.moshvr.session

import com.trilead.ssh2.ChannelCondition
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
                // A shell rc that hangs or prompts would otherwise keep us in "bootstrapping" forever.
                val done = session.waitForCondition(
                    ChannelCondition.EOF or ChannelCondition.CLOSED,
                    SERVER_ANSWER_TIMEOUT_MS,
                )
                if (done and ChannelCondition.TIMEOUT != 0) {
                    throw IOException(
                        "mosh-server did not answer within ${SERVER_ANSWER_TIMEOUT_MS / 1000} s." +
                            "\r\nDoes a login script on the host wait for input?",
                    )
                }
                val stdout = session.stdout.readBytes().toString(Charsets.UTF_8)
                val stderr = session.stderr.readBytes().toString(Charsets.UTF_8)
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
