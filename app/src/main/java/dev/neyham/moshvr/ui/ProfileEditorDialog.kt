package dev.neyham.moshvr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.view.WindowManager
import dev.neyham.moshvr.data.AuthMethod
import dev.neyham.moshvr.data.CryptoStore
import dev.neyham.moshvr.data.HostProfile
import java.util.UUID

@Composable
fun ProfileEditorDialog(
    initial: HostProfile?,
    onDismiss: () -> Unit,
    onSave: (HostProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var authMethod by remember { mutableStateOf(initial?.authMethod ?: AuthMethod.PASSWORD) }
    var password by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var showPrivateKey by remember { mutableStateOf(false) }
    var keyPassphrase by remember { mutableStateOf("") }
    var startupCommand by remember { mutableStateOf(initial?.startupCommand ?: "") }
    var useMosh by remember { mutableStateOf(initial?.useMosh ?: false) }
    val window = MicPermission.findActivity(LocalContext.current)?.window
    DisposableEffect(showPrivateKey) {
        if (showPrivateKey) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    WindowSafeAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MoshVrColors.Surface,
        title = { Text(if (initial == null) "New host" else "Edit host") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it.trim() },
                        label = { Text("Host") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.width(96.dp),
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim() },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = authMethod == AuthMethod.PASSWORD,
                        onClick = { authMethod = AuthMethod.PASSWORD },
                        label = { Text("Password") },
                    )
                    FilterChip(
                        selected = authMethod == AuthMethod.PUBLIC_KEY,
                        onClick = { authMethod = AuthMethod.PUBLIC_KEY },
                        label = { Text("SSH key") },
                    )
                }
                when (authMethod) {
                    AuthMethod.PASSWORD -> OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (initial?.encPassword != null) "Password (unchanged if empty)" else "Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AuthMethod.PUBLIC_KEY -> {
                        OutlinedTextField(
                            value = privateKey,
                            onValueChange = { privateKey = it },
                            label = {
                                Text(
                                    if (initial?.encPrivateKey != null) "Private key (unchanged if empty)"
                                    else "Private key (paste PEM / OpenSSH)",
                                )
                            },
                            minLines = 3,
                            maxLines = 6,
                            visualTransformation = if (showPrivateKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = { showPrivateKey = !showPrivateKey }) {
                            Text(
                                if (showPrivateKey) "Hide private key" else "Show private key",
                                color = MoshVrColors.TextSecondary,
                            )
                        }
                        OutlinedTextField(
                            value = keyPassphrase,
                            onValueChange = { keyPassphrase = it },
                            label = { Text("Key passphrase (optional)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                OutlinedTextField(
                    value = startupCommand,
                    onValueChange = { startupCommand = it },
                    label = { Text("Startup command, e.g. tmux new -As main (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useMosh, onCheckedChange = { useMosh = it })
                    Text(
                        "  Use mosh (can recover from network drops while the app stays running)",
                        color = MoshVrColors.TextSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (host.isBlank() || username.isBlank()) return@TextButton
                    val profile = HostProfile(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 22,
                        username = username.trim(),
                        authMethod = authMethod,
                        encPassword = when {
                            authMethod != AuthMethod.PASSWORD -> null
                            password.isNotEmpty() -> CryptoStore.encrypt(password)
                            else -> initial?.encPassword
                        },
                        encPrivateKey = when {
                            authMethod != AuthMethod.PUBLIC_KEY -> null
                            privateKey.isNotEmpty() -> CryptoStore.encrypt(privateKey)
                            else -> initial?.encPrivateKey
                        },
                        encKeyPassphrase = when {
                            authMethod != AuthMethod.PUBLIC_KEY -> null
                            keyPassphrase.isNotEmpty() -> CryptoStore.encrypt(keyPassphrase)
                            else -> initial?.encKeyPassphrase
                        },
                        startupCommand = startupCommand.trim().ifBlank { null },
                        useMosh = useMosh,
                    )
                    onSave(profile)
                },
            ) { Text("Save", color = MoshVrColors.Green) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MoshVrColors.TextSecondary) }
        },
    )
}
