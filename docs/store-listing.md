# Listing copy

## moshVR

**Native mosh + SSH terminal for Quest. Confirm text to the host.**

Bring your shell into your headset. moshVR connects to your own server with
mosh or SSH and displays a native terminal in a Home panel or a movable
immersive workspace.

Switch between multiple sessions, scroll with your controller, and use
terminal extra keys alongside the Quest keyboard. Keep your room in view
with passthrough or settle into the digital-rain environment.

Optional cloud speech puts a transcription in the composer. MIC STOP uploads
audio to your chosen speech endpoint; review the resulting text and tap Send
to send it to your server. Your own API key and endpoint consent are required.

mosh can recover from network drops while the app process remains alive.
If Android kills the process, reconnect to the remote workspace; use tmux
on the server to retain the shell across connections.

## Requirements and availability

A reachable SSH host is required. mosh also requires `mosh-server` and UDP
connectivity. The app targets arm64 Quest devices running Android API 34+;
physical-device validation is ongoing.

Category: Productivity / Utilities. Public distribution is not yet available.
Add the final source and download links only after they are live.

Privacy policy: https://neyham.github.io/moshvr/privacy/

The developer does not receive terminal sessions, credentials or recordings.
SSH authentication goes to the configured host. Optional speech sends audio,
model and API key to the configured provider. Meta SDK/Horizon OS have their
own data practices. The full policy must match the released build.

## Capture brief

Use real captures of host setup, a disposable terminal session, keyboard and
extra-key input, voice review, and immersive placement. Redact server and
account details. Branding artwork is not a product screenshot. Do not imply
store approval, universal headset compatibility or a feature not wired into
the current interface.
