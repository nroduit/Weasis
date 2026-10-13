# Settings

How a plugin declares a setting, reads and writes it, and how the value in force is chosen. This
is the typed layer over the stores described in [Preferences-Migration.md](Preferences-Migration.md);
the documents (presets, color maps, nodes…) have their own layered registries.

## Declaring

A setting is a constant of a holder class, declared once with its group, key, type, default and
scope:

```java
public final class CoreSettings {
  public static final Setting<Boolean> CONFIRM_CLOSING =
      Setting.builder(SettingType.BOOLEAN, "weasis-core", "weasis.confirm.closing", false)
          .description("Ask for a confirmation before closing the application")
          .declare();
}
```

`declare()` registers the setting in the `SettingCatalog`. A plugin calls
`SettingCatalog.declare(MySettings.class)` at start so that its settings are listed before any
is read; `UICore` does it for `CoreSettings`.

| Part | Meaning |
|---|---|
| group | plugin id or functional area (`weasis-core`, `view2d`…) |
| key | identity in every store, in the catalog and in an export; reuse the existing key when migrating a call site |
| type | `BOOLEAN`, `INTEGER`, `LONG`, `DOUBLE`, `STRING`, `COLOR`, `SettingType.of(MyEnum.class)`; the text form is the one the stores already hold |
| scope | `USER` (follows the person), `MACHINE` (this workstation, never sent), `SITE` (administrator only, never written) |
| backing | where the value is stored during the transition: `weasis.properties` (user default), `persistence.properties` (machine default) or a node of the group in the user settings store (`Backing.preferenceNode(path)`, the file `settings/<group>.json` of the preference directory, see [Preferences-Migration.md](Preferences-Migration.md)) |
| `notLockable()` | the site cannot lock it |
| `secret()` | never exported, logged or shown; no default in the catalog |
| `contextual()` | a hanging protocol or session may override it while active |

## Reading and writing

```java
boolean confirm = Settings.get(CoreSettings.CONFIRM_CLOSING);
int height = Settings.get(CoreSettings.FOOTER_HEIGHT, () -> GuiUtils.getScaleLength(350));
Settings.set(CoreSettings.CONFIRM_CLOSING, true);   // false when refused (site setting, locked)
Settings.reset(CoreSettings.CONFIRM_CLOSING);       // back to the site value or the default
Settings.addListener(CoreSettings.CONFIRM_CLOSING, value -> ...);
```

A write changes the store in memory; the store is saved to disk by its owner as before
(`UICore.saveSystemPreferences()`, exit).

## Resolution

The first layer that holds a valid value wins:

1. **context** — `Settings.applyContext(id, values)` for contextual settings, in memory, until the
   returned `Context` is closed; contexts stack, the last applied wins;
2. **stored** — the user or machine store of the setting's scope;
3. **site** — a Java system property, else the launch configuration (`base.json` with the Viewer
   Hub overrides);
4. **default** — the declaration.

A value that does not parse is logged and skipped. The site locks settings by listing their keys
in `weasis.settings.locked` (comma separated): the context and stored layers are then skipped and
writes are refused. `Settings.resolve(setting)` returns the value with its origin and lock.

## Commands

| Verb | Result |
|---|---|
| `config:catalog [--out <file>]` | every declared setting with type, default, scope, store and flags (`"kind": "weasis.catalog"`) |
| `config:setting <key>` | value in force, origin (`context`, `user`, `machine`, `site`, `default`) and lock |
