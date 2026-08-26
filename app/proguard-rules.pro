# R8 & ProGuard Optimization Rules for Media Player

# Code shrinker & optimization settings
-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.**
-dontwarn org.bouncycastle.**
-dontwarn okio.**

# Keep Data Models and Entities
-keep class com.example.data.model.** { *; }
-keep class com.example.data.local.** { *; }
-keep class com.example.data.repository.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# AndroidX Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3 & ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.exoplayer.rtsp.** { *; }
-keep class androidx.media3.exoplayer.hls.** { *; }
-dontwarn androidx.media3.**

# Coil & Coil Video
-keep class coil.** { *; }
-keepclassmembers class coil.** { *; }
-dontwarn coil.**

# Retrofit, OkHttp, Moshi
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class com.squareup.moshi.** { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}

# Apache Commons Net (FTP)
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**

# JCIFS / SMB
-keep class jcifs.** { *; }
-dontwarn jcifs.**

# Keep native methods if any
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Compose Lambdas & ViewModels
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
