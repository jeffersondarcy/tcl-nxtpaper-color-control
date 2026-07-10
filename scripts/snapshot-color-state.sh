#!/usr/bin/env bash
set -euo pipefail

out_dir="${1:-diagnostics}"
adb get-state >/dev/null
mkdir -p "$out_dir"
stamp="$(date +%Y%m%d-%H%M%S)"
out_file="$out_dir/color-state-$stamp.txt"
tmp_file="$out_file.tmp.$$"
expected_restore_records=20
trap 'rm -f "$tmp_file"' EXIT

{
  printf '# TCL NXTPAPER 11 Plus Color State\n\n'
  printf 'Collected: %s\n\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '## Device Identity\n\n'
  printf 'Device serial: %s\n' "$(adb get-serialno | tr -d '\r')"
  printf 'Device model: %s\n' "$(adb shell getprop ro.product.model | tr -d '\r')"
  printf 'Build fingerprint: %s\n\n' "$(adb shell getprop ro.build.fingerprint | tr -d '\r')"
  printf '## Settings\n\n'
  adb shell settings list secure | grep -Ei 'display|night|color|dalton|accessibility|paper|nxt|tcl|eye|reading|comfort|screen|gamma|temperature|white|black|ink|blue|reduce|filter|mode' || true
  adb shell settings list system | grep -Ei 'display|night|color|dalton|accessibility|paper|nxt|tcl|eye|reading|comfort|screen|gamma|temperature|white|black|ink|blue|reduce|filter|mode' || true
  adb shell settings list global | grep -Ei 'display|night|color|dalton|accessibility|paper|nxt|tcl|eye|reading|comfort|screen|gamma|temperature|white|black|ink|blue|reduce|filter|mode' || true
  printf '\n## color_display\n\n'
  adb shell dumpsys color_display
  printf '\n## Experimental Restore Values\n\n'
  printf '# namespace|key|presence|value\n'
  while IFS=' ' read -r namespace key; do
    value="$(adb shell settings get "$namespace" "$key" </dev/null | tr -d '\r')"
    if [[ "$value" == "null" ]]; then
      printf '%s|%s|absent|\n' "$namespace" "$key"
    else
      printf '%s|%s|present|%s\n' "$namespace" "$key" "$value"
    fi
  done <<'EOF'
secure accessibility_display_inversion_enabled
secure tct_color_temperature_activated
secure tct_color_temperature_matrix
secure font_weight_adjustment
secure high_text_contrast_enabled
secure contrast_level
secure nxtpaper_book_style_state
secure nxtpaper_ink_style_state
secure reduce_bright_colors_activated
secure reduce_bright_colors_level
system color_mode_value
system adv_color_mode_value
system nxt_gamma_setting
system nxt_gamma_setting_reason
system multimedia_enhancement_enable
system video_enhancement_enable
system sunshine_enable_setting
system reading_mode_always_enable
system darker_display_mode_enable
system night_brightness_mode
EOF
  printf '# restore-record-count=%s\n' "$expected_restore_records"
} > "$tmp_file"

actual_restore_records="$(awk '/^(secure|system|global)\|/ { count += 1 } END { print count + 0 }' "$tmp_file")"
if [[ "$actual_restore_records" -ne "$expected_restore_records" ]]; then
  echo "Incomplete snapshot: expected $expected_restore_records restore records, got $actual_restore_records" >&2
  exit 3
fi

mv "$tmp_file" "$out_file"
trap - EXIT

printf '%s\n' "$out_file"
