package dev.neyham.moshvr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** One stack per panel; disposing an older dialog must not remove a newer one. */
internal class PanelDialogRegistry<T : Any> {
    private val entries = mutableStateListOf<T>()
    val top: T? get() = entries.lastOrNull()
    fun add(entry: T) { if (entry !in entries) entries.add(entry) }
    fun remove(entry: T) { entries.remove(entry) }
}

private class PanelDialogEntry(val render: @Composable () -> Unit)
private val LocalPanelDialogs = staticCompositionLocalOf<PanelDialogRegistry<PanelDialogEntry>?> { null }

/** Spatial Compose panels cannot create an Android TYPE_APPLICATION dialog window. */
@Composable
fun PanelDialogHost(
    enabled: Boolean,
    onOpenChanged: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val registry = remember { PanelDialogRegistry<PanelDialogEntry>() }
    val top = if (enabled) registry.top else null
    val callback by rememberUpdatedState(onOpenChanged)
    SideEffect { callback(top != null) }
    DisposableEffect(Unit) { onDispose { callback(false) } }
    CompositionLocalProvider(LocalPanelDialogs provides if (enabled) registry else null) {
        Box(Modifier.fillMaxSize()) {
            Box(if (top != null) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) {
                content()
            }
            top?.render?.invoke()
        }
    }
}

/** Keep forms, confirmations and consent inside the owning spatial panel. */
@Composable
fun WindowSafeAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = MoshVrColors.Surface,
) {
    val registry = LocalPanelDialogs.current
    if (registry == null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            title = title,
            text = text,
            containerColor = containerColor,
        )
        return
    }
    val latestContent by rememberUpdatedState<@Composable () -> Unit> {
        val focus = remember { FocusRequester() }
        val manager = LocalFocusManager.current
        LaunchedEffect(Unit) {
            manager.clearFocus(force = true)
            focus.requestFocus()
        }
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.padding(24.dp).sizeIn(maxWidth = 560.dp)
                    .fillMaxWidth().focusRequester(focus).focusable()
                    .semantics { paneTitle = "Dialog" }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* Block clicks through the card to the dismissal scrim. */ },
                shape = MaterialTheme.shapes.extraLarge,
                color = containerColor,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    title?.let { ProvideTextStyle(MaterialTheme.typography.headlineSmall) { it() } }
                    text?.let {
                        Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
    val entry = remember(registry) { PanelDialogEntry { latestContent() } }
    DisposableEffect(registry, entry) {
        registry.add(entry)
        onDispose { registry.remove(entry) }
    }
}
