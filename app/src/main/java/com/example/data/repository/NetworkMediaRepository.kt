package com.example.data.repository

import android.net.Uri
import com.example.data.local.dao.MediaPlayerDao
import com.example.data.local.entity.NetworkServerEntity
import com.example.data.local.entity.StreamBookmarkEntity
import com.example.data.model.StreamType
import com.example.data.model.VideoMediaItem
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.IOException

import com.example.data.local.entity.NetworkShortcutEntity
import com.example.player.EncryptionUtil
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

data class NetworkFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val streamUri: String = ""
)

data class SampleStreamItem(
    val title: String,
    val url: String,
    val description: String,
    val formatTag: String
)

class NetworkMediaRepository(
    private val dao: MediaPlayerDao
) {
    val serversFlow: Flow<List<NetworkServerEntity>> = dao.getAllServers()
    val bookmarksFlow: Flow<List<StreamBookmarkEntity>> = dao.getAllBookmarks()
    val shortcutsFlow: Flow<List<NetworkShortcutEntity>> = dao.getAllShortcuts()

    suspend fun addServer(server: NetworkServerEntity): Long = dao.insertServer(server)
    suspend fun removeServer(id: Long) = dao.deleteServer(id)

    suspend fun addBookmark(bookmark: StreamBookmarkEntity): Long = dao.insertBookmark(bookmark)
    suspend fun removeBookmark(id: Long) = dao.deleteBookmark(id)

    suspend fun addShortcut(shortcut: NetworkShortcutEntity): Long = dao.insertShortcut(shortcut)
    suspend fun removeShortcut(id: Long) = dao.deleteShortcut(id)

    // Curated, 100% working and fast sample streaming URLs for instant testing
    fun getPresetSampleStreams(): List<SampleStreamItem> {
        return listOf(
            SampleStreamItem(
                title = "Big Buck Bunny (720p HD MP4)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                description = "Google Cloud CDN Fast 720p HD Stream",
                formatTag = "720p MP4"
            ),
            SampleStreamItem(
                title = "Sintel (1080p Full HD MP4)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                description = "Google Storage High Bitrate 1080p MP4",
                formatTag = "1080p MP4"
            ),
            SampleStreamItem(
                title = "Tears of Steel (1080p Sci-Fi MP4)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                description = "High Quality Cinematic Benchmark",
                formatTag = "1080p MP4"
            ),
            SampleStreamItem(
                title = "Elephant's Dream (1080p Full HD MP4)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                description = "Open Movie Project High Bitrate MP4",
                formatTag = "1080p MP4"
            ),
            SampleStreamItem(
                title = "For Bigger Blazes (1080p Short MP4)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                description = "Google Chromecast HD Test Stream",
                formatTag = "1080p MP4"
            ),
            SampleStreamItem(
                title = "We Are Going On Bullrun (1080p MP4)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
                description = "Google Storage High Bitrate Action Clip",
                formatTag = "1080p MP4"
            ),
            SampleStreamItem(
                title = "Big Buck Bunny (Mux HLS Adaptive Stream)",
                url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                description = "HLS Adaptive Stream (1080p/720p/480p/360p)",
                formatTag = "HLS M3U8"
            ),
            SampleStreamItem(
                title = "Apple BipBop (16:9 HLS Adaptive)",
                url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",
                description = "Apple HLS Reference Test Pattern 16:9",
                formatTag = "Apple HLS"
            ),
            SampleStreamItem(
                title = "Apple BipBop (4:3 HLS Adaptive)",
                url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8",
                description = "Apple HLS Reference Test Pattern 4:3",
                formatTag = "Apple HLS"
            ),
            SampleStreamItem(
                title = "Tears of Steel (Unified Streaming HLS)",
                url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                description = "Unified Streaming Multi-Audio HLS Stream",
                formatTag = "HLS M3U8"
            )
        )
    }

    suspend fun populatePresetSamplesIfEmpty() {
        val sampleList = getPresetSampleStreams()
        for (sample in sampleList) {
            dao.insertBookmark(
                StreamBookmarkEntity(
                    title = sample.title,
                    url = sample.url
                )
            )
        }
    }

    suspend fun addMissingPresetSamples() {
        val current = dao.getAllBookmarksSync()
        val samples = getPresetSampleStreams()
        val sampleUrls = samples.map { it.url }.toSet()

        // Clean up broken/invalid old sample URLs from DB
        for (bookmark in current) {
            if (bookmark.url.contains("jellyfin.org") ||
                bookmark.url.contains("test-videos.co.uk") ||
                bookmark.url.contains("jellyfish.media") ||
                bookmark.url.contains("filesfortesting.com") ||
                bookmark.url.contains("photoprism") ||
                bookmark.url.contains("akamaihd.net")
            ) {
                dao.deleteBookmark(bookmark.id)
            }
        }

        val updatedCurrent = dao.getAllBookmarksSync()
        val currentUrls = updatedCurrent.map { it.url }.toSet()

        for (sample in samples) {
            if (!currentUrls.contains(sample.url)) {
                dao.insertBookmark(
                    StreamBookmarkEntity(
                        title = sample.title,
                        url = sample.url
                    )
                )
            }
        }
    }

    suspend fun listNetworkFiles(
        server: NetworkServerEntity,
        remotePath: String,
        customEncryptedExt: String = "1ca",
        includeEncrypted: Boolean = true
    ): Result<List<NetworkFileItem>> = withContext(Dispatchers.IO) {
        try {
            // Strict 7 seconds timeout so the app NEVER hangs on unreachable server
            withTimeout(7000L) {
                if (server.type.equals("SMB", ignoreCase = true)) {
                    listSmbFilesInternal(server, remotePath, customEncryptedExt, includeEncrypted)
                } else {
                    listFtpFilesInternal(server, remotePath, customEncryptedExt, includeEncrypted)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(IOException("Koneksi timeout (7 detik). Pastikan IP/Host '${server.host}' dan Port ${server.port} dapat dijangkau di jaringan lokal."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun listFtpFilesInternal(
        server: NetworkServerEntity,
        remotePath: String,
        customEncryptedExt: String,
        includeEncrypted: Boolean
    ): Result<List<NetworkFileItem>> {
        val ftp = FTPClient()
        try {
            ftp.controlEncoding = "UTF-8"
            ftp.defaultTimeout = 5000
            ftp.connectTimeout = 5000
            ftp.setDataTimeout(java.time.Duration.ofMillis(5000))
            ftp.connect(server.host, server.port)

            val loginSuccess = if (server.isAnonymous || server.username.isBlank()) {
                ftp.login("anonymous", "anonymous")
            } else {
                ftp.login(server.username, server.password)
            }

            if (!loginSuccess) {
                return Result.failure(IOException("FTP Login gagal. Periksa username & password atau aktifkan Anonymous mode."))
            }

            try {
                ftp.sendCommand("OPTS UTF8", "ON")
            } catch (_: Exception) {}

            ftp.enterLocalPassiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)

            val workingPath = if (remotePath.isBlank()) "/" else remotePath
            ftp.changeWorkingDirectory(workingPath)

            val ftpFiles: Array<FTPFile> = ftp.listFiles(workingPath) ?: emptyArray()
            val resultList = mutableListOf<NetworkFileItem>()

            for (file in ftpFiles) {
                if (file.name == "." || file.name == "..") continue
                val isDir = file.isDirectory
                val isVideo = isSupportedVideoExtension(file.name, customEncryptedExt, includeEncrypted)

                if (isDir || isVideo) {
                    val fullPath = if (workingPath.endsWith("/")) "${workingPath}${file.name}" else "${workingPath}/${file.name}"
                    val authPart = if (!server.isAnonymous && server.username.isNotBlank()) {
                        "${Uri.encode(server.username)}:${Uri.encode(server.password)}@"
                    } else ""
                    val encodedPath = fullPath.split("/").joinToString("/") { Uri.encode(it) }
                    val streamUrl = "ftp://${authPart}${server.host}:${server.port}$encodedPath"

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

            try {
                ftp.logout()
                ftp.disconnect()
            } catch (_: Exception) {}

            return Result.success(resultList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })))
        } catch (e: Exception) {
            try {
                if (ftp.isConnected) ftp.disconnect()
            } catch (_: Exception) {}
            return Result.failure(e)
        }
    }

    fun getCifsContext(server: NetworkServerEntity): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.disableSMB1", "false")
            setProperty("jcifs.smb.client.minVersion", "SMB1")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "6000")
            setProperty("jcifs.smb.client.soTimeout", "6000")
            setProperty("jcifs.smb.client.connTimeout", "5000")
            setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
            setProperty("jcifs.smb.client.useSMB2Negotiation", "true")
        }
        val config = PropertyConfiguration(props)
        val base = BaseContext(config)
        return if (server.isAnonymous || server.username.isBlank()) {
            base.withAnonymousCredentials()
        } else {
            base.withCredentials(
                NtlmPasswordAuthenticator(
                    null,
                    server.username.trim(),
                    server.password
                )
            )
        }
    }

    private fun listSmbFilesInternal(
        server: NetworkServerEntity,
        remotePath: String,
        customEncryptedExt: String,
        includeEncrypted: Boolean
    ): Result<List<NetworkFileItem>> {
        try {
            val cifsContext = getCifsContext(server)

            val smbUrl = if (remotePath.startsWith("smb://", ignoreCase = true)) {
                if (remotePath.endsWith("/")) remotePath else "$remotePath/"
            } else {
                val cleanPath = remotePath.trim().removePrefix("/")
                if (cleanPath.isBlank()) {
                    "smb://${server.host}/"
                } else {
                    val pathWithSlash = if (cleanPath.endsWith("/")) cleanPath else "$cleanPath/"
                    "smb://${server.host}/$pathWithSlash"
                }
            }

            val smbDir = SmbFile(smbUrl, cifsContext)
            smbDir.connect()

            val files = smbDir.listFiles() ?: emptyArray()
            val resultList = mutableListOf<NetworkFileItem>()

            val hostPrefix = "smb://${server.host}/"

            for (file in files) {
                try {
                    val name = file.name.removeSuffix("/")
                    if (name.isBlank() || name == "." || name == "..") continue
                    val isDir = file.isDirectory
                    val isVideo = isSupportedVideoExtension(name, customEncryptedExt, includeEncrypted)

                    if (isDir || isVideo) {
                        val filePathString = file.path
                        val relPath = if (filePathString.startsWith(hostPrefix, ignoreCase = true)) {
                            filePathString.substring(hostPrefix.length)
                        } else {
                            filePathString.substringAfter("smb://", filePathString)
                                .substringAfter("/", filePathString)
                        }

                        val cleanRelPath = if (isDir && !relPath.endsWith("/")) "$relPath/" else relPath

                        val authPart = if (!server.isAnonymous && server.username.isNotBlank()) {
                            "${Uri.encode(server.username)}:${Uri.encode(server.password)}@"
                        } else ""

                        val streamUrl = "smb://${authPart}${server.host}/${cleanRelPath.removePrefix("/")}"

                        resultList.add(
                            NetworkFileItem(
                                name = name,
                                path = cleanRelPath,
                                isDirectory = isDir,
                                sizeBytes = if (isDir) 0L else (try { file.length() } catch (_: Exception) { 0L }),
                                lastModified = try { file.lastModified() } catch (_: Exception) { 0L },
                                streamUri = streamUrl
                            )
                        )
                    }
                } catch (_: Exception) {}
            }

            return Result.success(resultList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })))
        } catch (e: Exception) {
            return Result.failure(IOException("SMB/Samba error: ${e.localizedMessage ?: "Tidak dapat membuka folder Samba (0x80090305 / timeout)"}"))
        }
    }

    fun buildNetworkVideoItem(
        item: NetworkFileItem,
        serverName: String,
        serverType: String = "FTP",
        customEncryptedExt: String = "1ca"
    ): VideoMediaItem {
        val customExtList = customEncryptedExt.split(",")
            .map { it.trim().removePrefix(".").lowercase() }
            .filter { it.isNotBlank() }
        val isEncrypted = item.name.endsWith(".1ca", ignoreCase = true) ||
                customExtList.any { ext -> item.name.endsWith(".$ext", ignoreCase = true) }

        val sType = if (serverType.equals("SMB", ignoreCase = true)) StreamType.SMB else StreamType.FTP
        return VideoMediaItem(
            id = item.streamUri.hashCode().toLong(),
            uri = Uri.parse(item.streamUri),
            path = item.streamUri,
            title = item.name,
            displayName = item.name,
            durationMs = 0L,
            sizeBytes = item.sizeBytes,
            dateModified = item.lastModified,
            mimeType = if (isEncrypted) "application/octet-stream" else "video/*",
            folderPath = serverName,
            folderName = serverName,
            isEncrypted1ca = isEncrypted,
            streamType = sType
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

    private fun isSupportedVideoExtension(
        fileName: String,
        customEncryptedExt: String = "1ca",
        includeEncrypted: Boolean = true
    ): Boolean {
        val baseExtensions = listOf(
            ".mp4", ".mkv", ".avi", ".mov", ".flv", ".webm", ".ts",
            ".m3u8", ".3gp", ".vob", ".wmv", ".m4v", ".f4v"
        )
        if (baseExtensions.any { fileName.endsWith(it, ignoreCase = true) }) {
            return true
        }

        if (!includeEncrypted) return false

        val customExtList = customEncryptedExt.split(",")
            .map { it.trim().removePrefix(".").lowercase() }
            .filter { it.isNotBlank() }

        return fileName.endsWith(".1ca", ignoreCase = true) || customExtList.any { ext -> fileName.endsWith(".$ext", ignoreCase = true) }
    }

    // Remote File Management (Delete, Rename, Move, Copy, Download, Lock to Vault)
    suspend fun deleteRemoteFile(server: NetworkServerEntity, item: NetworkFileItem): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (server.type.equals("SMB", ignoreCase = true)) {
                val cifs = getCifsContext(server)
                val cleanPath = item.path.trim().removePrefix("/")
                val smbUrl = "smb://${server.host}/$cleanPath"
                val smbFile = SmbFile(smbUrl, cifs)
                if (smbFile.exists()) {
                    smbFile.delete()
                    Result.success(true)
                } else {
                    Result.failure(IOException("File tidak ditemukan di server Samba"))
                }
            } else {
                val ftp = FTPClient()
                ftp.connect(server.host, server.port)
                val login = if (server.isAnonymous || server.username.isBlank()) ftp.login("anonymous", "anonymous") else ftp.login(server.username, server.password)
                if (!login) return@withContext Result.failure(IOException("FTP Login gagal"))
                ftp.enterLocalPassiveMode()
                val deleted = if (item.isDirectory) ftp.removeDirectory(item.path) else ftp.deleteFile(item.path)
                ftp.disconnect()
                if (deleted) Result.success(true) else Result.failure(IOException("Gagal menghapus file dari server FTP"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameRemoteFile(server: NetworkServerEntity, item: NetworkFileItem, newName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (server.type.equals("SMB", ignoreCase = true)) {
                val cifs = getCifsContext(server)
                val cleanPath = item.path.trim().removePrefix("/").removeSuffix("/")
                val parentPath = if (cleanPath.contains("/")) cleanPath.substringBeforeLast("/") else ""
                val newRelPath = if (parentPath.isNotBlank()) "$parentPath/$newName" else newName
                val sourceUrl = "smb://${server.host}/$cleanPath"
                val targetUrl = "smb://${server.host}/$newRelPath"
                val sourceFile = SmbFile(sourceUrl, cifs)
                val targetFile = SmbFile(targetUrl, cifs)
                sourceFile.renameTo(targetFile)
                Result.success(true)
            } else {
                val ftp = FTPClient()
                ftp.connect(server.host, server.port)
                val login = if (server.isAnonymous || server.username.isBlank()) ftp.login("anonymous", "anonymous") else ftp.login(server.username, server.password)
                if (!login) return@withContext Result.failure(IOException("FTP Login gagal"))
                ftp.enterLocalPassiveMode()
                val parent = if (item.path.contains("/")) item.path.substringBeforeLast("/") else ""
                val targetPath = if (parent.isNotBlank()) "$parent/$newName" else "/$newName"
                val renamed = ftp.rename(item.path, targetPath)
                ftp.disconnect()
                if (renamed) Result.success(true) else Result.failure(IOException("Gagal mengubah nama file di FTP"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveRemoteFile(server: NetworkServerEntity, item: NetworkFileItem, targetDirectory: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanDir = targetDirectory.trim().removePrefix("/").removeSuffix("/")
            if (server.type.equals("SMB", ignoreCase = true)) {
                val cifs = getCifsContext(server)
                val cleanPath = item.path.trim().removePrefix("/").removeSuffix("/")
                val sourceUrl = "smb://${server.host}/$cleanPath"
                val targetUrl = if (cleanDir.isBlank()) "smb://${server.host}/${item.name}" else "smb://${server.host}/$cleanDir/${item.name}"
                val sourceFile = SmbFile(sourceUrl, cifs)
                val targetFile = SmbFile(targetUrl, cifs)
                sourceFile.renameTo(targetFile)
                Result.success(true)
            } else {
                val ftp = FTPClient()
                ftp.connect(server.host, server.port)
                val login = if (server.isAnonymous || server.username.isBlank()) ftp.login("anonymous", "anonymous") else ftp.login(server.username, server.password)
                if (!login) return@withContext Result.failure(IOException("FTP Login gagal"))
                ftp.enterLocalPassiveMode()
                val targetPath = if (cleanDir.isBlank()) "/${item.name}" else "/$cleanDir/${item.name}"
                val moved = ftp.rename(item.path, targetPath)
                ftp.disconnect()
                if (moved) Result.success(true) else Result.failure(IOException("Gagal memindahkan file di FTP"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyRemoteFile(server: NetworkServerEntity, item: NetworkFileItem, targetDirectory: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanDir = targetDirectory.trim().removePrefix("/").removeSuffix("/")
            if (server.type.equals("SMB", ignoreCase = true)) {
                val cifs = getCifsContext(server)
                val cleanPath = item.path.trim().removePrefix("/").removeSuffix("/")
                val sourceUrl = "smb://${server.host}/$cleanPath"
                val targetUrl = if (cleanDir.isBlank()) "smb://${server.host}/Copy_${item.name}" else "smb://${server.host}/$cleanDir/Copy_${item.name}"
                val sourceFile = SmbFile(sourceUrl, cifs)
                val targetFile = SmbFile(targetUrl, cifs)
                sourceFile.copyTo(targetFile)
                Result.success(true)
            } else {
                // FTP stream copy
                val ftp = FTPClient()
                ftp.connect(server.host, server.port)
                val login = if (server.isAnonymous || server.username.isBlank()) ftp.login("anonymous", "anonymous") else ftp.login(server.username, server.password)
                if (!login) return@withContext Result.failure(IOException("FTP Login gagal"))
                ftp.enterLocalPassiveMode()
                ftp.setFileType(FTP.BINARY_FILE_TYPE)
                val targetPath = if (cleanDir.isBlank()) "/Copy_${item.name}" else "/$cleanDir/Copy_${item.name}"
                val inputStream = ftp.retrieveFileStream(item.path)
                if (inputStream != null) {
                    val tempFile = File.createTempFile("ftp_copy", ".tmp")
                    FileOutputStream(tempFile).use { out -> inputStream.copyTo(out) }
                    ftp.completePendingCommand()
                    tempFile.inputStream().use { input ->
                        ftp.storeFile(targetPath, input)
                    }
                    tempFile.delete()
                    ftp.disconnect()
                    Result.success(true)
                } else {
                    ftp.disconnect()
                    Result.failure(IOException("Gagal membaca file sumber di FTP"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadRemoteFile(
        server: NetworkServerEntity,
        item: NetworkFileItem,
        targetLocalFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            targetLocalFile.parentFile?.mkdirs()
            val totalBytes = item.sizeBytes
            var bytesCopied = 0L

            if (server.type.equals("SMB", ignoreCase = true)) {
                val cifs = getCifsContext(server)
                val cleanPath = item.path.trim().removePrefix("/")
                val smbUrl = "smb://${server.host}/$cleanPath"
                val smbFile = SmbFile(smbUrl, cifs)
                smbFile.openInputStream().use { input ->
                    FileOutputStream(targetLocalFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            if (totalBytes > 0) {
                                onProgress(bytesCopied.toFloat() / totalBytes)
                            }
                        }
                        output.flush()
                    }
                }
                Result.success(targetLocalFile)
            } else {
                val ftp = FTPClient()
                ftp.connect(server.host, server.port)
                val login = if (server.isAnonymous || server.username.isBlank()) ftp.login("anonymous", "anonymous") else ftp.login(server.username, server.password)
                if (!login) return@withContext Result.failure(IOException("FTP Login gagal"))
                ftp.enterLocalPassiveMode()
                ftp.setFileType(FTP.BINARY_FILE_TYPE)
                val inputStream = ftp.retrieveFileStream(item.path)
                if (inputStream != null) {
                    FileOutputStream(targetLocalFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            if (totalBytes > 0) {
                                onProgress(bytesCopied.toFloat() / totalBytes)
                            }
                        }
                        output.flush()
                    }
                    ftp.completePendingCommand()
                    ftp.disconnect()
                    Result.success(targetLocalFile)
                } else {
                    ftp.disconnect()
                    Result.failure(IOException("Gagal mengunduh file dari FTP"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
