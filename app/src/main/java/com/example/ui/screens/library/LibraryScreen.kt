package com.example.ui.screens.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.VideoMediaItem
import com.example.data.model.ViewMode
import com.example.ui.components.AddNetworkServerDialog
import com.example.ui.components.AddStreamUrlDialog
import com.example.ui.components.DeleteFileDialog
import com.example.ui.components.LockToVaultDialog
import com.example.ui.components.MoveFileDialog
import com.example.ui.components.PinDialog
import com.example.ui.components.RenameFileDialog
import com.example.ui.components.SortBottomSheet
import com.example.ui.components.VideoDetailsDialog
import com.example.ui.components.VideoMenuAction
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onPlayVideo: (VideoMediaItem, List<VideoMediaItem>) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayedVideos by viewModel.displayedVideos.collectAsStateWithLifecycle()
    val folders by viewModel.folderList.collectAsStateWithLifecycle()
    val vaultVideos by viewModel.vaultVideos.collectAsStateWithLifecycle()
    val networkServers by viewModel.networkServers.collectAsStateWithLifecycle()
    val streamBookmarks by viewModel.streamBookmarks.collectAsStateWithLifecycle()

    // Dialog States
    var showSortSheet by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var isPinSetupMode by remember { mutableStateOf(false) }
    var showAddServerDialog by remember { mutableStateOf(false) }
    var showAddStreamDialog by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }

    // Target Video Actions
    var activeActionVideo by remember { mutableStateOf<VideoMediaItem?>(null) }
    var currentDialogAction by remember { mutableStateOf<VideoMenuAction?>(null) }

    // Runtime Permission Request
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.scanMedia()
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    LaunchedEffect(uiState.messageSnackbar) {
        uiState.messageSnackbar?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessageSnackbar()
        }
    }

    // Intercept Back button so navigating back from folders, FTP/SMB browser, search or tabs does NOT close the app
    BackHandler(
        enabled = uiState.selectedFolder != null ||
                uiState.ftpBrowsingServer != null ||
                searchActive ||
                uiState.activeTab != 0
    ) {
        if (searchActive) {
            searchActive = false
            viewModel.setSearchQuery("")
        } else if (uiState.ftpBrowsingServer != null) {
            val sanitized = uiState.ftpCurrentPath.trim().removeSuffix("/")
            if (sanitized.isNotBlank() && sanitized != "/" && sanitized.contains("/")) {
                val parent = sanitized.substringBeforeLast("/", "").ifBlank { "/" }
                viewModel.navigateFtp(parent)
            } else {
                viewModel.closeFtpBrowser()
            }
        } else if (uiState.selectedFolder != null) {
            viewModel.clearSelectedFolder()
        } else if (uiState.activeTab != 0) {
            viewModel.setActiveTab(0)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search videos, folders…") },
                            singleLine = true,
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                                .testTag("search_text_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Media Player",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (uiState.isLockModeUnlocked) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Vault Unlocked",
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchActive = !searchActive
                            if (!searchActive) viewModel.setSearchQuery("")
                        },
                        modifier = Modifier.testTag("search_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (searchActive) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }

                    IconButton(
                        onClick = {
                            val nextMode = if (uiState.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                            viewModel.setViewMode(nextMode)
                        },
                        modifier = Modifier.testTag("view_mode_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List"
                        )
                    }

                    IconButton(
                        onClick = { showSortSheet = true },
                        modifier = Modifier.testTag("sort_sheet_button")
                    ) {
                        Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort")
                    }

                    IconButton(
                        onClick = { viewModel.scanMedia() },
                        modifier = Modifier.testTag("refresh_scan_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Scan")
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = uiState.activeTab == 0,
                    onClick = { viewModel.setActiveTab(0) },
                    icon = { Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = "All Videos") },
                    label = { Text("Videos") },
                    modifier = Modifier.testTag("nav_all_videos")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.setActiveTab(1) },
                    icon = { Icon(imageVector = Icons.Default.Folder, contentDescription = "Folders") },
                    label = { Text("Folders") },
                    modifier = Modifier.testTag("nav_folders")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 2,
                    onClick = { viewModel.setActiveTab(2) },
                    icon = { Icon(imageVector = Icons.Default.Dns, contentDescription = "Network") },
                    label = { Text("Network") },
                    modifier = Modifier.testTag("nav_network")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 3,
                    onClick = { viewModel.setActiveTab(3) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Mode Kunci",
                            tint = if (uiState.isLockModeUnlocked) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = { Text("Vault") },
                    modifier = Modifier.testTag("nav_vault")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.activeTab) {
                0 -> {
                    AllVideosTab(
                        videos = displayedVideos,
                        viewMode = uiState.viewMode,
                        isScanning = uiState.isScanning,
                        onVideoClick = { video -> onPlayVideo(video, displayedVideos) },
                        onVideoMenuAction = { video, action ->
                            activeActionVideo = video
                            currentDialogAction = action
                            if (action == VideoMenuAction.PLAY) {
                                onPlayVideo(video, displayedVideos)
                            }
                        },
                        onScanClick = { viewModel.scanMedia() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                1 -> {
                    val videosInFolder = if (uiState.selectedFolder != null) {
                        displayedVideos.filter { it.folderPath == uiState.selectedFolder?.path }
                    } else emptyList()

                    FoldersTab(
                        folders = folders,
                        selectedFolder = uiState.selectedFolder,
                        videosInSelectedFolder = videosInFolder,
                        viewMode = uiState.viewMode,
                        isLockUnlocked = uiState.isLockModeUnlocked,
                        onLockClick = {
                            if (uiState.isLockModeUnlocked) {
                                viewModel.lockVault()
                            } else {
                                isPinSetupMode = !uiState.hasPinConfigured
                                showPinDialog = true
                            }
                        },
                        onFolderClick = { folder -> viewModel.selectFolder(folder) },
                        onBackFromFolder = { viewModel.selectFolder(null) },
                        onVideoClick = { video -> onPlayVideo(video, videosInFolder) },
                        onVideoMenuAction = { video, action ->
                            activeActionVideo = video
                            currentDialogAction = action
                            if (action == VideoMenuAction.PLAY) {
                                onPlayVideo(video, videosInFolder)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                2 -> {
                    NetworkTab(
                        servers = networkServers,
                        bookmarks = streamBookmarks,
                        browsingServer = uiState.ftpBrowsingServer,
                        currentFtpPath = uiState.ftpCurrentPath,
                        ftpFiles = uiState.ftpFiles,
                        isFtpLoading = uiState.ftpLoading,
                        ftpErrorMessage = uiState.ftpErrorMessage,
                        isLockUnlocked = uiState.isLockModeUnlocked,
                        onLockClick = {
                            if (uiState.isLockModeUnlocked) {
                                viewModel.lockVault()
                            } else {
                                isPinSetupMode = !uiState.hasPinConfigured
                                showPinDialog = true
                            }
                        },
                        onOpenServer = { server -> viewModel.openFtpServer(server) },
                        onNavigateFtp = { path -> viewModel.navigateFtp(path) },
                        onCloseFtp = { viewModel.closeFtpBrowser() },
                        onPlayFtpFile = { server, file ->
                            val streamItem = viewModel.networkRepository.buildNetworkVideoItem(file, server.name)
                            onPlayVideo(streamItem, listOf(streamItem))
                        },
                        onPlayBookmark = { bookmark ->
                            val streamItem = viewModel.networkRepository.buildUrlVideoItem(bookmark.title, bookmark.url)
                            onPlayVideo(streamItem, listOf(streamItem))
                        },
                        onAddServerClick = { showAddServerDialog = true },
                        onAddBookmarkClick = { showAddStreamDialog = true },
                        onLoadPresetSamples = { viewModel.loadPresetSampleStreams() },
                        onDeleteServer = { id -> viewModel.deleteNetworkServer(id) },
                        onDeleteBookmark = { id -> viewModel.deleteStreamBookmark(id) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                3 -> {
                    VaultTab(
                        isUnlocked = uiState.isLockModeUnlocked,
                        hasPinConfigured = uiState.hasPinConfigured,
                        vaultVideos = vaultVideos,
                        viewMode = uiState.viewMode,
                        onUnlockClick = {
                            isPinSetupMode = false
                            showPinDialog = true
                        },
                        onLockClick = { viewModel.lockVault() },
                        onSetupPinClick = {
                            isPinSetupMode = true
                            showPinDialog = true
                        },
                        onVideoClick = { video -> onPlayVideo(video, vaultVideos) },
                        onVideoMenuAction = { video, action ->
                            activeActionVideo = video
                            currentDialogAction = action
                            if (action == VideoMenuAction.PLAY) {
                                onPlayVideo(video, vaultVideos)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Modal Bottom Sheets & Dialogs
    if (showSortSheet) {
        SortBottomSheet(
            currentSort = uiState.sortOption,
            onSortSelected = { viewModel.setSortOption(it) },
            onDismiss = { showSortSheet = false }
        )
    }

    val securityQuestion by viewModel.securityQuestion.collectAsStateWithLifecycle()

    if (showPinDialog) {
        PinDialog(
            isSettingNewPin = isPinSetupMode,
            hasExistingPin = uiState.hasPinConfigured,
            initialVaultExtension = uiState.vaultExtension,
            savedSecurityQuestion = securityQuestion,
            onVerifyPin = { pin -> viewModel.checkPinMatches(pin) },
            onVerifySecurityAnswer = { answer -> viewModel.verifySecurityAnswer(answer) },
            onPinExtensionAndQuestionSuccess = { pin, ext, question, answer ->
                showPinDialog = false
                viewModel.configurePinAndExtensionWithQuestion(pin, ext, question, answer)
            },
            onPinAndExtensionSuccess = { pin, ext ->
                showPinDialog = false
                if (isPinSetupMode) {
                    viewModel.configurePinAndExtension(pin, ext)
                } else {
                    viewModel.verifyPin(pin)
                }
            },
            onPinSuccess = { pin ->
                showPinDialog = false
                if (isPinSetupMode) {
                    viewModel.configurePin(pin)
                } else {
                    if (pin.isNotBlank()) viewModel.verifyPin(pin)
                }
            },
            onDismiss = { showPinDialog = false }
        )
    }

    if (showAddServerDialog) {
        AddNetworkServerDialog(
            onAddServer = { name, type, host, port, user, pass, path ->
                showAddServerDialog = false
                viewModel.addNetworkServer(name, type, host, port, user, pass, path)
            },
            onDismiss = { showAddServerDialog = false }
        )
    }

    if (showAddStreamDialog) {
        AddStreamUrlDialog(
            onAddStream = { title, url ->
                showAddStreamDialog = false
                viewModel.addStreamBookmark(title, url)
                val streamItem = viewModel.networkRepository.buildUrlVideoItem(title, url)
                onPlayVideo(streamItem, listOf(streamItem))
            },
            onDismiss = { showAddStreamDialog = false }
        )
    }

    // Video Action Dialogs
    activeActionVideo?.let { video ->
        when (currentDialogAction) {
            VideoMenuAction.RENAME -> {
                RenameFileDialog(
                    video = video,
                    onConfirm = { newName ->
                        viewModel.renameVideo(video, newName)
                        activeActionVideo = null
                        currentDialogAction = null
                    },
                    onDismiss = {
                        activeActionVideo = null
                        currentDialogAction = null
                    }
                )
            }
            VideoMenuAction.MOVE -> {
                MoveFileDialog(
                    video = video,
                    onConfirm = { targetDir ->
                        viewModel.moveVideo(video, targetDir)
                        activeActionVideo = null
                        currentDialogAction = null
                    },
                    onDismiss = {
                        activeActionVideo = null
                        currentDialogAction = null
                    }
                )
            }
            VideoMenuAction.DELETE -> {
                DeleteFileDialog(
                    video = video,
                    onConfirm = {
                        viewModel.deleteVideo(video)
                        activeActionVideo = null
                        currentDialogAction = null
                    },
                    onDismiss = {
                        activeActionVideo = null
                        currentDialogAction = null
                    }
                )
            }
            VideoMenuAction.LOCK_VAULT -> {
                LockToVaultDialog(
                    video = video,
                    onConfirm = {
                        viewModel.lockVideoToVault(video)
                        activeActionVideo = null
                        currentDialogAction = null
                    },
                    onDismiss = {
                        activeActionVideo = null
                        currentDialogAction = null
                    }
                )
            }
            VideoMenuAction.UNLOCK_VAULT -> {
                viewModel.unlockVideoFromVault(video)
                activeActionVideo = null
                currentDialogAction = null
            }
            VideoMenuAction.DETAILS -> {
                VideoDetailsDialog(
                    video = video,
                    onDismiss = {
                        activeActionVideo = null
                        currentDialogAction = null
                    }
                )
            }
            else -> Unit
        }
    }
}
