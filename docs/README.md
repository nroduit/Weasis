# Weasis for developers

The entry point of the repository for contributors and plugin authors.
The user documentation (how to use, configure and deploy Weasis) is the [user site](https://nroduit.github.io); the rules a change must follow are in [AGENTS.md](../AGENTS.md) and [CONTRIBUTING.md](../CONTRIBUTING.md).

## What Weasis is made of

Weasis is a Swing desktop application assembled from [pf4j](https://pf4j.org) plugins by a small launcher.
Every feature is a `namespace:verb` command first, and the user interface, `weasis-cli`, `weasis://` links and AI agents are clients of the same commands.

| Module | What it holds |
| --- | --- |
| `weasis-bootstrap` | stage 1 of the launch: starts the JVM of the application from the launcher set, trains and uses the AOT cache |
| `weasis-launcher` | stage 2: configuration, base directories, the plugin manager and its policy, the command manager, the event bus, `weasis-cli` (`CommandClient`), the integrity check of the plugin set |
| `weasis-core` | the shared library every plugin compiles against: media and series model, image pipeline (display op chain), viewers and docking workspace, settings, measurement tools, printing, identity masking, the extension points of the UI |
| `weasis-opencv` | the OpenCV native libraries per platform (`weasis-core-img` is a separate repository) |
| `weasis-imageio` | the ImageIO codecs of the standard image formats |
| `weasis-base/weasis-base-ui` | the main window, menus, preferences dialog, the `weasis:` and `plugins:` namespaces |
| `weasis-base/weasis-base-explorer`, `weasis-base-viewer2d` | the non-DICOM media explorer and image viewer |
| `weasis-dicom/weasis-dicom-codec` | DICOM parsing and the DICOM model, hanging protocols, segmentation, window presets, export writers |
| `weasis-dicom/weasis-dicom-explorer` | the DICOM explorer, import and export, download transports, hanging protocol application, checkpoints and sessions |
| `weasis-dicom/weasis-dicom-viewer2d` | the 2D and MPR viewer, fusion, subtraction, synchronization, the `view2d:` namespace |
| `weasis-dicom/weasis-dicom-3d/viewer3d` | volume rendering on OpenGL through the FFM API |
| `weasis-dicom/weasis-dicom-sr`, `-rt`, `-au`, `-wave`, `-wsi` | the viewers of structured reports, radiotherapy objects, audio, waveforms (ECG) and whole slide images |
| `weasis-dicom/weasis-dicom-qr`, `-send`, `-isowriter`, `-audit` | query/retrieve, DICOM and DICOMweb send, ISO export, the audit trail (PS3.15 messages) |
| `weasis-acquire` | image acquisition and editing before a DICOM export |
| `weasis-distributions` | the configuration served to the application (`etc/config/base.json`), the native distribution and the installer scripts |
| `tests` | the scripted smoke suite ([tests/smoke](../tests/smoke/README.md)) and the coverage aggregate |
| `archetype` | the Maven archetypes of a plugin ([archetype/README.md](../archetype/README.md)) |
| `skills/weasis` | the Agent Skill of `weasis-cli` for terminal agents ([skills/weasis/README.md](../skills/weasis/README.md)) |

## Build and run

JDK 25 or later and Maven 3.8.1 or later (`java-version` in `weasis-parent/pom.xml`; CI runs JDK 27).

```bash
mvn -B -ntp install -DskipTests        # every module into the local repository
mvn -pl <module> -am test               # the tests of one module and what it depends on
mvn spotless:apply                      # formatting and license headers, before every commit
```

The application runs from the launcher jar; it reads `weasis-launcher/conf/base.json` and loads the plugins from the local Maven repository, so a rebuilt module is picked up by a restart:

```bash
cd weasis-launcher
java -cp target/weasis-launcher-*.jar org.weasis.launcher.AppLauncher
```

`weasis-distributions/script/weasis-cli.sh` wraps the same jar as the command client, and `weasis-cli --launch` starts the application when none runs (the IDE run configuration does the same with `AppLauncher` as main class).
The native distribution and the installers are built apart, see [Native-Build-Overview.md](../weasis-distributions/docs/Native-Build-Overview.md).

## Guides

- [Writing a plugin](plugins.md): the plugin model, the extension points, a command namespace, loading a development build, packaging and signing, what changed from 4.x.
- [Testing with commands](testing-with-commands.md): scripts with `@expect`, the JUnit report, the tests a namespace carries, how a bug becomes a script.
- [Release notes](release-notes/README.md): the tester notes of the pre-releases.

## Concepts, by subject

Each module documents its concepts next to its code, in `<module>/docs/`.
The pages describe the code as it is: purpose, main abstractions, data flow, conventions and invariants.

### Launch, plugins and commands

- [Launcher-Overview.md](../weasis-launcher/docs/Launcher-Overview.md): startup sequence, bootstrap and launcher set, class loaders, configuration sources, base directories, the single instance, commands and the command bridge.
- [Event-Model.md](../weasis-launcher/docs/Event-Model.md): typed events on one bus, the catalog, the stream on the singleton socket, `@wait` in scripts.
- [Settings.md](../weasis-core/docs/Settings.md) and [Preferences-Migration.md](../weasis-core/docs/Preferences-Migration.md): the settings catalog, the layers, the preference pages.

### User interface and workspace

- [Workspace-Docking.md](../weasis-core/docs/Workspace-Docking.md): the docking engine, views, snapshots, `workspace:` commands.
- [Shortcut-Management.md](../weasis-core/docs/Shortcut-Management.md): shortcut registry and contexts.
- [MigLayoutModel-Best-Practices.md](../weasis-core/docs/MigLayoutModel-Best-Practices.md): the layout conventions.
- [Printing.md](../weasis-core/docs/Printing.md): page model, renderer, destinations.

### Image pipeline

- [Display-Op-Chain.md](../weasis-core/docs/Display-Op-Chain.md): the display operations, what makes a node run, the contract of an operation.
- [Color-Management.md](../weasis-core/docs/Color-Management.md) and [ColorMap-Design.md](../weasis-core/docs/ColorMap-Design.md): ICC profiles and color maps.
- [Memory-Management.md](../weasis-core/docs/Memory-Management.md): native memory arenas, budgets, caches.
- [Measurement-Tools.md](../weasis-core/docs/Measurement-Tools.md): the graphic tool registry, measurement keys, the headless measurement service.
- [Identity-Masking.md](../weasis-core/docs/Identity-Masking.md): display-time masking and redaction.
- [Animation-Export.md](../weasis-core/docs/Animation-Export.md): APNG, GIF and DICOM SC export.

### DICOM

- [Hanging-Protocols.md](../weasis-dicom/weasis-dicom-codec/docs/Hanging-Protocols.md): protocol model and layers, series features, engine and plan, sessions, DICOM exchange.
- [Segmentation.md](../weasis-dicom/weasis-dicom-codec/docs/Segmentation.md) and [Window-Presets.md](../weasis-dicom/weasis-dicom-codec/docs/Window-Presets.md).
- [Retrieve-Paths.md](../weasis-dicom/weasis-dicom-explorer/docs/Retrieve-Paths.md): download transports and the shared download layer.

### Viewers

- [mpr-architecture.md](../weasis-dicom/weasis-dicom-viewer2d/docs/mpr-architecture.md) with [mpr-coordinate-systems.md](../weasis-dicom/weasis-dicom-viewer2d/docs/mpr-coordinate-systems.md), [mpr-transformations.md](../weasis-dicom/weasis-dicom-viewer2d/docs/mpr-transformations.md) and [mpr-curved.md](../weasis-dicom/weasis-dicom-viewer2d/docs/mpr-curved.md).
- [fusion-architecture.md](../weasis-dicom/weasis-dicom-viewer2d/docs/fusion-architecture.md), [subtraction.md](../weasis-dicom/weasis-dicom-viewer2d/docs/subtraction.md), [synch-architecture.md](../weasis-dicom/weasis-dicom-viewer2d/docs/synch-architecture.md).
- [OpenGL-Backend.md](../weasis-dicom/weasis-dicom-3d/viewer3d/docs/OpenGL-Backend.md): the GL thread and how a frame reaches Swing.

### Packaging

- [Native-Build-Overview.md](../weasis-distributions/docs/Native-Build-Overview.md): installer build jobs, binary compatibility, pre-release builds.
