#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
. scripts/android-env.sh

./gradlew :app:testDebugUnitTest :app:lintDebug
