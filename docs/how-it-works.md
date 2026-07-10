# How It Works

## Control Path

The app does not draw a color overlay over the screen. It obtains TCL's private
Binder service through `android.os.ServiceManager` reflection:

```text
service:   tct_nxtvision
interface: tct.nxtvision.ITctComponentNxtvisionManager
```

`AndroidColorBackend` isolates the firmware-specific calls. The verified Binder
transaction IDs on build `1RFO` are:

| Transaction | Purpose |
| ---: | --- |
| 12 | Apply a 16-float SurfaceFlinger client matrix. |
| 22 | Toggle TCL Image Enhancement. |
| 23 | Toggle TCL Video Enhancement. |
| 27 | Select a Screen Color mode. |
| 28 | Select an Advanced Screen Color mode. |

These are private OEM implementation details, not stable Android APIs. A TCL
firmware update can change or remove them.

## RGB And Saturation Matrix

The primary color profile is a 4x4 matrix sent to transaction 12. At 100%
saturation it behaves like independent red, green, and blue channel gains. Lower
saturation mixes each output channel toward luminance using coefficients
`0.231`, `0.715`, and `0.072`, while retaining the selected RGB gains.

The profile and saturation are therefore one compositor transform, not two
independent post-processing stages. This avoids relying on Android's generic
saturation service, which did not provide the required final pipeline control
on the tested firmware.

The app persists the last successfully applied profile and saturation. During a
one-time migration it can infer saturation from TCL's stored 16-element hex
float matrix, but accepts the inference only if reconstructing the matrix matches
within `0.01`. Unknown matrices fall back to 100% without rewriting the current
compositor.

## Inversion Ordering

Android inversion is controlled through
`accessibility_display_inversion_enabled`. Toggling inversion can rebuild the
display transform stack and replace the TCL client matrix, so the app treats the
operation as an ordered sequence:

1. Ensure TCL matrix activation is enabled.
2. Write the requested Android inversion state.
3. Re-send the current RGB/saturation matrix through TCL Binder.
4. Refresh readback and report any failed stage.

On a Binder failure, the backend attempts to restore the previous inversion and
activation values. This is best-effort because multiple system services own the
display pipeline.

## Other Controls

- Brightness uses `Settings.System`. UI `0%` intentionally maps to raw value `1`
  rather than OEM-special raw `0`, which prevents the tablet from rejecting a
  large jump to the minimum.
- Extra Dim uses Android's Reduce Bright Colors secure settings and capability
  detection.
- Bold Text writes `font_weight_adjustment`; High Contrast Text writes
  `high_text_contrast_enabled`.
- Screen Color sends raw modes `0`, `1`, `2`, `10`, `11`, and `12`. Advanced
  modes are sent through both relevant TCL transactions and verified through
  both firmware setting keys.

## Verification

An accepted Binder transaction only means the service accepted a Parcel. Check
the effective compositor state as well:

```bash
scripts/android-verify-color.sh
adb shell dumpsys SurfaceFlinger | grep -A2 -B2 colorTransformMatrix
```

Visual inspection on the physical panel remains necessary. Android's standard
screenshot and screen-recording paths capture content before the final
SurfaceFlinger/TCL panel transform, so their pixels may remain unchanged even
when the display visibly changes.

Use `scripts/snapshot-color-state.sh` before experiments. Its paired restore
script validates a complete snapshot and exact device identity before applying
settings in a conservative order: payload values first, then activation flags.
The in-app **Restore baseline** action is intentionally narrower and resets only
the matrix, inversion, matrix activation, and Extra Dim.
