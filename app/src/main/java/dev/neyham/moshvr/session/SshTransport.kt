package dev.neyham.moshvr.session

import com.trilead.ssh2.Connection
import com.trilead.ssh2.Session
import dev.neyham.moshvr.data.HostProfile
import dev.neyham.moshvr.data.KnownHostsStore
import java.io.IOException
import java.nio.ByteBuffer

class HostKeyChangedException(
    val host: String,
    val port: Int,
    val algorithm: String,
    val expectedFingerprint: String,
    val actualFingerprint: String,
) : IOException(
    "HOST KEY CHANGED for $host:$port ($algorithm)!\r\n" +
        "  expected $expectedFingerprint\r\n" +
        "  received $actualFingerprint\r\n" +
        "If this is expected, forget the host in Settings and retry.",
)

/** SSH shell transport built on ConnectBot's sshlib (trilead-ssh2 fork). */
class SshTransport(
    private val profile: HostProfile,
    private val knownHosts: KnownHostsStore,
    private val ownerId: String,
) : TerminalTransport {

    private var connection: Connection? = null
    private var session: Session? = null

    override fun connect(columns: Int, rows: Int): TransportStreams {
        val conn = SshAuth.connectAndAuthenticate(profile, knownHosts, ownerId)
        connection = conn
        val sess = conn.openSession()
        session = sess
        sess.requestPTY("xterm-256color", columns, rows, columns * 8, rows * 16, utf8PtyModes())
        sess.startShell()
        return TransportStreams(sess.stdout, sess.stdin)
    }

    private fun utf8PtyModes(): ByteArray {
        val buf = ByteBuffer.allocate(6)
        buf.put(42) // IUTF8
        buf.putInt(1)
        buf.put(0) // TTY_OP_END
        return buf.array()
    }

    override fun resize(columns: Int, rows: Int) {
        runCatching { session?.resizePTY(columns, rows, 0, 0) }
    }

    override fun close() {
        runCatching { session?.close() }
        runCatching { connection?.close() }
        session = null
        connection = null
    }
}
