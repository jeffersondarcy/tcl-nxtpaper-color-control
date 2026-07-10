# Device Compatibility

## Verified Device

All vendor-specific behavior was tested on one tablet:

| Field | Value |
| --- | --- |
| Marketing name | TCL NXTPAPER 11 Plus |
| Model | 9469X |
| Product / codename | 9469X_EEA / Bellona_WF_GL |
| Android | 15 / SDK 35 |
| Build | AP3A.240905.015.A2 / 1RFO |
| Security patch | 2026-04-05 |
| Hardware | MediaTek MT8781V/CA (`mt8781`) |

The bootloader remained locked and verified boot remained green. No root or
firmware modification was used.

## Observed Behavior

| Mechanism | Result on the tested build |
| --- | --- |
| TCL 4x4 client matrix | Visible full-display RGB change; reflected in SurfaceFlinger readback. |
| Saturation in the same matrix | Visible and persistent across overlay restart. |
| Android color inversion | Visible; matrix must be reapplied after stack changes. |
| Brightness / automatic brightness | Working with Modify system settings access. |
| Extra Dim / intensity | Working through Android Reduce Bright Colors settings. |
| Six Screen Color modes | Working; Advanced modes require dual-key handling. |
| Bold Text | Working for Android-rendered UI text. |
| High Contrast Text | Working for eligible Android UI text. |
| Image / Video Enhancement | Binder calls and setting readback work; visible scope appears content/app dependent. |

Gamma, Darker Display, Reading Mode, Sunlight Readability, Color Contrast, and
additional NXTPAPER modes are not exposed because testing found no reliable,
distinct effect for this app's reading workflow.

## PDF And Text Rendering

PDF reader behavior depends on its rendering pipeline. If a page is rasterized
to a bitmap, Android accessibility typography settings cannot change glyph
weight or antialiasing inside that bitmap. Bold Text and High Contrast Text can
still affect the reader's native controls. RGB matrices, saturation, inversion,
brightness, and Extra Dim operate later in the display path and can affect the
whole visible page.

Light text on a dark background can still feel less sharp than dark text on a
light background because of display flare, pupil dilation, font weight, and the
way antialiasing was computed before inversion. This app can alter the final
colors but cannot rerender glyph outlines inside an already rasterized PDF.

## Other Devices And Updates

Compatibility is unknown outside model 9469X on build 1RFO. In particular:

- A service with the same name may use different transaction IDs.
- Secure/system setting keys may be absent or owned by another component.
- SELinux or hidden-API policy may reject access.
- A firmware update may change mode values or matrix ordering.

Do not treat a successful install or a successful Binder return value as a
compatibility guarantee. Take a snapshot, test one reversible control at a time,
inspect readback, and keep the baseline restore action available.
