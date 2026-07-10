#!/usr/bin/env sh
# shellcheck disable=SC2317 # `exit` is the fallback when this source-only file is executed directly.

# Source this file before Android build commands:
#   . scripts/android-env.sh

if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ ! -d "$ANDROID_SDK_ROOT" ]; then
  echo "ANDROID_SDK_ROOT does not exist: $ANDROID_SDK_ROOT" >&2
  return 1 2>/dev/null || exit 1
fi

if [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  if [ -n "${ANDROID_HOME:-}" ]; then
    ANDROID_SDK_ROOT="$ANDROID_HOME"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
  elif [ -d "$HOME/Android/Sdk" ]; then
    ANDROID_SDK_ROOT="$HOME/Android/Sdk"
  else
    echo "Android SDK not found. Set ANDROID_SDK_ROOT before sourcing this script." >&2
    return 1 2>/dev/null || exit 1
  fi
fi

if [ ! -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]; then
  echo "Android SDK Platform Tools not found under $ANDROID_SDK_ROOT." >&2
  return 1 2>/dev/null || exit 1
fi
if [ ! -d "$ANDROID_SDK_ROOT/platforms/android-36" ]; then
  echo "Android SDK Platform 36 not found under $ANDROID_SDK_ROOT." >&2
  return 1 2>/dev/null || exit 1
fi

java_home_is_17() {
  [ -n "${1:-}" ] &&
    [ -x "$1/bin/java" ] &&
    "$1/bin/java" -version 2>&1 | grep -q 'version "17\.'
}

if ! java_home_is_17 "${JAVA_HOME:-}"; then
  JAVA_HOME=
  if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  fi
  if ! java_home_is_17 "${JAVA_HOME:-}" && [ -d /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ]; then
    JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
  fi
  if ! java_home_is_17 "${JAVA_HOME:-}" && [ -d /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ]; then
    JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
  fi
  if ! java_home_is_17 "${JAVA_HOME:-}"; then
    echo "JDK 17 not found. Set JAVA_HOME before sourcing this script." >&2
    return 1 2>/dev/null || exit 1
  fi
fi

ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_HOME ANDROID_SDK_ROOT JAVA_HOME
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
