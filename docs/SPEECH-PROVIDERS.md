# Speech providers

In **Settings → Speech provider**, select a service, set its key and endpoint, then Save. Settings retain an encrypted key, endpoint and model/resource ID for each provider. Switching requires a fresh upload consent and eligibility confirmation; no automatic fallback sends audio to a different service. The original default remains SiliconFlow.

| Provider | Model / resource | API base URL |
| --- | --- | --- |
| SiliconFlow / OpenAI compatible | `FunAudioLLM/SenseVoiceSmall` by default | `https://api.siliconflow.cn/v1` by default |
| Qwen ASR | `qwen3-asr-flash` | Use the API Host and region from your Model Studio workspace, followed by `/compatible-mode/v1` |
| Volcengine speech | `volc.bigasr.auc_turbo` | `https://openspeech.bytedance.com/api/v3/auc/bigmodel` |

## Qwen

Use a Model Studio API key authorized for the ASR model. Copy your workspace's **API Host** from the console and set the base URL to `https://YOUR_API_HOST/compatible-mode/v1`. The preset is the legacy Beijing endpoint `https://dashscope.aliyuncs.com/compatible-mode/v1`; it is not necessarily the correct endpoint for every workspace or region. Do not mix regions. Qwen's ASR uses audio messages through `/chat/completions`, not `/audio/transcriptions`.

An HTTP 403 means the request was denied; confirm the workspace host, region, source-IP restrictions and model permissions before retrying. It does not by itself prove a revoked key or an application defect.

## Volcengine / Doubao speech

Enable the recording-file recognition **turbo** resource. For the new speech console, enter the speech **APP Key** and leave Legacy App ID empty. For the old speech console, enter **Access Token** in the key field and set **Legacy App ID**. A key from a different Volcengine product must not be assumed to grant speech access.

HTTP 401 indicates an authentication problem. Confirm the key's service and authentication mode; resource access also needs to be enabled. The app checks the provider's service-status header as well as HTTP status, so an HTTP 200 with an ASR error is not reported as a transcript.

## Try a recording

1. Review the selected endpoint and enable cloud speech consent.
2. Tap MIC and speak a short phrase (under 20 seconds).
3. Tap STOP to send the audio once to the selected service.
4. Review the transcript in the composer. Send it to the SSH host only when you choose.

Changing provider, endpoint, model, key, legacy app ID or consent during a recording prevents that take from uploading. Clearing a key affects the selected provider when you Save; other providers' saved keys remain available.

The app sends WAV directly (multipart for OpenAI-compatible services, base64 JSON for Qwen and Volcengine). It does not require a public audio URL or upload audio to a separate storage service. Provider retention terms still apply. See [privacy](../PRIVACY.md).

## Verified scope — 2026-09-15

| Provider | Integration | Live verification |
| --- | --- | --- |
| Qwen ASR | Implemented | Successful API request and Quest 3 production-client probe with a validated 7.13-second synthetic WAV; the device returned 32 transcript characters in 1.09 seconds. |
| SiliconFlow / OpenAI compatible | Implemented; original default | Existing configuration retained; not revalidated in this test session. |
| Volcengine / Doubao speech | Implemented | Service rejected the supplied credential; recognition service was not enabled. End-to-end recognition remains unverified. |

These are speech-to-text providers, not a general chat-model interface. The Qwen probe does not validate the headset microphone, wearing comfort, or the full recording UI. Each user supplies a valid credential and enables the relevant service. No account keys or workspace-specific hostnames are bundled.

## Validation

JVM HTTPS integration checks cover each request format, transcript parsing, error/redirect handling, cancellation and credential isolation. Passing them does not establish that a particular account has access. A device probe is also available through the separate test APK: `am instrument -w -e mode speech .../DeviceConnectionProbe`. It accepts `files/device-speech-probe.json` in private debug-app storage, with base64 `wav` and a `providers` array (`provider`, `key`, `model`, `baseUrl`, optional `appId`). `saveInactiveProfiles` defaults to false; when enabled it encrypts the supplied keys and preserves the active provider. The probe uploads the supplied fixture, never records the microphone, and deletes its input. Keep all fixtures/credentials outside Git and uninstall the test APK afterward.

Official references (checked 2026-09-15): [Qwen ASR](https://help.aliyun.com/zh/model-studio/qwen-asr-api-reference), [Model Studio regions and API hosts](https://help.aliyun.com/zh/model-studio/beijing-access-information), [Volcengine turbo ASR](https://www.volcengine.com/docs/6561/1631584?lang=zh).
