package dev.neyham.moshvr.session

import com.trilead.ssh2.Connection
import com.trilead.ssh2.InteractiveCallback
import dev.neyham.moshvr.data.AuthMethod
import dev.neyham.moshvr.data.CryptoStore
import dev.neyham.moshvr.data.HostProfile
import dev.neyham.moshvr.data.KnownHostsStore
import java.io.IOException

/** Shared SSH connect + authenticate flow (used by the SSH transport and the mosh bootstrap). */
object SshAuth {

    fun connectAndAuthenticate(
        profile: HostProfile,
        knownHosts: KnownHostsStore,
        ownerId: String,
    ): Connection {
        val conn = Connection(profile.host, profile.port)
        try {
            conn.connect(
                { hostname, port, serverHostKeyAlgorithm, serverHostKey ->
                    when (val result = knownHosts.verify(hostname, port, serverHostKeyAlgorithm, serverHostKey)) {
                        is KnownHostsStore.Result.FirstUse -> {
                            val accepted = HostKeyPrompt.await(
                                ownerId = ownerId,
                                host = hostname,
                                port = port,
                                algorithm = serverHostKeyAlgorithm,
                                fingerprint = result.fingerprint,
                                isChange = false,
                            )
                            if (!accepted) throw IOException("Host key rejected for $hostname:$port")
                            knownHosts.accept(hostname, port, serverHostKeyAlgorithm, serverHostKey)
                            true
                        }
                        is KnownHostsStore.Result.Match -> true
                        is KnownHostsStore.Result.Mismatch -> {
                            val accepted = HostKeyPrompt.await(
                                ownerId = ownerId,
                                host = hostname,
                                port = port,
                                algorithm = serverHostKeyAlgorithm,
                                fingerprint = result.actualFingerprint,
                                isChange = true,
                                expectedFingerprint = result.expectedFingerprint,
                            )
                            if (!accepted) {
                                throw HostKeyChangedException(
                                    host = hostname,
                                    port = port,
                                    algorithm = serverHostKeyAlgorithm,
                                    expectedFingerprint = result.expectedFingerprint,
                                    actualFingerprint = result.actualFingerprint,
                                )
                            }
                            knownHosts.accept(hostname, port, serverHostKeyAlgorithm, serverHostKey)
                            true
                        }
                    }
                },
                15000,
                15000,
            )
            authenticate(conn, profile)
            return conn
        } catch (failure: Exception) {
            // Connection is not returned to the caller when trust/authentication fails.
            runCatching { conn.close() }
            throw failure
        }
    }

    private fun authenticate(conn: Connection, profile: HostProfile) {
        val user = profile.username
        when (profile.authMethod) {
            AuthMethod.PUBLIC_KEY -> {
                val key = profile.encPrivateKey?.let { CryptoStore.decrypt(it) }
                    ?: throw IOException("No private key configured for ${profile.displayName}")
                val passphrase = profile.encKeyPassphrase?.let { CryptoStore.decrypt(it) }
                if (!conn.authenticateWithPublicKey(user, key.toCharArray(), passphrase)) {
                    throw IOException("Public key authentication failed")
                }
            }
            AuthMethod.PASSWORD -> {
                val password = profile.encPassword?.let { CryptoStore.decrypt(it) }
                    ?: throw IOException("No password configured for ${profile.displayName}")
                val ok = if (conn.isAuthMethodAvailable(user, "password")) {
                    conn.authenticateWithPassword(user, password)
                } else {
                    false
                }
                if (!ok) {
                    val kbOk = runCatching {
                        conn.authenticateWithKeyboardInteractive(
                            user,
                            InteractiveCallback { _, _, numPrompts, _, _ -> Array(numPrompts) { password } },
                        )
                    }.getOrDefault(false)
                    if (!kbOk) throw IOException("Password authentication failed")
                }
            }
        }
    }
}
