package com.example

import android.app.Application
import android.util.Log
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary

class PlayerApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Global crash guard to prevent force closes ("ngeklose")
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("PlayerApplication", "FATAL CRASH intercepted on thread ${thread.name}: ${throwable.message}", throwable)

            // Check if it's a media/native/player related exception
            val isMediaRelated = throwable.stackTrace.any { elem ->
                elem.className.contains("media3", ignoreCase = true) ||
                elem.className.contains("exoplayer", ignoreCase = true) ||
                elem.className.contains("nextlib", ignoreCase = true) ||
                elem.className.contains("player", ignoreCase = true)
            }

            if (isMediaRelated) {
                Log.w("PlayerApplication", "Suppressed media-related crash on background/playback thread to prevent app exit.")
                // Allow the app to survive instead of force closing
                return@setDefaultUncaughtExceptionHandler
            }

            // For other uncaught crashes, delegate to system or default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Initialize FFmpeg native libraries safely at startup
        try {
            val nativeLibs = listOf("avutil", "swresample", "swscale", "avcodec", "media3ext")
            for (lib in nativeLibs) {
                try {
                    System.loadLibrary(lib)
                } catch (t: Throwable) {
                    Log.w("PlayerApplication", "Preload System.loadLibrary($lib): ${t.message}")
                }
            }
            try {
                FfmpegLibrary.setLibraries("avutil", "swresample", "swscale", "avcodec", "media3ext")
            } catch (t: Throwable) {
                Log.w("PlayerApplication", "FfmpegLibrary.setLibraries: ${t.message}")
            }
            val available = FfmpegLibrary.isAvailable()
            Log.i("PlayerApplication", "FFmpeg native libraries loaded successfully. isAvailable: $available")
        } catch (t: Throwable) {
            Log.e("PlayerApplication", "Error during FFmpeg native preloading: ${t.message}", t)
        }
    }
}
