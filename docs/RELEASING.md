# Release a build

A distributable build needs a durable signing identity, device validation,
accurate release notes and completed dependency/license review. A successful
Gradle task alone does not establish release readiness.

## Signing

Create and securely back up a release keystore outside this repository.
Choose its certificate identity deliberately: certificate information is
public in a signed APK. Do not put a personal address in it unnecessarily.
Keep its private key and passwords out of logs, source and shell arguments.

Configure these four Gradle properties in your private
`~/.gradle/gradle.properties`, or through a CI secret store:

```properties
MOSHVR_STORE_FILE=/absolute/path/to/release.keystore
MOSHVR_STORE_PASSWORD=REPLACE_IN_PRIVATE_CONFIG_ONLY
MOSHVR_KEY_ALIAS=moshvr
MOSHVR_KEY_PASSWORD=REPLACE_IN_PRIVATE_CONFIG_ONLY
```

CI may inject `ORG_GRADLE_PROJECT_` followed by each property name. Do not
commit these values to the project's `gradle.properties` or pass passwords
as Gradle `-P` command-line options. Release tasks fail closed without all
four properties and an existing keystore. Debug builds remain independent.

## Build and inspect

Follow [BUILDING.md](BUILDING.md) to build native components, then run:

```bash
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Read `app/build/outputs/apk/release/output-metadata.json` for the output name.
Verify the actual APK, its signing certificate and its hash:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
shasum -a 256 app/build/outputs/apk/release/app-release.apk
python3 scripts/verify-store-apk.py --help
```

Use the verifier's expected-certificate option with your public release
certificate fingerprint. It rejects debug artifacts and performs static
package/native-library checks. It cannot establish device behavior or
store approval. Do not upload an unsigned or debug APK as a stable release.

## Publication checklist

- Update versionCode/versionName in the Gradle configuration and manifest.
- Resolve the outstanding dependency/source issues in [THIRD_PARTY.md](../THIRD_PARTY.md).
- Record physical-device results and any known issues for this exact APK.
- Verify the public privacy/support links and source availability statements.
- Use screenshots and video captured from the real app with disposable host data.
- Publish release notes with installation steps, tested devices and APK checksum.
- Recheck the current distribution platform's requirements before submission.

There is no automatic signing, release upload or publishing workflow here.
Store metadata is drafted in [store-listing.md](store-listing.md).

## Refresh dependency notices

```bash
./gradlew -I scripts/release-inventory.init.gradle :app:writeReleaseInventory
python3 scripts/collect-release-notices.py
```

The generated inventory under `build/reports/release-inventory/` contains local dependency paths and
must remain private. The notice collector writes attributed license texts
into the app's legal assets. Neither operation resolves license compatibility
or certifies complete Corresponding Source.
