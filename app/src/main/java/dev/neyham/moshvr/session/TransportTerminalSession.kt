package dev.neyham.moshvr.session

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [TerminalSession] backed by a [TerminalTransport] (e.g. SSH) instead of a
 * local PTY subprocess. Reuses the vendored Termux emulator and its
 * main-thread plumbing; overrides everything that touches the JNI/PTY layer.
 */
class TransportTerminalSession(
    private val transport: TerminalTransport,
    private val startupCommand: String? = null,
    client: TerminalSessionClient,
) : TerminalSession(
    /* shellPath */ "",
    /* cwd */ "/",
    /* args */ emptyArray(),
    /* env */ emptyArray(),
    /* transcriptRows */ TRANSCRIPT_ROWS,
    client,
) {

    @Volatile
    private var streams: TransportStreams? = null

    @Volatile
    private var closed = false

    /** Guards MSG_PROCESS_EXITED: reader, keepalive and close() can all race to end the session. */
    private val exitPosted = AtomicBoolean(false)

    /** How the session ended; read on the main thread when the exit banner is drawn. */
    @Volatile
    private var ending: SessionEnding = SessionEnding.Disconnected

    // Written on the main thread, read by the connect thread.
    @Volatile private var columns = 80
    @Volatile private var rows = 24

    override fun initializeEmulator(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        this.columns = columns
        this.rows = rows
        mEmulator = TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient)
        mShellPid = 1 // "running" sentinel; base class gates writes on pid > 0
        transport.setDeadLinkListener {
            if (!closed) postExit(SessionEnding.Disconnected, 0)
        }

        Thread({
            try {
                showSystemMessage("connecting...\r\n")
                val connectedColumns = this.columns
                val connectedRows = this.rows
                val s = transport.connect(connectedColumns, connectedRows)
                streams = s
                if (closed) {
                    transport.close()
                    return@Thread
                }
                // Resizes while connecting were skipped (no streams yet); send the latest size.
                if (this.columns != connectedColumns || this.rows != connectedRows) {
                    transport.resize(this.columns, this.rows)
                }
                // Writer first: a startup command longer than the 4 KB input queue would block forever.
                startWriterThread(s)
                StartupCommand.typedLine(startupCommand)?.let { write(it) }
                readLoop(s)
            } catch (e: Exception) {
                if (!closed) {
                    showSystemMessage("\r\n\u001b[31m[connection failed: ${e.message}]\u001b[0m\r\n")
                    postExit(SessionEnding.ConnectFailed, -1)
                }
            }
        }, "TransportConnect[${mHandle}]").start()
    }

    /** Reader: transport -> emulator (via main thread handler). Runs on the connect thread. */
    private fun readLoop(s: TransportStreams) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val read = s.input.read(buffer)
                if (read == -1) break
                if (!mProcessToTerminalIOQueue.write(buffer, 0, read)) break
                mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT)
            }
        } catch (_: Exception) {
            // Connection torn down.
        }
        if (closed) return
        val status = transport.remoteExitStatus()
        if (status != null) {
            postExit(SessionEnding.RemoteExit(status), status)
        } else {
            postExit(SessionEnding.Disconnected, 0)
        }
    }

    private fun postExit(how: SessionEnding, exitCode: Int) {
        if (!exitPosted.compareAndSet(false, true)) return
        ending = how
        mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, exitCode))
    }

    override fun exitDescription(exitCode: Int): String = ending.banner()

    /** Writer: user input queue -> transport. */
    private fun startWriterThread(s: TransportStreams) {
        Thread({
            val buffer = ByteArray(4096)
            try {
                while (true) {
                    val toWrite = mTerminalToProcessIOQueue.read(buffer, true)
                    if (toWrite == -1) return@Thread
                    s.output.write(buffer, 0, toWrite)
                    s.output.flush()
                }
            } catch (_: Exception) {
                // Connection torn down.
            }
        }, "TransportWriter[${mHandle}]").start()
    }

    override fun updateSize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels)
        } else {
            this.columns = columns
            this.rows = rows
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels)
            if (streams != null) transport.resize(columns, rows)
        }
    }

    override fun finishIfRunning() {
        closed = true
        transport.close()
        if (isRunning) postExit(SessionEnding.Closed, 0)
    }

    override fun cleanupResources(exitStatus: Int) {
        synchronized(this) {
            mShellPid = -1
            mShellExitStatus = exitStatus
        }
        mTerminalToProcessIOQueue.close()
        mProcessToTerminalIOQueue.close()
        closed = true
        transport.close()
    }

    /** Feed informational text straight into the emulator (not to the remote). */
    private fun showSystemMessage(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (mProcessToTerminalIOQueue.write(bytes, 0, bytes.size)) {
            mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT)
        }
    }

    companion object {
        private const val TRANSCRIPT_ROWS = 8000
    }
}

sealed class SessionEnding {
    data object Disconnected : SessionEnding()
    data object ConnectFailed : SessionEnding()
    data object Closed : SessionEnding()
    data class RemoteExit(val status: Int) : SessionEnding()

    fun banner(): String = when (this) {
        Disconnected -> "\r\n\u001b[33m[disconnected - press Enter to reconnect]\u001b[0m"
        ConnectFailed -> "\r\n[press Enter to retry]"
        Closed -> "\r\n[closed]"
        is RemoteExit ->
            if (status == 0) "\r\n[session ended - press Enter to reconnect]"
            else "\r\n[session ended (code $status) - press Enter to reconnect]"
    }
}
