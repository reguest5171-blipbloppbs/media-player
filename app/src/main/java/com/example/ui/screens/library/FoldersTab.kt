package com.example.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FolderBreadcrumb
import com.example.data.model.FolderHistoryItem
import com.example.data.model.FolderTreeNode
import com.example.data.model.LocalDisplayMode
import com.example.data.model.VideoFolder
import com.example.data.model.VideoMediaItem
import com.example.data.model.ViewMode
import com.example.ui.components.FolderCard
import com.example.ui.components.FolderTreeNodeCard
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoListItem
import com.example.ui.components.VideoMenuAction

@Composable
fun FoldersTab(
    folders: List<VideoFolder>,
    selectedFolder: VideoFolder?,
    videosInSelectedFolder: List<VideoMediaItem>,
    viewMode: ViewMode,
    localDisplayMode: LocalDisplayMode = LocalDisplayMode.FOLDERS,
    folderTreeCurrentPath: String? = null,
    folderTreeNodes: List<FolderTreeNode> = emptyList(),
    folderTreeDirectVideos: List<VideoMediaItem> = emptyList(),
    breadcrumbs: List<FolderBreadcrumb> = emptyList(),
    folderPlaybackHistory: List<FolderHistoryItem> = emptyList(),
    folderFilterMode: String = "ALL", // "ALL" or "PLAYED_HISTORY"
    showThumbnails: Boolean = true,
    showDuration: Boolean = true,
    showSize: Boolean = true,
    showResolution: Boolean = true,
    isLockUnlocked: Boolean = false,
    onLockClick: () -> Unit = {},
    onFolderClick: (VideoFolder) -> Unit,
    onFolderNodeClick: (FolderTreeNode) -> Unit = {},
    onBreadcrumbClick: (String) -> Unit = {},
    onBackFromFolder: () -> Unit,
    onFolderTreeBack: () -> Unit = {},
    onFolderFilterModeChange: (String) -> Unit = {},
    onVideoClick: (VideoMediaItem) -> Unit,
    onVideoMenuAction: (VideoMediaItem, VideoMenuAction) -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Inside a single selected folder view (drilldown)
    if (selectedFolder != null) {
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
                        text = "${videosInSelectedFolder.size} berkas video di ${selectedFolder.path}",
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
                showThumbnails = showThumbnails,
                showDuration = showDuration,
                showSize = showSize,
                showResolution = showResolution,
                onVideoClick = onVideoClick,
                onVideoMenuAction = onVideoMenuAction,
                onScanClick = {},
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    // 2. Hierarchical Tree Navigation Mode (Mode Folder MX Player)
    if (localDisplayMode == LocalDisplayMode.FOLDER_FULL_PATH) {
        val isAtRoot = folderTreeCurrentPath.isNullOrBlank() || folderTreeCurrentPath == "ROOT"

        Column(modifier = modifier.fillMaxSize()) {
            // Top Navigation header if deeper than Root
            if (!isAtRoot) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onFolderTreeBack,
                            modifier = Modifier.testTag("tree_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali ke folder atas"
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            val currentName = breadcrumbs.lastOrNull()?.label ?: "Folder"
                            Text(
                                text = currentName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${folderTreeNodes.size} folder • ${folderTreeDirectVideos.size} berkas langsung",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onLockClick) {
                            Icon(
                                imageVector = if (isLockUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = "Lock Mode",
                                tint = if (isLockUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Tree Content (Subfolders and Direct Videos)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("folder_tree_list"),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (folderTreeNodes.isEmpty() && folderTreeDirectVideos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Tidak ada folder atau video di sini",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Subfolders in this directory
                if (folderTreeNodes.isNotEmpty()) {
                    item {
                        Text(
                            text = if (isAtRoot) "Penyimpanan Utama" else "Folder Sub-Direktori",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    items(folderTreeNodes, key = { it.path }) { node ->
                        FolderTreeNodeCard(
                            node = node,
                            onClick = { onFolderNodeClick(node) }
                        )
                    }
                }

                // Direct Videos in this directory
                if (folderTreeDirectVideos.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Berkas Video (${folderTreeDirectVideos.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    items(folderTreeDirectVideos, key = { it.id }) { video ->
                        if (viewMode == ViewMode.GRID) {
                            VideoCard(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onMenuAction = { action -> onVideoMenuAction(video, action) },
                                showThumbnails = showThumbnails,
                                showDuration = showDuration,
                                showSize = showSize,
                                showResolution = showResolution
                            )
                        } else {
                            val isCompact = (viewMode == ViewMode.COMPACT)
                            VideoListItem(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onMenuAction = { action -> onVideoMenuAction(video, action) },
                                showThumbnails = showThumbnails,
                                showDuration = showDuration,
                                showSize = showSize,
                                showResolution = showResolution,
                                isCompact = isCompact
                            )
                        }
                    }
                }
            }

            // Bottom Interactive Breadcrumbs Bar (like in MX Player)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    breadcrumbs.forEachIndexed { index, crumb ->
                        val isLast = index == breadcrumbs.size - 1
                        Text(
                            text = crumb.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { onBreadcrumbClick(crumb.path) }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        if (!isLast) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        return
    }

    // 3. Flat Folders View (with Play History filter chips)
    val displayFolders = if (folderFilterMode == "PLAYED_HISTORY") {
        folders.filter { it.hasPlayHistory }
    } else {
        folders
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Quick Filter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = folderFilterMode == "ALL",
                onClick = { onFolderFilterModeChange("ALL") },
                label = { Text("Semua Folder (${folders.size})") },
                leadingIcon = if (folderFilterMode == "ALL") {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )

            val playedFolderCount = folders.count { it.hasPlayHistory }
            FilterChip(
                selected = folderFilterMode == "PLAYED_HISTORY",
                onClick = { onFolderFilterModeChange("PLAYED_HISTORY") },
                label = { Text("Riwayat Pemutaran ($playedFolderCount)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }

        if (displayFolders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (folderFilterMode == "PLAYED_HISTORY") Icons.Default.History else Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (folderFilterMode == "PLAYED_HISTORY") "Belum Ada Riwayat Folder" else "Tidak Ada Folder Video",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (folderFilterMode == "PLAYED_HISTORY")
                            "Putar video untuk mulai mencatat riwayat pemutaran berdasarkan folder asal."
                        else "Video akan dikelompokkan berdasarkan direktori setelah dipindai.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("folders_list"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayFolders, key = { it.path }) { folder ->
                FolderCard(
                    folder = folder,
                    showFullPath = false,
                    onClick = { onFolderClick(folder) }
                )
            }
        }
    }
}
