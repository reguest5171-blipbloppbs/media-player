package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.NetworkServerEntity

@Composable
fun AddNetworkShortcutDialog(
    servers: List<NetworkServerEntity>,
    onDismiss: () -> Unit,
    onAddShortcut: (title: String, serverId: Long?, serverName: String, serverType: String, targetPath: String, isDirectory: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedServer by remember { mutableStateOf(servers.firstOrNull()) }
    var targetPath by remember { mutableStateOf(selectedServer?.initialPath ?: "/") }
    var isDirectory by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Pintasan Jaringan") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nama Pintasan (opsional)") },
                    placeholder = { Text("Contoh: Film Favorit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (servers.isNotEmpty()) {
                    Text(
                        text = "Pilih Server Asal:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        servers.forEach { s ->
                            FilterChip(
                                selected = selectedServer?.id == s.id,
                                onClick = {
                                    selectedServer = s
                                    if (targetPath.isBlank() || targetPath == "/") {
                                        targetPath = s.initialPath
                                    }
                                },
                                label = { Text("${s.name} (${s.type})") },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = targetPath,
                    onValueChange = { targetPath = it },
                    label = { Text("Jalur Path Direktori / File") },
                    placeholder = { Text("/Movies/Anime/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = isDirectory,
                        onClick = { isDirectory = true },
                        label = { Text("📁 Direktori Folder") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = !isDirectory,
                        onClick = { isDirectory = false },
                        label = { Text("🎬 File Video") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = selectedServer
                    val sName = s?.name ?: "Remote Server"
                    val sType = s?.type ?: "SMB"
                    val sId = s?.id
                    onAddShortcut(
                        title.ifBlank { targetPath.substringAfterLast("/").ifBlank { "Shortcut" } },
                        sId,
                        sName,
                        sType,
                        targetPath.ifBlank { "/" },
                        isDirectory
                    )
                    onDismiss()
                }
            ) {
                Text("Simpan Pintasan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
