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
- [Commands and the Command Bridge](#commands-and-the-command-bridge)
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
Extensions declared by a shared library (the `workspace:` and `image:` command namespaces of
`weasis-core`, for instance) are discovered through that loader as well: `CommonLibExtensionFinder`
reads the classpath extension index and loads its classes through `CommonLibClassLoader`, so a
library downloaded into the cache contributes its extensions exactly like one shipped in the app
image.

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
configuration hashes match. The same socket carries the command bridge described below; each
connection is served on its own virtual thread so a long command never blocks argument
forwarding.

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

The built-in `help` is navigable in three levels: `help` lists the namespaces with a one-line
summary, `help <namespace>` the verbs of a namespace, and `help <namespace>:<verb>` (or
`help <namespace> <verb>`) one verb in detail, its synopsis naming it `namespace:verb`. The
first two levels also come as `--names` (bare names) and `--index` (a title line, then one
`name`, tab, `summary` line per entry, rendered by `CommandHelp`); `--markdown` and `--json`
give the whole reference. On the client side, `weasis-cli help` at any level opens an
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
`weasis-cli` is an additional jpackage launcher tuned for a short-lived JVM that the installers
put on the `PATH` the way they register the `weasis://` scheme; a build or an IDE runs the same
class from the launcher jar, and neither needs the configuration, the plugins or the AOT
bootstrap. The client can start the sibling `Weasis` launcher when no instance is running, pick
an instance by id prefix when several run for the same user, and answer yes on the caller's behalf
to the questions a verb would ask.

`weasis-cli` keeps its own per-user AOT cache (`ClientAotCache`): a call answers first, a
detached child trains the cache, a later call verifies it and persists `-XX:AOTCache` into the
launcher `.cfg`, under the same conditions as the application (packaged, writable `.cfg`, not
macOS, cache path free of characters that break `JAVA_TOOL_OPTIONS`).

Whatever the entry point, the singleton file must be private to the user; the client refuses a
world-readable one. The result body goes to stdout, diagnostics to stderr, and the exit status is
the command status, so shells and agents can branch on it. The options and the installation
paths of `weasis-cli` are documented on the user site.

The `skills/weasis` folder at the repository root is an Agent Skill for terminal agents: a
`SKILL.md` with the workflows and rules, and `reference.md`, a snapshot of `help --markdown`. It
is shipped with the application next to `weasis-cli`.

## Logging

Two log destinations are produced under the **state** directory:

- `log/boot.log` — written by the `AppLauncher` static initializer before configuration is
  loaded. In portable mode this early file still lands in the resolved state directory
  rather than next to the executable, because the configuration (and thus the portable root)
  is not known yet at that point.
- `log/default.log` — the main rolling application log, enabled through the
  `org.apache.sling.commons.log.*` properties.
