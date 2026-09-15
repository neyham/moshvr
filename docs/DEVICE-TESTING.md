# Device connection probe

The separate `androidTest` APK runs the production SSH transport on the headset without launching an Activity. It checks pinned host identity, Android Keystore encryption/decryption of a temporary credential, public-key authentication, initial PTY size, UTF-8 output and PTY resizing. Optional mosh coverage uses `MoshTerminalSession`, the bundled native client and the terminal emulator.

This is an explicit integration probe, not a replacement for UI tests. It does not establish that RECENTER, immersive dialogs, rendering, hand tracking, microphone input or sleep/reconnection work. Instrumentation restarts the app process; run it with no active user sessions.

## Verified scope

On 2026-09-15, a Quest 3 development build passed this probe against a Linux server: pinned SSH authentication, bidirectional Chinese/emoji text, PTY resizing, and the bundled native mosh client's actual UDP session. The fixture and network access were temporary and removed afterward. This verifies the transport path on that device; immersive controls, arbitrary login shells, interruption recovery and other headset models remain separate validation work.

## Prepare a disposable endpoint

Use an endpoint you administer and a temporary test-only key. A forced command should invoke [device-probe-server.py](../tools/device-probe-server.py) under Python 3. The fixture accepts only `PING 你好 🥽`, `SIZE` and `QUIT`; it never executes submitted commands. Limit key lifetime, disable forwarding and allow PTY allocation. Prefer a dedicated unprivileged test account. Do not transfer a management private key to the headset.

Obtain SSH host public keys through an already trusted administration channel. Do not seed pins from unauthenticated `ssh-keyscan` alone.

For optional mosh testing, the forced-command wrapper must recognize the bootstrap request and launch `mosh-server new -c 256` with the fixed Python fixture as its child. It must not evaluate `SSH_ORIGINAL_COMMAND`. Use a bounded lifetime and explicitly controlled UDP reachability. This restricted fixture validates transport behavior, not arbitrary remote shell commands or the ordinary server PATH.

## Build and run

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Create a private local JSON file **outside the repository** with these fields:

| Field | Value |
| --- | --- |
| `host`, `port`, `username` | Disposable SSH endpoint |
| `privateKey` | Temporary test private key text |
| `hostKeys` | Object mapping SSH key algorithm to base64 public-key blob |
| `mosh` | Optional boolean, default `false` |
| `privateDiagnostics` | Optional boolean; writes a private failure transcript in app cache, default `false` |

Copy the JSON through stdin into the debug app's private storage; never put its contents in command-line arguments or shared headset storage:

```sh
adb shell run-as dev.neyham.moshvr mkdir -p files
adb shell "run-as dev.neyham.moshvr sh -c 'umask 077; cat > files/device-connection-probe.json'" < /path/outside/repository/probe.json
adb shell am instrument -w dev.neyham.moshvr.test/dev.neyham.moshvr.DeviceConnectionProbe
```

The probe deletes its input after reading it and uses an isolated temporary known-host store. It does not save a connection profile. It reports fixed stage names and exception types instead of arbitrary server output or secrets. Require `INSTRUMENTATION_RESULT: result=PASS` and `INSTRUMENTATION_CODE: -1`; a zero exit code from the `adb` command alone is not proof of success. Missing configuration fails explicitly.

## Cleanup and evidence

Revoke the temporary key server-side after every run, including failed runs. Remove endpoint fixtures and local credentials; remove any temporary network rules. Delete `files/device-connection-probe.json` through `run-as` if the process was interrupted. Remove `cache/device-probe-mosh-private.txt` through `run-as` if private diagnostics were enabled. Uninstall `dev.neyham.moshvr.test` when done. Preserve only sanitized result output and APK hashes in private review evidence. Do not commit endpoint details, screenshots with personal content, keys or test configuration.

Record SSH and mosh outcomes separately. Mosh needs an actual UDP response from the fixture; successful SSH bootstrap alone is insufficient. A forced-command fixture does not validate standard shell startup, login configuration, or interruption recovery.

Implementation references: [Android Instrumentation](https://developer.android.com/reference/android/app/Instrumentation), [OpenSSH authorized_keys restrictions](https://man.openbsd.org/sshd.8), [Mosh transport and server setup](https://mosh.org/). Accessed 2026-09-15.
