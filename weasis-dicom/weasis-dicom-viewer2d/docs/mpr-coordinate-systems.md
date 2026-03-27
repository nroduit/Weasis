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
      │  (conversion only in getRealVolumeTransformation)
      ▼
Isotropic Slice Space       [0, sliceSize]²      ← AxesControl.center, crosshair, mouse
      │
      │  view transform (pan, zoom)
      ▼
Screen / Viewport Space
```

---

## 2. Space Definitions

### 2.1 Voxel Index Space

| Property | Value |
|----------|-------|
| **Range** | `[0, size.x) × [0, size.y) × [0, size.z)` |
| **Units** | integer voxel indices |
| **Defined by** | `Volume.size` (`Vector3i`) |

The raw 3D voxel grid. Physical spacing per voxel is `pixelRatio` (mm):

```
pixelRatio = (rowSpacing, columnSpacing, sliceSpacing)
```

For typical axial CT: `pixelRatio.x ≈ pixelRatio.y`, but `pixelRatio.z` differs
→ the volume is **anisotropic**.

**Key methods:**
- `Volume.getSize()` → `Vector3i`
- `Volume.getPixelRatio()` → `Vector3d`
- `Volume.getValue(x, y, z, channel)` — direct voxel access

### 2.2 VR-Space (Voxel-Ratio-Scaled Space)

| Property | Value |
|----------|-------|
| **Range** | `[0, volSize.x) × [0, volSize.y) × [0, volSize.z)` |
| **Units** | "isotropised" voxel units (smallest physical spacing maps to 1.0) |
| **Derived from** | `volSize = Vector3d(size) × voxelRatio` |

Compensates for anisotropic spacing so that equal distances in VR-space correspond
to equal physical distances.

```java
voxelRatio = pixelRatio / min(pixelRatio)
// smallest spacing → 1.0, others > 1.0

volSize = Vector3d(size) × voxelRatio
// e.g. size=(256,256,128), pixelRatio=(0.5, 0.5, 2.0)
//   → minRatio=0.5, voxelRatio=(1, 1, 4), volSize=(256, 256, 512)
```

`getRealVolumeTransformation()` maps slice pixels into this space.
`interpolateVolume()` then divides by `voxelRatio` to reach Voxel Index Space:

```java
double xIndex = point.x / voxelRatio.x;   // VR → Voxel Index
```

**Key relationship:**
```
sliceSize = ceil( length(volSize) )    // the 3D diagonal of VR-space
```

**Key methods:**
- `Volume.getVoxelRatio()` → `Vector3d`
- `Volume.getSliceSize()` → `int`

### 2.3 Isotropic Slice Space (= crosshair space = working space)

| Property | Value |
|----------|-------|
| **Range** | `[0, sliceSize] × [0, sliceSize]` (2D display); `[0, sliceSize]³` conceptually |
| **Units** | slice image pixels |
| **Pixel spacing** | `min(pixelRatio)` mm in all directions |
| **Center** | `(halfSlice, halfSlice, halfSlice)` where `halfSlice = sliceSize / 2.0` |

This is where everything user-facing happens:

- **`AxesControl.center`** — the crosshair position
- **Crosshair intersection** — drawn at `getCenterForCanvas(view)`
- **Mouse coordinates** — from `getImageCoordinatesFromMouse()`
- **Control points** — rotation handles, MIP extension handles
- **`recenter()`** — pans the view so the crosshair is visible

The slice image is always a square of `sliceSize × sliceSize` pixels.
Each pixel represents `min(pixelRatio)` mm uniformly.

**Key methods:**
- `AxesControl.getCenter()` → `Vector3d` (in slice pixel coords)
- `AxesControl.setCenter(Vector3d)` — stores directly in slice pixel coords
- `AxesControl.getCenterForCanvas(SliceCanvas)` — projects center to a view's 2D plane
- `MprController.getCrossHairPosition(MprAxis)` — center projected to the view's canvas
- `MprController.getCrossHairPosition()` — raw volume center (for rendering)

---

## 3. Conversion between Slice Space and VR-Space

This conversion is only needed in **one place**: `MprAxis.getRealVolumeTransformation()`,
which builds the matrix that maps slice pixels → VR-space for volume sampling.

### 3.1 Slice → VR (per-component)

```
vrPosition.i = slicePosition.i × (volSize.i / sliceSize)
```

This is anisotropic because `volSize.x`, `volSize.y`, `volSize.z` are generally different.

### 3.2 VR → Slice (per-component)

```
slicePosition.i = vrPosition.i × (sliceSize / volSize.i)
```

### 3.3 Why the crosshair offset needs this conversion

In `getRealVolumeTransformation()`, the perpendicular offset determines how far from
the VR-space center the slice plane sits. The crosshair offset is in slice-space, so
it must be converted to VR-space before computing the dot product with the rotated normal:

```java
Vector3d crossHairOffset = crossHair − volCenter;   // in slice-space
// Convert to VR-space:
Vector3d offsetVR = new Vector3d(
    crossHairOffset.x * volSize.x / sliceSize,
    crossHairOffset.y * volSize.y / sliceSize,
    crossHairOffset.z * volSize.z / sliceSize
);
double perpendicularOffset = offsetVR.dot(sliceNormal);
```

---

## 4. Data Flow Diagrams

### 4.1 Rendering a slice pixel

```
Slice pixel (px, py, 0)
        │
        │  getRealVolumeTransformation() matrix
        │  (rotation around VR-center, plane orientation, perpendicular offset)
        │  NOTE: crosshair offset converted from slice-space to VR-space internally
        ▼
VR-Space (vr_x, vr_y, vr_z)
        │
        │  ÷ voxelRatio  (in interpolateVolume)
        ▼
Voxel Index (ix, iy, iz)
        │
        │  trilinear interpolation
        ▼
Pixel intensity value
```

### 4.2 Mouse click → update crosshair

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

### 4.3 Drawing crosshair lines

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
