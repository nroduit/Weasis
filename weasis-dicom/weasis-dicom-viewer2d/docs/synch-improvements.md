# How to Improve the Synchronization Refactoring

## Priority 1 — Critical bugs & missing logic

### 1.1 Implement manual sync deactivation (`DefaultView2d` lines 307-320)

The "OFF" click path is empty. When the user clicks the manual sync button to deactivate:

```java
// Current (empty):
for (ViewSynchData.ManualSyncData manualSynchData : synchData.getManualSyncDataSet()) {
  //manualSynchData.
}
```

**Needed:**
- For each `ManualSyncData` entry, remove the reciprocal link in the target pane's `ViewSynchData`.
- Set `manualSyncState = OFF` on both source and target.
- Remove the property change listener from the target pane.
- Update the manual sync button state on the target pane's view.
- If no more manual sync remains anywhere, set the global `SynchView.getSynchData().manualSyncState` to OFF.

### 1.2 Implement `resetSynchState()` manual cleanup (`DefaultView2d` lines 1616-1628)

When a series changes, the manual sync data must be cleaned up. The commented-out code must be completed so that removing a series from a view also cleans the paired views:

```java
if (manualSyncDataSet != null && !manualSyncDataSet.isEmpty()) {
  for (ViewSynchData.ManualSyncData msd : new HashSet<>(manualSyncDataSet)) {
    ViewCanvas<?> targetPane = msd.getTargetPane();
    ViewSynchData targetSd = (ViewSynchData) targetPane.getActionsInView().get(ActionW.SYNCH_LINK.cmd());
    if (targetSd != null) {
      targetSd.getManualSyncDataSet().removeIf(d -> d.getTargetPane() == this);
      if (targetSd.getManualSyncDataSet().isEmpty()) {
        targetSd.setManualSyncState(State.OFF);
      }
    }
  }
  manualSyncDataSet.clear();
  synchData.setManualSyncState(State.OFF);
}
```

### 1.3 NPE in `hasSameFrUid()` (`SynchManager` line 121)

```java
return getFrameOfReferenceUID(series1).equals(getFrameOfReferenceUID(series2));
```

If `getFrameOfReferenceUID(series1)` returns `null`, this throws NPE. Use `Objects.equals()`:

```java
return Objects.equals(getFrameOfReferenceUID(series1), getFrameOfReferenceUID(series2));
```

### 1.4 NPE in `getManualSyncDataByPane()` (`ViewSynchData` line 78)

`manualSyncDataSet` may be `null` (it is lazily initialized in `addManualSyncData`), but `getManualSyncDataByPane` iterates it without a null check:

```java
public ManualSyncData getManualSyncDataByPane(ViewCanvas<?> targetPane) {
  if (manualSyncDataSet == null) return null;  // ADD THIS
  for (ManualSyncData data : manualSyncDataSet) { ... }
}
```

Same issue in `emptyManualSyncDataSet()` and `removeManualSyncData()`.

### 1.5 NPE in `DefaultView2d.propertyChange(SynchCineEvent)` (line 1252)

When `synchData` or `issuerSyncData` is null (non-synced views), accessing `getManualSyncState()` will NPE:
```java
if (synchData.getManualSyncState() == State.ON && issuerSyncData.getManualSyncState() == State.ON) {
```
Add null checks, or guard this block under the already-checked `propagateSync` condition from manual sync.

---

## Priority 2 — Code cleanup

### 2.1 Remove debug output

- `DefaultView2d.resetSynchState()` line 1633: `System.err.println(this.series + " set fruid to null reset");`
- `DicomSynchManager.configurePaneSynchData()` line 426: `//System.err.println(paneSeries + " set fruid to " + paneFrameUID);`
- `DefaultView2d.propertyChange(SynchCineEvent)` line 1263: `AuditLog.LOGGER.info("synch:series nb:{}", ...)` — keep or move to DEBUG level.

### 2.2 Remove dead code

- **`updatePaneSynchData()`** in `DicomSynchManager` (lines 141-148): entirely commented out, never called → delete.
- **`addNonOrphanActivationMenu()`**, **`buildSynchMenuItem()`**, **`addStandardSynchMenu()`** in `DefaultView2d` (lines 428-522): 100+ lines of commented-out code → delete. If needed later, recover from git.
- **`getRectangleIcon()`** in `DefaultView2d` (lines 446-466): only referenced by commented code → delete.
- **`SynchOptionsCheckBoxGroup`**: 50+ lines of commented-out action listeners → either wire them up or delete the class.

### 2.3 Remove the black fill in `draw()` (line 1059-1060)

```java
g2d.setColor(Color.BLACK);
g2d.fillRect(0, 0, getWidth(), getHeight());
```

This fills the entire component with black **every repaint**, even when the image covers the full area. This is likely leftover debug code. If a background is needed, it should be set via `setBackground(Color.BLACK)` in the constructor instead.

### 2.4 Redundant null check in `ViewSynchData` constructor (lines 25-28)

The parent constructor `SynchData(Mode, Map, boolean)` already checks `actions == null`. The check in `ViewSynchData` is redundant and happens **before** `super()` is called (which is actually a compile error in standard Java — `super()` must be the first statement). Verify this compiles; if it does, it's because the check is dead code. Remove it:

```java
public ViewSynchData(Mode mode, Map<String, Boolean> actions, boolean synch) {
  super(mode, actions, synch);
  this.orphan = false;
  this.canBeManuallySynced = false;
}
```

---

## Priority 3 — Design improvements

### 3.1 Unify `SynchViewButton.State` and `ManualSynchViewButton.State`

Both enums are identical (`OFF`, `ON` with colors). `SynchData` already uses `SynchViewButton.State` for both `autoSyncState` and `manualSyncState`. This creates a confusing dependency where the **data model** (`SynchData`) depends on a **UI widget** (`SynchViewButton`).

**Recommendation:** Extract a shared `SyncState` enum into a standalone class or into `SynchData` itself:

```java
public enum SyncState {
  OFF, ON
}
```

Keep the color/icon mapping in the button classes. `SynchData` should only reference the domain enum.

### 3.2 `ManualSyncData` should be a top-level record, not an inner class

`ManualSyncData` is a non-static inner class of `ViewSynchData`, meaning every instance holds an implicit reference to its enclosing `ViewSynchData`. It also holds a reference to a `ViewCanvas` (which holds a `ViewSynchData`), creating a circular reference chain. Make it a `static` nested class or a top-level record:

```java
public record ManualSyncData(double sourceLocation, double targetLocation, ViewCanvas<?> targetPane) {}
```

### 3.3 Move manual sync setup logic out of `DefaultView2d`

The `setupManualSync()`, `addOrphanSeriesSelectionMenu()`, and the `ManualSynchViewButton` click handler contain ~130 lines of sync *business logic* in a *view class*. This logic should live in `SynchManager`:

```java
// In SynchManager:
public void setupManualSync(ViewCanvas<E> source, ViewCanvas<E> target) { ... }
public void deactivateManualSync(ViewCanvas<E> source) { ... }
public List<ViewCanvas<E>> getManuallySyncableViews(ViewCanvas<E> pane) { ... }
```

`DefaultView2d.updateSynchState()` should only wire the button to call these methods.

### 3.4 Replace `eventManager.getOptions().get("force.sync.orphans")` with a proper config

This string-keyed map lookup (`SynchManager` lines 101, 132) has no documentation, no setter, and no default. Either:
- Add it as a `WProperties` system preference with a clear default, or
- Remove it and make orphan handling always consistent.

### 3.5 Thread safety on `manualSyncDataSet`

`manualSyncDataSet` is a `HashSet` accessed from EDT (button clicks) and potentially from property change events. If any background thread touches it, use `ConcurrentHashMap.newKeySet()` or synchronize access. At minimum, initialize it eagerly to avoid null checks everywhere:

```java
protected final Set<ManualSyncData> manualSyncDataSet = ConcurrentHashMap.newKeySet();
```

### 3.6 Avoid storing `ViewCanvas` references in `ManualSyncData`

Holding a direct reference to a `ViewCanvas` pane creates a risk of:
- Memory leaks (disposed views kept alive by sync data).
- Stale references (view recycled, old reference still used).

Consider using an identifier (e.g., a pane index or a UUID assigned to each view) and resolving the pane from the container when needed.

---

## Priority 4 — Consistency & polish

### 4.1 `SynchManager.updateAllListeners()` vs `DicomSynchManager.updateAllListeners()`

The base `SynchManager` has a full implementation of `updateAllListeners()` that is **completely overridden** by `DicomSynchManager` (which never calls `super`). This means the base class implementation is dead code for DICOM. Either:
- Make the base method `abstract` and keep only shared helper methods, or
- Have `DicomSynchManager` call `super` and only add DICOM-specific behavior.

### 4.2 Inconsistent `setActionsInView` vs `getActionsInView().put()`

In `setupManualSync()` (lines 384-385):
```java
source.getActionsInView().put(ActionW.SYNCH_LINK.cmd(), synchDataSource);
target.getActionsInView().put(ActionW.SYNCH_LINK.cmd(), synchDataTarget);
```
But everywhere else, `setActionsInView()` is used. Standardize to `setActionsInView()`.

### 4.3 `original` flag semantics are unclear

The `original` field on `SynchData` is used in confusing ways:
- Set to `true` in constructor.
- Set to `false` when user manually modifies sync.
- Checked in `shouldConfigurePaneSync()` with `!synch.isSynchActivated() && !synch.isOriginal()`.
- Reset to `true` in `resetSynchState()`.

Document what "original" means (e.g., "hasn't been user-modified since last `updateAllListeners` call") and consider renaming to `userModified` (inverted).

### 4.4 Missing `@Override` on `toString()` in `State` enums

Both `SynchViewButton.State.toString()` and `ManualSynchViewButton.State.toString()` are missing `@Override`.

### 4.5 Wildcard import in `SynchViewButton`

Line 14: `import java.awt.*;` — replace with specific imports (`java.awt.Color`).

---

## Summary — Suggested action order

1. **Fix NPEs** (3 occurrences) — immediate crash risks.
2. **Implement manual sync deactivation** — the main missing feature.
3. **Implement `resetSynchState()` cleanup** — prevents stale sync state on series change.
4. **Remove debug output and dead code** — ~200 lines of commented-out code.
5. **Extract `SyncState` enum** from UI into domain model.
6. **Move manual sync logic** from `DefaultView2d` into `SynchManager`.
7. **Make `ManualSyncData` static** or a record, avoid pane references.
8. **Clarify base vs override** in `SynchManager` hierarchy.
