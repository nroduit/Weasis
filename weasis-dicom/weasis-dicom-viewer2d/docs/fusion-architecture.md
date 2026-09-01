# PET/CT Fusion Architecture Guide

This document describes the image fusion system in Weasis: overlaying a
functional series (PET, SPECT/NM) onto an anatomical one (CT, MR) with a
color LUT and an alpha-modulated blend. It covers the concept, the value
pipeline, the two geometry paths, and the invariants to preserve.

All classes live in `org.weasis.dicom.viewer2d.fusion` unless noted otherwise.

---

## 1. Overview

Fusion is an opt-in display operation (`FusionOp`) inserted in the per-view
op chain. For every rendered base slice it builds an ABGR overlay from the
functional series, aligned to the base pixel grid, and composites it over
the base image.

```
                 base (CT/MR) op chain
  WindowAndPresetsOp → FilterOp → PseudoColorOp → ShutterOp → OverlayOp
        → FusionOp → … → AffineTransformOp (zoom/rotation)
             │
             │ P_FUSION_SERIES (PET), P_FUSION_VOLUME, P_FUSION_LUT,
             │ P_FUSION_WINDOW, P_OPACITY_BASE, P_OPACITY_OVERLAY
             ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ FusionOp.process()                                          │
  │                                                             │
  │  1. Sample PET on the displayed plane                       │
  │       volume path: FusionVolumeResampler (3D reslice)       │
  │       slice path:  FusionSliceMatcher + FusionRegistration  │
  │  2. Window real values → 8-bit gray   (FusionWindow)        │
  │  3. Colorize gray → ABGR overlay      (ByteLutAlpha)        │
  │  4. Composite over the base image     (alpha blend)         │
  └─────────────────────────────────────────────────────────────┘
```

`FusionOp` sits **after** the base windowing/pseudo-color (so it blends with
the 8-bit rendered anatomy) and **before** the zoom/rotation affine (so all
geometry is computed on the un-zoomed base pixel grid).

Key invariant: **every value handed to the window is a modality-LUT (real)
value** — rescale slope/intercept already applied, i.e. BQML for PET. Both
sampling paths honor this, so a given activity gets the same color on every
slice and on every path.

---

## 2. Key Classes

| Class | Role |
|---|---|
| `FusionOp` | The op node: orchestrates sampling, windowing, colorization, compositing; owns the overlay caches |
| `FusionWindow` | Series-wide display window (record): real-value bounds + display factor/unit (SUVbw) |
| `FusionWindowEstimator` | Robust (percentile) maximum of the volume, excluding physiologic outliers |
| `FusionVolumeBuilder` | Builds the rectified PET `Volume` off the EDT (reuses the MPR `Volume` machinery) |
| `FusionVolumeResampler` | Reslices the PET volume on an arbitrary display plane |
| `FusionSliceMatcher` | Finds the nearest native PET slice for a base slice |
| `FusionRegistration` | 2D-affine aligns a native PET slice onto the base pixel grid |
| `FusionController` | Stateless glue for the EventManager actions: applies params to the target panes, builds the volume, clears caches |
| `FusionState` | Snapshot of a view's fusion configuration (to seed a newly opened MPR) |
| `FusionCompatibility` | Decides which same-study series can be fused onto the base series |
| `FusionMeasurableLayer` | Exposes PET values under a ROI drawn on the base image (SUV statistics) |
| `FusionColorScale` / `FusionColorBar` | The window + LUT actually painted, and the on-view color bar that displays them |
| `ByteLutAlpha` (weasis-core) | 4-channel ABGR LUT: color from a `ByteLut`, per-entry alpha ramp |
| `DicomMediaUtils.computeSUVFactor` (weasis-dicom-codec) | Computes `TagW.SuvFactor` per PET image |

---

## 3. Value Pipeline: from stored pixels to SUVbw

- **SUV factor.** `DicomMediaUtils.computeSUVFactor` sets `TagW.SuvFactor` on a
  PT image only when the conversion is fully determined (QIBA vendor-neutral
  approach); otherwise the series displays in raw units.
  `SUVbw = real_value × SuvFactor`.
- **Display window.** `FusionWindow` is a series-wide record
  `(min, max, displayFactor, displayUnit)` holding **real values**. It is
  derived from the data, not a user control. A provisional window
  (`fromSlice`) is computed on the EDT as soon as an overlay series is
  selected, then refined on the whole volume (`fromVolume`, using
  `FusionWindowEstimator`) so physiologic outliers saturate instead of
  owning the scale. SUV is only a display factor on top of real values.
- **Normalization.** Both paths map real values to 8-bit gray with the same
  linear ramp between `min` and `max`: below `min` is transparent
  background, above `max` saturates at the hottest LUT entry.

---

## 4. Geometry: two sampling paths

- **Volume reslice (preferred).** `FusionVolumeBuilder` builds a rectified
  `Volume` from the overlay series on a worker thread (voxels are real
  values). `FusionVolumeResampler` then samples it on the displayed plane,
  which stays correct for MPR coronal, sagittal and oblique reslices.
- **Single-slice fallback.** While no volume is available,
  `FusionSliceMatcher` picks the nearest native PET slice and
  `FusionRegistration` maps it onto the base grid with one 2D affine. Only
  valid when the displayed plane is (near) parallel to the PET slices — the
  usual axial PET/CT case.
- **Caches.** `FusionOp` caches colorized overlays with the color baked in
  but not the opacity. The volume-path cache is keyed by `GeometryOfSlice`
  value, because MPR reuses one `DicomImageElement` per axis and only
  mutates its geometry.

---

## 5. LUT, alpha and compositing

`ByteLutAlpha` turns a standard 3-channel `ByteLut` into an ABGR LUT whose
alpha is a ramp: the lowest entries are fully transparent (air never tints
the anatomy) and alpha saturates early, since the color already encodes the
value. The windowed gray image is looked up through this LUT in all four
channels, then alpha-blended over the base image using the base and overlay
opacities (`P_OPACITY_BASE`, `P_OPACITY_OVERLAY`). The overlay opacity is
applied only at composite time.

---

## 6. Color bar and measurements

- **Color bar.** `FusionColorBar`, painted by `InfoLayer` (`LayerItem.FUSION_LUT`),
  is the only place the derived scale can be read. It is labelled in the
  display unit (SUVbw when known). `FusionOp.getColorScale()` exposes the
  exact window and LUT being painted, so the bar cannot drift from the rendering.
- **Measurements.** An area measurement on the fused base image also reports
  overlay statistics through `FusionMeasurableLayer` (`FusionOp.getStatsLayer`).
  For coplanar series the ROI is mapped onto the native PET slice and
  statistics run on the stored voxels; for oblique/MPR planes they run on
  resampled real values. `SuvFactor` converts the result to SUV.

---

## 7. Control flow and lifecycle

```
User selects overlay series (EventManager FusionAction.SERIES)
  ├─ FusionController.applyParam(P_FUSION_SERIES)   ── drops stale volume+window
  ├─ applyDefaultFusionWindow → FusionWindow.fromSlice   (provisional, EDT-safe)
  └─ FusionController.buildVolume (worker thread)
        ├─ FusionVolumeBuilder.build
        ├─ FusionWindow.fromVolume (robust max — scans voxels)
        └─ applyVolume: only to panes still showing that series
                        (guards against series switches and out-of-order builds)
```

- Fusion is **per-view**; MPR is the exception — its three panes take every
  change together.
- A 2D view's fusion state is snapshot (`FusionState`) and re-applied when
  an MPR of the same series opens, reusing the already-built volume.
- `RESET_DISPLAY` turns fusion off and releases the volume reference;
  `SERIES_CHANGE` keeps the overlay only while it remains compatible with
  the new base series (`FusionCompatibility`), otherwise disables it.

---

## 8. Invariants to preserve

1. **One value domain.** Window bounds, volume voxels and slice samples are
   all modality-LUT (real) values. Never feed stored pixel values to the
   window, and never window SUV-scaled values (SUV is a display factor).
2. **Pixel-center convention everywhere.** `GeometryOfSlice.getPosition` /
   `getImagePosition` are exact inverses with TLHC = center of pixel (0,0);
   `Volume.lpsToVoxel` must stay the exact inverse of the volume writes.
3. **Opacity is composite-time only.** Nothing opacity-dependent may be
   baked into a cached overlay.
4. **Cache invalidation** on series / LUT / window / volume change goes
   through `FusionController` — a new invalidating parameter must be added
   to `applyParam`'s `clearCache` set.
5. **Alpha 0 = not shown.** The color bar relies on `alpha == 0` to blank
   entries; the transparent band must remain exactly the entries the
   renderer skips.
6. **Statistics prefer native voxels.** Any change to the stats path must
   keep SUVmax free of resampling loss for the coplanar case.
7. **Volume building stays off the EDT**, and applying its result must keep
   the "series still selected" guard (async builds can finish out of order).
