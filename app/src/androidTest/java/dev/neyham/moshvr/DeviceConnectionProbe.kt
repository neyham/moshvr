package dev.neyham.moshvr

import android.app.Activity
import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.Bundle
import com.trilead.ssh2.Connection
import dev.neyham.moshvr.data.AuthMethod
import dev.neyham.moshvr.data.CryptoStore
import dev.neyham.moshvr.data.HostProfile
import dev.neyham.moshvr.data.KnownHostsStore
import dev.neyham.moshvr.session.SshTransport
import dev.neyham.moshvr.session.MoshTerminalSession
import com.termux.terminal.TerminalSessionClient
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Explicit opt-in hardware probe. Only packaged in the separate androidTest APK. */
class DeviceConnectionProbe : Instrumentation() {
    private var speechMode = false

    override fun onCreate(arguments: Bundle?) {
        speechMode = arguments?.getString("mode") == "speech"
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        if (speechMode) { SpeechDeviceProbe.run(this); return }
        val result = Bundle()
        val configFile = File(targetContext.filesDir, "device-connection-probe.json")
        val isolatedDir = File(targetContext.cacheDir, "connection-probe-${System.nanoTime()}")
        var transport: SshTransport? = null
        var mosh: MoshTerminalSession? = null
        val executor = Executors.newSingleThreadExecutor()
        var stage = "configuration"
        try {
            check(BuildConfig.DEBUG) { "Debug build required" }
            val config = JSONObject(configFile.readText())
            check(configFile.delete()) { "Could not remove transient input" }
            check(isolatedDir.mkdirs())
            val isolatedContext = object : ContextWrapper(targetContext) {
                override fun getFilesDir(): File = isolatedDir
            }
            val knownHosts = KnownHostsStore(isolatedContext)
            val host = config.getString("host")
            val port = config.optInt("port", 22)
            val pins = config.getJSONObject("hostKeys")
            stage = "pinned_handshake"
            val preflight = Connection(host, port)
            try {
                preflight.connect({ hostname, serverPort, algorithm, key ->
                    val expected = pins.optString(algorithm)
                    val accepted = expected.isNotEmpty() &&
                        java.security.MessageDigest.isEqual(Base64.getDecoder().decode(expected), key)
                    if (accepted) knownHosts.accept(hostname, serverPort, algorithm, key)
                    accepted
                }, 15000, 15000)
            } finally {
                preflight.close()
            }
            result.putString("pinned_handshake", "PASS")
            stage = "keystore_and_authentication"
            val profile = HostProfile(
                id = "device-connection-probe",
                host = host,
                port = port,
                username = config.getString("username"),
                authMethod = AuthMethod.PUBLIC_KEY,
                encPrivateKey = CryptoStore.encrypt(config.getString("privateKey")),
            )
            transport = SshTransport(profile, knownHosts, "device-connection-probe")
            val streams = transport.connect(80, 24)
            result.putString("public_key_authentication", "PASS")
            stage = "pty_read"
            fun expect(token: String) {
                val future = executor.submit<Boolean> { readUntil(streams.input, token) }
                check(future.get(15, TimeUnit.SECONDS)) { "Expected response absent" }
            }
            expect("PROBE_READY")
            streams.output.write("SIZE\n".toByteArray())
            streams.output.flush()
            expect("PROBE_SIZE 24 80")
            result.putString("pty_initial_size", "PASS")
            stage = "utf8_roundtrip"
            streams.output.write("PING 你好 🥽\n".toByteArray())
            streams.output.flush()
            expect("PROBE_PONG 你好 🥽")
            result.putString("utf8_roundtrip", "PASS")
            stage = "pty_resize"
            transport.resize(100, 30)
            streams.output.write("SIZE\n".toByteArray())
            streams.output.flush()
            expect("PROBE_SIZE 30 100")
            result.putString("pty_resize", "PASS")
            streams.output.write("QUIT\n".toByteArray())
            streams.output.flush()
            expect("PROBE_BYE")
            transport.close()
            transport = null
            if (config.optBoolean("mosh", false)) {
                stage = "mosh_startup_and_udp"
                val screen = AtomicReference("")
                val client = Proxy.newProxyInstance(
                    TerminalSessionClient::class.java.classLoader,
                    arrayOf(TerminalSessionClient::class.java),
                ) { _, method, args ->
                    when (method.name) {
                        "onTextChanged" -> {
                            val session = args[0] as com.termux.terminal.TerminalSession
                            screen.set(session.emulator.screen.transcriptText)
                            null
                        }
                        "getTerminalCursorStyle" -> 0
                        else -> null
                    }
                } as TerminalSessionClient
                runOnMainSync {
                    mosh = MoshTerminalSession(targetContext, profile.copy(useMosh = true), knownHosts,
                        "device-connection-probe-mosh", client)
                    mosh!!.initializeEmulator(80, 24, 8, 16)
                }
                fun expectMosh(token: String) {
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35)
                    while (System.nanoTime() < deadline) {
                        if (screen.get().contains(token)) return
                        Thread.sleep(100)
                    }
                    if (config.optBoolean("privateDiagnostics", false)) {
                        File(targetContext.cacheDir, "device-probe-mosh-private.txt").writeText(screen.get())
                    }
                    // Fixed classifications only; never report endpoint/session keys or arbitrary output.
                    result.putString("mosh_ssh_bootstrap", if (screen.get().contains("[mosh] connected")) "PASS" else "UNCONFIRMED")
                    result.putString("mosh_udp_unreachable", (screen.get().contains("Nothing received") ||
                        screen.get().contains("did not make a successful connection")).toString())
                    error("Mosh response timeout")
                }
                expectMosh("PROBE_READY")
                result.putString("mosh_ssh_bootstrap", "PASS")
                result.putString("mosh_native_udp", "PASS")
                stage = "mosh_utf8_roundtrip"
                runOnMainSync { mosh!!.write("PING 你好 🥽\r") }
                expectMosh("PROBE_PONG 你好 🥽")
                result.putString("mosh_utf8_roundtrip", "PASS")
                stage = "mosh_resize"
                runOnMainSync {
                    mosh!!.updateSize(100, 30, 8, 16)
                    mosh!!.write("SIZE\r")
                }
                expectMosh("PROBE_SIZE 30 100")
                result.putString("mosh_resize", "PASS")
                runOnMainSync { mosh!!.write("QUIT\r") }
                expectMosh("PROBE_BYE")
            }
            result.putString("result", "PASS")
        } catch (error: Exception) {
            result.putString("result", "FAIL")
            result.putString("failed_stage", stage)
            // Never include arbitrary server output, credentials or addresses in reports.
            result.putString("exception_type", error.javaClass.simpleName)
        } finally {
            runOnMainSync { mosh?.finishIfRunning() }
            transport?.close()
            executor.shutdownNow()
            configFile.delete()
            isolatedDir.deleteRecursively()
        }
        finish(if (result.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }

    private fun readUntil(input: InputStream, expected: String): Boolean {
        val token = expected.toByteArray(Charsets.UTF_8)
        var matched = 0
        var count = 0
        while (count++ < 65536) {
            val value = input.read()
            if (value < 0) return false
            matched = if (value == (token[matched].toInt() and 255)) matched + 1
                else if (value == (token[0].toInt() and 255)) 1 else 0
            if (matched == token.size) return true
        }
        return false
    }
}
