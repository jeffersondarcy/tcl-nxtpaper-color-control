#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
. scripts/android-env.sh

scripts/check-supported-device.sh
./gradlew :app:assembleDebug
if install_output="$(adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1)"; then
  printf '%s\n' "$install_output"
else
  install_status=$?
  printf '%s\n' "$install_output" >&2
  if grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' <<<"$install_output"; then
    echo "The installed APK uses a different signing key." >&2
    echo "Uninstalling com.jeff.tclcolorcontrol allows installation but clears app preferences." >&2
  fi
  exit "$install_status"
fi

package=com.jeff.tclcolorcontrol
permission=android.permission.WRITE_SECURE_SETTINGS
if adb shell pm grant "$package" "$permission"; then
  if adb shell dumpsys package "$package" | grep -q "${permission}: granted=true"; then
    echo "Granted $permission."
  else
    echo "Warning: the grant command returned successfully, but readback did not confirm $permission." >&2
  fi
else
  echo "Warning: unable to grant $permission." >&2
  echo "Secure color, inversion, accessibility text, and Extra Dim controls may fail." >&2
  echo "Retry with: adb shell pm grant $package $permission" >&2
fi

echo "Overlay and Modify system settings access are requested through Android settings on first launch."
