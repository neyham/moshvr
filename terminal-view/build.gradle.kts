// Vendored from termux/termux-app (commit 3df69d1, 2026-07-15), GPLv3.
plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.termux.view"
  compileSdk = 34

  defaultConfig {
    minSdk = 29
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  api(project(":terminal-emulator"))
  implementation(libs.androidx.annotation)
}
