# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build, test, format

Maven multi-module build. Requires **JDK 25+** (CI runs JDK 26 Temurin) and Maven **3.8.1+** (enforced by `maven-enforcer-plugin`). Version is supplied via CI-friendly properties in `weasis-parent/pom.xml`: `${revision}` (default `4.7.0`) and `${changelist}` (default `-SNAPSHOT`).

```bash
# Full build + tests (replicates the CI "Build" job, minus Sonar)
mvn -B -ntp verify -Pcoverage

# Build only (no tests)
mvn -B -ntp install -DskipTests

# Run a single test
mvn -pl <module-path> -am test -Dtest=ClassName#methodName

# Apply the mandatory license header + Google Java Format (1.33.0)
mvn spotless:apply
mvn spotless:check          # CI-equivalent verification
```

Surefire runs tests with `<parallel>all</parallel>` — tests must not share mutable static state. The `argLine` injects `-Djava.library.path=target/lib/<os>-<arch>` for OpenCV native libs, so tests that need them must run via Maven (not directly from the IDE without the same `java.library.path`).

### Native distribution / installers

The `weasis-distributions` module is **not** built by the root `mvn install`. To produce the `weasis-native.zip` used by `jpackage`:

```bash
mvn -B clean install
mvn -B -P compressXZ -f weasis-distributions clean package
# Then weasis-distributions/script/package-weasis.sh drives jpackage for the installer.
```

The full installer pipeline (Windows MSI, macOS PKG with notarization, Linux DEB/RPM via Docker for multi-arch) lives in `.github/workflows/build-installer.yml` and is gated to `workflow_dispatch`.

### Plugin archetype

```bash
cd archetype && mvn install                       # publish archetypes locally
mvn archetype:generate -DarchetypeCatalog=local   # generate weasis-plugin-{base,dicom}-viewer-archetype
```

## Architecture

Weasis is an **OSGi application** (Apache Felix 7) packaged as a desktop DICOM viewer. Understanding three things is enough to navigate the codebase:

### 1. Launcher vs. bundles

- `weasis-launcher/` is a **plain JAR**, not an OSGi bundle (see its POM comment). `org.weasis.launcher.AppLauncher` (extends `WeasisLauncher`) bootstraps the Felix framework, sets up logging via Logback to `~/.weasis/log/`, then `AutoProcessor` installs and starts the bundles listed in `weasis-launcher/conf/config.properties`. `EmptyAccessibilityProvider` is wired in via `-Djavax.accessibility.assistive_technologies=...` to suppress AT discovery on launch.
- Everything under `weasis-core`, `weasis-base/*`, `weasis-dicom/*`, `weasis-acquire/*`, `weasis-imageio/*`, `weasis-opencv/*` is an **OSGi bundle**. Each bundle has an `Activator` in a package ending in `.internal` and is built via `biz.aQute.bnd:bnd-maven-plugin`.
- The bnd convention used everywhere: `Export-Package: !<groupPkg>.internal, <groupPkg>.*` — anything in `.internal` is bundle-private. Don't add cross-bundle imports of `internal` packages.

### 2. Module layering

```
weasis-launcher  →  weasis-core  →  weasis-base/*       (generic image viewer)
                                 →  weasis-dicom/*      (DICOM viewer, codec, explorer, RT, 3D, SR, RT, send, QR, etc.)
                                 →  weasis-acquire/*    (image acquisition / dicomizer)
                                 →  weasis-imageio, weasis-opencv  (image/native deps)
```

`weasis-core` exposes the **plugin SDK**: API surface lives under `org.weasis.core.api.*` (gui, image, media, model, service, util) and `org.weasis.core.ui.*` (editor, docking, model, dialog, pref, serialize). Downstream viewers extend `DefaultView2d`, `ImageViewerEventManager`, `ImageViewerPlugin`, `SynchView`, etc. — when adding viewer features, expect to touch a base class in `weasis-core` and concrete subclasses in `weasis-base-viewer2d`, `weasis-dicom-viewer2d`, and possibly `weasis-dicom-3d/viewer3d`.

### 3. UI stack

- Swing + **FlatLaf** themes (`com.formdev:flatlaf*`) for L&F.
- **MigLayout** for layout — see `weasis-core/docs/MigLayoutModel-Best-Practices.md` for the project's `MigLayoutModel` conventions (weights, grow/shrink priorities) before touching layouts.
- **DockingFrames** (`org.weasis.thirdparty:docking-frames`) for the dockable viewer/tool windows.
- DICOM I/O is provided by the external `weasis-dicom-tools` library (`org.weasis.dicom.{mf,op,param,tool,util,web}`); it is excluded from JaCoCo and treated as a third-party dependency.

## Conventions to respect

- **Spotless will fail the build** if a file is missing the EPL-2.0 OR Apache-2.0 header or isn't Google-Java-Format'd. Run `mvn spotless:apply` before committing. Use `// @formatter:off` / `// @formatter:on` to opt out of formatting for a block.
- **i18n**: every user-facing module has a `Messages.java` + `messages.properties` pair. Properties files matching `messages*.properties` under `src/main/java/` are picked up as resources by `weasis-parent/pom.xml`. The translated bundles come from the external `weasis-i18n-dist` artifact at distribution time (see `weasis-distributions/pom.xml`), so do not hand-edit non-English `messages_*.properties` files in this repo.
- **Versions**: never hard-code `4.7.0-SNAPSHOT` in a child POM — always use `${project.parent.version}` or the `${revision}${changelist}` pair. The `flatten-maven-plugin` resolves these into the published POM.
- **Native libs (OpenCV / JOGL)**: per-OS native packages live in `weasis-opencv-core-*` and `jogamp-*` bundles. The installer workflow strips out non-target-arch payloads in `build-installer.yml`; if you add a new native bundle, follow that pattern or installers will balloon.
- **Sonar excludes** (in root `pom.xml`): `Messages.java`, `Activator.java`, `module-info.java`, `package-info.java` are coverage-excluded; `archetype/`, `snap/`, `weasis-distributions/` are analysis-excluded.
- **Javadoc**: keep it minimal. **Private methods**: one-line comment max, or none if the name and signature are self-explanatory. **Public/protected API**: as compact as possible — one short sentence describing intent, `@param` / `@return` / `@throws` only when they add information the signature doesn't already convey. Never restate the method name in prose, never document obvious getters/setters, never leave `TODO`-style placeholders.
- **Modern Java (25)**: prefer `java.nio.file.Path` + `java.nio.file.Files` over `java.io.File` and `FileInputStream`/`FileOutputStream`. Use records, pattern matching (`switch`, `instanceof`), sealed types, text blocks, `var` for obvious local types, and `List.of` / `Map.of` / `Stream.toList()` over legacy collection idioms. Favor `Optional` returns over nullable returns at API boundaries; do not wrap fields or parameters in `Optional`.
- **Code quality**: favor readability and maintainability — small focused methods, expressive names, early returns. Remove redundant code (unused imports/locals, dead branches, duplicated logic, defensive null checks for values that cannot be null, comments that duplicate the code). Prefer extracting a private helper over copy-pasting a block.
- **Tests**: use **JUnit 6** (`org.junit.jupiter.*`) and **Mockito** only. Do **not** add AssertJ (`org.assertj.*`) — use JUnit's built-in `Assertions` (`assertEquals`, `assertThrows`, `assertAll`, …) for assertions. Tests follow the same Spotless / formatting rules as production code.
