# Writing a plugin for Weasis 5

How a plugin is built, what it can contribute, how it is loaded in development and how it reaches a workstation.
The commands quoted here are those of `weasis-cli`; the concepts of the launcher are in [Launcher-Overview.md](../weasis-launcher/docs/Launcher-Overview.md).

## The plugin model

Weasis 5 is a [pf4j](https://pf4j.org) application: the launcher builds a plugin manager (`WeasisPluginManager`), loads the plugins listed by the served configuration (`base.json`) and the ones the user imported, then starts them.
A plugin is a plain jar whose manifest carries the pf4j headers (`Plugin-Id`, `Plugin-Version`, `Plugin-Requires`, `Plugin-Provider`, `Plugin-License`) and whose classes contribute **extensions**: classes annotated `@org.pf4j.Extension` that implement an **extension point** of the platform.
There is no OSGi, no bundle activator and no service registry: a plugin that has nothing to run at start needs no plugin class at all.

Two kinds of jars reach the application:

- a **plugin** has its own class loader (`WeasisPluginClassLoader`, parent-last: the plugin, its dependencies, then the platform) and is isolated from the other plugins;
- a **common library** (`weasis-core`, `weasis-dicom-codec`, the viewers) is loaded by one shared loader (`CommonLibClassLoader`), so the types a plugin compiles against are the same objects for everyone.
  A plugin declares those as `provided` dependencies: they are never packed into the plugin jar.

A dependency the platform does not provide (an HTTP client, a model runtime) is packed into the plugin jar (shaded) and stays private to its class loader.
The platform provides pf4j, Picocli, Jakarta JSON, FlatLaf, MigLayout, SLF4J and the dcm4che and OpenCV libraries; it no longer provides Jackson.

The downloaded jars live in the plugin cache under the cache directory of the user (`weasis.cache.dir`), one folder per configuration; a change of the plugin set is applied at the next start, plugins are not started or stopped at runtime in 5.0.

## What a plugin can contribute

Every extension point is an interface that extends `org.pf4j.ExtensionPoint`; an implementation annotated `@Extension` is found by its index at start.
`@SingletonExtension` (`org.weasis.plugin`) makes the platform create one instance per plugin instead of one per lookup.

| Extension point | A plugin that implements it |
| --- | --- |
| `org.weasis.core.api.command.CommandExtension` | owns a command namespace: its verbs are reachable from launch arguments, `weasis://` links, `weasis-cli`, scripts and agents (see [A command namespace](#a-command-namespace)) |
| `org.weasis.core.api.gui.InsertableFactory` | adds a tool panel (`Insertable.Type.TOOL`) or a toolbar (`TOOLBAR`) to a viewer type; the archetypes generate one of each |
| `org.weasis.core.ui.docking.ToolContribution` | adds a tool to the shell with a relevance predicate on the `ViewContext` of the selected viewer, shown and hidden without the viewer knowing it |
| `org.weasis.core.ui.editor.ViewContextContributor` | publishes facts about what is displayed under dotted names, so the tools and menus of every plugin decide their relevance on the same facts |
| `org.weasis.core.ui.editor.SeriesMenuContributor` | adds items to the context menu of a series in an explorer |
| `org.weasis.core.ui.editor.DerivedSeriesProvider` | derives series from loaded ones and rebuilds them from a small spec when a session is restored |
| `org.weasis.core.ui.editor.VolumePrefetcher` | builds the volume of a series ahead of its use |
| `org.weasis.core.ui.model.graphic.GraphicToolProvider` | adds graphic (measurement and annotation) tools to the registry |
| `org.weasis.core.ui.print.PrintDestinationProvider` | adds print destinations |
| `org.weasis.core.api.explorer.DataExplorerViewFactory` | adds an explorer (a data source with its own view) |
| `org.weasis.core.api.media.data.Codec` | reads a media format into the series model |
| `org.weasis.dicom.codec.DicomSpecialElementFactory` | handles a DICOM object class that is not an image (a report, a structure set) |
| `org.weasis.dicom.codec.hp.SeriesFeatureProvider` | derives series features for the hanging protocols, each value with its provenance and confidence |
| `org.weasis.dicom.explorer.imp.DicomImportFactory`, `exp.DicomExportFactory` | adds an import or export page to the DICOM explorer |

The purpose and the contract of each point are in its Javadoc; the concept pages of [docs/README.md](README.md) describe the models they plug into.
A preference page is declared through the settings catalog (`weasis-core/docs/Settings.md`), with the settings of the plugin scoped by its plugin id.

The interface mirrors for the languages written right to left, and a plugin's windows and dialogs follow by themselves.
A component that shows an image or a chart with a time or value axis is marked with `GuiUtils.fixOrientation` so that it stays left to right; its custom painting is the plugin's own.
Lay out with leading and trailing sides, read direction icons with `ResourceUtil.getDirectionIcon`, and fill sentences with `Messages.format`, which isolates the arguments ([docs/i18n.md](i18n.md#right-to-left-languages)).

## A command namespace

A feature is designed command-first: a headless service holds the logic, a `CommandExtension` exposes it as Picocli verbs, and the Swing UI calls the same service.
The rules of a verb (handles instead of screen positions, structured JSON with stable keys, the result state after a change, `@Since`) are in [Launcher-Overview.md, Designing a feature as commands](../weasis-launcher/docs/Launcher-Overview.md#designing-a-feature-as-commands).
The smallest complete shape, generated by the archetypes as `SampleCommands`:

```java
@Extension
@Command(name = "sample", description = "...", subcommands = {SampleCommands.Info.class})
@Since("1.0")
public class SampleCommands implements CommandExtension {
  @Override public String getNamespace() { return "sample"; }
  @Override public Object getCommandModel() { return this; }

  @Command(name = "info", description = "What this plugin is and what it loaded.")
  @Since("1.0")
  static class Info extends AbstractVerb implements Returns {
    @Override protected void run() { print(json...); }
    @Override public ResultSchema result() {
      return ResultSchema.of().field("name", String.class, "The plugin name").build();
    }
  }
}
```

What the platform checks, and what the namespace test of the plugin pins:

- `CommandVocabulary.allViolations(namespace, extension)` is empty: every value is labelled (`paramLabel`), long option names are kebab-case, a value that names a concept is a `SemanticType` with a `@Definition`, a file read is an `InputPath` and a file written an `OutputPath`, no short option; every word of an option name or of a result key is a term of the glossary (`help glossary`) or a word the platform allows, and a term the plugin introduces is declared in `getTerms()`; no description uses a synonym the glossary bans; a verb that hands out a handle says so with `@Produces` and its result has a field of that type.
- `CommandVocabulary.resultViolations(namespace, model)` is empty: every verb implements `Returns` and declares the keys of its answer, nested keys as paths (`graphics[].id`).
- A verb that needs the machine declares it with `@Needs(Capability.X)` (`FILE_READ`, `FILE_WRITE`, `NETWORK_READ`, `NETWORK_WRITE`, `SEND_DICOM`, ...); the remote policy of the instance decides which capabilities a remote caller gets.
- The namespace and the capabilities are declared in the manifest (`Weasis-Namespaces`, `Weasis-Capabilities`): a plugin that is not part of the package may register verbs only in the namespaces it declares, never in a namespace of the package, and its verbs may need no capability it does not declare.
  `Weasis-Command-API` names the command API the plugin was built against; the plugin manager refuses another major.

`help sample` and `help --json` then describe the namespace like any other, and the Agent Skill of a running instance lists it.

## Loading a development build

A source build of Weasis runs with `weasis-launcher/conf/base.json`, whose plugin policy is `any`: a jar built by the developer is accepted without a signature.

1. Build the plugin: `mvn package` gives `target/<artifact>-<version>.jar`.
2. Import it once into the running instance:

   ```bash
   weasis-cli plugins:add --file target/<artifact>-<version>.jar
   weasis-cli plugins:list
   ```

   The jar is copied into the user folder (`<config dir>/plugins/`, `weasis.plugins.dir`) and recorded in `plugins.json` with its digest.
3. Restart the application (`weasis-cli -y weasis:quit`, then launch again): the plugin starts, `plugins:list` shows it as `user` / `unverified` / `started`, `help <namespace>` shows its verbs.
4. After a rebuild, `plugins:add` again with the new jar (the digest changed), then restart.
   `plugins:remove <id>`, `plugins:disable <id>` and `plugins:enable <id>` take effect at the next start too.

The same folder is read by the *Plugins* preference page.
A plugin can also be listed in a configuration file like the plugins of the package (an extended configuration, `weasis.extended.config.properties`), which is how a site deploys it to every workstation.

## Packaging

The parent pom (`org.weasis:weasis-parent`) writes the pf4j headers of every plugin jar and the `Weasis-Command-API` header through `maven-jar-plugin`; a plugin pom inherits them and adds its own entries:

```xml
<Plugin-Provider>Example Hospital</Plugin-Provider>
<Weasis-Namespaces>sample</Weasis-Namespaces>
<Weasis-Capabilities>FILE_READ</Weasis-Capabilities>
```

`Plugin-Requires` is the oldest Weasis the plugin accepts (`base.minimal.version` of the parent); a plugin that reads a header of its own does it through pf4j's `PluginDescriptor`.

A plugin meant for other workstations is **signed** by its publisher: an Ed25519 key pair, a `plugin.sig.json` written next to the jar (or inside it under `META-INF/weasis/`) with the digest of the jar and the headers it was signed with.
The tooling is in the launcher jar:

```bash
java -cp weasis-launcher.jar org.weasis.plugin.manager.PluginTool keygen <dir> <publisher-id>
java -cp weasis-launcher.jar org.weasis.plugin.manager.PluginTool sign <plugin.jar> <publisher-id>.key
java -cp weasis-launcher.jar org.weasis.plugin.manager.PluginTool verify <plugin.jar> <publisher-id>.pub
java -cp weasis-launcher.jar org.weasis.plugin.manager.PluginTool publisher-record <publisher-id>.pub "<name>" [url] [contact]
```

What a workstation accepts is the **site policy**, read by the launcher from the served configuration before any plugin class loads (`weasis.plugins.policy`): `core-only` (the package only, the default of the installers), `site` (plugins signed by a publisher the site lists in `weasis.plugins.publishers`), `any` (every jar after the user's confirmation), `off` (the user folder is ignored).
A site can also lock the loaded set (`weasis.plugins.lock`) and deny capabilities (`weasis.plugins.deny`).
`plugins:list` reports the tier of every loaded plugin (`core`, `site`, `unverified`) and `plugins:verify` re-runs the checks of a jar without importing it.

## What changed from 4.x

- **No OSGi.** `BundleContext`, `@Component`, `PreferencesService`, `BundleActivator` and the Gogo commands are gone; an extension is an `@Extension` class, a command a `CommandExtension`, a preference a setting of the catalog.
- **No Jackson from the platform.** A plugin that needs it packs it; the platform's JSON API is Jakarta JSON.
- **Preferences scoped by plugin id**, not by bundle symbolic name, stored by the settings catalog (`weasis-core/docs/Preferences-Migration.md`).
- **Configuration keys renamed** (`weasis.plugins.*`, `weasis.launcher.*`, the base directories per OS instead of `~/.weasis`); `base.minimal.version` is `5.0.0`.
- **One class loader per plugin**, parent-last: a plugin no longer sees the classes of another plugin unless it depends on it (`Plugin-Dependencies`).
- **Commands are the API.** A plugin that only adds Swing components is invisible to scripts and agents; give it a namespace.

## What may still change before 5.0.0

The platform of the previews is the one of 5.0.0 in its shape, not yet in every name:

- the command vocabulary: a verb or an option may still be renamed within the 2.x command API, the old spelling kept as a hidden alias until the next major;
- the list of extension points may grow, and `ToolContribution`, `ViewContextContributor` and `SeriesFeatureProvider` may gain methods with defaults;
- the plugin manager: the format of `plugin.sig.json`, the loaded-plugins manifest and the publisher records are new and may change until the first external publisher ships;
- the `Provenance` record of the derived values and the result schemas (`Returns`) are new in 5.0 and may gain fields.

A plugin built against a 5.0 preview is rebuilt against 5.0.0; the pre-release notes of each beta list what moved.
