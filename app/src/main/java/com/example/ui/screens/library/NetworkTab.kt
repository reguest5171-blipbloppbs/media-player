package com.example.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.NetworkServerEntity
import com.example.data.local.entity.NetworkShortcutEntity
import com.example.data.local.entity.StreamBookmarkEntity
import com.example.data.repository.NetworkFileItem

@Composable
fun NetworkTab(
    servers: List<NetworkServerEntity>,
    bookmarks: List<StreamBookmarkEntity>,
    shortcuts: List<NetworkShortcutEntity> = emptyList(),
    browsingServer: NetworkServerEntity?,
    currentFtpPath: String,
    ftpFiles: List<NetworkFileItem>,
    isFtpLoading: Boolean,
    ftpErrorMessage: String?,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    downloadingFileName: String? = null,
    isLockUnlocked: Boolean = false,
    onLockClick: () -> Unit = {},
    onOpenServer: (NetworkServerEntity) -> Unit,
    onOpenShortcut: (NetworkShortcutEntity) -> Unit = {},
    onNavigateFtp: (String) -> Unit,
    onCloseFtp: () -> Unit,
    onPlayFtpFile: (NetworkServerEntity, NetworkFileItem) -> Unit,
    onPlayBookmark: (StreamBookmarkEntity) -> Unit,
    onAddServerClick: () -> Unit,
    onAddBookmarkClick: () -> Unit,
    onAddShortcutClick: () -> Unit = {},
    onLoadPresetSamples: () -> Unit,
    onDeleteServer: (Long) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteShortcut: (Long) -> Unit = {},
    // Remote file actions
    onDeleteRemoteFile: (NetworkServerEntity, NetworkFileItem) -> Unit = { _, _ -> },
    onRenameRemoteFile: (NetworkServerEntity, NetworkFileItem, String) -> Unit = { _, _, _ -> },
    onMoveRemoteFile: (NetworkServerEntity, NetworkFileItem, String) -> Unit = { _, _, _ -> },
    onCopyRemoteFile: (NetworkServerEntity, NetworkFileItem, String) -> Unit = { _, _, _ -> },
    onDownloadRemoteFile: (NetworkServerEntity, NetworkFileItem) -> Unit = { _, _ -> },
    onLockRemoteFileToVault: (NetworkServerEntity, NetworkFileItem) -> Unit = { _, _ -> },
    onPinRemoteShortcut: (NetworkServerEntity, NetworkFileItem) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    // Dialog states for remote file operations
    var selectedFileForAction by remember { mutableStateOf<NetworkFileItem?>(null) }
    var activeActionDialog by remember { mutableStateOf<String?>(null) } // "RENAME", "MOVE", "COPY", "DELETE"
    var inputDialogText by remember { mutableStateOf("") }

    if (browsingServer != null) {
        // Network File Explorer View (FTP & SMB)
        Column(modifier = modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val sanitized = currentFtpPath.trim().removeSuffix("/")
                        if (sanitized.isBlank() || sanitized == "/") {
                            onCloseFtp()
                        } else {
                            val parent = sanitized.substringBeforeLast("/", "").ifBlank { "/" }
                            onNavigateFtp(parent)
                        }
                    },
                    modifier = Modifier.testTag("ftp_back_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${browsingServer.name} (${browsingServer.type})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentFtpPath.ifBlank { "/" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onLockClick,
                    modifier = Modifier.testTag("smb_lock_button")
                ) {
                    Icon(
                        imageVector = if (isLockUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = "Lock Mode",
                        tint = if (isLockUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onCloseFtp) {
                    Icon(imageVector = Icons.Default.Storage, contentDescription = "Disconnect")
                }
            }

            // Download progress banner if downloading
            if (isDownloading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mengunduh ${downloadingFileName ?: "file"}...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (downloadProgress <= 0f) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (isFtpLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Menghubungkan ke ${browsingServer.type} (${browsingServer.host})...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (ftpErrorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Gagal Memuat ${browsingServer.type}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = ftpErrorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = onCloseFtp) {
                                    Text("Tutup")
                                }
                                Button(onClick = { onNavigateFtp(currentFtpPath) }) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Coba Lagi")
                                }
                            }
                        }
                    }
                }
            } else if (ftpFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Folder Kosong",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tidak ditemukan file di direktori ini.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ftpFiles, key = { it.path }) { file ->
                        var menuExpanded by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (file.isDirectory) {
                                        onNavigateFtp(file.path)
                                    } else {
                                        onPlayFtpFile(browsingServer, file)
                                    }
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (file.isDirectory)
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (file.isDirectory) MaterialTheme.colorScheme.primaryContainer
                                            else Color(0xFF1565C0)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!file.isDirectory) {
                                        val mb = file.sizeBytes / (1024.0 * 1024.0)
                                        Text(
                                            text = String.format("%.1f MB", mb),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = "Direktori Folder",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // 3-Dots Action Menu for Remote File Management
                                Box {
                                    IconButton(
                                        onClick = { menuExpanded = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Menu Aksi File",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        if (!file.isDirectory) {
                                            DropdownMenuItem(
                                                text = { Text("Unduh ke Lokal") },
                                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    onDownloadRemoteFile(browsingServer, file)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Kunci ke Brankas") },
                                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    onLockRemoteFileToVault(browsingServer, file)
                                                }
                                            )
                                        }

                                        DropdownMenuItem(
                                            text = { Text("Buat Pintasan") },
                                            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                onPinRemoteShortcut(browsingServer, file)
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("Ubah Nama") },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                selectedFileForAction = file
                                                inputDialogText = file.name
                                                activeActionDialog = "RENAME"
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("Pindahkan") },
                                            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                selectedFileForAction = file
                                                inputDialogText = currentFtpPath
                                                activeActionDialog = "MOVE"
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("Salin") },
                                            leadingIcon = { Icon(Icons.Default.FileCopy, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                selectedFileForAction = file
                                                inputDialogText = currentFtpPath
                                                activeActionDialog = "COPY"
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("Hapus dari Server", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                menuExpanded = false
                                                selectedFileForAction = file
                                                activeActionDialog = "DELETE"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Dialogs for Remote File Operations
        if (activeActionDialog != null && selectedFileForAction != null) {
            val targetFile = selectedFileForAction!!
            when (activeActionDialog) {
                "RENAME" -> {
                    AlertDialog(
                        onDismissRequest = { activeActionDialog = null },
                        title = { Text("Ubah Nama File / Folder") },
                        text = {
                            Column {
                                Text("Masukkan nama baru untuk '${targetFile.name}':")
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = inputDialogText,
                                    onValueChange = { inputDialogText = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                activeActionDialog = null
                                onRenameRemoteFile(browsingServer, targetFile, inputDialogText.trim())
                            }) {
                                Text("Simpan")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeActionDialog = null }) {
                                Text("Batal")
                            }
                        }
                    )
                }

                "MOVE" -> {
                    AlertDialog(
                        onDismissRequest = { activeActionDialog = null },
                        title = { Text("Pindahkan ke Direktori Lain") },
                        text = {
                            Column {
                                Text("Tujuan folder baru di server:")
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = inputDialogText,
                                    onValueChange = { inputDialogText = it },
                                    singleLine = true,
                                    label = { Text("Target Path") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                activeActionDialog = null
                                onMoveRemoteFile(browsingServer, targetFile, inputDialogText.trim())
                            }) {
                                Text("Pindahkan")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeActionDialog = null }) {
                                Text("Batal")
                            }
                        }
                    )
                }

                "COPY" -> {
                    AlertDialog(
                        onDismissRequest = { activeActionDialog = null },
                        title = { Text("Salin ke Direktori Lain") },
                        text = {
                            Column {
                                Text("Tujuan folder salinan di server:")
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = inputDialogText,
                                    onValueChange = { inputDialogText = it },
                                    singleLine = true,
                                    label = { Text("Target Path") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                activeActionDialog = null
                                onCopyRemoteFile(browsingServer, targetFile, inputDialogText.trim())
                            }) {
                                Text("Salin")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeActionDialog = null }) {
                                Text("Batal")
                            }
                        }
                    )
                }

                "DELETE" -> {
                    AlertDialog(
                        onDismissRequest = { activeActionDialog = null },
                        title = { Text("Hapus dari Server?") },
                        text = { Text("Apakah Anda yakin ingin menghapus '${targetFile.name}' secara permanen dari server?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    activeActionDialog = null
                                    onDeleteRemoteFile(browsingServer, targetFile)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Hapus")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeActionDialog = null }) {
                                Text("Batal")
                            }
                        }
                    )
                }
            }
        }

        return
    }

    // Default Network Hub View
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("network_tab_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Action Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAddServerClick,
                    modifier = Modifier.weight(1f).testTag("add_server_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Dns, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Server")
                }

                OutlinedButton(
                    onClick = onAddBookmarkClick,
                    modifier = Modifier.weight(1f).testTag("add_url_stream_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Link, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("URL Stream")
                }
            }
        }

        // 1. Shortcuts Section (Pintasan Jalur Server)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pintasan Jaringan (${shortcuts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onAddShortcutClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah", fontSize = 13.sp)
                }
            }
        }

        if (shortcuts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "Belum ada shortcut. Anda dapat menyematkan direktori atau file server favorit langsung di sini untuk akses instan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(shortcuts, key = { it.id }) { shortcut ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenShortcut(shortcut) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (shortcut.isDirectory) Icons.Default.Folder else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = shortcut.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = shortcut.serverType,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${shortcut.serverName} • ${shortcut.targetPath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { onDeleteShortcut(shortcut.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Hapus Shortcut",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        // 2. Servers Section (FTP & Samba)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Server Jaringan (FTP / Samba SMB)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (servers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "Belum ada server FTP / Samba. Ketuk 'Tambah Server' untuk streaming langsung lewat Wi-Fi / LAN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(servers, key = { it.id }) { server ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenServer(server) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${server.type} • ${server.host}:${server.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { onDeleteServer(server.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Server",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        // 3. Bookmarks / Preset Sample URL Streams Section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sample Uji Coba & URL Stream",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                ElevatedButton(
                    onClick = onLoadPresetSamples,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Sample", fontSize = 12.sp)
                }
            }
        }

        if (bookmarks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Belum ada URL video. Tekan tombol di bawah untuk memuat sample uji coba (HLS .m3u8, MP4 1080p, dll).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onLoadPresetSamples) {
                            Text("⚡ Muat Sample Uji Coba URL")
                        }
                    }
                }
            }
        } else {
            items(bookmarks, key = { it.id }) { bm ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayBookmark(bm) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bm.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = bm.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Bookmark",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}
