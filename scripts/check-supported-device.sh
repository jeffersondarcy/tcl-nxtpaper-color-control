#!/usr/bin/env bash
set -euo pipefail

expected_model=9469X
expected_incremental=1RFO

adb get-state >/dev/null
actual_model="$(adb shell getprop ro.product.model | tr -d '\r')"
actual_incremental="$(adb shell getprop ro.build.version.incremental | tr -d '\r')"

if [[ "$actual_model" == "$expected_model" && "$actual_incremental" == "$expected_incremental" ]]; then
  echo "Verified supported device: $actual_model build $actual_incremental."
  exit 0
fi

if [[ "${TCL_ALLOW_UNTESTED_DEVICE:-0}" == "1" ]]; then
  echo "Warning: continuing on untested device $actual_model build $actual_incremental." >&2
  exit 0
fi

echo "Unsupported or untested device: $actual_model build $actual_incremental." >&2
echo "Expected model $expected_model build $expected_incremental." >&2
echo "Set TCL_ALLOW_UNTESTED_DEVICE=1 only after reviewing the firmware-specific Binder risks." >&2
exit 3
