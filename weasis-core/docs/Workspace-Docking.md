# Workspace docking engine

The central viewer area is managed by a Weasis-native workspace engine in
`org.weasis.core.ui.docking.ws`. It replaces the external docking libraries used by earlier
versions and has no third-party dependency.

## Abstractions

- **`WorkspaceManager`** — one instance per application, owned by `UICore` and reached through
  `UICore.getWorkspaceManager()`. It owns every workspace window and exposes the operations:
  open, stack, split, move tab, select, close, detach, maximize, restore, and the listener
  registration (`WorkspaceListener`: view selected, workspace emptied).
- **`Workspace`** — the root panel of a window: a tree of `JSplitPane` whose leaves are tab
  groups, with a maximize mode that shows a single view in place of the tree. The main window
  has one; each `DetachedFrame` has its own.
- **`TabGroup`** — a group of tabs (FlatLaf closable tabs). A view belongs to exactly one group.
- **`WorkspaceView`** — the contract a dockable content implements: a stable `viewId`, the
  component, the tab title, icon and tooltip, whether it is closable or detachable, a close
  veto (`requestClose`), the entries of its tab menu, and a `typeId` and `saveState` used by
  snapshots. `ViewerPlugin` implements it for all viewers.
- **`DockSide`** — the side of an anchor view on which a split is made.

Explorer and tool panels (`SidePanel`, `MiniStrip`, `PluginTool`) are plain Swing components
outside the workspace tree; the bottom footer hosts panels contributed through the
`FooterPanelProvider` extension point (`org.weasis.core.api.gui`).

## Drag and drop

`drag/` implements tab dragging on the window glass pane: `TabDragController` drives the
gesture, `DropZone` is a sealed type (stack onto a group, split beside a group, detach), and
`DropGeometry` computes the zones from the group bounds. Drag can be disabled with
`WorkspaceManager.setDragEnabled`.

## Snapshots

`snapshot/` captures the layout of every window as a `WorkspaceSnapshot` (a tree of split and
group nodes holding view descriptors) and serializes it as JSON with `WorkspaceSnapshotIO`.
Restoring takes a `ViewRestorer` that recreates a view from its descriptor; a view the
restorer cannot recreate is skipped. What a view stores in its descriptor is opaque to the
engine.

## Commands

`WorkspaceCommands` exposes the engine under the `workspace:` command namespace (list, select,
close, maximize, restore, stack, split, detach, snapshot, layout), so that a layout can be
driven from the command line or by an agent.
