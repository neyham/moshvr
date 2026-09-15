package dev.neyham.moshvr.data

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class ProfileFile(val profiles: List<HostProfile> = emptyList())

/** JSON-file backed store for host profiles. Secrets inside are Keystore-encrypted. */
class ProfileStore(context: Context) {
    private val file = File(context.filesDir, "profiles.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): List<HostProfile> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<ProfileFile>(file.readText()).profiles }
            .getOrDefault(emptyList())
    }

    fun save(profiles: List<HostProfile>) {
        file.writeText(json.encodeToString(ProfileFile(profiles)))
    }

    fun upsert(profile: HostProfile): List<HostProfile> {
        val updated = load().filter { it.id != profile.id } + profile
        save(updated)
        return updated
    }

    fun delete(id: String): List<HostProfile> {
        val updated = load().filter { it.id != id }
        save(updated)
        return updated
    }

    /**
     * Debug-only sideload helper. Release builds must not call this.
     * Returns false if the plaintext file could not be deleted after import.
     */
    fun importPlaintextIfPresent(): Boolean {
        val importFile = File(file.parentFile, "import-profiles.json")
        if (!importFile.exists()) return true
        val payload = runCatching {
            json.decodeFromString<ImportFile>(importFile.readText())
        }.getOrNull()
        if (payload != null) {
            payload.profiles.forEach { incoming ->
                upsert(
                    HostProfile(
                        id = incoming.id,
                        name = incoming.name,
                        host = incoming.host,
                        port = incoming.port,
                        username = incoming.username,
                        authMethod = incoming.authMethod,
                        encPassword = incoming.password?.takeIf { it.isNotEmpty() }?.let { CryptoStore.encrypt(it) },
                        encPrivateKey = incoming.privateKey?.takeIf { it.isNotEmpty() }?.let { CryptoStore.encrypt(it) },
                        encKeyPassphrase = incoming.keyPassphrase?.takeIf { it.isNotEmpty() }?.let { CryptoStore.encrypt(it) },
                        startupCommand = incoming.startupCommand,
                        useMosh = incoming.useMosh,
                    ),
                )
            }
        }
        return deleteImportFile(importFile)
    }

    companion object {
        fun deleteImportFile(importFile: File): Boolean {
            if (!importFile.exists()) return true
            if (importFile.delete()) return true
            runCatching { importFile.writeText("") }
            return importFile.delete() || !importFile.exists()
        }
    }
}

@Serializable
private data class ImportFile(val profiles: List<ImportProfile> = emptyList())

@Serializable
private data class ImportProfile(
    val id: String,
    val name: String = "",
    val host: String,
    val port: Int = 22,
    val username: String,
    val authMethod: AuthMethod = AuthMethod.PUBLIC_KEY,
    val password: String? = null,
    val privateKey: String? = null,
    val keyPassphrase: String? = null,
    val startupCommand: String? = null,
    val useMosh: Boolean = false,
)
