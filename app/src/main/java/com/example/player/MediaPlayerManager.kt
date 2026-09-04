package com.example.player

import android.content.Context
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
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
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.video.VideoRendererEventListener
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FFmpegOnlyRenderersFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
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
    val videoCodecName: String = "Auto",
    val activeVideoDecoder: String = "Belum terdeteksi",
    val activeAudioDecoder: String = "Belum terdeteksi",
    val videoFormatDetails: String = "Menganalisis stream...",
    val audioFormatDetails: String = "Menganalisis stream...",
    val availableSystemDecoders: List<String> = emptyList(),
    val decoderDebugLogs: List<String> = emptyList(),
    val showDebugDialog: Boolean = false,
    val droppedFramesCount: Int = 0,
    val estimatedBitrateKbps: Long = 0L,
    val firstFrameRendered: Boolean = false,
    val deviceInfo: String = ""
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

    private val debugLogs = mutableListOf<String>()

    companion object {
        private var ffmpegLoaded: Boolean = false
        private var ffmpegLoadError: String? = null

        init {
            ensureFfmpegLoaded()
        }

        fun ensureFfmpegLoaded(): Boolean {
            if (ffmpegLoaded) return true
            try {
                // Pre-load dependent FFmpeg native libraries in topological order for Android 7 & 8 (Oreo) linker compatibility
                val nativeLibs = listOf("avutil", "swresample", "swscale", "avcodec", "media3ext")
                for (lib in nativeLibs) {
                    try {
                        System.loadLibrary(lib)
                    } catch (t: Throwable) {
                        android.util.Log.w("MediaPlayerManager", "loadLibrary($lib): ${t.message}")
                    }
                }
                try {
                    FfmpegLibrary.setLibraries("avutil", "swresample", "swscale", "avcodec", "media3ext")
                } catch (_: Throwable) {}

                ffmpegLoaded = FfmpegLibrary.isAvailable()
                android.util.Log.i("MediaPlayerManager", "FFmpeg native libraries loaded. isAvailable: $ffmpegLoaded")
                return ffmpegLoaded
            } catch (t: Throwable) {
                ffmpegLoadError = t.message
                android.util.Log.e("MediaPlayerManager", "FFmpeg native load error: ${t.message}", t)
                return false
            }
        }

        fun isFfmpegAvailable(): Boolean = ffmpegLoaded || ensureFfmpegLoaded()
    }

    init {
        val devInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
        val systemCodecs = queryAvailableDecoders()
        val ffmpegReady = ensureFfmpegLoaded()
        addDebugLog("[SYSTEM] Perangkat: $devInfo")
        addDebugLog("[SYSTEM] Terdeteksi ${systemCodecs.size} MediaCodec di sistem Android")
        addDebugLog("[FFMPEG] Status Engine FFmpeg SW: ${if (ffmpegReady) "Aktif & Siap (Multi-Threaded) 🚀" else "Gagal memuat native library ($ffmpegLoadError) - Menggunakan OS SW Codecs"}")
        addDebugLog("[ENGINE] MediaPlayer siap dalam mode ${activeDecoderMode.label}")
        _playerState.value = _playerState.value.copy(
            deviceInfo = devInfo,
            availableSystemDecoders = systemCodecs
        )
    }

    fun addDebugLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val logLine = "[$timestamp] $msg"
        android.util.Log.d("MediaPlayerDebug", logLine)
        synchronized(debugLogs) {
            debugLogs.add(0, logLine)
            if (debugLogs.size > 120) debugLogs.removeAt(debugLogs.size - 1)
        }
        _playerState.value = _playerState.value.copy(decoderDebugLogs = ArrayList(debugLogs))
    }

    fun queryAvailableDecoders(mimeType: String? = null): List<String> {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val result = mutableListOf<String>()
            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue
                val types = info.supportedTypes
                val matches = if (mimeType.isNullOrEmpty()) {
                    types.any { it.startsWith("video/") }
                } else {
                    types.any { it.equals(mimeType, ignoreCase = true) || it.contains(mimeType.replace("video/", ""), ignoreCase = true) }
                }
                if (matches) {
                    val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        info.isHardwareAccelerated
                    } else {
                        !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                        !info.name.startsWith("c2.android.", ignoreCase = true)
                    }
                    val tag = if (isHw) "[HW]" else "[SW]"
                    val typesStr = types.filter { it.startsWith("video/") }.joinToString(", ")
                    result.add("${info.name} $tag ($typesStr)")
                }
            }
            if (result.isEmpty()) listOf("Tidak ditemukan dekoder untuk $mimeType") else result
        } catch (e: Exception) {
            listOf("Gagal membaca MediaCodecList: ${e.message}")
        }
    }

    fun refreshDiagnostics() {
        val systemCodecs = queryAvailableDecoders()
        addDebugLog("[DIAGNOSTIC] Menyegarkan daftar MediaCodec & Status Player...")
        val player = exoPlayer
        if (player != null) {
            val stateName = when (player.playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN"
            }
            addDebugLog("[STATUS] State: $stateName, IsPlaying: ${player.isPlaying}, Buffer: ${player.bufferedPosition}ms / ${player.duration}ms")
        } else {
            addDebugLog("[STATUS] Player belum diinisialisasi")
        }
        _playerState.value = _playerState.value.copy(availableSystemDecoders = systemCodecs)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()

            // Standard modern browser User-Agent to avoid anti-hotlink or bot blocks
            if (original.header("User-Agent") == null) {
                requestBuilder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                )
            }
            if (original.header("Accept") == null) {
                requestBuilder.header("Accept", "*/*")
            }
            if (original.header("Accept-Language") == null) {
                requestBuilder.header("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
            }
            if (original.header("Connection") == null) {
                requestBuilder.header("Connection", "keep-alive")
            }

            // Auto-inject Referer & Origin for CDN hotlink protection bypass
            if (original.header("Referer") == null) {
                try {
                    val host = original.url.host
                    val scheme = original.url.scheme
                    requestBuilder.header("Referer", "$scheme://$host/")
                    requestBuilder.header("Origin", "$scheme://$host")
                } catch (_: Exception) {}
            }

            chain.proceed(requestBuilder.build())
        }
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
            exoPlayer?.let { p ->
                p.stop()
                p.clearVideoSurface()
                p.clearMediaItems()
                p.release()
            }
        } catch (_: Throwable) {}
        exoPlayer = null

        activeDecoderMode = decoderMode

        val renderersFactory = createRenderersFactory(decoderMode)

        // Load control tuned for instant playback start and smooth buffering without getting stuck
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1500,  // min buffer 1.5s
                30000, // max buffer 30s
                200,   // buffer for playback 200ms (instant start like MX Player)
                500    // buffer for rebuffering 500ms
            )
            .setBackBuffer(10000, true)
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

        addDebugLog("[PLAYER] ExoPlayer diinisialisasi dalam mode ${decoderMode.label}")

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                val hwTag = when {
                    decoderName.contains("ffmpeg", ignoreCase = true) ||
                    decoderName.contains("libvpx", ignoreCase = true) -> "[SW-FFmpeg]"
                    decoderName.startsWith("OMX.google.", ignoreCase = true) ||
                    decoderName.startsWith("c2.android.", ignoreCase = true) ||
                    decoderName.contains("sw", ignoreCase = true) -> "[SW-Android]"
                    else -> "[HW]"
                }
                val label = "$decoderName $hwTag (Init: ${initializationDurationMs}ms)"
                addDebugLog("[DECODER] Video -> $label")
                _playerState.value = _playerState.value.copy(activeVideoDecoder = label)
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                val tag = when {
                    decoderName.contains("ffmpeg", ignoreCase = true) -> "[SW-FFmpeg]"
                    decoderName.startsWith("OMX.google.", ignoreCase = true) ||
                    decoderName.startsWith("c2.android.", ignoreCase = true) -> "[SW-Android]"
                    else -> "[HW]"
                }
                val label = "$decoderName $tag (Init: ${initializationDurationMs}ms)"
                addDebugLog("[DECODER] Audio -> $label")
                _playerState.value = _playerState.value.copy(activeAudioDecoder = label)
            }

            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                val fpsStr = if (format.frameRate > 0) "@${format.frameRate.toInt()}fps" else ""
                val details = "MIME=${format.sampleMimeType ?: "unknown"}, ${format.width}x${format.height}$fpsStr, Codec=${format.codecs ?: "-"}"
                addDebugLog("[FORMAT] Video -> $details")
                val available = queryAvailableDecoders(format.sampleMimeType)
                _playerState.value = _playerState.value.copy(
                    videoFormatDetails = details,
                    availableSystemDecoders = available
                )
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                val details = "MIME=${format.sampleMimeType ?: "unknown"}, Ch=${format.channelCount}, ${format.sampleRate}Hz"
                addDebugLog("[FORMAT] Audio -> $details")
                _playerState.value = _playerState.value.copy(audioFormatDetails = details)
            }

            override fun onRenderedFirstFrame(
                eventTime: AnalyticsListener.EventTime,
                output: Any,
                renderTimeMs: Long
            ) {
                addDebugLog("[RENDER] Frame video pertama berhasil ditampilkan ke layar! 🎬")
                _playerState.value = _playerState.value.copy(firstFrameRendered = true, isLoading = false)
            }

            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) {
                val newCount = _playerState.value.droppedFramesCount + droppedFrames
                _playerState.value = _playerState.value.copy(droppedFramesCount = newCount)
                if (droppedFrames > 5) {
                    addDebugLog("[PERF] Frame Drop: $droppedFrames frames terlewat (${elapsedMs}ms)")
                }
            }

            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long
            ) {
                val kbps = bitrateEstimate / 1000
                _playerState.value = _playerState.value.copy(estimatedBitrateKbps = kbps)
            }

            override fun onPlaybackStateChanged(
                eventTime: AnalyticsListener.EventTime,
                state: Int
            ) {
                val stateName = when (state) {
                    Player.STATE_IDLE -> "IDLE (Menganggur)"
                    Player.STATE_BUFFERING -> "BUFFERING (Memuat penyangga data...)"
                    Player.STATE_READY -> "READY (Siap memutar)"
                    Player.STATE_ENDED -> "ENDED (Selesai)"
                    else -> "UNKNOWN ($state)"
                }
                addDebugLog("[STATE] Status Playback -> $stateName")
            }

            override fun onIsPlayingChanged(
                eventTime: AnalyticsListener.EventTime,
                isPlaying: Boolean
            ) {
                addDebugLog("[PLAYING] ${if (isPlaying) "Video diputar (Playing)" else "Video dijeda (Paused)"}")
            }

            override fun onIsLoadingChanged(
                eventTime: AnalyticsListener.EventTime,
                isLoading: Boolean
            ) {
                if (isLoading) {
                    addDebugLog("[BUFFER] Mengunduh/membaca data stream...")
                }
            }

            override fun onPlayerError(
                eventTime: AnalyticsListener.EventTime,
                error: PlaybackException
            ) {
                addDebugLog("[ERROR] ${error.errorCodeName} (${error.errorCode}): ${error.message}")
            }
        })

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
                addDebugLog("[SIZE] Resolusi: ${videoSize.width}x${videoSize.height}")
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTracksList(tracks)
                addDebugLog("[TRACKS] Daftar track diperbarui: ${tracks.groups.size} kelompok track")
            }

            override fun onPlayerError(error: PlaybackException) {
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
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                )

                addDebugLog("[ERROR] PlayerError: isDecoderError=$isDecoderError, isNetworkError=$isNetworkError, code=${error.errorCodeName}")

                if (isDecoderError && activeDecoderMode != DecoderMode.SW && !fallbackAttempted) {
                    addDebugLog("[FALLBACK] Format video/audio tidak didukung hardware hp, otomatis beralih ke mode SW (FFmpeg)...")
                    switchToDecoder(DecoderMode.SW, isUserAction = false)
                } else {
                    val msg = when {
                        httpStatusCode == 404 -> "Video / URL streaming tidak ditemukan di server (HTTP 404 Not Found)."
                        httpStatusCode == 403 -> "Akses streaming ditolak oleh server (HTTP 403 Forbidden). Server membatasi akses URL ini."
                        httpStatusCode in 500..599 -> "Server video sedang mengalami kendala (HTTP $httpStatusCode Server Error)."
                        httpStatusCode > 0 -> "Gagal memuat URL streaming (HTTP $httpStatusCode)."
                        isNetworkError -> "Gagal menghubungkan ke server streaming. Periksa koneksi internet Anda."
                        isDecoderError -> "Format/codec tidak didukung oleh hardware. Aktifkan mode SW (Software Decoder) untuk memutar via FFmpeg."
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
        val customMediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoders = try {
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            } catch (_: Exception) {
                emptyList()
            }
            if (decoders.isEmpty()) return@MediaCodecSelector emptyList()

            when (decoderMode) {
                DecoderMode.SW -> {
                    val swDecoders = decoders.filter {
                        !it.hardwareAccelerated ||
                        it.name.startsWith("c2.android.", ignoreCase = true) ||
                        it.name.startsWith("OMX.google.", ignoreCase = true) ||
                        it.name.contains("sw", ignoreCase = true) ||
                        it.name.contains("software", ignoreCase = true)
                    }
                    if (swDecoders.isNotEmpty()) {
                        swDecoders + decoders.filterNot { swDecoders.contains(it) }
                    } else {
                        decoders
                    }
                }
                DecoderMode.HW, DecoderMode.HW_PLUS -> {
                    val hwDecoders = decoders.filter { it.hardwareAccelerated }
                    val swDecoders = decoders.filterNot { it.hardwareAccelerated }
                    if (hwDecoders.isNotEmpty()) hwDecoders + swDecoders else decoders
                }
            }
        }

        return object : DefaultRenderersFactory(context) {
            init {
                setEnableDecoderFallback(true)
                setAllowedVideoJoiningTimeMs(5000)
                setMediaCodecSelector(customMediaCodecSelector)
                setExtensionRendererMode(
                    if (decoderMode == DecoderMode.SW) EXTENSION_RENDERER_MODE_PREFER
                    else EXTENSION_RENDERER_MODE_ON
                )
            }

            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: java.util.ArrayList<Renderer>
            ) {
                when (decoderMode) {
                    DecoderMode.SW -> {
                        // MX Player style SW decoder: FFmpeg multi-threaded software decoding first!
                        if (isFfmpegAvailable()) {
                            try {
                                val availableCores = Runtime.getRuntime().availableProcessors()
                                val threads = maxOf(2, minOf(4, availableCores))
                                out.add(
                                    FfmpegVideoRenderer(
                                        allowedVideoJoiningTimeMs,
                                        eventHandler,
                                        eventListener,
                                        50,
                                        threads,
                                        8,
                                        8
                                    )
                                )
                                addDebugLog("[RENDERER] SW Mode: FfmpegVideoRenderer aktif ($threads threads) 🚀")
                            } catch (t: Throwable) {
                                addDebugLog("[RENDERER] Fallback basic FfmpegVideoRenderer: ${t.message}")
                                try {
                                    out.add(FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, 50))
                                } catch (t2: Throwable) {
                                    addDebugLog("[RENDERER] Gagal instansiasi FfmpegVideoRenderer: ${t2.message}")
                                }
                            }
                        } else {
                            addDebugLog("[RENDERER] FFmpeg library tidak aktif, menggunakan decoder software sistem Android (c2/OMX)")
                        }

                        // ALWAYS add Android software/hardware video renderers as reliable secondary fallback!
                        try {
                            super.buildVideoRenderers(
                                context,
                                EXTENSION_RENDERER_MODE_OFF,
                                mediaCodecSelector,
                                enableDecoderFallback,
                                eventHandler,
                                eventListener,
                                allowedVideoJoiningTimeMs,
                                out
                            )
                        } catch (t: Throwable) {
                            addDebugLog("[RENDERER] Error buildVideoRenderers super: ${t.message}")
                        }
                    }
                    DecoderMode.HW -> {
                        // Primary: Hardware accelerated MediaCodec
                        try {
                            super.buildVideoRenderers(
                                context,
                                EXTENSION_RENDERER_MODE_OFF,
                                mediaCodecSelector,
                                enableDecoderFallback,
                                eventHandler,
                                eventListener,
                                allowedVideoJoiningTimeMs,
                                out
                            )
                        } catch (t: Throwable) {
                            addDebugLog("[RENDERER] Error buildVideoRenderers HW: ${t.message}")
                        }
                        // Fallback: FFmpeg Video Renderer if phone hardware cannot decode format
                        if (isFfmpegAvailable()) {
                            try {
                                val availableCores = Runtime.getRuntime().availableProcessors()
                                val threads = maxOf(2, minOf(4, availableCores))
                                out.add(
                                    FfmpegVideoRenderer(
                                        allowedVideoJoiningTimeMs,
                                        eventHandler,
                                        eventListener,
                                        50,
                                        threads,
                                        4,
                                        4
                                    )
                                )
                                addDebugLog("[RENDERER] HW Mode: Hardware decoder + FFmpeg fallback siap")
                            } catch (_: Throwable) {}
                        }
                    }
                    DecoderMode.HW_PLUS -> {
                        // HW+: Hardware Video with FFmpeg video fallback
                        try {
                            super.buildVideoRenderers(
                                context,
                                EXTENSION_RENDERER_MODE_OFF,
                                mediaCodecSelector,
                                enableDecoderFallback,
                                eventHandler,
                                eventListener,
                                allowedVideoJoiningTimeMs,
                                out
                            )
                        } catch (t: Throwable) {
                            addDebugLog("[RENDERER] Error buildVideoRenderers HW+: ${t.message}")
                        }
                        if (isFfmpegAvailable()) {
                            try {
                                out.add(FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, 50))
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }

            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: android.os.Handler,
                eventListener: AudioRendererEventListener,
                out: java.util.ArrayList<Renderer>
            ) {
                when (decoderMode) {
                    DecoderMode.SW, DecoderMode.HW_PLUS -> {
                        // SW & HW+: FFmpeg Audio Renderer first (supports AC3, EAC3, DTS, TrueHD, FLAC, Opus, etc.)
                        if (isFfmpegAvailable()) {
                            try {
                                out.add(FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
                                addDebugLog("[RENDERER] ${decoderMode.label}: FfmpegAudioRenderer aktif 🔊")
                            } catch (t: Throwable) {
                                addDebugLog("[RENDERER] Gagal memuat FfmpegAudioRenderer: ${t.message}")
                            }
                        }
                        try {
                            super.buildAudioRenderers(
                                context,
                                EXTENSION_RENDERER_MODE_OFF,
                                mediaCodecSelector,
                                enableDecoderFallback,
                                audioSink,
                                eventHandler,
                                eventListener,
                                out
                            )
                        } catch (t: Throwable) {
                            addDebugLog("[RENDERER] Error buildAudioRenderers super: ${t.message}")
                        }
                    }
                    DecoderMode.HW -> {
                        // HW: System Audio first, then FFmpeg for unsupported formats (e.g. AC3/DTS)
                        try {
                            super.buildAudioRenderers(
                                context,
                                EXTENSION_RENDERER_MODE_OFF,
                                mediaCodecSelector,
                                enableDecoderFallback,
                                audioSink,
                                eventHandler,
                                eventListener,
                                out
                            )
                        } catch (t: Throwable) {
                            addDebugLog("[RENDERER] Error buildAudioRenderers HW: ${t.message}")
                        }
                        if (isFfmpegAvailable()) {
                            try {
                                out.add(FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        }
    }

    fun playMedia(media: VideoMediaItem, startPositionMs: Long = 0L) {
        fallbackAttempted = false
        playMediaInternal(media, startPositionMs)
    }

    private fun playMediaInternal(media: VideoMediaItem, startPositionMs: Long = 0L) {
        val player = getPlayer()
        currentMediaItem = media

        addDebugLog("[LOAD] Memuat Media: '${media.title}' [MIME: ${media.mimeType}, StreamType: ${media.streamType}]")
        addDebugLog("[LOAD] URI: ${media.uri}")
        if (startPositionMs > 0) {
            addDebugLog("[LOAD] Melanjutkan dari posisi: ${startPositionMs}ms")
        }

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
            addDebugLog("[LOAD_ERROR] Gagal memuat MediaSource: ${e.message}")
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
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(
                    mapOf(
                        "Accept" to "*/*",
                        "Accept-Language" to "en-US,en;q=0.9,id;q=0.8",
                        "Connection" to "keep-alive"
                    )
                )
            val defaultFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            val uriStr = media.uri.toString().lowercase()
            val mediaItemBuilder = MediaItem.Builder().setUri(media.uri)

            when {
                uriStr.contains(".m3u8") || media.mimeType.contains("mpegurl", ignoreCase = true) || media.mimeType.contains("application/x-mpegurl", ignoreCase = true) -> {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                uriStr.contains(".mpd") || media.mimeType.contains("dash", ignoreCase = true) -> {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                }
                uriStr.contains(".ism") -> {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_SS)
                }
            }

            return DefaultMediaSourceFactory(context, extractorsFactory)
                .setDataSourceFactory(defaultFactory)
                .createMediaSource(mediaItemBuilder.build())
        }

        val defaultFactory = DefaultDataSource.Factory(context)
        val mediaItemBuilder = MediaItem.Builder().setUri(media.uri)
        if (media.mimeType.isNotBlank() && media.mimeType != "video/*") {
            mediaItemBuilder.setMimeType(media.mimeType)
        }
        return DefaultMediaSourceFactory(defaultFactory, extractorsFactory)
            .createMediaSource(mediaItemBuilder.build())
    }

    fun switchToDecoder(decoderMode: DecoderMode, isUserAction: Boolean = false) {
        val player = exoPlayer
        val currentPos = player?.currentPosition ?: 0L
        val currentPlayWhenReady = player?.playWhenReady ?: true
        val media = currentMediaItem

        addDebugLog("[SWITCH] Mengganti mode dekoder ke ${decoderMode.label} (UserAction: $isUserAction)")

        if (!isUserAction) {
            fallbackAttempted = true
        } else {
            fallbackAttempted = false
        }

        try {
            initializePlayer(decoderMode)
            if (media != null) {
                playMediaInternal(media, currentPos)
                exoPlayer?.playWhenReady = currentPlayWhenReady
            }
        } catch (t: Throwable) {
            addDebugLog("[SWITCH_ERROR] Gagal beralih ke mode ${decoderMode.label}: ${t.message}")
            if (decoderMode != DecoderMode.HW) {
                try {
                    addDebugLog("[FALLBACK] Mengembalikan ke mode Hardware (HW) agar pemutaran tidak terhenti...")
                    initializePlayer(DecoderMode.HW)
                    if (media != null) {
                        playMediaInternal(media, currentPos)
                        exoPlayer?.playWhenReady = currentPlayWhenReady
                    }
                } catch (_: Throwable) {}
            }
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
        switchToDecoder(next, isUserAction = true)
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

    fun setDebugDialogVisible(visible: Boolean) {
        _playerState.value = _playerState.value.copy(showDebugDialog = visible)
    }

    fun toggleDebugDialog() {
        setDebugDialogVisible(!_playerState.value.showDebugDialog)
    }

    fun clearDebugLogs() {
        synchronized(debugLogs) {
            debugLogs.clear()
        }
        _playerState.value = _playerState.value.copy(decoderDebugLogs = emptyList())
    }

    fun release() {
        try {
            exoPlayer?.release()
        } catch (_: Exception) {}
        exoPlayer = null
    }
}
