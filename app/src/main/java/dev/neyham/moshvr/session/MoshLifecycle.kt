package dev.neyham.moshvr.session

/**
 * Atomic start/close for the mosh-client subprocess. Close during createSubprocess
 * cannot leave a PID that nobody will kill: adopt after close returns Keep=false
 * so the starter reclaims the late process.
 */
class MoshLifecycle {
    data class Process(val pid: Int, val ptyFd: Int)
    data class CloseResult(val running: Process?)

    enum class Phase { Idle, Starting, Running, Closed }

    private val lock = Any()

    @Volatile
    var phase: Phase = Phase.Idle
        private set

    @Volatile
    var process: Process? = null
        private set

    val closed: Boolean
        get() = phase == Phase.Closed

    fun beginStart(): Boolean = synchronized(lock) {
        if (phase != Phase.Idle) return false
        phase = Phase.Starting
        true
    }

    /** True: keep the process. False: caller must kill pid and close pty now. */
    fun adopt(pid: Int, ptyFd: Int): Boolean = synchronized(lock) {
        val seen = Process(pid, ptyFd)
        if (phase != Phase.Starting || pid <= 1 || ptyFd < 0) return false
        process = seen
        phase = Phase.Running
        true
    }

    fun close(): CloseResult = synchronized(lock) {
        val running = process
        process = null
        phase = Phase.Closed
        CloseResult(running)
    }
}
