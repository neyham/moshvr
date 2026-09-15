# Architecture

moshVR is a native Android client with two presentations of a terminal
workspace: a Compose panel in Horizon Home and an immersive activity using
Meta Spatial SDK.

```text
Quest input → composer / terminal → session transport → your server
                                        ├─ SSH stream
                                        └─ local mosh process → UDP

Optional microphone → HTTPS speech provider → composer → explicit Send
```

## Repository map

| Path | Responsibility |
| :--- | :--- |
| `app/src/main/java/dev/neyham/moshvr/` | Activities and spatial workspace |
| `app/…/data/` | Host profiles, known hosts, settings and encrypted secrets |
| `app/…/session/` | SSH authentication, host-key approval, transport and lifecycle |
| `app/…/ui/` | Compose interface, input routing and terminal chrome |
| `app/…/voice/` | Recording, endpoint consent and transcription requests |
| `terminal-emulator/` | Vendored Termux terminal engine and PTY JNI bridge |
| `terminal-view/` | Vendored terminal rendering and selection |
| `third_party/mosh/` | Native build scripts and OpenSSL source pin |

`TransportTerminalSession` adapts SSH streams to the emulator.
`MoshTerminalSession` starts the packaged mosh executable through a local PTY.
Mosh's SSH bootstrap and interactive SSH share explicit host-key approval.

The composer holds draft text until Send. Optional speech feeds that same
composer. A transcription request rechecks its owning session and consent
before upload; it does not send recognized text directly to the SSH host.

Secrets are encrypted at rest using Android Keystore. This does not make
a configured server or transcription provider trustworthy. Host-key checks,
endpoint consent and redacted diagnostics remain separate responsibilities.

`AgentDetector` is an unwired experiment. It is not an advertised feature.
