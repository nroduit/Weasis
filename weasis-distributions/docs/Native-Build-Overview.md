# Native installer build — overview & constraints

How the platform installers are produced by `.github/workflows/build-installer.yml`, and the
binary-compatibility constraints (glibc, architecture, toolchain) that result.

> TL;DR — `jpackage` only *assembles and packages* prebuilt binaries (Temurin JDK, OpenCV, JogAmp);
> it never compiles against the build host's libc. The effective **glibc floor of the shipped Linux
> packages is ~`GLIBC_2.17`**, fixed by the bundled natives, **independent of the build OS**.

## 1. Build jobs

The workflow (`workflow_dispatch` only) runs three job groups after the shared Maven `build`:

| Job | Runner | Produces | Arch(es) | Notes |
|-----|--------|----------|----------|-------|
| `build` | `ubuntu-latest` | `weasis-native.zip` (shared payload) | n/a | Maven `clean install` + `compressXZ`, JDK 26 Temurin |
| `jpackage` | `macos-*`, `windows-latest` | `.msi`, `.pkg` | host arch only | jpackage inline in the runner; **macOS + Windows only** |
| `linux-multiarch` | `ubuntu-latest` + Docker | `.deb` / `.rpm` | x86-64 **and** aarch64 | runs `package-weasis.sh` inside a container |

### Linux is built only in the Docker job

The `jpackage` job matrix produces **only `macosx` and `windows`** entries — it never builds Linux.
All Linux packages come from the **`linux-multiarch`** job, which builds and runs `weasis/builder` via
Docker Buildx + QEMU for both `linux/amd64` and `linux/arm64`, invoking the canonical
`script/package-weasis.sh`.

> There is intentionally a single Linux path. (Earlier revisions carried an in-runner x86-64 fallback
> branch in the `jpackage` job; it was unreachable dead code — the matrix has no `linux` entry — and was
> removed in favour of the Docker/script path.)

Why Docker is used for the Linux packages:
- **Multi-arch**: `ubuntu-latest` is x86-64; native **aarch64** `.deb`/`.rpm` require QEMU emulation in a container.
- **Reproducible toolchain**: a pinned JDK / jpackage / `rpmbuild` / `fakeroot`, frozen by the image.

It is **not** used to change glibc compatibility — see §4.

## 2. jpackage single-step on Linux (icon fix)

In `package-weasis.sh` the Linux `.deb`/`.rpm` are built in a **single** jpackage step directly from
`--input` (not the two-step `--type deb --app-image <prebuilt-image>` path). On **JDK 26+** the two-step
path fails to resolve the launcher icons from `--resource-dir` and falls back to the default
`JavaApp.png` for every launcher (main **and** Dicomizer). The single-step build resolves them correctly.

Consequence: on Linux the standalone `--type app-image` step is skipped (only needed for the other
platforms or `--no-installer` builds), and the `--java-options` are passed to the package step instead.

## 3. Docker image

Defined in `weasis-distributions/docker/Dockerfile`:

| Property | Value |
|----------|-------|
| Base | `ubuntu:26.04` (codename *resolute*) — **LTS** |
| Host glibc | `2.43` (Ubuntu 26.04) |
| JDK | Temurin **26.0.1+8** (downloaded, GPG + SHA256 verified) |
| Extra tooling | `fakeroot`, `rpm`, `unzip`, `xz-utils`, `bzip2`, `fontconfig`, locales |
| Tag | `weasis/builder:latest`, built per-arch via Buildx |

> The host glibc is **2.43**, but as shown below this does **not** become the floor of the produced packages.

## 4. glibc — the key constraint

`jpackage` does **not** compile or link anything against the host libc. It copies prebuilt binaries
(the launcher template + `libapplauncher.so` from the JDK, the jlink'd runtime, and the third-party
native `.so`s), patches metadata, and shells out to `dpkg-deb` / `rpmbuild`. The JDK ships **no
compiler** at all.

So the runtime glibc requirement is set by the **prebuilt binaries**, not the build OS. Measured max
`GLIBC_*` symbol version required by each native file in the Linux app-image:

| Component | Origin | Built by | Max `GLIBC_` required |
|-----------|--------|----------|-----------------------|
| `bin/Weasis`, `bin/Dicomizer` | `jpackageapplauncher` (JDK resource) | Temurin JDK | `2.14` |
| `lib/app/libapplauncher.so` | `libjpackageapplauncheraux.so` (JDK resource) | Temurin JDK | `2.14` |
| `lib/runtime/.../libjvm.so` + runtime `.so`s | jlink'd runtime | Temurin JDK | `2.15` |
| `libopencv_java.so` | `weasis-opencv-core-linux-*` bundle | OpenCV build | `2.16` |
| **jogamp** (`libgluegen_rt.so`, …) | `jogamp-linux-*` bundle | JogAmp build | **`2.17`** |

**Effective floor: `GLIBC_2.17`** — driven by jogamp's `libgluegen_rt.so` (OpenCV `2.16` just behind).
Build host is glibc `2.39`/`2.43`; **nothing references a symbol above `2.17`**.

Practical reach: `GLIBC_2.17` (Dec 2012) runs on essentially any Linux from ~2012 onward — RHEL/CentOS 7,
Ubuntu 14.04+, etc. — provided the declared package deps are met. On **aarch64** the floor is `2.17` by
construction (glibc gained AArch64 support in 2.17).

> To **lower** the floor you would rebuild jogamp/OpenCV against an older toolchain. To **raise** it you
> would have to introduce a *new* native compiled on the host — which this pipeline never does. Bumping
> the Docker base image (e.g. 24.04 → 26.04) has **zero effect** on the floor.

How to re-measure a native's floor:

```bash
objdump -T <file>.so | grep -oE 'GLIBC_[0-9]+\.[0-9]+' | sort -V | tail -1
```

## 5. Other per-platform constraints

| Platform | Constraint |
|----------|------------|
| **All** | JDK 26 Temurin; `--add-modules` list is OS-specific (Windows adds `jdk.crypto.mscapi`) |
| **Linux** | Declared deps: deb `libstdc++6, libgcc1`; rpm none. `rpmbuild` auto-generates `Requires` from the real ELF symbol versions → reflects `GLIBC_2.17`, not the host |
| **Linux/Docker** | `JAVA_TOOL_OPTIONS=-Djdk.lang.Process.launchMechanism=vfork` — default spawn mechanism is unreliable under QEMU emulation |
| **Linux** | `-Dsun.awt.disablegrab=true` works around unreliable X11 pointer grabs on XWayland (issue #819) |
| **macOS** | Native libs inside JARs are pre-signed (`--options runtime --timestamp`) before jpackage repacks them; `--mac-sign` is used at app-image stage only, **never** at the `pkg` stage (would re-sign dylibs without timestamp and break notarization) |
| **Windows** | Per-arch `--win-upgrade-uuid`; MSI built from the prebuilt app-image |

## 6. Shared `--java-options`

Each build path declares the runtime options as `customOptions` (per-OS, e.g. the splash screen and
Linux-only `-Dsun.awt.disablegrab=true`) plus `commonOptions` (identical everywhere):
`-Dgosh.port=17179`, `--enable-native-access=ALL-UNNAMED`, `-XX:MaxRAMPercentage=25`,
`-XX:+UseStringDeduplication`,
`-Djavax.accessibility.assistive_technologies=org.weasis.launcher.EmptyAccessibilityProvider`,
`-Djavax.accessibility.screen_magnifier_present=false`.

- **macOS / Windows** — declared once in the `jpackage` job's *"Build app binary"* step and applied to
  the app-image; the package step reuses that image.
- **Linux** — declared in `package-weasis.sh`, which (since the app-image step is skipped) passes them
  directly to the single-step `.deb`/`.rpm` build.
