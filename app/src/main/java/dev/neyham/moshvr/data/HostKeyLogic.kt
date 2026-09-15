package dev.neyham.moshvr.data

import java.security.MessageDigest
import java.util.Base64

/** Pure host-key helpers so first-use / mismatch can be unit-tested without Android. */
object HostKeyLogic {
    fun id(host: String, port: Int): String = "$host:$port"

    fun encode(algorithm: String, key: ByteArray): String =
        "$algorithm:" + Base64.getEncoder().encodeToString(key)

    fun fingerprint(key: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    enum class Kind { FirstUse, Match, Mismatch }

    fun classify(existing: String?, encoded: String): Kind = when {
        existing == null -> Kind.FirstUse
        existing == encoded -> Kind.Match
        else -> Kind.Mismatch
    }
}
