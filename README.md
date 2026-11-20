<p align="center">
  <img src="docs/assets/hero.svg" alt="moshVR. A native mosh and SSH terminal for Meta Quest." width="100%" />
</p>

<p align="center">
  <strong>Your remote shell. A workspace you can step into.</strong>
</p>

<p align="center">
  <a href="https://www.meta.com/experiences/moshvr/1340208049179116/"><strong>Get it on the Meta Horizon Store ↗</strong></a>&nbsp;&nbsp;·&nbsp;&nbsp;
  <a href="https://sidequestvr.com/app/62512">SideQuest</a>&nbsp;&nbsp;·&nbsp;&nbsp;
  <a href="#install--run">Install &amp; run</a>&nbsp;&nbsp;·&nbsp;&nbsp;
  <a href="docs/USAGE.md">Controls</a>&nbsp;&nbsp;·&nbsp;&nbsp;
  <a href="SUPPORT.md">Get help</a>&nbsp;&nbsp;·&nbsp;&nbsp;
  <a href="docs/ROADMAP.md">Roadmap</a>
</p>

moshVR is a native **mosh and SSH client for Meta Quest**. Bring a shell,
a tmux workspace, or a terminal editor into VR. Work in a Home panel, or
move into a spacious immersive terminal with passthrough and digital rain.
The client runs on your headset; the shell runs on your server.

## Features

<table>
<tr>
<td width="50%" valign="top">
<h3>⌘ &nbsp; Familiar tools, more room</h3>
An xterm-256color terminal, multiple session tabs, and the shell tools you
already use. Put the terminal beside your apps or give it a wall of its own.
</td>
<td width="50%" valign="top">
<h3>↗ &nbsp; Take your connection with you</h3>
Mosh recovers from network drops while the app process stays alive.
Choose plain SSH when you only need a stream connection.
</td>
</tr>
<tr>
<td width="50%" valign="top">
<h3>⌁ &nbsp; Made for spatial input</h3>
Both Touch controllers are mapped in VR mode: hold the grip to talk, A for
Enter, stick to scroll or switch herdr tabs and workspaces. Plus the Quest
keyboard, terminal extra keys and hand microgestures.
</td>
<td width="50%" valign="top">
<h3>›_ &nbsp; Speak. Review. Send.</h3>
The Quest's own on-device recognizer turns speech into composer text, with
no account or API key. Optional cloud transcription (SiliconFlow /
OpenAI-compatible, Qwen ASR, Volcengine) is there if you prefer it. Read it,
edit it, and send it to your host when you are ready.
</td>
</tr>
</table>

## Install & run

### Meta Horizon Store (Recommended)

moshVR is free on the Meta Horizon Store. No Developer Mode needed:

👉 **[Get moshVR on the Meta Horizon Store](https://www.meta.com/experiences/moshvr/1340208049179116/)**

### SideQuest

The same signed build is on [SideQuest (App #62512)](https://sidequestvr.com/app/62512)
for sideloading. Connect your Quest in Developer Mode and install with the SideQuest
app or web installer. Release APKs and checksums are also attached to each
[GitHub release](https://github.com/neyham/moshvr/releases).

### Build from source

You need a Quest running **Android API 34+**, Developer Mode, and a reachable
SSH server. For mosh connections, install `mosh-server` on the server too.

After [setting up JDK 17, Android SDK and NDK](docs/BUILDING.md#toolchain):

```sh
./third_party/mosh/build-mosh-client.sh
./gradlew :app:assembleDebug
adb -s YOUR_HEADSET_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Open moshVR in **Unknown Sources**, choose **New host**, and verify your server's
host-key fingerprint on the first connection. Set `tmux new -As main` as the
startup command to reattach to a persistent remote workspace.

[Full build instructions →](docs/BUILDING.md) &nbsp; [Connection help →](SUPPORT.md)

## Good to know

<details>
<summary><strong>What survives a disconnect?</strong></summary>

Mosh can recover while its local process remains alive. If Android kills the
app, reconnect. Running tmux on the server keeps the remote shell available
to reattach; it does not preserve the local moshVR process.

</details>

<details>
<summary><strong>Where do my credentials and voice go?</strong></summary>

Saved passwords, keys and speech API keys are encrypted with Android Keystore.
SSH credentials authenticate to your host. By default MIC uses the headset's
on-device recognizer and moshVR never records or uploads the audio. Optional
cloud speech requires your API key and endpoint consent: then **MIC STOP
uploads audio to the speech provider**. Either way, Enter / **Send** sends the
resulting text to your SSH host.
The developer does not receive your terminal sessions or recordings. Meta's
SDK and Horizon OS have their own data practices. [Read the privacy policy](PRIVACY.md).

</details>

<details>
<summary><strong>Do I need cloud speech?</strong></summary>

No. Without an API key, MIC uses the Quest's on-device speech recognizer,
which follows the headset's system language (English on the Quest we tested;
it does not support Chinese). For Chinese, add a cloud speech key. You can
also just use the Quest keyboard and terminal controls.
See [controls and voice](docs/USAGE.md) and [provider setup and verification](docs/SPEECH-PROVIDERS.md).

</details>

## Built on good foundations

[Termux](https://github.com/termux/termux-app) for terminal emulation ·
[mosh](https://mosh.org) for roaming connections ·
[ConnectBot sshlib](https://github.com/connectbot/sshlib) for SSH ·
Meta Spatial SDK for the workspace.

Want to help? Start with [contributing](CONTRIBUTING.md), explore the
[architecture](docs/ARCHITECTURE.md), or report a reproducible issue through
[the support guide](SUPPORT.md).

---

Made by **[neyham](https://github.com/neyham)** · Independent software for Quest.

[GPLv3](LICENSE) for moshVR code; dependencies retain their own licenses as noted in [THIRD_PARTY.md](THIRD_PARTY.md).
Available on the [Meta Horizon Store](https://www.meta.com/experiences/moshvr/1340208049179116/) and [SideQuest](https://sidequestvr.com/app/62512).
