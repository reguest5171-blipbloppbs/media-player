package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AddNetworkServerDialog(
    onAddServer: (name: String, type: String, host: String, port: Int, user: String, pass: String, path: String) -> Unit,
    onDismiss: () -> Unit
) {
    var serverType by remember { mutableStateOf("FTP") } // "FTP" or "SMB"
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("21") }
    var isAnonymous by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remotePath by remember { mutableStateOf("/") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Network Server", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Protocol selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = serverType == "FTP",
                        onClick = {
                            serverType = "FTP"
                            port = "21"
                        },
                        label = { Text("FTP Server") },
                        modifier = Modifier.testTag("chip_ftp")
                    )
                    FilterChip(
                        selected = serverType == "SMB",
                        onClick = {
                            serverType = "SMB"
                            port = "445"
                        },
                        label = { Text("Samba / SMB") },
                        modifier = Modifier.testTag("chip_smb")
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Nickname (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("server_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host / IP Address") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.weight(2f).testTag("server_host_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("server_port_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Anonymous / Guest toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isAnonymous,
                        onClick = {
                            isAnonymous = true
                            username = ""
                            password = ""
                        },
                        label = { Text("Guest / Anonymous Mode") },
                        modifier = Modifier.testTag("chip_anonymous_mode")
                    )
                    FilterChip(
                        selected = !isAnonymous,
                        onClick = { isAnonymous = false },
                        label = { Text("Login Auth") },
                        modifier = Modifier.testTag("chip_auth_mode")
                    )
                }

                if (!isAnonymous) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("server_user_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("server_pass_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = remotePath,
                    onValueChange = { remotePath = it },
                    label = { Text("Directory / Share Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("server_path_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (host.isNotBlank()) {
                        val p = port.toIntOrNull() ?: if (serverType == "FTP") 21 else 445
                        val finalUser = if (isAnonymous) "" else username.trim()
                        val finalPass = if (isAnonymous) "" else password.trim()
                        onAddServer(name.trim(), serverType, host.trim(), p, finalUser, finalPass, remotePath.trim())
                    }
                },
                enabled = host.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_server")
            ) {
                Text("Connect & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddStreamUrlDialog(
    onAddStream: (title: String, url: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Network Stream URL", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Supports HTTP/HTTPS, HLS (.m3u8), MKV, MP4, RTSP, and direct stream links.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Stream Title (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("stream_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Stream URL") },
                    placeholder = { Text("https://example.com/live/stream.m3u8") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("stream_url_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        onAddStream(title.trim(), url.trim())
                    }
                },
                enabled = url.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_stream")
            ) {
                Text("Add & Play")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
