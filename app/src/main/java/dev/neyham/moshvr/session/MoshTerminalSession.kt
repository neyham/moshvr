package dev.neyham.moshvr.session

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.termux.terminal.JNI
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dev.neyham.moshvr.data.HostProfile
import dev.neyham.moshvr.data.KnownHostsStore
import java.io.File

/**
 * A mosh session: bootstraps `mosh-server` over SSH, then runs the bundled
 * mosh-client binary (packaged as libmosh_client.so) in a local PTY. The UDP
 * link can recover across network interruptions while the process remains alive.
 * Sleep/doff recovery still requires final-APK headset validation.
 */
class MoshTerminalSession(
    private val context: Context,
    private val profile: HostProfile,
    private val knownHosts: KnownHostsStore,
    private val ownerId: String,
    client: TerminalSessionClient,
) : TerminalSession("", "/", emptyArray(), emptyArray(), TRANSCRIPT_ROWS, client) {

    @Volatile
    private var ptyFd = -1

    private val lifecycle = MoshLifecycle()
    private val processLock = Any()

    private var columns = 80
    private var rows = 24
    private var cellW = 0
    private var cellH = 0

    override fun initializeEmulator(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        this.columns = columns
        this.rows = rows
        this.cellW = cellWidthPixels
        this.cellH = cellHeightPixels
        mEmulator = TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient)
        if (!lifecycle.beginStart()) return
        mShellPid = 1 // sentinel: "starting"

        Thread({
            try {
                showSystemMessage("[mosh] bootstrapping over ssh...\r\n")
                val endpoint = MoshBootstrap.run(profile, knownHosts, ownerId)
                if (lifecycle.closed) return@Thread
                showSystemMessage("[mosh] connected to ${endpoint.ip}:${endpoint.port}\r\n")
                startClient(endpoint)
            } catch (e: Exception) {
                if (!lifecycle.closed) {
                    showSystemMessage("\r\n\u001b[31m[mosh bootstrap failed: ${e.message}]\u001b[0m\r\n")
                }
                mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, -1))
            }
        }, "MoshBootstrap[$mHandle]").start()
    }

    private fun startClient(endpoint: MoshBootstrap.Endpoint) {
        if (lifecycle.closed) return
        val exe = File(context.applicationInfo.nativeLibraryDir, CLIENT_SO).absolutePath
        if (!File(exe).exists()) {
            showSystemMessage("\u001b[31m[mosh-client binary missing from this build]\u001b[0m\r\n")
            mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, -1))
            return
        }
        val terminfo = TerminfoInstaller.ensure(context)
        val env = arrayOf(
            "MOSH_KEY=${endpoint.key}",
            "TERM=xterm-256color",
            "TERMINFO=$terminfo",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
            "HOME=${context.filesDir.absolutePath}",
            "TMPDIR=${context.cacheDir.absolutePath}",
        )
        val processId = IntArray(1)
        val createdFd = JNI.createSubprocess(
            exe,
            context.filesDir.absolutePath,
            arrayOf("mosh-client", endpoint.ip, endpoint.port.toString()),
            env,
            processId,
            rows,
            columns,
            cellW,
            cellH,
        )
        val createdPid = processId[0]
        if (createdPid <= 1 || createdFd < 0) {
            reclaimBeforeWaiter(createdPid, createdFd)
            throw java.io.IOException("Could not create mosh terminal process")
        }
        val input: ParcelFileDescriptor.AutoCloseInputStream
        val output: ParcelFileDescriptor.AutoCloseOutputStream
        synchronized(processLock) {
            if (!lifecycle.adopt(createdPid, createdFd)) {
                reclaimBeforeWaiter(createdPid, createdFd)
                return
            }
            ptyFd = createdFd
            mShellPid = createdPid
            // Each stream owns a duplicate. Closing one cannot close/reuse another
            // thread's descriptor or race the lifecycle's original PTY descriptor.
            val readFd = try { ParcelFileDescriptor.fromFd(createdFd) } catch (e: Exception) {
                lifecycle.close(); ptyFd = -1; reclaimBeforeWaiter(createdPid, createdFd); throw e
            }
            val writeFd = try { ParcelFileDescriptor.fromFd(createdFd) } catch (e: Exception) {
                readFd.close(); lifecycle.close(); ptyFd = -1; reclaimBeforeWaiter(createdPid, createdFd); throw e
            }
            input = ParcelFileDescriptor.AutoCloseInputStream(readFd)
            output = ParcelFileDescriptor.AutoCloseOutputStream(writeFd)
        }

        Thread({
            try {
                input.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) return@Thread
                        if (!mProcessToTerminalIOQueue.write(buffer, 0, read)) return@Thread
                        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT)
                    }
                }
            } catch (_: Exception) {
            }
        }, "MoshReader[$createdPid]").start()

        Thread({
            val buffer = ByteArray(4096)
            try {
                output.use { output ->
                    while (true) {
                        val toWrite = mTerminalToProcessIOQueue.read(buffer, true)
                        if (toWrite == -1) return@Thread
                        output.write(buffer, 0, toWrite)
                    }
                }
            } catch (_: Exception) {
            }
        }, "MoshWriter[$createdPid]").start()

        Thread({
            val exitCode = JNI.waitFor(createdPid)
            mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, exitCode))
        }, "MoshWaiter[$createdPid]").start()
    }

    override fun updateSize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels)
        } else {
            this.columns = columns
            this.rows = rows
            this.cellW = cellWidthPixels
            this.cellH = cellHeightPixels
            synchronized(processLock) {
                if (ptyFd >= 0) JNI.setPtyWindowSize(ptyFd, rows, columns, cellWidthPixels, cellHeightPixels)
            }
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels)
        }
    }

    override fun finishIfRunning() {
        val wasListed = isRunning
        synchronized(processLock) {
            lifecycle.close().running?.let { reclaimProcess(it.pid, it.ptyFd) }
            ptyFd = -1
        }
        if (wasListed) {
            mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, 0))
        }
    }

    /** Starter thread owns reaping until the dedicated waiter has been started. */
    private fun reclaimBeforeWaiter(pid: Int, fd: Int) {
        reclaimProcess(pid, fd)
        if (pid > 1) runCatching { JNI.waitFor(pid) }
    }

    private fun reclaimProcess(pid: Int, fd: Int) {
        if (pid > 1) runCatching { Os.kill(pid, OsConstants.SIGKILL) }
        if (fd >= 0) runCatching { JNI.close(fd) }
    }

    override fun cleanupResources(exitStatus: Int) {
        synchronized(this) {
            mShellPid = -1
            mShellExitStatus = exitStatus
        }
        mTerminalToProcessIOQueue.close()
        mProcessToTerminalIOQueue.close()
        synchronized(processLock) {
            lifecycle.close().running?.let { JNI.close(it.ptyFd) }
            ptyFd = -1
        }
    }

    private fun showSystemMessage(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (mProcessToTerminalIOQueue.write(bytes, 0, bytes.size)) {
            mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT)
        }
    }

    companion object {
        private const val CLIENT_SO = "libmosh_client.so"
        private const val TRANSCRIPT_ROWS = 8000
    }
}
