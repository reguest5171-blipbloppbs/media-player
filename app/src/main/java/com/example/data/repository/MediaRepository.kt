package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.local.dao.MediaPlayerDao
import com.example.data.local.entity.PlayHistoryEntity
import com.example.data.model.StreamType
import com.example.data.model.VideoFolder
import com.example.data.model.VideoMediaItem
import com.example.player.EncryptionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MediaRepository(
    private val context: Context,
    private val dao: MediaPlayerDao
) {
    val playHistoryFlow: Flow<List<PlayHistoryEntity>> = dao.getAllPlayHistory()

    suspend fun getPlayHistoryForUri(uri: String): PlayHistoryEntity? {
        return dao.getPlayHistoryForUri(uri)
    }

    suspend fun savePlaybackPosition(
        uri: String,
        path: String,
        title: String,
        positionMs: Long,
        durationMs: Long,
        decoderMode: String = "HW"
    ) {
        val isCompleted = durationMs > 0 && positionMs >= (durationMs * 0.95)
        dao.savePlayHistory(
            PlayHistoryEntity(
                mediaUri = uri,
                mediaPath = path,
                title = title,
                lastPositionMs = positionMs,
                durationMs = durationMs,
                lastPlayedTimestamp = System.currentTimeMillis(),
                decoderMode = decoderMode,
                isCompleted = isCompleted
            )
        )
    }

    suspend fun clearHistory() = dao.clearAllHistory()

    suspend fun scanLocalVideos(customEncryptedExt: String = "1ca", includeVault1ca: Boolean = false): List<VideoMediaItem> = withContext(Dispatchers.IO) {
        val videoList = mutableListOf<VideoMediaItem>()
        val seenPaths = HashSet<String>()

        val cleanCustom = customEncryptedExt.trim().removePrefix(".").lowercase()

        // 1. Scan MediaStore
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.MIME_TYPE
            )

            val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val displayName = cursor.getString(nameCol) ?: "Video_$id"
                    val title = if (titleCol >= 0) cursor.getString(titleCol) ?: displayName else displayName
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val duration = if (durCol >= 0) cursor.getLong(durCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val dateModified = if (dateCol >= 0) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"

                    val file = if (path.isNotBlank()) File(path) else null
                    val parentFile = file?.parentFile
                    val folderPath = parentFile?.absolutePath ?: "Storage"
                    val folderName = parentFile?.name ?: "Storage"

                    val is1ca = displayName.endsWith(".1ca", ignoreCase = true) || path.endsWith(".1ca", ignoreCase = true) ||
                            (cleanCustom.isNotBlank() && (displayName.endsWith(".$cleanCustom", ignoreCase = true) || path.endsWith(".$cleanCustom", ignoreCase = true)))

                    if (!is1ca || includeVault1ca) {
                        if (path.isNotBlank()) seenPaths.add(path)
                        videoList.add(
                            VideoMediaItem(
                                id = id,
                                uri = contentUri,
                                path = path,
                                title = title,
                                displayName = displayName,
                                durationMs = duration,
                                sizeBytes = size,
                                dateModified = dateModified,
                                width = width,
                                height = height,
                                mimeType = mimeType,
                                folderPath = folderPath,
                                folderName = folderName,
                                isEncrypted1ca = is1ca,
                                streamType = if (is1ca) StreamType.VAULT_1CA else StreamType.LOCAL
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Scan Storage Folders for non-indexed files & .1ca files
        val commonDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Video"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"),
            File(Environment.getExternalStorageDirectory(), "Telegram/Telegram Video"),
            File(context.filesDir, "vault_1ca")
        )

        for (dir in commonDirs) {
            if (dir.exists() && dir.isDirectory) {
                scanDirectoryRecursively(dir, videoList, seenPaths, cleanCustom, includeVault1ca, depth = 0)
            }
        }

        videoList
    }

    private fun scanDirectoryRecursively(
        directory: File,
        results: MutableList<VideoMediaItem>,
        seenPaths: MutableSet<String>,
        cleanCustomExt: String,
        includeVault1ca: Boolean,
        depth: Int
    ) {
        if (depth > 4) return
        val files = directory.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanDirectoryRecursively(file, results, seenPaths, cleanCustomExt, includeVault1ca, depth + 1)
            } else if (file.isFile) {
                val name = file.name
                val is1ca = name.endsWith(".1ca", ignoreCase = true) ||
                        (cleanCustomExt.isNotBlank() && name.endsWith(".$cleanCustomExt", ignoreCase = true))
                val isVideo = isSupportedVideoFile(name)

                if ((isVideo || is1ca) && !seenPaths.contains(file.absolutePath)) {
                    if (!is1ca || includeVault1ca) {
                        seenPaths.add(file.absolutePath)
                        results.add(
                            VideoMediaItem(
                                id = file.absolutePath.hashCode().toLong(),
                                uri = Uri.fromFile(file),
                                path = file.absolutePath,
                                title = name,
                                displayName = name,
                                durationMs = 0L,
                                sizeBytes = file.length(),
                                dateModified = file.lastModified(),
                                width = 0,
                                height = 0,
                                mimeType = if (is1ca) "application/octet-stream" else "video/*",
                                folderPath = file.parent ?: "",
                                folderName = file.parentFile?.name ?: "Unknown",
                                isEncrypted1ca = is1ca,
                                streamType = if (is1ca) StreamType.VAULT_1CA else StreamType.LOCAL
                            )
                        )
                    }
                }
            }
        }
    }

    fun groupVideosIntoFolders(videos: List<VideoMediaItem>): List<VideoFolder> {
        val groups = videos.groupBy { it.folderPath }
        return groups.map { (folderPath, items) ->
            val totalSize = items.sumOf { it.sizeBytes }
            val folderName = items.firstOrNull()?.folderName ?: "Folder"
            val latestThumb = items.maxByOrNull { it.dateModified }?.uri
            VideoFolder(
                path = folderPath,
                name = folderName,
                videoCount = items.size,
                totalSizeBytes = totalSize,
                latestThumbnailUri = latestThumb
            )
        }.sortedByDescending { it.videoCount }
    }

    suspend fun deleteVideoFile(item: VideoMediaItem): Boolean = withContext(Dispatchers.IO) {
        try {
            // Delete from Room history if present
            dao.deletePlayHistory(item.uri.toString())
            if (item.path.isNotBlank()) {
                dao.deletePlayHistory(item.path)
            }

            if (item.path.isNotBlank()) {
                val file = File(item.path)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) return@withContext true
                }
            }

            // Fallback content resolver delete
            val rows = context.contentResolver.delete(item.uri, null, null)
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun renameVideoFile(item: VideoMediaItem, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (item.path.isBlank()) return@withContext false
            val file = File(item.path)
            if (!file.exists()) return@withContext false

            val parent = file.parentFile ?: return@withContext false
            val cleanName = if (item.isEncrypted1ca && !newName.endsWith(".1ca", ignoreCase = true)) {
                "${newName}.1ca"
            } else newName

            val target = File(parent, cleanName)
            file.renameTo(target)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun moveVideoFile(item: VideoMediaItem, targetDirectoryPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (item.path.isBlank()) return@withContext false
            val sourceFile = File(item.path)
            if (!sourceFile.exists()) return@withContext false

            val targetDir = File(targetDirectoryPath)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val targetFile = File(targetDir, sourceFile.name)
            val moved = sourceFile.renameTo(targetFile)
            if (moved) return@withContext true

            // Fallback copy & delete
            val input = FileInputStream(sourceFile)
            val output = FileOutputStream(targetFile)
            input.copyTo(output)
            input.close()
            output.close()
            sourceFile.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun lockVideoToVault(item: VideoMediaItem): VideoMediaItem? = withContext(Dispatchers.IO) {
        try {
            val file = if (item.path.isNotBlank()) File(item.path) else null
            if (file != null && file.exists()) {
                val encryptedFile = EncryptionUtil.encryptVideoTo1ca(file) ?: return@withContext null
                return@withContext item.copy(
                    path = encryptedFile.absolutePath,
                    uri = Uri.fromFile(encryptedFile),
                    displayName = encryptedFile.name,
                    title = encryptedFile.name,
                    isEncrypted1ca = true,
                    streamType = StreamType.VAULT_1CA
                )
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun unlockVideoFromVault(item: VideoMediaItem): VideoMediaItem? = withContext(Dispatchers.IO) {
        try {
            val file = if (item.path.isNotBlank()) File(item.path) else null
            if (file != null && file.exists()) {
                val decryptedFile = EncryptionUtil.decrypt1caVideo(file) ?: return@withContext null
                return@withContext item.copy(
                    path = decryptedFile.absolutePath,
                    uri = Uri.fromFile(decryptedFile),
                    displayName = decryptedFile.name,
                    title = decryptedFile.name,
                    isEncrypted1ca = false,
                    streamType = StreamType.LOCAL
                )
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isSupportedVideoFile(name: String): Boolean {
        val ext = listOf(".mp4", ".mkv", ".avi", ".mov", ".flv", ".webm", ".ts", ".3gp", ".vob", ".wmv", ".m4v")
        return ext.any { name.endsWith(it, ignoreCase = true) }
    }
}
