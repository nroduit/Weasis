# Analysis of mhex's Synchronization Refactoring (branch `4.7-synchro`)

## Overview

This is a large-scale refactoring of the view synchronization system in Weasis, spanning **~30 files** with **+3053 / -1517 lines** across 8 non-merge commits. The work introduces a new architecture separating **automatic synchronization** (based on DICOM FrameOfReferenceUID) from a new **manual synchronization** mechanism, and extracts synchronization logic into dedicated manager classes. The feature is explicitly **work-in-progress**, with several TODO comments and incomplete deactivation logic.

---

## Key Commits (chronological)

| Commit | Summary |
|--------|---------|
| `81b93552a` / `ac9b70914` | WEASIS-660 – Initial refactoring: split sync options into global/local, introduce `SynchViewButton`, `ViewSynchData` |
| `85d82a03b` | Fix double synchro issue |
| `af3354a4a` | Merge synchro-2 (consolidation) |
| `fff699fd3` | **Manual synchronization** – WIP: add `ManualSynchViewButton`, `ManualSyncData`, per-view manual sync setup |
| `5a797a3b4` | Fix synchronization in tile mode |
| `3c0f80e8a` | Handle >2 views in manual sync, fix non-scroll sync; deactivation **not yet implemented** |

---

## Architectural Changes

### 1. New `SynchManager` Hierarchy (extracted from `EventManager`)

Previously, all synchronization logic lived inside `EventManager.updateAllListeners()`. Now:

```
SynchManager<E>                    (abstract, in weasis-core)
  ├── DefaultSynchManager<E>       (trivial default, in weasis-core)
  └── DicomSynchManager            (DICOM-specific, in weasis-dicom-viewer2d)
```

- **`SynchManager<E>`** – abstract base with common logic: orphan view detection, `applyStackMode()`, `applyTileMode()`, `applyManualSync()`, `disableManualSync()`.
- **`DicomSynchManager`** – overrides `updateAllListeners()` with full DICOM-aware logic: FrameOfReferenceUID grouping, crosslines, KO filter handling, orientation checks, MprView/MipView special cases.
- `ImageViewerEventManager.updateAllListeners()` now **delegates** to `synchManager.updateAllListeners()`.
- A factory method `createSynchManager()` allows subclass `EventManager` implementations to provide their own manager (DICOM `EventManager` returns `DicomSynchManager`).

### 2. Dual-State Synchronization Model (`SynchData` + `ViewSynchData`)

**Before:** `SynchData` had a single `Mode` enum (`NONE`, `STACK`, `TILE`) and a boolean `original`.

**After:**
- `Mode.NONE` is **removed**. Only `STACK` and `TILE` remain.
- Two independent state flags are added using `SynchViewButton.State` (ON/OFF):
    - **`autoSyncState`** – automatic sync based on matching FrameOfReferenceUID.
    - **`manualSyncState`** – user-initiated sync between views that don't share a FrameOfReferenceUID.
- `isSynchActivated()` returns `true` if **either** auto or manual sync is ON.
- Constructor now takes a `boolean synch` parameter.

**`ViewSynchData`** extends `SynchData` with per-view data:
- `orphan` – view with unique/missing FrameOfReferenceUID, excluded from auto-sync.
- `canBeManuallySynced` – flag for UI: should the manual sync button be shown.
- `frameOfReferenceUID` – stored per-view for propagation filtering.
- `Set<ManualSyncData> manualSyncDataSet` – pairs of (sourceLocation, targetLocation, targetPane) for manual offset-based synchronization.

### 3. `SynchView.NONE` Removed

- The `SynchView.NONE` constant is deleted entirely.
- `SynchView.DEFAULT_STACK` is used as the default instead.
- `isSynch()` method added to `SynchView`, checking `synchData.isSynchActivated()`.
- `resetSynchData()` restores the original sync configuration.

### 4. New UI Components

| Class | Purpose |
|-------|---------|
| **`SynchViewButton`** | Overlaid on each view (EAST position). Shows red (OFF) or green (ON) sync icon. Click toggles auto-sync for that individual view. |
| **`ManualSynchViewButton`** | Overlaid on each view (SOUTHEAST position). Hand icon, red/green. Click opens manual sync setup (series selection popup if >1 candidate). |
| **`SynchOptionsCheckBoxGroup`** | (Partially implemented) Checkbox menu for granular sync option selection (scroll, pan, zoom, rotation, flip, W/L, spatial unit). Currently mostly commented out. |

### 5. Event Propagation Rewrite (`DefaultView2d.propertyChange`)

The `propertyChange(SynchCineEvent)` method now includes a **sync propagation guard**:

1. **Issuer check:** If the event comes from another view, it verifies:
    - The issuer view's sync is activated.
    - For **auto-sync**: both views share the same `frameOfReferenceUID` and both have auto-sync ON.
    - For **manual sync**: both views have manual sync ON and the issuer has a `ManualSyncData` entry targeting the current view.
2. **Location offset for manual sync:** When in manual mode, the slice location is adjusted by the offset: `location = location + targetLocation - sourceLocation`. This enables synchronizing views that don't share the same spatial reference but are aligned by the user at a specific pair of slices.

The `propertyChange(SynchEvent)` (non-cine, e.g., zoom/pan/rotation) also now:
- Blocks propagation if either view has sync disabled.
- Only filters actions for non-self events (`this != synch.getView()`).

### 6. Manual Sync Workflow

The manual sync setup (`DefaultView2d.setupManualSync()`) works as follows:

1. User clicks the hand button on a view.
2. If only one eligible view (same orientation, different FrameOfReferenceUID) exists, sync is set up directly.
3. If multiple candidates exist, a popup menu lets the user choose.
4. If manual sync is **already active** in the container (other views paired), the new view joins the existing group.
5. On setup:
    - Current `SlicePosition` of both views is recorded as the reference offset.
    - Both views get `ManualSyncData` entries pointing to each other.
    - Both views are registered as property change listeners.
    - Manual sync state is set to ON on both views.
    - The global `SynchView.getSynchData().manualSyncState` is set to ON.

### 7. `DicomSynchManager.handleStackMode()` Logic

The core algorithm in stack mode:
1. Collects all views (including visible views from other containers with same group ID).
2. Groups views by `FrameOfReferenceUID`.
3. Auto-sync is activated if any FrameOfReferenceUID group has >1 views.
4. For each view:
    - Same orientation + same FrameOfReferenceUID → full sync (or scroll-only if different pixel size or PR applied).
    - Different orientation + same FrameOfReferenceUID → crosslines enabled + scroll sync (full for MprView).
    - Different FrameOfReferenceUID + same orientation → marked as `canBeManuallySynced` with scroll-only actions.
5. The global sync button and per-view buttons are enabled/disabled accordingly.

---

## Known Issues & Incomplete Areas

1. **Manual sync deactivation not implemented** – The commit message explicitly states this. The "OFF" path in `ManualSynchViewButton` click handler has empty loops and commented-out code.
2. **Multiple TODOs** throughout:
    - Adapting list of manually syncable views depending on existing auto-sync.
    - Error message if no location information is available for manual sync.
    - Display criteria for auto/manual buttons.
    - Blocking sync activation when nothing can be synced.
3. **Debug output** – `System.err.println` calls left in production code (`resetSynchState`, `configurePaneSynchData`).
4. **`SynchOptionsCheckBoxGroup`** – The action listeners for individual sync options (scroll, zoom, rotation, etc.) are entirely commented out. The class is instantiated in `ViewerToolBar` but the menu items are not wired up.
5. **`DefaultView2d.resetSynchState()`** – Manual sync cleanup on series change is commented out ("The updated view is included in manual synchronization, disable it for this view...").
6. **`SynchManager.markOrphanViews()`** – References an `eventManager.getOptions()` map with key `"force.sync.orphans"` that doesn't appear to be populated anywhere.
7. **`updatePaneSynchData()` in `DicomSynchManager`** – The entire method body is commented out (both `setActionsInView` calls), making it a no-op.
8. **Black background fill** – `DefaultView2d.draw()` now fills the entire view with `Color.BLACK` at the beginning of every paint, which may have performance implications or be unintended debug code.

---

## Summary

The refactoring introduces a clean separation of concerns for synchronization management with a strategy pattern (`SynchManager` hierarchy), replaces the flat `NONE/STACK/TILE` model with a composable auto+manual dual-state model, and adds per-view UI controls. The manual synchronization feature enables users to pair views with different spatial references by recording slice position offsets. However, the implementation is explicitly work-in-progress with deactivation logic, error handling, and UI refinements still pending.
