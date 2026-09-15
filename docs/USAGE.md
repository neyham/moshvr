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

| Control | Action |
| :--- | :--- |
| IMMERSE / 2D | Switch between the spatial workspace and Home panel |
| RECENTER | Reset the immersive terminal's placement |
| A− / A+ | Adjust terminal text size |
| Controller stick up / down | Scroll the terminal |
| Controller stick left / right | Send tmux `Ctrl+B p` / `Ctrl+B n` to the remote host |
| Extra-key row | Enter terminal keys such as Escape, Tab and Control combinations |
| Immersive thumb swipe | Switch local session tabs |
| Immersive thumb tap | Toggle the digital-rain environment |

The tmux window shortcuts assume tmux's default `Ctrl+B` prefix. They are
separate from local moshVR session tabs. Mouse events depend on the remote
application enabling terminal mouse tracking.

## Voice

You can use Quest keyboard dictation under Horizon OS's own settings. The
in-app microphone is a separate, optional cloud transcription feature:

1. Add your speech API key in Settings and review the selected HTTPS endpoint.
2. Give the required endpoint consent and adult eligibility confirmation.
3. Tap MIC to record. Tap STOP to upload audio for transcription.
4. Review the text in the composer, then tap Send to send it to your host.

The default endpoint is SiliconFlow. Settings also offer Qwen ASR, Volcengine
speech and custom OpenAI-compatible transcription endpoints. See
[speech provider setup](SPEECH-PROVIDERS.md). Your audio and API key go to that provider. Leaving
or cancelling before STOP discards the recording; cancelling an already-sent
request cannot guarantee the provider did not process it.

## Local data

Delete a host from the connection list. Clear a speech key or known host in
Settings. Android Clear storage or uninstall removes the app's local data.
The [privacy policy](../PRIVACY.md) describes storage, network use and limits.
