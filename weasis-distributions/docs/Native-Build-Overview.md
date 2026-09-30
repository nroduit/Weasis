# Native installer build — overview & constraints

How the platform installers are produced by `.github/workflows/build-installer.yml`, and the
binary-compatibility constraints that contributors must respect when changing the packaging.

> TL;DR — `jpackage` only *assembles and packages* prebuilt binaries (Temurin JDK, OpenCV, JogAmp);
> it never compiles against the build host's libc. The **glibc floor of the Linux packages is
> `GLIBC_2.17`**, fixed by the bundled natives, **independent of the build OS**.

## 1. Build jobs

The workflow is triggered manually (`workflow_dispatch`). Its `platforms` input selects `all`, `none`
(only the shared payload) or a comma-separated subset of `windows-x86-64`, `macosx-x86-64`,
`macosx-aarch64`, `linux-x86-64`, `linux-aarch64`.

| Job | Runs on | Produces |
|-----|---------|----------|
| `setup` | `ubuntu-latest` | the `jpackage` and Linux build matrices, from the `platforms` input |
| `build` | `ubuntu-latest` | `weasis-native.zip`, the shared payload (Maven build, JDK 27) |
| `jpackage` | macOS and Windows runners | `.pkg` (macOS x86-64 and aarch64), `.msi` (Windows x86-64) |
| `linux-multiarch` | `ubuntu-latest` + Docker (Buildx, QEMU) | `.deb` and `.rpm` for x86-64 and aarch64 |

Every installer is published as a workflow artifact with a `.sha256` file.

```
setup ─┐
build ─┴─► weasis-native.zip ─┬─► jpackage (macOS, Windows)  ─► .pkg / .msi
                              └─► linux-multiarch (Docker)   ─► .deb / .rpm
```

Linux is built **only** in the Docker job, which runs `weasis-distributions/script/package-weasis.sh`
inside the `weasis/builder` image (`weasis-distributions/docker/Dockerfile`). Docker provides the
aarch64 build (through QEMU) and a pinned toolchain (JDK, `rpmbuild`, `fakeroot`); it is **not** used
to control glibc compatibility.

## 2. Binary compatibility

### Linux: glibc floor

`jpackage` does not compile or link anything against the host libc: it copies the JDK launcher and
runtime and the third-party native libraries, then calls `dpkg-deb` / `rpmbuild`. The glibc required at
run time is therefore the highest symbol version referenced by those prebuilt binaries: JogAmp
(`GLIBC_2.17`), OpenCV (`2.16`), the JDK launcher and runtime (`2.14`–`2.15`).

`GLIBC_2.17` (December 2012) covers RHEL/CentOS 7, Ubuntu 14.04 and later. On aarch64 it is the
floor by construction (glibc gained AArch64 support in 2.17).

Rules:
- Do not add a native binary compiled on the build host: it would raise the floor to the host glibc.
- Changing the Docker base image has **no effect** on the floor; lowering the floor requires rebuilding
  JogAmp and OpenCV with an older toolchain.
- The `.deb` declares `libstdc++6, libgcc1`; the `.rpm` declares none and lets `rpmbuild` derive
  `Requires` from the actual ELF symbol versions.

Check the floor of a native library with:

```bash
objdump -T <file>.so | grep -oE 'GLIBC_[0-9]+\.[0-9]+' | sort -V | tail -1
```

### macOS and Windows

The same principle applies: the installers bundle the prebuilt Temurin runtime and native libraries, so
their supported OS versions follow those binaries, not the runner image.

## 3. Packaging constraints

| Platform | Constraint |
|----------|------------|
| **All** | JDK 27 (`JDK_VERSION` in the workflow): Temurin when published, otherwise the OpenJDK GA build of jdk.java.net with pinned checksums, which has no macOS x86-64 build; the `--add-modules` list is OS-specific (Windows adds `jdk.crypto.mscapi`). JDK 27 is required for the AOT class-linking tier of the application and `weasis-cli` caches (JDK-8373392) |
| **Linux** | `.deb`/`.rpm` are built in a **single** jpackage step from `--input`. The two-step path (`--app-image` then `--type deb`) loses the launcher icons on JDK 26+ |
| **Linux/Docker** | `JAVA_TOOL_OPTIONS=-Djdk.lang.Process.launchMechanism=vfork`: the default process spawn mechanism is unreliable under QEMU |
| **macOS** | Native libraries inside JARs are signed (`--options runtime --timestamp`) before jpackage repacks them; `--mac-sign` is used at the app-image stage only, **never** at the `pkg` stage, which would break notarization |
| **Windows** | WiX 4+ is required by jpackage on JDK 26+; per-architecture `--win-upgrade-uuid`; the MSI is built from the app-image |

`script/launch-options.sh` is the single source of truth for `JDK_MODULES`, `customOptions` (per-OS,
e.g. the splash screen and Linux-only `-Dsun.awt.disablegrab=true`) and `commonOptions` (identical
everywhere): `--enable-native-access=ALL-UNNAMED`, `-XX:MaxRAMPercentage=25`,
`-XX:+UseStringDeduplication`, `-Dweasis.launch.type=NATIVE`,
`-Djavax.accessibility.assistive_technologies=org.weasis.launcher.EmptyAccessibilityProvider`,
`-Djavax.accessibility.screen_magnifier_present=false`.

`weasis-distributions/script/launch-options.sh` is the single source of truth for the JDK modules and
the launcher `--java-options` (per-OS options and options common to all platforms). The workflow
(macOS, Windows) and `package-weasis.sh` (Linux) both source it with the platform as argument, so the
launchers cannot drift apart.

The **Dicomizer** launcher does not inherit these options: jpackage discards the command-line
`--java-options` for a launcher whose properties file sets `java-options`. Any option it needs must be
repeated in `resources/<platform>/dicomizer-launcher.properties`; the Dicomizer reads its configuration
from `conf/dicomizer.json`.

## 5. Deployment-specific customisation

Both build paths look for two optional scripts next to `package-weasis.sh`. They are not part of the
public distribution; when absent, the stock installers are built.

| Script | Called | Purpose |
|--------|--------|---------|
| `script/launch-options-site.sh` | sourced last by `launch-options.sh` | appends to the common launch options (e.g. a configuration service URL) |
| `script/prepare-input-site.sh` | before jpackage, with `bin-dist/weasis` as argument | adapts the packaged payload |

`weasis-native.zip` is uploaded by the `build` job before any packaging, so `prepare-input-site.sh`
never modifies the published server payload.

## 6. Common-library split and the AOT cache

Before jpackage runs, every `bundle/*.jar.xz` is inspected: jars **without** a `Plugin-Id`
manifest header (or `plugin.properties` `plugin.id`) are common libraries — they are
decompressed into `common-lib/` inside the `--input` tree and jpackage automatically adds
them to the application classpath in the generated `.cfg`. pf4j plugins must remain
`.jar.xz` in `bundle/` to stay **off** the classpath — the split step fails hard if a plain
jar remains there, so **`-P compressXZ` is required** when building `weasis-distributions`
for native packaging (CI always uses it).

The classpath placement is what makes the common libraries reliably eligible for the JDK AOT
cache. Two related constraints on the jpackage runtime:

- `--jlink-options "--strip-debug --no-man-pages --no-header-files --generate-cds-archive"`
  replaces jpackage's defaults **on purpose**: the default set includes
  `--strip-native-commands`, but `bin/java` must remain because the AOT assembly phase
  (`-XX:AOTCacheOutput`) spawns `<java.home>/bin/java` at JVM exit.
- `--generate-cds-archive` ships a base CDS archive (`lib/server/classes.jsa`) that speeds up
  the first (untrained) launch and the AOT stage-1 relauncher.

See `weasis-launcher/docs/Launcher-Overview.md` (“Launch Pipeline”) for the runtime side.

## 7. Files

| File | Role |
|------|------|
| `.github/workflows/build-installer.yml` | CI pipeline |
| `weasis-distributions/script/package-weasis.sh` | jpackage build for one platform (Linux in CI, any platform locally) |
| `weasis-distributions/script/launch-options.sh` | JDK modules and launcher options |
| `weasis-distributions/docker/Dockerfile` | `weasis/builder` image for the Linux packages |
| `weasis-distributions/script/resources/<platform>/` | icons, installer resources, Dicomizer launcher properties |

## 7. Common-library split and the AOT cache

Before jpackage runs, every `bundle/*.jar.xz` is inspected: jars **without** a `Plugin-Id`
manifest header (or `plugin.properties` `plugin.id`) are common libraries — they are
decompressed into `common-lib/` inside the `--input` tree and jpackage automatically adds
them to the application classpath in the generated `.cfg`. pf4j plugins must remain
`.jar.xz` in `bundle/` to stay **off** the classpath — the split step fails hard if a plain
jar remains there, so **`-P compressXZ` is required** when building `weasis-distributions`
for native packaging (CI always uses it).

The classpath placement is what makes the common libraries reliably eligible for the JDK AOT
cache. Two related constraints on the jpackage runtime:

- `--jlink-options "--strip-debug --no-man-pages --no-header-files --generate-cds-archive"`
  replaces jpackage's defaults **on purpose**: the default set includes
  `--strip-native-commands`, but `bin/java` must remain because the AOT assembly phase
  (`-XX:AOTCacheOutput`) spawns `<java.home>/bin/java` at JVM exit.
- `--generate-cds-archive` ships a base CDS archive (`lib/server/classes.jsa`) that speeds up
  the first (untrained) launch and the AOT stage-1 relauncher.

See `weasis-launcher/docs/Launcher-Overview.md` (“Launch Pipeline”) for the runtime side.
