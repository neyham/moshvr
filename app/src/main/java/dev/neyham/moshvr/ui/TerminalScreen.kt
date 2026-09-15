package dev.neyham.moshvr.ui

import androidx.compose.foundation.verticalScroll

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.neyham.moshvr.data.SettingsStore
import dev.neyham.moshvr.session.OpenSession
import dev.neyham.moshvr.voice.VoiceInput
import dev.neyham.moshvr.voice.VoiceSession

/** Ctrl/Alt latch state shared between the extra-keys row and the terminal view. */
class ModifierKeysState {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
}

class MoshVrTerminalViewClient(
    private val context: Context,
    private val keys: ModifierKeysState,
    private val fontSizeChanged: (delta: Int) -> Unit,
) : TerminalViewClient {

    var view: TerminalView? = null

    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            fontSizeChanged(if (scale > 1f) 2 else -2)
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        val v = view ?: return
        v.requestFocus()
        val imm = context.getSystemService(InputMethodManager::class.java)
        val emu = v.mEmulator
        val reportClick = TerminalPointer.shouldReportClick(
            mouseTrackingActive = emu?.isMouseTrackingActive == true,
        )
        if (TerminalPointer.shouldShowImeOnTap(reportClick)) {
            imm?.showSoftInput(v, 0)
        } else {
            imm?.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    override fun shouldBackButtonBeMappedToEscape() = true

    // TYPE_NULL breaks many IMEs (including dictation); force char-based input.
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (StickChrome.dispatchKey(e.source, keyCode, true, e.repeatCount, view)) return true
        return TerminalPointer.shouldSwallowControllerKey(e.source, keyCode)
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent) =
        TerminalPointer.shouldSwallowControllerKey(e.source, keyCode)
    override fun onLongPress(event: MotionEvent) = false

    override fun readControlKey() = keys.ctrl
    override fun readAltKey() = keys.alt
    override fun readShiftKey() = false
    override fun readFnKey() = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        // One-shot modifier latches.
        keys.ctrl = false
        keys.alt = false
        return false
    }

    override fun onEmulatorSet() {}

    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}

@Composable
fun TerminalPane(
    open: OpenSession,
    fontSizePx: Int,
    onFontSizeDelta: (Int) -> Unit,
    settingsStore: SettingsStore,
    modifier: Modifier = Modifier,
) {
    val keys = remember { ModifierKeysState() }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val composerText = open.composerDraft
    var composerOpen by remember { mutableStateOf(false) }
    var inputHint by remember { mutableStateOf<String?>(null) }
    var showSttConsent by remember { mutableStateOf(false) }
    var showMicRationale by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val composerFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun showComposerKeyboard() {
        composerOpen = true
        runCatching { composerFocus.requestFocus() }
        keyboard?.show()
    }
    val voice = remember(open.id) {
        VoiceInput(
            context = context,
            settingsStore = settingsStore,
            scope = scope,
            onResult = { spoken ->
                open.composerDraft = ComposerDraft.mergeSpoken(open.composerDraft, spoken)
            },
            onStatus = { voiceStatus = it },
            onRecording = { recording = it },
            onBusy = { transcribing = it },
            onFallbackToKeyboard = { showComposerKeyboard() },
            onNeedConsent = { showSttConsent = true },
        )
    }
    fun beginTalk() {
        if (transcribing) return
        if (voice.usesCloudStt && !voice.hasMicPermission) {
            when (MicPermission.lastDecision) {
                MicPermission.Decision.DontAskAgain -> {
                    voiceStatus = MicPermission.DONT_ASK_AGAIN
                    MicPermission.openAppSettings(context)
                }
                else -> {
                    showMicRationale = true
                    voiceStatus = MicPermission.NEEDED
                }
            }
            return
        }
        voice.start()
    }

    fun endTalk() {
        voice.stop()
    }

    fun toggleTalk() {
        if (recording) endTalk() else beginTalk()
    }

    DisposableEffect(voice) {
        VoiceSession.attach(voice, context)
        VoicePtt.bind(voice, toggle = { toggleTalk() }, cancel = { voice.cancel() })
        onDispose {
            VoicePtt.unbind(voice)
            voice.release()
        }
    }
    if (showMicRationale) {
        WindowSafeAlertDialog(
            onDismissRequest = { showMicRationale = false },
            containerColor = MoshVrColors.Surface,
            title = { Text("Microphone") },
            text = { Text(MicPermission.RATIONALE, color = MoshVrColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showMicRationale = false
                    voiceStatus = if (MicPermission.request(context)) {
                        "Allow microphone, then tap MIC again"
                    } else {
                        MicPermission.DENIED
                    }
                }) { Text("Continue", color = MoshVrColors.Green) }
            },
            dismissButton = {
                TextButton(onClick = { showMicRationale = false }) {
                    Text("Cancel", color = MoshVrColors.TextSecondary)
                }
            },
        )
    }
    if (showSttConsent) {
        val consentSettings = remember { settingsStore.load() }
        var adultConfirmed by remember { mutableStateOf(false) }
        WindowSafeAlertDialog(
            onDismissRequest = { showSttConsent = false },
            containerColor = MoshVrColors.Surface,
            title = { Text("Cloud speech-to-text") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        dev.neyham.moshvr.voice.SttPolicy.disclosure(
                            consentSettings.apiBaseUrl, consentSettings.transcriptionModel,
                        ), color = MoshVrColors.TextSecondary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = adultConfirmed, onCheckedChange = { adultConfirmed = it },
                        )
                        Text("I am 18 or older and eligible to use this provider")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = adultConfirmed && dev.neyham.moshvr.voice.SttPolicy.isHttps(consentSettings.apiBaseUrl), onClick = {
                    val current = settingsStore.load()
                    // Do not bind consent to an endpoint the user did not see in this dialog.
                    if (current.apiBaseUrl == consentSettings.apiBaseUrl &&
                        current.transcriptionModel == consentSettings.transcriptionModel) {
                        settingsStore.save(current.withSpeechConsent(adultConfirmed))
                        showSttConsent = false
                        voice.start()
                    } else {
                        showSttConsent = false
                        voiceStatus = "Speech settings changed. Tap MIC to review them again."
                    }
                }) { Text("Agree and record", color = MoshVrColors.Green) }
            },
            dismissButton = {
                TextButton(onClick = { showSttConsent = false }) {
                    Text("Cancel", color = MoshVrColors.TextSecondary)
                }
            },
        )
    }

    Column(modifier = modifier.background(Color.Black).imePadding()) {
        val stickScroll = rememberScrollableState { delta ->
            if (kotlin.math.abs(delta) < 2f) return@rememberScrollableState 0f
            val rows = if (delta > 0) -1 else 1
            terminalView?.scrollLines(rows)
            inputHint = if (rows < 0) "scroll ↑" else "scroll ↓"
            delta
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .scrollable(stickScroll, Orientation.Vertical)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val dy = event.changes.fold(0f) { acc, c -> acc + c.scrollDelta.y }
                            if (dy != 0f) {
                                val rows = if (dy < 0) -1 else 1
                                terminalView?.scrollLines(rows)
                                inputHint = if (rows < 0) "scroll ↑" else "scroll ↓"
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val client = MoshVrTerminalViewClient(ctx, keys, onFontSizeDelta)
                    TerminalView(ctx, null).apply {
                        // Quest's compositor garbles custom Canvas text under GPU layers.
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                        client.view = this
                        setTerminalViewClient(client)
                        setTextSize(fontSizePx)
                        setTypeface(TerminalFonts.load(ctx))
                        setBackgroundColor(android.graphics.Color.BLACK)
                        keepScreenOn = settingsStore.load().keepScreenOn
                        isFocusable = true
                        isFocusableInTouchMode = true
                        tag = fontSizePx
                    }
                },
                update = { v ->
                    terminalView = v
                    v.keepScreenOn = settingsStore.load().keepScreenOn
                    if (v.tag != fontSizePx) {
                        v.tag = fontSizePx
                        v.setTextSize(fontSizePx)
                    }
                    if (v.mTermSession !== open.session) {
                        open.client.view = v
                        v.attachSession(open.session)
                    }
                    v.onExternalScroll = java.util.function.IntConsumer { rows ->
                        inputHint = if (rows < 0) "scroll ↑" else "scroll ↓"
                    }
                    // Do not onScreenUpdated() here — it snaps scroll back to the bottom.
                },
            )
            LaunchedEffect(open.id, terminalView) {
                terminalView?.requestFocus()
            }
            if (composerText.isNotEmpty()) {
                Text(
                    "▸ $composerText",
                    color = MoshVrColors.Green,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color(0xE6101612))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            val hud = when {
                recording -> "REC  tap STOP"
                transcribing -> "…"
                inputHint != null -> inputHint
                else -> null
            }
            if (hud != null) {
                Text(
                    hud,
                    color = MoshVrColors.Green,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color(0xC0101612))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        ExtraKeysRow(
            keys = keys,
            recording = recording,
            transcribing = transcribing,
            onWrite = { open.session.write(it) },
            onKeyCode = { code -> terminalView?.handleKeyCode(code, 0) },
            onMic = { toggleTalk() },
            onCancelVoice = { voice.cancel() },
            onScroll = { rows -> terminalView?.scrollLines(rows) },
            onKeyboard = { showComposerKeyboard() },
        )
        if (composerOpen || composerText.isNotEmpty() || voiceStatus != null) {
            CommandComposer(
                text = composerText,
                onTextChange = { open.composerDraft = it },
                recording = recording,
                transcribing = transcribing,
                status = voiceStatus,
                onMic = { toggleTalk() },
                onSend = { text ->
                    open.session.write(text + "\r")
                    composerOpen = false
                },
                focusRequester = composerFocus,
            )
        }
    }
}

@Composable
private fun ExtraKeysRow(
    keys: ModifierKeysState,
    recording: Boolean,
    transcribing: Boolean,
    onWrite: (String) -> Unit,
    onKeyCode: (Int) -> Unit,
    onMic: () -> Unit,
    onCancelVoice: () -> Unit,
    onScroll: (Int) -> Unit,
    onKeyboard: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoshVrColors.Surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (recording || transcribing) {
            ExtraKey("X", active = true) { onCancelVoice() }
        }
        ExtraKeys.catalog.forEach { spec ->
            val label = if (spec.action is ExtraKeyAction.Mic) {
                ExtraKeys.micLabel(recording, transcribing)
            } else {
                spec.label
            }
            val active = when (spec.action) {
                ExtraKeyAction.ToggleCtrl -> keys.ctrl
                ExtraKeyAction.ToggleAlt -> keys.alt
                ExtraKeyAction.Mic -> recording || transcribing
                else -> false
            }
            ExtraKey(
                label,
                active = active,
            ) {
                when (val action = spec.action) {
                    ExtraKeyAction.Mic -> onMic()
                    ExtraKeyAction.Keyboard -> onKeyboard()
                    ExtraKeyAction.ToggleCtrl -> keys.ctrl = !keys.ctrl
                    ExtraKeyAction.ToggleAlt -> keys.alt = !keys.alt
                    is ExtraKeyAction.Write -> onWrite(action.payload)
                    is ExtraKeyAction.KeyCode -> onKeyCode(action.code)
                    is ExtraKeyAction.Scroll -> onScroll(action.rows)
                    ExtraKeyAction.CancelVoice -> onCancelVoice()
                }
            }
        }
    }
}

@Composable
private fun ExtraKey(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 40.dp)
            .clip(shape)
            .background(if (active) MoshVrColors.SurfaceBright else Color(0xFF0D1410))
            .border(1.dp, if (active) MoshVrColors.Green else MoshVrColors.SurfaceBright, shape)
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) MoshVrColors.Green else MoshVrColors.TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

/**
 * Text composer under the terminal. Voice is shared with the extra-keys MIC:
 * system dictation when available, otherwise Whisper if a key is configured.
 */
@Composable
private fun CommandComposer(
    text: String,
    onTextChange: (String) -> Unit,
    recording: Boolean,
    transcribing: Boolean,
    status: String?,
    onMic: () -> Unit,
    onSend: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoshVrColors.Surface)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        fun submitComposer() {
            if (text.isNotBlank()) {
                onSend(text.trim())
                onTextChange("")
            } else {
                onSend("")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MoshVrColors.TextPrimary,
                ),
                placeholder = {
                    Text(
                        "Type a command, then ENTER",
                        color = MoshVrColors.TextSecondary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitComposer() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoshVrColors.Green,
                    unfocusedBorderColor = MoshVrColors.SurfaceBright,
                    cursorColor = MoshVrColors.Green,
                ),
            )
            if (transcribing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).padding(2.dp),
                    color = MoshVrColors.Green,
                    strokeWidth = 2.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onMic),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (recording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (recording) "Stop recording" else "Start recording",
                        tint = if (recording) MoshVrColors.Error else MoshVrColors.Green,
                    )
                }
            }
            TextButton(
                onClick = { submitComposer() },
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                Text("⏎", color = MoshVrColors.Green, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            }
        }
        status?.let {
            Text(
                it,
                color = if (recording) MoshVrColors.Green else MoshVrColors.Error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }
    }
}
