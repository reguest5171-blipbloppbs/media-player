package com.example.data.model

import android.net.Uri

enum class DecoderMode(val label: String, val description: String) {
    HW("HW", "Hardware MediaCodec (Akselerasi Chipset Asli - Cepat & Hemat Baterai)"),
    VLC("VLC / SW", "LibVLC Native Engine (Mesin C++ VLC - Pemutar HEVC 10-Bit & Audio 6-Channel)"),
    SYSTEM("Sistem", "Android Native MediaPlayer (Mesin Bawaan OS - Paling Stabil untuk HP Oppo/Lama)"),
    HW_PLUS("HW+", "Hardware+ Enhanced (Akselerasi Perangkat Keras Diperluas)"),
    SW("SW (Google)", "Google Software Decoder (Decoder Bawaan Android)"),
    FFMPEG("FFmpeg", "FFmpeg Software Engine (Multi-Format)")
}

enum class AspectRatioMode(val label: String) {
    FIT("Fit to Screen"),
    CROP("Crop / Fill"),
    STRETCH("Stretch"),
    ORIGINAL("Original 100%")
}

enum class VideoEnhancerMode(val label: String, val description: String) {
    OFF("Normal (Bawaan)", "Warna & kontras asli video tanpa filter"),
    HDR_ADAPTIVE("Adaptive HDR Beautify", "Tone mapping dinamis & kontras S-Curve untuk detail bayangan jernih"),
    SHADOW_LIFT("Shadow Boost (Film Gelap)", "Menaikkan kecerahan & gamma adegan gelap tanpa noise berlebih"),
    VIVID_POP("Vivid Cinema Color", "Saturasi dinamis & warna hidup berkontras tinggi"),
    CUSTOM("Kustom Mandiri", "Atur kontras, kecerahan video, dan saturasi secara manual")
}

enum class SortOption(val title: String) {
    NAME("Judul / Nama"),
    DATE("Tanggal Dimodifikasi"),
    SIZE("Ukuran File"),
    DURATION("Panjang Durasi"),
    RESOLUTION("Resolusi"),
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First"),
    DURATION_DESC("Longest Duration"),
    DURATION_ASC("Shortest Duration")
}

enum class ViewMode {
    GRID,
    LIST,
    COMPACT
}

enum class LocalDisplayMode(val label: String, val subtitle: String) {
    ALL_VIDEOS("Semua Video", "Daftar langsung semua video di penyimpanan"),
    FOLDERS("Mode Folder", "Kelompokkan video berdasarkan folder"),
    FOLDER_FULL_PATH("Folder Full Path", "Tampilkan jalur direktori lengkap dari folder")
}

enum class StreamType {
    LOCAL,
    VAULT_1CA,
    FTP,
    SMB,
    URL_STREAM
}

data class VideoMediaItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val title: String,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateModified: Long,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "video/mp4",
    val folderPath: String = "",
    val folderName: String = "",
    val isEncrypted1ca: Boolean = false,
    val streamType: StreamType = StreamType.LOCAL,
    val playbackPosition: Long = 0L,
    val isWatched: Boolean = false,
    val codec: String = "H.264 / HEVC",
    val isNewVideo: Boolean = false
) {
    val isCompleted: Boolean
        get() = isWatched || (durationMs > 0 && playbackPosition >= (durationMs * 0.95))

    val isInProgress: Boolean
        get() = !isCompleted && playbackPosition > 3000L

    val progressPercent: Int
        get() = if (durationMs > 0) ((playbackPosition.toDouble() / durationMs) * 100).toInt().coerceIn(0, 100) else 0

    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "00:00"
            val totalSec = durationMs / 1000
            val seconds = totalSec % 60
            val minutes = (totalSec / 60) % 60
            val hours = totalSec / 3600
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 MB"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                else -> String.format("%.0f KB", kb)
            }
        }

    val resolutionTag: String
        get() {
            return when {
                width >= 3840 || height >= 2160 -> "4K UHD"
                width >= 2560 || height >= 1440 -> "2K QHD"
                width >= 1920 || height >= 1080 -> "1080p FHD"
                width >= 1280 || height >= 720 -> "720p HD"
                width > 0 && height > 0 -> "${width}x${height}"
                else -> "HD"
            }
        }
}

data class VideoFolder(
    val path: String,
    val name: String,
    val videoCount: Int,
    val totalSizeBytes: Long,
    val latestThumbnailUri: Uri? = null,
    val subFolderCount: Int = 0,
    val lastPlayedVideoTitle: String? = null,
    val lastPlayedTimestamp: Long? = null,
    val hasPlayHistory: Boolean = false,
    val newVideoCount: Int = 0,
    val isNewFolder: Boolean = false
) {
    val formattedTotalSize: String
        get() {
            val mb = totalSizeBytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0
            return if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%.1f MB", mb)
        }
}

data class FolderBreadcrumb(
    val label: String,
    val path: String
)

data class FolderTreeNode(
    val path: String,
    val name: String,
    val isStorageRoot: Boolean = false,
    val subFolderCount: Int = 0,
    val directVideoCount: Int = 0,
    val totalVideoCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val latestThumbnailUri: Uri? = null,
    val lastPlayedVideoTitle: String? = null,
    val lastPlayedTimestamp: Long? = null,
    val hasPlayHistory: Boolean = false,
    val newVideoCount: Int = 0,
    val isNewFolder: Boolean = false
)

data class FolderHistoryItem(
    val folderPath: String,
    val folderName: String,
    val lastPlayedVideoTitle: String,
    val lastPlayedUri: String,
    val lastPlayedTimestamp: Long,
    val videoCount: Int,
    val thumbnailUri: Uri? = null
)
