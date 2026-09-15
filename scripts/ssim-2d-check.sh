#!/usr/bin/env bash
# Headless-ish 2D checks on Spatial Simulator. Does not wait for a Quest.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERIAL="${SSIM_SERIAL:-emulator-5554}"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

need() { command -v "$1" >/dev/null || { echo "missing $1"; exit 1; }; }
need adb
need npx

if [[ ! -f "$APK" ]]; then
  echo "APK missing; build first: ./gradlew :app:assembleDebug"
  exit 1
fi

status="$(npx --yes metavr ssim status --format plain 2>/dev/null || true)"
if [[ "$status" != *running* ]]; then
  echo "SpatialSim not running. Start with:"
  echo "  ANDROID_EMULATOR_ROOT=\$ANDROID_HOME/emulator npx metavr ssim start"
  exit 2
fi

npx --yes metavr -d "$SERIAL" app install "$APK"
npx --yes metavr -d "$SERIAL" app launch dev.neyham.moshvr
sleep 3
npx --yes metavr -d "$SERIAL" ui list --json > /tmp/moshvr-ssim-ui.json

python3 - <<'PY'
import json, sys
d = json.load(open("/tmp/moshvr-ssim-ui.json"))
texts = { (x.get("text") or "") for x in d }
need = {"[ IMMERSE ]", "A-", "A+", "New host"}
missing = sorted(t for t in need if t not in texts)
if missing:
    print("FAIL missing home chrome:", missing)
    sys.exit(1)
print("OK home chrome:", ", ".join(sorted(need)))
PY
echo "ssim-2d-check: home chrome present"
