package com.example.data.repository

import android.net.Uri
import com.example.data.local.dao.MediaPlayerDao
import com.example.data.local.entity.NetworkServerEntity
import com.example.data.local.entity.StreamBookmarkEntity
import com.example.data.model.StreamType
import com.example.data.model.VideoMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.IOException

data class NetworkFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val streamUri: String
)

class NetworkMediaRepository(
    private val dao: MediaPlayerDao
) {
    val serversFlow: Flow<List<NetworkServerEntity>> = dao.getAllServers()
    val bookmarksFlow: Flow<List<StreamBookmarkEntity>> = dao.getAllBookmarks()

    suspend fun addServer(server: NetworkServerEntity): Long = dao.insertServer(server)
    suspend fun removeServer(id: Long) = dao.deleteServer(id)

    suspend fun addBookmark(bookmark: StreamBookmarkEntity): Long = dao.insertBookmark(bookmark)
    suspend fun removeBookmark(id: Long) = dao.deleteBookmark(id)

    suspend fun listFtpFiles(
        server: NetworkServerEntity,
        remotePath: String
    ): Result<List<NetworkFileItem>> = withContext(Dispatchers.IO) {
        val ftp = FTPClient()
        try {
            ftp.defaultTimeout = 10000
            ftp.connect(server.host, server.port)
            
            val loginSuccess = if (server.isAnonymous || server.username.isBlank()) {
                ftp.login("anonymous", "anonymous")
            } else {
                ftp.login(server.username, server.password)
            }

            if (!loginSuccess) {
                return@withContext Result.failure(IOException("FTP Authentication failed"))
            }

            ftp.enterLocalPassiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)

            val workingPath = if (remotePath.isBlank()) "/" else remotePath
            ftp.changeWorkingDirectory(workingPath)

            val ftpFiles: Array<FTPFile> = ftp.listFiles(workingPath) ?: emptyArray()
            val resultList = mutableListOf<NetworkFileItem>()

            for (file in ftpFiles) {
                if (file.name == "." || file.name == "..") continue
                val isDir = file.isDirectory
                val isVideo = isSupportedVideoExtension(file.name)

                if (isDir || isVideo) {
                    val fullPath = if (workingPath.endsWith("/")) "${workingPath}${file.name}" else "${workingPath}/${file.name}"
                    val authPart = if (!server.isAnonymous && server.username.isNotBlank()) {
                        "${server.username}:${server.password}@"
                    } else ""
                    val streamUrl = "ftp://${authPart}${server.host}:${server.port}${fullPath}"

                    resultList.add(
                        NetworkFileItem(
                            name = file.name,
                            path = fullPath,
                            isDirectory = isDir,
                            sizeBytes = file.size,
                            lastModified = file.timestamp?.timeInMillis ?: 0L,
                            streamUri = streamUrl
                        )
                    )
                }
            }

            ftp.logout()
            ftp.disconnect()
            Result.success(resultList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })))
        } catch (e: Exception) {
            try {
                if (ftp.isConnected) {
                    ftp.disconnect()
                }
            } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    fun buildNetworkVideoItem(item: NetworkFileItem, serverName: String): VideoMediaItem {
        val is1ca = item.name.endsWith(".1ca", ignoreCase = true)
        return VideoMediaItem(
            id = item.streamUri.hashCode().toLong(),
            uri = Uri.parse(item.streamUri),
            path = item.streamUri,
            title = item.name,
            displayName = item.name,
            durationMs = 0L, // Stream duration resolved during playback
            sizeBytes = item.sizeBytes,
            dateModified = item.lastModified,
            mimeType = if (is1ca) "application/octet-stream" else "video/*",
            folderPath = serverName,
            folderName = serverName,
            isEncrypted1ca = is1ca,
            streamType = StreamType.FTP
        )
    }

    fun buildUrlVideoItem(title: String, url: String): VideoMediaItem {
        val uri = Uri.parse(url)
        val displayName = title.ifBlank { uri.lastPathSegment ?: "Live Stream" }
        return VideoMediaItem(
            id = url.hashCode().toLong(),
            uri = uri,
            path = url,
            title = displayName,
            displayName = displayName,
            durationMs = 0L,
            sizeBytes = 0L,
            dateModified = System.currentTimeMillis(),
            mimeType = if (url.contains(".m3u8", ignoreCase = true)) "application/x-mpegURL" else "video/*",
            folderPath = "Network Streams",
            folderName = "Network Streams",
            isEncrypted1ca = url.endsWith(".1ca", ignoreCase = true),
            streamType = StreamType.URL_STREAM
        )
    }

    private fun isSupportedVideoExtension(fileName: String): Boolean {
        val extensions = listOf(
            ".mp4", ".mkv", ".avi", ".mov", ".flv", ".webm", ".ts",
            ".m3u8", ".3gp", ".vob", ".wmv", ".m4v", ".f4v", ".1ca"
        )
        return extensions.any { fileName.endsWith(it, ignoreCase = true) }
    }
}
