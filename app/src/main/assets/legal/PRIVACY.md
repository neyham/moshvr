# moshVR Privacy Policy

*Last updated: 2026-09-07*

Public URL: https://neyham.github.io/moshvr/privacy/

moshVR is a terminal you run on your own Meta Quest headset. The developer of
moshVR does not operate accounts, analytics, or a backend that receives your
SSH sessions, passwords, keys, or recordings.

This is not a claim that **no** software on the headset processes data. Meta
Spatial SDK and Horizon OS may collect or receive technical information under
Meta’s terms (including Meta Platform Technologies). The developer does not
control that processing.

## How to delete data

You do not need to contact the developer to delete local data:

- **Host profiles** — delete a host from the connections list.
- **Speech API key** — Settings → Clear API key. Saving an empty key field
  does **not** clear an existing key; use the Clear action. After Clear, the
  previous ciphertext is not kept.
- **Known hosts** — Settings → Forget a host, or Clear all known hosts.
- **Uninstall or Android “Clear storage / Clear data”** — removes app-private
  files (profiles, settings, known hosts, encrypted blobs). The Android
  Keystore key for this app is removed with the app.

The developer never receives a copy of this data, so there is nothing for the
developer to delete on request. Product page: https://neyham.github.io/moshvr/

## Data stored on your device

- **Host profiles** (hostname, port, username, optional startup command) in
  app-private storage.
- **Secrets** (passwords, SSH private keys, optional speech API keys) are
  encrypted with an **Android Keystore-backed** AES-256-GCM key before they
  are written to disk. That is not a guarantee of StrongBox or TEE hardware
  on every device. Encryption at rest is not the same as “never leaves the
  device”: an SSH password is sent to the host you configured; a speech API
  key is sent to the speech endpoint you configured.
- **Known host keys** are stored only after you explicitly accept the host,
  algorithm, and fingerprint. You can forget or clear them in Settings.
- **Clipboard** text is copied or pasted only when you use terminal copy/paste.
  It is not stored by moshVR or uploaded to the developer.
- **Hands, head, and controllers** are used for pointing, grabbing the panel,
  scrolling, and optional microgestures. Those signals are not recorded or
  uploaded by moshVR.
- **Passthrough** is the system compositor. moshVR does not request camera
  permission and does not access or save raw camera or passthrough images.

## Network connections the app makes

- **SSH / mosh** go only to servers **you** configure. Terminal text is sent
  to that host when you type, use extra keys, or tap Send on the composer.
  The host operator may log connections under their own policy and
  jurisdiction.

- **Optional cloud speech-to-text** is off until you add an API key **and**
  consent for the current endpoint (first recording prompt or Settings).
  Changing the provider or API base URL clears consent until you opt in again. You must
  separately confirm you are 18 or older and eligible to use your provider.
  The app stores the current consent’s endpoint, disclosure version, device-clock
  timestamp and adult confirmation locally. This is self-attestation, not verified
  age. Old consent without this record requires a new opt-in. Turning off cloud
  speech or clearing its API key removes this record. It is not sent to the developer.
  Before STOP uploads, the app checks consent again and discards the recording
  if the provider, endpoint, model, key, legacy app ID or consent changed during the take.

  Each transcription POST sends the in-memory WAV **audio**, the **model/resource
  ID**, and your **API credentials** to the selected HTTPS endpoint. SiliconFlow /
  OpenAI-compatible providers use multipart `/audio/transcriptions` and Bearer
  authentication. Qwen ASR uses base64 audio in `/chat/completions` with Bearer
  authentication. Volcengine speech uses base64 audio in `/recognize/flash`,
  speech-key headers (or legacy App ID and Access Token), a random request ID,
  and a fixed app label. The app does not write the clip to storage. Each STOP starts at most one send attempt. Automatic retries and
  redirects are disabled; a failed request requires a new user recording.

  Default provider: **SiliconFlow**, endpoint `https://api.siliconflow.cn/v1`,
  default model `FunAudioLLM/SenseVoiceSmall` (listed on SiliconFlow’s current
  STT API docs; not verified here with a live key). Purpose: turn speech into
  composer text. You still tap Send before that text goes to the SSH host.
  Tapping **MIC STOP** ends the recording and **does** start the upload.

  Processing for the default endpoint may occur in the People’s Republic of
  China. Retention and deletion at SiliconFlow follow
  [SiliconFlow’s privacy policy](https://docs.siliconflow.cn/en/legals/privacy-policy).
  Qwen and Volcengine are optional providers. Qwen's preset uses the Beijing
  endpoint; choose the endpoint matching your account region. Review the chosen
  provider's privacy and retention terms before enabling uploads. Keys are stored
  separately per provider using Android Keystore encryption. Switching providers
  keeps saved keys but requires a fresh upload consent. Clear each saved key in
  its provider settings, or clear app data to remove all local settings.
  A custom HTTPS endpoint is entirely your responsibility. Non-HTTPS speech
  URLs are rejected.

  **Discard vs upload:** If you leave (Activity pause/stop, focus loss, Cancel,
  or the 20-second cap) **before** tapping STOP, moshVR does not start the
  transcription POST. If you already tapped STOP, Cancel only stops the local
  OkHttp Call and coroutine. A request that already left the device may still
  be processed by the provider. Tapping STOP finishes the take and starts the
  upload if you have consented.

  With no key, in-app MIC does not record. Quest keyboard dictation, if you
  use it, is handled by Horizon OS under Meta’s terms.

## Permissions

- **INTERNET** — SSH/mosh and optional transcription HTTPS calls.
- **RECORD_AUDIO** — only while you are recording with the in-app MIC, after
  you grant it. The terminal works without a microphone.
- **com.oculus.permission.HAND_TRACKING** — optional hand tracking /
  microgestures in immersive mode.
- **com.oculus.permission.RENDER_MODEL** — optional controller render models.

The app does not use `ACCESS_NETWORK_STATE` or `MODIFY_AUDIO_SETTINGS`.

## Contact

https://neyham.github.io/moshvr/

There is no separate email or support form yet.
