package com.example.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.IOException
import java.io.InputStream

@OptIn(UnstableApi::class)
class FtpDataSource : BaseDataSource(/* isNetwork = */ true) {

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = FtpDataSource()
    }

    private var ftpClient: FTPClient? = null
    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        val rawUri = dataSpec.uri
        val host = rawUri.host ?: throw IOException("Invalid FTP host in URI: $rawUri")
        val port = if (rawUri.port > 0) rawUri.port else 21

        var user = "anonymous"
        var pass = "anonymous"
        val userInfo = rawUri.userInfo
        if (!userInfo.isNullOrBlank() && userInfo.contains(":")) {
            val parts = userInfo.split(":", limit = 2)
            user = Uri.decode(parts[0])
            pass = Uri.decode(parts[1])
        }

        val fullPath = rawUri.path ?: "/"

        val ftp = FTPClient()
        ftp.connectTimeout = 8000
        ftp.defaultTimeout = 8000
        ftp.setDataTimeout(java.time.Duration.ofMillis(8000))

        ftp.connect(host, port)
        val loginOk = ftp.login(user, pass)
        if (!loginOk) {
            ftp.disconnect()
            throw IOException("FTP Login gagal untuk user: $user")
        }

        ftp.enterLocalPassiveMode()
        ftp.setFileType(FTP.BINARY_FILE_TYPE)

        if (dataSpec.position > 0) {
            ftp.setRestartOffset(dataSpec.position)
        }

        val stream = ftp.retrieveFileStream(fullPath)
            ?: throw IOException("Gagal membuka file stream FTP: $fullPath")

        inputStream = stream
        ftpClient = ftp

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            C.LENGTH_UNSET.toLong()
        }

        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(bytesRemaining, length.toLong()).toInt()
        }

        val stream = inputStream ?: return C.RESULT_END_OF_INPUT
        val bytesRead = stream.read(buffer, offset, bytesToRead)
        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        inputStream = null

        try {
            ftpClient?.completePendingCommand()
            ftpClient?.logout()
            ftpClient?.disconnect()
        } catch (_: Exception) {}
        ftpClient = null

        transferEnded()
    }
}
