#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
. scripts/android-env.sh

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.jeff.tclcolorcontrol android.permission.WRITE_SECURE_SETTINGS || true
