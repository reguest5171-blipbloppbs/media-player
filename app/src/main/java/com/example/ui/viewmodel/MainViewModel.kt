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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val isScanning: Boolean = false,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_DESC,
    val viewMode: ViewMode = ViewMode.GRID,
    val isLockModeUnlocked: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val activeTab: Int = 0, // 0: All, 1: Folders, 2: Network, 3: Vault
    val selectedFolder: VideoFolder? = null,
    val ftpBrowsingServer: NetworkServerEntity? = null,
    val ftpCurrentPath: String = "/",
    val ftpFiles: List<NetworkFileItem> = emptyList(),
    val ftpLoading: Boolean = false,
    val ftpErrorMessage: String? = null,
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

    // Filtered & Sorted Videos
    val displayedVideos: StateFlow<List<VideoMediaItem>> = combine(
        _rawVideos,
        _uiState
    ) { videos, state ->
        var list = videos.filter { video ->
            // In normal mode, hide .1ca files unless Lock Mode is unlocked
            if (video.isEncrypted1ca && !state.isLockModeUnlocked) {
                false
            } else {
                true
            }
        }

        // Folder filter if active
        if (state.selectedFolder != null) {
            list = list.filter { it.folderPath == state.selectedFolder.path }
        }

        // Search filter
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.trim().lowercase()
            list = list.filter {
                it.displayName.lowercase().contains(query) ||
                it.folderName.lowercase().contains(query) ||
                it.path.lowercase().contains(query)
            }
        }

        // Sorting
        when (state.sortOption) {
            SortOption.DATE_DESC -> list.sortedByDescending { it.dateModified }
            SortOption.DATE_ASC -> list.sortedBy { it.dateModified }
            SortOption.NAME_ASC -> list.sortedBy { it.displayName.lowercase() }
            SortOption.NAME_DESC -> list.sortedByDescending { it.displayName.lowercase() }
            SortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> list.sortedBy { it.sizeBytes }
            SortOption.DURATION_DESC -> list.sortedByDescending { it.durationMs }
            SortOption.DURATION_ASC -> list.sortedBy { it.durationMs }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folderList: StateFlow<List<VideoFolder>> = combine(
        _rawVideos,
        _uiState
    ) { videos, state ->
        val visibleVideos = videos.filter {
            !it.isEncrypted1ca || state.isLockModeUnlocked
        }
        mediaRepository.groupVideosIntoFolders(visibleVideos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultVideos: StateFlow<List<VideoMediaItem>> = _rawVideos.combine(_uiState) { videos, state ->
        if (state.isLockModeUnlocked) {
            videos.filter { it.isEncrypted1ca }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadPreferencesAndScan()
    }

    private fun loadPreferencesAndScan() {
        viewModelScope.launch {
            // Restore PIN from persistent external storage if app data was cleared
            val pin = preferencesManager.vaultSecurityManager.getOrRestorePin()
            _uiState.value = _uiState.value.copy(
                hasPinConfigured = !pin.isNullOrBlank()
            )

            val autoScan = preferencesManager.autoScanFlow.first()
            if (autoScan) {
                scanMedia()
            }
            // Auto populate preset test stream URLs so user has instant test videos
            try {
                val currentBookmarks = networkRepository.bookmarksFlow.first()
                if (currentBookmarks.isEmpty()) {
                    networkRepository.populatePresetSamplesIfEmpty()
                }
            } catch (_: Exception) {}
        }
    }

    fun loadPresetSampleStreams() {
        viewModelScope.launch {
            networkRepository.populatePresetSamplesIfEmpty()
            _uiState.value = _uiState.value.copy(messageSnackbar = "Preset contoh streaming berhasil dimuat!")
        }
    }

    fun scanMedia() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            try {
                // Also ensure PIN is refreshed/restored if needed
                val currentPin = preferencesManager.vaultSecurityManager.getOrRestorePin()
                _uiState.value = _uiState.value.copy(hasPinConfigured = !currentPin.isNullOrBlank())

                val videos = mediaRepository.scanLocalVideos(includeVault1ca = true)
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

    fun setActiveTab(tab: Int) {
        _uiState.value = _uiState.value.copy(activeTab = tab, selectedFolder = null)
    }

    fun selectFolder(folder: VideoFolder?) {
        _uiState.value = _uiState.value.copy(selectedFolder = folder)
    }

    fun clearSelectedFolder() {
        _uiState.value = _uiState.value.copy(selectedFolder = null)
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
            }
        }
        return correct
    }

    suspend fun checkPinMatches(pin: String): Boolean {
        val isMatch = preferencesManager.vaultSecurityManager.verifyPin(pin)
        if (isMatch) {
            _uiState.value = _uiState.value.copy(
                isLockModeUnlocked = true,
                hasPinConfigured = true
            )
        }
        return isMatch
    }

    fun configurePin(newPin: String) {
        viewModelScope.launch {
            preferencesManager.setPinCode(newPin)
            _uiState.value = _uiState.value.copy(
                hasPinConfigured = true,
                isLockModeUnlocked = true,
                messageSnackbar = "PIN Keamanan berhasil disimpan permanen!"
            )
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
        }
    }

    fun lockVault() {
        _uiState.value = _uiState.value.copy(isLockModeUnlocked = false)
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
                _uiState.value = _uiState.value.copy(messageSnackbar = "Encrypted to .1ca vault")
            } else {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Failed to lock video")
            }
        }
    }

    fun unlockVideoFromVault(video: VideoMediaItem) {
        viewModelScope.launch {
            val unlocked = mediaRepository.unlockVideoFromVault(video)
            if (unlocked != null) {
                scanMedia()
                _uiState.value = _uiState.value.copy(messageSnackbar = "Restored original video")
            } else {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Failed to unlock video")
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
            val result = networkRepository.listNetworkFiles(server, path)
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
