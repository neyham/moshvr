# Help with moshVR

Start with [installation](docs/BUILDING.md) or the [controls guide](docs/USAGE.md).
For a bug in the app, use this repository's **Issues → New issue → Bug report**.
Include your app version, headset model, Horizon OS version, connection type,
and the smallest sequence that reproduces the problem.

## Connection problems

| Symptom | Check first |
| :--- | :--- |
| SSH cannot connect | Confirm the address, port and username using another SSH client on the same network. |
| SSH works; mosh does not | Check that `mosh-server` is on the remote PATH and that the server's mosh UDP port range is reachable. |
| The host key changed | Verify the server's key through a trusted channel before accepting a replacement. |
| The shell disappeared after the app stopped | Use a remote tmux session and reconnect; local process restoration is not implemented. |
| tmux shortcuts do nothing | Left/right controller shortcuts use tmux's default `Ctrl+B` prefix. |

## Input and speech

| Symptom | Check first |
| :--- | :--- |
| In-app MIC will not record | Check microphone permission, configured speech API key, endpoint consent and eligibility confirmation. |
| Transcription fails | Verify that the selected HTTPS provider supports the configured model and OpenAI-compatible transcription endpoint. |
| Recognized text did not reach the server | It stays in the composer until you tap Send. |
| Terminal mouse input behaves differently between apps | The remote application must enable terminal mouse tracking. |

Avoid posting raw terminal recordings, SSH keys, passwords, API tokens, host
addresses or personal usernames. Reproduce with a disposable host where
possible. Follow [SECURITY.md](SECURITY.md) for vulnerabilities.

This is an independent, pre-release project; support is best effort. No
external chat, paid support or response-time guarantee is offered.
