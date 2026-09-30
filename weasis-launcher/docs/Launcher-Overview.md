# Weasis Launcher Overview

## Table of Contents
- [Introduction](#introduction)
- [Startup Sequence](#startup-sequence)
- [Launch Pipeline](#launch-pipeline)
- [Class Loaders](#class-loaders)
- [Configuration Sources and Precedence](#configuration-sources-and-precedence)
- [Base Directories](#base-directories)
  - [Per-OS Layout](#per-os-layout)
  - [How the Directories Are Resolved](#how-the-directories-are-resolved)
  - [System Properties](#system-properties)
  - [Migration from `~/.weasis`](#migration-from-weasis)
  - [Portable Mode and Explicit Overrides](#portable-mode-and-explicit-overrides)
- [Single-Instance Handling](#single-instance-handling)
- [Logging](#logging)

## Introduction

`weasis-launcher` is a **plain JAR**, not a plugin. It is the entry point that bootstraps
the application before any plugin is started:

- `org.weasis.launcher.AppLauncher` (extends `WeasisLauncher`) is the `main` class.
- It starts the **PF4J** plugin manager (`WeasisPluginManager`), which **replaces the former
  Apache Felix OSGi framework**, then loads and starts the plugins declared in the
  configuration.
- Because it runs first and outside the plugin framework, the launcher owns everything that
  must be decided *before* plugins exist: configuration loading, logging setup,
  single-instance control, and the resolution of the on-disk directories described below.

> **Configuration keys.** Since 5.0 the configuration uses `weasis.*` names only — the
> historical OSGi/Felix names (`felix.auto.start.N`, `felix.config.properties`,
> `org.osgi.framework.storage`, …) are gone. The plugin entries are `weasis.plugins.N`
> (numeric suffix, filtered by `weasis.plugins.max.level`), the configuration file URL is
> `weasis.config.properties` / `weasis.extended.config.properties`, and the plugin cache is
> `weasis.plugin.cache.dir` / `weasis.plugin.cache.clean`. Server-side deployments must ship
> a configuration in this format.

## Startup Sequence

1. **AOT relaunch check** (packaged app only) — `AotCache.relaunch` re-executes the native
   launcher with the JDK AOT cache flags (see [Launch Pipeline](#launch-pipeline)); the
   re-executed child performs all the following steps.
2. **Boot logging** — a static initializer in `AppLauncher` configures a temporary Logback
   appender (`boot.log`) so that early failures are captured before the real logging
   configuration is available.
3. **Configuration loading** — `ConfigData` reads launch arguments, Java system properties,
   the shipped/remote `base.json`, and any profile-specific JSON, then performs variable
   substitution.
4. **Base directory resolution** — the per-OS config/data/cache/state directories are
   resolved and published as system properties (see [Base Directories](#base-directories)).
5. **Single-instance check** — `Singleton` decides whether to hand the arguments to an
   already-running instance or to start a new one.
6. **Plugin framework start** — the PF4J plugin manager loads and starts the plugins
   (`loadPlugins()` / `startPlugins()`).

## Launch Pipeline

How code reaches the JVM differs between development and the packaged application:

- **Development (IDE)** — `AutoProcessor` copies the `weasis.plugins.N` jars from the local
  Maven repository into the per-user cache; non-plugin jars land in `common-lib/` and
  `WeasisPluginManager.loadCommonLibraries()` publishes them through the common library
  classloader (see below). No AOT cache is used in dev.
- **Packaged application** — the packaging step splits the bundle jars: jars **without** a
  `Plugin-Id` manifest header (weasis-core, weasis-core-img, …) ship
  uncompressed in `$APPDIR/common-lib/` and jpackage puts them on the **application
  classpath**, so they live in the system classloader from process start; pf4j plugins stay
  `.jar.xz` in `$APPDIR/bundle/`. Only a **remote codebase** launch (`cdb=` parameter) whose
  server ships libraries that are not in the app image needs the common library classloader.
- **AOT cache (JEP 483/514/515)** — on Windows and Linux packaged launches, `Bootstrap.main`
  (a dependency-free entry point that runs before `AppLauncher` and its static initializers)
  calls `AotCache.relaunch`. The first launch after an install or update re-executes the native
  launcher with `-XX:AOTCacheOutput` to train a per-user cache; once a trained cache has been
  used successfully, the `-XX:AOTCache` option is persisted in the launcher `.cfg` so that later
  launches start in a single process with the cache active. The cache lives in the per-OS cache
  directory under `aot/` and is keyed by the application version and the runtime image, so an
  update trains it again. Classpath jars are what the cache reliably serves; classes from the
  common library and plugin classloaders are served only while their jar is unchanged since
  training. Every failure falls back to a normal launch, and a cache that crashes the JVM at
  startup is discarded and disabled for that build, so a bad cache never blocks the
  application. Disable with `-Dweasis.aot.cache=false` (the Dicomizer launcher does); macOS is
  excluded for now.
- **PERF markers** — `*PERF*` lines in `boot.log` (JVM start, AOT cache mode, relaunch
  overhead, plugin start, global starting) measure the pipeline across cold and warm launches.

## Class Loaders

```
AppClassLoader          weasis-launcher.jar + $APPDIR/common-lib/*.jar (packaged app)
  └── CommonLibClassLoader    shared libraries downloaded at runtime into <cache>/common-lib/
        └── WeasisPluginClassLoader   one per pf4j plugin (parent-last: plugin → deps → parent)
```

`WeasisPluginManager.loadCommonLibraries()` is called once, after `AutoProcessor` has
downloaded the jars and before `loadPlugins()`. It adds every jar of `<cache>/common-lib/` to
`CommonLibClassLoader` — skipping the file names already on the application classpath, so a
library shipped in the app image keeps being loaded by the `AppClassLoader` and stays
AOT-cacheable — and sets it as the context classloader of the main thread.

Delegation to the parent is the standard parent-first one, so a classpath jar always wins over
a downloaded copy of the same library. Consequently the launcher's own classes never see the
downloaded libraries: everything the launcher needs before this point (`weasis-core-img`, …)
must be on the classpath, which the packaging step guarantees.

## Configuration Sources and Precedence

Configuration values are resolved with the following priority (highest first):

1. Last persisted user value (`weasis.properties`, absent on first launch).
2. Java system property (`-Dkey=value`).
3. Value defined in `base.json` (or a profile JSON file).
4. Built-in default.

`base.json` exists in two copies that **must be kept in sync**:

- `weasis-distributions/etc/config/base.json` — shipped with the native distribution
  (Maven-filtered, so `$` is escaped as `${dollar}`).
- `weasis-launcher/conf/base.json` — used when running from the IDE / development (not
  Maven-filtered; uses `${...}` directly and a `-dev` cache suffix).

Variable substitution (`AppPreferences.substVars`) resolves `${name}` first against the
preferences and then against Java system properties. This is why the launcher publishes the
base directories as system properties *before* substitution: it lets `base.json` reference
them (e.g. the plugin cache uses `${weasis.cache.dir}/cache`).

## Base Directories

Historically Weasis stored everything under a single `~/.weasis` directory on every
platform. The launcher now places files in the conventional per-OS locations, following the
[XDG Base Directory Specification](https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html)
on Linux and the native conventions on macOS and Windows.

Four independent categories are used:

| Category | Contents | Internal layout |
|----------|----------|-----------------|
| **config** | User configuration: preferences (`weasis.properties`), proxy settings | `preferences/<user>/<profile>/` |
| **data** | User data: per-plugin data folders, downloaded resources | `data/<plugin>/`, `data/resources/<sourceId>/` |
| **cache** | Regenerable, non-essential data: the PF4J plugin cache | `cache/` |
| **state** | State data: logs and the single-instance lock | `log/`, `singleton/` |

### Per-OS Layout

| Category | Linux (XDG) | macOS | Windows |
|----------|-------------|-------|---------|
| config | `$XDG_CONFIG_HOME/weasis` → `~/.config/weasis` | `~/Library/Application Support/Weasis` | `%APPDATA%\Weasis` |
| data | `$XDG_DATA_HOME/weasis` → `~/.local/share/weasis` | `~/Library/Application Support/Weasis` | `%APPDATA%\Weasis` |
| cache | `$XDG_CACHE_HOME/weasis` → `~/.cache/weasis` | `~/Library/Caches/Weasis` | `%LOCALAPPDATA%\Weasis` |
| state | `$XDG_STATE_HOME/weasis` → `~/.local/state/weasis` | `~/Library/Logs/Weasis` | `%LOCALAPPDATA%\Weasis` |

Any other platform falls back to the historical single `~/.weasis` root.

### How the Directories Are Resolved

`org.weasis.pref.PlatformDirectories` is a pure static utility (no dependency on
`ConfigData`) so it can also be used during boot logging. For each category it applies, in
order:

1. On Linux, the matching `XDG_*` environment variable — **only if it holds an absolute
   path** (relative values are ignored, as the specification requires).
2. The OS default from the table above.

On Linux the `XDG_*` variables are honored when the user has customized them; otherwise the
spec defaults apply.

The four categories cannot be relocated individually: the `weasis.*.dir` properties listed
below are **outputs** of the resolution, not inputs. The only way to override the layout is
to redirect all four categories to a single root, as described in
[Portable Mode and Explicit Overrides](#portable-mode-and-explicit-overrides).

### System Properties

The launcher publishes the resolved roots as system properties, consumed by the rest of the
application (notably `org.weasis.core.api.gui.util.AppProperties`):

| Property | Meaning |
|----------|---------|
| `weasis.config.dir` | config root |
| `weasis.data.dir` | data root |
| `weasis.cache.dir` | cache root (the plugin cache is `${weasis.cache.dir}/cache`) |
| `weasis.log.dir` | state root (logs are `${weasis.log.dir}/log`) |
| `weasis.path` | **backward-compatible alias of the data root** |

`weasis.path` is retained because external scripts and older integrations read it; it now
points at the data directory. It is also the only one of these properties that is read back
as an input — when set explicitly it becomes the single root for all four categories (see
below).

### Migration from `~/.weasis`

On the first launch with the new layout, `PlatformDirectories.migrateLegacyLayout` performs
a one-time migration when a legacy `~/.weasis` directory exists:

- `~/.weasis/preferences` → config root
- `~/.weasis/data` → data root
- `~/.weasis/log` → state root
- `~/.weasis/cache` is **deleted** (it is regenerable, so it is not worth copying)

The move merges into the destination without overwriting files that already exist there. A
`.xdg-migrated` marker file is written in `~/.weasis` so the migration runs **at most once**.

### Portable Mode and Explicit Overrides

The XDG/native resolution is **bypassed** in two cases, keeping a single self-contained root
(the historical `~/.weasis`-style layout) and skipping migration:

- **Portable mode** — when `weasis.portable.dir` is defined (e.g. launched from removable
  media). All categories share one root.
- **Explicit override** — when `weasis.path` is set explicitly (e.g. by an administrator or
  installer). The provided path becomes the single root for all categories.

## Single-Instance Handling

`Singleton` implements single-instance behavior over a local socket. The port/lock file is
stored in the **state** directory (`<state>/singleton`). A second launch forwards its
arguments to the running instance instead of starting a new JVM, provided the user and
configuration hashes match.

## Logging

Two log destinations are produced under the **state** directory:

- `log/boot.log` — written by the `AppLauncher` static initializer before configuration is
  loaded. In portable mode this early file still lands in the resolved state directory
  rather than next to the executable, because the configuration (and thus the portable root)
  is not known yet at that point.
- `log/default.log` — the main rolling application log, enabled through the
  `org.apache.sling.commons.log.*` properties.
