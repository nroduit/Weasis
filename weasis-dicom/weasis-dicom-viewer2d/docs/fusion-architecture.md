# PET/CT Fusion Architecture Guide

This document describes the image fusion system in Weasis: overlaying a
functional series (PET, SPECT/NM) onto an anatomical one (CT, MR) with a
color LUT and an alpha-modulated blend. It covers the concept, the value
pipeline (SUV), the two geometry paths, the LUT/alpha mapping, and the
measurement and color-scale integration.

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
| `FusionVolumeResampler` | Reslices the PET volume on an arbitrary display plane (fork/join, trilinear) |
| `FusionSliceMatcher` | Finds the nearest native PET slice for a base slice (projection on the PET normal) |
| `FusionRegistration` | 2D-affine aligns a native PET slice onto the base pixel grid (warpAffine) |
| `FusionController` | Stateless glue for the EventManager actions: applies params to the target panes, builds the volume, clears caches |
| `FusionState` | Snapshot of a view's fusion configuration (to seed a newly opened MPR) |
| `FusionCompatibility` | Decides which same-study series can be fused onto the base series |
| `FusionMeasurableLayer` | Exposes PET values under a ROI drawn on the base image (SUV statistics) |
| `FusionColorScale` / `FusionColorBar` | The window + LUT actually painted, and the on-view color bar that displays them |
| `ByteLutAlpha` (weasis-core) | 4-channel ABGR LUT: color from a `ByteLut`, per-entry alpha ramp |
| `DicomMediaUtils.computeSUVFactor` (weasis-dicom-codec) | Computes `TagW.SuvFactor` per PET image |

---

## 3. Value Pipeline: from stored pixels to SUVbw

### 3.1 SUV factor

`DicomMediaUtils.computeSUVFactor` follows the QIBA vendor-neutral
pseudocode. It sets `TagW.SuvFactor` on a PT image only when the conversion
is fully determined; otherwise the blocking attribute is logged (debug) and
the series displays in raw units.

Requirements: `CorrectedImage` contains `ATTN` + `DECY`, and `Units` is one of:

| Units | Factor |
|---|---|
| `BQML` | `weight(kg) × 1000 / (dose × 2^(−Δt/T½))` — dose decayed from injection to series time (`DecayCorrection` must be `START`) |
| `CNTS` | Philips private factor `(7053,1000)` when creator is `Philips PET Private Group` |
| `GML` | `1.0` (already grams/milliliter = SUVbw) |

`SUVbw = real_value × SuvFactor`. The factor is computed per image; the
fusion window uses the middle slice's factor (uniform in practice, since the
decay reference is the series time).

### 3.2 Display window (`FusionWindow`)

The window is a series-wide record `(min, max, displayFactor, displayUnit)`
holding **real values**, never a user control — it is derived from the data,
as in dedicated PET viewers, and the `FusionColorBar` is the only place it
can be read.

- **SUV series**: `min = 1 SUVbw` (normal blood pool ≈ 1; below is
  background and stays untinted), `max = ceil(robust SUVmax)` clamped to
  `[4, 30]` SUVbw. `displayFactor = SuvFactor`, `displayUnit = "SUVbw"`.
- **Non-SUV series**: `0 → dataMax`, factor 1, unit = the DICOM pixel value
  unit (e.g. `BQML`, `CNTS`).

Two constructors, one refinement step:

1. `fromSlice(series)` — provisional, middle-slice max only, cheap enough
   for the EDT. Pushed immediately when the user selects an overlay series.
2. `fromVolume(series, volume)` — measured on the whole built volume via
   `FusionWindowEstimator.robustMax`: the 99.5th percentile of the active
   voxels (2048-bin histogram, background = values ≤ 0.1% of max, strided to
   ~4M samples). Physiologic outliers (bladder, injection site) saturate at
   the top of the LUT instead of owning the scale.

`isQuantitative()` is true only for SUVbw — anything else depends on dose,
weight and uptake time and cannot be compared between acquisitions.

### 3.3 Normalization

Both paths map real values to 8-bit gray with the same linear ramp:

```
gray = clamp(round((v − min) × 255 / (max − min)), 0, 255)
```

Values below `min` clamp to 0 (transparent background), values above `max`
saturate at 255 (the hottest LUT entry).

---

## 4. Geometry: two sampling paths

### 4.1 Volume reslice (preferred)

`FusionVolumeBuilder.build` creates a rectified `Volume` from the overlay
series (same machinery as MPR; slices are loaded through
`getUncachedModalityLutImage`, so voxels are real values). Built on a worker
thread; until it completes, fusion renders through the slice path.

`FusionVolumeResampler.resampleToGray` then walks the displayed plane pixel
by pixel (DICOM pixel-center convention):

```
LPS(c, r) = TLHC + c·row·spacingX + r·col·spacingY
voxel     = volume.lpsToVoxel(LPS)          // exact inverse of the volume writes
v         = volume.getInterpolatedDouble()  // trilinear, sign-safe
```

This stays correct for MPR coronal/sagittal and oblique reslices, where a
single-slice 2D affine cannot be. Out-of-volume pixels stay at gray 0
(transparent). The work is split over the fork/join common pool by bands of
rows.

`resampleToValue` is the statistics variant: `CV_32FC1`, **nearest-neighbour**
(no interpolation loss on SUVmax), `NaN` outside the volume.

### 4.2 Single-slice fallback

When no volume is available (still building, or non-rectifiable geometry):

1. `FusionSliceMatcher` projects the base slice center onto the PET normal
   and picks the nearest PET slice.
2. The slice's real values (`getModalityLutImage`) are windowed and colorized.
3. `FusionRegistration.alignOverlayToBase` maps it onto the base grid with a
   single `warpAffine` (scale from the `PixelSpacing` ratio + translation
   from the projected TLHC for coplanar series; a 3-corner affine through
   patient space otherwise; plain resize when geometry is missing).

Only valid when the displayed plane is (near) parallel to the PET slices —
the usual axial PET/CT case.

### 4.3 Caches

Colorized overlays are cached with the color **baked in** but the opacity
**not** (opacity is applied at composite time, so moving the opacity slider
never invalidates a cache):

- Slice path: `WeakHashMap<DicomImageElement, PlanarImage>` keyed by the
  matched PET slice.
- Volume path: bounded LRU keyed by `GeometryOfSlice` (value equality).
  MPR reuses a single `DicomImageElement` per axis and only mutates its
  geometry between reslices, so identity keying would serve the first
  plane's overlay for every subsequent reslice.

`FusionController` clears the caches whenever the series, LUT, window or
volume changes.

---

## 5. LUT and alpha mapping

### 5.1 `ByteLutAlpha`

A `[4][256]` ABGR LUT built from a standard 3-channel BGR `ByteLut`
(`fromColorLut`). The channel order matches `TYPE_4BYTE_ABGR`. The alpha
channel is a piecewise ramp:

```
alpha
 255·op ┤          ┌────────────────────────────
        │        ╱
        │      ╱
     0  ┤─────┘
        └─────┬────┬───────────────────────────┬─ entry
              2    25                          255
        transparent  ramp   full overlay opacity
```

- Entries `0..2` (`ALPHA_TRANSPARENT_BELOW = 256/100`): fully transparent —
  air / outside the body never tints the anatomy.
- Full opacity from entry `25` (`ALPHA_OPAQUE_FROM = 256/10`): alpha must
  saturate early. The color LUT already encodes the value; if alpha also
  ramped across the whole range, a low entry would be dark *and*
  transparent, and everything but the hottest voxels would vanish.

The LUT is built with opacity `1.0`; the user's overlay opacity is applied
at composite time only (never double-applied, caches survive it).

### 5.2 Colorization in one native call

```java
Core.merge(List.of(gray, gray, gray, gray), src4);  // replicate intensity
Core.LUT(src4, abgrLutMat, dst);                    // ABGR lookup per pixel
```

The gray mask is replicated into all four channels so **every** output
channel — alpha included — is looked up from the same intensity.
(`GRAY2BGRA` would pin the 4th channel to 255, i.e. alpha would always read
entry 255.)

### 5.3 Compositing

```
result = baseOpacity · base · (1 − a)  +  overlay · a
a      = (overlayAlpha / 255) · overlayOpacity
```

Defaults: base opacity `1.0`, overlay opacity `0.5` — the even split PET/CT
readers expect: the LUT bottom is black, so the overlay dims the anatomy by
half where uptake is low, and pushing higher darkens the CT more than it
reveals.

---

## 6. Color bar (`FusionColorBar`)

Painted by `InfoLayer` (preference `LayerItem.FUSION_LUT`, on by default).
Since the window is derived rather than user-set, the bar is the only place
the scale can be read:

- Labels are in the **display unit**: SUVbw when the factor is known, the
  raw DICOM unit otherwise, and a percentage of the window when even that is
  unknown — an unlabelled number would be read as a value.
- Both clip bounds are always labelled; intermediate ticks use a 1-2-5
  "nice step" (~4 intervals).
- Fully transparent LUT entries are left blank, so the deliberately hidden
  background band is visible as such instead of being mistaken for a color.
- It shifts left when the base image LUT bar already occupies the right edge.

`FusionOp.getColorScale()` exposes the exact `FusionWindow` + `ByteLutAlpha`
being painted, so the bar can never drift from the rendering.

---

## 7. Measurements (`FusionOp.getStatsLayer`)

An area measurement drawn on the fused base image additionally reports
overlay statistics (labelled with the overlay modality, e.g. `PT`), through
a `FusionMeasurableLayer`:

- **Native slice mode** (preferred): the ROI is affine-mapped through
  patient space into the matched PET slice's pixel grid
  (`baseToOverlayTransform`, planes must be near-parallel: |n₁·n₂| ≥ 0.999)
  and statistics run on the **untouched stored voxels** with the slice's own
  rescale — identical to measuring on the PET series directly, no
  interpolation loss on SUVmax.
- **Resampled volume mode** (oblique/MPR fallback): `resampleToValue` output
  — real values on the base grid, `NaN` excluded from statistics.

In both modes the `SuvFactor` of the reference slice converts real values to
SUV on top. The layer is computed on demand (statistics are only requested
on measurement release), never cached.

---

## 8. Control flow and lifecycle

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
  change together (`FusionController.targetViews`).
- A 2D view's fusion state is snapshot (`FusionState`) and re-applied when
  an MPR of the same series opens, reusing the already-built volume.
- `RESET_DISPLAY` turns fusion off and releases the volume reference;
  `SERIES_CHANGE` keeps the overlay only while it remains compatible with
  the new base series (`FusionCompatibility`), otherwise disables it.

---

## 9. Invariants to preserve

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

---

## 10. Related tests

| Test | Covers |
|---|---|
| `SuvFactorTest` (weasis-dicom-codec) | BQML decay formula, GML, missing-attribute guards, non-PT modality |
| `FusionWindowTest` | SUV scale bounds/clamping, display factor, raw fallback, degenerate range |
| `FusionColorBarTest` | 1-2-5 tick step |
| `ByteLutAlphaTest` (weasis-core) | ABGR layout, alpha ramp, validation |