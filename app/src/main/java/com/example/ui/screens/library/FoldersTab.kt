package com.example.ui.screens.library

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.VideoFolder
import com.example.data.model.VideoMediaItem
import com.example.data.model.ViewMode
import com.example.ui.components.FolderCard
import com.example.ui.components.VideoMenuAction

@Composable
fun FoldersTab(
    folders: List<VideoFolder>,
    selectedFolder: VideoFolder?,
    videosInSelectedFolder: List<VideoMediaItem>,
    viewMode: ViewMode,
    isLockUnlocked: Boolean = false,
    onLockClick: () -> Unit = {},
    onFolderClick: (VideoFolder) -> Unit,
    onBackFromFolder: () -> Unit,
    onVideoClick: (VideoMediaItem) -> Unit,
    onVideoMenuAction: (VideoMediaItem, VideoMenuAction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedFolder != null) {
        // Inside selected folder view
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackFromFolder,
                    modifier = Modifier.testTag("folder_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to folders"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedFolder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${videosInSelectedFolder.size} videos in ${selectedFolder.path}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onLockClick,
                    modifier = Modifier.testTag("folder_lock_button")
                ) {
                    Icon(
                        imageVector = if (isLockUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = "Lock Mode",
                        tint = if (isLockUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AllVideosTab(
                videos = videosInSelectedFolder,
                viewMode = viewMode,
                isScanning = false,
                onVideoClick = onVideoClick,
                onVideoMenuAction = onVideoMenuAction,
                onScanClick = {},
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    if (folders.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Video Folders",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Videos will be organized by their directories once scanned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("folders_list"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(folders, key = { it.path }) { folder ->
            FolderCard(
                folder = folder,
                onClick = { onFolderClick(folder) }
            )
        }
    }
}
