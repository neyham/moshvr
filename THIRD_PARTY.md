# Third-party code and licenses

moshVR itself is GPLv3. It incorporates:

| Component | Origin | License | Notes |
|---|---|---|---|
| terminal-emulator, terminal-view | [termux/termux-app](https://github.com/termux/termux-app) @ `3df69d1` | GPLv3 | vendored; local modifications marked with `MODIFIED (moshVR)` comments |
| mosh (client frontend sources + core static libs) | [mobile-shell/mosh](https://github.com/mobile-shell/mosh) @ `9c4e59a` | GPLv3 | compiled to `libmosh_client.so` via `third_party/mosh/build-mosh-client.sh` |
| mosh Android prebuilt dependency libs | [rjyo/mosh-android](https://github.com/rjyo/mosh-android) v1.0.0 | GPLv3 (mosh) / respective upstream licenses | protobuf (BSD), abseil (Apache 2.0), ncurses (MIT-X11); original OpenSSL prebuilt is excluded |
| sshlib | [connectbot/sshlib](https://github.com/connectbot/sshlib) | BSD 3-Clause (resolved 2.2.23 POM declaration) | exact release notice reconciliation pending; current upstream main license is not evidence for this old artifact |
| OpenSSL libcrypto 3.5.8 | [OpenSSL release](https://github.com/openssl/openssl/releases/tag/openssl-3.5.8) | Apache 2.0 | checksum-pinned source built for arm64 API 34; replaces unsupported prebuilt 3.2.1 |
| Meta Spatial SDK | Maven Central `com.meta.spatial` | Meta Platform Technologies SDK License | compiled and packaged into the APK (`libMetaSpatialSDK.so` and related natives), not a Horizon OS system library |
| OkHttp, kotlinx.serialization, kotlinx.coroutines, AndroidX/Compose | Maven Central | Apache 2.0 | |
| xterm-256color terminfo entry | ncurses terminfo database | MIT-X11 | bundled in `app/src/main/assets/terminfo` |
| JetBrains Mono Nerd Font Mono | [ryanoasis/nerd-fonts](https://github.com/ryanoasis/nerd-fonts) (JetBrains Mono + Nerd patches) | OFL-1.1 | `app/src/main/assets/fonts/JetBrainsMonoNerdFontMono-Regular.ttf` |

## Bundled notice inventory (2026-09-07)

`assets/legal/DEPENDENCY-NOTICES.txt` retains all distinct LICENSE/NOTICE texts
found in the resolved release AAR/JAR files, attributed to their coordinates.
`JetBrainsMono-OFL.txt` and `JetBrainsMonoNerdFont-OFL.txt` include font notices.
About / Legal displays all bundled legal assets. A local artifact and POM inventory can be regenerated with the release
inventory scripts; it contains machine paths and is excluded from version control.

This collection does not certify completeness: exact native prebuilt dependency
source revisions, patched font glyph provenance and GPLv3/Meta SDK distribution
compatibility still require resolution before public distribution. The Meta SDK
is bundled proprietary software, not a GPL system-library exception established
by this project. No public corresponding-source release is claimed.
