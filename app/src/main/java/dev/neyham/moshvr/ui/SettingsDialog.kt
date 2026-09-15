package dev.neyham.moshvr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.neyham.moshvr.data.AppSettings
import dev.neyham.moshvr.data.CryptoStore
import dev.neyham.moshvr.data.KnownHostEntry
import dev.neyham.moshvr.voice.SttPolicy
import dev.neyham.moshvr.voice.SpeechProvider
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsDialog(
    current: AppSettings,
    knownHosts: List<KnownHostEntry>,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit,
    onClearApiKey: () -> Unit,
    onForgetHost: (KnownHostEntry) -> Unit,
    onClearKnownHosts: () -> Unit,
    onAbout: () -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    var appId by remember { mutableStateOf(current.speechAppId) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(current.transcriptionModel) }
    var baseUrl by remember { mutableStateOf(current.apiBaseUrl) }
    var keepScreenOn by remember { mutableStateOf(current.keepScreenOn) }
    var sttConsent by remember { mutableStateOf(current.hasSpeechConsent) }
    var adultConfirmed by remember { mutableStateOf(current.hasSpeechConsent) }

    WindowSafeAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MoshVrColors.Surface,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Speech provider", color = MoshVrColors.TextPrimary)
                SpeechProvider.entries.forEach { choice ->
                    TextButton(onClick = {
                        if (choice != draft.speechProvider) {
                            draft = draft.copy(
                                encApiKey = if (apiKey.isNotBlank()) CryptoStore.encrypt(apiKey.trim()) else draft.encApiKey,
                                transcriptionModel = model, apiBaseUrl = baseUrl, speechAppId = appId,
                            ).selectSpeechProvider(choice)
                            apiKey = ""
                            model = draft.transcriptionModel
                            baseUrl = draft.apiBaseUrl
                            appId = draft.speechAppId
                            sttConsent = false
                            adultConfirmed = false
                        }
                    }) { Text((if (draft.speechProvider == choice) "✓ " else "") + choice.label) }
                }
                Text(
                    SttPolicy.disclosure(baseUrl, model),
                    color = MoshVrColors.TextSecondary,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; sttConsent = false },
                    label = { Text(if (draft.hasApiKey) "API key (unchanged if empty)" else "API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    apiKey = ""
                    draft = draft.copy(encApiKey = null, speechProfiles = draft.speechProfiles - draft.speechProvider)
                    sttConsent = false
                }) {
                    Text("Clear selected key (apply with Save)", color = MoshVrColors.Error)
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; sttConsent = false },
                    label = { Text(if (draft.speechProvider == SpeechProvider.VOLCENGINE) "Speech resource ID" else "Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        if (SttPolicy.normalizeBaseUrl(it) !=
                            SttPolicy.normalizeBaseUrl(draft.consentedApiBaseUrl.orEmpty())
                        ) {
                            sttConsent = false
                        }
                    },
                    label = { Text("API base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (draft.speechProvider == SpeechProvider.QWEN) {
                    Text("Use the endpoint for your API key region. The default is Beijing.", color = MoshVrColors.TextSecondary)
                }
                if (draft.speechProvider == SpeechProvider.VOLCENGINE) {
                    Text("Use a speech APP Key. For legacy speech accounts, enter App ID below and use Access Token as the key.", color = MoshVrColors.TextSecondary)
                    OutlinedTextField(value = appId, onValueChange = { appId = it; sttConsent = false },
                        label = { Text("Legacy App ID (leave empty for new keys)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = adultConfirmed, onCheckedChange = {
                        adultConfirmed = it
                        if (!it) sttConsent = false
                    })
                    Text("  I am 18 or older and eligible to use this provider", color = MoshVrColors.TextSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = sttConsent, enabled = adultConfirmed && SttPolicy.isHttps(baseUrl),
                        onCheckedChange = { sttConsent = it })
                    Text("  I consent to cloud speech uploads", color = MoshVrColors.TextSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = keepScreenOn, onCheckedChange = { keepScreenOn = it })
                    Text("  Keep screen on while the terminal is open", color = MoshVrColors.TextSecondary)
                }
                Text("Known hosts", color = MoshVrColors.TextPrimary)
                if (knownHosts.isEmpty()) {
                    Text("None stored.", color = MoshVrColors.TextSecondary)
                } else {
                    knownHosts.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${entry.host}:${entry.port}  ${entry.algorithm}\n${entry.fingerprint}",
                                color = MoshVrColors.TextSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onForgetHost(entry) }) {
                                Text("Forget", color = MoshVrColors.Error)
                            }
                        }
                    }
                    TextButton(onClick = onClearKnownHosts) {
                        Text("Clear all known hosts", color = MoshVrColors.Error)
                    }
                }
                TextButton(onClick = onAbout) {
                    Text("About / Legal", color = MoshVrColors.Green)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val url = baseUrl.trim().ifBlank { draft.speechProvider.defaultUrl }
                val https = SttPolicy.isHttps(url)
                val edited = draft.copy(
                    encApiKey = if (apiKey.isNotBlank()) CryptoStore.encrypt(apiKey.trim()) else draft.encApiKey,
                    transcriptionModel = model.trim().ifBlank { draft.speechProvider.defaultModel },
                    apiBaseUrl = if (https) url else draft.apiBaseUrl,
                    keepScreenOn = keepScreenOn,
                    speechAppId = appId.trim(),
                )
                onSave(
                    if (!https || !sttConsent || !adultConfirmed) edited.withoutSpeechConsent()
                    else if (edited.hasSpeechConsent) edited
                    else edited.withSpeechConsent(adultConfirmed),
                )
            }) { Text("Save", color = MoshVrColors.Green) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MoshVrColors.TextSecondary) }
        },
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notices = remember {
        context.assets.list("legal").orEmpty().sorted().joinToString("\n\n") { name ->
            val body = runCatching {
                context.assets.open("legal/$name").bufferedReader().use { it.readText() }
            }.getOrDefault("Unable to load notice")
            "---- $name ----\n$body"
        }
    }
    WindowSafeAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MoshVrColors.Surface,
        title = { Text("${Legal.APP_NAME}  ·  ${Legal.LICENSE}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(Legal.SOURCE_STATUS, color = MoshVrColors.TextSecondary)
                Text(Legal.THIRD_PARTY, color = MoshVrColors.TextSecondary)
                Text("Privacy: ${Legal.PRIVACY_URL}", color = MoshVrColors.TextSecondary)
                Text("Contact: ${Legal.CONTACT_URL}", color = MoshVrColors.TextSecondary)
                Text(notices, color = MoshVrColors.TextSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = MoshVrColors.Green) }
        },
    )
}

@Composable
fun HostKeyConfirmDialog(
    request: dev.neyham.moshvr.session.HostKeyGate.Request,
    onRespond: (Boolean) -> Unit,
) {
    WindowSafeAlertDialog(
        onDismissRequest = { onRespond(false) },
        containerColor = MoshVrColors.Surface,
        title = {
            Text(if (request.isChange) "Host key changed" else "New SSH host key")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${request.host}:${request.port}", color = MoshVrColors.TextPrimary)
                Text("Algorithm: ${request.algorithm}", color = MoshVrColors.TextSecondary)
                Text("Fingerprint:\n${request.fingerprint}", color = MoshVrColors.TextSecondary)
                if (request.isChange) {
                    Text(
                        "Expected:\n${request.expectedFingerprint}\nThis can mean a server reinstall or a man-in-the-middle.",
                        color = MoshVrColors.Error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRespond(true) }) {
                Text(if (request.isChange) "Trust new key" else "Trust this host", color = MoshVrColors.Green)
            }
        },
        dismissButton = {
            TextButton(onClick = { onRespond(false) }) {
                Text("Reject", color = MoshVrColors.Error)
            }
        },
    )
}
