package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.model.VideoMediaItem
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PlayerViewModel

enum class AppScreen {
    LIBRARY,
    PLAYER,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.LIBRARY) }

                // Check for incoming intent (external video open or .1ca file)
                LaunchedEffect(intent) {
                    handleIncomingIntent(intent) { mediaItem ->
                        playerViewModel.playMediaItem(mediaItem, listOf(mediaItem))
                        currentScreen = AppScreen.PLAYER
                    }
                }

                BackHandler(enabled = currentScreen != AppScreen.LIBRARY) {
                    if (currentScreen == AppScreen.PLAYER) {
                        playerViewModel.playerManager.pause()
                        currentScreen = AppScreen.LIBRARY
                    } else if (currentScreen == AppScreen.SETTINGS) {
                        currentScreen = AppScreen.LIBRARY
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            AppScreen.LIBRARY -> {
                                LibraryScreen(
                                    viewModel = mainViewModel,
                                    onPlayVideo = { video, playlist ->
                                        playerViewModel.playMediaItem(video, playlist)
                                        currentScreen = AppScreen.PLAYER
                                    },
                                    onOpenSettings = {
                                        currentScreen = AppScreen.SETTINGS
                                    }
                                )
                            }
                            AppScreen.PLAYER -> {
                                PlayerScreen(
                                    viewModel = playerViewModel,
                                    onBack = {
                                        playerViewModel.playerManager.pause()
                                        currentScreen = AppScreen.LIBRARY
                                    }
                                )
                            }
                            AppScreen.SETTINGS -> {
                                SettingsScreen(
                                    onBack = {
                                        currentScreen = AppScreen.LIBRARY
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent) { mediaItem ->
            playerViewModel.playMediaItem(mediaItem, listOf(mediaItem))
        }
    }

    private fun handleIncomingIntent(intent: Intent?, onVideoReady: (VideoMediaItem) -> Unit) {
        val uri = intent?.data ?: return
        val scheme = uri.scheme ?: ""
        val fileName = uri.lastPathSegment ?: "External Video"
        val isEncrypted = fileName.endsWith(".1ca", ignoreCase = true)

        val item = VideoMediaItem(
            id = System.currentTimeMillis(),
            uri = uri,
            path = uri.path ?: "",
            displayName = fileName,
            title = fileName.substringBeforeLast("."),
            durationMs = 0L,
            sizeBytes = 0L,
            dateModified = System.currentTimeMillis(),
            mimeType = intent.type ?: "video/*",
            isEncrypted1ca = isEncrypted
        )
        onVideoReady(item)
    }
}
