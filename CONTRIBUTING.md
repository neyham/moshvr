# Contributing

Thanks for taking an interest in moshVR. The most useful contributions make
real terminal work more comfortable and reliable inside Quest.

## Report a problem

Use the bug-report form with your headset model, Horizon OS version, build
version, connection type and steps to reproduce. Say what you expected and
what happened. A minimal reproduction with a disposable host is ideal.

Remove hostnames, IP addresses, usernames, terminal output containing private
data, passwords, keys and API tokens before attaching logs or screenshots.
For vulnerabilities, read [SECURITY.md](SECURITY.md) first.

## Change the code

1. Follow [the build guide](docs/BUILDING.md).
2. Keep changes focused and describe the observable behavior they improve.
3. Run `./gradlew :app:testDebugUnitTest :app:lintDebug`.
4. For spatial, input, audio or lifecycle changes, describe physical-device
   testing separately. Simulator/JVM results do not establish headset behavior.

Retain upstream notices and mark modifications to vendored Termux code.
Avoid unrelated formatting changes in vendored sources. Contributions to
moshVR code are under the project's GPLv3 license; third-party code keeps its
own terms. Do not include signing keys, personal profiles or generated builds.

If you want to change the product direction, open a feature request explaining
what you are trying to do in VR before starting a large implementation.
