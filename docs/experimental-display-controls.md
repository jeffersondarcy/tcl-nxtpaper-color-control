# Experimental display controls

The `Experiments` tab exposes reversible TCL and Android settings for manual
reading-comfort tests. It intentionally does not store a baseline in the app.

Before a device test, capture the current persistent state:

```bash
scripts/snapshot-color-state.sh diagnostics
```

Restore that exact snapshot with:

```bash
scripts/restore-color-state.sh diagnostics/color-state-TIMESTAMP.txt
```

The restore script returns all 20 historically tested persistent settings to
their prior values, including deleting keys that were previously absent. It
validates the complete snapshot and the connected device serial, model, and
build fingerprint before changing anything. It restores payload values first,
then Extra Dim, inversion, and matrix activation flags after their payloads.

The focused tab contains:

- Six Screen Color modes. Advanced modes verify both TCL color-mode keys.
- Saturation, applied through the same TCL compositor matrix as the RGB profile.
- TCL Image Enhancement and Video Enhancement switches.
- Android Bold Text and High Contrast Text switches.

Saturation is saved after a successful apply. On the first upgraded launch, the
app reconstructs the value from a compatible stored 16-element TCL matrix; an
unknown matrix falls back to 100% without changing the compositor.

Gamma and Darker Display were removed because this firmware does not advertise
their required TCL features. Reading Mode, Sunlight Readability, Color Contrast,
and NXTPAPER were also removed from the app after device testing showed no value
for this reading workflow. Their historical keys remain in snapshots so an old
experiment can still be restored exactly.

Image and video enhancement may be restricted to TCL's multimedia app list.
Accessibility text controls affect rendered UI text and may not affect PDF
pages that LitRes has already rasterized.
