package dev.neyham.moshvr

import android.app.Application
import dev.neyham.moshvr.data.ProfileStore
import dev.neyham.moshvr.data.SettingsStore
import dev.neyham.moshvr.session.SessionManager

class MoshVrApp : Application() {

    lateinit var sessionManager: SessionManager
        private set

    lateinit var profileStore: ProfileStore
        private set

    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        profileStore = ProfileStore(this)
        settingsStore = SettingsStore(this)
        if (BuildConfig.DEBUG) {
            profileStore.importPlaintextIfPresent()
        }
    }
}
