# Build moshVR

A development build runs the terminal locally on Quest and connects to your
own SSH server. No developer credentials are bundled with the source.

## Toolchain

| Tool | Version |
| :--- | :--- |
| JDK | 17 |
| Android SDK | Platform 34; build tools 35.0.0 |
| Android NDK | 27.2.12479018 |
| Gradle | Use the included `gradlew` wrapper |
| Native build utilities | Git, curl, tar, make, Perl, shasum |

The native scripts target macOS and Linux. Windows users need a compatible
Linux build environment; there is no native Windows build script.

Set `JAVA_HOME` to your JDK 17 installation and `ANDROID_HOME` to your Android
SDK. Install the required packages with Android Studio's SDK Manager or:

```bash
sdkmanager "platform-tools" "platforms;android-34" "build-tools;35.0.0" "ndk;27.2.12479018"
```

Review and accept the SDK licenses yourself when prompted. Set the local SDK
path (this file is ignored by Git):

```bash
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
```

## Compile

From the repository root:

```bash
./third_party/mosh/build-mosh-client.sh
./gradlew :app:assembleDebug
```

The native script downloads pinned mosh frontend sources, inherited prebuilt
static dependencies and checksum-verified OpenSSL source. It builds OpenSSL
and the mosh frontend, then places `libmosh_client.so` in
`app/src/main/jniLibs/arm64-v8a/`. These generated files are ignored by Git.
See [THIRD_PARTY.md](../THIRD_PARTY.md) for the remaining provenance limits.

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Debug builds use Android's development signing identity.

## Install on your own headset

Enable Developer Mode and authorize USB debugging on the headset. With your
headset connected, select its serial explicitly:

```bash
adb devices
adb -s YOUR_HEADSET_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch moshVR from the headset's Unknown Sources application list. Developer
Mode and menu names may vary with Horizon OS. You need a reachable SSH host;
mosh additionally requires `mosh-server` and reachable UDP ports (commonly
60000–61000) on that host.

The manifest declares Quest 2, Quest Pro, Quest 3 and Quest 3S; the app requires
arm64 and Android API 34+. Quest 3 has passed the [SSH and mosh connection
probe](DEVICE-TESTING.md#verified-scope). Full interaction coverage and the other
headset models remain unverified.

## Checks

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

These checks exercise JVM logic and Android static analysis. They do not
require a headset or prove spatial rendering, controller behavior, audio
quality or session recovery on a device. The CI workflow runs these checks
without signing or uploading an APK. Its first GitHub run is still pending.

For an opt-in connection test on a USB-connected headset, see
[device testing](DEVICE-TESTING.md). It runs separately from the JVM checks
and requires a disposable SSH endpoint.

For a local simulator, see [2D checks](2d-checklist.md). For distributable
artifacts, follow [releasing](RELEASING.md).
