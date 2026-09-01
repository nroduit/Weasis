# MPR Coordinate Systems

This document defines the coordinate spaces used by the MPR (Multi-Planar Reconstruction)
system in Weasis and the transformations between them.

See [mpr-architecture.md](mpr-architecture.md) for the full system overview.

---

## 1. Pipeline (4 spaces)

```
Voxel Index Space           [0, size.i)
      │
      │  × voxelRatio (per-component)
      ▼
VR-Space                    [0, volSize.i)       ← getRealVolumeTransformation output
      │
      │  rotation around the volume center (getRealVolumeTransformation)
      ▼
Isotropic Slice Space       [0, sliceSize]²      ← AxesControl.center, crosshair, mouse
      │
      │  view transform (pan, zoom)
      ▼
Screen / Viewport Space
```

All 3D positions follow the DICOM LPS convention (see
[mpr-architecture.md](mpr-architecture.md#31-patient-coordinate-system-dicom-lps)).

---

## 2. Space Definitions

### 2.1 Voxel Index Space

| Property | Value |
|----------|-------|
| **Range** | `[0, size.x) × [0, size.y) × [0, size.z)` |
| **Units** | integer voxel indices |
| **Defined by** | `Volume.getSize()` (`Vector3i`) |

The raw 3D voxel grid. Physical spacing per voxel is `Volume.getPixelRatio()` (mm). The
spacing usually differs along the slice axis, so the volume is generally **anisotropic**.

### 2.2 VR-Space (Voxel-Ratio-Scaled Space)

| Property | Value |
|----------|-------|
| **Range** | `[0, volSize.x) × [0, volSize.y) × [0, volSize.z)` |
| **Units** | "isotropised" voxel units (smallest physical spacing maps to 1.0) |
| **Derived from** | `volSize = Vector3d(size) × voxelRatio` |

Compensates for anisotropic spacing so that equal distances in VR-space correspond
to equal physical distances.

```
voxelRatio = pixelRatio / min(pixelRatio)     // Volume.getVoxelRatio()
voxelIndex = vrPosition / voxelRatio          // per component, when sampling
```

### 2.3 Isotropic Slice Space (= crosshair space = working space)

| Property | Value |
|----------|-------|
| **Range** | `[0, sliceSize] × [0, sliceSize]` (2D display); `[0, sliceSize]³` conceptually |
| **Units** | slice image pixels |
| **Pixel spacing** | `min(pixelRatio)` mm in all directions |
| **Center** | `(halfSlice, halfSlice, halfSlice)` where `halfSlice = sliceSize / 2.0` |

```
sliceSize = ceil( length(volSize) )    // Volume.getSliceSize(), the 3D diagonal of VR-space
```

The slice image is always a square of `sliceSize × sliceSize` pixels, large enough to contain
the volume at any rotation. This is where everything user-facing happens:

- **`AxesControl.center`** — the crosshair position (`getCenter()` / `setCenter()`)
- **Crosshair intersection** — drawn at `AxesControl.getCenterForCanvas(view)`
- **Mouse coordinates** — from `MprView.getImageCoordinatesFromMouse()`
- **Control points** — rotation handles, MIP extension handles

---

## 3. Data Flow Diagrams

### 3.1 Rendering a slice pixel

```
Slice pixel (px, py, 0)
        │
        │  MprAxis.getRealVolumeTransformation() matrix
        │  (rotation around VR-center, plane orientation, perpendicular offset)
        ▼
VR-Space (vr_x, vr_y, vr_z)
        │
        │  ÷ voxelRatio
        ▼
Voxel Index (ix, iy, iz)
        │
        │  trilinear interpolation
        ▼
Pixel intensity value
```

### 3.2 Mouse click → update crosshair

```
Mouse (screen_x, screen_y)
        │
        │  getImageCoordinatesFromMouse()
        ▼
Slice pixel (px, py)                           ← Isotropic Slice Space
        │
        │  getDisplayPointToTexturePointMatrix()
        ▼
Volume center in slice-space (vx, vy, vz)
        │
        │  AxesControl.setCenter()
        ▼
Crosshair updated → trigger repaint of all three views
```

### 3.3 Drawing crosshair lines

```
AxesControl.center                             ← Isotropic Slice Space
        │
        │  getCenterForCanvas(view)
        │  (inverse of full rotation chain: viewRot⁻¹ then basePlaneRot⁻¹)
        ▼
(cx, cy) in view's slice image coords
        │
        │  getLinePoints(axis, center, vertical)
        │  (extend line from center along the cross-plane direction)
        ▼
Two endpoints defining the crosshair line
        │
        │  view transform (pan, zoom) → screen coords
        ▼
Drawn on screen
```
