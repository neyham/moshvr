plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

fun storeSigningReady(): Boolean {
  val storeFilePath = (findProperty("MOSHVR_STORE_FILE") as String?)?.trim().orEmpty()
  val storePassword = (findProperty("MOSHVR_STORE_PASSWORD") as String?)?.trim().orEmpty()
  val keyAlias = (findProperty("MOSHVR_KEY_ALIAS") as String?)?.trim().orEmpty()
  val keyPassword = (findProperty("MOSHVR_KEY_PASSWORD") as String?)?.trim().orEmpty()
  if (storeFilePath.isEmpty() || storePassword.isEmpty() || keyAlias.isEmpty() || keyPassword.isEmpty()) {
    return false
  }
  return file(storeFilePath).isFile
}

android {
  namespace = "dev.neyham.moshvr"
  compileSdk = 34

  defaultConfig {
    applicationId = "dev.neyham.moshvr"
    minSdk = 34 // Horizon OS is Android 14 (API 34)
    targetSdk = 34
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "dev.neyham.moshvr.DeviceConnectionProbe"
    // Quest runtime and both app-owned native components are arm64 only.
    ndk { abiFilters += "arm64-v8a" }
  }

  packaging {
    // Keep one copy of each notice instead of stripping all META-INF licenses.
    resources.pickFirsts.add("META-INF/LICENSE")
    resources.pickFirsts.add("META-INF/LICENSE.txt")
    resources.pickFirsts.add("META-INF/NOTICE")
    resources.pickFirsts.add("META-INF/NOTICE.txt")
    // Extract native libs to disk so mosh-client (libmosh_client.so) can be exec'd
    // from nativeLibraryDir (required on targetSdk >= 29).
    jniLibs.useLegacyPackaging = true
  }

  lint {
    abortOnError = true
    checkReleaseBuilds = true
  }

  signingConfigs {
    if (storeSigningReady()) {
      create("release") {
        storeFile = file(project.property("MOSHVR_STORE_FILE") as String)
        storePassword = project.property("MOSHVR_STORE_PASSWORD") as String
        keyAlias = project.property("MOSHVR_KEY_ALIAS") as String
        keyPassword = project.property("MOSHVR_KEY_PASSWORD") as String
        enableV2Signing = true
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  implementation(project(":terminal-emulator"))
  implementation(project(":terminal-view"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.material.icons.extended)
  debugImplementation(libs.androidx.ui.tooling)

  // Meta Spatial SDK
  implementation(libs.meta.spatial.sdk.base)
  implementation(libs.meta.spatial.sdk.vr)
  implementation(libs.meta.spatial.sdk.toolkit)
  implementation(libs.meta.spatial.sdk.compose)

  // Protocols
  implementation(libs.connectbot.sshlib)
  implementation(libs.okhttp)

  // Kotlinx
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)

  testImplementation("junit:junit:4.13.2")
  testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
  testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
}

gradle.taskGraph.whenReady {
  val storeTask = allTasks.any { task ->
    task.project == project && (
      task.name == "assembleRelease" ||
        task.name == "bundleRelease" ||
        task.name == "packageRelease"
      )
  }
  if (storeTask && !storeSigningReady()) {
    throw GradleException(
      "Release/store signing is fail-closed. Set MOSHVR_STORE_FILE, MOSHVR_STORE_PASSWORD, MOSHVR_KEY_ALIAS, and MOSHVR_KEY_PASSWORD in ~/.gradle/gradle.properties or a CI secret store (ORG_GRADLE_PROJECT_*). Do not pass store passwords on the Gradle command line. MOSHVR_STORE_FILE must exist. Debug builds are unaffected. Other modules are unaffected.",
    )
  }
}
