# Curved MPR (cMPR)

Curved Multi-Planar Reconstruction reformats a 3D volume along a user-drawn path. From a
single polyline it produces two derivatives:

- a **panoramic view** — the volume "straightened" along the curve and shown as one 2D cell
  inside the MPR container (the dental panoramic / OPG use case);
- a **cross-sectional series** — a stack of slabs cut perpendicular to the curve, opened as a
  real DICOM series in a new viewer tab (the dental cross-cuts use case).

The generator is currently oriented toward a curve drawn on the **axial** plane at a fixed Z
level (a dental arch): it samples height along the volume's Z axis. See
[Limitations](#limitations).

All code lives in `org.weasis.dicom.viewer2d.mpr.cmpr`. The only callers are
`MprView` (context-menu actions) and `MprContainer` (orchestration).

## Classes

| Class | Role |
|-------|------|
| `CurvedMprBuilder` | Static factory/orchestrator. `buildAxis(...)` assembles the panoramic axis+reader; `openCrossSectionSeries(...)` builds and opens the cross-section series. Centralizes DICOM tag inheritance. |
| `CurveSampler` | Pure geometry: Catmull-Rom smoothing, arc-length resampling, per-sample in-plane perpendicular directions. Shared by both derivatives. |
| `CurvedMprAxis` | Curve state + parameters (`widthMm`, `stepMm`), live-edit binding to the source polyline, and the optional debug overlay. |
| `CurvedMprImageIO` | `DcmMediaReader` that generates the panoramic image (MIP slab along the curve). MIME `image/cmpr`. |
| `CurvedMprView` | `View2d` subclass: the in-container panoramic cell. Adds the "Panoramic settings" popup. |
| `CrossSectionParams` | `record(widthMm, heightMm, spacingMm)` with volume-derived `defaults(...)`. |
| `CrossSectionDialog` | Modal dialog that prompts for the cross-section parameters. |
| `CrossSectionImageIO` | `DcmMediaReader` for one perpendicular slab. MIME `image/cmpr-xs`. |

## User workflow

There is **no dedicated drawing tool and no toolbar button**. The curve is an ordinary
`PolylineGraphic` (the standard measurement polyline).

1. Open a volume in the MPR container.
2. Draw a polyline on a plane (in practice the axial plane) tracing the structure of interest.
3. Right-click the completed polyline. The context menu offers:
   - **Build panoramic view** → `MprView.openCurvedMprFromPolyline` → `MprContainer.openCurvedMpr`.
   - **Build cross-sectional slices** → `MprView.openCrossSectionsFromPolyline` → `CrossSectionDialog`
     → `MprContainer.openCrossSections`.

A polyline needs at least **2** valid curve points (points that map to volume coordinates);
otherwise the action is silently aborted with a warning in the log.

## Panoramic view

### Wiring

`MprContainer.openCurvedMpr` builds the axis (`CurvedMprBuilder.buildAxis`), binds it to the
source polyline for live updates (`CurvedMprAxis.bindPolyline`), switches the layout to
`mprWithCurved` (a 2×2 grid: axial / coronal / sagittal + one `CurvedMprView` cell), and calls
`curvedView.setCurvedMprAxis(axis)`. The panoramic therefore appears **in place** inside the
existing container — not in a separate window.

`CurvedMprView` extends `View2d`, so it inherits the standard window/level, LUT, zoom and pan
controls. It adds a single "Panoramic settings" button (top-right) whose popup has two sliders:

- **Height** → `CurvedMprAxis.setWidthMm` — the vertical (Z) extent of the panoramic.
- **Step** → `CurvedMprAxis.setStepMm` — the sampling distance along the curve; its label shows
  the resulting sample count (arc length ÷ step).

Both sliders regenerate the image **only on release** (`!getValueIsAdjusting()`) so dragging
stays responsive on large volumes.

### Live editing

While the panoramic is open, editing the source polyline (drag / insert / delete a handle)
re-reads its points and regenerates the image after a **150 ms** debounce
(`REFRESH_DEBOUNCE_MS`). Removing the polyline unbinds the axis.

### Generation algorithm (`CurvedMprImageIO.generatePanoramicImage`)

1. `CurveSampler.sample(curvePoints, planeNormal, stepMm, pixelMm)`:
   - **Smooth** the handle points with a Catmull-Rom spline (`samplesPerVoxel = 2.0`, so denser
     segments get more samples).
   - **Resample** at a uniform arc-length step. `stepMm` is converted to voxels via the volume's
     min pixel ratio; non-positive inputs fall back to 1-voxel spacing.
   - **Perpendiculars**: at each sample, `perp = planeNormal × tangent`, normalized, kept
     flip-consistent along the curve, and globally negated so the middle perpendicular points
     *outward* from the curve centroid (correct orientation for a dental arch).
2. Output geometry:
   - `widthPx = number of sampled points` — the X axis is **arc-length position along the curve**.
   - `heightPx = round(widthMm / pixelMm)` — the Y axis is the **volume Z direction**, centered on
     the curve's Z level (`zOffsetVoxels = (j - heightPx/2) / voxelRatio.z` corrects for
     anisotropic Z spacing).
3. Each pixel is a **single interpolated sample** taken on the curve at
   `(curvePoint.x, curvePoint.y, sampleZ)` via `volume.getInterpolatedValueFromSource(...)` — a
   thin curved reformat, so pixel values match the source exactly (no slab, no MIP). Out-of-range
   samples return `null` and are skipped (the pixel keeps its default 0).

> Only **Height** and **Step** are user-adjustable; there is no slab-thickness parameter. The
> per-sample perpendiculars from `CurveSampler` are used by the cross-sections and the debug
> overlay, but the panoramic itself samples a thin slice directly on the curve.

### Pixel spacing / measurements

The panoramic reader **intentionally does not write `PixelSpacing`**. The X axis measures
arc length along the curve, which is not Euclidean distance between distant points, so calibrated
mm measurements would be misleading. Measurements on the panoramic therefore report in **pixels**.

## Cross-sectional series

Triggered by **Build cross-sectional slices**. `CrossSectionDialog.prompt` collects a
`CrossSectionParams(widthMm, heightMm, spacingMm)`:

| Parameter | Meaning | Default | Dialog range / step |
|-----------|---------|---------|---------------------|
| `widthMm` | slab extent along the perpendicular | `40` | `[1, 200]` step `1` |
| `heightMm` | slab extent along Z | full volume Z extent (`sizeZ × pixelRatio.z`) | `[1, 1000]` step `1` |
| `spacingMm` | distance between consecutive cuts | `1` | `[pixelMm, 50]` step `pixelMm` |

The unit label tracks the source image's pixel-spacing unit (mm when calibrated, pix otherwise).

`CurvedMprBuilder.openCrossSectionSeries` samples the curve at exactly `spacingMm` (one sample =
one cut), creates one `CrossSectionImageIO` per sample, assembles them into a real `DicomSeries`,
registers it **under the source study** in the `DicomModel`, and opens it in a **new viewer tab**
(`MipView.openSeries`). The study is looked up from the original acquisition series carried by the
volume's stack, because the `MprView`'s own series is internally derived and has no study parent.

Each `CrossSectionImageIO` slab spans the in-plane perpendicular (output X) and Z (output Y),
centered on its curve sample. Unlike the panoramic, cross-sections **do write
`PixelSpacing = {pixelMm, pixelMm}`** and use a single sample per pixel (no MIP), so they are
spatially calibrated.

## DICOM identity

`CurvedMprBuilder` copies a fixed set of source tags (`BASE_TAG_IDS`: patient/study identity plus
photometric and VOI/window tags) onto every derived reader so the panoramic and cross-sections
inherit the same patient, study and display characteristics as the source image. The panoramic
carries a minimal `Attributes` (`buildBaseAttributes`); cross-sections clone the full source
header so they can drop into the explorer as a genuine series, with fresh per-instance
SOP/Instance UIDs and the new `SeriesInstanceUID`.

## Debug overlay

Launch with `-Dweasis.cmpr.debug=true` to enable `CurvedMprAxis.DEBUG_DRAW`. When the axial source
view is on the curve's slice, `drawDebug` overlays the original handles (red), the smoothed curve
(green), the per-sample perpendicular slab extents (cyan) and a summary line.

## Limitations

- **Plane assumption**: the panoramic generator assumes the curve lies in the axial XY plane at a
  fixed Z and samples height along world Z. Curves drawn on coronal / sagittal / oblique planes are
  not handled correctly by the current generator.
- **Thin sample**: each panoramic pixel is a single on-curve sample, not a thick-slab MIP, so
  structures off the curve plane are not projected in.
- **Minimum 2 points**; very tight or self-intersecting curves may distort the result.
- **Out-of-volume samples** are skipped (left at 0), not filled with a background value.
- **Panoramic is uncalibrated**: measurements are in pixels (see above).
