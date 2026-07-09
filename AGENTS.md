# TCL NXTPAPER 11 Plus Android Modification Workspace

## Purpose

This workspace tracks diagnostics, notes, and future modification work for a
TCL NXTPAPER 11 Plus Android tablet connected to this Mac.

The primary product goal is deeper system-level color and blue-light control
than regular overlay apps can provide. Root, Magisk, and custom ROM work are
possible future paths, but they are not assumptions. Prefer stock or reversible
ADB-level options first.

## Safety Rules

- Do not run bootloader unlock commands without explicit user approval.
- Do not flash partitions, boot images, recovery images, vbmeta images, or ROMs
  without explicit user approval.
- Do not root the device, patch Magisk images, or install Magisk modules without
  explicit user approval.
- Do not factory reset, wipe, erase, format, or relock the bootloader without
  explicit user approval.
- Treat all stock firmware images as build-specific. Never flash or patch an
  image from another device, even if the marketing model name matches.
- Record original settings before changing Android display/color settings.
- Prefer non-destructive ADB diagnostics before fastboot, root, or firmware work.

## Device Facts

Fill these from ADB diagnostics before making root or firmware decisions.

- Marketing device: TCL NXTPAPER 11 Plus
- Exact model: 9469X
- Product/device/codename: 9469X_EEA / Bellona_WF_GL
- Android version: 15 / SDK 35
- Security patch: 2026-04-05
- Build fingerprint: TCL/9469X_EEA/Bellona_WF_GL:15/AP3A.240905.015.A2/1RFO:user/release-keys
- Build ID / incremental: AP3A.240905.015.A2 / 1RFO
- SoC / hardware platform: MediaTek MT8781V/CA, hardware mt8781
- Bootloader version: U1RFO0O0EB00
- Verified boot state: green, locked, verity enforcing
- OEM unlock supported:
- OEM unlock ability: sys.oem_unlock_allowed=0 at first ADB inventory
- Boot image path: /dev/block/by-name/boot_b for active slot _b
- Init boot image path: /dev/block/by-name/init_boot_b for active slot _b
- Recovery image path:
- Active slot / A/B status: active slot _b, A/B OTA enabled
- Partition layout notes:
  - boot_a/boot_b, init_boot_a/init_boot_b, vendor_boot_a/vendor_boot_b, vbmeta_a/vbmeta_b exist.
  - Dynamic partitions are enabled.
  - Bootloader/root work must identify whether Magisk should patch init_boot or boot for this build before flashing anything.

## Workflow Notes

- Use Android SDK Platform Tools for `adb` and `fastboot`.
- Start with `adb devices`; the expected state is `device`, not `unauthorized`.
- If the device is `unauthorized`, approve this Mac's RSA prompt on the tablet.
- Keep OEM unlocking disabled unless root preparation explicitly begins.
- Save diagnostics under `diagnostics/`.
- Keep original firmware under `firmware/original/`.
- Keep patched images under `firmware/patched/`, labeled with the exact build
  and date.
- Keep command output that affects decisions in project notes, not only terminal
  scrollback.
- Current best non-root research target: TCL binder service `tct_nxtvision`
  (`tct.nxtvision.ITctComponentNxtvisionManager`). Known useful calls include
  `setSFClentMatrix(float[16])`, `setDisplayWhiteBalanceColorTemperature(int)`,
  `setAdvColorModeValue(int)`, and `setGammaValue(int)`.
- Do not treat accepted binder calls as proof that a color transform is active.
  Confirm with `dumpsys SurfaceFlinger | rg -A2 -B2 "colorTransformMatrix"`
  and visual testing, then restore baseline if the user did not explicitly ask
  to keep the change.
- Practical color controls live in `scripts/tcl-color-control.sh`.
  `deep-matrix` and `extra-deep-matrix` are confirmed to change the final
  SurfaceFlinger compositor matrix without root.
- Android app setup and agentic development recommendations are documented in
  `docs/android-dev-setup-20260709.md`.
- Android implementation guidelines for this project are documented in
  `docs/android-agent-guidelines.md`.
- Android toolchain installation details are documented in
  `diagnostics/toolchain-setup-20260709.md`. Source `scripts/android-env.sh`
  before Gradle/SDK commands so the project uses JDK 17 and the standard SDK
  root.
- If building the on-device color-control app, keep the MVP as a single-module
  Kotlin + Compose app with a small ViewModel/state holder and an isolated TCL
  backend. Avoid Hilt, Room, Navigation, and multi-module architecture unless
  the app clearly grows into needing them.
- The emulator is useful for generic UI checks only. TCL color behavior must be
  verified on the real tablet.

## Initial References

- Android Platform Tools: https://developer.android.com/tools/releases/platform-tools
- AOSP bootloader unlock docs: https://source.android.com/docs/core/architecture/bootloader/locking_unlocking
- Magisk install docs: https://topjohnwu.github.io/Magisk/install.html
