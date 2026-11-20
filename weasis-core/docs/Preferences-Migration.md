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

The nodes are not in the JDK store: they are `java.util.prefs` nodes held by the user
instance of the `SettingsFileStore`, one JSON file per plugin in the `settings` folder of the preference
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

### Importing a Weasis 4 installation

A preview build never migrates `~/.weasis` by itself: the 4.x release the person still runs keeps
using it. The import is an explicit action instead, *Import Weasis 4 settings…* in the preference
dialog or `config:import-legacy [--from <folder>] [--replace] [--dry-run]`, implemented by
`LegacyInstallation`. It reads `~/.weasis/preferences/<user>/<profile>/<module>.xml` into the user
store (`weasis-core-ui` into `weasis-core`), the `.properties` files of `~/.weasis/data/<plugin>`
into the machine store, and the documents the registries declared with their earlier location
(`DocumentStore.locate`, the earlier path relocated from the current roots to the 4.x ones) into
the document folders. A value already set in 5 is kept, replaced only with *Replace* or
`--replace`; a document is copied when absent, overwritten only on replace, and its registry reads
it again. Nothing under `~/.weasis` is written. The verb takes a checkpoint first and answers the
items with their counts; `--dry-run` answers the same without changing anything.

### Machine settings

What belongs to the workstation rather than to the person — window geometry, last folders, panel
sizes, proxy, OpenGL capabilities, the import and export options of the plugins — is kept by the
machine instance of the same store, `SettingsFileStore.machine()`, in
`<config>/machine/settings/<plugin>.json` of the OS account; it is never sent anywhere.

- `UICore.getLocalPersistence()` is filled from the values of `weasis-core` at start and written
  back on exit.
- A plugin keeps its options in a node named after the earlier file:
  `SettingsFileStore.machine().load(pluginId, "export", properties)` at start and `store(...)` at
  stop (`import-export`, `import`, `export`, `publish`).
- When a plugin has no file yet, the `.properties` files of its data folder are copied once:
  `persistence.properties` as the values of `weasis-core` (then the 4.x `weasis-core-ui` ones),
  any other file as the node named after it; the proxy credentials (`proxy.auth.*`) are dropped.
  The `.properties` files are left in place.
- The launcher reads the proxy from `weasis-core.json` before any plugin starts, and from
  `persistence.properties` until that file exists.

### Configuration documents

The user and machine layers of the configuration documents (window presets, color maps,
masking, measurement profiles, DICOM and DICOMweb nodes, printers, authentication methods, search
templates, calling nodes, launchers) are kept by scope, under the same name as the site document
they override:

| Scope | Folder | Documents |
|---|---|---|
| user | `<prefDir>/documents/` | `windowPresets.json`, `colormaps.json`, `identityMasking.json`, `measurementProfiles.json`, `dicomNodes.json`, `dicomWebNodes.json`, `dicomPrinterNodes.json`, `authentication.json`, `searchTemplates.json` |
| machine | `<config>/machine/documents/` | `dicomCallingNodes.json`, `dicomLaunchers.json`, `otherLaunchers.json` |

A registry asks `DocumentStore.locate(scope, name, earlier...)` for its file; the first earlier
file found (`customWindowPresets.json`, `customColorMaps.json` and the other files of the
preference directory, or the JSON of a plugin data folder) is copied once and left in place, and
the 4.x XML is still converted when neither exists. `DocumentStore.declared()` lists the documents
asked for, with their scope, for `config:list` and for the export and the mirror to come.

Every document is written atomically with the same header, then its body:

```json
{"kind": "weasis.document", "id": "dicomNodes", "schema": 1, "updated": "2026-10-13T09:40:00Z",
 "nodes": [ ... ]}
```

`id` is the file name without `.json`; a reader refuses a `schema` newer than its own and reads
the earlier `version` key of `measurementProfiles.json` as the schema.

#### Hanging protocols and sessions

`HangingProtocolDocuments` keeps two documents before the engine that uses them exists. Their
entries are stored as JSON by `LayeredDocument`, which knows only `id`, `hidden`, `locked` and the
layer, and merges built-in, site and user entries with `LayeredEntries`:

| Document | Layers | Notes |
|---|---|---|
| `hp.protocols.json`, entries `protocols` | built-in (later), site, user | the site ships the folder `config/hanging-protocols/`, one protocol per file |
| `hp.sessions.json`, entries `sessions` | user | private: never packaged, never mirrored |

A site document can be a folder (`SiteDocuments.declareFolder(name, folder, entries)`): each
`*.json` file in it is one entry, whose `id` defaults to the file name, or a document with an
array of entries. `SiteDocuments.read(name)` merges the folder into one document, on top of a
`config/<name>` file when there is one. A file it cannot read, or with a newer `schema`, is
skipped and logged. Only `schema` is read from an entry file: an entry may have a `version` of
its own.

An entry lists the entries of other documents it uses, by id. The writer of the entry keeps the
list up to date:

```json
{"id": "user.ct-chest", "references": {"windowPresets": ["user.lung"], "colormaps": ["hot"]}}
```

The registries tell `DocumentStore.registerIds(documentId, ids)` the ids they know. This covers
window presets, color maps, measurement profiles, masking and protocols. Those ids are what the
package checks references against.

### Settings packages

`SettingsPackage` copies a layer into one zip, to restore it, to move to another workstation, or to
give a configuration to Viewer Hub:

| Layer | Content |
|---|---|
| `user` (default) | `settings/<group>.json`, `documents/<name>.json`, `launcher/weasis.properties` (the person's launch values, without the log files, the screen scale and the notices already shown) |
| `user` + machine | the above and `machine/settings/…`, `machine/documents/…` (the same person on a new workstation) |
| `machine` | `machine/settings/…`, `machine/documents/…` |
| `site` | the user documents as `config/<name>.json`, the layout of the `config` folder of a resources package; a document shipped as a folder is split into `config/<folder>/<id>.json` |

`manifest.json` gives `kind: "weasis.package"`, the schema, the creation instant, the source
(user, profile, host, Weasis version), the layer, and every file with its id, schema, `updated` and
SHA-256. Secrets and private documents (the sessions) are never packaged, and an import skips a
private document.

The manifest also lists every reference of the packaged entries. For each one it gives where the
reference resolved when the package was written:

- `package`: the entry is in the package;
- `site`: in a site document;
- `built-in`: known to a registry;
- `missing`: the registry does not know it;
- `unchecked`: no registry tells.

`config:pack` returns the missing ones. An import warns about each reference found neither in the
package nor on the target machine, without blocking the import.

An import checks the manifest and the checksums, plans every file (entries or values added,
replaced, unchanged, or skipped because the site locks them) and, unless it is a dry run, saves a
backup package of the current settings in `<prefDir>/backups/` before applying: documents merged
by entry id or replaced whole, settings groups merged by key or replaced whole, launch values
always merged. A site package is not imported; its `config` folder goes into the resources
package.

Surfaces: **Export settings…** and **Import settings…** in the preference dialog, and the verbs
`config:pack [--layer user|machine|site] [--with-machine] [--out <file>]` and
`config:unpack --file <file> [--replace] [--dry-run]`.

### Mirror to the preference service

With `weasis.pref.sync` on (the 4.8 name `weasis.pref.sync.documents` is still read), a
preference service in `weasis.pref.url`, and a session allowed to store remotely
(`weasis.pref.local.session`, `weasis.pref.store.local.session`), `RemoteSettings` mirrors the
user layer — the settings groups and the user documents — to the service. The machine store and
the secrets never leave the workstation; the launch values keep their own exchange
(`text/x-java-properties`).

- One module per file, `text/plain`, `module=<kind>/<id>@<schema>`: `settings/weasis-core@1`,
  `documents/windowPresets@1`. The schema in the name keeps clients of another schema apart. A
  private document is neither sent nor taken from the service. The
  service has no listing, so the module `index@1` lists the modules written. A body is compact
  JSON with every non-ASCII character escaped: the service returns `text/plain` without a charset.
- At start, before any plugin reads its settings, the index and its modules are read in parallel
  within a deadline and merged into the local files: a settings group key by key, the newer file
  (by `updated`) winning a key changed on both sides; a document entry by entry, the newer
  document on top. Keys and entries present on one side only are kept, so a deletion can come
  back from another workstation. When the service is unreachable the local files are used.
- A change is sent about three seconds after it is saved, and at exit: every file whose content
  differs from what the service last had (checksums in `<prefDir>/sync-state.json`), then the
  index.
- `config:sync [--pull]` does the same on demand; it needs the network capabilities of the command
  policy.

 New code declares typed settings
on top of these nodes or of the other stores, see [Settings.md](Settings.md).

## Preference pages

Preference UI pages are pf4j extensions: a `PreferencesPageFactory` annotated with
`@Extension` (and `@SingletonExtension`) is discovered through the plugin manager by
`PreferenceDialog`, which places each page according to the factory's component position
band and honours the per-factory enable flag from the system preferences.

A page is built when it is first shown, not when the dialog opens. A factory makes that possible by
overriding `describe()` to return the title and the position of its page (`PageDescriptor`) without
building it; the title must be the one the page gives itself, since `showPage(title)` finds a page
by it. A factory without a description still works: its page is built when the dialog opens.
The first search in the dialog builds the remaining pages, to look into their labels, group
titles and tooltips. Closing the dialog saves the pages that were built
(`closeAdditionalWindow()`); the categories (General, Viewer, DICOM, Draw & Measure) no longer
own their sub-pages, the dialog lists them.
