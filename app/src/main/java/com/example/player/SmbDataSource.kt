package com.example.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.InputStream
import java.util.Properties

class SmbDataSource(
    private val cifsContext: CIFSContext? = null
) : BaseDataSource(/* isNetwork = */ true) {

    private var smbFile: SmbFile? = null
    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        val rawUri = dataSpec.uri
        val context = cifsContext ?: createDefaultCifsContext(rawUri)

        val cleanUrl = if (rawUri.userInfo != null) {
            val scheme = rawUri.scheme ?: "smb"
            val host = rawUri.host ?: ""
            val port = if (rawUri.port > 0) ":${rawUri.port}" else ""
            val path = rawUri.path ?: ""
            "$scheme://$host$port$path"
        } else {
            rawUri.toString()
        }

        val smb = SmbFile(cleanUrl, context)
        smbFile = smb

        val totalLength = try { smb.length() } catch (_: Exception) { C.LENGTH_UNSET.toLong() }
        val stream = smb.inputStream
        if (dataSpec.position > 0) {
            stream.skip(dataSpec.position)
        }
        inputStream = stream

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (totalLength != C.LENGTH_UNSET.toLong()) {
            totalLength - dataSpec.position
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
        smbFile = null
        transferEnded()
    }

    companion object {
        fun createDefaultCifsContext(uri: Uri? = null): CIFSContext {
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

            val userInfo = uri?.userInfo
            return if (!userInfo.isNullOrBlank() && userInfo.contains(":")) {
                val parts = userInfo.split(":", limit = 2)
                val user = parts[0]
                val pass = if (parts.size > 1) parts[1] else ""
                base.withCredentials(NtlmPasswordAuthenticator(null, user, pass))
            } else {
                base.withAnonymousCredentials()
            }
        }
    }

    class Factory(
        private val cifsContext: CIFSContext? = null
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return SmbDataSource(cifsContext)
        }
    }
}
