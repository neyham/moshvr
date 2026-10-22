# Make yourself at home

## Connect

Choose **New host**, give the connection a name, and enter the hostname, SSH
port and username. Configure password or key authentication. Verify the
host-key fingerprint on first connection before accepting it. A changed key
needs investigation rather than automatic trust.

Use mosh when you want recovery from a changing network. It first connects
over SSH to start `mosh-server`, then uses UDP. Plain SSH needs only an SSH
server. Neither mode creates a server for you.

A useful startup command is `tmux new -As main`: a subsequent connection can
reattach to the same remote workspace even if the local app process stopped.

## Controls

### Touch controllers (VR mode)

| Right controller | Action |
| :--- | :--- |
| Trigger | Click |
| Grip, hold | Talk; release to finish |
| A | Enter (sends the dictated text if there is any) |
| B | Backspace; while talking, cancel |
| Stick ← / → | Previous / next herdr tab (`Ctrl+B p` / `Ctrl+B n`) |
| Stick ↑ / ↓ | Previous / next herdr workspace (`Ctrl+B ↑` / `Ctrl+B ↓`) |
| Stick click | Ctrl+C — only with the stick centred, sent on release |

| Left controller | Action |
| :--- | :--- |
| Trigger | Click |
| X | Show / hide the keyboard |
| Y | Esc |
| Stick ↑ / ↓ | Scroll the terminal; hold to speed up |
| Grip + stick | Arrow keys (hold to repeat) |
| Stick click | Tab |

Each right-stick push switches once, and only on the axis it settles on.
The left stick on its own never types a key, so scrolling never snaps you back
to the bottom. Tap **[ ? ]** in the top bar for this list inside the app.

### Everywhere

| Control | Action |
| :--- | :--- |
| IMMERSE / 2D | Switch between the spatial workspace and Home panel |
| [ RECENTER ] | Reset the immersive terminal's placement |
| [ MOVE ] | Then grip the wall to move it; tap again when done |
| A− / A+ | Adjust terminal text size |
| 🎤 MIC · ⌨ KEYB · ⏎ ENTER | Large keys at the right of the bottom bar |
| Extra-key row | Esc, `/`, Tab, Ctrl, Alt, herdr ◀TAB / TAB▶, PgUp / PgDn, ^C |
| 2D: controller stick | ← / → herdr tab, ↑ / ↓ herdr workspace |
| Immersive thumb swipe | Switch local session tabs |
| Immersive thumb tap | Toggle the digital-rain construct |

The herdr shortcuts send its default `Ctrl+B` prefix; outside herdr they reach
the shell as plain keystrokes (tmux uses `Ctrl+B p` / `n` for windows too).
They are separate from local moshVR session tabs. Mouse events depend on the
remote application enabling terminal mouse tracking.

When a connection drops (for example after the headset sleeps), the terminal
says so; press Enter to reconnect in the same tab.

## Voice

Without an API key, MIC uses the Quest's built-in **on-device** speech
recognizer. The audio stays on the headset and moshVR does not record or
upload it.

1. Hold the right grip (or tap MIC) and speak; release (or tap STOP) to finish.
2. The text appears in the composer. Edit it if needed.
3. Press A, or tap Send / Enter, to send it to your host.

Recognition follows the headset's system language. To dictate in a different
language, choose **中文** or **English** under *Headset speech language* in
Settings. If the headset has no on-device recognizer, MIC opens the keyboard.

### Optional cloud transcription

1. Add your speech API key in Settings and review the selected HTTPS endpoint.
2. Give the required endpoint consent and adult eligibility confirmation.
3. Tap MIC to record. Tap STOP to upload audio for transcription.
4. Review the text in the composer, then send it to your host.

The default endpoint is SiliconFlow. Settings also offer Qwen ASR, Volcengine
speech and custom OpenAI-compatible transcription endpoints. See
[speech provider setup](SPEECH-PROVIDERS.md). Your audio and API key go to that provider. Leaving
or cancelling before STOP discards the recording; cancelling an already-sent
request cannot guarantee the provider did not process it.

## Local data

Delete a host from the connection list. Clear a speech key or known host in
Settings. Android Clear storage or uninstall removes the app's local data.
The [privacy policy](../PRIVACY.md) describes storage, network use and limits.
