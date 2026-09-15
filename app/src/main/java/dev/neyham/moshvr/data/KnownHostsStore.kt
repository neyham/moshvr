package dev.neyham.moshvr.data

import android.content.Context
import java.io.File
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class KnownHostsFile(val entries: MutableMap<String, String> = mutableMapOf())

data class KnownHostEntry(
    val id: String,
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprint: String,
)

/** Host key store. First contact does **not** persist until the user accepts. */
class KnownHostsStore(context: Context) {
    private val file = File(context.filesDir, "known_hosts.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    sealed class Result {
        data class FirstUse(val fingerprint: String) : Result()
        object Match : Result()
        data class Mismatch(val expectedFingerprint: String, val actualFingerprint: String) : Result()
    }

    fun verify(host: String, port: Int, algorithm: String, key: ByteArray): Result {
        val encoded = HostKeyLogic.encode(algorithm, key)
        val existing = load().entries[HostKeyLogic.id(host, port)]
        return when (HostKeyLogic.classify(existing, encoded)) {
            HostKeyLogic.Kind.FirstUse -> Result.FirstUse(HostKeyLogic.fingerprint(key))
            HostKeyLogic.Kind.Match -> Result.Match
            HostKeyLogic.Kind.Mismatch -> {
                val expectedKey = runCatching {
                    Base64.getDecoder().decode(existing!!.substringAfter(':'))
                }.getOrNull()
                Result.Mismatch(
                    expectedFingerprint = expectedKey?.let { HostKeyLogic.fingerprint(it) } ?: "unknown",
                    actualFingerprint = HostKeyLogic.fingerprint(key),
                )
            }
        }
    }

    fun accept(host: String, port: Int, algorithm: String, key: ByteArray) {
        val data = load()
        data.entries[HostKeyLogic.id(host, port)] = HostKeyLogic.encode(algorithm, key)
        save(data)
    }

    fun forget(host: String, port: Int) {
        val data = load()
        data.entries.remove(HostKeyLogic.id(host, port))
        save(data)
    }

    fun clearAll() {
        save(KnownHostsFile())
    }

    fun list(): List<KnownHostEntry> = load().entries.map { (id, encoded) ->
        val host = id.substringBeforeLast(':')
        val port = id.substringAfterLast(':').toIntOrNull() ?: 22
        val algorithm = encoded.substringBefore(':')
        val key = runCatching {
            Base64.getDecoder().decode(encoded.substringAfter(':'))
        }.getOrNull()
        KnownHostEntry(
            id = id,
            host = host,
            port = port,
            algorithm = algorithm,
            fingerprint = key?.let { HostKeyLogic.fingerprint(it) } ?: "unknown",
        )
    }.sortedBy { it.id }

    private fun load(): KnownHostsFile {
        if (!file.exists()) return KnownHostsFile()
        return runCatching { json.decodeFromString<KnownHostsFile>(file.readText()) }
            .getOrDefault(KnownHostsFile())
    }

    private fun save(data: KnownHostsFile) = file.writeText(json.encodeToString(data))
}
