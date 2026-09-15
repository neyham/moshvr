# Input and 2D validation

Use this checklist when testing a build. Record results against the exact
APK and runtime in your own development notes; these are test procedures,
not claims that all device checks have passed.

## Home panel

- Open the app and check that host creation, IMMERSE and text-size controls are visible.
- Connect to a disposable SSH host and verify host-key approval behavior.
- Open the Quest keyboard; confirm the composer remains usable.
- Check Enter, Escape, Tab, Control combinations and paste behavior.
- Confirm controller stick scrolling does not type characters into the PTY.
- Check local tabs separately from remote tmux window shortcuts.
- Test terminal selection and mouse tracking in a remote application that enables it.

## Voice and lifecycle

- Verify the terminal works without microphone permission or a speech key.
- Confirm MIC requires endpoint consent and STOP initiates transcription.
- Confirm recognized text waits in the composer until Send.
- Cancel before STOP, change sessions and pause the activity during recording.
- Test doffing, sleep and network interruption on the actual headset.
- Distinguish a surviving mosh process from a process killed by Android.

## Local simulator helper

`scripts/ssim-2d-check.sh` operates a running Meta Spatial Simulator through
`metavr`. It installs a debug build and checks basic panel controls. Read the
script before running it; it is separate from the JVM suite. A simulator
without an OpenXR runtime cannot validate immersive rendering.
