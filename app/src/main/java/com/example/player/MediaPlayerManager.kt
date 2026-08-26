package com.example.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.model.AspectRatioMode
import com.example.data.model.DecoderMode
import com.example.data.model.StreamType
import com.example.data.model.VideoMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.Collections
import java.util.concurrent.TimeUnit

data class PlayerTrackInfo(
    val id: String,
    val label: String,
    val language: String,
    val isSelected: Boolean,
    val trackGroupIndex: Int,
    val trackIndex: Int
)

data class PlayerState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val decoderMode: DecoderMode = DecoderMode.HW,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val audioTracks: List<PlayerTrackInfo> = emptyList(),
    val subtitleTracks: List<PlayerTrackInfo> = emptyList(),
    val isMuted: Boolean = false,
    val errorMessage: String? = null,
    val videoCodecName: String = "Auto"
)

@OptIn(UnstableApi::class)
class MediaPlayerManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var currentMediaItem: VideoMediaItem? = null
    private var activeDecoderMode: DecoderMode = DecoderMode.HW

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun getPlayer(): ExoPlayer {
        return exoPlayer ?: initializePlayer(activeDecoderMode)
    }

    fun initializePlayer(decoderMode: DecoderMode = DecoderMode.HW): ExoPlayer {
        exoPlayer?.release()
        activeDecoderMode = decoderMode

        val renderersFactory = createRenderersFactory(decoderMode)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                500, // min buffer 0.5s for instant scrub & playback
                15000, // max buffer 15s
                500, // buffer for playback 0.5s
                1000 // buffer for rebuffering 1s
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val defaultDataSourceFactory = DefaultDataSource.Factory(context)
        val encryptedDataSourceFactory = EncryptionUtil.getDecryptedStreamDataSourceFactory()

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(defaultDataSourceFactory)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isLoading = playbackState == Player.STATE_BUFFERING
                val duration = if (player.duration > 0) player.duration else 0L
                _playerState.value = _playerState.value.copy(
                    isLoading = isLoading,
                    durationMs = duration
                )
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                _playerState.value = _playerState.value.copy(
                    videoWidth = videoSize.width,
                    videoHeight = videoSize.height
                )
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTracksList(tracks)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // If HW decoder fails on low-end device or unsupported codec profile, auto fallback to SW decoder
                if (activeDecoderMode != DecoderMode.SW) {
                    switchToDecoder(DecoderMode.SW)
                } else {
                    _playerState.value = _playerState.value.copy(
                        errorMessage = error.localizedMessage ?: "Playback error: codec not supported"
                    )
                }
            }
        })

        exoPlayer = player
        _playerState.value = _playerState.value.copy(decoderMode = decoderMode)
        return player
    }

    private fun createRenderersFactory(decoderMode: DecoderMode): DefaultRenderersFactory {
        val rf = DefaultRenderersFactory(context)

        when (decoderMode) {
            DecoderMode.SW -> {
                // Software decoder selector prioritizes Google/AOSP software decoders
                val swCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                    val swDecoders = decoders.filter {
                        it.name.startsWith("c2.android.") ||
                        it.name.startsWith("OMX.google.") ||
                        it.name.contains("sw", ignoreCase = true) ||
                        it.name.contains("software", ignoreCase = true)
                    }
                    if (swDecoders.isNotEmpty()) swDecoders else decoders
                }
                rf.setMediaCodecSelector(swCodecSelector)
                rf.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            }
            DecoderMode.HW -> {
                // Prefer Hardware decoder
                rf.setMediaCodecSelector(MediaCodecSelector.DEFAULT)
                rf.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            }
            DecoderMode.HW_PLUS -> {
                rf.setMediaCodecSelector(MediaCodecSelector.DEFAULT)
                rf.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            }
        }
        return rf
    }

    fun playMedia(media: VideoMediaItem, startPositionMs: Long = 0L) {
        val player = getPlayer()
        currentMediaItem = media

        val mediaSource = createMediaSourceFor(media)
        player.setMediaSource(mediaSource)
        player.prepare()
        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        player.playWhenReady = true

        _playerState.value = _playerState.value.copy(
            errorMessage = null,
            videoCodecName = media.codec
        )
    }

    private fun createMediaSourceFor(media: VideoMediaItem): MediaSource {
        if (media.isEncrypted1ca || media.streamType == StreamType.VAULT_1CA) {
            val encFactory = EncryptionUtil.getDecryptedStreamDataSourceFactory()
            val mediaItem = MediaItem.Builder()
                .setUri(media.uri)
                .setMimeType(MimeTypes.VIDEO_MP4)
                .build()
            return ProgressiveMediaSource.Factory(encFactory).createMediaSource(mediaItem)
        }

        if (media.streamType == StreamType.URL_STREAM || media.path.startsWith("http://") || media.path.startsWith("https://")) {
            val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            val defaultFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaItem = MediaItem.fromUri(media.uri)
            return DefaultMediaSourceFactory(defaultFactory).createMediaSource(mediaItem)
        }

        val defaultFactory = DefaultDataSource.Factory(context)
        return DefaultMediaSourceFactory(defaultFactory).createMediaSource(MediaItem.fromUri(media.uri))
    }

    fun switchToDecoder(decoderMode: DecoderMode) {
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition
        val currentPlayWhenReady = player.playWhenReady
        val media = currentMediaItem

        initializePlayer(decoderMode)
        if (media != null) {
            playMedia(media, currentPos)
            exoPlayer?.playWhenReady = currentPlayWhenReady
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(maxOf(0L, positionMs))
    }

    fun skipForward(seconds: Int = 10) {
        val player = exoPlayer ?: return
        val target = player.currentPosition + (seconds * 1000L)
        player.seekTo(minOf(player.duration, target))
    }

    fun skipBackward(seconds: Int = 10) {
        val player = exoPlayer ?: return
        val target = player.currentPosition - (seconds * 1000L)
        player.seekTo(maxOf(0L, target))
    }

    fun setPlaybackSpeed(speed: Float) {
        val player = exoPlayer ?: return
        player.playbackParameters = PlaybackParameters(speed)
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
    }

    fun setAspectRatioMode(mode: AspectRatioMode) {
        _playerState.value = _playerState.value.copy(aspectRatioMode = mode)
    }

    fun cycleAspectRatio() {
        val current = _playerState.value.aspectRatioMode
        val next = when (current) {
            AspectRatioMode.FIT -> AspectRatioMode.CROP
            AspectRatioMode.CROP -> AspectRatioMode.STRETCH
            AspectRatioMode.STRETCH -> AspectRatioMode.ORIGINAL
            AspectRatioMode.ORIGINAL -> AspectRatioMode.FIT
        }
        setAspectRatioMode(next)
    }

    fun cycleDecoder() {
        val current = _playerState.value.decoderMode
        val next = when (current) {
            DecoderMode.HW -> DecoderMode.SW
            DecoderMode.SW -> DecoderMode.HW_PLUS
            DecoderMode.HW_PLUS -> DecoderMode.HW
        }
        switchToDecoder(next)
    }

    fun selectAudioTrack(trackInfo: PlayerTrackInfo) {
        val player = exoPlayer ?: return
        val tracks = player.currentTracks
        val group = tracks.groups[trackInfo.trackGroupIndex]
        val override = TrackSelectionOverride(group.mediaTrackGroup, trackInfo.trackIndex)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
    }

    fun selectSubtitleTrack(trackInfo: PlayerTrackInfo?) {
        val player = exoPlayer ?: return
        if (trackInfo == null) {
            // Disable subtitles
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            val tracks = player.currentTracks
            val group = tracks.groups[trackInfo.trackGroupIndex]
            val override = TrackSelectionOverride(group.mediaTrackGroup, trackInfo.trackIndex)
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(override)
                .build()
        }
    }

    private fun updateTracksList(tracks: Tracks) {
        val audioList = mutableListOf<PlayerTrackInfo>()
        val subtitleList = mutableListOf<PlayerTrackInfo>()

        for (gIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[gIndex]
            for (tIndex in 0 until group.length) {
                val format = group.getTrackFormat(tIndex)
                val isSelected = group.isTrackSelected(tIndex)
                val trackId = "${gIndex}_${tIndex}_${format.id ?: tIndex}"
                val lang = format.language ?: "und"
                val label = format.label ?: "Track ${tIndex + 1} (${lang.uppercase()})"

                if (group.type == C.TRACK_TYPE_AUDIO) {
                    val sampleRate = if (format.sampleRate > 0) " ${format.sampleRate}Hz" else ""
                    val channels = if (format.channelCount > 0) " ${format.channelCount}ch" else ""
                    audioList.add(
                        PlayerTrackInfo(
                            id = trackId,
                            label = "$label$channels$sampleRate",
                            language = lang,
                            isSelected = isSelected,
                            trackGroupIndex = gIndex,
                            trackIndex = tIndex
                        )
                    )
                } else if (group.type == C.TRACK_TYPE_TEXT) {
                    subtitleList.add(
                        PlayerTrackInfo(
                            id = trackId,
                            label = label,
                            language = lang,
                            isSelected = isSelected,
                            trackGroupIndex = gIndex,
                            trackIndex = tIndex
                        )
                    )
                }
            }
        }

        _playerState.value = _playerState.value.copy(
            audioTracks = audioList,
            subtitleTracks = subtitleList
        )
    }

    fun updateProgress() {
        val player = exoPlayer ?: return
        val pos = player.currentPosition
        val dur = if (player.duration > 0) player.duration else 0L
        val buffered = player.bufferedPosition
        _playerState.value = _playerState.value.copy(
            currentPositionMs = pos,
            durationMs = dur,
            bufferedPositionMs = buffered
        )
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
