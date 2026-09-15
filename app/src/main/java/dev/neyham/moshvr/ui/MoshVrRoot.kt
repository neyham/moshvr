package dev.neyham.moshvr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import dev.neyham.moshvr.data.ProfileStore
import dev.neyham.moshvr.data.SettingsStore
import dev.neyham.moshvr.session.HostKeyPrompt
import dev.neyham.moshvr.session.SessionManager

/**
 * The whole app UI, shared verbatim between the 2D panel activity and the
 * immersive panel (hybrid app pattern).
 */
@Composable
fun MoshVrRoot(
    inputAllowed: () -> Boolean,
    sessionManager: SessionManager,
    profileStore: ProfileStore,
    settingsStore: SettingsStore,
    modeSwitchLabel: String,
    onModeSwitch: () -> Unit,
    extraTopBarContent: (@Composable () -> Unit)? = null,
    /** 2D home already rounds the window; clipping again punches gray/white corners. */
    clipCorners: Boolean = true,
    initialFontSizePx: Int = PanelLayout.DEFAULT_FONT_PX,
    startOnHome: Boolean = true,
    spatialDialogs: Boolean = false,
    onSpatialDialogChanged: (Boolean) -> Unit = {},
) {
    var spatialDialogOpen by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf(profileStore.load()) }
    var showHome by rememberSaveable { mutableStateOf(startOnHome) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var knownHosts by remember { mutableStateOf(sessionManager.knownHosts.list()) }
    var fontSizePx by rememberSaveable { mutableIntStateOf(initialFontSizePx) }
    DisposableEffect(sessionManager) {
        StickChrome.onCycleTab = { dir ->
            sessionManager.active?.session?.write(HerdrKeys.tabChord(dir))
        }
        onDispose { StickChrome.onCycleTab = null }
    }

    MoshVrTheme {
        PanelDialogHost(spatialDialogs, onOpenChanged = {
            spatialDialogOpen = it
            onSpatialDialogChanged(it)
        }) {
            val rootModifier =
                if (clipCorners) {
                    Modifier.fillMaxSize().clip(PanelShape).background(MoshVrColors.Background)
                } else {
                    Modifier.fillMaxSize().background(MoshVrColors.Background)
                }.pointerInput(inputAllowed) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (!inputAllowed()) event.changes.forEach { it.consume() }
                        }
                    }
                }.onPreviewKeyEvent { ev ->
                    if (!inputAllowed()) return@onPreviewKeyEvent true
                    if (spatialDialogOpen) return@onPreviewKeyEvent false
                    val code = when (ev.key) {
                        Key.DirectionUp -> android.view.KeyEvent.KEYCODE_DPAD_UP
                        Key.DirectionDown -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
                        Key.DirectionLeft -> android.view.KeyEvent.KEYCODE_DPAD_LEFT
                        Key.DirectionRight -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                        else -> return@onPreviewKeyEvent false
                    }
                    StickChrome.dispatchKey(
                        0,
                        code,
                        ev.type == KeyEventType.KeyDown,
                        0,
                        sessionManager.active?.client?.view,
                    )
                }

            Column(modifier = rootModifier) {
                // Top bar: tabs + actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MoshVrColors.Surface)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        sessionManager.sessions.forEach { open ->
                            val selected = !showHome && sessionManager.activeSessionId == open.id
                            ChromeText(
                                SessionChrome.tabTitle(open.title, open.finished),
                                if (selected) MoshVrColors.Green else MoshVrColors.TextSecondary,
                            ) {
                                sessionManager.activeSessionId = open.id
                                showHome = false
                                sessionManager.sessions.firstOrNull { it.id == open.id }?.client?.view?.requestFocus()
                            }
                            ChromeText("×", MoshVrColors.TextSecondary) { sessionManager.close(open.id) }
                        }
                        ChromeText("+", MoshVrColors.Green) { showHome = true }
                    }
                    ChromeText("A-", MoshVrColors.TextSecondary) { if (fontSizePx > 14) fontSizePx -= 2 }
                    ChromeText("A+", MoshVrColors.TextSecondary) { if (fontSizePx < 64) fontSizePx += 2 }
                    ChromeText("⚙", MoshVrColors.TextSecondary) { showSettings = true }
                    extraTopBarContent?.invoke()
                    ChromeText(modeSwitchLabel, MoshVrColors.Green, onClick = onModeSwitch)
                }

                val active = sessionManager.active
                if (showHome || active == null) {
                    ConnectionsScreen(
                        profiles = profiles,
                        onConnect = { profile ->
                            sessionManager.open(profile)
                            showHome = false
                        },
                        onSave = { profiles = profileStore.upsert(it) },
                        onDelete = { profiles = profileStore.delete(it) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    TerminalPane(
                        open = active,
                        fontSizePx = fontSizePx,
                        onFontSizeDelta = { delta -> fontSizePx = (fontSizePx + delta).coerceIn(14, 64) },
                        settingsStore = settingsStore,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (showSettings) {
                SettingsDialog(
                    current = settingsStore.load(),
                    knownHosts = knownHosts,
                    onDismiss = { showSettings = false },
                    onSave = {
                        settingsStore.save(it)
                        showSettings = false
                    },
                    onClearApiKey = {
                        settingsStore.clearApiKey()
                        showSettings = false
                    },
                    onForgetHost = { entry ->
                        sessionManager.knownHosts.forget(entry.host, entry.port)
                        knownHosts = sessionManager.knownHosts.list()
                    },
                    onClearKnownHosts = {
                        sessionManager.knownHosts.clearAll()
                        knownHosts = sessionManager.knownHosts.list()
                    },
                    onAbout = {
                        showSettings = false
                        showAbout = true
                    },
                )
            }
            if (showAbout) {
                AboutDialog(onDismiss = { showAbout = false })
            }
            HostKeyPrompt.current?.let { request ->
                HostKeyConfirmDialog(
                    request = request,
                    onRespond = { accepted -> HostKeyPrompt.respond(request.id, accepted) },
                )
            }
        }
    }
}

internal fun Modifier.chromeUnfocusable(): Modifier = focusProperties { canFocus = false }

@Composable
internal fun ChromeText(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .chromeUnfocusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}
