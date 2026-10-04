package dev.neyham.moshvr.session

import java.io.InputStream
import java.io.OutputStream

class TransportStreams(val input: InputStream, val output: OutputStream)

/**
 * A bidirectional byte pipe carrying a remote PTY. Implementations: SSH (TCP);
 * mosh runs through a different path (local subprocess speaking UDP).
 */
interface TerminalTransport {
    /** Blocking connect; called on a background thread. */
    @Throws(Exception::class)
    fun connect(columns: Int, rows: Int): TransportStreams

    /** Inform the remote PTY of a new terminal size. */
    fun resize(columns: Int, rows: Int)

    /** Exit status the remote reported for the shell, or null if the link dropped without one. */
    fun remoteExitStatus(): Int? = null

    /** Called from a background thread when the transport notices the link is dead. */
    fun setDeadLinkListener(listener: () -> Unit) {}

    fun close()
}
