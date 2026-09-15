// Vendored from termux/termux-app (commit 3df69d1, 2026-07-15), GPLv3.
// Local modifications: TerminalSession opened up for subclassing so network-backed
// sessions (SSH) can reuse the emulator without a local PTY subprocess.
plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.termux.emulator"
  compileSdk = 34
  ndkVersion = "27.2.12479018"

  defaultConfig {
    minSdk = 29

    externalNativeBuild {
      ndkBuild {
        cFlags("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fstack-protector-strong", "-Wl,--gc-sections")
      }
    }

    ndk { abiFilters += listOf("arm64-v8a") }
  }

  externalNativeBuild {
    ndkBuild { path = file("src/main/jni/Android.mk") }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(libs.androidx.annotation)
}
