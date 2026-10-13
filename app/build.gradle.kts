import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// ── Release signing & identity pin (Phase 5) ─────────────────────────────────
// Everything can come from gradle.properties / ~/.gradle/gradle.properties or environment variables.
// New names are MYRA_*; the older KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD env names still work (CI).
fun cfg(name: String): String? = providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull

fun buildConfigString(value: String): String =
  "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val releaseStorePath = cfg("MYRA_RELEASE_STORE_FILE") ?: cfg("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
val releaseStorePassword = cfg("MYRA_RELEASE_STORE_PASSWORD") ?: cfg("STORE_PASSWORD")
val releaseKeyAlias = cfg("MYRA_RELEASE_KEY_ALIAS") ?: "upload"
val releaseKeyPassword = cfg("MYRA_RELEASE_KEY_PASSWORD") ?: cfg("KEY_PASSWORD")
val releaseSigningReady = !releaseStorePassword.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank() && file(releaseStorePath).exists()
// SHA-256 of the release certificate (colons optional, several separated by commas). Print it with scripts/print_cert_sha256.sh
val releaseCertSha256 = (cfg("MYRA_RELEASE_CERT_SHA256") ?: "").replace(":", "").uppercase()
val developerName = cfg("MYRA_DEVELOPER_NAME")?.ifBlank { null } ?: "Unconfigured"
val copyrightOwner = cfg("MYRA_COPYRIGHT_OWNER")?.ifBlank { null } ?: "Unconfigured"
// true = a release build WITHOUT a pinned certificate refuses to run (fail closed). Default false so your own builds keep working.
val requireReleasePin = cfg("MYRA_REQUIRE_PIN").equals("true", ignoreCase = true)
// true = turn on R8 shrinking for release (see proguard-rules.pro). Off by default because it cannot be tested without a device.
val minifyRelease = cfg("MYRA_MINIFY").equals("true", ignoreCase = true)

android {
  namespace = "com.soltini.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.soltini.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "PINNED_RELEASE_CERT_SHA256", buildConfigString(releaseCertSha256))
    buildConfigField("boolean", "REQUIRE_RELEASE_PIN", requireReleasePin.toString())
    buildConfigField("String", "DEVELOPER_NAME", buildConfigString(developerName))
    buildConfigField("String", "COPYRIGHT_OWNER", buildConfigString(copyrightOwner))
  }

  signingConfigs {
    create("release") {
      if (releaseSigningReady) {
        storeFile = file(releaseStorePath)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = minifyRelease
      isShrinkResources = minifyRelease
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (releaseSigningReady) {
        signingConfig = signingConfigs.getByName("release")
      } else {
        logger.warn("WARNING: release signing is not configured (set MYRA_RELEASE_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD). assembleRelease will produce an UNSIGNED apk.")
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
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
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.documentfile)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.paho.mqtt)
  // implementation(libs.coil.compose)
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
  implementation(libs.play.services.location)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.retrofit)
  implementation(libs.onnxruntime.android)
  // Offline wake-word listening ("Myra") — Vosk + JNA
  implementation("net.java.dev.jna:jna:5.13.0@aar")
  implementation("com.alphacephei:vosk-android:0.3.75@aar")
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
