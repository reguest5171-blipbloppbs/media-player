package com.example.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

object EncryptionUtil {
    private const val HEADER_MAGIC = "1CA_MEDIA_VAULT_V1\n"
    private val HEADER_BYTES = HEADER_MAGIC.toByteArray(Charsets.UTF_8)
    private const val XOR_KEY: Byte = 0x5A

    private val inMemoryCache = ConcurrentHashMap<String, File>()

    fun getCachedThumbnailFile(context: Context, file: File): File? {
        val mem = inMemoryCache[file.absolutePath]
        if (mem != null && mem.exists() && mem.length() > 0) {
            return mem
        }
        val cacheDir = File(context.cacheDir, "vault_thumbs")
        val thumbFile = File(cacheDir, "${file.name}.jpg")
        if (thumbFile.exists() && thumbFile.length() > 0) {
            inMemoryCache[file.absolutePath] = thumbFile
            return thumbFile
        }
        return null
    }

    suspend fun getOrExtractThumbnailFileAsync(context: Context, file: File): File? = withContext(Dispatchers.IO) {
        getOrExtractThumbnailFile(context, file)
    }

    fun getOrExtractThumbnailFile(context: Context, file: File): File? {
        val cached = getCachedThumbnailFile(context, file)
        if (cached != null) return cached

        return try {
            val cacheDir = File(context.cacheDir, "vault_thumbs").apply { mkdirs() }
            val thumbFile = File(cacheDir, "${file.name}.jpg")
            if (thumbFile.exists() && thumbFile.length() > 0) {
                inMemoryCache[file.absolutePath] = thumbFile
                return thumbFile
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val retriever = MediaMetadataRetriever()
                val ds = object : MediaDataSource() {
                    val raf = RandomAccessFile(file, "r")
                    val isEncrypted = isEncrypted1caFile(file)
                    val headerSize = HEADER_BYTES.size
                    val xorSize = 4096

                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                        val virtualLen = if (isEncrypted) raf.length() - headerSize else raf.length()
                        if (position >= virtualLen) return -1
                        val actualOffset = if (isEncrypted) position + headerSize else position
                        raf.seek(actualOffset)
                        val toRead = minOf(size.toLong(), virtualLen - position).toInt()
                        val read = raf.read(buffer, offset, toRead)
                        if (read <= 0) return -1
                        if (isEncrypted && position < xorSize) {
                            for (i in 0 until read) {
                                val p = position + i
                                if (p < xorSize) {
                                    buffer[offset + i] = (buffer[offset + i].toInt() xor XOR_KEY.toInt()).toByte()
                                }
                            }
                        }
                        return read
                    }

                    override fun getSize(): Long = if (isEncrypted) raf.length() - headerSize else raf.length()
                    override fun close() {
                        try { raf.close() } catch (_: Exception) {}
                    }
                }
                retriever.setDataSource(ds)
                val bitmap = retriever.getFrameAtTime(2000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                retriever.release()
                ds.close()

                if (bitmap != null) {
                    val fos = FileOutputStream(thumbFile)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                    fos.flush()
                    fos.close()
                    inMemoryCache[file.absolutePath] = thumbFile
                    return thumbFile
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun isEncrypted1caFile(file: File): Boolean {
        if (!file.exists() || file.length() < HEADER_BYTES.size) return false
        if (file.name.endsWith(".1ca", ignoreCase = true)) return true
        return try {
            val raf = RandomAccessFile(file, "r")
            val buffer = ByteArray(HEADER_BYTES.size)
            raf.readFully(buffer)
            raf.close()
            buffer.contentEquals(HEADER_BYTES)
        } catch (e: Exception) {
            false
        }
    }

    fun encryptVideoTo1ca(sourceFile: File): File? {
        return try {
            val parent = sourceFile.parentFile ?: return null
            val newName = if (sourceFile.name.endsWith(".1ca", ignoreCase = true)) {
                sourceFile.name
            } else {
                "${sourceFile.name}.1ca"
            }
            val targetFile = File(parent, newName)
            
            // Fast header XOR encryption
            val raf = RandomAccessFile(sourceFile, "rw")
            val length = raf.length()
            val headerBlockSize = minOf(4096L, length).toInt()
            val buffer = ByteArray(headerBlockSize)
            raf.seek(0)
            raf.readFully(buffer)
            
            // Apply XOR mask to header
            for (i in buffer.indices) {
                buffer[i] = (buffer[i].toInt() xor XOR_KEY.toInt()).toByte()
            }
            
            // Write magic + XOR-ed header to target
            val tempFile = File(parent, ".tmp_${System.currentTimeMillis()}.1ca")
            val fos = FileOutputStream(tempFile)
            fos.write(HEADER_BYTES)
            fos.write(buffer)
            
            // Stream the remaining unencrypted body for zero latency
            if (length > headerBlockSize) {
                val inputStream = FileInputStream(sourceFile)
                inputStream.channel.position(headerBlockSize.toLong())
                val streamBuf = ByteArray(64 * 1024)
                var read: Int
                while (inputStream.read(streamBuf).also { read = it } != -1) {
                    fos.write(streamBuf, 0, read)
                }
                inputStream.close()
            }
            fos.flush()
            fos.close()
            raf.close()

            sourceFile.delete()
            tempFile.renameTo(targetFile)
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decrypt1caVideo(sourceFile: File): File? {
        return try {
            val parent = sourceFile.parentFile ?: return null
            val rawName = sourceFile.name
            val originalName = if (rawName.endsWith(".1ca", ignoreCase = true)) {
                rawName.substring(0, rawName.length - 4)
            } else {
                "${rawName}.mp4"
            }
            val targetFile = File(parent, originalName)

            val raf = RandomAccessFile(sourceFile, "r")
            val magicBuf = ByteArray(HEADER_BYTES.size)
            raf.readFully(magicBuf)

            if (!magicBuf.contentEquals(HEADER_BYTES)) {
                // If it was only renamed without header magic, just rename back
                raf.close()
                sourceFile.renameTo(targetFile)
                return targetFile
            }

            val headerBlockSize = minOf(4096L, raf.length() - HEADER_BYTES.size).toInt()
            val buffer = ByteArray(headerBlockSize)
            raf.readFully(buffer)

            for (i in buffer.indices) {
                buffer[i] = (buffer[i].toInt() xor XOR_KEY.toInt()).toByte()
            }

            val tempFile = File(parent, ".tmp_dec_${System.currentTimeMillis()}")
            val fos = FileOutputStream(tempFile)
            fos.write(buffer)

            val remainingStream = FileInputStream(sourceFile)
            remainingStream.channel.position((HEADER_BYTES.size + headerBlockSize).toLong())
            val streamBuf = ByteArray(64 * 1024)
            var read: Int
            while (remainingStream.read(streamBuf).also { read = it } != -1) {
                fos.write(streamBuf, 0, read)
            }
            remainingStream.close()
            fos.flush()
            fos.close()
            raf.close()

            sourceFile.delete()
            tempFile.renameTo(targetFile)
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getDecryptedStreamDataSourceFactory(): DataSource.Factory {
        return DataSource.Factory {
            Encrypted1caDataSource()
        }
    }
}

class Encrypted1caDataSource : DataSource {
    private var randomAccessFile: RandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var isEncrypted: Boolean = false
    private val headerMagicSize = 19 // "1CA_MEDIA_VAULT_V1\n".length
    private val xorHeaderBlock = 4096

    override fun addTransferListener(transferListener: TransferListener) {}

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val path = uri?.path ?: throw IllegalArgumentException("Invalid URI")
        val file = File(path)
        randomAccessFile = RandomAccessFile(file, "r")

        // Check header magic
        if (file.length() >= headerMagicSize) {
            val magicBuf = ByteArray(headerMagicSize)
            randomAccessFile?.seek(0)
            randomAccessFile?.readFully(magicBuf)
            val magic = String(magicBuf, Charsets.UTF_8)
            isEncrypted = magic.startsWith("1CA_MEDIA_VAULT_V1")
        } else {
            isEncrypted = false
        }

        val fileLength = file.length()
        val virtualLength = if (isEncrypted) fileLength - headerMagicSize else fileLength

        if (dataSpec.position > virtualLength) {
            throw IllegalArgumentException("Position exceeds length")
        }

        val actualOffset = if (isEncrypted) dataSpec.position + headerMagicSize else dataSpec.position
        randomAccessFile?.seek(actualOffset)

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            virtualLength - dataSpec.position
        }

        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = minOf(length.toLong(), bytesRemaining).toInt()
        val currentFilePos = randomAccessFile?.filePointer ?: 0L
        val bytesRead = randomAccessFile?.read(buffer, offset, bytesToRead) ?: -1

        if (bytesRead <= 0) return C.RESULT_END_OF_INPUT

        // If reading within the encrypted header block
        if (isEncrypted && currentFilePos < (headerMagicSize + xorHeaderBlock)) {
            val startInXor = maxOf(0L, currentFilePos - headerMagicSize)
            val endInXor = minOf(xorHeaderBlock.toLong(), (currentFilePos + bytesRead) - headerMagicSize)
            val relativeOffset = (startInXor - (currentFilePos - headerMagicSize)).toInt()

            for (i in 0 until bytesRead) {
                val absolutePosInPayload = (currentFilePos - headerMagicSize) + i
                if (absolutePosInPayload in 0 until xorHeaderBlock) {
                    buffer[offset + i] = (buffer[offset + i].toInt() xor 0x5A).toByte()
                }
            }
        }

        bytesRemaining -= bytesRead
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            randomAccessFile?.close()
        } finally {
            randomAccessFile = null
        }
    }
}
