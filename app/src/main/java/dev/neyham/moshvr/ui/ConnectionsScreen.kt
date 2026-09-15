package dev.neyham.moshvr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neyham.moshvr.data.HostProfile

@Composable
fun ConnectionsScreen(
    profiles: List<HostProfile>,
    onConnect: (HostProfile) -> Unit,
    onSave: (HostProfile) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<HostProfile?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (profiles.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    HostOnboarding.HEADLINE,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MoshVrColors.Green,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.padding(8.dp))
                Text(
                    HostOnboarding.LINE1,
                    color = MoshVrColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    HostOnboarding.LINE2,
                    color = MoshVrColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.padding(8.dp))
                Text(
                    HostOnboarding.NETWORK,
                    color = MoshVrColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Text(
                    HostOnboarding.RETRY,
                    color = MoshVrColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "${HostOnboarding.NETWORK} ${HostOnboarding.RETRY}",
                        color = MoshVrColors.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
                items(profiles.sortedBy { it.displayName.lowercase() }, key = { it.id }) { profile ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MoshVrColors.Surface),
                        onClick = { onConnect(profile) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    profile.displayName,
                                    color = MoshVrColors.TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                )
                                Text(
                                    buildString {
                                        append("${profile.username}@${profile.host}:${profile.port}")
                                        append("  ·  ${profile.authMethod.name.lowercase().replace('_', ' ')}")
                                        if (profile.useMosh) append("  ·  mosh")
                                    },
                                    color = MoshVrColors.TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                )
                            }
                            IconButton(onClick = {
                                editing = profile
                                showEditor = true
                            }) {
                                Icon(Icons.Default.Edit, "Edit", tint = MoshVrColors.TextSecondary)
                            }
                            IconButton(onClick = { onDelete(profile.id) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MoshVrColors.TextSecondary)
                            }
                            TextButton(onClick = { onConnect(profile) }) {
                                Text(
                                    "CONNECT ❯",
                                    color = MoshVrColors.Green,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                editing = null
                showEditor = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MoshVrColors.Green,
            contentColor = androidx.compose.ui.graphics.Color.Black,
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New host")
        }
    }

    if (showEditor) {
        ProfileEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = {
                onSave(it)
                showEditor = false
            },
        )
    }
}
