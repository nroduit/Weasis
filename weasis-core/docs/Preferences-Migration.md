# Preferences on the pf4j branch

This document explains how preferences changed on the `4.7-pf4j` branch (which replaces the
Apache Felix / OSGi framework with [pf4j](https://pf4j.org/)) compared to `master`, and how
preferences are now loaded and stored.

## TL;DR

| Aspect            | `master` (OSGi)                                                    | `4.7-pf4j`                                              |
| ----------------- | ----------------------------------------------------------------- | ------------------------------------------------------ |
| Preferences API   | `org.osgi.service.prefs.Preferences` + `PreferencesService`       | JDK `java.util.prefs.Preferences`                      |
| Backing store     | Custom `StreamBackingStoreImpl` (Felix `BackingStore`, single XML) | JDK preferences store + XML import/export via file     |
| Identity key      | `BundleContext` / bundle symbolic name                            | `String pluginId` (from `WeasisPluginManager`)         |
| Page discovery    | `BundleContext.getServiceReferences(...)`                        | `PluginManager.getExtensions(PreferencesPageFactory.class)` |
| Factory wiring    | `@org.osgi.service.component.annotations.Component`               | `@org.pf4j.Extension` + `@SingletonExtension`          |
| Property map type | `java.util.Hashtable<String,Object>`                             | `java.util.Map<String,Object>`                         |

The removed class `weasis-core/.../internal/StreamBackingStoreImpl.java` (and its test) was the
OSGi-specific glue that made Felix persist the whole preferences tree to a single stream and sync it
to the remote preferences service. That responsibility now lives in `BundlePreferences` itself.

## 1. Why it changed

On `master`, preferences went through the OSGi Preferences **service**: a bundle asked its
`BundleContext` for the `PreferencesService`, and Felix's preferences implementation persisted the
tree through our `StreamBackingStoreImpl` backing store. With OSGi gone, there is no service registry
and no `BundleContext`, so:

- The **standard JDK** `java.util.prefs.Preferences` API replaces the OSGi one.
- A **plugin id** (`String`) replaces `BundleContext` as the thing that scopes a preferences subtree.
- The custom backing store is deleted; load/store logic (including the remote sync that
  `StreamBackingStoreImpl` used to do) is folded into `BundlePreferences`.

## 2. Node layout and identity

Every plugin gets its own subtree under the JDK user root:

```
userRoot()  ->  org/weasis/<pluginId>
```

See `BundlePreferences`:

```java
private static final String PREFERENCES_NODE = "org/weasis";

public static Preferences getUserPreferences(String pluginId, String name) {
  if (pluginId != null) {
    Preferences root = Preferences.userRoot();
    return root.node(PREFERENCES_NODE + "/" + pluginId);
  }
  return null;
}

public static Preferences getDefaultPreferences(String pluginId) {
  return getUserPreferences(pluginId, AppProperties.WEASIS_USER);
}
```

The `pluginId` is resolved from the class that owns the preferences, via the new pf4j plugin manager:

```java
String plugInId = GuiUtils.getUICore().getPluginManager().getPluginId(this.getClass());
Preferences prefs = BundlePreferences.getDefaultPreferences(plugInId);
```

`WeasisPluginManager.getPluginId(Class<?>)` looks up which plugin loaded the class and returns its id
(`WeasisPlugin.getPluginId()`); it returns `null` for classes not owned by a plugin. Core code that
is not plugin-scoped uses the constant `AppProperties.CORE_PLUGIN_ID` (`"weasis-core"`) instead —
e.g. `UICore`, `BundleTools`. When `pluginId` is `null`/blank, data-folder helpers fall back to the
core plugin id.

### Data folders

The file-based helpers took the same `BundleContext -> String pluginId` change and now always create
the directory:

```java
BundlePreferences.getDataFolder(String pluginId);                 // ~/.weasis/data/<pluginId>
BundlePreferences.getFileInDataFolder(String pluginId, String filename);
```

`persistence.properties` (the `WProperties localPersistence` store used for monitor pitch, etc.)
lives in the core plugin's data folder.

## 3. How preferences are loaded

There are two layers.

### 3a. Node-based load (the common path)

Most callers just read from the JDK-backed node tree and apply values onto their in-memory settings.
Example wiring in `UICore.loadPreferences()`:

```java
Preferences prefs = BundlePreferences.getDefaultPreferences(AppProperties.CORE_PLUGIN_ID);
AbstractInfoLayer.applyPreferences(prefs);
MeasureTool.viewSetting.initMonitors(localPersistence);
MeasureTool.viewSetting.applyPreferences(prefs);
```

Each settings class navigates to its own child node and reads typed values with defaults — see
`ViewSetting.applyPreferences(Preferences)`, which now splits the old monolithic method into small
per-node helpers (`applyFontPreferences`, `applyDrawingPreferences`, `applyStatisticsPreferences`,
`applyMeasurementLabels`) reading from `view2d.default/{font,drawing,statistics}` and per-graphic
child nodes. The **key names and node structure are unchanged**, so existing stored values keep
working; only the `Preferences` type (`java.util.prefs` instead of `org.osgi.service.prefs`) differs.

`ViewSetting.initMonitors` also changed signature — it now takes the `WProperties localPersistence`
explicitly instead of reaching through `GuiUtils.getUICore().getLocalPersistence()`, so monitor
scale factors are read from the passed-in store.

### 3b. File + remote merge load

`BundlePreferences.load(String pluginId, Path localFile)` is the richer entry point that reconstructs
what the OSGi backing store used to do:

1. `loadFromFile` — if `localFile` exists, `Preferences.importPreferences(InputStream)` imports the
   JDK XML dump into the tree, then returns the plugin's node.
2. `loadFromService` — if `weasis.pref.url` is configured (and the session policy allows it, see
   §5), it builds `"<url>?user=&profile=&module=<pluginId>"`, opens a `ClosableURLConnection`, and
   imports the remote XML. HTTP `204 No Content` means "nothing stored remotely".
3. `mergePreferences(local, remote)` — recursively copies remote keys/child nodes onto local;
   **remote values win** on conflict.

## 4. How preferences are stored

### 4a. Node-based store (the common path)

Settings classes write typed values back into their node (via the `BundlePreferences.putXxxPreferences`
helpers, which no-op on `null` key/value), then the owner flushes. `UICore.savePreferences()`:

```java
Preferences prefs = BundlePreferences.getDefaultPreferences(AppProperties.CORE_PLUGIN_ID);
AbstractInfoLayer.savePreferences(prefs);
MeasureTool.viewSetting.savePreferences(prefs);
prefs.sync();          // JDK flush to the backing store
```

`ViewSetting.savePreferences` mirrors the load side with `saveFontPreferences` /
`saveDrawingPreferences` / `saveStatisticsPreferences` / `saveMeasurementLabels` helpers, and
serializes measurement labels as `"<id>:<0|1>,..."` exactly as before.

### 4b. File + remote store

`BundlePreferences.save(Preferences prefs, Path file)`:

1. `FileUtil.prepareToWriteFile(file)` then `prefs.exportSubtree(OutputStream)` + `prefs.flush()`
   writes the plugin subtree to `file` as JDK preferences XML.
2. `saveToRemoteService` — if `weasis.pref.url` is set and the session policy allows it, it POSTs the
   file bytes to `"<url>?user=&profile=&module=<pluginId>"` and reads the response. The `pluginId` is
   recovered from the node path with `extractPluginIdFromPreferences` (parses `/org/weasis/<pluginId>`,
   falling back to `CORE_PLUGIN_ID`).

## 5. Remote-session policy

Both remote paths are gated the same way (unchanged behavior, just moved into `BundlePreferences`):

```java
if (GuiUtils.getUICore().isLocalSession() && !GuiUtils.getUICore().isStoreLocalSession()) {
  return;   // do not read/write the remote preferences service
}
```

- `isLocalSession()` = `weasis.pref.local.session`
- `isStoreLocalSession()` = `weasis.pref.store.local.session`
- Remote URL = `weasis.pref.url`

`URLParameters` use `Content-Type: text/plain; charset=UTF-8` for POST and `Accept: text/plain`
for GET.

> **Charset note (carried over from `master`).** The POST `Content-Type` **must** advertise
> `charset=UTF-8`. `Preferences.exportSubtree` writes the payload as UTF-8, but the HTTP default
> charset for `text/*` is ISO-8859-1, so a bare `text/plain` lets the server mangle non-ASCII
> content — the "preference corruption with remote service" bug fixed on `master` in commit
> `53002f41a`. That commit lived in the now-deleted `StreamBackingStoreImpl`, so the fix was
> re-applied to `BundlePreferences.getURLParameters`. The commit's other half — guarding against
> invalid XML element names — is **not** ported: it was specific to the old hand-rolled XML writer
> that used node names / keys as element tags. The JDK `importPreferences`/`exportSubtree` format
> stores them as escaped attribute values validated against the JDK DTD, so that corruption vector
> no longer exists.

## 6. Preference *pages* discovery (`PreferenceDialog`)

Preference UI pages are pf4j **extensions** instead of OSGi services.

`PreferencesPageFactory.createInstance` now takes `Map<String,Object>` instead of `Hashtable`
(and `PreferenceDialog` builds a `HashMap`). Each factory is annotated:

```java
@Extension                    // org.pf4j
@SingletonExtension           // org.weasis.plugin
public class ViewerPrefFactory implements PreferencesPageFactory { ... }
```

`PreferenceDialog.initializePages()` enumerates them through the plugin manager:

```java
PluginManager pluginManager = GuiUtils.getUICore().getPluginManager();
for (PreferencesPageFactory factory : pluginManager.getExtensions(PreferencesPageFactory.class)) {
  ...
}
```

The page-placement rules (component-position bands 500–600 viewer, 600–700 dicom, 700–800 draw, else
general; `>= 1000` becomes a top-level page) and the per-factory enable check against
`getSystemPreferences().getProperty(factory.getClass().getName())` are unchanged.

The same OSGi→pf4j swap applies to every other `*PrefFactory` (`Viewer3dPrefFactory`,
`DicomNodePrefFactory`, `MprPrefFactory`, `DicomExplorerPrefFactory`, …).

## 7. Migrating a fleet to Weasis 5 (client & server)

The 4.x → 5 jump changes the **on-the-wire XML format** of the stored preferences blob, because the
serializer changed from the Felix `StreamBackingStoreImpl` (hand-rolled XML) to the JDK
`java.util.prefs` format (`Preferences.exportSubtree` / `importPreferences`). The two formats are
**not interconvertible**:

| | 4.x (Felix `StreamBackingStoreImpl`) | 5 (JDK `java.util.prefs`) |
| --- | --- | --- |
| Root | `<preferences>` | `<!DOCTYPE preferences SYSTEM "http://java.sun.com/dtd/preferences.dtd">` then `<preferences EXTERNAL_XML_VERSION="1.0">` |
| Node | element **named after the node** (`<view2d.default>`) | `<root type="user">` / `<node name="view2d.default">` |
| Key / value | element **named after the key**, value = text | `<map><entry key="…" value="…"/></map>` |

Feeding a 4.x blob to `Preferences.importPreferences` throws `InvalidPreferencesFormatException`
(missing DOCTYPE + wrong structure). In `loadFromFile` / `loadFromRemoteConnection` that exception is
**caught and swallowed**, so the default 4.x → 5 behavior is a **silent reset to defaults** — no crash,
but the user loses their stored preferences unless a conversion step is added.

### 7a. Client side

1. **Format is automatic going forward** — 5 always reads/writes the JDK format; no per-call work.
2. **Existing data is not migrated.** Choose one:
   - **Accept the reset** (simplest — call it out in the release notes), or
   - **Add a one-shot converter**: on first 5 launch, detect an old-format local file (root
     `<preferences>` with no DOCTYPE), parse it with the old StAX reader, replay the keys into the
     JDK `Preferences` node, then `BundlePreferences.save(...)` re-exports it in the new format. The
     deleted `StreamBackingStoreImpl.read(...)` is the reference reader to salvage for this.
3. **Confirm the `module=` key is stable.** The server file is keyed by `module=<id>`. 4.x used the
   OSGi **bundle symbolic name**; 5 uses the **pf4j plugin id** (`getPluginId(class)`, core =
   `weasis-core`). If those strings differ for any module, its old server data is orphaned regardless
   of format — verify the mapping, or have the converter translate old ids → new ids.
4. `persistence.properties` (the `WProperties` local store) is a plain `.properties` file — **unaffected**.

### 7b. Server side

The preferences service stores and serves the payload **opaquely** (keyed by `user` / `profile` /
`module`) and does not parse it, so it is largely format-agnostic. Three things still matter:

1. **Preserve the DOCTYPE byte-for-byte.** JDK import is a *validating* parse
   (`DocumentBuilderFactory.setValidating(true)`), so it requires the
   `<!DOCTYPE preferences SYSTEM "http://java.sun.com/dtd/preferences.dtd">` line. If a WAF, XXE
   hardening, or XML re-serialization on the server strips or rewrites the DOCTYPE, 5 clients can no
   longer import. The JDK resolves that URI to a **built-in** DTD through a local `EntityResolver`, so
   the server never has to serve `java.sun.com` and no outbound fetch happens — it only has to leave
   the declaration intact.
2. **Honor the request charset.** POST now sends `text/plain; charset=UTF-8` (see §5); the server must
   not force ISO-8859-1.
3. **Isolate 5 blobs from 4.x during a mixed rollout.** If a 4.x and a 5 client share the same
   `(user, profile, module)` slot they will hand each other unreadable XML — and a 4.x client can
   overwrite the 5 file with old-format data. Mitigate by **segmenting the storage key** for 5 (bump
   `profile`, add a schema-version suffix, or use a new endpoint). Optionally run a **server-side batch
   conversion** of stored files if preferences should be carried forward rather than reset.

### 7c. Bottom line

- **Client:** format is handled automatically; the real work is a *migration decision* (reset vs.
  one-shot converter) plus confirming `pluginId == old bundle symbolic name` for the `module` key.
- **Server:** no parsing changes, but it must preserve the DOCTYPE, honor UTF-8, and keep 5's blobs
  separate from 4.x's to avoid mutual corruption during rollout.

## 8. Files touched

- `weasis-core/.../api/service/BundlePreferences.java` — core rewrite (JDK prefs, file + remote
  load/save/merge, `String pluginId`).
- `weasis-core/.../internal/StreamBackingStoreImpl.java` **(+ test)** — deleted.
- `weasis-core/.../api/gui/PreferencesPageFactory.java` — `Hashtable` → `Map`.
- `weasis-core/.../ui/pref/PreferenceDialog.java` — page discovery via `PluginManager`.
- `weasis-core/.../ui/pref/ViewSetting.java` — refactored load/save + `initMonitors(WProperties)`.
- `weasis-core/.../ui/pref/ShortcutPrefView.java`, `ZoomSetting.java` — `pluginId` + JDK prefs import.
- `weasis-dicom/**`, `weasis-acquire/**`, `weasis-base/**` `*PrefFactory` / `EventManager` /
  `View*Container` / `AuthenticationPersistence` / `AbstractDicomNode` — same `BundleContext →
  pluginId` and `@Component → @Extension` migration.

> Note: `ConfigData.java` and the new `PlatformDirectories.java` (XDG base-directory support) also
> changed on this branch, but those concern *where* `~/.weasis` resolves, not the preferences
> load/store mechanism described here.
