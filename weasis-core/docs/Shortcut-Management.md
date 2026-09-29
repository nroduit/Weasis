# Shortcut management

How keyboard shortcuts are declared, customized, matched and dispatched, and how a mouse action
knows which button an event belongs to. The classes live in `org.weasis.core.api.gui.util` and are
part of the plugin SDK. The default shortcuts and the way a user changes them are described on the
[Shortcuts](https://weasis.org/en/basics/shortcuts/) page of the user site.

## The registry

`ShortcutManager` is the single registry of the configurable shortcuts. A shortcut is a
`ShortcutEntry` identified by a stable **id** (`viewer.zoomIn`, `dicom.nextSeries`,
`mpr.recenterAll`, `draw.copy`, `graphic.<toolKey>`): ids, not keys, are what the code, the
preferences and the documentation refer to. An entry carries a description, a category used to
group it in the preference page, a `ShortcutContext`, a default binding and the current binding,
which differs from the default once the user has changed it.

The registry is filled at start-up with the shortcuts of the viewers, one entry per `ActionW`
feature that has a key and one entry per palette tool of the `GraphicRegistry` (see
[Measurement tools](Measurement-Tools.md)); the user's bindings are then read from the
preferences and copied into the `Feature` objects, which the toolbars and the mouse action keys
read. A shortcut registered later, by a plugin, receives the user's binding at registration.
Every change fires `ShortcutManager.PROPERTY_SHORTCUTS_CHANGED`.

### Contexts and conflicts

A `ShortcutContext` says where a shortcut is active. Contexts form a tree: `VIEW_CANVAS` is the
root of the viewers, `DICOM_VIEWER` and `VIEWER_3D` are its children, `MPR` is a child of
`DICOM_VIEWER`, and `DICOM_EXPLORER` is a separate root. Two entries with the same binding
conflict only when their contexts overlap, that is when one is the other or one of its ancestors:
the same key may serve the 3D viewer and the MPR, never the MPR and every view. Conflicts are
reported to the user, who may keep them.

### Persistence

Only the bindings that differ from their default are stored, under a node per operating system
(`ShortcutManager.getPreferenceNodeName()`): preferences can be shared between workstations, while
the platform modifier and the keys reserved by the system differ from one system to another.

## Key bindings

A `KeyBinding` is a key code with the keyboard modifiers held with it. Its modifiers are always
the extended ones of `InputEvent` (`SHIFT_DOWN_MASK`, `CTRL_DOWN_MASK`, `META_DOWN_MASK`,
`ALT_DOWN_MASK`, `ALT_GRAPH_DOWN_MASK`); whatever is passed in is normalized, so a value written
with the deprecated masks of the first AWT event model, by an older plugin or in older preferences,
designates the same shortcut, and mouse button bits are ignored. The methods of `ShortcutManager`
and `Feature` that take a modifier as an `int` normalize it the same way and always return the
extended form.

A binding matches a `KeyEvent` when the key is the same and **exactly** the same keyboard
modifiers are held: a plain key does not fire while Alt is down, and Ctrl + Alt + X is not
Alt + X. A mouse button held during the key press does not count. A binding with key code `0` is
unassigned and matches nothing.

`KeyBinding.MENU_SHORTCUT_MASK` is the modifier of the platform menu shortcuts, Ctrl on Windows and
Linux and Cmd on macOS; use it for the clipboard-like shortcuts. `KeyBinding.modifiersOf` gives
the keyboard modifiers of an event for the few protected methods that still take a key code and
modifiers. Deprecated input API (`getModifiers()`, the `*_MASK` constants without `DOWN`) is not
used anywhere: in that model Alt and the middle button, and Meta and the right button, share a bit.

## Dispatching key events

A key listener does not compare keys: it asks the registry whether an event matches an id
(`ShortcutManager.matches(id, event)`), or, when it handles several shortcuts, declares them in a
table.

- `ShortcutActions` binds ids to actions (`Runnable`, or `Consumer<KeyEvent>` when the action needs
  the event) and `dispatch` runs the first one the event matches, returning whether there was one.
- `ShortcutTable<T>` binds ids to any value and `find` returns the value of the first match. It is
  the form to use when the outcome decides whether the event goes on to the next handler: the
  drawing keys of `DrawingsKeyListeners` hold predicates, the tab navigation holds a direction.

Tables hold ids and resolve them against the current bindings at each event, so a shortcut changed
in the preferences applies at once and a table is built once, as a field of its listener. When two
ids share a binding the first one registered in the table wins.

A key pressed on a view is offered, in this order, to the drawing in progress (which owns Escape
and Backspace by default while a graphic is being drawn), to the shortcuts of the view (mouse
action cycling, information layer, full screen, rotation, flip), to the keys of the left mouse
actions, then to the event manager: the shortcuts common to all viewers in
`ImageViewerEventManager`, those of the concrete viewer (`DICOM_VIEWER`, and `MPR` only while an
MPR container is selected), the window/level preset keys and the keys of the graphic tools. The
shortcuts that act on the docking layout (tab navigation, maximize, externalize, close) are bound
to the docking framework, outside the key listeners of the views.

## Menu accelerators

A menu item that repeats a shortcut takes its accelerator from the registry,
`ShortcutManager.getKeyStroke(id)`, never from a hard-coded keystroke, so the menus show the key
the user chose. An unassigned shortcut gives no accelerator.

## Mouse buttons

A `MouseActionAdapter` is bound to one or several buttons by an extended button mask
(`setButtonMaskEx`). On press and drag the extended modifiers of the event tell whether the
adapter is concerned; on release they no longer contain the released button, so the adapter tests
the button itself with `isBoundButton(MouseEvent)`. Keyboard modifiers held during a mouse gesture
are read from the extended modifiers (`isAltDown()`, `getModifiersEx()`), and an event is copied
with `MouseEventDouble`, which keeps them.

## Adding a shortcut

Declare an id and register it with `ShortcutManager.register`, giving its category, the context it
is active in and a default key code and modifiers, expressed with the extended masks (or key code
`0`, to let the user assign one). Handle it with `matches(id, event)` or a `ShortcutActions` table
in the key listener of that context, and give the matching menu item the accelerator of the
registry. The entry then appears in the preference page and in the shortcut page of the Help
menu, takes part in conflict detection and is persisted without further code. A graphic tool needs
none of this: the default key of its `GraphicToolDescriptor` is registered for it.
