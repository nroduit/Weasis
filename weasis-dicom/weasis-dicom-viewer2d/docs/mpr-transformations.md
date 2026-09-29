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

The matrix is read right to left:

| Step | Operation | Effect |
|------|-----------|-------|
| 1 | `translate(-halfSlice, -halfSlice, perpOffset)` | Centers the slice on the origin and sets its depth: the crosshair offset projected on the rotated slice normal |
| 2 | `rotate(r)` | Applies the view rotation (global rotation + per-plane offset), then the base plane rotation |
| 3 | `translate(center)` | Moves the result to the VR-space volume center |

| Plane | Base rotation after `rotate(r)` | Slice normal |
|---|---|---|
| AXIAL | _(none)_ | (0, 0, 1) |
| CORONAL | `rotateX(-90°)` then `scale(1, -1, 1)` | (0, 1, 0) |
| SAGITTAL | `rotateY(90°)` then `rotateZ(90°)` | (1, 0, 0) |

The `volumeCenter` it receives must be the 3D volume center
(`MprController.getCrossHairPosition()`), **not** a canvas projection; otherwise the slice is
rendered at the wrong depth.

---

## 2. `getDisplayPointToTexturePointMatrix()` — Canvas Coords → Volume Coords

**Location:** `MprView.getDisplayPointToTexturePointMatrix()`

**Purpose:** Maps 2D canvas coordinates to 3D volume coordinates. Used through
`MprView.getVolumeCoordinates()` by `MprController.setNewCenter()` to turn a mouse position
into the new crosshair position.

It uses the same rotation chain as `getRealVolumeTransformation()`, but pivots on the slice
space center `(halfSlice, halfSlice, halfSlice)`, so its result stays in isotropic slice space
(not VR-space).

---

## 3. `getCenterForCanvas()` — Volume → Canvas Projection

**Location:** `AxesControl.getCenterForCanvas(SliceCanvas, Vector3d)`

**Purpose:** Projects the 3D crosshair position onto a specific view's 2D canvas
coordinate system. This is the **inverse** of the forward transform's rotation chain: the
point is centered on the origin, rotated by the inverse chain, then shifted back.

| Plane | Forward (canvas → volume) | Inverse (volume → canvas) |
|---|---|---|
| AXIAL | `R(viewRot)` | `R(viewRot)⁻¹` |
| CORONAL | `R(viewRot) · Rx(-90°) · S(1,-1,1)` | `S(1,-1,1) · Rx(90°) · R(viewRot)⁻¹` |
| SAGITTAL | `R(viewRot) · Ry(90°) · Rz(90°)` | `Rz(-90°) · Ry(-90°) · R(viewRot)⁻¹` |

### Critical invariant

The **forward** transform (`getDisplayPointToTexturePointMatrix`) and the
**inverse** (`getCenterForCanvas`) must use the
**same rotation**: `getViewRotation(plane)`. If one is changed, the other
must be updated to match. Failing to include the view rotation in the inverse
causes the crosshair to drift when planes are tilted.

---

## 4. Rotation Components

- **`AxesControl.getViewRotation(plane)`** — the full per-view rotation: the global rotation
  (accumulated user tilts) combined with the rotation offset of that plane. When the user tilts
  the crosshair on view V, the global rotation changes for all views and the offset of V is set
  to cancel it, so only the other views see the tilt.
- **`AxesControl.getRotationForSlice(plane)`** — the fixed base plane orientation (Identity,
  Rx(-90°), Ry(90°) · Rz(90°) for AXIAL, CORONAL, SAGITTAL). It maps the 2D canvas axes (u, v)
  to the 3D volume axes for each anatomical plane.

---

## 5. Scroll Position

`AxesControl.getCenterAlongAxis()` / `setCenterAlongAxis()` read and set the depth of the
center along a view's normal rotated by the global rotation
(`getRotatedCanvasAxis()`). `MprAxis.getSliceIndex()` / `setSliceIndex()` use them for
scrolling.

---

## 6. Round-Trip Consistency

The round-trip **mouse click → store center → render slice → crosshair position**
must be consistent: a click at a pixel is converted by the forward transform into the new
center, `getCenterForCanvas()` must project that center back onto the same pixel, and
`getRealVolumeTransformation()` must map the slice center pixel to the same anatomical
position.
