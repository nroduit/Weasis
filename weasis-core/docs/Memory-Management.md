# Weasis Memory Management

A developer guide to how Weasis uses memory, why a DICOM viewer needs more than
the JVM heap, how the image caches and the 3D viewer share a common budget, and
which knobs are available to tune it.

## Table of Contents

- [Why memory needs explicit management](#why-memory-needs-explicit-management)
- [The three memory arenas](#the-three-memory-arenas)
- [The native-memory budget](#the-native-memory-budget)
- [The image caches](#the-image-caches)
- [Pinning: protecting the visible images](#pinning-protecting-the-visible-images)
- [Global coordination between consumers](#global-coordination-between-consumers)
- [The 3D viewer](#the-3d-viewer)
- [Tuning memory parameters](#tuning-memory-parameters)
- [Packaging and distribution settings](#packaging-and-distribution-settings)
- [Reference: files and symbols](#reference-files-and-symbols)
- [Troubleshooting](#troubleshooting)

## Why memory needs explicit management

A DICOM viewer routinely handles studies far larger than the JVM heap: a single
CT or MR series is hundreds of slices, a digital mammography image is tens of
megabytes, a 3D volume is hundreds of megabytes. The decoded pixel data does
**not** live on the Java heap — it is allocated by native code (OpenCV) and by
the GPU pipeline. The garbage collector neither sees nor reclaims it.

The consequence: the JVM's own ergonomics protect only the heap. Nothing in the
JVM knows that OpenCV is allocating gigabytes of pixel buffers next to it. Weasis
therefore manages that native memory itself — sizing it from the real machine,
bounding it, and coordinating the consumers so they do not collectively overrun
physical RAM.

> **Key fact:** `Runtime.getRuntime().maxMemory()` describes only the heap. A
> decoded image is not on the heap, so sizing a native buffer pool from
> `maxMemory()` has no relation to how much memory the machine actually has.
> Weasis sizes native memory from physical RAM instead.

## The three memory arenas

Weasis touches memory in three places that the JVM does not manage as one pool.
Keeping them distinct is the first step to reasoning about a memory problem.

| Arena | What lives there | Reclaimed by | Bounded by |
|---|---|---|---|
| **JVM heap** | Swing UI, OSGi/Felix, DICOM metadata, Java pixel copies | Garbage collector | `-Xmx` / `-XX:MaxRAMPercentage` |
| **OpenCV native heap** | Decoded image pixel buffers (`org.opencv.core.Mat` / `ImageCV`), off-heap C++ `malloc` | Explicit `release()`, driven by the image caches | The native-memory budget |
| **GPU and native staging** | 3D volume textures in VRAM; off-heap FFM `Arena` slice staging | GL driver; explicit `Arena` close | VRAM; the volume staging chunk size |

The heap is sized by the JVM. The other two are sized and bounded by Weasis,
which is what the rest of this document describes.

## The native-memory budget

`SystemMemory` (`org.weasis.core.api.util.SystemMemory`) computes a single
budget for off-heap image memory, derived from the **physical machine**:

```
budget = totalPhysicalRAM * percent  -  maxHeap  -  OS_RESERVE
```

- **`totalPhysicalRAM`** comes from `com.sun.management.OperatingSystemMXBean`.
  It is **container/cgroup aware** on modern JDKs — inside Docker or Kubernetes
  it returns the cgroup limit, not the host RAM — and works uniformly on macOS,
  Windows and Linux.
- **`percent`** defaults to **50 %** of physical RAM.
- **`maxHeap`** is subtracted because the heap and the native buffers compete
  for the same physical RAM.
- **`OS_RESERVE`** is a fixed **512 MB** left to the OS, the native libraries
  and short-lived uncounted buffers.
- The result is floored at **256 MB** so Weasis stays usable on small machines.

If the OS bean is unavailable, `SystemMemory` falls back to a `maxHeap / 2`
estimate. The budget is computed once at startup.

Indicative budgets at the default 50 % setting:

| Physical RAM | Native-memory budget |
|---|---|
| 4 GB | ~0.5 GB |
| 8 GB | ~1.5 GB |
| 16 GB | ~3.5 GB |
| 32 GB | ~7.5 GB |

## The image caches

Decoded images are kept in a `NativeCache` — an access-ordered, size-bounded
cache that owns the lifetime of native pixel buffers.

- **`ImageElement`** keeps full decoded images in a process-wide `NativeCache`
  sized to the native-memory budget above.
- **`Thumbnail`** keeps thumbnails in a separate, small `NativeCache` (a fixed
  30 MB).

The budget is a **soft limit**. When a `put` would exceed it,
`NativeCache.expungeStaleEntries()` evicts entries in **least-recently-used
order** and calls `release()` on their native buffers immediately (rather than
waiting for non-deterministic finalization). An evicted image is **reloaded
transparently** on its next access: `ImageElement.getImage()` re-decodes from
the source on a cache miss. Eviction is therefore safe — it costs a reload, not
data.

`NativeCache` is intentionally **not** a `java.util.Map`: callers only store,
fetch and drop entries, and exposing a `Map` view would let code mutate the
backing store while bypassing the native-memory accounting.

## Pinning: protecting the visible images

A pure LRU cache has one dangerous failure mode: evicting an image **while a
viewport is still displaying it**. `NativeCache` prevents this with **pinning**.

- `NativeCache.pin(key)` / `unpin(key)` mark an entry as in use. A pinned entry
  is **never evicted**, even under memory pressure; `expungeStaleEntries()`
  skips it and keeps scanning.
- Pins are **reference counted**: an image shown in several viewports at once
  stays pinned until the last viewport releases it.

This splits the cache into two tiers:

- **Pinned** — the image currently displayed in a live viewport. Protected.
- **Unpinned** — everything else: previous frames of a series, images of
  backgrounded views. LRU-ordered, evicted oldest-first, reloaded on return.

### Where pinning happens

`DefaultView2d` holds exactly one pin per viewport. `DefaultView2d.setImage(img)`
calls `updatePinnedImage(img)`, which unpins the previously displayed image,
pins the new one, and records it in the `pinnedImage` field. It is driven by the
`img` **argument**, not by `imageLayer` state, so it stays correct even though
subclasses (`View2d`, `MprView`, …) reorder their `imageLayer` updates around
the `super.setImage()` call. Closing a view routes through `setImage(null)` (via
`disposeView()` → `setSeries(null)`), so no pin is left dangling.

`ImageElement.pinInCache()` / `unpinFromCache()` are the public entry points
that delegate to the cache.

Net effect: scrolling a 500-slice series keeps only the *visible* slice of each
open viewport pinned; everything else is evicted first under pressure and
reloaded when scrolled back to.

> **Residual window:** between the `NativeCache.put()` that stores a freshly
> decoded image and the `setImage()` that pins it, a concurrent `put` from
> another thread can still evict it. This is rare and self-healing —
> `ImageElement.getCacheImage()` detects a released buffer (`width() <= 0`) and
> reloads.

## Global coordination between consumers

Several subsystems allocate native memory: the image cache, the thumbnail cache,
the 3D volume loader. If each only watched its own budget, all of them could
stay within their own limit yet collectively overrun physical RAM.

`MemoryManager` (`org.weasis.core.api.util.MemoryManager`) closes that gap. It is
a process-wide **accountant** — it never allocates or frees memory itself:

- Every native-memory source implements `NativeMemoryConsumer` (one method,
  `usedNativeMemory()`) and registers with the `MemoryManager`.
- The manager holds the **global budget** (the `SystemMemory` value) and exposes
  the aggregate: `getUsedNativeMemory()`, `getAvailableNativeMemory()`,
  `isMemoryAvailable()`, `getPressure()`.

Consumers play one of two roles:

- **Elastic** — the `NativeCache` instances. Their `isMemoryAvailable()` checks
  *both* their own budget and the global budget, so they evict unpinned entries
  when either limit is hit. `expungeStaleEntries()` frees 5 % of the budget plus
  the larger of the local or global overage.
- **Rigid** — an in-progress 3D volume load. `VolumeBuilder` registers a
  consumer reporting the volume staging footprint for the duration of a build,
  and unregisters when the build finishes or is stopped.

Net effect: while a 3D volume loads, aggregate native usage rises; the next
image `put` makes the image cache evict unpinned slices to yield room — instead
of both subsystems growing until the OS swaps. When the build ends, the staging
consumer unregisters and the caches expand again.

> **Eviction is `put`-driven:** a cache trims on its next insertion, not the
> instant global pressure rises. In practice, 2D navigation during a 3D load
> produces `put`s frequently enough; a background trimmer would be a future
> refinement.
>
> **Heap-resident, by design:** the MPR `Volume` stores the reconstructed
> volume in a `ChunkedArray` of Java arrays — on the **JVM heap**, with a
> memory-mapped-file fallback when heap allocation fails. It is governed by the
> GC and `-Xmx`, not by the native budget, and is intentionally *not* a
> `MemoryManager` consumer (counting it there would double-count the heap).
> What stays uncounted on the native side is short-lived: the per-slice OpenCV
> `Mat`s of a volume build (released immediately after use) and intermediate
> processed images such as window/level output and zoom.

## The 3D viewer

The 3D viewer (`weasis-dicom-3d`) has its own memory profile and does **not**
use `java.nio` direct buffers for volumes:

- **GPU / VRAM** — the volume is uploaded as a 3D texture
  (`glTexImage3D` / `glTexSubImage3D`). This memory lives on the GPU, bounded by
  the driver; `VolumeBuilder` checks `glGetError()` after each upload.
- **Off-heap staging** — slices are copied into Foreign Function & Memory
  `Arena` segments (`TextureSliceDataBuffer`) before each GPU upload.
  `VolumeBuilder` uploads in chunks; the chunk size is bounded by
  `SystemMemory.getVolumeStagingMemory()` (5 % of physical RAM, clamped to
  64 MB–512 MB).

A key implication for tuning: **`-XX:MaxDirectMemorySize` does not apply here.**
That flag bounds only the `java.nio` direct buffer pool; it has no effect on FFM
`Arena` memory, and no JVM flag limits FFM memory. The only control is the
application-level staging chunk size. (The FFM API requires
`--enable-native-access=ALL-UNNAMED`, which is already set in the packaged
`java-options`.)

A smaller staging chunk lowers the transient native spike during a 3D load
(slices are briefly held both as OpenCV `Mat`s and as the `Arena` copy); a
larger chunk means fewer GPU upload round-trips.

## Tuning memory parameters

All parameters are JVM system properties — pass them as `-Dkey=value`, either on
the command line or in the launcher / jpackage `java-options`. They are read
once at startup.

| Property | Effect | Default |
|---|---|---|
| `weasis.native.memory` | Absolute native-memory budget, in bytes. Overrides the percentage. | unset |
| `weasis.native.memory.percent` | Native-memory budget as a percentage (1–90) of physical RAM. | 50 |
| `weasis.volume.staging.memory` | Bytes staged per 3D upload chunk (clamped to 64 MB–512 MB). | 5 % of RAM |

Heap sizing is a JVM option, not a Weasis property:

| Option | Effect |
|---|---|
| `-XX:MaxRAMPercentage=<n>` | Maximum heap as a percentage of physical RAM (Weasis ships with `25`). |
| `-Xmx<size>` | Absolute maximum heap. Use only when a fixed value is required. |

### Guidance

- **Dedicated imaging workstation** — raise `weasis.native.memory.percent` to
  `~70` so more series stay cached and reloads are rare.
- **Shared or virtualized host, many other applications** — lower it to
  `30`–`40` to leave room for the rest of the system.
- **Reproducible behaviour across a fleet** — set an absolute
  `weasis.native.memory` value instead of a percentage.
- **Heap vs native trade-off** — the heap is subtracted from the native budget,
  so raising `-XX:MaxRAMPercentage` shrinks the native cache and vice versa.
  Keep `heap% + native%` comfortably under 100 % of RAM.
- **3D-heavy use on a tight machine** — lower `weasis.volume.staging.memory` to
  reduce the transient spike when a volume loads.

## Packaging and distribution settings

The installers are built with `jpackage`. Memory-related settings live in two
places that **must stay in sync**:

- `weasis-distributions/script/package-weasis.sh` — `commonOptions` (java
  options) and `JDK_MODULES_BASE` (jlink module list).
- `.github/workflows/build-installer.yml` — its own inline `commonOptions` and
  `JDK_MODULES`, used for the Windows and macOS jobs; the Linux job calls
  `package-weasis.sh`.

Shipped `java-options`:

- `-XX:MaxRAMPercentage=25` — makes the heap sizing explicit and stable.
- `-XX:+UseStringDeduplication` — trims heap use of the metadata-heavy UI.
- `--enable-native-access=ALL-UNNAMED` — required by the FFM `Arena` API.

**Required jlink module:** `jdk.management` must be in the module list, because
`SystemMemory` uses `com.sun.management.OperatingSystemMXBean`. Without it the
bundled runtime lacks the class and `SystemMemory` fails at startup.

**OSGi note:** `com.sun.management` is exported to bundles via
`framework.system.packages.extra.basic` in **both** `base.json` files —
`weasis-launcher/conf/base.json` (development) and
`weasis-distributions/etc/config/base.json` (shipped app).

## Reference: files and symbols

| File | Role |
|---|---|
| `weasis-core/.../api/util/SystemMemory.java` | Cross-platform RAM probe, native-budget and staging-size formulas |
| `weasis-core/.../api/util/MemoryManager.java` | Process-wide native-memory accountant; global budget and pressure |
| `weasis-core/.../api/util/NativeMemoryConsumer.java` | Interface a native-memory source implements to be tracked |
| `weasis-core/.../api/util/ResourceMonitor.java` | Session + cross-session resource metrics; logger alerts on degradation |
| `weasis-core/.../api/util/ResourceAdvisor.java` | Verdict (sub-optimal / optimal / abundant) and upgrade recommendation |
| `weasis-core/.../api/util/GraphicsInfo.java` | Holds the OpenGL renderer detected by the 3D viewer for other modules to display |
| `weasis-core/.../api/media/data/NativeCache.java` | Bounded LRU cache, native-memory accounting, pinning, global-pressure aware |
| `weasis-core/.../api/media/data/ImageElement.java` | Owns the image `NativeCache`; `pinInCache()` / `unpinFromCache()` |
| `weasis-core/.../ui/editor/image/DefaultView2d.java` | `updatePinnedImage()` — one pin per viewport |
| `weasis-dicom-3d/.../viewer3d/vr/VolumeBuilder.java` | Chunked 3D volume upload; registers the staging consumer |
| `weasis-dicom-3d/.../viewer3d/vr/TextureSliceDataBuffer.java` | Off-heap FFM `Arena` staging buffer for volume slices |
| `weasis-launcher/conf/base.json` | OSGi system packages (development) |
| `weasis-distributions/etc/config/base.json` | OSGi system packages (shipped app) |
| `weasis-distributions/script/package-weasis.sh` | jpackage options and jlink modules |
| `.github/workflows/build-installer.yml` | CI jpackage options and jlink modules |

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `NoClassDefFoundError: com.sun.management...` at startup | The bundled runtime is missing the `jdk.management` jlink module — add it to the module list. |
| Frequent image reloads, sluggish scrolling | Native budget too small for the workload — raise `weasis.native.memory.percent`. |
| OS swapping, or the process being killed | Heap + native budget exceed physical RAM — lower `weasis.native.memory.percent` and/or `-XX:MaxRAMPercentage`. |
| Reloads only while a 3D volume loads | Expected — the image cache is yielding room to the 3D build. Lower `weasis.volume.staging.memory` if too aggressive, or raise the overall budget. |
| 3D volume fails to load on a large dataset | Likely VRAM exhaustion — check the logs for the `glGetError()` message; not tunable from the JVM. |

To inspect memory at runtime: open **Help → System resources** for a live
dashboard — JVM heap, native image memory and CPU pressure, the GPU renderer
(once a 3D view has run; a software renderer is flagged), a verdict on whether
the hardware fits the user's practice (accumulated across sessions), the limiting
events observed, and an exportable upgrade recommendation. The verdict and the
limiting events are also written to `~/.weasis/log/` — `ResourceMonitor` logs a
warning when the situation first becomes sub-optimal, when an out-of-memory error
occurs, and when a volume spills to disk. Programmatically, `ResourceMonitor`
collects the metrics, `ResourceAdvisor` produces the verdict, and the cumulative
statistics persist in `~/.weasis/resource-stats.properties`.
