# MPR Architecture Guide

This document describes the Multi-Planar Reconstruction (MPR) system in Weasis.
It is intended as a reference for developers making future modifications.

---

## 1. Overview

The MPR system reconstructs three orthogonal slice views (Axial, Coronal, Sagittal)
from a 3D volume and lets the user:

- **Move** the crosshair to navigate through the volume.
- **Rotate** (tilt) the crosshair to view oblique slices.
- **Scroll** along the slice normal to step through slices.
- **Adjust MIP thickness** for Maximum Intensity Projection.

```
┌────────────────────────────────────────────────────────┐
│                     MprContainer                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  MprView     │  │  MprView     │  │  MprView     │  │
│  │  (AXIAL)     │  │  (CORONAL)   │  │  (SAGITTAL)  │  │
│  │              │  │              │  │              │  │
│  │  MprAxis ◄───┼──┼── MprAxis ◄──┼──┼── MprAxis    │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                 │          │
│         └────────┬────────┴────────┬────────┘          │
│                  │                 │                   │
│           MprController       AxesControl              │
│            (mouse input)    (center, rotation)         │
│                  │                 │                   │
│                  └────────┬────────┘                   │
│                           │                            │
│                      Volume<?,?>                       │
│                     (voxel data)                       │
└────────────────────────────────────────────────────────┘
```

---

## 2. Key Classes

### `MprView` (extends `View2d`, implements `SliceCanvas`)

The Swing panel that displays one slice. Each instance is assigned a `Plane`
(AXIAL, CORONAL, or SAGITTAL). Responsibilities:

| Responsibility | Key Methods |
|---|---|
| Display the reconstructed slice image | `setImage()`, `repaint()` |
| Convert screen coords → model coords | `getPlaneCoordinatesFromMouse()`, `getImageCoordinatesFromMouse()` |
| Convert model coords → volume coords | `getVolumeCoordinates()`, `getDisplayPointToTexturePointMatrix()` |
| Draw crosshair lines & control points | `addCrossline()`, `processImage()`, `addCrosshairLine()` |
| Compute recentering | `computeCrosslines()` → `mprController.recenter()` |

### `MprController` (implements `MouseListener`, `MouseMotionListener`, `MouseWheelListener`)

Central controller shared by all three views. Handles all crosshair mouse interactions.

| Responsibility | Key Methods |
|---|---|
| Detect which crosshair line the mouse is near | `mouseMoved()`, `paintCrossline()`, `processLine()` |
| Move the crosshair center | `mousePressed()`, `mouseDragged()` → `updatePosition()` |
| Move a single crosshair line | `updateSelectedPosition()` |
| Rotate the crosshair | `updateSelectedRotation()` |
| Adjust MIP thickness | `updateSelectedMIP()` |
| Compute crosshair line endpoints | `getLinePoints()`, `getPlaneDirection()` |
| Convert canvas position to volume center | `setNewCenter()` |
| Recenter other views after a change | `center()`, `recenter()`, `centerAll()` |

### `AxesControl`

Stores the global state of the crosshair system:

| Field | Type | Description |
|---|---|---|
| `center` | `Vector3d` | The 3D crosshair position in **isotropic slice-space** `[0, sliceSize]³` |
| `globalRotation` | `Quaterniond` | The accumulated rotation from crosshair tilting |
| `canvasRotationOffset` | `EnumMap<Plane, Double>` | Per-plane rotation offsets (see §5) |

Key methods:

| Method | Purpose |
|---|---|
| `getCenter()` | Returns the volume-space crosshair position (a copy) |
| `setCenter(Vector3d)` | Stores a new crosshair position |
| `getCenterForCanvas(SliceCanvas)` | Projects the 3D center onto a specific view's 2D canvas |
| `getCenterAlongAxis(SliceCanvas)` | Returns the depth along a view's normal (for scrolling) |
| `setCenterAlongAxis(SliceCanvas, double)` | Sets the depth along a view's normal |
| `getViewRotation(Plane)` | Returns the full rotation for a given plane (global + offset) |
| `rotateAroundAxis(Plane, double)` | Rotates the crosshair on a specific view |
| `getRotationForSlice(Plane)` | Returns the base plane rotation (identity / Rx(-90°) / Ry(90°)·Rz(90°)) |

### `MprAxis`

Links a `Plane` to its `MprView`, `VolImageIO`, image element, and transformation matrix.

| Responsibility | Key Methods |
|---|---|
| Build the slice→volume transform | `getRealVolumeTransformation()` |
| Trigger slice re-rendering | `updateImage()`, `updateRotation()` |
| Track slice index for scrolling | `getSliceIndex()`, `setSliceIndex()` |
| Store MIP thickness | `getThicknessExtension()`, `setThicknessExtension()` |
| Provide anatomical axis metadata | `getAxisDirection()`, `getAxisDColor()` |

### `AxisDirection`

Defines the anatomical axis directions and colors for each plane. The vectors
`axisX`, `axisY`, `axisZ` are **anatomical orientation arrows** used only for
the on-screen overlay (`drawAxes`); they are **not** the volume coordinate axes.

| Plane | axisX | axisY | axisZ (arrow) | `invertedDirection` |
|---|---|---|---|---|
| AXIAL | (1,0,0) R→L | (0,1,0) A→P | (0,0,-1) S→I | `true` |
| CORONAL | (1,0,0) R→L | (0,0,-1) S→I | (0,1,0) A→P | `false` |
| SAGITTAL | (0,1,0) A→P | (0,0,-1) S→I | (1,0,0) R→L | `false` |

#### What `invertedDirection` means

`invertedDirection` is used only in `MprAxis.getSliceIndex()` /
`setSliceIndex()` to translate between the **internal volume-space depth
along the plane normal** and the **DICOM instance number**:

```java
// getSliceIndex():
if (axisDirection.isInvertedDirection()) {
    sliceIndex = sliceSize - index;   // flip
} else {
    sliceIndex = index;               // direct
}
```

For AXIAL, `getCenterAlongAxis()` returns a value that **increases as the
crosshair moves Superior** (+Z direction). But DICOM axial series are
conventionally numbered from top (Superior, instance 1) to bottom (Inferior,
instance N) — the opposite direction. Hence `invertedDirection = true` flips
the depth to match that convention.

CORONAL and SAGITTAL increase in the same direction as their DICOM instance
ordering, so they do not need the flip.

#### Coordinate system: DICOM LPS, implemented with JOML

The 3D volume space used throughout the MPR system is **DICOM LPS** —
[JOML](https://github.com/JOML-CI/JOML) is only the math library:

| Axis | Anatomical direction | LPS |
|---|---|---|
| +X | Patient's Left | **L** |
| +Y | Patient's Posterior | **P** |
| +Z | Patient's Superior (head) | **S** |

This can be verified from the `AxisDirection` vectors: `axisX = (1,0,0)`
(R→L) ≡ +X = Left; `axisY = (0,1,0)` (A→P) ≡ +Y = Posterior; `axisZ =
(0,0,-1)` is an arrow pointing **Inferior** (−Z), confirming +Z = Superior.

### `Volume<?,?>`

The 3D voxel data. Key properties:

| Property | Method | Description |
|---|---|---|
| Size | `getSize()` → `Vector3i` | Voxel grid dimensions |
| Pixel ratio | `getPixelRatio()` → `Vector3d` | Physical spacing per voxel (mm) |
| Voxel ratio | `getVoxelRatio()` → `Vector3d` | Normalized spacing (min = 1.0) |
| Slice size | `getSliceSize()` → `int` | `ceil(length(size × voxelRatio))` — the 3D diagonal |

### `Plane` (enum in `MprView`)

```java
AXIAL(2),    // Normal along Z axis — direction (0, 0, 1)
CORONAL(1),  // Normal along Y axis — direction (0, 1, 0)
SAGITTAL(0)  // Normal along X axis — direction (1, 0, 0)
```

---

## 3. Coordinate Spaces

```
Voxel Index Space           [0, size.i)         ← raw voxel grid
      │
      │  × voxelRatio (per-component)
      ▼
VR-Space                    [0, volSize.i)      ← getRealVolumeTransformation output
      │
      │  (conversion only in getRealVolumeTransformation)
      ▼
Isotropic Slice Space       [0, sliceSize]²     ← AxesControl.center, crosshair, mouse
      │
      │  view transform (pan, zoom)
      ▼
Screen / Viewport Space
```

### Isotropic Slice Space (the main working space)

Almost all crosshair logic operates in this space. It is a **uniform cubic space**
of `sliceSize × sliceSize × sliceSize` where each unit = `min(pixelSpacing)` mm.

- `AxesControl.center` is stored here
- Mouse/plane coordinates are in this space
- Crosshair line endpoints are in this space
- The volume center is at `(halfSlice, halfSlice, halfSlice)`

### VR-Space

Only used inside `MprAxis.getRealVolumeTransformation()` for the actual volume sampling.
The crosshair offset is explicitly converted from slice-space to VR-space there.

---

## 4. Rotation System

The rotation system has three layers:

### 4.1 Base Plane Rotations (`getRotationForSlice()`)

These are **fixed** rotations that orient each plane relative to the volume axes:

| Plane | Rotation | Effect |
|---|---|---|
| AXIAL | Identity | Canvas XY = Volume XY |
| CORONAL | Rx(-90°) | Canvas XY maps to Volume XZ |
| SAGITTAL | Ry(90°) · Rz(90°) | Canvas XY maps to Volume YZ |

The coronal plane also applies `S(1, -1, 1)` (Y-flip) for correct anatomical orientation.

### 4.2 Global Rotation (`globalRotation`)

When the user **tilts the crosshair** on any view, the global rotation accumulates.
For example, tilting the crosshair 45° on the AXIAL view adds `Rz(angle)` to
`globalRotation` (rotation around the AXIAL normal — the Z-axis).

```java
// In rotateAroundAxis():
Vector3d axis = plane.getDirection();          // (0,0,1) for AXIAL
Quaterniond q = Quaterniond().fromAxisAngleRad(axis, planeAngle);
globalRotation.mul(q);
```

### 4.3 Per-Plane Rotation Offset (`canvasRotationOffset`)

When the crosshair is tilted on view V, the global rotation changes for ALL views.
But view V itself should appear unchanged (the crosshair rotates around the
view's normal, which doesn't change that view's slice plane). The per-plane offset
**cancels the global rotation for the tilting view** so only the other views are affected.

```java
// getViewRotation() combines global rotation with the per-plane offset:
public Quaterniond getViewRotation(Plane plane) {
    Quaterniond all = getGlobalRotation();     // copy
    double offset = -getRotationOffset(plane);
    return switch (plane) {
        case AXIAL    -> all.rotateZ(offset);    // cancels Z-rotation component
        case CORONAL  -> all.rotateY(-offset);   // cancels Y-rotation component
        case SAGITTAL -> all.rotateX(offset);    // cancels X-rotation component
    };
}
```

**Example:** Tilt 45° on the AXIAL view:
- `globalRotation = Rz(45°)`
- `rotationOffset[AXIAL] = 45°`
- `getViewRotation(AXIAL)` = `Rz(45°) · Rz(-45°)` = **identity** (AXIAL unchanged)
- `getViewRotation(CORONAL)` = `Rz(45°)` (CORONAL sees the tilt)
- `getViewRotation(SAGITTAL)` = `Rz(45°)` (SAGITTAL sees the tilt)

### 4.4 Full Forward Transform (Display → Texture)

Built in `getDisplayPointToTexturePointMatrix()` and `getRealVolumeTransformation()`:

```
M = T(halfSlice) · R(viewRot) · [basePlaneRot] · T(-halfSlice, -halfSlice, perpOffset)
```

where `viewRot = getViewRotation(plane)` and `basePlaneRot` depends on the plane:

| Plane | Full Forward Rotation |
|---|---|
| AXIAL | `R(viewRot)` |
| CORONAL | `R(viewRot) · Rx(-90°) · S(1,-1,1)` |
| SAGITTAL | `R(viewRot) · Ry(90°) · Rz(90°)` |

---

## 5. The Two "getCrossHairPosition" Methods

This is a critical design point. There are **two distinct needs**:

### `getCrossHairPosition()` — Volume Center (no args)

Returns the **raw volume-space center** directly from `axesControl.getCenter()`.
Used by the **rendering pipeline** which needs the true 3D position:

- `MprAxis.updateRotation()` — builds the slice transform
- `MprAxis.getRealVolumeTransformation()` — fallback when no center provided
- `VolImageIO.getImageFragment()` — renders the slice image

**Must NOT apply any canvas projection**, because the rendering code interprets
this as a 3D volume position.

### `getCrossHairPosition(MprAxis axis)` — Canvas Projection

Returns the center **projected onto a specific view's 2D canvas** via
`getCenterForCanvas(axis.getMprView())`. Used for **display and interaction**:

- `mouseMoved()` — drawing crosshair lines, hit-testing
- `addCrossline()` — positioning crosshair graphics
- `mousePressed()` — checking distance to center
- `mouseDragged()` — computing new center position

**Must apply the full inverse rotation** to correctly project the 3D center
onto each view's 2D canvas.

### Why the distinction matters

Before the rotation fix, `getCenterForCanvas(AXIAL)` happened to return the
raw volume center because the AXIAL base rotation is identity and no view
rotation was applied. This made `getCrossHairPosition()` (which always routed
through the AXIAL view) accidentally correct.

After fixing `getCenterForCanvas` to apply the full `viewRotation⁻¹`, the no-arg
version broke when tilting on non-axial views (because the AXIAL `viewRot` became
non-identity). The fix was to make `getCrossHairPosition()` return
`axesControl.getCenter()` directly.

---

## 6. `getCenterForCanvas()` — Volume → Canvas Projection

This is the **inverse of the forward transform's rotation chain**.

### Forward (canvas → volume):
```
volumePoint = T(h) · R(viewRot) · basePlaneRot · T(-h) · canvasPoint
```

### Inverse (volume → canvas):
```
canvasPoint = T(h) · basePlaneRot⁻¹ · R(viewRot)⁻¹ · T(-h) · volumePoint
```

Implementation in `applyRotationMatrix()`:

```java
private void applyRotationMatrix(Vector3d vector, SliceCanvas canvas) {
    Plane plane = canvas.getPlane();

    // Step 1: undo the view rotation (global + per-plane offset)
    Quaterniond viewRotation = getViewRotation(plane);
    new Matrix3d().set(viewRotation).invert().transform(vector);

    // Step 2: undo the base plane rotation
    Quaterniond planeRotation = getRotationForSlice(plane);
    new Matrix3d().set(planeRotation).invert().transform(vector);

    // Step 3: undo the coronal Y-flip
    if (plane == Plane.CORONAL) {
        vector.y = -vector.y;
    }
}
```

**Critical:** The full `viewRotation` (not just the base plane rotation) must
be inverted. Without this, the crosshair drifts on tilted planes.

---

## 7. Crosshair Interaction Flow

### 7.1 Moving the Crosshair Center

```
User drags near crosshair center on view V
    │
    ├─ mousePressed(): canMove = true
    │
    ├─ mouseDragged():
    │     pt = view.getPlaneCoordinatesFromMouse(x, y)   ← canvas coords
    │     updatePosition(view, pt, center)
    │         │
    │         ├─ setNewCenter(view, (pt.x, pt.y, 0))
    │         │     vCenter = view.getVolumeCoordinates(...)   ← forward matrix
    │         │     axesControl.setCenter(vCenter)             ← store in volume space
    │         │
    │         ├─ pair.first().updateImage()    ← re-render other views
    │         ├─ pair.second().updateImage()
    │         └─ center(view)                 ← recenter other views
    │               recenter(pair.first(), mode)
    │               recenter(pair.second(), mode)
    │                   p = getCenterForCanvas(view)    ← inverse projection
    │                   view.setCenter(p.x - w/2, p.y - h/2)
    │
    └─ mouseReleased(): canMove = false, centerAll()
```

### 7.2 Moving a Single Crosshair Line

```
User drags near a crosshair line on view V
    │
    ├─ mouseMoved(): selectedAxis = the cross-axis for this line
    │
    ├─ mousePressed(): canMoveSelected = true
    │
    ├─ mouseDragged():
    │     pt = view.getPlaneCoordinatesFromMouse(x, y)
    │     updateSelectedPosition(view, pt, crossHair)
    │         │
    │         ├─ Compute perpendicular distance from mouse to the line
    │         ├─ Move center perpendicular to the line by that distance
    │         └─ updatePosition(view, newCenter, crossHair)  ← same as 7.1
    │
    └─ mouseReleased()
```

### 7.3 Rotating the Crosshair

```
User drags a rotation handle on view V
    │
    ├─ mouseDragged():
    │     updateSelectedRotation(view, current, center)
    │         │
    │         ├─ Compute rotation angle from mouse position
    │         ├─ axesControl.rotateAroundAxis(view.plane, angle)
    │         │     globalRotation.mul(q)           ← update global rotation
    │         │     setRotationOffset(plane, angle)  ← cancel for this view
    │         │
    │         ├─ pair.first().updateImage()
    │         ├─ pair.second().updateImage()
    │         └─ center(view)
    │
    └─ mouseReleased()
```

### 7.4 Scrolling (Changing Slice Depth)

```
MprView.computeCrosslines(location)
    │
    └─ mprController.recenter(axis, mode)
         │
         ├─ p = getCenterForCanvas(view)    ← project center to this canvas
         └─ view.setCenter(p.x - w/2, p.y - h/2)   ← auto-pan
```

---

## 8. Rendering Pipeline

### 8.1 Slice Image Generation

```
MprAxis.updateImage()
    │
    ├─ imageElement.removeImageFromCache()
    ├─ mprView.setImage(imageElement)
    │     │
    │     └─ VolImageIO.getImageFragment()
    │           │
    │           ├─ volumeCenter = controller.getCrossHairPosition()  ← volume space!
    │           ├─ getSlice(volumeCenter)
    │           │     │
    │           │     ├─ transformation = getRealVolumeTransformation(rotation, center)
    │           │     └─ volume.getVolumeSlice(axis, center)
    │           │           │
    │           │           └─ For each pixel (px, py):
    │           │                 vrPoint = transformation × (px, py, 0)
    │           │                 voxel = vrPoint / voxelRatio
    │           │                 value = trilinear_interpolate(voxel)
    │           │
    │           └─ (MIP: render multiple slices, combine with max/min/avg)
    │
    └─ mprView.repaint()
```

### 8.2 `getRealVolumeTransformation()` Matrix

Maps slice image pixels → VR-space for volume sampling:

```java
// Pseudocode (AXIAL example):
sliceNormal = viewRot.transform(0, 0, 1);        // rotated normal
perpOffset  = crossHairOffsetVR.dot(sliceNormal); // depth in VR-space

matrix = T(vrCenter) · R(viewRot) · T(-halfSlice, -halfSlice, perpOffset);
```

**Important:** The crosshair offset must be converted from slice-space to VR-space
before computing the perpendicular offset:

```java
Vector3d crossHairOffsetVR = new Vector3d(
    crossHairOffset.x * volSize.x / sliceImageSize,
    crossHairOffset.y * volSize.y / sliceImageSize,
    crossHairOffset.z * volSize.z / sliceImageSize
);
```

---

## 9. Cross-Axis Pairing

Each view displays crosshair lines for the other two planes:

| View | Horizontal line | Vertical line |
|---|---|---|
| AXIAL | CORONAL (pair.first) | SAGITTAL (pair.second) |
| CORONAL | AXIAL (pair.first) | SAGITTAL (pair.second) |
| SAGITTAL | AXIAL (pair.first) | CORONAL (pair.second) |

Defined by `getCrossAxis()`. The `selectedAxis` tracks which cross-plane's
line the user is interacting with.

---

## 10. Common Pitfalls & Future Guidance

### 10.1 Volume Center vs Canvas Projection

Always be clear about which space a position is in:

- **Volume center** (`axesControl.getCenter()`): the 3D position in isotropic
  slice-space. Use for rendering and volume operations.
- **Canvas projection** (`getCenterForCanvas(view)`): the 2D projection onto a
  specific view. Use for display, hit-testing, and mouse interaction.

If you add new code that needs the crosshair position, ask yourself:
*"Do I need the 3D position or the 2D projection on a specific view?"*

### 10.2 Rotation Consistency

The forward transform (display → texture) and the inverse (texture → display)
must always use **matching** rotations:

- **Forward:** `R(viewRot) · basePlaneRot` (in `getDisplayPointToTexturePointMatrix`)
- **Inverse:** `basePlaneRot⁻¹ · R(viewRot)⁻¹` (in `getCenterForCanvas` / `applyRotationMatrix`)

If you change one, you **must** update the other.

### 10.3 Adding a New Rotation Mode

If adding a new rotation mode or axis constraint:

1. Update `rotateAroundAxis()` for the new rotation logic
2. Make sure `getViewRotation()` correctly cancels the rotation for the
   originating view
3. Verify `applyRotationMatrix()` still inverts the full rotation chain
4. Test with all three planes as the originating view

### 10.4 Anisotropic Volumes

When working with the `perpendicular offset` in `getRealVolumeTransformation()`,
always convert the crosshair offset from slice-space to VR-space first.
The conversion factor is `volSize.i / sliceSize` per component.

### 10.5 The `adjusting` Flag

When `adjusting = true`, MIP slabs with `thicknessExtension > 0` are skipped
during drag (for performance). The full MIP is re-rendered on `mouseReleased`.

---

## 11. File Reference

| File | Role |
|---|---|
| `MprView.java` | Swing panel, coordinate conversion, crosshair drawing |
| `MprController.java` | Mouse event handling, crosshair interaction logic |
| `AxesControl.java` | Crosshair state: center position, rotation, canvas projection |
| `MprAxis.java` | Per-plane axis: transform, image, slice index |
| `AxisDirection.java` | Anatomical axis definitions, colors, orientation arrows |
| `Volume.java` | 3D voxel data, spacing, slice size |
| `VolImageIO.java` | Image I/O bridge, renders slices from volume |
| `MprContainer.java` | Top-level container, creates the three views |
| `ArcBallController.java` | Alternative 3D rotation input mode |
| `SliceCanvas.java` | Interface for canvases that display slices |

