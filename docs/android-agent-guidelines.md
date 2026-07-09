# Android Agent Guidelines

This project builds a tiny personal Android app for TCL NXTPAPER 11 Plus color
control. Keep every change small, reversible, and real-device verified.

## Architecture

- Use one Android app module.
- Use Kotlin, Compose, and Material 3.
- Keep UI state unidirectional: UI emits events, state holder updates state, UI
  renders immutable state.
- Keep TCL-specific binder calls out of composables.
- Isolate vendor service names, transaction codes, secure setting keys, and
  Parcel logic in `device/`.
- Do not add Hilt, Room, Navigation, multi-module structure, detekt, ktlint,
  R8 tuning, Perfetto, XR, Play Billing, or Android Studio requirements unless
  a later task proves they are needed.

## UI

- The primary app surface is a compact dialog-style Activity with no background
  dimming, so the user can see the screen behind it while adjusting color.
- The preferred over-reader entry point is a Quick Settings tile that launches
  the compact Activity with `TileService.startActivityAndCollapse`; do not add a
  true overlay service unless real-device testing proves the Activity path does
  not stay above the current reader app.
- Use sliders for RGB values and buttons for presets.
- Keep touch targets large and labels short.
- Ensure the controls stay legible under the strongest red/warm matrix.
- The emulator cannot verify the TCL color path; use it only for generic layout
  checks if it is installed later.

## Device Safety

- Do not run root, bootloader unlock, fastboot flashing, wipe, Magisk, or custom
  ROM commands from app implementation tasks.
- The app should prefer no-root TCL binder controls first.
- If direct app binder access fails, document the result and plan a Shizuku or
  shell-assisted fallback.

## Verification

- Source `scripts/android-env.sh` before Gradle commands.
- Run `./gradlew :app:assembleDebug`.
- Run `./gradlew :app:testDebugUnitTest`.
- Run `./gradlew :app:lintDebug`.
- Real TCL validation must compare visual effect and, when possible,
  `dumpsys SurfaceFlinger` color matrix.

## Relevant Official Android Skills

- android/skills: https://github.com/android/skills
- testing-setup: useful for testing strategy, but keep it minimal here.
- edge-to-edge: useful for dialogs/insets if the compact window grows.
- adaptive: useful later for broader layouts, but do not add Navigation 3 for
  this one-screen MVP.
- agp-9-upgrade: useful for Gradle compatibility notes.
