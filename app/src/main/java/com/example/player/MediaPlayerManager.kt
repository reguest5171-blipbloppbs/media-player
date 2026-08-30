package com.example.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.data.model.AspectRatioMode
import com.example.data.model.DecoderMode
import com.example.data.model.StreamType
import com.example.data.model.VideoMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
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

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var exoPlayer: ExoPlayer? = null
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var currentMediaItem: VideoMediaItem? = null
    private var activeDecoderMode: DecoderMode = DecoderMode.HW
    private var fallbackAttempted: Boolean = false

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun getPlayer(): ExoPlayer {
        return exoPlayer ?: initializePlayer(activeDecoderMode)
    }

    private fun createExtractorsFactory(): DefaultExtractorsFactory {
        return DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
    }

    @Synchronized
    fun initializePlayer(decoderMode: DecoderMode = DecoderMode.HW): ExoPlayer {
        try {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.release()
        } catch (_: Exception) {}
        exoPlayer = null

        activeDecoderMode = decoderMode

        val renderersFactory = createRenderersFactory(decoderMode)

        // Load control tuned for instant playback start and smooth buffering
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // min buffer 15s
                50000, // max buffer 50s
                500,   // buffer for playback 0.5s (instant start)
                1000   // buffer for rebuffering 1s
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Connection" to "keep-alive"
                )
            )
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val extractorsFactory = createExtractorsFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
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

                // Watchdog: If player stalls in STATE_BUFFERING for > 4.0s while playWhenReady at start, auto-fallback or report error
                if (playbackState == Player.STATE_BUFFERING && player.playWhenReady) {
                    val targetMedia = currentMediaItem
                    coroutineScope.launch {
                        delay(4000L)
                        if (exoPlayer?.playbackState == Player.STATE_BUFFERING &&
                            exoPlayer?.playWhenReady == true &&
                            currentMediaItem == targetMedia &&
                            _playerState.value.errorMessage == null
                        ) {
                            if (!fallbackAttempted) {
                                fallbackAttempted = true
                                val fallbackMode = if (activeDecoderMode == DecoderMode.SW) DecoderMode.HW else DecoderMode.SW
                                switchToDecoder(fallbackMode)
                            } else {
                                _playerState.value = _playerState.value.copy(
                                    isLoading = false,
                                    errorMessage = "Decoder perangkat keras/lunak Android 8 ini tidak mendukung pemutaran format video ini."
                                )
                            }
                        }
                    }
                }
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

            override fun onPlayerError(error: PlaybackException) {
                // Check if the root cause or inner cause is HttpDataSource / network error
                var isNetworkError = false
                var httpStatusCode = 0
                var currentCause: Throwable? = error
                while (currentCause != null) {
                    if (currentCause is HttpDataSource.InvalidResponseCodeException) {
                        isNetworkError = true
                        httpStatusCode = currentCause.responseCode
                        break
                    } else if (currentCause is HttpDataSource.HttpDataSourceException ||
                               currentCause is java.net.SocketTimeoutException ||
                               currentCause is java.net.UnknownHostException ||
                               currentCause is java.net.ConnectException) {
                        isNetworkError = true
                        break
                    }
                    currentCause = currentCause.cause
                }

                val isDecoderError = !isNetworkError && (
                        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
                )

                // If HW decoder fails on low-end device, auto fallback to SW decoder once (only for decoder/codec errors, NOT network/404/403 errors)
                if (isDecoderError && activeDecoderMode != DecoderMode.SW && !fallbackAttempted) {
                    fallbackAttempted = true
                    switchToDecoder(DecoderMode.SW)
                } else {
                    val msg = when {
                        httpStatusCode == 404 -> "Video / URL streaming tidak ditemukan di server (HTTP 404 Not Found)."
                        httpStatusCode == 403 -> "Akses streaming ditolak oleh server (HTTP 403 Forbidden). Server membatasi akses URL ini."
                        httpStatusCode in 500..599 -> "Server video sedang mengalami kendala (HTTP $httpStatusCode Server Error)."
                        httpStatusCode > 0 -> "Gagal memuat URL streaming (HTTP $httpStatusCode)."
                        isNetworkError -> "Gagal menghubungkan ke server streaming. Periksa koneksi internet Anda."
                        isDecoderError -> "Codec video tidak didukung oleh perangkat keras (Hardware Error). Coba alihkan ke mode SW (Software Decoder)."
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Format kontainer file video tidak didukung."
                        else -> error.localizedMessage ?: "Gagal memutar video (Error ${error.errorCodeName})"
                    }
                    _playerState.value = _playerState.value.copy(
                        isLoading = false,
                        errorMessage = msg
                    )
                }
            }
        })

        exoPlayer = player
        _playerState.value = _playerState.value.copy(
            decoderMode = decoderMode,
            errorMessage = null
        )
        return player
    }

    private fun createRenderersFactory(decoderMode: DecoderMode): DefaultRenderersFactory {
        val rf = try {
            NextRenderersFactory(context)
        } catch (_: Throwable) {
            DefaultRenderersFactory(context)
        }
        rf.setEnableDecoderFallback(true)
        rf.setAllowedVideoJoiningTimeMs(5000)

        val nextPlayerMediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoders = try {
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            } catch (_: Exception) {
                emptyList()
            }
            if (decoders.isEmpty()) return@MediaCodecSelector emptyList()

            val isHevc = mimeType.equals(MimeTypes.VIDEO_H265, ignoreCase = true) ||
                    mimeType.equals("video/hevc", ignoreCase = true) ||
                    mimeType.contains("hevc", ignoreCase = true) ||
                    mimeType.contains("265", ignoreCase = true)

            if (isHevc) {
                // HEVC Main 10 (10-bit x265) is NOT supported by AOSP software HEVC decoder (OMX.google.hevc.decoder / c2.android.hevc.decoder).
                // On Android 8, AOSP software HEVC decoders accept 10-bit input but stall in buffering forever.
                // Filter out AOSP software HEVC decoders and force non-software/hardware decoders.
                val validDecoders = decoders.filterNot {
                    it.name.startsWith("OMX.google.", ignoreCase = true) ||
                    it.name.startsWith("c2.android.", ignoreCase = true) ||
                    it.name.contains("google", ignoreCase = true)
                }
                if (validDecoders.isNotEmpty()) validDecoders else decoders
            } else {
                when (decoderMode) {
                    DecoderMode.SW -> {
                        val swDecoders = decoders.filter {
                            it.name.startsWith("c2.android.", ignoreCase = true) ||
                            it.name.startsWith("OMX.google.", ignoreCase = true) ||
                            it.name.contains("sw", ignoreCase = true) ||
                            it.name.contains("software", ignoreCase = true)
                        }
                        val hwDecoders = decoders.filterNot { swDecoders.contains(it) }
                        if (swDecoders.isNotEmpty()) swDecoders + hwDecoders else decoders
                    }
                    DecoderMode.HW, DecoderMode.HW_PLUS -> {
                        val hwDecoders = decoders.filter { it.hardwareAccelerated }
                        val swDecoders = decoders.filterNot { it.hardwareAccelerated }
                        if (hwDecoders.isNotEmpty()) hwDecoders + swDecoders else decoders
                    }
                }
            }
        }

        rf.setMediaCodecSelector(nextPlayerMediaCodecSelector)
        rf.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        return rf
    }

    fun playMedia(media: VideoMediaItem, startPositionMs: Long = 0L) {
        fallbackAttempted = false
        val player = getPlayer()
        currentMediaItem = media

        try {
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
        } catch (e: Exception) {
            _playerState.value = _playerState.value.copy(
                errorMessage = "Gagal memuat video: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    private fun createMediaSourceFor(media: VideoMediaItem): MediaSource {
        val extractorsFactory = createExtractorsFactory()

        if (media.isEncrypted1ca || media.streamType == StreamType.VAULT_1CA) {
            val encFactory = EncryptionUtil.getDecryptedStreamDataSourceFactory()
            val mediaItem = MediaItem.fromUri(media.uri)
            return ProgressiveMediaSource.Factory(encFactory, extractorsFactory).createMediaSource(mediaItem)
        }

        if (media.streamType == StreamType.SMB || media.path.startsWith("smb://") || media.uri.scheme == "smb") {
            val smbFactory = SmbDataSource.Factory()
            val mediaItem = MediaItem.fromUri(media.uri)
            return ProgressiveMediaSource.Factory(smbFactory, extractorsFactory).createMediaSource(mediaItem)
        }

        if (media.streamType == StreamType.FTP || media.path.startsWith("ftp://") || media.uri.scheme == "ftp") {
            val ftpFactory = FtpDataSource.Factory()
            val mediaItem = MediaItem.fromUri(media.uri)
            return ProgressiveMediaSource.Factory(ftpFactory, extractorsFactory).createMediaSource(mediaItem)
        }

        if (media.streamType == StreamType.URL_STREAM || media.path.startsWith("http://") || media.path.startsWith("https://")) {
            val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                .setDefaultRequestProperties(
                    mapOf(
                        "Accept" to "*/*",
                        "Connection" to "keep-alive"
                    )
                )
            val defaultFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaItem = MediaItem.fromUri(media.uri)
            return DefaultMediaSourceFactory(context, extractorsFactory)
                .setDataSourceFactory(defaultFactory)
                .createMediaSource(mediaItem)
        }

        val defaultFactory = DefaultDataSource.Factory(context)
        return ProgressiveMediaSource.Factory(defaultFactory, extractorsFactory).createMediaSource(MediaItem.fromUri(media.uri))
    }

    fun switchToDecoder(decoderMode: DecoderMode) {
        val player = exoPlayer
        val currentPos = player?.currentPosition ?: 0L
        val currentPlayWhenReady = player?.playWhenReady ?: true
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
        if (trackInfo.trackGroupIndex < tracks.groups.size) {
            val group = tracks.groups[trackInfo.trackGroupIndex]
            if (trackInfo.trackIndex < group.length) {
                val override = TrackSelectionOverride(group.mediaTrackGroup, trackInfo.trackIndex)
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(override)
                    .build()
            }
        }
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
            if (trackInfo.trackGroupIndex < tracks.groups.size) {
                val group = tracks.groups[trackInfo.trackGroupIndex]
                if (trackInfo.trackIndex < group.length) {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, trackInfo.trackIndex)
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(override)
                        .build()
                }
            }
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
        try {
            exoPlayer?.release()
        } catch (_: Exception) {}
        exoPlayer = null
    }
}
