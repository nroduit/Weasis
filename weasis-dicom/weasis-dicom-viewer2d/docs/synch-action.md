# Synchronization Action Plan

## Step 1 — Analysis

> Analyze the changes regarding the view's synchronization and the new manual synchronization, committed by mhex.

Generate `synch-analysis.md` based on the commit history and code changes in the branch `4.7-synchro`.

---

## Step 2 — Improvement Proposals

> How to improve these changes.

Generate `synch-improvements.md` based on the analysis, with a list of identified issues and proposed improvements.

---

## Step 3 — Apply Improvements

> Apply Improvements

### ✅ Completed

| #   | Description                                                                 |
|-----|-----------------------------------------------------------------------------|
| 1.1 | Manual sync deactivation implemented                                        |
| 1.2 | `resetSynchState()` manual cleanup implemented                              |
| 1.3 | NPE in `hasSameFrUid()` fixed with `Objects.equals()`                      |
| 1.4 | NPE guards in `ViewSynchData` (now moot with eager init)                   |
| 1.5 | NPE in `propertyChange(SynchCineEvent)` fixed with null checks             |
| 2.1 | Debug output removed/changed to `DEBUG` level                               |
| 2.2 | Dead code removed (`updatePaneSynchData`, commented menus, `getRectangleIcon`, `SynchOptionsCheckBoxGroup` dead listeners) |
| 2.3 | Black fill replaced with `setBackground(Color.BLACK)` in constructor        |
| 2.4 | Redundant null check in `ViewSynchData` constructor removed                 |
| 3.1 | `SyncState` enum extracted into `SynchData`, decoupled from UI              |
| 3.2 | `ManualSyncData` made static                                                |
| 3.5 | Thread safety: `manualSyncDataSet` eagerly initialized with `ConcurrentHashMap.newKeySet()` |
| 4.2 | Standardized to `setActionsInView()` in `setupManualSync()`                 |
| 4.3 | `original` field documented with Javadoc                                    |
| 4.4 | `@Override` added on `toString()` in both `State` enums                     |
| 4.5 | Wildcard import replaced in `SynchViewButton`                               |

### ⏳ Deferred (larger architectural changes)

| #   | Description                                                                 | Reason                                                      |
|-----|-----------------------------------------------------------------------------|-------------------------------------------------------------|
| 3.3 | Move manual sync logic from `DefaultView2d` into `SynchManager`            | Major refactor, would change the API contract               |
| 3.4 | Replace `force.sync.orphans` with proper config                             | Requires `WProperties` integration and documentation        |
| 3.6 | Replace `ViewCanvas` references with identifiers in `ManualSyncData`        | Requires lookup mechanism                                   |
| 4.1 | Make `SynchManager.updateAllListeners()` abstract                           | Breaking change, needs verification of all subclasses       |

> All priority 1 (critical bugs), priority 2 (cleanup), and most priority 3–4 items have been applied. The compilation succeeds for both `weasis-core` and `weasis-dicom-viewer2d`. The remaining items (3.3, 3.4, 3.6, 4.1) are larger architectural refactors that should be done as separate tasks to minimize risk.

---

## Step 4 — Fix Manual Synchronization for Multiple Views

> The manual synchronization does not always work when selecting multiple views with series in the same orientation. For instance, when selecting four axial series with manual synchronization and then activating or deactivating by clicking on the manual button, the effect is not always correct.

### Identified Issues

1. **Activation — "join existing group" logic is broken for >2 views**
   When `globalSynchData.isManualSynchActivated()` (lines 282–294), the code finds ONE already-synced view, loops through its `ManualSyncDataSet` targets, and sets up sync between the new pane and each target, plus with `v` itself. But it does `break` after the first synced view. This means it doesn't update the OTHER already-synced views to know about the new pane — they won't have a `ManualSyncData` entry pointing back to the newly added pane.

2. **`setupManualSync` creates duplicate entries**
   When adding a 3rd view to an existing pair, `setupManualSync(pane, msd.getTargetPane())` adds entries. But if `setupManualSync(pane, v)` is then called, it adds entries for `pane↔v`. However, the existing views (targets of `msd`) don't get entries pointing to `pane`.

3. **Deactivation — only cleans links FROM this view, not links BETWEEN other views**
   When deactivating (lines 306–333), it removes this view from all targets, but the remaining views still have links between each other. The global sync state is set to `OFF` unconditionally (line 329), even if other views are still manually synced.

4. **`manualSyncData` can be null at line 1171**
   If `issuerSyncData.getManualSyncDataByPane(this)` returns `null` (e.g., because the link wasn't set up symmetrically), this will NPE.

---

## Step 5 — Improved Manual Synchronization Logic

> The logic of the manual synchronization still does not always work when selecting multiple views. How to improve this logic to allow multiple manual synchronizations.

Improvements to allow multiple manual synchronizations to work reliably:

1. **Fixed stale `synchData` reference in button lambda**
   The lambda now reads `synchData` fresh from `actionsInView` every time the button is clicked, instead of capturing it at button creation time. This was the root cause of inconsistent behavior: after `updateAllListeners` replaced the `ViewSynchData` object, the old lambda was still operating on the previous instance.

2. **Button always created, visibility controlled separately**
   The manual sync button is now created once unconditionally (no longer gated by `isCanBeManuallySynced()`). Visibility is controlled in the state update section, and the button state is synced from the current `synchData.isManualSynchActivated()` on every `updateSynchState()` call.

3. **Extracted activation/deactivation into separate methods**
   `activateManualSync()` and `deactivateManualSync()` are clean methods that operate on the fresh `currentSynchData` parameter, not a captured variable. This makes the logic easier to follow and debug.

4. **Deactivation preserves remaining group members**
   When a view leaves the group, only its own links are removed. Other group members' mutual links are preserved. The property change listener is only removed from a target if that target has no remaining manual sync links. The global sync state is only set to `OFF` if NO view has manual sync remaining.

5. **Duplicate link prevention**
   `setupManualSync` skips if `source == target` or if a link already exists between the pair.
