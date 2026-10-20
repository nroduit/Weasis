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
  has one; each `DetachedFrame` has its own. It remembers the view last selected in its window.
- **`DetachedFrame`** — a *viewer window*: a workspace plus a host for the toolbar row of the
  application. There are two window kinds only: the main window (menu bar, shell, workspace)
  and viewer windows. The toolbar row is one component (the viewer toolbars are singletons
  bound to the event manager) that the main window moves into the window of the selected
  viewer (`WeasisWin.moveToolbarRow`); the shell (explorer, tools) stays in the main window.
- **`TabGroup`** — a group of tabs (FlatLaf closable tabs). A view belongs to exactly one group.
- **`WorkspaceView`** — the contract a dockable content implements: a stable `viewId`, the
  component, the tab title, icon and tooltip, whether it is closable or detachable, a close
  veto (`requestClose`), the entries of its tab menu, and a `typeId` and `saveState` used by
  snapshots. `ViewerPlugin` implements it for all viewers.
- **`DockSide`** — the side of an anchor view on which a split is made.

### Selection and the active window

The manager keeps the application's selection (`selectedView`): the view last reported by
`viewSelected`, while it shows. The window of that view is the *active window*
(`activeWorkspace`), and that is where a new view goes: `openInActive` fills it when empty, and
`ViewerPlugin.showDockable` anchors a new tab or split on the selected viewer. Activating a
window (focus gained) selects the view that window has selected (its last selected view, else
the selected view of its first group), unless that view is already the selection, so a click on
a viewer window brings its toolbar row and its tools without a click on a tab. `workspace:list`
reports the selection under `selected`.

### Screens by role

A screen is addressed by a role, never by a device, in what travels (sessions, protocols,
verbs): `control` (the worklist screen, where the main window is), `diagnostic-1`,
`diagnostic-2`, `color`, `portrait` (`ScreenRole` in the command vocabulary). `ScreenRoles`
maps them to the monitors of the workstation. A role the user gave a monitor on the Screens
preference page wins (local setting `weasis.screen.<id>.role`, never in the remote
preferences; `workspace:screens --id <screen> --role <role>` sets it too, `--auto` takes it
back); the other roles are derived: the main window's screen is `control`, the other screens
from left to right are `diagnostic-1` and `diagnostic-2`, `portrait` the first other screen
taller than wide, `color` the first other screen. A role no monitor fills resolves to the
nearest one, so every role resolves and a single-screen workstation maps every role to its
screen; the mapping says which roles are *fulfilled* by a monitor of their own.
`ExternalDisplay.onRole(role)` is the placement a hanging plan or a session uses
(`ViewerPlacement.External`), and `ViewerPlugin.showDockable` detaches the viewer on that
screen through the manager.

A snapshot window carries its monitor (device id, bounds of the monitor and the role it
answered to), its own bounds and its state (`normal`, `maximized`; `fullscreen` reserved).
A restore places a window by role first: on the monitor of a fulfilled role, at the saved
bounds when it is the same device, at the same relative place and size when the role moved
to another monitor; else on its device when it still exists; else at its bounds when a
monitor shows them. A viewer window that none of this places folds into the main window as
tabs, or as a split beside the main tree when the local setting `weasis.screen.fold` says
`split`; nothing is lost. The main window goes back to its saved place and state the same
way. `restore` returns what it folded, and `workspace:load` reports it under `folded` with the
tool ids the shell did not offer under `skippedTools`.

### Viewer windows: title, series drop, Window menu

A viewer window is titled after the view it stands for (its last selected view, else the
selected view of its first group), through `tabTitle()`, so the title is masked like the tab
and follows the selection and the identity mask (`updateTabInfo` refreshes it). Every
workspace window accepts a series drop from the explorer: the main window registers its drop
handler once (`setDropHandler`), the manager gives it to every workspace, and the handler
activates the workspace it was dropped on (`activate`: its view becomes the selection, an
empty workspace stays the target of the next open) before opening the series, so the series
opens where it was dropped. `moveToScreen(window, role)` puts a window on the usable area of
the screen of a role (`workspace:move --view --screen`), the main window included. The Window
menu of the main window lists the windows (the active one checked; choosing one activates
it), the screens by role to move the active window to, its fullscreen state and *Open in a
new window* for the selected viewer.

### Window states: maximized, fullscreen, immersive

`maximize(view)` shows a view alone in its window (a card swap in the `Workspace`).
`setFullscreen(window, on)` is a window state: the window takes the full bounds of its
monitor without decorations (`GraphicsDevice.setFullScreenWindow`), the toolkit restores the
previous bounds on the way back; the snapshot carries it as the window state `fullscreen` and a
session restores it (`workspace:fullscreen [--view] [--off]`, Shift+F11 on the window of the
selected viewer). `setImmersive(window, on)` shows the workspace of a window alone: the
listeners that own the chrome hide it (`WorkspaceListener.immersiveChanged`: the main window
hides its menu bar, toolbar row, both shell sides with their stripes and the strip; a viewer
window hides its toolbar row); it is not a snapshot state.

The canvas fullscreen of an image viewer (F11, double click, `workspace:fullscreen --canvas`)
is the composition of four states, each undone on the way back only when this call set it: the
selected canvas alone in the viewer's grid (`ImageViewerPlugin.maximizedSelectedImagePane`),
the viewer maximized in its window, the window fullscreen and immersive. No dialog is involved
and no canvas is reparented into another window.

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

The viewers fill that descriptor with their content and presentation. `ViewerPlugin` stores
its name and series ids and reports the id of its factory as the view type; an
`ImageViewerPlugin` adds its grid, its synchronization, the selected slot and, per cell, the
tiling of the viewport and a `ViewState` per pane. A `ViewState` has two parts: the data
(`ViewData`: series, image, instance, frame), which is what a session binds, and the
presentation (`ViewPresentation`: zoom, pan, rotation, flip, window/level or preset id, LUT
shape, color map id, inversion, stack order, a position in the stack and the modality and pixel
size it was captured on), which holds no identifier. The combination restores a pane exactly;
the presentation alone is a display template whose adapter rules keep, on other data, only what
transfers (the image by slice location or relative position, the explicit scale for the same
pixel size, the window values for the same modality, presets, color maps and stack orders by
id). Both apply themselves through the events the synchronization uses; a viewer type whose
presets and sort orders have ids supplies a `ViewStateAdapter`.
A third part, the blend (`ViewState.Blend`), carries the id of a series blended over the shown
one (fusion) and the parameters of the blend, which the adapter owns: the series id is data, the
parameters are presentation.

The typed form of what an `ImageViewerPlugin` saves is a `HangingLayout`: grid, synchronization,
selected slot and, per cell, the tiling and the `ViewState` of each pane. `ImageViewerPlugin.hang`
applies one headlessly on a live viewer, selected or not ("this series, in this cell of this
grid, shown this way"); `restoreState` is that call on the JSON, `captureLayout` the reverse, and
the `view2d:hang` and `view2d:viewport` verbs expose both. A hanging plan produces the same
object. The MPR and volume rendering containers store their own part instead (`MprState`:
source series, crosshair, rotation, MIP mode, thickness and presentation per plane;
`VolumeState`: series, preset id, camera, rendering); they build the volume again from the
series and apply that part once the build is ready.
`ViewerRestorer` (`org.weasis.core.ui.editor`) is the restorer of the application: it reuses
an open view by id, otherwise asks the factory of the type for a new viewer, finds the series
through the `DataExplorerModel` of each explorer (`SeriesResolver.ofApplication`) and calls
`ViewerPlugin.restoreState`.

## Commands

`WorkspaceCommands` exposes the engine under the `workspace:` command namespace (list, select,
close, maximize, unmaximize, fullscreen, stack, split, detach, move, screens, save, load, panel), so that a layout can be
driven from the command line, from `weasis-cli` or by an agent. The namespace is a Picocli
command model (see the command bridge in `weasis-launcher/docs/Launcher-Overview.md`): every
verb documents itself with `--help`, and the verbs that change the layout return the new state
when JSON output is on, which is the default for remote calls.

`list` returns the windows with their tab groups and views as JSON. A view carries its id, its
tab title resolved under the identity mask in force, whether it is selected, and its viewer type
id when a `SeriesViewerFactory` claims it; an image viewer adds its grid, its synchronization
and one entry per pane with the series and the image the pane shows (`ViewDisplayState`, in
`org.weasis.core.ui.editor.image`, builds both that pane description and the full display state
the display verbs echo). A window reports the view it has maximized; the listing reports the
application's selection under `selected`. View ids are durable handles; the window and group
numbers are positions in that listing and change with the layout.

`detach` floats a view in a viewer window near the main window, on the usable area of the
screen of a role (`--screen diagnostic-1`) or at explicit bounds (`--bounds`); `screens` lists
the roles with the monitor each one resolves to. `hp:apply --screen <role>` hangs a reading
step in a viewer window on that screen.
