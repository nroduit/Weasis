# Curved MPR (cMPR)

Curved Multi-Planar Reconstruction reformats a 3D volume along a user-drawn path. From a
single polyline it produces two derivatives:

- a **panoramic view** — the volume "straightened" along the curve and shown as one 2D cell
  inside the MPR container (the dental panoramic / OPG use case);
- a **cross-sectional series** — a stack of slabs cut perpendicular to the curve, opened as a
  real DICOM series in a new viewer tab (the dental cross-cuts use case).

The generator targets a curve drawn on the **axial** plane at a fixed Z level (a dental arch):
it samples height along the volume's Z axis.

All code lives in `org.weasis.dicom.viewer2d.mpr.cmpr`. The only callers are `MprView`
(context-menu actions) and `MprContainer` (orchestration). For the surrounding MPR design, see
[mpr-architecture.md](mpr-architecture.md).

## Classes

| Class | Role |
|-------|------|
| `CurvedMprBuilder` | Static factory/orchestrator: builds the panoramic axis and reader, builds and opens the cross-section series, centralizes DICOM tag inheritance. |
| `CurveSampler` | Pure geometry: spline smoothing, arc-length resampling, per-sample in-plane perpendicular directions. Shared by both derivatives. |
| `CurvedMprAxis` | Curve state and parameters (`widthMm`, `stepMm`), live-edit binding to the source polyline, optional debug overlay. |
| `CurvedMprImageIO` | `DcmMediaReader` that generates the panoramic image. MIME `image/cmpr`. |
| `CurvedMprView` | `View2d` subclass: the in-container panoramic cell, with a "Panoramic settings" popup. |
| `CrossSectionParams` | `record(widthMm, heightMm, spacingMm)` with volume-derived defaults. |
| `CrossSectionDialog` | Modal dialog that prompts for the cross-section parameters. |
| `CrossSectionImageIO` | `DcmMediaReader` for one perpendicular slab. MIME `image/cmpr-xs`. |

## Data flow

There is no dedicated drawing tool: the curve is an ordinary `PolylineGraphic` (the standard
measurement polyline). Its context menu starts either derivative.

```
PolylineGraphic (on an MPR plane)
  ├─ Build panoramic view
  │    MprView.openCurvedMprFromPolyline → MprContainer.openCurvedMpr
  │      → CurvedMprBuilder.buildAxis → CurvedMprAxis (bound to the polyline)
  │      → layout mprWithCurved → CurvedMprView ← CurvedMprImageIO
  └─ Build cross-sectional slices
       MprView.openCrossSectionsFromPolyline → CrossSectionDialog
         → MprContainer.openCrossSections → CurvedMprBuilder.openCrossSectionSeries
         → one CrossSectionImageIO per curve sample → DicomSeries in a new tab
```

- **Panoramic view.** Shown in place inside the existing MPR container (a 2×2 layout with the
  three planes). X is the arc-length position along the curve, Y is the volume Z direction.
  Each pixel is an interpolated sample of the source volume taken on the curve. Editing the
  source polyline regenerates the image; removing it unbinds the axis.
- **Cross-sectional series.** The curve is sampled at the requested spacing (one sample = one
  cut). Each slab spans the in-plane perpendicular (X) and Z (Y), centered on its sample. The
  series is registered under the source study in the `DicomModel` and opened in a new viewer tab.

## Conventions

- **Panoramic is uncalibrated.** `CurvedMprImageIO` intentionally does not write
  `PixelSpacing`: its X axis is arc length, not Euclidean distance, so measurements report in
  pixels. Cross-sections are spatially calibrated and do write `PixelSpacing`.
- **DICOM identity.** Every derived reader inherits the source patient, study and display tags
  through `CurvedMprBuilder`. Cross-sections carry a full source header with fresh SOP/Instance
  UIDs and a new `SeriesInstanceUID`, so they behave as a genuine series in the explorer.
- **Debug overlay.** Launch with `-Dweasis.cmpr.debug=true` (`CurvedMprAxis.DEBUG_DRAW`) to draw
  the handles, smoothed curve and perpendicular extents on the axial source view.
