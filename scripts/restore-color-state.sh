#!/usr/bin/env bash
set -euo pipefail

snapshot="${1:-}"
if [[ -z "$snapshot" || ! -f "$snapshot" ]]; then
  echo "Usage: scripts/restore-color-state.sh diagnostics/color-state-TIMESTAMP.txt" >&2
  exit 2
fi

expected_restore_records=20
records_file="$(mktemp "${TMPDIR:-/tmp}/tcl-color-restore-records.XXXXXX")"
expected_keys_file="$(mktemp "${TMPDIR:-/tmp}/tcl-color-restore-keys.XXXXXX")"
actual_keys_file="$(mktemp "${TMPDIR:-/tmp}/tcl-color-restore-actual.XXXXXX")"
trap 'rm -f "$records_file" "$expected_keys_file" "$actual_keys_file"' EXIT

awk '
  /^## Experimental Restore Values$/ { in_restore = 1; next }
  /^## / && in_restore { exit }
  in_restore && /^(secure|system|global)\|/ { print }
' "$snapshot" > "$records_file"

printf '%s\n' \
  'secure|accessibility_display_inversion_enabled' \
  'secure|contrast_level' \
  'secure|font_weight_adjustment' \
  'secure|high_text_contrast_enabled' \
  'secure|nxtpaper_book_style_state' \
  'secure|nxtpaper_ink_style_state' \
  'secure|reduce_bright_colors_activated' \
  'secure|reduce_bright_colors_level' \
  'secure|tct_color_temperature_activated' \
  'secure|tct_color_temperature_matrix' \
  'system|adv_color_mode_value' \
  'system|color_mode_value' \
  'system|darker_display_mode_enable' \
  'system|multimedia_enhancement_enable' \
  'system|night_brightness_mode' \
  'system|nxt_gamma_setting' \
  'system|nxt_gamma_setting_reason' \
  'system|reading_mode_always_enable' \
  'system|sunshine_enable_setting' \
  'system|video_enhancement_enable' \
  | LC_ALL=C sort > "$expected_keys_file"

record_count="$(wc -l < "$records_file" | tr -d ' ')"
record_marker="$(awk -F= '/^# restore-record-count=/ { print $2 }' "$snapshot")"
invalid_record_count="$(awk -F'|' '
  NF != 4 || ($3 != "present" && $3 != "absent") || ($3 == "absent" && $4 != "") { count += 1 }
  END { print count + 0 }
' "$records_file")"
awk -F'|' '{ print $1 "|" $2 }' "$records_file" | LC_ALL=C sort > "$actual_keys_file"

if [[ "$record_count" -ne "$expected_restore_records" ||
      "$record_marker" != "$expected_restore_records" ||
      "$invalid_record_count" -ne 0 ]] ||
   ! cmp -s "$expected_keys_file" "$actual_keys_file"; then
  echo "Snapshot is incomplete or incompatible; exact $expected_restore_records-key restore block required" >&2
  exit 3
fi

adb get-state >/dev/null

snapshot_serial="$(awk '/^Device serial: / { sub(/^Device serial: /, ""); print; exit }' "$snapshot")"
snapshot_model="$(awk '/^Device model: / { sub(/^Device model: /, ""); print; exit }' "$snapshot")"
snapshot_fingerprint="$(awk '/^Build fingerprint: / { sub(/^Build fingerprint: /, ""); print; exit }' "$snapshot")"
connected_serial="$(adb get-serialno | tr -d '\r')"
connected_model="$(adb shell getprop ro.product.model | tr -d '\r')"
connected_fingerprint="$(adb shell getprop ro.build.fingerprint | tr -d '\r')"

if [[ -z "$snapshot_serial" || -z "$snapshot_model" || -z "$snapshot_fingerprint" ||
      "$snapshot_serial" != "$connected_serial" ||
      "$snapshot_model" != "$connected_model" ||
      "$snapshot_fingerprint" != "$connected_fingerprint" ]]; then
  echo "Snapshot device/build does not match the connected tablet; refusing to restore" >&2
  exit 5
fi

restored=0
restore_record() {
  local namespace="$1"
  local key="$2"
  local presence="$3"
  local value="$4"
  case "$presence" in
    present)
      adb shell settings put "$namespace" "$key" "$value" </dev/null
      ;;
    absent)
      adb shell settings delete "$namespace" "$key" </dev/null >/dev/null
      ;;
    *)
      echo "Invalid restore record for $namespace:$key" >&2
      exit 4
      ;;
  esac
  restored=$((restored + 1))
}

# Fail safe if restoration is interrupted: disable active transforms first,
# restore payload values, then restore inversion and activation flags last.
adb shell settings put secure tct_color_temperature_activated 0 </dev/null
adb shell settings put secure accessibility_display_inversion_enabled 0 </dev/null
adb shell settings put secure reduce_bright_colors_activated 0 </dev/null

while IFS='|' read -r namespace key presence value; do
  if [[ "$key" != "accessibility_display_inversion_enabled" &&
        "$key" != "tct_color_temperature_activated" &&
        "$key" != "reduce_bright_colors_activated" ]]; then
    restore_record "$namespace" "$key" "$presence" "$value"
  fi
done < "$records_file"

while IFS='|' read -r namespace key presence value; do
  if [[ "$key" == "reduce_bright_colors_activated" ]]; then
    restore_record "$namespace" "$key" "$presence" "$value"
  fi
done < "$records_file"

while IFS='|' read -r namespace key presence value; do
  if [[ "$key" == "accessibility_display_inversion_enabled" ]]; then
    restore_record "$namespace" "$key" "$presence" "$value"
  fi
done < "$records_file"

while IFS='|' read -r namespace key presence value; do
  if [[ "$key" == "tct_color_temperature_activated" ]]; then
    restore_record "$namespace" "$key" "$presence" "$value"
  fi
done < "$records_file"

nxtpaper_book="$(awk -F'|' '$1 == "secure" && $2 == "nxtpaper_book_style_state" { print $4 }' "$records_file")"
nxtpaper_ink="$(awk -F'|' '$1 == "secure" && $2 == "nxtpaper_ink_style_state" { print $4 }' "$records_file")"
if [[ "$nxtpaper_book" == "1" ]]; then
  nxtpaper_label="Max Ink"
  nxtpaper_guidance="Re-select Max Ink in TCL settings to replay ThemeService side effects."
else
  case "$nxtpaper_ink" in
    0) nxtpaper_label="Normal" ;;
    1) nxtpaper_label="Color paper" ;;
    2) nxtpaper_label="Ink paper" ;;
    *) nxtpaper_label="unknown ($nxtpaper_ink)" ;;
  esac
  nxtpaper_guidance="Re-select that mode in TCL settings or this app to replay ThemeService side effects."
fi

echo "Restored $restored persistent settings from $snapshot"
echo "Recorded NXTPAPER mode: $nxtpaper_label"
echo "$nxtpaper_guidance Settings alone cannot restore theme/wallpaper changes."
echo "Reopen the color panel to refresh its readback."
