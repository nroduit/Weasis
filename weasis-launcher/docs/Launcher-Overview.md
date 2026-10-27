# Weasis Launcher Overview

## Table of Contents
- [Introduction](#introduction)
- [Startup Sequence](#startup-sequence)
- [Bootstrap and Launcher Set](#bootstrap-and-launcher-set)
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
- [Commands and the Command Bridge](#commands-and-the-command-bridge)
- [Logging](#logging)

## Introduction

Two plain JARs, neither a plugin, start the application:

- `weasis-bootstrap` is **stage 1** of a packaged launch: the only jar on the class path of
  the native launchers (`Weasis`, `Dicomizer`, `weasis-cli`). `org.weasis.bootstrap.Bootstrap`
  depends on the JDK alone, checks the single instance, resolves and verifies the **launcher
  set**, then starts stage 2 with that set on its class path. It is what the installer freezes;
  everything else updates with the package.
- `weasis-launcher` is **stage 2**, the application: `org.weasis.launcher.AppLauncher`
  (extends `WeasisLauncher`), reached through a per-application entry class (`Weasis`,
  `Dicomizer`) or directly from an IDE. It starts the **PF4J** plugin manager
  (`WeasisPluginManager`), which **replaces the former Apache Felix OSGi framework**, then
  loads and starts the plugins declared in the configuration. Because it runs before any
  plugin, it owns configuration loading, logging setup, the single-instance server and the
  resolution of the on-disk directories described below.

> **Configuration keys.** Since 5.0 the configuration uses `weasis.*` names only — the
> historical OSGi/Felix names (`felix.auto.start.N`, `felix.config.properties`,
> `org.osgi.framework.storage`, …) are gone. The plugin entries are `weasis.plugins.N`
> (numeric suffix, filtered by `weasis.plugins.max.level`), the configuration file URL is
> `weasis.config.properties` / `weasis.extended.config.properties`, and the plugin cache is
> `weasis.plugin.cache.dir` / `weasis.plugin.cache.clean`. Server-side deployments must ship
> a configuration in this format.

## Startup Sequence

1. **Stage 1** (packaged app only) — the bootstrap offers the activation to a running
   instance, resolves the launcher set and the stage-2 JVM options, and spawns stage 2 (see
   [Bootstrap and Launcher Set](#bootstrap-and-launcher-set)). A development launch starts at
   step 2.
2. **Boot logging** — a static initializer in `AppLauncher` configures a temporary Logback
   appender (`boot.log`) so that early failures are captured before the real logging
   configuration is available.
3. **Contract check** — `BootstrapContract` refuses a bootstrap outside the protocol range
   this launcher speaks, with the reserved exit code and a one-line reason.
4. **Configuration loading** — `ConfigData` reads launch arguments, Java system properties,
   the shipped/remote `base.json` (from the file stage 1 fetched when it is the same URI), and
   any profile-specific JSON, then performs variable substitution. Started by the bootstrap,
   it re-checks the facts the package requires (`weasis.requires.jdk`, `.modules`,
   `.bootstrap`) against the JVM it runs in.
5. **Base directory resolution** — the per-OS config/data/cache/state directories are
   resolved and published as system properties (see [Base Directories](#base-directories)).
6. **Single-instance check** — `Singleton` decides whether to hand the arguments to an
   already-running instance or to start a new one.
7. **Plugin framework start** — the PF4J plugin manager loads and starts the plugins
   (`loadPlugins()` / `startPlugins()`); the launcher then prints `READY` for stage 1.

## Bootstrap and Launcher Set

The native image holds the JDK runtime, `weasis-bootstrap.jar` and an **embedded launcher
set**: `$APPDIR/launcher/*.jar.embedded` (the launcher jar and the common libraries, renamed so
that jpackage keeps them off its class path) with its `launcher.sha256` manifest. The package a
hub serves declares its own set with three keys of `base.json`, `weasis.launcher.url`,
`weasis.launcher.libs` and `weasis.launcher.sha256`, plus the entry class
(`weasis.launcher.main`), the JVM options of stage 2 (`weasis.launcher.jvm-options`, one common
set, one per OS and one for the client) and the facts it requires of the runtime
(`weasis.requires.jdk`, `.modules`, `.bootstrap`). The bootstrap reads exactly these keys, with
`${weasis.codebase.url}` as its only variable; everything else is the launcher's business.

```
jpackage launcher (Weasis, Dicomizer, weasis-cli)
  └── stage 1: weasis-bootstrap.jar, a tiny JVM
        1. single instance: the activation is offered to the running instances of the user
        2. launcher set: <cache>/launcher/<version>-<sha>/ downloaded from the package and verified,
           or $APPDIR/launcher/ as shipped
        3. stage 2: <runtime>/bin/java -cp <set> [AOT flags] [served options] <entry class> args
  └── stage 2: the application, from AppLauncher onwards
```

A launch ends in one of **two states**: the set named by the hub, complete and verified, with
the hub's package; or the embedded set with the installed configuration, which is the installer
exactly as shipped. A set is downloaded into a `.part-<pid>` directory, each jar checked against
the manifest and, when the image carries a truststore (`$APPDIR/launcher/truststore/`), against
the jar signatures; one atomic rename makes it visible. Anything else (offline, a server error,
a digest or signature mismatch, a runtime that lacks a required module, a set that died before
`READY` last time) takes the embedded path, and `$APPDIR` is never written. When the package
declared a set that could not be used, stage 2 is told so (`weasis.bootstrap.fallback`) and
runs the installed configuration rather than combining the embedded launcher with remote
plugins; the user sees why. A package that declares no set (one built before the split) runs as
before: the embedded launcher with the remote configuration.

Stage 1 waits for stage 2 and reads its stdout: `READY` means the plugin manager has started.
An exit before `READY` with a hub set, a served option set or an AOT cache in use is recovered
once, with the embedded set, the embedded options and no cache; the hub set is marked failed
until the hub names another, a crashing cache is discarded and disabled for its key. The
reserved exit code (`BootstrapContract.EXIT_REFUSED`) means stage 2 refused the environment and
is not retried. Options served by the hub are an allow-list (`JvmOptions`): anything that loads
code outside the signed set, an agent, a boot class path, a module path, is refused, since
`base.json` is not signed.

The **single instance** moves to stage 1 for packaged launches: the bootstrap connects to every
instance of the OS user with the raw arguments and what the command line says about the
configuration (user, codebase or configuration service URL, extended configuration, profile).
The application decides: `si.ack` (handled, stage 2 never starts), `si.other` (another
application or configuration), or `si.exit` when its configuration changed since it started,
which it re-reads for the purpose. A `FileChannel` lock serialises concurrent starts until
`READY`. The client launcher (`weasis-cli`) fetches nothing and runs the set the application ran
last, else the embedded one.

On **macOS** stage 1 loads the set in its own process, in a class loader whose parent is the
platform loader, and calls the entry class: LaunchServices keeps one process for the open-file
events. The JVM options of the application are therefore native there, and no AOT cache is
used. In a development launch nothing of this exists: the IDE starts `AppLauncher` directly.

Every decision is one line in `<state>/log/bootstrap.log` (set, id, digest, reason, JDK,
options, AOT mode, restart, exit), echoed to stderr with `-Dweasis.bootstrap.trace=true`.

## Launch Pipeline

How code reaches the JVM differs between development and the packaged application:

- **Development (IDE)** — `AutoProcessor` copies the `weasis.plugins.N` jars from the local
  Maven repository into the per-user cache; non-plugin jars land in `common-lib/` and
  `WeasisPluginManager.loadCommonLibraries()` publishes them through the common library
  classloader (see below). No AOT cache is used in dev.
- **Packaged application** — the launcher set (the launcher jar and the common libraries:
  weasis-core, weasis-core-img, …) is the explicit class path stage 1 gives stage 2, so those
  classes live in the application class loader from process start; pf4j plugins stay `.jar.xz`
  in `$APPDIR/bundle/` or come from the remote codebase. `AutoProcessor` recognises a library
  of the set by its file name and does not download it again; only a library the package
  serves beyond the set goes through the common library classloader.
- **AOT cache (JEP 483/514/515)** — on Windows and Linux packaged launches the bootstrap passes
  the cache flags as plain arguments of stage 2: `-XX:AOTCache` when the per-user cache exists,
  `-XX:AOTCacheOutput` to train it on the first launch with a given launcher set and runtime
  image (`<cache>/aot/<entry>-<set>-<runtime>.aot`). Nothing is persisted in the launcher
  `.cfg`. A cache that crashes stage 2 before `READY` is deleted and disabled for its key. The
  client trains its own cache in a detached process after a call. Disable with
  `-Dweasis.aot.cache=false` (the Dicomizer launcher does); macOS runs in one process and uses
  none.
- **PERF markers** — `*PERF*` lines in `boot.log` (JVM start, AOT cache mode, bootstrap
  overhead, plugin start, global starting) measure the pipeline across cold and warm launches.

## Class Loaders

```
AppClassLoader          the launcher set: weasis-launcher.jar + its libraries (explicit -cp from stage 1)
  └── CommonLibClassLoader    libraries listed in base.json but not in the set, downloaded into <cache>/common-lib/
        └── WeasisPluginClassLoader   one per pf4j plugin (parent-last: plugin → deps → parent)
```

On macOS the set is loaded by a `URLClassLoader` of stage 1 whose parent is the platform
loader, which takes the place of the `AppClassLoader` in this picture; the rest is unchanged.
`WeasisPluginManager.loadCommonLibraries()` is called once, after `AutoProcessor` has
downloaded the jars and before `loadPlugins()`. It adds every jar of `<cache>/common-lib/` to
`CommonLibClassLoader` — skipping the file names already on the launcher's class path, so a
library of the set keeps being loaded by its loader and stays AOT-cacheable — and sets it as
the context classloader of the main thread.
Extensions declared by a shared library (the `workspace:` and `image:` command namespaces of
`weasis-core`, for instance) are discovered through that loader as well: `CommonLibExtensionFinder`
reads the classpath extension index and loads its classes through `CommonLibClassLoader`, so a
library downloaded into the cache contributes its extensions exactly like one shipped in the app
image.

Delegation to the parent is the standard parent-first one, so a jar of the set always wins over
a downloaded copy of the same library. Consequently the launcher's own classes never see the
downloaded libraries: everything the launcher needs before this point (`weasis-core-img`, …)
must be in the launcher set, which the package declares (`weasis.launcher.libs`).

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
arguments to the running instance instead of starting a new JVM: in a packaged launch the
bootstrap does it before stage 2 exists, and the application compares what the command line
says about the instance (user, configuration source, profile) and whether its configuration
changed since it started (`SingletonApp.isSameInstance`, `configurationChanged`); in a
development launch the new process compares the user and the configuration hash itself. The
same socket carries the command bridge described below; each connection is served on its own
virtual thread so a long command never blocks argument forwarding.

## Commands and the Command Bridge

Commands are `namespace:verb` strings (or `namespace verb`) dispatched by `CommandManager` to
the plugin `CommandExtension`s (`org.weasis.core.api.command`). A namespace either exposes a
Picocli command model (`getCommandModel()`), whose subcommands are its verbs, or parses its
verbs itself in `execute`. Every execution returns a `CommandResult` (status, text body, error
text, produced files) and runs under a `CommandContext` that carries the `CommandOrigin`
(launch arguments, `weasis://` URI or remote) and collects the files a verb writes. Picocli
verbs get `--help` automatically, the shared `OutputOptions` mixin provides `--json`, and verbs
that write a file take `--out` and report the path; JSON output is the default for remote calls.

The **command bridge** lets a local process, typically the `weasis-cli` launcher shipped next to
`Weasis`, run a command in the running instance: `CommandClient` reads the singleton file,
connects with the secret, sends the tokens (`CommandProtocol`), and prints the body on stdout,
the error text on stderr and the status as its exit code (0 ok, 1 error, 2 usage, 3 no
instance, 4 timeout, 5 connection, 6 not permitted remotely). Remote calls are subject to a
request timeout (`weasis.commands.remote.timeout.max`) and are logged by `CommandAudit` as JSON
lines under `<state>/audit` with option names only, never values.

What a remote caller may do is the **`CommandPolicy`**, which the instance owns and no argument
can lower. Its namespace part is the former `RemotePolicy` (`weasis.commands.remote.enabled`,
`weasis.commands.remote.namespaces`, plus `CommandExtension.isRemotable()`); the rest is closed by
default: files are written only under the session directory, the temporary directory of the
machine and the user's home, never on a network share, and never over a file the session did not
write unless `--force` is given; the network is refused in both directions until the instance
opens it, with an optional host allowlist and https outside this machine; a page that shows
unmasked identity, such as query/retrieve, is never opened for a remote caller; and stopping the
application, which ends every session it serves, takes an explicit confirmation (`-y`) and can be
forbidden. One system property per key overrides a default
(`weasis.commands.policy.network.read=allow`).

A verb states what it needs with `@Needs(Capability…)` on the verb or on the option that needs
it, and an option typed `InputPath`, `OutputPath` or `RemoteUrl` states it by its type alone. The
gate sits in `CommandModel` once the arguments are parsed, so the values are known: a refusal is
status 6 with a line naming the policy key (`Refused by policy network.read: …`), the verb never
runs, and the audit line carries the key. The capabilities appear in the verb help (`Needs: file
write, network read`), in the Markdown reference and in the JSON descriptor, so a caller knows
what is closed before trying. Only the remote origin is policed: launch arguments and `weasis://`
links are the integration path of the site.

A command that fails answers a remote caller with **one JSON object on the error stream**
(`CommandError`): a stable `code` (`usage`, `invalid-value`, `unknown-handle`, `unknown-command`,
`not-applicable`, `refused-by-policy`, `failed`, `timeout`), the message, and the fields that help
the caller get it right — the synopsis of the verb, the option at fault, the verb that produces the
kind of handle that was not found, the policy key that refused, near-miss spellings, and the
notices of the call. A verb describes its own failure by throwing a `CommandFailure`; otherwise the
code comes from what it threw, and the parse errors of Picocli are mapped where they are raised.
The other origins keep the plain message they have always had, and a command that succeeds keeps
its notices as plain lines.

`help --agent` prints the instructions an agent needs to drive this instance — the workflow, the
coordinate conventions and the rules — filled with the version, the protocol and the namespaces of
the running profile, and without the sections of a namespace the profile does not load
(`AgentSkill`, whose text is a resource of the launcher). A skill folder is a snapshot of it plus
the front matter its host wants; a host that reads its own instruction file only needs the line
that runs the command.

The `script:` namespace runs a list of commands from a file in one call: `run` (with `--set` for
parameters, `--keep-going`, `--delay`, `--from` / `--to`, `--timeout`, `--brief` and `--report` for
JUnit XML), `check` (parses every step against the models without running anything) and `pause`.
It is registered by the `CommandManager` itself, not by a plugin, since the engine is the manager:
each step is the command a caller would have sent, with the same origin, masking and restriction
policies, and one audit line each. `$name = <command>` keeps the body of a step, `${name.path}`
reads inside it, and `@expect` compares a value, which is what turns a script into a test; the
answer is one JSON object per step and a summary, and the status is that of the first failing step.
A script never runs a script. The client sugar is `weasis-cli --script <file>`.

The same socket carries the **event stream**: a second request, `si.sub` (protocol version 2),
holds the connection open and writes one JSON object per line for every change of state the
instance publishes on its event bus (`org.weasis.core.api.event`, next to the command model);
`weasis-cli events` is the client, `events:recent` the poll, and the `events:` namespace is
registered by the `CommandManager` like `script:`. The policy decides whether a remote caller may
observe, which namespaces and how many at once, identity fields are masked like command results,
and each subscription is an audit line. See [Event-Model.md](Event-Model.md).

The built-in `help` is navigable in three levels: `help` lists the namespaces with a one-line
summary, `help <namespace>` the verbs of a namespace, and `help <namespace>:<verb>` (or
`help <namespace> <verb>`) one verb in detail, its synopsis naming it `namespace:verb`. The
first two levels also come as `--names` (bare names) and `--index` (a title line, then one
`name`, tab, `summary` line per entry, rendered by `CommandHelp`); `--markdown` and `--json`
give the whole reference. `--context` keeps only the namespaces relevant for the viewer type
selected now (`CommandExtension.isRelevantFor`, the viewer type supplied by the UI core), the
smaller surface an agent asks for after `ui:context`; a namespace named explicitly always
answers. On the client side, `weasis-cli help` at any level opens an
interactive browser (`HelpBrowser`) when a terminal is attached: entries are chosen by number,
name or unique prefix, Enter goes back and `q` quits. Without a terminal, as in a pipe or from an
agent, the client prints the level and the `weasis-cli help ...` commands that lead down and
back, so the same three levels are usable non-interactively.

### Designing a feature as commands

The command namespaces are the only interface shared by launch arguments, `weasis://` links,
scripts and AI agents, so a feature that has no verbs is invisible to all of them. A new feature
or plugin is therefore designed command-first:

```
 Swing UI (menus, tools) ──►  headless service  ◄── namespace:verb (launch args, weasis://, weasis-cli, agent)
                                    │
                                    └──► state and result objects
```

- a **headless service** holds the logic and the state and has no Swing type in its API;
- a **`CommandExtension`** in the owning plugin exposes the service as Picocli verbs, and only
  does argument parsing, event-thread hopping, identity masking and result formatting;
- the **UI** calls the same service. The UI never calls the verbs, and the verbs never drive the
  UI through synthetic events.

Verbs identify things by the handles other verbs return (view ids from `workspace:list`,
series UIDs from `dicom:list`, graphic ids from `view2d:graphics`) rather than by screen
position or the current selection alone, produce structured JSON with stable keys and explicit
units, and after a change return the resulting state so a caller can verify it without a second
call. A verb that displays or measures therefore names its target rather than relying on what
the user last clicked: `--view` takes a viewer id and `--pane` the pane inside its grid, counted
from 1 like every index of the surface, and both default to the selection. A verb that changes
what a pane shows makes that pane the selected one first, as a click would, so the toolbars and
the window/level range of the toolbar keep describing the pane that was acted on; a verb that
only reads leaves the selection alone. Called with no argument at all, a display verb reports
the state of its target instead of failing, which is how a caller reads the current zoom,
window/level, rotation, flip, pan, image and grid. A verb that changes something the caller cannot
see answers too, rather than staying silent: what it loaded, closed or opened, and the handles of
what it created, so the next verb has something to name. When the work is asynchronous the answer
says how far it went, and the caller chooses how long to wait for it.

The body itself is written for a program, not for a reader of the interface: a result bound to a
view opens with a `target` block naming the view, the pane, the viewer type, the series, the image
index and the image count it resolved; a DICOM attribute keeps its PS3.6 keyword in lower camel
case with the value the standard defines, dates and times in ISO 8601; a measured value is
`{key, name, value, unit}`, the key of the `Measurement`, an English name and a UCUM unit
(`MeasurementVocabulary`); a rectangle is `{x, y, width, height}`; an enumerated value is
lower-case kebab in both directions. One key means one thing across the surface, and an identifier
is `id` inside the object it identifies and the name of the concept (`view`, `series`, `entry`)
where a result points at one. A value that names a concept of the surface — a view, a pane, an index, a
handle, a path — is declared as the `SemanticType` of that concept rather than as a bare string or
integer, so the type validates itself at parse time (a malformed value is a usage error naming the
concept), shows the same label everywhere (`--view <view-id>`) and reaches the descriptor under
that name; `CommandModel` registers one converter per type it finds in a model, so a plugin adds
its own types without touching the launcher, and `CommandVocabulary` reports the drifts a
namespace test then refuses. Work that must run on the event dispatch thread goes through
`GuiExecutor.invokeAndRethrow`, which keeps the `CommandContext` bound; output that may carry
patient identity goes through `IdentityMask.callForCommand`; a namespace that must never be
reached from the socket overrides `isRemotable()`. A verb or an option carries `@Since`, is
never removed within an API major, and its syntax is pinned by the namespace's parse test.

A spelling kept from an earlier major works and stays out of sight: a verb declares its former
names as Picocli aliases, which the model turns into hidden subcommands, and an option a hidden
setter marked `@Renamed` with the current spelling. The help, the Markdown reference and the JSON
descriptor then show one spelling per thing, while a remote caller that used an old one gets a
line on the error stream naming the new one; launch arguments and `weasis://` links, written
once, stay silent. Weasis 5 keeps six command lines of the 4.x era this way, the ones a hospital
portal writes into a `weasis://` link — `dicom:get`, `dicom:rs`, `dicom:close`, `image:get`,
`image:close` and `weasis:info`, with their options of the time. Every other 4.x spelling was
dropped with the 2.0 major.

`WorkspaceCommands` is the smallest complete example: an `@Extension` class implementing
`CommandExtension` that returns itself as the Picocli model, and one static verb class per
subcommand that parses its options, hops to the event dispatch thread and prints the resulting
state. An agent composes such verbs and chains their handles: `dicom:list` gives series UIDs,
`dicom:open` opens one and `workspace:list` names the view, `view2d:capture` shows the
image, `view2d:measure` draws a region and `view2d:stats` reads its statistics.

### Versions and compatibility

Two numbers, independent of the application version, describe the bridge:

- the **wire protocol version** (`CommandProtocol.VERSION`), exchanged on every `si.run`
  request and reply; a peer speaking a newer version is refused with a message that names the
  side to update;
- the **command API version** (`CommandManager.API_VERSION`, `major.minor`): the minor grows
  with additive changes (a verb, an option, a JSON key), the major with breaking ones. A verb or
  an option is never removed within a major; a renamed one keeps its old name as a hidden,
  deprecated alias until the next major, which is free to drop it.

Both are reported by `weasis:info`, by `weasis-cli --version` and in the header of
`help --markdown`. `help --json` prints the machine-readable descriptor of every namespace
(verbs, options with type, arity, default, `since` and `deprecated`, exit codes), from which the
Markdown reference is a rendering. Verbs and options declare their origin with the `@Since`
annotation and their retirement with `@Deprecated`; both appear in the help, the reference and
the descriptor. The `namespace:verb` syntax and the option names are the same for launch
arguments, `weasis://` links and the client. Of the command lines documented for Weasis 3.5 to
4.x, the six that load, close and report keep working; a `LegacyCommandsTest` next to the
namespace that owns them pins their syntax.

### Reaching the bridge

The client is `org.weasis.launcher.CommandClient` in the launcher jar. The packaged
`weasis-cli` is an additional jpackage launcher that the installers put on the `PATH` the way
they register the `weasis://` scheme; it goes through the bootstrap like the others (kind
`cli`: no configuration fetch, the launcher set the application ran last, a JVM tuned for a
short-lived process). A build or an IDE runs the same class from the launcher jar, and neither
needs the configuration or the plugins. The client can start the sibling `Weasis` launcher
when no instance is running, pick an instance by id prefix when several run for the same user,
and answer yes on the caller's behalf to the questions a verb would ask.

`weasis-cli` has its own per-user AOT cache: a call answers first, then the bootstrap trains
the cache in a detached child, and the next calls start with it; a cache that crashes the
client is discarded and disabled for its key.

Whatever the entry point, the singleton file must be private to the user; the client refuses a
world-readable one. The result body goes to stdout, diagnostics to stderr, and the exit status is
the command status, so shells and agents can branch on it. The options and the installation
paths of `weasis-cli` are documented on the user site.

The `skills/weasis` folder at the repository root is an Agent Skill for terminal agents: a
`SKILL.md` with the workflows and rules, and `reference.md`, a snapshot of `help --markdown`. It
is shipped with the application next to `weasis-cli`.

## Logging

Two log destinations are produced under the **state** directory:

- `log/bootstrap.log` — one line per packaged launch, written by stage 1: which launcher set
  ran and why, the options source, the AOT mode, whether a restart happened and the exit code.
- `log/boot.log` — written by the `AppLauncher` static initializer before configuration is
  loaded. In portable mode this early file still lands in the resolved state directory
  rather than next to the executable, because the configuration (and thus the portable root)
  is not known yet at that point.
- `log/default.log` — the main rolling application log, enabled through the
  `org.apache.sling.commons.log.*` properties.
