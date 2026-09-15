package dev.neyham.moshvr.data

import kotlinx.serialization.Serializable

@Serializable
enum class AuthMethod { PASSWORD, PUBLIC_KEY }

@Serializable
data class HostProfile(
    val id: String,
    val name: String = "",
    val host: String,
    val port: Int = 22,
    val username: String,
    val authMethod: AuthMethod = AuthMethod.PASSWORD,
    /** Encrypted with [CryptoStore]. */
    val encPassword: String? = null,
    /** Encrypted PEM/OpenSSH private key text. */
    val encPrivateKey: String? = null,
    /** Encrypted private key passphrase. */
    val encKeyPassphrase: String? = null,
    /** Optional command to run right after connecting, e.g. "tmux new -As main". */
    val startupCommand: String? = null,
    /** Connect with mosh (bootstrap over SSH, then switch to UDP). */
    val useMosh: Boolean = false,
) {
    val displayName: String
        get() = name.ifBlank { "$username@$host" }
}
