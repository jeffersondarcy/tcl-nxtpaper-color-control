#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
. scripts/android-env.sh

scripts/check-supported-device.sh
adb shell am start -n com.jeff.tclcolorcontrol/.ColorControlActivity >/dev/null

sleep 1
echo "TCL matrix activation:"
adb shell settings get secure tct_color_temperature_activated
echo "SurfaceFlinger color transform:"
adb shell dumpsys SurfaceFlinger | grep -A2 -B2 "colorTransformMatrix" || true
