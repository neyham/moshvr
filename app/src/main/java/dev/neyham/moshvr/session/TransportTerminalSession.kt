package dev.neyham.moshvr.session

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

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

    private var columns = 80
    private var rows = 24

    override fun initializeEmulator(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        this.columns = columns
        this.rows = rows
        mEmulator = TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient)
        mShellPid = 1 // "running" sentinel; base class gates writes on pid > 0

        Thread({
            try {
                showSystemMessage("connecting...\r\n")
                val s = transport.connect(this.columns, this.rows)
                streams = s
                if (closed) {
                    transport.close()
                    return@Thread
                }
                StartupCommand.typedLine(startupCommand)?.let { write(it) }
                startWriterThread(s)
                readLoop(s)
            } catch (e: Exception) {
                if (!closed) {
                    showSystemMessage("\r\n\u001b[31m[connection failed: ${e.message}]\u001b[0m\r\n")
                    mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, -1))
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
        mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, 0))
    }

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
        if (isRunning) {
            mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, 0))
        }
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
