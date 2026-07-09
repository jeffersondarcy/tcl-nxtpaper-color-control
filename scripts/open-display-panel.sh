#!/usr/bin/env bash
set -euo pipefail

panel="${1:-nxtpaper}"

case "$panel" in
  nxtpaper)
    action="android.settings.action.NXTPAPER_MODE"
    ;;
  eye|eyeprotection)
    action="android.settings.action.EYEPROTECTION"
    ;;
  reading)
    action="android.settings.READINGMODE_SETTINGS"
    ;;
  night)
    action="android.settings.NIGHT_DISPLAY_SETTINGS"
    ;;
  reduce-bright-colors|rbc)
    action="android.settings.REDUCE_BRIGHT_COLORS_SETTINGS"
    ;;
  display)
    action="android.settings.DISPLAY_SETTINGS"
    ;;
  *)
    printf 'Unknown panel: %s\n' "$panel" >&2
    printf 'Use one of: nxtpaper, eye, reading, night, reduce-bright-colors, display\n' >&2
    exit 64
    ;;
esac

adb shell am start -a "$action"
