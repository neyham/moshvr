package dev.neyham.moshvr

import dev.neyham.moshvr.data.AppSettings
import dev.neyham.moshvr.data.HostKeyLogic
import dev.neyham.moshvr.data.ProfileStore
import dev.neyham.moshvr.ui.Legal
import dev.neyham.moshvr.ui.ListingCopy
import dev.neyham.moshvr.ui.PanelLayout
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorePrepRegressionTest {

    @Test
    fun subtitleFitsHorizonLimit() {
        assertTrue(ListingCopy.SUBTITLE.length <= 80)
        assertTrue(ListingCopy.SUBTITLE.isNotBlank())
    }

    @Test
    fun legalDoesNotCiteMissingGithubRepo() {
        assertFalse(Legal.CONTACT_URL.contains("github.com/neyham/moshvr"))
        assertFalse(Legal.PRIVACY_URL.contains("github.com/neyham/moshvr"))
        assertTrue(Legal.CONTACT_URL.startsWith("https://neyham.github.io/moshvr"))
        assertTrue(Legal.SOURCE_STATUS.contains("not yet published"))
        assertTrue(Legal.THIRD_PARTY.contains("packaged in this APK"))
    }

    @Test
    fun firstUseDoesNotLookLikeMatch() {
        val key = ByteArray(32) { 7 }
        val encoded = HostKeyLogic.encode("ssh-ed25519", key)
        assertEquals(HostKeyLogic.Kind.FirstUse, HostKeyLogic.classify(null, encoded))
        assertEquals(HostKeyLogic.Kind.Match, HostKeyLogic.classify(encoded, encoded))
        assertEquals(HostKeyLogic.Kind.Mismatch, HostKeyLogic.classify("ssh-ed25519:other", encoded))
        assertTrue(HostKeyLogic.fingerprint(key).startsWith("SHA256:"))
        assertEquals("example.com:22", HostKeyLogic.id("example.com", 22))
    }

    @Test
    fun blankApiKeyIsNotAStoredKey() {
        assertFalse(AppSettings(encApiKey = null).hasApiKey)
        assertFalse(AppSettings(encApiKey = "").hasApiKey)
        assertFalse(AppSettings(encApiKey = "   ").hasApiKey)
        assertTrue(AppSettings(encApiKey = "enc").hasApiKey)
    }

    @Test
    fun importFileDeleteIsChecked() {
        val file = File.createTempFile("import-profiles", ".json")
        file.writeText("{")
        assertTrue(file.exists())
        assertTrue(ProfileStore.deleteImportFile(file))
        assertFalse(file.exists())
        assertTrue(ProfileStore.deleteImportFile(file))
    }

    @Test
    fun manifestMeetsStoreStaticChecks() {
        val manifest = readNearby("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:version=\"1\""))
        assertTrue(manifest.contains("android.hardware.vr.headtracking"))
        assertEquals(2, Regex("""android:excludeFromRecents="true"""").findAll(manifest).count())
        assertFalse(manifest.contains("MODIFY_AUDIO_SETTINGS"))
        assertFalse(manifest.contains("ACCESS_NETWORK_STATE"))
        assertTrue(manifest.contains("android:minWidth=\"${PanelLayout.MIN_WIDTH_DP}dp\""))
        assertTrue(manifest.contains("android:minHeight=\"${PanelLayout.MIN_HEIGHT_DP}dp\""))
    }

    @Test
    fun listingOmitsMissingSourceRepo() {
        val listing = readNearby("docs/store-listing.md", "../docs/store-listing.md")
        assertTrue(listing.contains(ListingCopy.SUBTITLE))
        assertTrue(listing.contains("not a live repo") || listing.contains("not yet"))
        assertFalse(listing.contains("https://github.com/neyham/moshvr"))
        assertFalse(listing.contains("credentials stay on-device"))
        assertFalse(listing.contains("before anything is sent"))
    }

    @Test
    fun activitiesDoNotCancelAllHostKeysOnDestroy() {
        val panel = readNearby(
            "app/src/main/java/dev/neyham/moshvr/PanelActivity.kt",
            "src/main/java/dev/neyham/moshvr/PanelActivity.kt",
        )
        val immersive = readNearby(
            "app/src/main/java/dev/neyham/moshvr/ImmersiveActivity.kt",
            "src/main/java/dev/neyham/moshvr/ImmersiveActivity.kt",
        )
        val prompt = readNearby(
            "app/src/main/java/dev/neyham/moshvr/session/HostKeyPrompt.kt",
            "src/main/java/dev/neyham/moshvr/session/HostKeyPrompt.kt",
        )
        assertFalse(panel.contains("cancelPending") || panel.contains("cancelAll"))
        assertFalse(immersive.contains("cancelPending") || immersive.contains("cancelAll"))
        assertFalse(prompt.contains("fun cancelPending") || prompt.contains("cancelAll"))
        assertTrue(prompt.contains("fun cancelOwner"))
        assertFalse(prompt.contains("postCurrent(gate.current)"))
        assertFalse(prompt.contains("postCurrent("))
        assertTrue(prompt.contains("publisher.onDisplayed"))
        val publisher = readNearby(
            "app/src/main/java/dev/neyham/moshvr/session/HostKeyPublisher.kt",
            "src/main/java/dev/neyham/moshvr/session/HostKeyPublisher.kt",
        )
        assertTrue(publisher.contains("applyIfCurrent"))
        val terminal = readNearby(
            "app/src/main/java/dev/neyham/moshvr/ui/TerminalScreen.kt",
            "src/main/java/dev/neyham/moshvr/ui/TerminalScreen.kt",
        )
        assertTrue(terminal.contains("open.composerDraft"))
        assertFalse(terminal.contains("var composerText by remember"))
        val settings = readNearby(
            "app/src/main/java/dev/neyham/moshvr/ui/SettingsDialog.kt",
            "src/main/java/dev/neyham/moshvr/ui/SettingsDialog.kt",
        )
        assertTrue(settings.contains("edited.withoutSpeechConsent"))
        assertTrue(settings.contains("current.hasSpeechConsent"))
    }

    @Test
    fun gitignoreCoversImportProfiles() {
        val ignore = readNearby(".gitignore", "../.gitignore")
        assertTrue(ignore.contains("import-profiles.json"))
        assertTrue(ignore.contains("*.keystore"))
    }

    private fun readNearby(vararg candidates: String): String {
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("missing ${candidates.toList()}")
        return file.readText()
    }
}
