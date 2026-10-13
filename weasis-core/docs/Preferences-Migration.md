# Preferences

How plugin preferences are scoped, loaded, stored and exposed as preference pages since the
move from the OSGi Preferences service to the JDK `java.util.prefs` API.

## Model

Every plugin owns a subtree of the JDK user root, `org/weasis/<pluginId>`, obtained through
`BundlePreferences.getDefaultPreferences(pluginId)`. The plugin id is the pf4j id of the
plugin that loaded the calling class (`WeasisPluginManager.getPluginId(Class)`); code that is
not plugin-scoped uses `AppProperties.CORE_PLUGIN_ID`. The same id scopes the per-plugin data
folder (`BundlePreferences.getDataFolder(pluginId)`) under the data root described in
[Launcher-Overview.md](../../weasis-launcher/docs/Launcher-Overview.md#base-directories).

Node names and key names are the same as in previous versions; only the API type changed, so
settings classes navigate to their child node and read or write typed values with defaults.

## Loading and storing

There are two layers:

- **Node-based** (the common path): a settings class applies the values of its node onto its
  in-memory state and writes them back through the `BundlePreferences.putXxxPreferences`
  helpers; the owner flushes with `sync()`.
- **File and remote** (`BundlePreferences.load` / `save`): the plugin subtree is exported to
  and imported from a file in the JDK XML preferences format. When a preferences service is
  configured (`weasis.pref.url`), the remote copy of the same subtree is fetched and merged on
  load, with remote values taking precedence, and posted back on save. Remote access is
  skipped for a local session unless storing the local session is explicitly enabled
  (`weasis.pref.local.session`, `weasis.pref.store.local.session`).

The remote exchange is opaque text: the server stores the payload keyed by user, profile and
module without parsing it.

Nothing calls `BundlePreferences.load` / `save` at present: the nodes live only in the JDK store
of the OS account and are not exchanged with the preferences service. New code declares typed
settings instead, see [Settings.md](Settings.md); the stores behind them are being unified.

## Preference pages

Preference UI pages are pf4j extensions: a `PreferencesPageFactory` annotated with
`@Extension` (and `@SingletonExtension`) is discovered through the plugin manager by
`PreferenceDialog`, which places each page according to the factory's component position
band and honours the per-factory enable flag from the system preferences.
