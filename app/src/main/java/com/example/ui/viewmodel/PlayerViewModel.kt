package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MediaPlayerDatabase
import com.example.data.local.UserPreferencesManager
import com.example.data.model.AspectRatioMode
import com.example.data.model.DecoderMode
import com.example.data.model.VideoMediaItem
import com.example.player.MediaPlayerManager
import com.example.player.PlayerState
import com.example.player.PlayerTrackInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class GestureOverlayState(
    val isVisible: Boolean = false,
    val type: GestureType = GestureType.NONE,
    val valuePercent: Int = 0,
    val seekTargetMs: Long = 0L,
    val seekDeltaMs: Long = 0L
)

enum class GestureType {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK
}

data class DoubleTapRipple(
    val isForward: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playerManager = MediaPlayerManager(application)
    private val db = MediaPlayerDatabase.getDatabase(application)
    private val repository = com.example.data.repository.MediaRepository(application, db.mediaPlayerDao())
    private val preferencesManager = UserPreferencesManager(application)

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val playerState: StateFlow<PlayerState> = playerManager.playerState

    private val _currentMedia = MutableStateFlow<VideoMediaItem?>(null)
    val currentMedia: StateFlow<VideoMediaItem?> = _currentMedia.asStateFlow()

    private val _playlist = MutableStateFlow<List<VideoMediaItem>>(emptyList())
    val playlist: StateFlow<List<VideoMediaItem>> = _playlist.asStateFlow()

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _isScreenLocked = MutableStateFlow(false)
    val isScreenLocked: StateFlow<Boolean> = _isScreenLocked.asStateFlow()

    private val _gestureState = MutableStateFlow(GestureOverlayState())
    val gestureState: StateFlow<GestureOverlayState> = _gestureState.asStateFlow()

    private val _doubleTapRipple = MutableStateFlow<DoubleTapRipple?>(null)
    val doubleTapRipple: StateFlow<DoubleTapRipple?> = _doubleTapRipple.asStateFlow()

    private var progressTrackerJob: Job? = null
    private var hideControlsJob: Job? = null
    private var hideGestureJob: Job? = null

    // System Brightness and Volume caches
    private var currentBrightness: Float = 0.5f

    init {
        startProgressTracker()
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = viewModelScope.launch {
            while (isActive) {
                playerManager.updateProgress()
                saveCurrentPosition()
                delay(500)
            }
        }
    }

    fun playMediaItem(media: VideoMediaItem, playlistQueue: List<VideoMediaItem> = emptyList()) {
        _currentMedia.value = media
        _playlist.value = playlistQueue.ifEmpty { listOf(media) }

        viewModelScope.launch {
            val defaultDec = preferencesManager.defaultDecoderFlow.first()
            val decoder = when (defaultDec) {
                "SW" -> DecoderMode.SW
                "HW_PLUS" -> DecoderMode.HW_PLUS
                else -> DecoderMode.HW
            }

            val resume = preferencesManager.resumePlaybackFlow.first()
            val history = if (resume) repository.getPlayHistoryForUri(media.uri.toString()) else null
            val startPos = history?.lastPositionMs ?: 0L

            playerManager.initializePlayer(decoder)
            playerManager.playMedia(media, startPos)
            showControlsWithTimeout()
        }
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
        showControlsWithTimeout()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
        showControlsWithTimeout()
    }

    fun skipForward(seconds: Int = 10) {
        playerManager.skipForward(seconds)
        _doubleTapRipple.value = DoubleTapRipple(isForward = true)
        viewModelScope.launch {
            delay(600)
            _doubleTapRipple.value = null
        }
    }

    fun skipBackward(seconds: Int = 10) {
        playerManager.skipBackward(seconds)
        _doubleTapRipple.value = DoubleTapRipple(isForward = false)
        viewModelScope.launch {
            delay(600)
            _doubleTapRipple.value = null
        }
    }

    fun playNext() {
        val list = _playlist.value
        val current = _currentMedia.value ?: return
        val index = list.indexOfFirst { it.id == current.id }
        if (index >= 0 && index < list.size - 1) {
            playMediaItem(list[index + 1], list)
        }
    }

    fun playPrevious() {
        val list = _playlist.value
        val current = _currentMedia.value ?: return
        val index = list.indexOfFirst { it.id == current.id }
        if (index > 0) {
            playMediaItem(list[index - 1], list)
        }
    }

    fun setDecoderMode(mode: DecoderMode) {
        playerManager.switchToDecoder(mode, isUserAction = true)
    }

    fun cycleDecoder() {
        playerManager.cycleDecoder()
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        playerManager.setAspectRatioMode(mode)
    }

    fun cycleAspectRatio() {
        playerManager.cycleAspectRatio()
    }

    fun setSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
    }

    fun selectAudioTrack(track: PlayerTrackInfo) {
        playerManager.selectAudioTrack(track)
    }

    fun selectSubtitleTrack(track: PlayerTrackInfo?) {
        playerManager.selectSubtitleTrack(track)
    }

    fun toggleScreenLock() {
        _isScreenLocked.value = !_isScreenLocked.value
        if (_isScreenLocked.value) {
            _controlsVisible.value = false
        } else {
            showControlsWithTimeout()
        }
    }

    fun toggleControlsVisibility() {
        if (_isScreenLocked.value) {
            // briefly show lock icon
            _controlsVisible.value = !_controlsVisible.value
            return
        }
        if (_controlsVisible.value) {
            _controlsVisible.value = false
            hideControlsJob?.cancel()
        } else {
            showControlsWithTimeout()
        }
    }

    fun showControlsWithTimeout(timeoutMs: Long = 4000) {
        _controlsVisible.value = true
        hideControlsJob?.cancel()
        hideControlsJob = viewModelScope.launch {
            delay(timeoutMs)
            if (playerState.value.isPlaying && !_isScreenLocked.value) {
                _controlsVisible.value = false
            }
        }
    }

    private var volumeAccumulator: Float = -1f

    // Gestures
    fun onBrightnessSwipe(delta: Float, activity: Activity) {
        try {
            val window = activity.window
            val lp = window.attributes
            var current = if (lp.screenBrightness < 0) {
                try {
                    Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                } catch (e: Exception) { 0.5f }
            } else lp.screenBrightness

            current = (current + delta).coerceIn(0.01f, 1.0f)
            lp.screenBrightness = current
            window.attributes = lp
            currentBrightness = current

            showGestureOverlay(GestureType.BRIGHTNESS, (current * 100).toInt())
        } catch (_: Exception) {}
    }

    fun onVolumeSwipe(delta: Float) {
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVolume <= 0) return
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            if (volumeAccumulator < 0f) {
                volumeAccumulator = currentVolume.toFloat()
            }

            volumeAccumulator = (volumeAccumulator + (delta * maxVolume)).coerceIn(0f, maxVolume.toFloat())
            val newVol = kotlin.math.round(volumeAccumulator).toInt().coerceIn(0, maxVolume)

            if (newVol != currentVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
            }

            val percent = (newVol * 100) / maxVolume
            showGestureOverlay(GestureType.VOLUME, percent)
        } catch (_: Exception) {}
    }

    fun resetVolumeAccumulator() {
        volumeAccumulator = -1f
    }

    fun onSeekSwipe(deltaMs: Long) {
        val current = playerState.value.currentPositionMs
        val dur = playerState.value.durationMs
        val target = (current + deltaMs).coerceIn(0L, dur)

        _gestureState.value = GestureOverlayState(
            isVisible = true,
            type = GestureType.SEEK,
            seekTargetMs = target,
            seekDeltaMs = deltaMs
        )

        hideGestureJob?.cancel()
    }

    fun onSeekSwipeFinished() {
        if (_gestureState.value.type == GestureType.SEEK) {
            seekTo(_gestureState.value.seekTargetMs)
        }
        hideGestureOverlay()
    }

    private fun showGestureOverlay(type: GestureType, valuePercent: Int) {
        _gestureState.value = GestureOverlayState(
            isVisible = true,
            type = type,
            valuePercent = valuePercent
        )
        hideGestureJob?.cancel()
        hideGestureJob = viewModelScope.launch {
            delay(1200)
            _gestureState.value = _gestureState.value.copy(isVisible = false)
        }
    }

    fun hideGestureOverlay() {
        hideGestureJob?.cancel()
        hideGestureJob = viewModelScope.launch {
            delay(300)
            _gestureState.value = _gestureState.value.copy(isVisible = false)
        }
    }

    private fun saveCurrentPosition() {
        val media = _currentMedia.value ?: return
        val pos = playerState.value.currentPositionMs
        val dur = playerState.value.durationMs
        if (dur > 0 && pos > 0) {
            viewModelScope.launch {
                repository.savePlaybackPosition(
                    uri = media.uri.toString(),
                    path = media.path,
                    title = media.title,
                    positionMs = pos,
                    durationMs = dur,
                    decoderMode = playerState.value.decoderMode.name
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressTrackerJob?.cancel()
        playerManager.release()
    }
}
