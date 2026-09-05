import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.mediaplayer.vkwqz"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters.add("arm64-v8a")
    }
  }

  signingConfigs {
    create("release") {
      val envKeystorePath = System.getenv("KEYSTORE_PATH")
      val envKeystore = envKeystorePath?.let { file(it) }
      val customKeystore = file("${rootDir}/keystore/release-key.jks")
      val legacyKeystore = file("${rootDir}/release-key.jks")
      val debugKeystore = file("${rootDir}/debug.keystore")

      val targetStore = when {
        envKeystore != null && envKeystore.exists() -> envKeystore
        customKeystore.exists() -> customKeystore
        legacyKeystore.exists() -> legacyKeystore
        debugKeystore.exists() -> debugKeystore
        else -> {
          // If running on clean CI without pre-existing keystore, create one to ensure build succeeds
          debugKeystore.parentFile?.mkdirs()
          try {
            ProcessBuilder(
              "keytool", "-genkeypair",
              "-alias", "androiddebugkey",
              "-keypass", "android",
              "-keystore", debugKeystore.absolutePath,
              "-storepass", "android",
              "-dname", "CN=Android Debug,O=Android,C=US",
              "-keyalg", "RSA",
              "-keysize", "2048",
              "-validity", "10000"
            ).redirectErrorStream(true).start().waitFor()
          } catch (_: Throwable) {}
          debugKeystore
        }
      }

      storeFile = targetStore
      if (targetStore.name.contains("release")) {
        storePassword = System.getenv("STORE_PASSWORD") ?: "android"
        keyAlias = System.getenv("KEY_ALIAS") ?: "mediaplayer"
        keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
      } else {
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      val envKeystorePath = System.getenv("KEYSTORE_PATH")
      val envKeystore = envKeystorePath?.let { file(it) }
      val customKeystore = file("${rootDir}/keystore/release-key.jks")
      val debugKeystore = file("${rootDir}/debug.keystore")

      val targetStore = when {
        envKeystore != null && envKeystore.exists() -> envKeystore
        customKeystore.exists() -> customKeystore
        debugKeystore.exists() -> debugKeystore
        else -> debugKeystore
      }

      storeFile = targetStore
      if (targetStore.name.contains("release")) {
        storePassword = System.getenv("STORE_PASSWORD") ?: "android"
        keyAlias = System.getenv("KEY_ALIAS") ?: "mediaplayer"
        keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
      } else {
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.coil.video)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  implementation(libs.media3.session)
  implementation(libs.media3.datasource)
  implementation(libs.media3.datasource.okhttp)
  implementation(libs.media3.extractor)
  implementation(libs.media3.exoplayer.rtsp)
  implementation(libs.media3.exoplayer.hls)
  implementation(libs.commons.net)
  implementation(libs.jcifs)
  implementation(libs.nextlib.media3ext)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
