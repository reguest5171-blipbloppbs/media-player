package com.example.player

import android.net.Uri
import jcifs.CIFSContext
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance local HTTP loopback proxy (127.0.0.1) for SMB and FTP network media.
 * Enables both LibVLC (SW) and ExoPlayer (HW/HW+) to stream HEVC/H.265 videos without stalling,
 * supporting full byte-range seeking (HTTP 206 Partial Content).
 */
object LocalMediaServer {

    private var serverSocket: ServerSocket? = null
    private var serverPort: Int = 0
    private var job: Job? = null
    private val activeStreams = ConcurrentHashMap<String, StreamSource>()

    interface StreamSource {
        val totalLength: Long
        val mimeType: String
        fun readRange(start: Long, length: Long, output: OutputStream)
    }

    @Synchronized
    fun start(): Int {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            return serverPort
        }

        try {
            val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = s
            serverPort = s.localPort

            job = CoroutineScope(Dispatchers.IO).launch {
                while (isActive && !s.isClosed) {
                    try {
                        val client = s.accept()
                        launch(Dispatchers.IO) {
                            handleClient(client)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
            return serverPort
        } catch (e: Exception) {
            android.util.Log.e("LocalMediaServer", "Failed to start local media server: ${e.message}")
            return -1
        }
    }

    fun registerSmbStream(smbUrl: String, cifsContext: CIFSContext): String {
        val port = start()
        val key = "smb_" + Math.abs(smbUrl.hashCode())

        activeStreams[key] = object : StreamSource {
            private val smbFile = SmbFile(smbUrl, cifsContext)
            override val totalLength: Long
                get() = try { smbFile.length() } catch (_: Exception) { -1L }

            override val mimeType: String
                get() {
                    val name = smbFile.name.lowercase()
                    return when {
                        name.endsWith(".mp4") -> "video/mp4"
                        name.endsWith(".mkv") -> "video/x-matroska"
                        name.endsWith(".ts") -> "video/mp2t"
                        name.endsWith(".webm") -> "video/webm"
                        name.endsWith(".avi") -> "video/x-msvideo"
                        name.endsWith(".mov") -> "video/quicktime"
                        else -> "video/*"
                    }
                }

            override fun readRange(start: Long, length: Long, output: OutputStream) {
                var raf: SmbRandomAccessFile? = null
                try {
                    raf = SmbRandomAccessFile(smbFile, "r")
                    if (start > 0) {
                        raf.seek(start)
                    }
                    val buffer = ByteArray(64 * 1024)
                    var remaining = length
                    while (remaining > 0) {
                        val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                        val bytesRead = raf.read(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        remaining -= bytesRead
                    }
                    output.flush()
                } finally {
                    try { raf?.close() } catch (_: Exception) {}
                }
            }
        }

        return "http://127.0.0.1:$port/$key"
    }

    fun registerFtpStream(ftpUri: Uri, fallbackLength: Long = -1L): String {
        val port = start()
        val key = "ftp_" + Math.abs(ftpUri.toString().hashCode())

        activeStreams[key] = object : StreamSource {
            private val host = ftpUri.host ?: "127.0.0.1"
            private val ftpPort = if (ftpUri.port > 0) ftpUri.port else 21
            private var cachedLength: Long = if (fallbackLength > 0) fallbackLength else -1L

            private fun getCredentials(): Pair<String, String> {
                var user = "anonymous"
                var pass = "anonymous"
                val userInfo = ftpUri.userInfo
                if (!userInfo.isNullOrBlank() && userInfo.contains(":")) {
                    val parts = userInfo.split(":", limit = 2)
                    user = Uri.decode(parts[0])
                    pass = Uri.decode(parts[1])
                }
                return Pair(user, pass)
            }

            private fun connectFtp(): FTPClient {
                val ftp = FTPClient()
                ftp.controlEncoding = "UTF-8"
                ftp.connectTimeout = 8000
                ftp.defaultTimeout = 8000
                ftp.setDataTimeout(java.time.Duration.ofMillis(8000))
                ftp.connect(host, ftpPort)
                val (user, pass) = getCredentials()
                val ok = ftp.login(user, pass)
                if (!ok) {
                    ftp.disconnect()
                    throw java.io.IOException("FTP Login gagal untuk user: $user")
                }
                try {
                    ftp.sendCommand("OPTS UTF8", "ON")
                } catch (_: Exception) {}
                ftp.enterLocalPassiveMode()
                ftp.setFileType(FTP.BINARY_FILE_TYPE)
                return ftp
            }

            override val totalLength: Long
                get() {
                    if (cachedLength > 0) return cachedLength
                    var ftp: FTPClient? = null
                    try {
                        ftp = connectFtp()
                        val fullPath = Uri.decode(ftpUri.path ?: "/")
                        val files = ftp.listFiles(fullPath)
                        if (files != null && files.isNotEmpty()) {
                            cachedLength = files[0].size
                        }
                    } catch (_: Exception) {}
                    finally {
                        try { ftp?.disconnect() } catch (_: Exception) {}
                    }
                    return cachedLength
                }

            override val mimeType: String
                get() {
                    val name = (ftpUri.path ?: "").lowercase()
                    return when {
                        name.endsWith(".mp4") -> "video/mp4"
                        name.endsWith(".mkv") -> "video/x-matroska"
                        name.endsWith(".ts") -> "video/mp2t"
                        name.endsWith(".webm") -> "video/webm"
                        name.endsWith(".avi") -> "video/x-msvideo"
                        name.endsWith(".mov") -> "video/quicktime"
                        name.endsWith(".flv") -> "video/x-flv"
                        else -> "video/*"
                    }
                }

            override fun readRange(start: Long, length: Long, output: OutputStream) {
                var ftp: FTPClient? = null
                var inputStream: java.io.InputStream? = null
                try {
                    ftp = connectFtp()
                    if (start > 0) {
                        ftp.setRestartOffset(start)
                    }
                    val fullPath = Uri.decode(ftpUri.path ?: "/")
                    inputStream = ftp.retrieveFileStream(fullPath)
                        ?: throw java.io.IOException("Gagal membuka stream file FTP: $fullPath")

                    val buffer = ByteArray(64 * 1024)
                    var remaining = length
                    while (remaining > 0) {
                        val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                        val bytesRead = inputStream.read(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        remaining -= bytesRead
                    }
                    output.flush()
                } finally {
                    try { inputStream?.close() } catch (_: Exception) {}
                    try {
                        ftp?.completePendingCommand()
                        ftp?.logout()
                        ftp?.disconnect()
                    } catch (_: Exception) {}
                }
            }
        }

        return "http://127.0.0.1:$port/$key"
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = BufferedOutputStream(socket.getOutputStream())

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0)?.uppercase() ?: ""
            if (method != "GET" && method != "HEAD") {
                socket.close()
                return
            }

            val pathKey = parts.getOrNull(1)?.trim()?.removePrefix("/")?.substringBefore("?") ?: ""
            val source = activeStreams[pathKey]
            if (source == null) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()
                socket.close()
                return
            }

            var rangeStart = 0L
            var rangeEnd = -1L
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotBlank()) {
                val header = line!!
                if (header.startsWith("Range:", ignoreCase = true)) {
                    val rangeVal = header.substringAfter("bytes=").trim()
                    val rangeParts = rangeVal.split("-")
                    rangeStart = rangeParts[0].toLongOrNull() ?: 0L
                    if (rangeParts.size > 1 && rangeParts[1].isNotBlank()) {
                        rangeEnd = rangeParts[1].toLongOrNull() ?: -1L
                    }
                }
            }

            val total = source.totalLength
            val actualEnd = if (rangeEnd != -1L && rangeEnd < total) rangeEnd else (if (total > 0) total - 1 else -1L)
            val contentLength = if (actualEnd >= rangeStart) (actualEnd - rangeStart + 1) else 0L

            if (rangeStart > 0 || rangeEnd != -1L) {
                val responseHeaders = buildString {
                    append("HTTP/1.1 206 Partial Content\r\n")
                    append("Content-Type: ${source.mimeType}\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    if (total > 0) {
                        append("Content-Range: bytes $rangeStart-$actualEnd/$total\r\n")
                    }
                    append("Content-Length: $contentLength\r\n")
                    append("Connection: keep-alive\r\n\r\n")
                }
                out.write(responseHeaders.toByteArray())
                out.flush()
                if (method == "GET") {
                    source.readRange(rangeStart, contentLength, out)
                }
            } else {
                val responseHeaders = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: ${source.mimeType}\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    if (total > 0) {
                        append("Content-Length: $total\r\n")
                    }
                    append("Connection: keep-alive\r\n\r\n")
                }
                out.write(responseHeaders.toByteArray())
                out.flush()
                if (method == "GET") {
                    source.readRange(0, if (total > 0) total else Long.MAX_VALUE, out)
                }
            }
        } catch (_: Exception) {
            // Socket or client disconnect
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun stop() {
        try {
            job?.cancel()
            serverSocket?.close()
            serverSocket = null
            activeStreams.clear()
        } catch (_: Exception) {}
    }
}
