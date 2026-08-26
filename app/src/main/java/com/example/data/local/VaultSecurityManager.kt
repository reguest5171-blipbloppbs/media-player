package com.example.data.local

import android.content.Context
import android.os.Environment
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * VaultSecurityManager handles PIN persistence across internal DataStore and
 * external persistent storage files.
 *
 * This ensures that even when the user performs "Hapus Data Aplikasi" (Clear App Data)
 * in Android Settings, the configured 4-digit PIN for .1ca Vault is safely preserved
 * and automatically restored upon next app launch.
 */
class VaultSecurityManager(private val context: Context) {

    companion object {
        private const val TAG = "VaultSecurityManager"
        private const val MAGIC_HEADER = "1CA_PIN_SEC_V2:"
        private const val AES_SALT = "MediaPlayer_1CA_Vault_Persistent_Salt_2026"
        private val IV_BYTES = byteArrayOf(
            0x1A.toByte(), 0x2B.toByte(), 0x3C.toByte(), 0x4D.toByte(),
            0x5E.toByte(), 0x6F.toByte(), 0x70.toByte(), 0x81.toByte(),
            0x92.toByte(), 0xA3.toByte(), 0xB4.toByte(), 0xC5.toByte(),
            0xD6.toByte(), 0xE7.toByte(), 0xF8.toByte(), 0x09.toByte()
        )
    }

    private fun getSecretKey(): SecretKeySpec {
        val sha = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha.digest(AES_SALT.toByteArray(StandardCharsets.UTF_8))
        val key16 = keyBytes.copyOf(16) // 128-bit AES key
        return SecretKeySpec(key16, "AES")
    }

    private fun encryptPayload(pin: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = getSecretKey()
            val ivSpec = IvParameterSpec(IV_BYTES)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val rawData = "$MAGIC_HEADER$pin:${System.currentTimeMillis()}".toByteArray(StandardCharsets.UTF_8)
            val encrypted = cipher.doFinal(rawData)
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting PIN payload", e)
            ""
        }
    }

    private fun decryptPayload(encryptedBase64: String): String? {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = getSecretKey()
            val ivSpec = IvParameterSpec(IV_BYTES)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val encryptedBytes = Base64.decode(encryptedBase64.trim(), Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val raw = String(decryptedBytes, StandardCharsets.UTF_8)
            if (raw.startsWith(MAGIC_HEADER)) {
                val parts = raw.removePrefix(MAGIC_HEADER).split(":")
                if (parts.isNotEmpty() && parts[0].length in 4..8) {
                    parts[0]
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting PIN payload", e)
            null
        }
    }

    /**
     * List of resilient persistent storage locations for PIN backup.
     * These files survive Android's "Clear Data" (Hapus Data Aplikasi).
     */
    private fun getPersistentKeyFiles(): List<File> {
        val files = mutableListOf<File>()

        try {
            // 1. Primary: Hidden folder on External Storage root
            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null) {
                files.add(File(extRoot, ".mediaplayer_vault/.vault_pin_security.sec"))
            }
        } catch (_: Exception) {}

        try {
            // 2. Documents directory
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docs != null) {
                files.add(File(docs, ".mediaplayer_vault_sec.dat"))
            }
        } catch (_: Exception) {}

        try {
            // 3. Movies directory
            val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (movies != null) {
                files.add(File(movies, ".1ca_vault_pin.sec"))
            }
        } catch (_: Exception) {}

        try {
            // 4. App internal files (fast local fallback)
            files.add(File(context.filesDir, ".vault_pin.sec"))
        } catch (_: Exception) {}

        return files
    }

    /**
     * Retrieves the PIN. If DataStore is empty (e.g. after Clear Data),
     * it automatically searches external persistent files and restores the PIN to DataStore.
     */
    suspend fun getOrRestorePin(): String? = withContext(Dispatchers.IO) {
        // 1. Check DataStore first
        var pin: String? = null
        try {
            pin = context.dataStore.data.first()[UserPreferencesManager.KEY_PIN_CODE]
        } catch (e: Exception) {
            Log.w(TAG, "DataStore read error: ${e.message}")
        }

        if (!pin.isNullOrBlank()) {
            // Ensure external files are also in sync
            syncToExternalFiles(pin)
            return@withContext pin
        }

        // 2. DataStore has no PIN (could be fresh install or user tapped 'Hapus Data Aplikasi')
        // Check external persistent storage
        for (file in getPersistentKeyFiles()) {
            if (file.exists() && file.canRead()) {
                try {
                    val content = file.readText(StandardCharsets.UTF_8).trim()
                    val recoveredPin = decryptPayload(content)
                    if (!recoveredPin.isNullOrBlank()) {
                        Log.i(TAG, "Successfully restored PIN from persistent backup: ${file.absolutePath}")
                        // Restore into DataStore
                        savePinToDataStore(recoveredPin)
                        // Sync to any missing backup locations
                        syncToExternalFiles(recoveredPin)
                        return@withContext recoveredPin
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read backup file ${file.name}: ${e.message}")
                }
            }
        }

        return@withContext null
    }

    /**
     * Saves the new PIN to DataStore and all external persistent locations.
     */
    suspend fun savePin(newPin: String) = withContext(Dispatchers.IO) {
        savePinToDataStore(newPin)
        syncToExternalFiles(newPin)
    }

    /**
     * Clears the PIN from DataStore and deletes all external persistent security files.
     */
    suspend fun clearPin() = withContext(Dispatchers.IO) {
        try {
            context.dataStore.edit { preferences ->
                preferences.remove(UserPreferencesManager.KEY_PIN_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing DataStore PIN", e)
        }

        for (file in getPersistentKeyFiles()) {
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting backup file: ${file.name}", e)
            }
        }
    }

    /**
     * Verifies if the entered PIN matches either DataStore or external persistent storage.
     */
    suspend fun verifyPin(enteredPin: String): Boolean = withContext(Dispatchers.IO) {
        val currentPin = getOrRestorePin()
        return@withContext currentPin != null && currentPin == enteredPin
    }

    private suspend fun savePinToDataStore(pin: String) {
        try {
            context.dataStore.edit { preferences ->
                preferences[UserPreferencesManager.KEY_PIN_CODE] = pin
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving PIN to DataStore", e)
        }
    }

    private fun syncToExternalFiles(pin: String) {
        val encryptedPayload = encryptPayload(pin)
        if (encryptedPayload.isBlank()) return

        for (file in getPersistentKeyFiles()) {
            try {
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                file.writeText(encryptedPayload, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                // Log and continue to next location
                Log.d(TAG, "Could not write to ${file.absolutePath}: ${e.message}")
            }
        }
    }
}
