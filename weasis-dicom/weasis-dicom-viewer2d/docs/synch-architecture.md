# Analysis of Synchronization Refactoring

How views are synchronized in the 2D viewer: which classes decide what propagates, how events
travel between views, and the rules contributors must keep when touching this code.

## Overview

Synchronization propagates actions (scroll, pan, zoom, rotation, window/level, …) from one view
to others. Two independent mechanisms coexist:

- **Automatic synchronization** links views whose series share the same DICOM
  `FrameOfReferenceUID`.
- **Manual synchronization** links views that do not share a `FrameOfReferenceUID`, using a slice
  position offset recorded when the user pairs them. It only drives scrolling.

A third, separate case is **TILE** synchronization, scoped to the canvases of a single tiled
viewport.

---

## Architectural Changes

### 1. `SynchManager` Hierarchy

The per-view synchronization setup is computed by a strategy object, not by the event manager
itself.

```
SynchManager<E>                    (abstract, weasis-core)
  ├── DefaultSynchManager<E>       (non-DICOM: no cross-view sync, weasis-core)
  └── DicomSynchManager            (DICOM-aware, weasis-dicom-viewer2d)
```

- **`SynchManager<E>`** – entry point `updateAllListeners(viewerPlugin, synchView)`, plus
  `FrameOfReferenceUID` and orientation helpers and `applyManualSync()`.
- **`DefaultSynchManager<E>`** – only registers the selected view as a `SYNCH` listener so its
  own actions apply.
- **`DicomSynchManager`** – groups views by `FrameOfReferenceUID` (including visible views of
  other `View2dContainer`s with the same group ID in `STACK` mode), handles orientation,
  crosslines, pixel size / presentation state, `MprView` and `MipView` special cases.
- `ImageViewerEventManager.createSynchManager()` is the extension point; the DICOM
  `EventManager` returns a `DicomSynchManager`. `ImageViewerEventManager.updateAllListeners()`
  delegates to it.

### 2. Synchronization Data Model

| Class | Scope | Holds |
|---|---|---|
| `SynchView` | profile (`DEFAULT_STACK`, `DEFAULT_TILE`, …) held by each `ImageViewerPlugin` | name, icon, live `SynchData` and a snapshot restored by `resetSynchData()` |
| `SynchData` | rules | `Mode` (`STACK`, `TILE`), action map, `autoSyncState` and `manualSyncState` (`SynchData.SyncState` ON/OFF), `original` flag |
| `ViewSynchData` | one per view, stored under `ActionW.SYNCH_LINK` | per-view action overrides, `FrameOfReferenceUID`, `canBeAutoSynced` / `canBeManuallySynced`, `ManualSyncData` links |

- `isSynchActivated()` is `true` when **either** the auto or the manual state is ON.
- `original` is `true` while the configuration is the computed default; any user change sets it
  to `false`, and `DicomSynchManager` then keeps that configuration instead of recomputing it.
- `ViewSynchData.ManualSyncData` records `(sourceLocation, targetLocation, targetPane)` for each
  manually linked view.

### 3. UI Components

| Class | Purpose |
|---|---|
| **`SynchViewButton`** | Overlay button on each view. Red (OFF) or green (ON); shows a colour chip identifying the view's `FrameOfReferenceUID` when its container holds several. Click opens a popup: toggle sync for this view, per-action options, *Apply to all views*. |
| **`ManualSynchViewButton`** | Overlay button on each view (hand icon, red/green). Click sets up manual sync, or removes the view from its manual sync group. |
| **`SynchOptionsCheckBoxGroup`** | The per-action checkboxes (scroll, pan, zoom, rotation, flip, window/level, spatial unit) shared by the per-view popup and the toolbar *Synch* drop-down. |

Both overlay buttons are hidden in TILE mode. `DefaultView2d.updateSynchState()` is the single
place that renders them from the view's `ViewSynchData`.

### 4. Event Propagation (`DefaultView2d.propertyChange`)

Views listen to the `ActionW.SYNCH` property of the event manager.

```
source view ──SynchEvent / SynchCineEvent──> ImageViewerEventManager ──> every SYNCH listener
                                                                         └─ checks sender + receiver data
```

- **`SynchEvent`** (zoom, pan, rotation, W/L, …): a view other than the sender applies an action
  only when both views are synchronized and **both** have the action enabled. The sender always
  applies it to itself. Events with no source view (toolbar actions) use the receiver's data only.
  TILE events never cross `ViewportPane` boundaries.
- **`SynchCineEvent`** (scroll, cine): accepted when both views are synchronized, both have
  *Scroll* enabled, and one link holds – same `FrameOfReferenceUID` with auto sync ON, a
  `ManualSyncData` entry with manual sync ON, or the same tiled `ViewportPane`.
- **Manual offset:** for a manual link the slice location is shifted:
  `location = location + targetLocation - sourceLocation`.

---

## Conventions

- Anything that changes a view's `SyncState` must end with `updateSynchState()` on that view,
  usually through `SynchManager.updateAllListeners`.
- Per-view option changes go into `ViewSynchData` overrides and set `original = false`; do not
  write them into the `SynchView` action map, which is shared by the views of a profile.
  Profile-wide defaults are changed with `SynchView.setActionEnabled()`, which also survives
  `resetSynchData()`.
- Manual sync is built on scroll propagation: *Scroll* stays enabled while manual sync is ON.
- Manual sync requires a `SlicePosition` on both images.

## Summary

Synchronization rules live in `SynchView` / `SynchData` / `ViewSynchData`, their computation in
the `SynchManager` hierarchy, and their enforcement in `DefaultView2d.propertyChange`. Main
packages: `org.weasis.core.ui.editor.image` (weasis-core) and `org.weasis.dicom.viewer2d`
(weasis-dicom-viewer2d).
