# TCL NXTPAPER Color Control Agent Guide

## Project

This is a small, device-specific Android app for no-root display controls on a
TCL NXTPAPER 11 Plus. It was developed with coding-agent assistance and the
repository intentionally keeps this guide so future contributors can continue
that workflow transparently.

The app is verified only on:

- TCL NXTPAPER 11 Plus, model 9469X
- Product/codename: 9469X_EEA / Bellona_WF_GL
- Android 15, SDK 35
- Build AP3A.240905.015.A2 / 1RFO

## Safety

- Do not run bootloader unlock, root, Magisk, flashing, wipe, factory reset, or
  firmware commands as part of app development.
- Keep changes reversible and record display settings before device experiments.
- Treat TCL Binder names, transaction IDs, setting keys, and mode values as
  firmware-specific. An accepted Binder call is not proof of a visible effect.
- Never commit device dumps, serial numbers, MAC addresses, OEM APK/JAR files,
  decompiled sources, keystores, or signing credentials.
- Use `scripts/snapshot-color-state.sh` before changing experimental settings and
  `scripts/restore-color-state.sh` only with a snapshot from the same device and
  build.

## Architecture

- Keep the project as one Kotlin/Compose app module.
- Preserve unidirectional UI state through `ColorControlViewModel`.
- Keep TCL-specific Binder calls, transaction IDs, setting keys, and Parcel
  logic under `app/src/main/java/com/jeff/tclcolorcontrol/device/`.
- Keep profile and saturation math in `color/`, persistence in `state/`, and
  Compose rendering in `ui/`.
- Avoid adding Hilt, Room, Navigation, or multiple modules unless the product
  grows enough to require them.
- Do not change the deliberate `targetSdk 28` without first proving TCL Binder
  compatibility on the real tablet and documenting the result.

## Verification

Source `scripts/android-env.sh`, then run:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Run shell syntax checks for every script. Generic UI can be checked on an
emulator, but TCL color behavior must be tested on the physical tablet. Confirm
matrix behavior with both visual inspection and SurfaceFlinger readback as
described in `docs/how-it-works.md`.

## Scope

Public repository content should stay focused on the app, tests, build files,
documentation, and reversible build/install/verification scripts. Local
research belongs in ignored `diagnostics/` or outside this repository.
