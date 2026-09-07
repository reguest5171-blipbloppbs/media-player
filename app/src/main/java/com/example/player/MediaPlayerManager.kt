package com.example.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.view.Surface
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
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as LibVlcMediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
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
    val decoderMode: DecoderMode = DecoderMode.HW_PLUS,
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
    val deviceInfo: String = "",
    val subtitleOffsetDp: Int = 24,
    val subtitleDelayMs: Long = 0L,
    val subtitleCuesText: String = "",
    val externalSubtitleName: String? = null,
    val externalAudioName: String? = null
)

@OptIn(UnstableApi::class)
class OptimizedRenderersFactory(
    private val context: Context,
    private val decoderMode: DecoderMode,
    private val customMediaCodecSelector: MediaCodecSelector
) : DefaultRenderersFactory(context) {

    init {
        setEnableDecoderFallback(true)
        setAllowedVideoJoiningTimeMs(5000L)
        setMediaCodecSelector(customMediaCodecSelector)
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
        val ffmpegAvailable = FfmpegLibrary.isAvailable()
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
        val numInputBuffers = 64
        val numOutputBuffers = 64

        if ((decoderMode == DecoderMode.FFMPEG || decoderMode == DecoderMode.SW) && ffmpegAvailable) {
            // Software mode (FFmpeg Engine): FFmpeg video renderer FIRST
            try {
                val ffmpegVideoRenderer = FfmpegVideoRenderer(
                    allowedVideoJoiningTimeMs,
                    eventHandler,
                    eventListener,
                    50
                )
                out.add(ffmpegVideoRenderer)
            } catch (e: Exception) {
                android.util.Log.e("OptimizedRenderers", "Error creating FfmpegVideoRenderer", e)
            }
            // Add hardware decoder as fallback
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
        } else {
            // Mode HW, HW+ (Google Android): 100% Menggunakan MediaCodec Android Asli (Tanpa FFmpeg)
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
        val ffmpegAvailable = FfmpegLibrary.isAvailable()

        if ((decoderMode == DecoderMode.FFMPEG || decoderMode == DecoderMode.SW) && ffmpegAvailable) {
            // FFmpeg audio renderer FIRST
            try {
                val ffmpegAudioRenderer = FfmpegAudioRenderer(
                    eventHandler,
                    eventListener,
                    audioSink
                )
                out.add(ffmpegAudioRenderer)
            } catch (e: Exception) {
                android.util.Log.e("OptimizedRenderers", "Error creating FfmpegAudioRenderer", e)
            }
            // Add system audio decoder as fallback
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
        } else {
            // Mode HW, HW+, SW (Google): 100% Android System Audio Track (Tanpa FFmpeg)
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
        }
    }
}

class SystemPlayerEngine(
    private val context: Context,
    private val onStateUpdate: (isPlaying: Boolean, isLoading: Boolean, currentPosMs: Long, durationMs: Long, bufferedPosMs: Long, width: Int, height: Int, firstFrame: Boolean, error: String?) -> Unit,
    private val onCompletion: () -> Unit,
    private val onDebugLog: (String) -> Unit
) {
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var surface: Surface? = null
    private var isPrepared = false
    private var pendingSeekPositionMs: Long = 0L
    private var targetPlaybackSpeed: Float = 1.0f

    fun setSurface(newSurface: Surface?) {
        surface = newSurface
        try {
            mediaPlayer?.setSurface(newSurface)
            if (newSurface != null) {
                onDebugLog("[SYSTEM_MP] Surface video terhubung ke Mesin MediaPlayer Sistem Android")
            }
        } catch (e: Exception) {
            onDebugLog("[SYSTEM_MP_ERROR] Gagal setSurface: ${e.message}")
        }
    }

    fun playMedia(media: VideoMediaItem, startPositionMs: Long) {
        release()
        pendingSeekPositionMs = startPositionMs
        onDebugLog("[SYSTEM_MP] Memulai pemutaran Mesin Bawaan Android (NuPlayer/Stagefright): '${media.title}'")
        onStateUpdate(false, true, startPositionMs, 0L, 0L, 0, 0, false, null)

        try {
            val mp = android.media.MediaPlayer()
            mediaPlayer = mp

            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )

            surface?.let {
                if (it.isValid) {
                    mp.setSurface(it)
                }
            }

            mp.setDataSource(context, media.uri)

            mp.setOnPreparedListener { player ->
                isPrepared = true
                val duration = player.duration.toLong().coerceAtLeast(0L)
                val width = player.videoWidth
                val height = player.videoHeight
                onDebugLog("[SYSTEM_MP_PREPARED] Video siap di Mesin Sistem Android! Durasi: ${duration}ms, Resolusi: ${width}x${height}")

                if (pendingSeekPositionMs > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        player.seekTo(pendingSeekPositionMs, android.media.MediaPlayer.SEEK_CLOSEST)
                    } else {
                        player.seekTo(pendingSeekPositionMs.toInt())
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && targetPlaybackSpeed != 1.0f) {
                    try {
                        player.playbackParams = android.media.PlaybackParams().apply { speed = targetPlaybackSpeed }
                    } catch (_: Exception) {}
                }

                player.start()
                onStateUpdate(true, false, pendingSeekPositionMs, duration, duration, width, height, true, null)
            }

            mp.setOnVideoSizeChangedListener { _, width, height ->
                if (width > 0 && height > 0) {
                    onDebugLog("[SYSTEM_MP] Dimensi video terdeteksi: ${width}x${height}")
                    val dur = mp.duration.toLong().coerceAtLeast(0L)
                    val pos = mp.currentPosition.toLong().coerceAtLeast(0L)
                    onStateUpdate(mp.isPlaying, false, pos, dur, dur, width, height, true, null)
                }
            }

            mp.setOnInfoListener { _, what, _ ->
                when (what) {
                    android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                        onDebugLog("[SYSTEM_MP] Frame pertama dirender (MEDIA_INFO_VIDEO_RENDERING_START) 🎉")
                        val dur = mp.duration.toLong().coerceAtLeast(0L)
                        val pos = mp.currentPosition.toLong().coerceAtLeast(0L)
                        onStateUpdate(mp.isPlaying, false, pos, dur, dur, mp.videoWidth, mp.videoHeight, true, null)
                        true
                    }
                    android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                        onStateUpdate(mp.isPlaying, true, mp.currentPosition.toLong(), mp.duration.toLong(), 0L, mp.videoWidth, mp.videoHeight, true, null)
                        true
                    }
                    android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                        onStateUpdate(mp.isPlaying, false, mp.currentPosition.toLong(), mp.duration.toLong(), 0L, mp.videoWidth, mp.videoHeight, true, null)
                        true
                    }
                    else -> false
                }
            }

            mp.setOnBufferingUpdateListener { _, percent ->
                val dur = mp.duration.toLong().coerceAtLeast(0L)
                val buffered = (dur * percent) / 100
                val pos = mp.currentPosition.toLong().coerceAtLeast(0L)
                onStateUpdate(mp.isPlaying, false, pos, dur, buffered, mp.videoWidth, mp.videoHeight, true, null)
            }

            mp.setOnCompletionListener {
                onDebugLog("[SYSTEM_MP] Pemutaran video selesai")
                onCompletion()
            }

            mp.setOnErrorListener { _, what, extra ->
                onDebugLog("[SYSTEM_MP_ERROR] Error pemutaran sistem: what=$what, extra=$extra")
                val errorMsg = "Mesin Sistem Android mengalami error ($what, $extra). Silakan coba beralih ke Mode HW atau SW."
                onStateUpdate(false, false, 0L, 0L, 0L, 0, 0, false, errorMsg)
                true
            }

            mp.prepareAsync()
        } catch (e: Exception) {
            onDebugLog("[SYSTEM_MP_EXCEPTION] Gagal inisialisasi: ${e.message}")
            onStateUpdate(false, false, 0L, 0L, 0L, 0, 0, false, "Gagal memuat Mesin Sistem: ${e.message}")
        }
    }

    fun play() {
        try {
            if (isPrepared) {
                mediaPlayer?.start()
                val pos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                val dur = mediaPlayer?.duration?.toLong() ?: 0L
                onStateUpdate(true, false, pos, dur, dur, mediaPlayer?.videoWidth ?: 0, mediaPlayer?.videoHeight ?: 0, true, null)
            }
        } catch (_: Exception) {}
    }

    fun pause() {
        try {
            if (isPrepared) {
                mediaPlayer?.pause()
                val pos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                val dur = mediaPlayer?.duration?.toLong() ?: 0L
                onStateUpdate(false, false, pos, dur, dur, mediaPlayer?.videoWidth ?: 0, mediaPlayer?.videoHeight ?: 0, true, null)
            }
        } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        if (mediaPlayer?.isPlaying == true) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        try {
            if (isPrepared) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mediaPlayer?.seekTo(positionMs, android.media.MediaPlayer.SEEK_CLOSEST)
                } else {
                    mediaPlayer?.seekTo(positionMs.toInt())
                }
                val dur = mediaPlayer?.duration?.toLong() ?: 0L
                onStateUpdate(mediaPlayer?.isPlaying == true, false, positionMs, dur, dur, mediaPlayer?.videoWidth ?: 0, mediaPlayer?.videoHeight ?: 0, true, null)
            } else {
                pendingSeekPositionMs = positionMs
            }
        } catch (_: Exception) {}
    }

    fun setPlaybackSpeed(speed: Float) {
        targetPlaybackSpeed = speed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isPrepared) {
            try {
                mediaPlayer?.playbackParams = android.media.PlaybackParams().apply { this.speed = speed }
            } catch (_: Exception) {}
        }
    }

    fun setVolume(volume: Float) {
        try {
            mediaPlayer?.setVolume(volume, volume)
        } catch (_: Exception) {}
    }

    fun pollProgress() {
        val mp = mediaPlayer ?: return
        if (isPrepared) {
            try {
                val pos = mp.currentPosition.toLong().coerceAtLeast(0L)
                val dur = mp.duration.toLong().coerceAtLeast(0L)
                val isPlaying = mp.isPlaying
                onStateUpdate(isPlaying, false, pos, dur, dur, mp.videoWidth, mp.videoHeight, true, null)
            } catch (_: Exception) {}
        }
    }

    fun release() {
        isPrepared = false
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }
}

class VlcPlayerEngine(
    private val context: Context,
    private val onStateUpdate: (isPlaying: Boolean, isLoading: Boolean, currentPosMs: Long, durationMs: Long, bufferedPosMs: Long, width: Int, height: Int, firstFrame: Boolean, error: String?) -> Unit,
    private val onCompletion: () -> Unit,
    private val onDebugLog: (String) -> Unit,
    private val onTracksAvailable: ((subtitles: List<PlayerTrackInfo>, audio: List<PlayerTrackInfo>) -> Unit)? = null
) {
    private var libVLC: LibVLC? = null
    private var mediaPlayer: LibVlcMediaPlayer? = null
    private var isPlaying = false
    private var durationMs: Long = 0L
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var pendingSeekPositionMs: Long = 0L
    private var attachedSurfaceView: android.view.SurfaceView? = null
    private var currentPfd: android.os.ParcelFileDescriptor? = null

    init {
        try {
            val options = arrayListOf(
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--network-caching=3000",
                "--file-caching=3000",
                "--avcodec-hw=none",
                "--avcodec-threads=0",
                "-vv"
            )
            libVLC = LibVLC(context, options)
            onDebugLog("[VLC_ENGINE] Mesin LibVLC C++ Native Berhasil Diinisialisasi 🚀")
        } catch (e: Throwable) {
            onDebugLog("[VLC_ENGINE_ERROR] Gagal inisialisasi LibVLC: ${e.message}")
        }
    }

    fun attachVout(surfaceView: android.view.SurfaceView, width: Int = 0, height: Int = 0) {
        try {
            attachedSurfaceView = surfaceView
            val mp = mediaPlayer ?: return
            val vout = mp.vlcVout
            val w = if (width > 0) width else surfaceView.width
            val h = if (height > 0) height else surfaceView.height
            if (w > 0 && h > 0) {
                vout.setWindowSize(w, h)
            }
            if (!vout.areViewsAttached()) {
                vout.setVideoView(surfaceView)
                vout.attachViews()
                onDebugLog("[VLC_ENGINE] Video view berhasil di-attach ke LibVLC Native Vout (${w}x${h}px) 📺")
            } else if (w > 0 && h > 0) {
                vout.setWindowSize(w, h)
            }
        } catch (e: Exception) {
            onDebugLog("[VLC_ENGINE_ERROR] attachVout gagal: ${e.message}")
        }
    }

    fun updateWindowSize(width: Int, height: Int) {
        try {
            if (width > 0 && height > 0) {
                mediaPlayer?.vlcVout?.setWindowSize(width, height)
            }
        } catch (_: Throwable) {}
    }

    fun detachVout() {
        try {
            attachedSurfaceView = null
            mediaPlayer?.vlcVout?.detachViews()
            onDebugLog("[VLC_ENGINE] Video view di-detach dari LibVLC")
        } catch (_: Exception) {}
    }

    fun playMedia(mediaItem: VideoMediaItem, startPositionMs: Long = 0L) {
        pendingSeekPositionMs = startPositionMs
        onDebugLog("[VLC_ENGINE] Memulai LibVLC SW C++ Engine: '${mediaItem.title}' [Start: ${startPositionMs}ms]")
        onStateUpdate(false, true, startPositionMs, 0L, 0L, 0, 0, false, null)

        try {
            val vlc = libVLC ?: run {
                val opts = arrayListOf(
                    "--no-drop-late-frames",
                    "--network-caching=3000",
                    "--file-caching=3000",
                    "--avcodec-hw=none",
                    "--avcodec-threads=0"
                )
                LibVLC(context, opts).also { libVLC = it }
            }

            try {
                mediaPlayer?.stop()
                mediaPlayer?.vlcVout?.detachViews()
                mediaPlayer?.release()
            } catch (_: Throwable) {}

            try {
                currentPfd?.close()
            } catch (_: Throwable) {}
            currentPfd = null

            val mp = LibVlcMediaPlayer(vlc)
            mediaPlayer = mp

            // Re-attach surface if SurfaceView is available
            attachedSurfaceView?.let { sv ->
                try {
                    val vout = mp.vlcVout
                    val w = sv.width
                    val h = sv.height
                    if (w > 0 && h > 0) {
                        vout.setWindowSize(w, h)
                    }
                    if (!vout.areViewsAttached()) {
                        vout.setVideoView(sv)
                        vout.attachViews()
                        onDebugLog("[VLC_ENGINE] Surface re-attached ke mediaPlayer baru (${w}x${h}px) 📺")
                    } else if (w > 0 && h > 0) {
                        vout.setWindowSize(w, h)
                    }
                } catch (e: Throwable) {
                    onDebugLog("[VLC_ENGINE_ERROR] Re-attach surface gagal: ${e.message}")
                }
            }

            val media = try {
                if (mediaItem.uri.scheme == "content" || mediaItem.uri.scheme == "file") {
                    val pfd = try { context.contentResolver.openFileDescriptor(mediaItem.uri, "r") } catch (_: Throwable) { null }
                    if (pfd != null) {
                        currentPfd = pfd
                        Media(vlc, pfd.fileDescriptor)
                    } else {
                        Media(vlc, mediaItem.uri)
                    }
                } else {
                    Media(vlc, mediaItem.uri)
                }
            } catch (_: Throwable) {
                Media(vlc, mediaItem.uri)
            }

            // Force SW Decoding in VLC C++
            media.setHWDecoderEnabled(false, false)
            media.addOption(":file-caching=3000")
            media.addOption(":network-caching=3000")
            media.addOption(":clock-jitter=0")
            media.addOption(":clock-synchro=0")
            media.addOption(":avcodec-threads=0")
            
            mp.media = media
            media.release()

            mp.setEventListener { event ->
                when (event.type) {
                    LibVlcMediaPlayer.Event.Opening -> {
                        onDebugLog("[VLC_EVENT] Opening stream...")
                        onStateUpdate(false, true, 0L, 0L, 0L, videoWidth, videoHeight, false, null)
                    }
                    LibVlcMediaPlayer.Event.Playing -> {
                        isPlaying = true
                        onDebugLog("[VLC_EVENT] Playing (LibVLC C++ SW Decoding Active) 🟢")
                        if (pendingSeekPositionMs > 0) {
                            mp.time = pendingSeekPositionMs
                            pendingSeekPositionMs = 0L
                        }
                        val dur = mp.length.coerceAtLeast(0L)
                        val pos = mp.time.coerceAtLeast(0L)
                        onStateUpdate(true, false, pos, dur, dur, videoWidth, videoHeight, true, null)
                        try {
                            val subs = getSubtitleTracks()
                            val auds = getAudioTracks()
                            if (subs.isNotEmpty() || auds.isNotEmpty()) {
                                onTracksAvailable?.invoke(subs, auds)
                            }
                        } catch (_: Throwable) {}
                    }
                    LibVlcMediaPlayer.Event.Paused -> {
                        isPlaying = false
                        val dur = mp.length.coerceAtLeast(0L)
                        val pos = mp.time.coerceAtLeast(0L)
                        onStateUpdate(false, false, pos, dur, dur, videoWidth, videoHeight, true, null)
                    }
                    LibVlcMediaPlayer.Event.Stopped -> {
                        isPlaying = false
                        onStateUpdate(false, false, 0L, durationMs, durationMs, videoWidth, videoHeight, false, null)
                    }
                    LibVlcMediaPlayer.Event.EndReached -> {
                        isPlaying = false
                        onCompletion()
                    }
                    LibVlcMediaPlayer.Event.EncounteredError -> {
                        isPlaying = false
                        onDebugLog("[VLC_EVENT_ERROR] LibVLC menemukan error pada media stream")
                        onStateUpdate(false, false, 0L, 0L, 0L, 0, 0, false, "LibVLC Engine Error pada decoding stream")
                    }
                    LibVlcMediaPlayer.Event.Vout -> {
                        val tracks = mp.currentVideoTrack
                        if (tracks != null) {
                            videoWidth = tracks.width
                            videoHeight = tracks.height
                            onDebugLog("[VLC_EVENT_VOUT] Resolusi terdeteksi oleh LibVLC: ${videoWidth}x${videoHeight}")
                        }
                        try {
                            val subs = getSubtitleTracks()
                            val auds = getAudioTracks()
                            if (subs.isNotEmpty() || auds.isNotEmpty()) {
                                onTracksAvailable?.invoke(subs, auds)
                            }
                        } catch (_: Throwable) {}
                    }
                    LibVlcMediaPlayer.Event.TimeChanged -> {
                        val dur = mp.length.coerceAtLeast(0L)
                        val pos = mp.time.coerceAtLeast(0L)
                        durationMs = dur
                        onStateUpdate(isPlaying, false, pos, dur, dur, videoWidth, videoHeight, true, null)
                    }
                }
            }

            mp.play()
        } catch (e: Throwable) {
            onDebugLog("[VLC_ENGINE_ERROR] Gagal memutar di LibVLC: ${e.message}")
            onStateUpdate(false, false, 0L, 0L, 0L, 0, 0, false, "Gagal memuat video di LibVLC: ${e.message}")
        }
    }

    fun play() {
        try {
            mediaPlayer?.play()
            isPlaying = true
        } catch (_: Exception) {}
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            isPlaying = false
        } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.time = positionMs
            val dur = mediaPlayer?.length ?: durationMs
            onStateUpdate(isPlaying, false, positionMs, dur, dur, videoWidth, videoHeight, true, null)
        } catch (_: Exception) {}
    }

    fun setPlaybackSpeed(speed: Float) {
        try {
            mediaPlayer?.setRate(speed)
        } catch (_: Exception) {}
    }

    fun setVolume(volume: Float) {
        try {
            val vlcVol = (volume * 100).toInt().coerceIn(0, 100)
            mediaPlayer?.setVolume(vlcVol)
        } catch (_: Exception) {}
    }

    fun pollProgress() {
        val mp = mediaPlayer ?: return
        try {
            val pos = mp.time.coerceAtLeast(0L)
            val dur = mp.length.coerceAtLeast(0L)
            onStateUpdate(mp.isPlaying, false, pos, dur, dur, videoWidth, videoHeight, true, null)
        } catch (_: Exception) {}
    }

    fun addSlaveSubtitle(uriString: String) {
        try {
            val uri = if (uriString.startsWith("http://") || uriString.startsWith("https://") || uriString.startsWith("file://")) {
                Uri.parse(uriString)
            } else {
                Uri.fromFile(java.io.File(uriString))
            }
            mediaPlayer?.addSlave(0, uri, true) // 0 = IMedia.Slave.Type.Subtitle
            onDebugLog("[VLC_ENGINE] Slave subtitle ditambahkan ke VLC: $uriString")
        } catch (e: Exception) {
            onDebugLog("[VLC_ENGINE_ERROR] Gagal menambah slave subtitle: ${e.message}")
        }
    }

    fun addSlaveAudio(uriString: String) {
        try {
            val uri = if (uriString.startsWith("http://") || uriString.startsWith("https://") || uriString.startsWith("file://")) {
                Uri.parse(uriString)
            } else {
                Uri.fromFile(java.io.File(uriString))
            }
            mediaPlayer?.addSlave(1, uri, true) // 1 = IMedia.Slave.Type.Audio
            onDebugLog("[VLC_ENGINE] Slave audio ditambahkan ke VLC: $uriString")
        } catch (e: Exception) {
            onDebugLog("[VLC_ENGINE_ERROR] Gagal menambah slave audio: ${e.message}")
        }
    }

    fun getSubtitleTracks(): List<PlayerTrackInfo> {
        val mp = mediaPlayer ?: return emptyList()
        val currentSpu = mp.spuTrack
        val tracks = mp.spuTracks ?: return emptyList()
        return tracks.filter { it.id != -1 }.mapIndexed { index, td ->
            PlayerTrackInfo(
                id = "vlc_spu_${td.id}",
                label = td.name ?: "Subtitle ${index + 1}",
                language = "",
                isSelected = td.id == currentSpu,
                trackGroupIndex = 0,
                trackIndex = td.id
            )
        }
    }

    fun getAudioTracks(): List<PlayerTrackInfo> {
        val mp = mediaPlayer ?: return emptyList()
        val currentAudio = mp.audioTrack
        val tracks = mp.audioTracks ?: return emptyList()
        return tracks.filter { it.id != -1 }.mapIndexed { index, td ->
            PlayerTrackInfo(
                id = "vlc_audio_${td.id}",
                label = td.name ?: "Audio ${index + 1}",
                language = "",
                isSelected = td.id == currentAudio,
                trackGroupIndex = 0,
                trackIndex = td.id
            )
        }
    }

    fun selectSubtitleTrack(trackId: Int) {
        try {
            mediaPlayer?.spuTrack = trackId
            onDebugLog("[VLC_ENGINE] Subtitle track disetel ke ID: $trackId")
        } catch (e: Exception) {
            onDebugLog("[VLC_ENGINE_ERROR] Gagal set spuTrack: ${e.message}")
        }
    }

    fun selectAudioTrack(trackId: Int) {
        try {
            mediaPlayer?.audioTrack = trackId
            onDebugLog("[VLC_ENGINE] Audio track disetel ke ID: $trackId")
        } catch (e: Exception) {
            onDebugLog("[VLC_ENGINE_ERROR] Gagal set audioTrack: ${e.message}")
        }
    }

    fun setSubtitleDelay(delayMs: Long) {
        try {
            mediaPlayer?.setSpuDelay(delayMs * 1000L)
            onDebugLog("[VLC_ENGINE] Subtitle delay diatur: ${delayMs}ms")
        } catch (e: Exception) {
            onDebugLog("[VLC_ENGINE_ERROR] Gagal set subtitle delay: ${e.message}")
        }
    }

    fun release() {
        try {
            detachVout()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentPfd?.close()
            currentPfd = null
        } catch (_: Exception) {}
    }
}

class MediaPlayerManager(private val context: Context) {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var exoPlayer: ExoPlayer? = null
    private val _activePlayer = MutableStateFlow<ExoPlayer?>(null)
    val activePlayer: StateFlow<ExoPlayer?> = _activePlayer.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val debugLogs = mutableListOf<String>()

    private var currentMediaItem: VideoMediaItem? = null
    private var activeDecoderMode: DecoderMode = DecoderMode.HW_PLUS
    private var fallbackAttempted: Boolean = false
    private var bufferingWatchdogJob: kotlinx.coroutines.Job? = null

    val systemPlayerEngine = SystemPlayerEngine(
        context = context,
        onStateUpdate = { isPlaying, isLoading, currentPos, duration, buffered, width, height, firstFrame, error ->
            _playerState.value = _playerState.value.copy(
                isPlaying = isPlaying,
                isLoading = isLoading,
                currentPositionMs = currentPos,
                durationMs = duration,
                bufferedPositionMs = buffered,
                videoWidth = if (width > 0) width else _playerState.value.videoWidth,
                videoHeight = if (height > 0) height else _playerState.value.videoHeight,
                firstFrameRendered = firstFrame || _playerState.value.firstFrameRendered,
                errorMessage = error
            )
        },
        onCompletion = {
            _playerState.value = _playerState.value.copy(isPlaying = false)
        },
        onDebugLog = { log ->
            addDebugLog(log)
        }
    )

    val vlcPlayerEngine = VlcPlayerEngine(
        context = context,
        onStateUpdate = { isPlaying, isLoading, currentPos, duration, buffered, width, height, firstFrame, error ->
            _playerState.value = _playerState.value.copy(
                isPlaying = isPlaying,
                isLoading = isLoading,
                currentPositionMs = currentPos,
                durationMs = duration,
                bufferedPositionMs = buffered,
                videoWidth = if (width > 0) width else _playerState.value.videoWidth,
                videoHeight = if (height > 0) height else _playerState.value.videoHeight,
                firstFrameRendered = firstFrame || _playerState.value.firstFrameRendered,
                errorMessage = error
            )
        },
        onCompletion = {
            _playerState.value = _playerState.value.copy(isPlaying = false)
        },
        onDebugLog = { log ->
            addDebugLog(log)
        },
        onTracksAvailable = { subs, audios ->
            _playerState.value = _playerState.value.copy(
                subtitleTracks = subs,
                audioTracks = audios
            )
            addDebugLog("[VLC_TRACKS] LibVLC mendeteksi ${subs.size} subtitle dan ${audios.size} audio tracks 🎯")
        }
    )

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
        val logs = debugLogs ?: return
        synchronized(logs) {
            logs.add(0, logLine)
            if (logs.size > 120) logs.removeAt(logs.size - 1)
        }
        _playerState?.value = _playerState?.value?.copy(decoderDebugLogs = ArrayList(logs)) ?: return
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
            .setTextTrackTranscodingEnabled(true)
    }

    @Synchronized
    fun initializePlayer(decoderMode: DecoderMode = DecoderMode.HW_PLUS): ExoPlayer {
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

        // Load control tuned for ultra-fast, responsive start without buffer starvation
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1500,  // min buffer 1.5s
                30000, // max buffer 30s
                250,   // buffer for playback 250ms (instant start!)
                500    // buffer for rebuffering 500ms
            )
            .setBackBuffer(10000, false)
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

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setIgnoredTextSelectionFlags(0)
                    .setPreferredTextLanguage(null)
                    .setSelectUndeterminedTextLanguage(true)
            )
        }

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()

        addDebugLog("[PLAYER] ExoPlayer diinisialisasi dalam mode ${decoderMode.label}")

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onSurfaceSizeChanged(
                eventTime: AnalyticsListener.EventTime,
                width: Int,
                height: Int
            ) {
                addDebugLog("[SURFACE] Ukuran Surface terpasang: ${width}x${height} px")
            }

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
                bufferingWatchdogJob?.cancel()
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

                if (playbackState == Player.STATE_BUFFERING) {
                    bufferingWatchdogJob?.cancel()
                    bufferingWatchdogJob = coroutineScope.launch {
                        kotlinx.coroutines.delay(2500)
                        if (_playerState.value.isLoading && exoPlayer?.playbackState == Player.STATE_BUFFERING) {
                            val buf = exoPlayer?.bufferedPosition ?: 0L
                            val pos = exoPlayer?.currentPosition ?: 0L
                            val dur = exoPlayer?.duration ?: 0L
                            addDebugLog("[WATCHDOG] Buffering aktif (Pos: ${pos}ms, Buffer: ${buf}ms / ${dur}ms). Memicu pemutaran...")
                            exoPlayer?.play()
                            
                            // If still buffering after another 2 seconds, trigger a frame refresh
                            kotlinx.coroutines.delay(2000)
                            if (_playerState.value.isLoading && exoPlayer?.playbackState == Player.STATE_BUFFERING) {
                                val currentP = exoPlayer?.currentPosition ?: 0L
                                addDebugLog("[WATCHDOG] Mendorong sinkronisasi frame pada ${currentP}ms...")
                                exoPlayer?.seekTo(currentP)
                                exoPlayer?.play()
                            }
                        }
                    }
                } else {
                    bufferingWatchdogJob?.cancel()
                }
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

            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                val cues = cueGroup.cues
                val text = cues.joinToString("\n") { it.text?.toString() ?: "" }.trim()
                _playerState.value = _playerState.value.copy(subtitleCuesText = text)
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

                // Automatic Decoder Fallback: HW+ -> HW -> SW VLC
                if (isDecoderError && !fallbackAttempted) {
                    if (activeDecoderMode == DecoderMode.HW_PLUS) {
                        fallbackAttempted = true
                        addDebugLog("[AUTO_FALLBACK] Dekoder HW+ gagal (${error.errorCodeName}). Mengalihkan otomatis ke Hardware standar (HW)... ⚡")
                        coroutineScope.launch {
                            switchToDecoder(DecoderMode.HW, isUserAction = false)
                        }
                        return
                    } else if (activeDecoderMode == DecoderMode.HW) {
                        fallbackAttempted = true
                        addDebugLog("[AUTO_FALLBACK] Dekoder Hardware gagal (${error.errorCodeName}). Mengalihkan otomatis ke Software VLC C++ Native... 🚀")
                        coroutineScope.launch {
                            switchToDecoder(DecoderMode.VLC, isUserAction = false)
                        }
                        return
                    }
                }

                val errorDetails = buildString {
                    append("[ERROR] ${error.errorCodeName} (${error.errorCode}): ${error.message ?: "Unknown"}")
                    var cause = error.cause
                    var depth = 0
                    while (cause != null && depth < 3) {
                        append(" -> [Penyebab: ${cause.javaClass.simpleName}: ${cause.message}]")
                        cause = cause.cause
                        depth++
                    }
                }
                addDebugLog(errorDetails)

                val msg = when {
                    httpStatusCode == 404 -> "Video / URL streaming tidak ditemukan di server (HTTP 404 Not Found)."
                    httpStatusCode == 403 -> "Akses streaming ditolak oleh server (HTTP 403 Forbidden). Server membatasi akses URL ini."
                    httpStatusCode in 500..599 -> "Server video sedang mengalami kendala (HTTP $httpStatusCode Server Error)."
                    httpStatusCode > 0 -> "Gagal memuat URL streaming (HTTP $httpStatusCode)."
                    isNetworkError -> "Gagal menghubungkan ke server streaming. Periksa koneksi internet Anda."
                    isDecoderError && activeDecoderMode == DecoderMode.HW -> "Codec/format video tidak didukung hardware hp. Silakan beralih ke mode SW (Software/FFmpeg)."
                    isDecoderError && activeDecoderMode == DecoderMode.SW -> "Dekoder SW (FFmpeg) gagal mengurai format ini. Coba beralih ke HW atau format lain."
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Format kontainer file video tidak didukung."
                    else -> error.localizedMessage ?: "Gagal memutar video (Error ${error.errorCodeName})"
                }
                _playerState.value = _playerState.value.copy(
                    isLoading = false,
                    errorMessage = msg
                )
            }
        })

        exoPlayer = player
        _activePlayer.value = player
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
                    // Google Android Software Decoders (pure Android OS built-in software decoders)
                    val swDecoders = decoders.filter {
                        it.name.startsWith("c2.android.", ignoreCase = true) ||
                        it.name.startsWith("OMX.google.", ignoreCase = true) ||
                        it.name.contains("sw", ignoreCase = true) ||
                        it.name.contains("software", ignoreCase = true) ||
                        !it.hardwareAccelerated
                    }
                    if (swDecoders.isNotEmpty()) {
                        swDecoders + decoders.filterNot { swDecoders.contains(it) }
                    } else {
                        decoders
                    }
                }
                DecoderMode.HW, DecoderMode.HW_PLUS -> {
                    // Prioritize hardware accelerated decoders (e.g. OMX.MTK.VIDEO.DECODER.HEVC on Oppo Helio P35)
                    val hwDecoders = decoders.filter {
                        it.hardwareAccelerated ||
                        it.name.startsWith("OMX.MTK.", ignoreCase = true) ||
                        it.name.startsWith("OMX.qcom.", ignoreCase = true) ||
                        it.name.startsWith("OMX.Exynos.", ignoreCase = true) ||
                        it.name.startsWith("c2.mtk.", ignoreCase = true) ||
                        it.name.startsWith("c2.qcom.", ignoreCase = true)
                    }
                    val swDecoders = decoders.filterNot { hwDecoders.contains(it) }
                    if (hwDecoders.isNotEmpty()) hwDecoders + swDecoders else decoders
                }
                DecoderMode.FFMPEG, DecoderMode.SYSTEM, DecoderMode.VLC -> decoders
            }
        }

        val factory = OptimizedRenderersFactory(context, decoderMode, customMediaCodecSelector)
        when (decoderMode) {
            DecoderMode.HW -> addDebugLog("[RENDERER] Mode HW: Pure Hardware MediaCodec (Akselerasi Chipset Asli - Cepat & Ringan) ⚡")
            DecoderMode.VLC -> addDebugLog("[RENDERER] Mode VLC: LibVLC C++ Native Engine (Pemutar HEVC 10-bit & Multi-Channel) 🚀")
            DecoderMode.HW_PLUS -> addDebugLog("[RENDERER] Mode HW+: Enhanced Hardware MediaCodec ⚡+")
            DecoderMode.SW -> addDebugLog("[RENDERER] Mode SW: Google Android OS SW Decoder (Tanpa FFmpeg) ⚙️")
            DecoderMode.FFMPEG -> addDebugLog("[RENDERER] Mode FFmpeg: FFmpeg Software Engine 🎞️")
            DecoderMode.SYSTEM -> addDebugLog("[RENDERER] Mode Sistem: Android Native MediaPlayer (NuPlayer/Stagefright OS) 🏛️")
        }

        return factory
    }

    fun playMedia(media: VideoMediaItem, startPositionMs: Long = 0L) {
        fallbackAttempted = false
        currentMediaItem = media
        try {
            when (activeDecoderMode) {
                DecoderMode.SYSTEM -> {
                    try {
                        exoPlayer?.stop()
                        exoPlayer?.clearVideoSurface()
                        exoPlayer?.release()
                    } catch (_: Throwable) {}
                    exoPlayer = null
                    _activePlayer.value = null
                    vlcPlayerEngine.release()
                    _playerState.value = _playerState.value.copy(
                        decoderMode = DecoderMode.SYSTEM,
                        errorMessage = null,
                        videoCodecName = media.codec,
                        firstFrameRendered = false
                    )
                    systemPlayerEngine.playMedia(media, startPositionMs)
                }
                DecoderMode.VLC -> {
                    try {
                        exoPlayer?.stop()
                        exoPlayer?.clearVideoSurface()
                        exoPlayer?.release()
                    } catch (_: Throwable) {}
                    exoPlayer = null
                    _activePlayer.value = null
                    systemPlayerEngine.release()
                    _playerState.value = _playerState.value.copy(
                        decoderMode = DecoderMode.VLC,
                        errorMessage = null,
                        videoCodecName = media.codec,
                        firstFrameRendered = false
                    )
                    val targetMedia = if (media.streamType == StreamType.SMB || media.uri.scheme?.equals("smb", ignoreCase = true) == true) {
                        try {
                            val cleanUrl = if (media.uri.userInfo != null) {
                                val s = media.uri.scheme ?: "smb"
                                val h = media.uri.host ?: ""
                                val p = if (media.uri.port > 0) ":${media.uri.port}" else ""
                                val path = media.uri.path ?: ""
                                "$s://$h$p$path"
                            } else media.uri.toString()
                            val cifs = SmbDataSource.createDefaultCifsContext(media.uri)
                            val httpUrl = LocalMediaServer.registerSmbStream(cleanUrl, cifs)
                            addDebugLog("[NETWORK_PROXY] Routing SMB ke LocalMediaServer loopback HTTP: $httpUrl")
                            media.copy(uri = Uri.parse(httpUrl))
                        } catch (e: Exception) {
                            addDebugLog("[NETWORK_PROXY_ERROR] Gagal register SMB ke LocalMediaServer: ${e.message}")
                            media
                        }
                    } else {
                        media
                    }
                    vlcPlayerEngine.playMedia(targetMedia, startPositionMs)
                }
                else -> {
                    systemPlayerEngine.release()
                    vlcPlayerEngine.release()
                    playMediaInternal(media, startPositionMs)
                }
            }
        } catch (e: Throwable) {
            addDebugLog("[PLAY_ERROR] Error memutar video: ${e.message}")
            _playerState.value = _playerState.value.copy(
                isLoading = false,
                errorMessage = "Gagal memutar video: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
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
            if (startPositionMs > 0) {
                player.setMediaSource(mediaSource, startPositionMs)
            } else {
                player.setMediaSource(mediaSource)
            }
            player.prepare()
            player.playWhenReady = true
            player.play()

            _playerState.value = _playerState.value.copy(
                errorMessage = null,
                videoCodecName = media.codec,
                firstFrameRendered = false
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

        // Local storage / MediaStore content:// videos:
        val mediaItemBuilder = MediaItem.Builder().setUri(media.uri)
        if (media.path.isNotBlank()) {
            val sidecarSubtitles = findAutoSidecarSubtitles(media.path)
            if (sidecarSubtitles.isNotEmpty()) {
                addDebugLog("[SUBTITLE] Terdeteksi ${sidecarSubtitles.size} subtitle otomatis di folder video: ${sidecarSubtitles.joinToString { it.name }}")
                val configs = sidecarSubtitles.map { subFile ->
                    val mimeType = when (subFile.extension.lowercase()) {
                        "vtt" -> MimeTypes.TEXT_VTT
                        "ass", "ssa" -> MimeTypes.TEXT_SSA
                        else -> MimeTypes.APPLICATION_SUBRIP
                    }
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subFile))
                        .setMimeType(mimeType)
                        .setLanguage(subFile.extension.ifBlank { "ind" })
                        .setLabel(subFile.name)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                }
                mediaItemBuilder.setSubtitleConfigurations(configs)
            }
        }

        return DefaultMediaSourceFactory(context, extractorsFactory)
            .createMediaSource(mediaItemBuilder.build())
    }

    private fun findAutoSidecarSubtitles(videoPath: String): List<java.io.File> {
        try {
            val videoFile = java.io.File(videoPath)
            val parentDir = videoFile.parentFile ?: return emptyList()
            if (!parentDir.exists() || !parentDir.isDirectory) return emptyList()

            val videoBaseName = videoFile.nameWithoutExtension.lowercase()
            val subExts = setOf("srt", "vtt", "ass", "ssa", "sub")

            return parentDir.listFiles()?.filter { f ->
                if (!f.isFile) return@filter false
                val ext = f.extension.lowercase()
                if (!subExts.contains(ext)) return@filter false
                val fName = f.nameWithoutExtension.lowercase()
                fName.startsWith(videoBaseName)
            } ?: emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
    }

    fun loadExternalSubtitle(fileOrUrl: String) {
        val currentPos = _playerState.value.currentPositionMs
        addDebugLog("[SUBTITLE] Memuat subtitle eksternal: $fileOrUrl")
        val uri = if (fileOrUrl.startsWith("http://") || fileOrUrl.startsWith("https://")) {
            Uri.parse(fileOrUrl)
        } else {
            Uri.fromFile(java.io.File(fileOrUrl))
        }
        val fileName = uri.lastPathSegment ?: "Subtitle Eksternal"

        _playerState.value = _playerState.value.copy(externalSubtitleName = fileName)

        when (activeDecoderMode) {
            DecoderMode.VLC -> {
                vlcPlayerEngine.addSlaveSubtitle(uri.toString())
            }
            else -> {
                val player = exoPlayer ?: return
                val ext = fileName.substringAfterLast(".", "srt").lowercase()
                val mimeType = when (ext) {
                    "vtt" -> MimeTypes.TEXT_VTT
                    "ass", "ssa" -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
                val subConfig = MediaItem.SubtitleConfiguration.Builder(uri)
                    .setMimeType(mimeType)
                    .setLabel(fileName)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()

                val currentMedia = currentMediaItem
                if (currentMedia != null) {
                    val subSource = androidx.media3.exoplayer.source.SingleSampleMediaSource.Factory(
                        DefaultDataSource.Factory(context)
                    ).createMediaSource(subConfig, C.TIME_UNSET)
                    val videoSource = createMediaSourceFor(currentMedia)
                    val mergedSource = androidx.media3.exoplayer.source.MergingMediaSource(videoSource, subSource)
                    player.setMediaSource(mergedSource, currentPos)
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    fun loadExternalAudio(fileOrUrl: String) {
        val currentPos = _playerState.value.currentPositionMs
        addDebugLog("[AUDIO] Memuat audio eksternal: $fileOrUrl")
        val uri = if (fileOrUrl.startsWith("http://") || fileOrUrl.startsWith("https://")) {
            Uri.parse(fileOrUrl)
        } else {
            Uri.fromFile(java.io.File(fileOrUrl))
        }
        val fileName = uri.lastPathSegment ?: "Audio Eksternal"

        _playerState.value = _playerState.value.copy(externalAudioName = fileName)

        when (activeDecoderMode) {
            DecoderMode.VLC -> {
                vlcPlayerEngine.addSlaveAudio(uri.toString())
            }
            else -> {
                val player = exoPlayer ?: return
                val extractorsFactory = createExtractorsFactory()
                val audioMediaSource = ProgressiveMediaSource.Factory(
                    DefaultDataSource.Factory(context),
                    extractorsFactory
                ).createMediaSource(MediaItem.fromUri(uri))

                val currentMedia = currentMediaItem
                if (currentMedia != null) {
                    val videoMediaSource = createMediaSourceFor(currentMedia)
                    val mergedSource = androidx.media3.exoplayer.source.MergingMediaSource(videoMediaSource, audioMediaSource)
                    player.setMediaSource(mergedSource, currentPos)
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    fun setSubtitleOffset(offsetDp: Int) {
        _playerState.value = _playerState.value.copy(subtitleOffsetDp = offsetDp)
    }

    fun switchToDecoder(decoderMode: DecoderMode, isUserAction: Boolean = false) {
        val currentPos = when (activeDecoderMode) {
            DecoderMode.SYSTEM, DecoderMode.VLC -> _playerState.value.currentPositionMs
            else -> exoPlayer?.currentPosition ?: _playerState.value.currentPositionMs
        }
        val currentPlayWhenReady = _playerState.value.isPlaying
        val media = currentMediaItem

        addDebugLog("[SWITCH] Mengganti mode dekoder ke ${decoderMode.label} (UserAction: $isUserAction, Posisi: ${currentPos}ms)")

        fallbackAttempted = !isUserAction
        activeDecoderMode = decoderMode
        _playerState.value = _playerState.value.copy(
            decoderMode = decoderMode,
            errorMessage = null,
            isLoading = true
        )

        try {
            when (decoderMode) {
                DecoderMode.SYSTEM -> {
                    exoPlayer?.stop()
                    exoPlayer?.clearVideoSurface()
                    exoPlayer?.release()
                    exoPlayer = null
                    _activePlayer.value = null
                    vlcPlayerEngine.release()
                    if (media != null) {
                        systemPlayerEngine.playMedia(media, currentPos)
                    }
                }
                DecoderMode.VLC -> {
                    exoPlayer?.stop()
                    exoPlayer?.clearVideoSurface()
                    exoPlayer?.release()
                    exoPlayer = null
                    _activePlayer.value = null
                    systemPlayerEngine.release()
                    if (media != null) {
                        vlcPlayerEngine.playMedia(media, currentPos)
                    }
                }
                else -> {
                    systemPlayerEngine.release()
                    vlcPlayerEngine.release()
                    initializePlayer(decoderMode)
                    if (media != null) {
                        playMediaInternal(media, currentPos)
                        exoPlayer?.playWhenReady = currentPlayWhenReady
                    }
                }
            }
        } catch (t: Throwable) {
            addDebugLog("[SWITCH_ERROR] Gagal beralih ke mode ${decoderMode.label}: ${t.message}")
            _playerState.value = _playerState.value.copy(
                errorMessage = "Gagal beralih ke mode ${decoderMode.label}: ${t.localizedMessage ?: t.message}"
            )
        }
    }

    fun play() {
        when (activeDecoderMode) {
            DecoderMode.SYSTEM -> systemPlayerEngine.play()
            DecoderMode.VLC -> vlcPlayerEngine.play()
            else -> exoPlayer?.play()
        }
    }

    fun pause() {
        when (activeDecoderMode) {
            DecoderMode.SYSTEM -> systemPlayerEngine.pause()
            DecoderMode.VLC -> vlcPlayerEngine.pause()
            else -> exoPlayer?.pause()
        }
    }

    fun togglePlayPause() {
        when (activeDecoderMode) {
            DecoderMode.SYSTEM -> systemPlayerEngine.togglePlayPause()
            DecoderMode.VLC -> vlcPlayerEngine.togglePlayPause()
            else -> {
                val player = exoPlayer ?: return
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        when (activeDecoderMode) {
            DecoderMode.SYSTEM -> systemPlayerEngine.seekTo(maxOf(0L, positionMs))
            DecoderMode.VLC -> vlcPlayerEngine.seekTo(maxOf(0L, positionMs))
            else -> exoPlayer?.seekTo(maxOf(0L, positionMs))
        }
    }

    fun skipForward(seconds: Int = 10) {
        val current = _playerState.value.currentPositionMs
        val duration = _playerState.value.durationMs
        val target = current + (seconds * 1000L)
        val finalPos = if (duration > 0) minOf(duration, target) else target
        seekTo(finalPos)
    }

    fun skipBackward(seconds: Int = 10) {
        val current = _playerState.value.currentPositionMs
        val target = current - (seconds * 1000L)
        seekTo(maxOf(0L, target))
    }

    fun setPlaybackSpeed(speed: Float) {
        when (activeDecoderMode) {
            DecoderMode.SYSTEM -> systemPlayerEngine.setPlaybackSpeed(speed)
            DecoderMode.VLC -> vlcPlayerEngine.setPlaybackSpeed(speed)
            else -> exoPlayer?.playbackParameters = PlaybackParameters(speed)
        }
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
            DecoderMode.HW -> DecoderMode.HW_PLUS
            DecoderMode.HW_PLUS -> DecoderMode.VLC
            DecoderMode.VLC -> DecoderMode.HW
            else -> DecoderMode.HW
        }
        switchToDecoder(next, isUserAction = true)
    }

    fun selectAudioTrack(trackInfo: PlayerTrackInfo) {
        if (activeDecoderMode == DecoderMode.VLC) {
            vlcPlayerEngine.selectAudioTrack(trackInfo.trackIndex)
            _playerState.value = _playerState.value.copy(
                audioTracks = _playerState.value.audioTracks.map {
                    it.copy(isSelected = it.trackIndex == trackInfo.trackIndex)
                }
            )
            return
        }
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
        if (activeDecoderMode == DecoderMode.VLC) {
            vlcPlayerEngine.selectSubtitleTrack(trackInfo?.trackIndex ?: -1)
            _playerState.value = _playerState.value.copy(
                subtitleTracks = _playerState.value.subtitleTracks.map {
                    it.copy(isSelected = trackInfo != null && it.trackIndex == trackInfo.trackIndex)
                }
            )
            return
        }
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

    fun setSubtitleDelay(delayMs: Long) {
        _playerState.value = _playerState.value.copy(subtitleDelayMs = delayMs)
        if (activeDecoderMode == DecoderMode.VLC) {
            vlcPlayerEngine.setSubtitleDelay(delayMs)
        } else {
            addDebugLog("[SUBTITLE] Delay subtitle diatur ke ${delayMs}ms")
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
        when (activeDecoderMode) {
            DecoderMode.SYSTEM -> systemPlayerEngine.pollProgress()
            DecoderMode.VLC -> vlcPlayerEngine.pollProgress()
            else -> {
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
        }
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

    fun forcePlay() {
        addDebugLog("[CONTROL] Paksa pemutaran (Force Play) dipicu...")
        when (activeDecoderMode) {
            DecoderMode.SYSTEM -> systemPlayerEngine.play()
            DecoderMode.VLC -> vlcPlayerEngine.play()
            else -> {
                exoPlayer?.let { p ->
                    p.playWhenReady = true
                    p.play()
                    val cur = p.currentPosition
                    p.seekTo(cur)
                    addDebugLog("[CONTROL] State: ${p.playbackState}, Pos: ${cur}ms, PlayWhenReady: ${p.playWhenReady}")
                }
            }
        }
    }

    fun reloadCurrentMedia() {
        val media = currentMediaItem ?: return
        val pos = _playerState.value.currentPositionMs
        addDebugLog("[CONTROL] Memuat ulang media saat ini dari posisi: ${pos}ms...")
        when (activeDecoderMode) {
            DecoderMode.SYSTEM -> systemPlayerEngine.playMedia(media, pos)
            DecoderMode.VLC -> vlcPlayerEngine.playMedia(media, pos)
            else -> playMediaInternal(media, pos)
        }
    }

    fun getFullDiagnosticReport(): String {
        val player = exoPlayer
        val state = _playerState.value
        val media = currentMediaItem

        val pStateName = when (state.decoderMode) {
            DecoderMode.SYSTEM -> if (state.isPlaying) "PLAYING (Mesin Sistem Android)" else "PAUSED/READY (Mesin Sistem Android)"
            DecoderMode.VLC -> if (state.isPlaying) "PLAYING (LibVLC C++ Native SW)" else "PAUSED/READY (LibVLC C++ Native SW)"
            else -> when (player?.playbackState) {
                Player.STATE_IDLE -> "IDLE (Menganggur)"
                Player.STATE_BUFFERING -> "BUFFERING (Memuat penyangga)"
                Player.STATE_READY -> "READY (Siap memutar)"
                Player.STATE_ENDED -> "ENDED (Selesai)"
                null -> "TIDAK ADA EXOPLAYER (Aktif di Engine Non-Exo)"
                else -> "UNKNOWN (${player?.playbackState})"
            }
        }

        return buildString {
            appendLine("=== LAPORAN DIAGNOSTIK PEMUTAR VIDEO ===")
            appendLine("Waktu: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine()
            appendLine("[1. PERANGKAT & SISTEM]")
            appendLine("- Perangkat: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("- Versi Android: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("- CPU / ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("- Core Tersedia: ${Runtime.getRuntime().availableProcessors()}")
            appendLine()
            appendLine("[2. STATUS DEKODER & FFMPEG]")
            appendLine("- Mode Dekoder Dipilih: ${state.decoderMode.label} (${state.decoderMode.name})")
            appendLine("- LibVLC C++ Native Engine: Aktif (org.videolan.libvlc)")
            appendLine("- FFmpeg NextLib Aktif: ${isFfmpegAvailable()}")
            appendLine("- FFmpeg Load Error: ${ffmpegLoadError ?: "None"}")
            appendLine("- Dekoder Video Aktif: ${state.activeVideoDecoder}")
            appendLine("- Dekoder Audio Aktif: ${state.activeAudioDecoder}")
            appendLine()
            appendLine("[3. STATUS ENGINE PEMUTAR]")
            appendLine("- Playback State: $pStateName")
            appendLine("- Is Playing: ${state.isPlaying}")
            appendLine("- Is Loading / Buffering: ${state.isLoading}")
            appendLine("- Play When Ready: ${player?.playWhenReady ?: state.isPlaying}")
            appendLine("- First Frame Rendered: ${state.firstFrameRendered}")
            appendLine("- Posisi Saat Ini: ${state.currentPositionMs}ms / ${state.durationMs}ms")
            appendLine("- Penyangga (Buffer): ${state.bufferedPositionMs}ms")
            appendLine("- Total Buffer Terisi: ${player?.totalBufferedDuration ?: state.bufferedPositionMs}ms")
            appendLine("- Frame Terlewat (Dropped): ${state.droppedFramesCount}")
            appendLine("- Estimasi Bitrate: ${state.estimatedBitrateKbps} kbps")
            appendLine("- Pesan Error: ${state.errorMessage ?: "Tidak ada error"}")
            appendLine()
            appendLine("[4. INFORMASI MEDIA]")
            appendLine("- Judul: ${media?.title ?: "Tidak ada media"}")
            appendLine("- URI: ${media?.uri}")
            appendLine("- Path: ${media?.path}")
            appendLine("- MIME Type: ${media?.mimeType}")
            appendLine("- Stream Type: ${media?.streamType}")
            appendLine("- Detail Format Video: ${state.videoFormatDetails}")
            appendLine("- Detail Format Audio: ${state.audioFormatDetails}")
            appendLine()
            appendLine("[5. LOG AKTIVITAS TERMINAL (${state.decoderDebugLogs.size} baris)]")
            if (state.decoderDebugLogs.isEmpty()) {
                appendLine("(Belum ada log tercatat)")
            } else {
                state.decoderDebugLogs.forEach { log ->
                    appendLine(log)
                }
            }
            appendLine("=== AKHIR LAPORAN ===")
        }
    }

    fun release() {
        _activePlayer.value = null
        try {
            systemPlayerEngine.release()
            vlcPlayerEngine.release()
            exoPlayer?.let { p ->
                p.stop()
                p.clearVideoSurface()
                p.clearMediaItems()
                p.release()
            }
        } catch (_: Throwable) {}
        exoPlayer = null
    }
}
