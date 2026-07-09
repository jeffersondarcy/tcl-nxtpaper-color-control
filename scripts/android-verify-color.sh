#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
. scripts/android-env.sh

PROFILE="${1:-red}"

adb get-state >/dev/null
adb shell am start \
  -n com.jeff.tclcolorcontrol/.ColorControlActivity \
  --es profile "$PROFILE" \
  --ez apply true >/dev/null

sleep 1
adb shell settings get secure tct_color_temperature_activated
adb shell dumpsys SurfaceFlinger | rg -A2 -B2 "colorTransformMatrix" || true
