package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "media_player_settings")

class UserPreferencesManager(private val context: Context) {

    companion object {
        val KEY_PIN_CODE = stringPreferencesKey("pin_code")
        val KEY_VAULT_EXTENSION = stringPreferencesKey("vault_file_extension")
        val KEY_DEFAULT_DECODER = stringPreferencesKey("default_decoder")
        val KEY_DEFAULT_ASPECT = stringPreferencesKey("default_aspect")
        val KEY_AUTO_SCAN = booleanPreferencesKey("auto_scan_startup")
        val KEY_RESUME_PLAYBACK = booleanPreferencesKey("resume_playback")
        val KEY_SKIP_INTERVAL = intPreferencesKey("skip_interval_seconds")
        val KEY_SORT_OPTION = stringPreferencesKey("sort_option")
        val KEY_VIEW_MODE = stringPreferencesKey("view_mode")
        val KEY_FOLLOW_ORIENTATION = booleanPreferencesKey("follow_orientation")
        val KEY_SWIPE_GESTURES = booleanPreferencesKey("swipe_gestures")
        val KEY_FAST_SEEK_SMOOTH = booleanPreferencesKey("fast_seek_smooth")
    }

    val vaultSecurityManager = VaultSecurityManager(context)

    val pinCodeFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_PIN_CODE]
    }

    val vaultExtensionFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_VAULT_EXTENSION] ?: "1ca"
    }

    val defaultDecoderFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEFAULT_DECODER] ?: "HW"
    }

    val sortOptionFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SORT_OPTION] ?: "DATE_DESC"
    }

    val viewModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_VIEW_MODE] ?: "GRID"
    }

    val autoScanFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_SCAN] ?: true
    }

    val resumePlaybackFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_RESUME_PLAYBACK] ?: true
    }

    val skipIntervalFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_SKIP_INTERVAL] ?: 10
    }

    val followOrientationFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FOLLOW_ORIENTATION] ?: true
    }

    val swipeGesturesFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SWIPE_GESTURES] ?: true
    }

    val fastSeekSmoothFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FAST_SEEK_SMOOTH] ?: true
    }

    suspend fun setPinCode(pin: String) {
        vaultSecurityManager.savePin(pin)
    }

    suspend fun setVaultExtension(extension: String) {
        val cleanExt = extension.trim().removePrefix(".").lowercase().ifBlank { "1ca" }
        context.dataStore.edit { preferences ->
            preferences[KEY_VAULT_EXTENSION] = cleanExt
        }
    }

    suspend fun clearPinCode() {
        vaultSecurityManager.clearPin()
    }

    suspend fun setDefaultDecoder(decoder: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEFAULT_DECODER] = decoder
        }
    }

    suspend fun setSortOption(sort: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SORT_OPTION] = sort
        }
    }

    suspend fun setViewMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_VIEW_MODE] = mode
        }
    }

    suspend fun setAutoScan(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_SCAN] = enabled
        }
    }

    suspend fun setResumePlayback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RESUME_PLAYBACK] = enabled
        }
    }

    suspend fun setSkipInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SKIP_INTERVAL] = seconds
        }
    }

    suspend fun setFollowOrientation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FOLLOW_ORIENTATION] = enabled
        }
    }

    suspend fun setSwipeGestures(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SWIPE_GESTURES] = enabled
        }
    }
}
