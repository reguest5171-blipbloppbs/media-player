package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MediaPlayerDatabase
import com.example.data.local.UserPreferencesManager
import com.example.data.local.entity.NetworkServerEntity
import com.example.data.local.entity.PlayHistoryEntity
import com.example.data.local.entity.StreamBookmarkEntity
import com.example.data.model.LocalDisplayMode
import com.example.data.model.SortOption
import com.example.data.model.VideoFolder
import com.example.data.model.VideoMediaItem
import com.example.data.model.ViewMode
import com.example.data.repository.MediaRepository
import com.example.data.repository.NetworkFileItem
import com.example.data.repository.NetworkMediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.data.local.entity.NetworkShortcutEntity
import com.example.data.model.FolderBreadcrumb
import com.example.data.model.FolderHistoryItem
import com.example.data.model.FolderTreeNode
import java.io.File
import android.os.Environment

data class MainUiState(
    val isScanning: Boolean = false,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_DESC,
    val isSortAscending: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID,
    val localDisplayMode: LocalDisplayMode = LocalDisplayMode.FOLDERS,
    val showThumbnails: Boolean = true,
    val showDuration: Boolean = true,
    val showSize: Boolean = true,
    val showResolution: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val isLockModeUnlocked: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val vaultExtension: String = "1ca",
    val activeTab: Int = 0, // 0: Lokal, 1: Network, 2: Vault
    val selectedFolder: VideoFolder? = null,
    val folderTreeCurrentPath: String? = null,
    val folderFilterMode: String = "ALL", // "ALL" or "PLAYED_HISTORY"
    val ftpBrowsingServer: NetworkServerEntity? = null,
    val ftpCurrentPath: String = "/",
    val ftpFiles: List<NetworkFileItem> = emptyList(),
    val ftpLoading: Boolean = false,
    val ftpErrorMessage: String? = null,
    val isDownloadingRemoteFile: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadingFileName: String? = null,
    val messageSnackbar: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MediaPlayerDatabase.getDatabase(application)
    val mediaRepository = MediaRepository(application, db.mediaPlayerDao())
    val networkRepository = NetworkMediaRepository(db.mediaPlayerDao())
    val preferencesManager = UserPreferencesManager(application)

    private val _rawVideos = MutableStateFlow<List<VideoMediaItem>>(emptyList())
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val playHistory: StateFlow<List<PlayHistoryEntity>> = mediaRepository.playHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val networkServers: StateFlow<List<NetworkServerEntity>> = networkRepository.serversFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streamBookmarks: StateFlow<List<StreamBookmarkEntity>> = networkRepository.bookmarksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val networkShortcuts: StateFlow<List<NetworkShortcutEntity>> = networkRepository.shortcutsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folderPlaybackHistory: StateFlow<List<FolderHistoryItem>> = combine(
        _rawVideos,
        playHistory
    ) { videos, history ->
        mediaRepository.getFolderPlaybackHistory(videos, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folderTreeBreadcrumbs: StateFlow<List<FolderBreadcrumb>> = _uiState
        .map { it.folderTreeCurrentPath }
        .distinctUntilChanged()
        .map { path -> mediaRepository.buildBreadcrumbs(path) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(FolderBreadcrumb("Media", "ROOT")))

    val folderTreeData: StateFlow<Pair<List<FolderTreeNode>, List<VideoMediaItem>>> = combine(
        _rawVideos,
        _uiState.map { it.folderTreeCurrentPath }.distinctUntilChanged(),
        playHistory
    ) { videos, currentPath, history ->
        val visibleVideos = videos.filter {
            !it.isEncrypted1ca || _uiState.value.isLockModeUnlocked
        }
        mediaRepository.buildFolderTree(visibleVideos, currentPath, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(emptyList(), emptyList()))

    private data class VideoFilterParams(
        val isLockModeUnlocked: Boolean,
        val showHiddenFiles: Boolean,
        val selectedFolderPath: String?,
        val searchQuery: String,
        val sortOption: SortOption,
        val isSortAscending: Boolean
    )

    private val _filterParams = _uiState.map { state ->
        VideoFilterParams(
            isLockModeUnlocked = state.isLockModeUnlocked,
            showHiddenFiles = state.showHiddenFiles,
            selectedFolderPath = state.selectedFolder?.path,
            searchQuery = state.searchQuery,
            sortOption = state.sortOption,
            isSortAscending = state.isSortAscending
        )
    }.distinctUntilChanged()

    // Filtered & Sorted Videos (Recomputes ONLY when filter/sort params change)
    val displayedVideos: StateFlow<List<VideoMediaItem>> = combine(
        _rawVideos,
        _filterParams
    ) { videos, params ->
        var list = videos.filter { video ->
            // In normal mode, hide .1ca files unless Lock Mode is unlocked
            if (video.isEncrypted1ca && !params.isLockModeUnlocked) {
                false
            } else {
                true
            }
        }

        // Hidden files filter
        if (!params.showHiddenFiles) {
            list = list.filter { !it.displayName.startsWith(".") && !it.folderName.startsWith(".") }
        }

        // Folder filter if active
        if (params.selectedFolderPath != null) {
            list = list.filter { it.folderPath == params.selectedFolderPath }
        }

        // Search filter
        if (params.searchQuery.isNotBlank()) {
            val query = params.searchQuery.trim().lowercase()
            list = list.filter {
                it.displayName.lowercase().contains(query) ||
                it.folderName.lowercase().contains(query) ||
                it.path.lowercase().contains(query)
            }
        }

        // Sorting (respects isSortAscending or explicit direction)
        val asc = params.isSortAscending
        when (params.sortOption) {
            SortOption.NAME -> if (asc) list.sortedBy { it.displayName.lowercase() } else list.sortedByDescending { it.displayName.lowercase() }
            SortOption.DATE -> if (asc) list.sortedBy { it.dateModified } else list.sortedByDescending { it.dateModified }
            SortOption.SIZE -> if (asc) list.sortedBy { it.sizeBytes } else list.sortedByDescending { it.sizeBytes }
            SortOption.DURATION -> if (asc) list.sortedBy { it.durationMs } else list.sortedByDescending { it.durationMs }
            SortOption.RESOLUTION -> if (asc) list.sortedBy { it.width * it.height } else list.sortedByDescending { it.width * it.height }
            SortOption.DATE_DESC -> if (!asc) list.sortedByDescending { it.dateModified } else list.sortedBy { it.dateModified }
            SortOption.DATE_ASC -> if (!asc) list.sortedBy { it.dateModified } else list.sortedByDescending { it.dateModified }
            SortOption.NAME_ASC -> if (!asc) list.sortedBy { it.displayName.lowercase() } else list.sortedByDescending { it.displayName.lowercase() }
            SortOption.NAME_DESC -> if (!asc) list.sortedByDescending { it.displayName.lowercase() } else list.sortedBy { it.displayName.lowercase() }
            SortOption.SIZE_DESC -> if (!asc) list.sortedByDescending { it.sizeBytes } else list.sortedBy { it.sizeBytes }
            SortOption.SIZE_ASC -> if (!asc) list.sortedBy { it.sizeBytes } else list.sortedByDescending { it.sizeBytes }
            SortOption.DURATION_DESC -> if (!asc) list.sortedByDescending { it.durationMs } else list.sortedBy { it.durationMs }
            SortOption.DURATION_ASC -> if (!asc) list.sortedBy { it.durationMs } else list.sortedByDescending { it.durationMs }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folderList: StateFlow<List<VideoFolder>> = combine(
        _rawVideos,
        _uiState.map { it.isLockModeUnlocked }.distinctUntilChanged()
    ) { videos, isUnlocked ->
        val visibleVideos = videos.filter {
            !it.isEncrypted1ca || isUnlocked
        }
        mediaRepository.groupVideosIntoFolders(visibleVideos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultVideos: StateFlow<List<VideoMediaItem>> = combine(
        _rawVideos,
        _filterParams
    ) { videos, params ->
        if (!params.isLockModeUnlocked) {
            emptyList<VideoMediaItem>()
        } else {
            var list = videos.filter { it.isEncrypted1ca }
            if (params.searchQuery.isNotBlank()) {
                val q = params.searchQuery.lowercase().trim()
                list = list.filter {
                    it.displayName.lowercase().contains(q) ||
                    it.folderName.lowercase().contains(q)
                }
            }
            val asc = params.isSortAscending
            when (params.sortOption) {
                SortOption.NAME -> if (asc) list.sortedBy { it.displayName.lowercase() } else list.sortedByDescending { it.displayName.lowercase() }
                SortOption.DATE -> if (asc) list.sortedBy { it.dateModified } else list.sortedByDescending { it.dateModified }
                SortOption.SIZE -> if (asc) list.sortedBy { it.sizeBytes } else list.sortedByDescending { it.sizeBytes }
                SortOption.DURATION -> if (asc) list.sortedBy { it.durationMs } else list.sortedByDescending { it.durationMs }
                SortOption.RESOLUTION -> if (asc) list.sortedBy { it.width * it.height } else list.sortedByDescending { it.width * it.height }
                SortOption.DATE_DESC -> if (!asc) list.sortedByDescending { it.dateModified } else list.sortedBy { it.dateModified }
                SortOption.DATE_ASC -> if (!asc) list.sortedBy { it.dateModified } else list.sortedByDescending { it.dateModified }
                SortOption.NAME_ASC -> if (!asc) list.sortedBy { it.displayName.lowercase() } else list.sortedByDescending { it.displayName.lowercase() }
                SortOption.NAME_DESC -> if (!asc) list.sortedByDescending { it.displayName.lowercase() } else list.sortedBy { it.displayName.lowercase() }
                SortOption.SIZE_DESC -> if (!asc) list.sortedByDescending { it.sizeBytes } else list.sortedBy { it.sizeBytes }
                SortOption.SIZE_ASC -> if (!asc) list.sortedBy { it.sizeBytes } else list.sortedByDescending { it.sizeBytes }
                SortOption.DURATION_DESC -> if (!asc) list.sortedByDescending { it.durationMs } else list.sortedBy { it.durationMs }
                SortOption.DURATION_ASC -> if (!asc) list.sortedBy { it.durationMs } else list.sortedByDescending { it.durationMs }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadPreferencesAndScan()
    }

    private fun loadPreferencesAndScan() {
        viewModelScope.launch {
            val pin = preferencesManager.vaultSecurityManager.getOrRestorePin()
            val ext = preferencesManager.vaultExtensionFlow.first()
            val savedSort = try { SortOption.valueOf(preferencesManager.sortOptionFlow.first()) } catch (_: Exception) { SortOption.DATE_DESC }
            val savedSortAsc = preferencesManager.sortAscendingFlow.first()
            val savedView = try { ViewMode.valueOf(preferencesManager.viewModeFlow.first()) } catch (_: Exception) { ViewMode.GRID }
            val savedThumbnails = preferencesManager.showThumbnailsFlow.first()
            val savedDuration = preferencesManager.showDurationFlow.first()
            val savedSize = preferencesManager.showSizeFlow.first()
            val savedResolution = preferencesManager.showResolutionFlow.first()
            val savedHiddenFiles = preferencesManager.showHiddenFilesFlow.first()
            val savedLocalDisplayModeStr = preferencesManager.localDisplayModeFlow.first()
            val savedLocalDisplayMode = try { LocalDisplayMode.valueOf(savedLocalDisplayModeStr) } catch (_: Exception) { LocalDisplayMode.FOLDERS }
            val rawTab = preferencesManager.lastTabFlow.first()
            val savedTab = when (rawTab) {
                0, 1 -> 0
                2 -> 1
                3 -> 2
                else -> 0
            }
            val savedFolderPath = preferencesManager.lastFolderPathFlow.first()

            _uiState.value = _uiState.value.copy(
                hasPinConfigured = !pin.isNullOrBlank(),
                vaultExtension = ext.trim().removePrefix(".").lowercase().ifBlank { "1ca" },
                sortOption = savedSort,
                isSortAscending = savedSortAsc,
                viewMode = savedView,
                localDisplayMode = savedLocalDisplayMode,
                showThumbnails = savedThumbnails,
                showDuration = savedDuration,
                showSize = savedSize,
                showResolution = savedResolution,
                showHiddenFiles = savedHiddenFiles,
                activeTab = savedTab
            )

            val autoScan = preferencesManager.autoScanFlow.first()
            if (autoScan) {
                scanMedia()
                if (!savedFolderPath.isNullOrBlank()) {
                    val foundFolder = folderList.first().find { it.path == savedFolderPath }
                    if (foundFolder != null) {
                        _uiState.value = _uiState.value.copy(selectedFolder = foundFolder)
                    }
                }
            }
            // Auto populate preset test stream URLs so user has instant test videos
            try {
                networkRepository.addMissingPresetSamples()
            } catch (_: Exception) {}
        }
    }

    fun loadPresetSampleStreams() {
        viewModelScope.launch {
            networkRepository.addMissingPresetSamples()
            _uiState.value = _uiState.value.copy(messageSnackbar = "Preset contoh streaming HEVC & HLS berhasil dimuat!")
        }
    }

    fun scanMedia() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            try {
                val currentPin = preferencesManager.vaultSecurityManager.getOrRestorePin()
                val ext = preferencesManager.vaultExtensionFlow.first()
                _uiState.value = _uiState.value.copy(
                    hasPinConfigured = !currentPin.isNullOrBlank(),
                    vaultExtension = ext.trim().removePrefix(".").lowercase().ifBlank { "1ca" }
                )

                val videos = mediaRepository.scanLocalVideos(
                    customEncryptedExt = _uiState.value.vaultExtension,
                    includeVault1ca = true
                )
                _rawVideos.value = videos
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSortOption(sort: SortOption) {
        _uiState.value = _uiState.value.copy(sortOption = sort)
        viewModelScope.launch {
            preferencesManager.setSortOption(sort.name)
        }
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
        viewModelScope.launch {
            preferencesManager.setViewMode(mode.name)
        }
    }

    fun setSortAscending(ascending: Boolean) {
        _uiState.value = _uiState.value.copy(isSortAscending = ascending)
        viewModelScope.launch {
            preferencesManager.setSortAscending(ascending)
        }
    }

    fun setShowThumbnails(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showThumbnails = enabled)
        viewModelScope.launch {
            preferencesManager.setShowThumbnails(enabled)
        }
    }

    fun setShowDuration(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showDuration = enabled)
        viewModelScope.launch {
            preferencesManager.setShowDuration(enabled)
        }
    }

    fun setShowSize(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showSize = enabled)
        viewModelScope.launch {
            preferencesManager.setShowSize(enabled)
        }
    }

    fun setShowResolution(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showResolution = enabled)
        viewModelScope.launch {
            preferencesManager.setShowResolution(enabled)
        }
    }

    fun setShowHiddenFiles(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showHiddenFiles = enabled)
        viewModelScope.launch {
            preferencesManager.setShowHiddenFiles(enabled)
        }
    }

    fun setLocalDisplayMode(mode: LocalDisplayMode) {
        _uiState.value = _uiState.value.copy(localDisplayMode = mode)
        viewModelScope.launch {
            preferencesManager.setLocalDisplayMode(mode.name)
        }
    }

    fun setActiveTab(tab: Int) {
        _uiState.value = _uiState.value.copy(activeTab = tab, selectedFolder = null)
        viewModelScope.launch {
            preferencesManager.setLastTab(tab)
            preferencesManager.setLastFolderPath(null)
        }
    }

    fun selectFolder(folder: VideoFolder?) {
        _uiState.value = _uiState.value.copy(selectedFolder = folder)
        viewModelScope.launch {
            preferencesManager.setLastFolderPath(folder?.path)
        }
    }

    fun clearSelectedFolder() {
        _uiState.value = _uiState.value.copy(selectedFolder = null)
        viewModelScope.launch {
            preferencesManager.setLastFolderPath(null)
        }
    }

    // Lock Mode / PIN Management
    fun verifyPin(pin: String): Boolean {
        var correct = false
        viewModelScope.launch {
            val isMatch = preferencesManager.vaultSecurityManager.verifyPin(pin)
            if (isMatch) {
                correct = true
                _uiState.value = _uiState.value.copy(
                    isLockModeUnlocked = true,
                    hasPinConfigured = true
                )
                refreshCurrentViewAfterLockChange()
            }
        }
        return correct
    }

    val securityQuestion: StateFlow<String?> = preferencesManager.securityQuestionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun checkPinMatches(pin: String): Boolean {
        val isMatch = preferencesManager.vaultSecurityManager.verifyPin(pin)
        if (isMatch) {
            _uiState.value = _uiState.value.copy(
                isLockModeUnlocked = true,
                hasPinConfigured = true
            )
            refreshCurrentViewAfterLockChange()
        }
        return isMatch
    }

    suspend fun verifySecurityAnswer(answer: String): Boolean {
        val isMatch = preferencesManager.verifySecurityAnswer(answer)
        if (isMatch) {
            _uiState.value = _uiState.value.copy(
                isLockModeUnlocked = true,
                hasPinConfigured = true
            )
            refreshCurrentViewAfterLockChange()
        }
        return isMatch
    }

    fun configurePin(newPin: String) {
        configurePinAndExtension(newPin, _uiState.value.vaultExtension)
    }

    fun configurePinAndExtension(newPin: String, customExtension: String) {
        configurePinAndExtensionWithQuestion(newPin, customExtension, "", "")
    }

    fun configurePinAndExtensionWithQuestion(
        newPin: String,
        customExtension: String,
        question: String,
        answer: String
    ) {
        viewModelScope.launch {
            val cleanExt = customExtension.trim().removePrefix(".").lowercase().ifBlank { "1ca" }
            if (newPin.isNotBlank()) {
                preferencesManager.setPinCode(newPin)
            }
            preferencesManager.setVaultExtension(cleanExt)
            if (question.isNotBlank() && answer.isNotBlank()) {
                preferencesManager.setSecurityQuestionAndAnswer(question, answer)
            }
            val updatedExts = preferencesManager.vaultExtensionFlow.first()
            _uiState.value = _uiState.value.copy(
                hasPinConfigured = true,
                isLockModeUnlocked = true,
                vaultExtension = updatedExts,
                messageSnackbar = "PIN & Pengaturan Keamanan berhasil disimpan!"
            )
            refreshCurrentViewAfterLockChange()
        }
    }

    fun removePin() {
        viewModelScope.launch {
            preferencesManager.clearPinCode()
            _uiState.value = _uiState.value.copy(
                hasPinConfigured = false,
                isLockModeUnlocked = false,
                messageSnackbar = "PIN Keamanan berhasil dihapus"
            )
            refreshCurrentViewAfterLockChange()
        }
    }

    fun lockVault() {
        _uiState.value = _uiState.value.copy(isLockModeUnlocked = false)
        refreshCurrentViewAfterLockChange()
    }

    fun toggleLockMode() {
        if (_uiState.value.isLockModeUnlocked) {
            lockVault()
        }
    }

    private fun refreshCurrentViewAfterLockChange() {
        val server = _uiState.value.ftpBrowsingServer
        if (server != null) {
            fetchFtpFiles(server, _uiState.value.ftpCurrentPath)
        } else if (_rawVideos.value.isEmpty()) {
            scanMedia()
        }
    }

    // File Operations
    fun deleteVideo(video: VideoMediaItem) {
        viewModelScope.launch {
            val success = mediaRepository.deleteVideoFile(video)
            if (success) {
                _rawVideos.value = _rawVideos.value.filter { it.id != video.id }
                _uiState.value = _uiState.value.copy(messageSnackbar = "Deleted ${video.displayName}")
            } else {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Could not delete file")
            }
        }
    }

    fun renameVideo(video: VideoMediaItem, newName: String) {
        viewModelScope.launch {
            val success = mediaRepository.renameVideoFile(video, newName)
            if (success) {
                scanMedia()
                _uiState.value = _uiState.value.copy(messageSnackbar = "Renamed successfully")
            } else {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Could not rename file")
            }
        }
    }

    fun moveVideo(video: VideoMediaItem, targetDir: String) {
        viewModelScope.launch {
            val success = mediaRepository.moveVideoFile(video, targetDir)
            if (success) {
                scanMedia()
                _uiState.value = _uiState.value.copy(messageSnackbar = "Moved file successfully")
            } else {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Could not move file")
            }
        }
    }

    fun lockVideoToVault(video: VideoMediaItem) {
        viewModelScope.launch {
            val locked = mediaRepository.lockVideoToVault(video)
            if (locked != null) {
                scanMedia()
                _uiState.value = _uiState.value.copy(messageSnackbar = "Video berhasil dikunci ke Brankas")
            } else {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Gagal mengunci video")
            }
        }
    }

    fun unlockVideoFromVault(video: VideoMediaItem) {
        viewModelScope.launch {
            val unlocked = mediaRepository.unlockVideoFromVault(video)
            if (unlocked != null) {
                scanMedia()
                _uiState.value = _uiState.value.copy(messageSnackbar = "Video berhasil dipulihkan ke galeri normal")
            } else {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Gagal memulihkan video")
            }
        }
    }

    // Network Navigation (FTP & SMB Samba)
    fun openFtpServer(server: NetworkServerEntity) {
        _uiState.value = _uiState.value.copy(
            ftpBrowsingServer = server,
            ftpCurrentPath = server.initialPath.ifBlank { "/" }
        )
        fetchFtpFiles(server, server.initialPath.ifBlank { "/" })
    }

    fun navigateFtp(path: String) {
        val server = _uiState.value.ftpBrowsingServer ?: return
        _uiState.value = _uiState.value.copy(ftpCurrentPath = path)
        fetchFtpFiles(server, path)
    }

    private fun fetchFtpFiles(server: NetworkServerEntity, path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(ftpLoading = true, ftpErrorMessage = null)
            val result = networkRepository.listNetworkFiles(
                server = server,
                remotePath = path,
                customEncryptedExt = _uiState.value.vaultExtension,
                includeEncrypted = _uiState.value.isLockModeUnlocked
            )
            result.onSuccess { files ->
                _uiState.value = _uiState.value.copy(
                    ftpFiles = files,
                    ftpLoading = false
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    ftpErrorMessage = error.localizedMessage ?: "${server.type} Connection Error",
                    ftpLoading = false
                )
            }
        }
    }

    fun closeFtpBrowser() {
        _uiState.value = _uiState.value.copy(
            ftpBrowsingServer = null,
            ftpFiles = emptyList(),
            ftpErrorMessage = null
        )
    }

    fun addNetworkServer(name: String, type: String, host: String, port: Int, user: String, pass: String, path: String) {
        viewModelScope.launch {
            networkRepository.addServer(
                NetworkServerEntity(
                    name = name.ifBlank { host },
                    type = type,
                    host = host,
                    port = port,
                    username = user,
                    password = pass,
                    initialPath = path,
                    isAnonymous = user.isBlank()
                )
            )
            _uiState.value = _uiState.value.copy(messageSnackbar = "Added $type server")
        }
    }

    fun deleteNetworkServer(id: Long) {
        viewModelScope.launch {
            networkRepository.removeServer(id)
        }
    }

    fun setFolderFilterMode(mode: String) {
        _uiState.value = _uiState.value.copy(folderFilterMode = mode)
    }

    fun navigateFolderTree(path: String?) {
        val cleanPath = if (path == "ROOT" || path.isNullOrBlank()) null else path
        _uiState.value = _uiState.value.copy(folderTreeCurrentPath = cleanPath)
    }

    fun navigateFolderTreeUp() {
        val current = _uiState.value.folderTreeCurrentPath ?: return
        val internalRoot = Environment.getExternalStorageDirectory().absolutePath
        if (current == internalRoot || current == "/storage/emulated/0" || !current.contains("/")) {
            _uiState.value = _uiState.value.copy(folderTreeCurrentPath = null)
        } else {
            val parent = current.substringBeforeLast("/")
            if (parent.isBlank() || parent == "/storage") {
                _uiState.value = _uiState.value.copy(folderTreeCurrentPath = null)
            } else {
                _uiState.value = _uiState.value.copy(folderTreeCurrentPath = parent)
            }
        }
    }

    // Network Shortcuts
    fun addNetworkShortcut(
        title: String,
        serverId: Long?,
        serverName: String,
        serverType: String,
        targetPath: String,
        isDirectory: Boolean
    ) {
        viewModelScope.launch {
            networkRepository.addShortcut(
                NetworkShortcutEntity(
                    title = title.ifBlank { targetPath.substringAfterLast("/").ifBlank { "Shortcut" } },
                    serverId = serverId,
                    serverName = serverName,
                    serverType = serverType,
                    targetPath = targetPath,
                    isDirectory = isDirectory
                )
            )
            _uiState.value = _uiState.value.copy(messageSnackbar = "Pintasan '$title' berhasil ditambahkan!")
        }
    }

    fun deleteNetworkShortcut(id: Long) {
        viewModelScope.launch {
            networkRepository.removeShortcut(id)
            _uiState.value = _uiState.value.copy(messageSnackbar = "Pintasan dihapus")
        }
    }

    // Remote File Management
    fun deleteRemoteFile(server: NetworkServerEntity, item: NetworkFileItem) {
        viewModelScope.launch {
            val result = networkRepository.deleteRemoteFile(server, item)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Berhasil menghapus '${item.name}' dari server")
                fetchFtpFiles(server, _uiState.value.ftpCurrentPath)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(messageSnackbar = "Gagal menghapus: ${err.localizedMessage}")
            }
        }
    }

    fun renameRemoteFile(server: NetworkServerEntity, item: NetworkFileItem, newName: String) {
        viewModelScope.launch {
            val result = networkRepository.renameRemoteFile(server, item, newName)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Berhasil mengubah nama menjadi '$newName'")
                fetchFtpFiles(server, _uiState.value.ftpCurrentPath)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(messageSnackbar = "Gagal mengubah nama: ${err.localizedMessage}")
            }
        }
    }

    fun moveRemoteFile(server: NetworkServerEntity, item: NetworkFileItem, targetDir: String) {
        viewModelScope.launch {
            val result = networkRepository.moveRemoteFile(server, item, targetDir)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Berhasil memindahkan '${item.name}' ke '$targetDir'")
                fetchFtpFiles(server, _uiState.value.ftpCurrentPath)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(messageSnackbar = "Gagal memindahkan: ${err.localizedMessage}")
            }
        }
    }

    fun copyRemoteFile(server: NetworkServerEntity, item: NetworkFileItem, targetDir: String) {
        viewModelScope.launch {
            val result = networkRepository.copyRemoteFile(server, item, targetDir)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Berhasil menyalin '${item.name}'")
                fetchFtpFiles(server, _uiState.value.ftpCurrentPath)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(messageSnackbar = "Gagal menyalin: ${err.localizedMessage}")
            }
        }
    }

    fun downloadRemoteFileToLocal(server: NetworkServerEntity, item: NetworkFileItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloadingRemoteFile = true,
                downloadProgress = 0f,
                downloadingFileName = item.name
            )
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: getApplication<Application>().filesDir
            val targetFile = File(downloadDir, item.name)

            val result = networkRepository.downloadRemoteFile(
                server = server,
                item = item,
                targetLocalFile = targetFile,
                onProgress = { p ->
                    _uiState.value = _uiState.value.copy(downloadProgress = p)
                }
            )

            result.onSuccess { localFile ->
                _uiState.value = _uiState.value.copy(
                    isDownloadingRemoteFile = false,
                    downloadProgress = 1f,
                    downloadingFileName = null,
                    messageSnackbar = "Selesai mengunduh '${item.name}' ke folder Download"
                )
                scanMedia()
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isDownloadingRemoteFile = false,
                    downloadProgress = 0f,
                    downloadingFileName = null,
                    messageSnackbar = "Gagal mengunduh: ${err.localizedMessage}"
                )
            }
        }
    }

    fun lockRemoteFileToVault(server: NetworkServerEntity, item: NetworkFileItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloadingRemoteFile = true,
                downloadProgress = 0f,
                downloadingFileName = "Mengunci ${item.name}..."
            )
            val tempFile = File.createTempFile("net_vault", ".tmp")
            val dlResult = networkRepository.downloadRemoteFile(
                server = server,
                item = item,
                targetLocalFile = tempFile,
                onProgress = { p ->
                    _uiState.value = _uiState.value.copy(downloadProgress = p)
                }
            )

            dlResult.onSuccess { downloadedTemp ->
                val vaultDir = File(getApplication<Application>().filesDir, "vault_1ca").apply { mkdirs() }
                val destFile = File(vaultDir, item.name)
                downloadedTemp.copyTo(destFile, overwrite = true)
                downloadedTemp.delete()
                val encryptedFile = com.example.player.EncryptionUtil.encryptVideoTo1ca(destFile)

                _uiState.value = _uiState.value.copy(
                    isDownloadingRemoteFile = false,
                    downloadProgress = 1f,
                    downloadingFileName = null
                )

                if (encryptedFile != null) {
                    _uiState.value = _uiState.value.copy(
                        messageSnackbar = "File '${item.name}' berhasil dikunci & dienkripsi ke Brankas!"
                    )
                    scanMedia()
                } else {
                    _uiState.value = _uiState.value.copy(
                        messageSnackbar = "Gagal mengenkripsi file ke Brankas"
                    )
                }
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isDownloadingRemoteFile = false,
                    downloadProgress = 0f,
                    downloadingFileName = null,
                    messageSnackbar = "Gagal mengunduh file untuk Brankas: ${err.localizedMessage}"
                )
            }
        }
    }

    fun addStreamBookmark(title: String, url: String) {
        viewModelScope.launch {
            networkRepository.addBookmark(
                StreamBookmarkEntity(
                    title = title.ifBlank { "Stream" },
                    url = url
                )
            )
            _uiState.value = _uiState.value.copy(messageSnackbar = "Added stream bookmark")
        }
    }

    fun deleteStreamBookmark(id: Long) {
        viewModelScope.launch {
            networkRepository.removeBookmark(id)
        }
    }

    fun clearMessageSnackbar() {
        _uiState.value = _uiState.value.copy(messageSnackbar = null)
    }
}
