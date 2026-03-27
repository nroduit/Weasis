# MPR Transformations: Slice ↔ Volume

This document describes the key transformations that connect the isotropic slice image
to the anisotropic volume data, how the crosshair position drives them, and
how the forward/inverse rotation chain must stay consistent.

See [mpr-architecture.md](mpr-architecture.md) for the full system overview.

---

## 1. `getRealVolumeTransformation()` — Slice Pixels → VR-Space

**Location:** `MprAxis.getRealVolumeTransformation(Quaterniond rotation, Vector3d volumeCenter)`

**Purpose:** Builds a `Matrix4d` that maps every pixel `(px, py, 0)` of the square
slice image into VR-Space coordinates `(vr_x, vr_y, vr_z)`, from which voxel values
are sampled.

### Construction (AXIAL example)

```java
// Inputs
int sliceImageSize = volume.getSliceSize();            // diagonal of volSize
Vector3d volSize   = Vector3d(size).mul(voxelRatio);   // anisotropic extents
Vector3d center    = volSize × 0.5;                    // VR-space center
double halfSlice   = sliceImageSize / 2.0;             // isotropic half-extent
Vector3d crossHair = getCrossHairPosition();           // volume center in slice-space
Vector3d volCenter = (halfSlice, halfSlice, halfSlice);
Vector3d crossHairOffset = crossHair − volCenter;      // offset in slice-space

// Convert offset to VR-space (anisotropic conversion)
Vector3d crossHairOffsetVR = crossHairOffset × (volSize / sliceSize);

// Perpendicular offset: project VR-space offset onto the rotated normal
Vector3d sliceNormal = rotation.transform(new Vector3d(0, 0, 1));
double perpendicularOffset = crossHairOffsetVR.dot(sliceNormal);

// Build matrix (read right-to-left):
matrix = translate(center)                              // 3. Move to VR-space center
       × rotate(r)                                      // 2. Apply MPR rotation
       × translate(-halfSlice, -halfSlice, perpOffset)  // 1. Center the slice + depth
```

### What each step does

| Step | Operation | Effect |
|------|-----------|-------|
| 1 | `translate(-halfSlice, -halfSlice, perpOffset)` | Shifts slice origin so pixel `(halfSlice, halfSlice)` → `(0, 0, perpOffset)` |
| 2 | `rotate(r)` | Applies the view rotation (global rotation + per-plane offset) |
| 3 | `translate(center)` | Moves result to VR-space volume center |

### Plane-specific variations

| Plane | Base rotation after `rotate(r)` | Slice normal |
|---|---|---|
| AXIAL | _(none)_ | (0, 0, 1) |
| CORONAL | `rotateX(-90°)` then `scale(1, -1, 1)` | (0, 1, 0) |
| SAGITTAL | `rotateY(90°)` then `rotateZ(90°)` | (1, 0, 0) |

### Important: `getCrossHairPosition()` must return volume center

`getRealVolumeTransformation()` receives `volumeCenter` from `getCrossHairPosition()`.
This **must** be the raw volume-space center (from `axesControl.getCenter()`),
**not** a canvas projection. The crosshair offset is computed as
`crossHair − volCenter`, so if `crossHair` were a canvas projection, the offset
would be wrong and the slice would be rendered at the wrong depth.

---

## 2. `getDisplayPointToTexturePointMatrix()` — Canvas Coords → Volume Coords

**Location:** `MprView.getDisplayPointToTexturePointMatrix()`

**Purpose:** Maps 2D canvas coordinates to 3D volume coordinates.
Used by `setNewCenter()` to convert a mouse position to the new crosshair position
in volume space.

### Construction

```java
Quaterniond r = mprController.getRotation(plane);  // = getViewRotation(plane)
Vector3d center = axes.getCenter();                // crosshair in slice-space
double halfSlice = sliceSize / 2.0;
Vector3d crossHairOffset = center − (halfSlice, halfSlice, halfSlice);

// Perpendicular offset along the rotated normal
Vector3d sliceNormal = r.transform(planeNormal);
double perpOffset = crossHairOffset.dot(sliceNormal);

// AXIAL example:
matrix = T(halfSlice) · R(r) · T(-halfSlice, -halfSlice, perpOffset)

// CORONAL:
matrix = T(halfSlice) · R(r) · Rx(-90°) · S(1,-1,1) · T(-halfSlice, -halfSlice, perpOffset)

// SAGITTAL:
matrix = T(halfSlice) · R(r) · Ry(90°) · Rz(90°) · T(-halfSlice, -halfSlice, perpOffset)
```

This is the same rotation chain as `getRealVolumeTransformation()` but operates
in isotropic slice-space (not VR-space).

---

## 3. `getCenterForCanvas()` — Volume → Canvas Projection

**Location:** `AxesControl.getCenterForCanvas(SliceCanvas, Vector3d)`

**Purpose:** Projects the 3D crosshair position onto a specific view's 2D canvas
coordinate system. This is the **inverse** of the forward transform's rotation chain.

### Forward rotation chain (per plane)

| Plane | Forward (canvas → volume) |
|---|---|
| AXIAL | `R(viewRot)` |
| CORONAL | `R(viewRot) · Rx(-90°) · S(1,-1,1)` |
| SAGITTAL | `R(viewRot) · Ry(90°) · Rz(90°)` |

### Inverse rotation chain (volume → canvas)

| Plane | Inverse |
|---|---|
| AXIAL | `R(viewRot)⁻¹` |
| CORONAL | `S(1,-1,1) · Rx(90°) · R(viewRot)⁻¹` |
| SAGITTAL | `Rz(-90°) · Ry(-90°) · R(viewRot)⁻¹` |

### Implementation

```java
private void applyRotationMatrix(Vector3d vector, SliceCanvas canvas) {
    Plane plane = canvas.getPlane();

    // Step 1: undo view rotation (global rotation + per-plane offset)
    Quaterniond viewRotation = getViewRotation(plane);
    new Matrix3d().set(viewRotation).invert().transform(vector);

    // Step 2: undo base plane rotation
    Quaterniond planeRotation = getRotationForSlice(plane);
    new Matrix3d().set(planeRotation).invert().transform(vector);

    // Step 3: undo coronal Y-flip
    if (plane == Plane.CORONAL) {
        vector.y = -vector.y;
    }
}
```

### Full `getCenterForCanvas()` flow

```java
Vector3d adjustedCenter = pt − (halfSlice, halfSlice, halfSlice);  // center around origin
applyRotationMatrix(adjustedCenter, canvas);                       // inverse rotation
adjustedCenter += (halfSlice, halfSlice, halfSlice);               // restore offset
// Result: (x, y) are the canvas coordinates, z is the depth (unused for 2D)
```

### Critical invariant

The **forward** transform (`getDisplayPointToTexturePointMatrix`) and the
**inverse** (`getCenterForCanvas` via `applyRotationMatrix`) must use the
**same rotation**: `getViewRotation(plane)`. If one is changed, the other
must be updated to match. Failing to include the view rotation in the inverse
causes the crosshair to drift when planes are tilted.

---

## 4. Rotation Components

### `getViewRotation(plane)` — the full per-view rotation

```java
Quaterniond all = getGlobalRotation();           // accumulated user tilts
double offset = -getRotationOffset(plane);       // cancellation for this plane
return switch (plane) {
    case AXIAL    -> all.rotateZ(offset);
    case CORONAL  -> all.rotateY(-offset);
    case SAGITTAL -> all.rotateX(offset);
};
```

When the user tilts the crosshair on view V:
- `globalRotation` changes (all views affected)
- `rotationOffset[V]` is set to cancel the effect on V
- V's `viewRot` becomes identity; other views' `viewRot` becomes non-identity

### `getRotationForSlice(plane)` — base plane orientation

Fixed rotations that orient each plane relative to the volume:

| Plane | Quaternion |
|---|---|
| AXIAL | Identity |
| CORONAL | Rx(-90°) |
| SAGITTAL | Ry(90°) · Rz(90°) |

These never change. They map the 2D canvas axes (u, v) to the 3D volume axes
for each anatomical plane.

---

## 5. `getCenterAlongAxis()` / `setCenterAlongAxis()` — Scroll Position

Used by `MprAxis.getSliceIndex()` and `setSliceIndex()` for scrolling.

```java
// Get depth of center along a view's rotated normal:
double halfSlice = getSliceSize() / 2.0;
Vector3d axis = getRotatedCanvasAxis(plane);      // globalRotation × canvasAxis
return center.sub(halfSlice, halfSlice, halfSlice).dot(axis) + halfSlice;

// Set center depth along a view's rotated normal:
double currentDepth = center.sub(halfSlice, ...).dot(axis);
double delta = (value - halfSlice) - currentDepth;
center.add(axis × delta);
```

`getRotatedCanvasAxis()` correctly uses `globalRotation` to rotate the base
canvas axis (Z for AXIAL, Y for CORONAL, -X for SAGITTAL).

---

## 6. `setNewCenter()` — Mouse Position → Volume Center

```java
protected void setNewCenter(MprView view, Vector3d newCenter) {
    Vector3d vCenter = view.getVolumeCoordinates(newCenter, false);
    axesControl.setCenter(vCenter);
}
```

1. `newCenter = (pt.x, pt.y, 0)` — mouse position in canvas space
2. `getVolumeCoordinates()` applies `getDisplayPointToTexturePointMatrix()` — forward transform
3. Result is in isotropic slice-space — stored directly as the new center

The perpendicular offset in the matrix ensures the correct depth (z) is computed
from the current center's position relative to the slice plane.

---

## 7. Round-Trip Consistency

The round-trip **mouse click → store center → render slice → crosshair position**
must be consistent.

### Forward: click at pixel `(px, py)` in AXIAL view

```
input:   (px, py, 0)                                  // canvas coords
matrix:  T(h) · R(viewRot) · T(-h, -h, perpOffset)    // forward transform
output:  new center in slice-space                     // stored by setCenter()
```

### Inverse: render slice, where does crosshair appear?

```
getCenterForCanvas(AXIAL):
  adjusted = center − (h, h, h)
  R(viewRot)⁻¹ · adjusted                             // inverse rotation
  result + (h, h, h)
  → (cx, cy) matches the pixel where the user clicked  ✓
```

### Rendering verification:

```
getRealVolumeTransformation():
  crossHairOffset = center − (h, h, h)
  offsetVR = crossHairOffset × (volSize / sliceSize)   // slice→VR conversion
  perpOffset = offsetVR.dot(rotatedNormal)
  matrix maps pixel (h, h) → VR-center + perpOffset along normal
  → correct anatomical position  ✓
```
