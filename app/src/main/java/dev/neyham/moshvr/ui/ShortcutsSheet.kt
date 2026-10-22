package dev.neyham.moshvr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What every control does, shown from the "?" button. Keep in step with ControllerShortcuts. */
object ShortcutsCheatsheet {
    class Section(val title: String, val rows: List<Pair<String, String>>)

    val sections = listOf(
        Section(
            "Right controller (VR mode)",
            listOf(
                "Trigger" to "Click",
                "Grip, hold" to "Talk; release to finish (on-headset speech)",
                "A" to "Enter (sends the dictated text if any)",
                "B" to "Backspace; while talking: cancel",
                "Stick ← →" to "herdr previous / next tab",
                "Stick ↑ ↓" to "herdr previous / next workspace",
                "Stick press" to "Ctrl+C (stick centred; sent on release)",
            ),
        ),
        Section(
            "Left controller (VR mode)",
            listOf(
                "Trigger" to "Click",
                "X" to "Show / hide keyboard",
                "Y" to "Esc",
                "Stick ↑ ↓" to "Scroll the terminal (hold to speed up)",
                "Grip + stick" to "Arrow keys (hold to repeat)",
                "Stick press" to "Tab",
            ),
        ),
        Section(
            "Hands (VR mode)",
            listOf(
                "Thumb swipe ← →" to "Previous / next moshVR tab",
                "Thumb tap" to "Enter / leave the construct",
            ),
        ),
        Section(
            "Bottom bar",
            listOf(
                "🎤 MIC" to "Talk (tap again to stop)",
                "⌨ KEYB" to "System keyboard",
                "⏎ ENTER" to "Enter",
                "^C" to "Ctrl+C: stop the running command",
                "◀TAB / TAB▶" to "herdr previous / next tab",
                "PgUp / PgDn" to "Scroll a page",
            ),
        ),
        Section(
            "Top bar (VR mode)",
            listOf(
                "[ MOVE ]" to "Then grip the wall to move it; tap again when done",
                "[ RECENTER ]" to "Put the wall back in front of you",
                "[ CONSTRUCT ]" to "Digital-rain world instead of passthrough",
            ),
        ),
    )
}

@Composable
fun ShortcutsDialog(onDismiss: () -> Unit) {
    WindowSafeAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MoshVrColors.Surface,
        title = { Text("Controls") },
        text = {
            Column(
                modifier = androidx.compose.ui.Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ShortcutsCheatsheet.sections.forEach { section ->
                    Text(
                        section.title,
                        color = MoshVrColors.Green,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    section.rows.forEach { (control, action) ->
                        Row {
                            Text(
                                control,
                                color = MoshVrColors.Green,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                modifier = androidx.compose.ui.Modifier.width(170.dp),
                            )
                            Text(action, color = MoshVrColors.TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = MoshVrColors.Green) }
        },
    )
}
