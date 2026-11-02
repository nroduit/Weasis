# AGENTS.md

Guidance for AI coding agents and human contributors working in this repository.
The nearest `AGENTS.md` up the directory tree applies; a module may add its own for local rules.
Read [CONTRIBUTING.md](CONTRIBUTING.md) for how issues and pull requests are handled.

## Project overview

Weasis is a desktop DICOM viewer built as an OSGi application (Apache Felix 7) on Swing.
It is a Maven multi-module build that requires **JDK 25+** (`java-version` in `weasis-parent/pom.xml`; CI runs JDK 27 Temurin, the macOS x64 installer stays on JDK 26) and Maven **3.8.1+** (enforced by `maven-enforcer-plugin`).
The version comes from CI-friendly properties in `weasis-parent/pom.xml`: `${revision}` and `${changelist}` (default `-SNAPSHOT`).
User documentation lives on the [user site](https://nroduit.github.io), not in this repository.

## Commands

```bash
# Format and add the license header (run before every commit)
mvn spotless:apply
mvn spotless:check                                  # what CI verifies

# Build without tests
mvn -B -ntp install -DskipTests

# Run one test (and build the modules it depends on)
mvn -pl <module-path> -am test -Dtest=ClassName#methodName

# Full build and tests, as the CI "Build" job (minus Sonar)
mvn -B -ntp verify -Pcoverage
```

Surefire runs tests with `<parallel>all</parallel>`, so tests must not share mutable static state.
Its `argLine` sets `-Djava.library.path=target/lib/<os>-<arch>` for the OpenCV native libraries: tests that need them must run through Maven, or from the IDE with the same `java.library.path`.
The `tests` module only aggregates coverage reports.

### Native distribution and installers

The `weasis-distributions` module is **not** built by the root `mvn install`.
To produce the `weasis-native.zip` used by `jpackage`:

```bash
mvn -B clean install
mvn -B -P compressXZ -f weasis-distributions clean package
# weasis-distributions/script/package-weasis.sh then drives jpackage for the installer.
```

The installer pipeline (Windows MSI, macOS PKG with notarization, Linux DEB/RPM through Docker for several architectures) lives in `.github/workflows/build-installer.yml` and runs only on `workflow_dispatch`.
See `weasis-distributions/docs/Native-Build-Overview.md` for the build jobs and the binary compatibility constraints.

### Plugin archetype

```bash
cd archetype && mvn install                         # publish the archetypes locally
mvn archetype:generate -DarchetypeCatalog=local     # weasis-plugin-{base,dicom}-viewer-archetype
```

## Definition of done

A change is complete when:

- `mvn spotless:check` passes and the affected modules build and pass their tests (`mvn -pl <module> -am verify`).
- New behavior has tests, and a fixed bug has a test that failed before the fix, when the code can be tested without a display.
- The build adds no new compiler or Sonar warning in the changed code.
- A doc in `docs/` that describes a changed concept is updated in the same change (see [Documentation](#documentation)).
- The description of the change says how it was verified: tests, or manual testing with which data and operating system.

## Architecture

Three things are enough to navigate the codebase.

### 1. Launcher and bundles

- `weasis-launcher/` is a **plain JAR**, not an OSGi bundle (see its POM comment).
  `org.weasis.launcher.AppLauncher` (extends `WeasisLauncher`) writes the boot log through Logback to `~/.weasis/log/boot.log`, applies the single-instance rules (`Singleton`) and starts the Felix framework; `WeasisLoader` then runs `AutoProcessor`, which installs and starts the bundles.
- **Launcher configuration is JSON**, not properties files.
  `weasis-launcher/conf/base.json` is the default, with `dicomizer.json` and `non-dicom-explorer.json` for the other launchers.
  Each holds a `weasisPreferences` array of `code` / `value` entries; the bundles to install and start are the `felix.auto.start.<level>` entries.
  An optional extension file (`felix.extended.config.properties`) adds to or overrides the base file.
  The launcher loads them into `ConfigData` / `AppPreferences` (`org.weasis.pref`).
- `EmptyAccessibilityProvider` is wired in through `-Djavax.accessibility.assistive_technologies=...` (set in `weasis-distributions/script/launch-options.sh`) to suppress the discovery of assistive technologies at launch.
- Everything under `weasis-core`, `weasis-base/*`, `weasis-dicom/*`, `weasis-acquire/*`, `weasis-imageio` and `weasis-opencv/*` is an **OSGi bundle**, built by `biz.aQute.bnd:bnd-maven-plugin` (configured in `weasis-parent/pom.xml`).
  A bundle's `Activator`, when it has one, lives in a package ending in `.internal`.
- The bnd convention is `Export-Package: !<groupPkg>.internal, <groupPkg>.*`: anything in `.internal` is private to the bundle.
  Modules without an `.internal` package export `<groupPkg>.*`.
  Never import an `internal` package from another bundle.
  Exception to fix when touched: `weasis-dicom-3d/viewer3d` has an `internal` package but exports `org.weasis.dicom.viewer3d.*` without excluding it.

### 2. Module layering

```text
weasis-launcher  →  weasis-core  →  weasis-base/*       (generic image viewer: explorer, ui, viewer2d)
                                 →  weasis-dicom/*      (DICOM: codec, explorer, viewer2d, 3d, rt, sr, au, wave, qr, send, isowriter, ai-agent)
                                 →  weasis-acquire/*    (image acquisition / Dicomizer: explorer, editor)
                                 →  weasis-imageio, weasis-opencv  (image and native dependencies)
```

`weasis-core` exposes the **plugin SDK**.
Its API lives under `org.weasis.core.api.*` (command, explorer, gui, image, media, model, net, service, util, vol) and `org.weasis.core.ui.*` (dialog, docking, editor, launcher, model, pref, serialize, tp, util).
Viewers extend `DefaultView2d`, `ImageViewerEventManager`, `ImageViewerPlugin`, `SynchView` and similar classes.
A viewer feature usually touches a base class in `weasis-core` and its subclasses in `weasis-base-viewer2d`, `weasis-dicom-viewer2d` and possibly `weasis-dicom-3d/viewer3d`.

### 3. UI stack

- Swing with **FlatLaf** themes (`com.formdev:flatlaf*`).
- **MigLayout** for layout: read `weasis-core/docs/MigLayoutModel-Best-Practices.md` (weights, grow and shrink priorities) before touching a layout.
- **DockingFrames** (`dockingframes.version` in `weasis-parent/pom.xml`) for the dockable viewer and tool windows.
- DICOM I/O comes from the external `weasis-dicom-tools` library (`weasis-dicom-tools.version` in `weasis-parent/pom.xml`; packages `org.weasis.dicom.{geom,hp,macro,mf,op,param,ref,tool,util,web}`).
  Its source is not in this repository: treat it as a third-party dependency.

## Code conventions

### Formatting and headers

- **Spotless fails the build** when a file lacks the EPL-2.0 OR Apache-2.0 license header or is not formatted with Google Java Format.
  Run `mvn spotless:apply` before committing.
  Use `// @formatter:off` / `// @formatter:on` only around a block whose layout carries meaning, such as a matrix.

### Java

- **Modern Java (25)**: prefer `java.nio.file.Path` and `Files` over `java.io.File` and `FileInputStream` / `FileOutputStream`.
  Use records, pattern matching (`switch`, `instanceof`), sealed types, text blocks, `var` for obvious local types, and `List.of` / `Map.of` / `Stream.toList()` over legacy collection idioms.
- **Optional**: return `Optional` rather than `null` at API boundaries; never wrap fields or parameters in `Optional`.
- **Code quality**: small focused methods, expressive names, early returns.
  Remove redundant code: unused imports and locals, dead branches, duplicated logic, null checks for values that cannot be null, comments that repeat the code.
  Extract a private helper rather than copy a block.
- **Logging**: use SLF4J (`org.slf4j.Logger` from `LoggerFactory.getLogger(Foo.class)`) with `{}` placeholders, never `System.out`, `System.err` or `printStackTrace()`.
  Pass the exception as the last argument so its stack trace is kept.
- **Threading**: touch Swing components only on the event dispatch thread, through `GuiExecutor.execute` or `GuiExecutor.invokeAndWait` (`org.weasis.core.api.gui.util`).
  Never block the EDT with I/O, decoding or network calls; run them on a background executor and post the result back.
- **Javadoc**: keep it minimal.
  A private method gets at most a one-line comment, or none when its name and signature are clear.
  A public or protected API gets one short sentence on its intent, with `@param` / `@return` / `@throws` only when they add what the signature does not say.
  Never restate the method name, never document obvious getters and setters, never leave `TODO` placeholders.

### Project rules

- **i18n**: every user-facing module has a `Messages.java` and `messages.properties` pair.
  Every user-visible string goes through it, never as a literal in the code.
  Properties files matching `messages*.properties` under `src/main/java/` are picked up as resources by `weasis-parent/pom.xml`.
  The translations come from the external `weasis-i18n-dist` artifact at distribution time (see `weasis-distributions/pom.xml`): never edit a non-English `messages_*.properties` file in this repository.
- **DICOM and IHE conformance**: follow the DICOM standard (tag semantics, value representations, attribute types and conditions, IOD and module rules) and the IHE Radiology profiles Weasis implements:
  - **BIR** (Basic Image Review, RAD TF-1 §31 / TF-2 §4.16): image annotations, lossy compression and presentation state indications, the required viewing tools and their behavior.
  - **PDI** (Portable Data for Imaging, RAD TF-1 §15 / TF-2 §4.47): reading and writing interchange media (DICOMDIR, file-set layout, viewer on media).
  - **IID** (Invoke Image Display, RAD TF-1 §35 / TF-2 §4.106): launching the viewer from an external application with patient and study parameters.

  An intentional deviation (a lenient fallback for malformed or non-conformant files, a vendor-specific workaround, a choice made for robustness) carries a short comment saying that it departs from the standard or profile and why.
  Cite the PS3 part and section, the IHE TF volume and section, or the vendor or modality when known.
- **Versions**: never hard-code a version such as `4.8.0-SNAPSHOT` in a child POM; use `${project.parent.version}` or the `${revision}${changelist}` pair.
  The `flatten-maven-plugin` resolves them in the published POM.
- **Native libraries (OpenCV, JOGL)**: the packages for each OS live in the `weasis-opencv-core-*` and `jogamp-*` bundles.
  `build-installer.yml` strips the payloads of the other architectures; a new native bundle must follow that pattern or the installers grow.
- **Sonar exclusions** (root `pom.xml`): `Messages.java`, `Activator.java`, `module-info.java` and `package-info.java` are excluded from coverage; `archetype/`, `snap/` and `weasis-distributions/` are excluded from analysis.
- **Cross-platform**: Weasis runs on Windows, macOS and Linux.
  Build paths with `Path`, never with `/` or `\` concatenation, and never assume a case-sensitive file system or a default charset; pass `StandardCharsets.UTF_8` explicitly.

## Testing

- Use **JUnit 6** (`org.junit.jupiter.*`) and **Mockito** only.
  Do not add AssertJ (`org.assertj.*`): use JUnit's `Assertions` (`assertEquals`, `assertThrows`, `assertAll`, …).
- Tests live in `src/test/java` of the module they test, in the same package; test resources go in `src/test/resources`.
- Tests are parallel (see [Commands](#commands)): no shared mutable static state, no fixed ports, no files outside a `@TempDir`.
- Tests must not need a network, a display or a running Weasis instance.
- Tests follow the same Spotless rules as production code.

## Writing conventions

These rules apply to Markdown docs, Javadoc, comments, user-visible messages and commit messages.

- **American English**: center, color, gray, behavior, millimeter, catalog, license (noun), analyze, normalize.
  DICOM keywords and external API names keep their own spelling.
- **Markdown**:
  - One sentence per line, so that diffs show the changed sentence only.
  - A blank line before and after headings, lists, tables, code blocks and quotes; never more than one blank line.
  - A hard line break is a trailing `\`, never two trailing spaces.
  - CommonMark with GFM tables and fenced code blocks that name their language (`java`, `bash`, `text`, …); no raw HTML.
  - Relative links between files in the repository, absolute links to the user site.
- `.editorconfig` sets UTF-8, LF and a final newline; `.markdownlint.yaml` holds the Markdown rules a linter can check.

## Documentation

Documentation is split by audience; put each piece in exactly one place:

| Where | Audience | Holds |
|---|---|---|
| `<module>/docs/*.md` in this repository | developers and contributors | **general concepts only**: purpose, main abstractions and their responsibilities, data flow, coordinate and value conventions, invariants, extension points |
| User site [nroduit.github.io](https://nroduit.github.io) (separate repository) | users, integrators, administrators | how to use, configure and deploy Weasis |
| Not in this repository | maintainer | detailed specifications, work items, design history, benchmarks, test procedures |

Rules for `docs/`:

- **Describe the code as it is**, at concept level.
  No TODO lists, roadmap, status or phase tables, "work in progress" notes, history of how the design was reached, or debates between alternatives.
- **Keep detail out**: no method-by-method walkthroughs, exhaustive field or constant tables, long code excerpts or step-by-step algorithm derivations.
  The code and its Javadoc are the reference for detail; when a doc grows into a specification, cut it back to the concept.
- **Keep docs in sync with the code**: when a change alters a concept a doc describes, update the doc in the same change.
  Every class, method or property named in a doc must exist.
- **Stable file names**: docs are referenced from this file and from each other; do not rename them, and keep relative links valid.
- **User-facing behavior belongs on the user site**: link to the relevant page (for example `https://nroduit.github.io/en/tutorials/mpr/`) instead of duplicating it.
- Write docs in English (see [Writing conventions](#writing-conventions)), next to the module they describe.

Existing docs:

| Doc | Topic |
|---|---|
| `weasis-core/docs/Animation-Export.md` | APNG / GIF / DICOM SC export of 3D rotations, cine loops and recordings |
| `weasis-core/docs/ColorMap-Design.md` | JSON color maps for 2D, fusion and 3D |
| `weasis-core/docs/Identity-Masking.md` | display-time identity masking, masking profiles, redaction of burned-in pixels |
| `weasis-core/docs/Measurement-Tools.md` | graphic tool registry, measurement keys, presentation XML compatibility, headless measurement service |
| `weasis-core/docs/Memory-Management.md` | native memory arenas, budgets, caches |
| `weasis-core/docs/MigLayoutModel-Best-Practices.md` | `MigLayoutModel` layout conventions |
| `weasis-core/docs/Shortcut-Management.md` | shortcut registry and contexts, `KeyBinding`, dispatch tables, menu accelerators, mouse button matching |
| `weasis-dicom/weasis-dicom-codec/docs/Window-Presets.md` | configured window/level presets: order, model, layers, anatomy matching |
| `weasis-dicom/weasis-dicom-explorer/docs/Retrieve-Paths.md` | DICOM download transports and shared download layer |
| `weasis-dicom/weasis-dicom-viewer2d/docs/mpr-architecture.md` | MPR architecture (entry point for the `mpr-*` docs) |
| `weasis-dicom/weasis-dicom-viewer2d/docs/mpr-coordinate-systems.md` | MPR coordinate spaces |
| `weasis-dicom/weasis-dicom-viewer2d/docs/mpr-transformations.md` | MPR transforms and rotations |
| `weasis-dicom/weasis-dicom-viewer2d/docs/mpr-curved.md` | curved MPR (panoramic, cross-sections) |
| `weasis-dicom/weasis-dicom-viewer2d/docs/fusion-architecture.md` | PET/CT fusion pipeline |
| `weasis-dicom/weasis-dicom-viewer2d/docs/synch-architecture.md` | view synchronization |
| `weasis-distributions/docs/Native-Build-Overview.md` | installer build jobs and binary compatibility |

## Git and commits

- One logical change per commit; never mix formatting or refactoring with a functional change.
- Subject line: an imperative sentence that says what the change does for the user or the code, capitalized, without a trailing period, at most about 72 characters (for example `Keep the closing edge of a polygon in its measurements`).
  Reference the issue as `#123` when there is one.
- A body, after a blank line, explains why when the subject is not enough.
- Never push, force-push, rewrite published history or create a release unless explicitly asked.
- `CHANGELOG.md` is generated by the `changelog` workflow: never edit it by hand.

## Boundaries

### Never

- Commit patient data: no real DICOM files, screenshots, logs or test fixtures containing names, IDs, dates of birth or other identifiers.
  Use synthetic or anonymized data, and keep test fixtures small.
- Commit secrets, credentials, internal host names or links to private resources.
- Edit generated or external content: `target/`, `.flattened-pom.xml`, `CHANGELOG.md`, the non-English `messages_*.properties` files, or the code of `weasis-dicom-tools`.
- Disable or skip Spotless, tests or the enforcer to make a build pass.

### Ask first

- Adding, upgrading or removing a dependency or a Maven plugin.
  A new dependency must have a license compatible with EPL-2.0 OR Apache-2.0; a bundled runtime or code copied into the repository is also listed in `3rd-party-licenses.md`.
- Changing a public API of `weasis-core` (`org.weasis.core.api.*`, `org.weasis.core.ui.*`), which plugins outside this repository use; deprecate with `@Deprecated` rather than remove.
- Changing the launcher configuration files (`weasis-launcher/conf/*.json`), the CI workflows or the installer scripts.
- Large or repository-wide refactorings and renames.
