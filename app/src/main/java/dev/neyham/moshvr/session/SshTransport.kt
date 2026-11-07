package dev.neyham.moshvr.session

import com.trilead.ssh2.ChannelCondition
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

    @Volatile
    private var connection: Connection? = null

    @Volatile
    private var session: Session? = null

    @Volatile
    private var deadLinkListener: (() -> Unit)? = null

    private var keepAlive: Thread? = null

    private val resizer = SshResizer()

    override fun connect(columns: Int, rows: Int): TransportStreams {
        val conn = SshAuth.connectAndAuthenticate(profile, knownHosts, ownerId)
        connection = conn
        val sess = conn.openSession()
        session = sess
        sess.requestPTY("xterm-256color", columns, rows, columns * 8, rows * 16, utf8PtyModes())
        sess.startShell()
        keepAlive = startKeepAlive(conn, sess)
        return TransportStreams(sess.stdout, sess.stdin)
    }

    /**
     * A sleeping headset or an idle NAT drops TCP silently, and a blocked read would never
     * notice. Ping the server; if no reply arrives in time, report the link dead.
     * Pings the session channel: Connection.ping() holds the connection's monitor while it
     * waits, so a hung one would block close() forever and leak the socket and remote shell.
     * Session.ping() does not, and close() wakes it with an error.
     */
    private fun startKeepAlive(conn: Connection, sess: Session): Thread = Thread({
        try {
            while (connection === conn) {
                Thread.sleep(SshKeepAlive.INTERVAL_MS)
                if (connection !== conn) return@Thread
                if (!SshKeepAlive.pingWithin(SshKeepAlive.TIMEOUT_MS) { sess.ping() }) {
                    if (connection === conn) deadLinkListener?.invoke()
                    return@Thread
                }
            }
        } catch (_: InterruptedException) {
            // Closed.
        }
    }, "SshKeepAlive").apply {
        isDaemon = true
        start()
    }

    override fun remoteExitStatus(): Int? {
        val sess = session ?: return null
        return runCatching {
            sess.waitForCondition(ChannelCondition.EXIT_STATUS, EXIT_STATUS_WAIT_MS)
            sess.exitStatus
        }.getOrNull()
    }

    override fun setDeadLinkListener(listener: () -> Unit) {
        deadLinkListener = listener
    }

    private fun utf8PtyModes(): ByteArray {
        val buf = ByteBuffer.allocate(6)
        buf.put(42) // IUTF8
        buf.putInt(1)
        buf.put(0) // TTY_OP_END
        return buf.array()
    }

    override fun resize(columns: Int, rows: Int) {
        val sess = session ?: return
        resizer.post(columns, rows) { c, r -> sess.resizePTY(c, r, 0, 0) }
    }

    override fun close() {
        val sess = session
        val conn = connection
        session = null
        connection = null
        keepAlive?.interrupt()
        keepAlive = null
        if (sess == null && conn == null) return
        // close() can block behind a hung ping(); never do that on the caller (often main) thread.
        Thread({
            runCatching { sess?.close() }
            runCatching { conn?.close() }
        }, "SshClose").apply {
            isDaemon = true
            start()
        }
    }

    private companion object {
        const val EXIT_STATUS_WAIT_MS = 500L
    }
}

/**
 * Sends PTY resizes off the caller's thread, newest size only.
 *
 * Resizes arrive on the main thread. There a socket write throws NetworkOnMainThreadException
 * after sshlib has already encrypted the packet, so the cipher and sequence number move on
 * without the bytes being sent and the server drops the link ("Corrupted MAC on input").
 */
class SshResizer {
    private val latest = java.util.concurrent.atomic.AtomicReference<Pair<Int, Int>?>(null)
    private val executor = java.util.concurrent.ThreadPoolExecutor(
        0, 1, 5, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.LinkedBlockingQueue(),
    ) { r -> Thread(r, "SshResize").apply { isDaemon = true } }

    fun post(columns: Int, rows: Int, send: (Int, Int) -> Unit) {
        latest.set(columns to rows)
        executor.execute {
            val (c, r) = latest.getAndSet(null) ?: return@execute
            runCatching { send(c, r) }
        }
    }
}

object SshKeepAlive {
    const val INTERVAL_MS = 15_000L
    const val TIMEOUT_MS = 15_000L

    /** Runs [ping] on a helper thread; true if it returned normally within [timeoutMs]. */
    fun pingWithin(timeoutMs: Long, ping: () -> Unit): Boolean {
        val ok = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = Thread({
            ok.set(runCatching(ping).isSuccess)
        }, "SshPing").apply {
            isDaemon = true
            start()
        }
        worker.join(timeoutMs)
        return !worker.isAlive && ok.get()
    }
}
