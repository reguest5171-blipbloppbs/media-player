package com.example.data.model

import android.net.Uri

enum class DecoderMode(val label: String, val description: String) {
    HW("HW", "Hardware acceleration (Low battery, fast)"),
    SW("SW", "Classic MX Software Decoder (Maximum compatibility, x265/legacy chips)"),
    HW_PLUS("HW+", "Hardware+ Enhanced Renderer")
}

enum class AspectRatioMode(val label: String) {
    FIT("Fit to Screen"),
    CROP("Crop / Fill"),
    STRETCH("Stretch"),
    ORIGINAL("Original 100%")
}

enum class SortOption(val title: String) {
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
    LIST
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
    val codec: String = "H.264 / HEVC"
) {
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
    val latestThumbnailUri: Uri? = null
) {
    val formattedTotalSize: String
        get() {
            val mb = totalSizeBytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0
            return if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%.1f MB", mb)
        }
}
