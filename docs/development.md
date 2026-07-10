# Development

## Toolchain

- JDK 17
- Android SDK Platform 36
- Android SDK Platform Tools (`adb`)
- Gradle Wrapper 9.4.1
- Android Gradle Plugin 9.2.1
- Kotlin/Compose plugin 2.3.21

`scripts/android-env.sh` respects existing `JAVA_HOME` and
`ANDROID_SDK_ROOT`, then checks conventional macOS and Linux SDK locations plus
Homebrew JDK 17 locations. It validates JDK 17, Platform Tools, and Platform 36,
and prints a clear error instead of embedding a local developer path.

```bash
. scripts/android-env.sh
./gradlew :app:assembleDebug
```

## Deliberate SDK Configuration

The app uses `compileSdk 36`, `minSdk 35`, and deliberately keeps
`targetSdk 28`. The low target is a compatibility choice for reflection and a
private OEM Binder surface on the tested Android 15 firmware; it is not a claim
of broad modern-Android compatibility. Gradle warns because `minSdk` is higher
than `targetSdk`; that warning is expected here.

Changing `targetSdk` can alter hidden-API enforcement and platform behavior.
Any upgrade must be tested on the real 9469X across matrix apply, inversion,
overlay launch, Quick Settings launch, and all permission paths. This repository
is not configured for Play Store publication.

## Project Layout

```text
app/src/main/java/com/jeff/tclcolorcontrol/
  device/   TCL Binder and Android settings backend
  color/    profiles, matrix generation, saturation migration
  state/    ViewModel and SharedPreferences persistence
  ui/       Compose panel and theme
scripts/    build, install, verification, snapshot, and restore tools
docs/       public technical notes
```

The app is intentionally one module with no dependency injection framework,
database, or navigation framework. The UI emits actions to
`ColorControlViewModel`; the backend owns all platform and TCL-specific effects.

## Tests And Checks

```bash
. scripts/android-env.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
for script in scripts/*.sh; do bash -n "$script"; done
```

Unit tests cover profile/matrix math, saturation migration, brightness and Extra
Dim mapping, backend rollback/readback, ViewModel behavior, and panel placement.
Lint runs against the debug variant.

The emulator is useful only for generic layout. Real-device verification should
include:

1. Build and install with `scripts/android-install.sh`.
2. Confirm overlay and Modify system settings access.
3. Confirm `WRITE_SECURE_SETTINGS` in the install script output.
4. Exercise both tabs, panel drag/minimize/close, Quick Settings entry, and
   profile persistence.
5. Compare visible color changes with SurfaceFlinger readback.
6. Restore the pre-test snapshot when the experiment should not persist.

Install and verification scripts refuse devices other than model `9469X` on
build `1RFO`. For deliberate porting work only, set
`TCL_ALLOW_UNTESTED_DEVICE=1` after reviewing the private Binder transaction
risks.

## Snapshot And Restore

`snapshot-color-state.sh` writes a private diagnostic file containing device
identity, display dumps, and a strict 20-key restore block. `diagnostics/` is
gitignored. `restore-color-state.sh` refuses incomplete snapshots and snapshots
whose serial, model, or fingerprint differ from the connected device.

The script can restore persistent settings, but TCL theme or wallpaper side
effects may require reselecting the recorded NXTPAPER mode in system settings.

## Release Artifact

Version `0.1.0` uses package ID `com.jeff.tclcolorcontrol`, `versionCode 1`, and
debug signing. The debug APK is suitable for direct ADB installation, not store
distribution. Publish its SHA-256 alongside the artifact and warn that moving to
a different signing key may require uninstalling the debug build.
