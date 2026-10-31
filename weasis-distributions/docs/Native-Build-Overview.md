# Native installer build — overview & constraints

How the platform installers are produced by `.github/workflows/build-installer.yml`, and the
binary-compatibility constraints that contributors must respect when changing the packaging.

> TL;DR — `jpackage` only *assembles and packages* prebuilt binaries (Temurin JDK, OpenCV);
> it never compiles against the build host's libc. The **glibc floor of the Linux packages is
> `GLIBC_2.16` on x86-64 and `GLIBC_2.17` on aarch64**, fixed by the bundled natives,
> **independent of the build OS**.

## 1. Build jobs

The workflow is triggered manually (`workflow_dispatch`). Its `platforms` input selects `all`, `none`
(only the shared payload) or a comma-separated subset of `windows-x86-64`, `macosx-x86-64`,
`macosx-aarch64`, `linux-x86-64`, `linux-aarch64`.

| Job | Runs on | Produces |
|-----|---------|----------|
| `setup` | `ubuntu-latest` | the `jpackage` and Linux build matrices, from the `platforms` input |
| `build` | `ubuntu-latest` | `weasis-native.zip`, the shared payload (Maven build, JDK 27 Temurin) |
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
run time is therefore the highest symbol version referenced by those prebuilt binaries: OpenCV
(`2.16`), the JDK launcher and runtime (`2.14`–`2.15`). The 3D viewer adds no native library of its
own: it calls the system OpenGL, EGL, WGL or CGL libraries through the Foreign Function & Memory API.

`GLIBC_2.16` (June 2012) covers RHEL/CentOS 7, Ubuntu 14.04 and later. On aarch64 the floor is
`2.17` by construction (glibc gained AArch64 support in 2.17).

Rules:
- Do not add a native binary compiled on the build host: it would raise the floor to the host glibc.
- Changing the Docker base image has **no effect** on the floor; lowering the floor requires rebuilding
  OpenCV with an older toolchain.
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
| **All** | JDK 27 Temurin (`JDK_VERSION` in the workflow), except the macOS x64 installer on JDK 26 (Temurin 27 has no macOS x64 build, so that platform runs without the AOT class-linking tier); the `--add-modules` list is OS-specific (Windows adds `jdk.crypto.mscapi`). JDK 27 is required for the AOT class-linking tier of the application and `weasis-cli` caches (JDK-8373392) |
| **Linux** | `.deb`/`.rpm` are built in a **single** jpackage step from `--input`. The two-step path (`--app-image` then `--type deb`) loses the launcher icons on JDK 26+ |
| **Linux/Docker** | `JAVA_TOOL_OPTIONS=-Djdk.lang.Process.launchMechanism=vfork`: the default process spawn mechanism is unreliable under QEMU |
| **macOS** | Native libraries inside JARs are signed (`--options runtime --timestamp`) before jpackage repacks them; `--mac-sign` is used at the app-image stage only, **never** at the `pkg` stage, which would break notarization |
| **Windows** | WiX 4+ is required by jpackage on JDK 26+; per-architecture `--win-upgrade-uuid`; the MSI is built from the app-image |

`weasis-distributions/script/launch-options.sh` is the single source of truth for the JDK modules
and the `--java-options` of the native launchers. The workflow (macOS, Windows) and
`package-weasis.sh` (Linux) both source it with the platform as argument, so the launchers cannot
drift apart. The module list is read from `build.properties` (`jdk.modules`, filtered from the
pom), the same value the package declares in `weasis.requires.modules`.

The native launchers start the **bootstrap** (`weasis-bootstrap.jar`, stage 1), so their options
are those of a small, short-lived JVM; the options of the application (stage 2) are served by the
package (`weasis.launcher.jvm-options` in `base.json`, also from the pom) and applied by the
bootstrap. macOS is the exception: the bootstrap runs the launcher set in its own process there,
so its `.cfg` carries the application options. The **Dicomizer** and **weasis-cli** launchers do
not inherit the command-line options: jpackage discards them for a launcher whose properties file
sets `java-options`, so `resources/<platform>/*-launcher.properties` repeat the stage-1 set and
name the entry class (`weasis.bootstrap.entry`). See `weasis-launcher/docs/Launcher-Overview.md`
(“Bootstrap and Launcher Set”) for the runtime side.

## 4. The launcher set in the package and in the image

The distribution build copies the launcher jar and the common libraries, plain, into
`bin-dist/weasis/launcher/` next to their `launcher.sha256` manifest (written by
`org.weasis.bootstrap.tool.Sha256Sum`), and declares them in `base.json`
(`weasis.launcher.url`, `.libs`, `.sha256`). The same libraries stay in `bundle/` as `.jar.xz`
for the development launch and remote codebases. A hub serves `launcher/` like the bundles.

Before jpackage runs, the packaging step renames `launcher/*.jar` to `*.jar.embedded`: jpackage
adds every plain `*.jar` found under `--input` to the class path of the native launchers, which
must carry `weasis-bootstrap.jar` only. The bootstrap builds the class path of stage 2 itself, from
the embedded set or from a set downloaded into the per-user cache. Any other plain jar under
`--input` fails the build, so **`-P compressXZ` is required** when building `weasis-distributions`
for native packaging (CI always uses it). A site that signs its own launcher sets drops the
certificates in `launcher/truststore/` through `prepare-input-site.sh`.

## 5. Deployment-specific customisation

Both build paths look for two optional scripts next to `package-weasis.sh`. They are not part of the
public distribution; when absent, the stock installers are built.

| Script | Called | Purpose |
|--------|--------|---------|
| `script/launch-options-site.sh` | sourced last by `launch-options.sh` | appends to the common launch options (e.g. a configuration service URL) |
| `script/prepare-input-site.sh` | before jpackage, with `bin-dist/weasis` as argument | adapts the packaged payload |

`weasis-native.zip` is uploaded by the `build` job before any packaging, so `prepare-input-site.sh`
never modifies the published server payload.

## 6. The jpackage runtime and the AOT cache

- `--jlink-options "--strip-debug --no-man-pages --no-header-files --generate-cds-archive"`
  replaces jpackage's defaults **on purpose**: the default set includes
  `--strip-native-commands`, but `bin/java` must remain because the bootstrap spawns
  `<runtime>/bin/java` for stage 2 and the AOT assembly phase (`-XX:AOTCacheOutput`) spawns it
  at JVM exit.
- `--generate-cds-archive` ships a base CDS archive (`lib/server/classes.jsa`) that speeds up
  the stage-1 bootstrap JVM and the first (untrained) launch of stage 2.

## 7. Files

| File | Role |
|------|------|
| `.github/workflows/build-installer.yml` | CI pipeline |
| `weasis-distributions/script/package-weasis.sh` | jpackage build for one platform (Linux in CI, any platform locally) |
| `weasis-distributions/script/launch-options.sh` | JDK modules and the options of the stage-1 launchers |
| `weasis-distributions/script/build.properties` | versions, `jdk.modules` and `bootstrap.protocol`, filtered from the pom |
| `weasis-distributions/docker/Dockerfile` | `weasis/builder` image for the Linux packages |
| `weasis-distributions/script/resources/<platform>/` | icons, installer resources, Dicomizer and weasis-cli launcher properties |
