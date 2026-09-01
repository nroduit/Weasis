# MPR Architecture Guide

This document describes the Multi-Planar Reconstruction (MPR) system in Weasis: its main
classes, the conventions they share, and how a user interaction becomes a rendered slice.

Code lives in `org.weasis.dicom.viewer2d.mpr` (module `weasis-dicom-viewer2d`).
Related documents:

- [mpr-coordinate-systems.md](mpr-coordinate-systems.md) — definitions of the coordinate spaces
- [mpr-transformations.md](mpr-transformations.md) — the slice ↔ volume transforms

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

| Class | Responsibility |
|---|---|
| `MprContainer` | Top-level viewer; creates the three views |
| `MprView` | Swing panel (extends `View2d`, implements `SliceCanvas`) showing one `Plane`; converts mouse ↔ model coordinates and draws the crosshair |
| `MprController` | Shared by the three views; handles all crosshair mouse interactions (move, move one line, rotate, MIP thickness) and recentering |
| `AxesControl` | Global crosshair state: 3D center, global rotation, per-plane rotation offsets; projects the center onto a view |
| `MprAxis` | Links a `Plane` to its view, `VolImageIO` and image; builds the slice → volume transform; slice index and MIP thickness |
| `AxisDirection` | Anatomical axis directions, colors and orientation arrows of a plane (overlay only) |
| `Volume<?,?>` | 3D voxel data: size, spacing, slice size, sampling |
| `VolImageIO` | Image I/O bridge that renders a slice from the volume |
| `SliceCanvas` | Interface for canvases that display slices |
| `ArcBallController` | Alternative 3D rotation input mode |

`Plane` is an enum in `MprView`: `AXIAL` (normal along Z), `CORONAL` (normal along Y),
`SAGITTAL` (normal along X).

---

## 3. Conventions

### 3.1 Patient coordinate system: DICOM LPS

The 3D volume space is **DICOM LPS**; [JOML](https://github.com/JOML-CI/JOML) is only the
math library:

| Axis | Anatomical direction | LPS |
|---|---|---|
| +X | Patient's Left | **L** |
| +Y | Patient's Posterior | **P** |
| +Z | Patient's Superior (head) | **S** |

The `AxisDirection` vectors are **anatomical orientation arrows** for the on-screen overlay;
they are **not** the volume coordinate axes.

### 3.2 Slice index vs. DICOM instance order

`MprAxis.getSliceIndex()` / `setSliceIndex()` translate between the depth of the crosshair
along the plane normal and the slice index shown to the user. The depth increases toward +Z
(Superior) for AXIAL, while axial series are conventionally numbered from Superior to
Inferior, so `AxisDirection.isInvertedDirection()` is `true` for AXIAL only.

### 3.3 Coordinate spaces

Almost all crosshair logic works in the **isotropic slice space**, a cube of
`sliceSize × sliceSize × sliceSize` where `sliceSize` is the volume diagonal
(`Volume.getSliceSize()`). Volume sampling happens in **VR-space**, then in voxel indices.
See [mpr-coordinate-systems.md](mpr-coordinate-systems.md).

---

## 4. Rotation System

The rotation of a view has three layers, all held by `AxesControl`:

1. **Base plane rotation** (`getRotationForSlice()`) — fixed, orients each plane relative
   to the volume axes:

   | Plane | Rotation | Effect |
   |---|---|---|
   | AXIAL | Identity | Canvas XY = Volume XY |
   | CORONAL | Rx(-90°) | Canvas XY maps to Volume XZ |
   | SAGITTAL | Ry(90°) · Rz(90°) | Canvas XY maps to Volume YZ |

   The coronal plane also applies `S(1, -1, 1)` (Y-flip) for correct anatomical orientation.
2. **Global rotation** — accumulates every tilt of the crosshair (`rotateAroundAxis()`), and
   affects all views.
3. **Per-plane rotation offset** — cancels the global rotation for the view where the tilt was
   made, so that view is unchanged and only the two other views are affected.

`getViewRotation(plane)` combines layers 2 and 3. See
[mpr-transformations.md](mpr-transformations.md) for the full forward and inverse chains.

---

## 5. Volume Center vs. Canvas Projection

There are two ways to read the crosshair position, and code must use the right one:

| Method | Returns | Use for |
|---|---|---|
| `MprController.getCrossHairPosition()` | the 3D center from `AxesControl.getCenter()` | rendering and volume operations (`MprAxis`, `VolImageIO`) |
| `MprController.getCrossHairPosition(MprAxis)` | the center projected onto that view's canvas (`AxesControl.getCenterForCanvas()`) | display, hit-testing, mouse interaction |

The rendering code interprets the position as a 3D volume position, so it must never receive
a canvas projection. When adding code that needs the crosshair, ask: *"Do I need the 3D
position or the 2D projection on a specific view?"*

---

## 6. Data Flow

### 6.1 Interaction

```
Mouse drag on view V (MprController)
    │
    ├─ move center / move one line / rotate / MIP thickness
    │     └─ update AxesControl (center or rotation)
    │
    ├─ re-render the two other views (MprAxis.updateImage())
    └─ recenter the other views so the crosshair stays visible
```

Scrolling changes the depth of the center along the view normal
(`AxesControl.setCenterAlongAxis()`), then recenters.

### 6.2 Rendering

```
MprAxis.updateImage()
    └─ VolImageIO.getImageFragment()
          ├─ volume center = MprController.getCrossHairPosition()
          ├─ MprAxis.getRealVolumeTransformation()   slice pixel → VR-space
          └─ Volume.getVolumeSlice()                 VR-space → voxel, interpolation
                (MIP: several slices combined)
```

---

## 7. Cross-Axis Pairing

Each view displays crosshair lines for the other two planes:

| View | Horizontal line | Vertical line |
|---|---|---|
| AXIAL | CORONAL (pair.first) | SAGITTAL (pair.second) |
| CORONAL | AXIAL (pair.first) | SAGITTAL (pair.second) |
| SAGITTAL | AXIAL (pair.first) | CORONAL (pair.second) |

Defined by `MprController.getCrossAxis()`. The `selectedAxis` tracks which cross-plane's
line the user is interacting with.

---

## 8. Rules for Contributors

- **Rotation consistency.** The forward transform (display → texture,
  `MprView.getDisplayPointToTexturePointMatrix()`) and the inverse (texture → display,
  `AxesControl.getCenterForCanvas()`) must use matching rotations. If you change one, update
  the other; otherwise the crosshair drifts on tilted planes.
- **Adding a rotation mode.** Update `rotateAroundAxis()`, make sure `getViewRotation()`
  still cancels the rotation for the originating view, check that the inverse projection
  still inverts the full rotation chain, and test with each of the three planes as the
  originating view.
- **The `adjusting` flag.** While `MprController.isAdjusting()` is true (during a drag), MIP
  slabs are not rendered; the full MIP is rendered when the interaction ends.
