# MediaPlayer

A modern, highly optimized Android Media Player built with Jetpack Compose.

## 🚀 Key Features

- **Quad-Engine Architecture**: Uses LibVLC, FFmpeg, Android System (NuPlayer), and ExoPlayer to playback any media formats smoothly.
- **Hardware & Software Decoding**: Automatically uses Hardware Acceleration (HW+) when possible. You can manually force SW decoding or VLC decoding for difficult files.
- **Picture-in-Picture (PiP)**: Keep watching while using other apps.
- **Full Gestures**: Swipe left side to adjust brightness, swipe right side to adjust volume, swipe horizontally to seek.
- **File Manager Integration**: Browse your local files directly within the app and open any supported formats smoothly.
- **Secure Pin Lock**: Lock the app with a secure PIN.

## 📦 Download

Please check the latest releases on the GitHub Actions artifacts page.

## 🛠️ Build and Development

The app uses standard Android Gradle Plugin (AGP) and Jetpack Compose.

To build a release APK locally, run:

```bash
gradle :app:assembleRelease
```

> **Note on App Size**: The generated APK ranges from 30MB - 60MB. This is normal because it contains full C++ native shared libraries for FFmpeg and LibVLC engines to ensure maximum compatibility for playing high quality MKV/HEVC/RTSP files.
