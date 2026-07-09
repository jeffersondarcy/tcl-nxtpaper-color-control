#!/usr/bin/env bash
set -euo pipefail

out_dir="${1:-diagnostics}"
mkdir -p "$out_dir"
stamp="$(date +%Y%m%d-%H%M%S)"
out_file="$out_dir/color-state-$stamp.txt"

{
  printf '# TCL NXTPAPER 11 Plus Color State\n\n'
  printf 'Collected: %s\n\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '## Settings\n\n'
  adb shell settings list secure | grep -Ei 'display|night|color|dalton|accessibility|paper|nxt|tcl|eye|reading|comfort|screen|gamma|temperature|white|black|ink|blue|reduce|filter|mode' || true
  adb shell settings list system | grep -Ei 'display|night|color|dalton|accessibility|paper|nxt|tcl|eye|reading|comfort|screen|gamma|temperature|white|black|ink|blue|reduce|filter|mode' || true
  adb shell settings list global | grep -Ei 'display|night|color|dalton|accessibility|paper|nxt|tcl|eye|reading|comfort|screen|gamma|temperature|white|black|ink|blue|reduce|filter|mode' || true
  printf '\n## color_display\n\n'
  adb shell dumpsys color_display
} > "$out_file"

printf '%s\n' "$out_file"
