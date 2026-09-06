package com.example

import android.app.Application
import android.util.Log
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary

class PlayerApplication : Application() {

    override fun onCreate() {
        super.onCreate()

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
            val available = try { FfmpegLibrary.isAvailable() } catch (_: Throwable) { false }
            Log.i("PlayerApplication", "FFmpeg native libraries loaded. isAvailable: $available")
        } catch (t: Throwable) {
            Log.e("PlayerApplication", "Error during FFmpeg native preloading: ${t.message}", t)
        }
    }
}
