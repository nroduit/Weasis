# Preferences

How plugin preferences are scoped, loaded, stored and exposed as preference pages since the
move from the OSGi Preferences service to the JDK `java.util.prefs` API.

## Model

Every plugin owns a subtree `org/weasis/<pluginId>`, obtained through
`BundlePreferences.getDefaultPreferences(pluginId)`. The plugin id is the pf4j id of the
plugin that loaded the calling class (`WeasisPluginManager.getPluginId(Class)`); code that is
not plugin-scoped uses `AppProperties.CORE_PLUGIN_ID`. The same id scopes the per-plugin data
folder (`BundlePreferences.getDataFolder(pluginId)`) under the data root described in
[Launcher-Overview.md](../../weasis-launcher/docs/Launcher-Overview.md#base-directories).

Node names and key names are the same as in previous versions; only the API type changed, so
settings classes navigate to their child node and read or write typed values with defaults.

## Loading and storing

The nodes are not in the JDK store: they are `java.util.prefs` nodes held by the
`UserSettingsStore`, one JSON file per plugin in the `settings` folder of the preference
directory, so they belong to the current Weasis user and profile:

```json
{"kind": "weasis.settings", "id": "weasis-core", "schema": 1, "updated": "2026-10-13T16:00:00Z",
 "values": {"key": "value"},
 "nodes": {"view2d.default": {"values": {"…": "…"}, "nodes": {}}}}
```

- A plugin's file is read when its node is first asked for. When the file does not exist yet,
  the earlier values are copied once, a value found first being kept: the same node of the JDK
  store, where the earlier 5.0 builds kept it for the whole OS account, then the 4.x file
  `<module>.xml` of the same preference directory (Felix format: an element with child elements
  is a node, an element with text is a key; `weasis-core` also reads the 4.x `weasis-core-ui`).
  Neither is modified, so a 4.x installation still finds its files.
- A settings class applies the values of its node onto its in-memory state and writes them back
  through the `BundlePreferences.putXxxPreferences` helpers. A change is saved about two seconds
  later, on `flush()` / `sync()`, once the last plugin has stopped, and at exit.
- A file that does not parse is renamed `.invalid` and the plugin starts from its defaults; a
  file of a newer `schema` is read but never overwritten.

The files are not exchanged with the preferences service yet. New code declares typed settings
on top of these nodes or of the other stores, see [Settings.md](Settings.md).

## Preference pages

Preference UI pages are pf4j extensions: a `PreferencesPageFactory` annotated with
`@Extension` (and `@SingletonExtension`) is discovered through the plugin manager by
`PreferenceDialog`, which places each page according to the factory's component position
band and honours the per-factory enable flag from the system preferences.
