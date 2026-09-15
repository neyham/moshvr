package dev.neyham.moshvr.session

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import dev.neyham.moshvr.data.HostProfile
import dev.neyham.moshvr.data.KnownHostsStore
import java.util.UUID

/**
 * One open terminal tab: a Termux-based session plus UI state. The session object
 * outlives any particular TerminalView (2D panel vs immersive panel can reattach).
 */
class OpenSession(
    val id: String,
    val profile: HostProfile,
    val client: ViewTerminalSessionClient,
    val session: TerminalSession,
) {
    var title by mutableStateOf(profile.displayName)
    var finished by mutableStateOf(false)
    /** Unsent composer text; survives 2D↔VR Activity handoff. Never auto-sent. */
    var composerDraft by mutableStateOf("")
}

/**
 * Routes Termux session callbacks to whichever TerminalView is currently attached.
 */
class ViewTerminalSessionClient(
    private val onFinished: () -> Unit,
    private val onTitle: (String?) -> Unit,
    private val onScreenChanged: (TerminalSession) -> Unit = {},
) : TerminalSessionClient {

    @Volatile
    var view: TerminalView? = null

    override fun onTextChanged(changedSession: TerminalSession) {
        view?.onScreenUpdated()
        onScreenChanged(changedSession)
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        onTitle(changedSession.title)
    }

    override fun onSessionFinished(finishedSession: TerminalSession) = onFinished()

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        val v = view ?: return
        val clipboard = v.context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text ?: ""))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val v = view ?: return
        val clipboard = v.context.getSystemService(android.content.ClipboardManager::class.java)
        val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(v.context)?.toString()
        if (!text.isNullOrEmpty()) session?.emulator?.paste(text)
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {
        view?.onScreenUpdated()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}

/** App-wide registry of open sessions, shared between 2D and immersive activities. */
class SessionManager(private val appContext: Context) {

    val sessions = mutableStateListOf<OpenSession>()
    var activeSessionId by mutableStateOf<String?>(null)

    val knownHosts = KnownHostsStore(appContext)

    val active: OpenSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }

    /** Open a session for the profile, choosing the transport it asks for. */
    fun open(profile: HostProfile): OpenSession {
        val id = UUID.randomUUID().toString()
        lateinit var open: OpenSession
        val client = ViewTerminalSessionClient(
            onFinished = { open.finished = true },
            onTitle = { title -> if (!title.isNullOrBlank()) open.title = title },
        )
        val session = if (profile.useMosh) {
            MoshTerminalSession(appContext, profile, knownHosts, id, client)
        } else {
            TransportTerminalSession(SshTransport(profile, knownHosts, id), profile.startupCommand, client)
        }
        open = OpenSession(id, profile, client, session)
        sessions.add(open)
        activeSessionId = id
        return open
    }

    /** Cycle the active session tab (used by hand microgestures in immersive mode). */
    fun cycleActive(direction: Int) {
        if (sessions.isEmpty()) return
        val index = sessions.indexOfFirst { it.id == activeSessionId }
        val next = dev.neyham.moshvr.ui.SessionChrome.cycleIndex(index, sessions.size, direction)
        if (next >= 0) activeSessionId = sessions[next].id
    }

    fun close(id: String) {
        val open = sessions.firstOrNull { it.id == id } ?: return
        HostKeyPrompt.cancelOwner(id)
        runCatching { open.session.finishIfRunning() }
        sessions.remove(open)
        if (activeSessionId == id) {
            activeSessionId = sessions.lastOrNull()?.id
        }
    }

    fun closeAll() {
        sessions.toList().forEach { close(it.id) }
    }
}
