package dev.neyham.moshvr.ui

object Legal {
    const val APP_NAME = "moshVR"
    const val LICENSE = "GPLv3"
    const val CONTACT_URL = "https://neyham.github.io/moshvr/"
    const val PRIVACY_URL = "https://neyham.github.io/moshvr/privacy/"
    const val SOURCE_STATUS =
        "Source for this build: github.com/neyham/moshvr (tag v" + dev.neyham.moshvr.BuildConfig.VERSION_NAME + ")."
    const val THIRD_PARTY =
        "Vendored Termux terminal-emulator/view (GPLv3), mosh (GPLv3), " +
            "ConnectBot sshlib (BSD 3-Clause; see bundled notices), Meta Spatial SDK (Meta SDK License; " +
            "native libs are packaged in this APK, not a device system library), " +
            "OkHttp / AndroidX / kotlinx (Apache 2.0)."
}
