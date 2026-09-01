# Weasis Memory Management

How Weasis bounds the memory a DICOM viewer needs beyond the JVM heap, and how the image caches
and the 3D viewer share one native-memory budget.

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

## Why memory needs explicit management

Decoded pixel data does **not** live on the Java heap: it is allocated by native code (OpenCV)
and by the GPU pipeline, where the garbage collector neither sees nor reclaims it. Weasis
therefore sizes, bounds and coordinates that native memory itself.

> **Key fact:** `Runtime.getRuntime().maxMemory()` describes only the heap. Native buffers are
> sized from physical RAM, never from `maxMemory()`.

## The three memory arenas

| Arena | What lives there | Reclaimed by | Bounded by |
|---|---|---|---|
| **JVM heap** | Swing UI, OSGi/Felix, DICOM metadata, Java pixel copies, the MPR `Volume` | Garbage collector | `-Xmx` / `-XX:MaxRAMPercentage` |
| **OpenCV native heap** | Decoded image pixel buffers (`org.opencv.core.Mat` / `ImageCV`) | Explicit `release()`, driven by the image caches | The native-memory budget |
| **GPU and native staging** | 3D volume textures in VRAM; off-heap FFM `Arena` slice staging | GL driver; explicit `Arena` close | VRAM; the volume staging chunk size |

## The native-memory budget

`SystemMemory` (`org.weasis.core.api.util`) computes one budget for off-heap image memory,
once at startup, from the physical machine:

```
budget = totalPhysicalRAM * percent  -  maxHeap  -  OS_RESERVE
```

Physical RAM comes from `com.sun.management.OperatingSystemMXBean`, which is container (cgroup)
aware; `HardwareInfo` (OSHI) is only a fallback because it reports the whole host inside a
container. The heap is subtracted because heap and native buffers compete for the same RAM, and
the result has a floor so Weasis stays usable on small machines.

## The image caches

Decoded images are kept in a `NativeCache`, an access-ordered, size-bounded cache that owns the
lifetime of native pixel buffers. `ImageElement` holds the process-wide image cache sized to the
native budget; `Thumbnail` holds a separate small one.

The budget is a soft limit: when a `put` would exceed it, `NativeCache.expungeStaleEntries()`
evicts entries in least-recently-used order and calls `release()` on them immediately. An evicted
image is reloaded transparently on its next access (`ImageElement.getImage()`), so eviction costs
a reload, not data.

`NativeCache` is intentionally **not** a `java.util.Map`: exposing a map view would let code
mutate the store while bypassing the native-memory accounting.

## Pinning: protecting the visible images

A pinned entry is never evicted. `NativeCache.pin(key)` / `unpin(key)` are reference counted, so
an image shown in several viewports stays pinned until the last one releases it.
`ImageElement.pinInCache()` / `unpinFromCache()` are the public entry points.

Rules for view code:

- `DefaultView2d` holds exactly one pin per viewport. `setImage(img)` calls
  `updatePinnedImage(img)`, which unpins the previous image and pins the new one.
- The pin follows the `img` **argument**, not `imageLayer` state, so subclasses (`View2d`,
  `MprView`, …) may reorder their layer updates around `super.setImage()`.
- Closing a view must route through `setImage(null)` (via `disposeView()` → `setSeries(null)`) so
  no pin is left dangling.
- Code that reads from the cache must tolerate a released buffer and reload.

## Global coordination between consumers

`MemoryManager` (`org.weasis.core.api.util`) is a process-wide **accountant**; it never allocates
or frees memory. Every native-memory source implements `NativeMemoryConsumer`
(`usedNativeMemory()`) and registers with it. The manager holds the global budget and exposes
`getUsedNativeMemory()`, `getAvailableNativeMemory()`, `isMemoryAvailable()` and `getPressure()`.

Consumers play one of two roles:

- **Elastic**: the `NativeCache` instances. They evict unpinned entries when either their own
  budget or the global budget is exceeded.
- **Rigid**: an in-progress 3D volume load. `VolumeBuilder` registers a consumer reporting the
  staging footprint for the duration of a build and unregisters it when the build ends.

While a volume loads, aggregate usage rises and the image cache yields room on its next `put`.
Eviction is `put`-driven, not immediate.

The MPR `Volume` stores its data in a `ChunkedArray` on the JVM heap; it is governed by the GC and
is deliberately **not** a `MemoryManager` consumer, which would double-count the heap.

## The 3D viewer

The 3D viewer (`weasis-dicom-3d`) does not use `java.nio` direct buffers for volumes:

- **GPU / VRAM**: the volume is uploaded as a 3D texture; `VolumeBuilder` checks `glGetError()`
  after each upload.
- **Off-heap staging**: slices are copied into FFM `Arena` segments (`TextureSliceDataBuffer`)
  before upload, in chunks bounded by `SystemMemory.getVolumeStagingMemory()`.

`-XX:MaxDirectMemorySize` does **not** apply to FFM `Arena` memory; the staging chunk size is the
only control. A smaller chunk lowers the transient spike; a larger one means fewer upload
round-trips.

## Tuning memory parameters

The budgets are JVM system properties (`-Dkey=value`), read once at startup and defined as
constants in `SystemMemory`:

| Property | Effect |
|---|---|
| `weasis.native.memory` | Absolute native-memory budget, in bytes; overrides the percentage |
| `weasis.native.memory.percent` | Native-memory budget as a percentage of physical RAM |
| `weasis.volume.staging.memory` | Bytes staged per 3D upload chunk |

Heap sizing is a JVM option (`-XX:MaxRAMPercentage`, `-Xmx`). Because the heap is subtracted from
the native budget, raising one shrinks the other.

## Packaging and distribution settings

The jlink module list (`JDK_MODULES`) and the jpackage `java-options` are defined once in
`weasis-distributions/script/launch-options.sh`, sourced by both `package-weasis.sh` and
`.github/workflows/build-installer.yml`. The Dicomizer launchers repeat the options in
`weasis-distributions/script/resources/<os>/dicomizer-launcher.properties`.

Invariants to keep:

- `jdk.management` must stay in the module list: `SystemMemory` needs
  `com.sun.management.OperatingSystemMXBean`.
- `com.sun.management` must stay exported to bundles in **both** `base.json` files
  (`weasis-launcher/conf/base.json` and `weasis-distributions/etc/config/base.json`).
- `--enable-native-access=ALL-UNNAMED` is required by the FFM `Arena` API.

## Reference: files and symbols

| File | Role |
|---|---|
| `weasis-core/.../api/util/SystemMemory.java` | RAM probe, native-budget and staging-size formulas |
| `weasis-core/.../api/util/MemoryManager.java` | Process-wide native-memory accountant |
| `weasis-core/.../api/util/NativeMemoryConsumer.java` | Interface a tracked native-memory source implements |
| `weasis-core/.../api/util/ResourceMonitor.java` | Session and cross-session resource metrics behind **Help → System resources** |
| `weasis-core/.../api/util/ResourceAdvisor.java` | Hardware verdict and upgrade recommendation |
| `weasis-core/.../api/util/GraphicsInfo.java` | OpenGL renderer detected by the 3D viewer |
| `weasis-core/.../api/media/data/NativeCache.java` | Bounded LRU cache, accounting, pinning |
| `weasis-core/.../api/media/data/ImageElement.java` | Owns the image `NativeCache`; `pinInCache()` / `unpinFromCache()` |
| `weasis-core/.../ui/editor/image/DefaultView2d.java` | `updatePinnedImage()`: one pin per viewport |
| `weasis-dicom-3d/.../viewer3d/vr/VolumeBuilder.java` | Chunked volume upload; registers the staging consumer |
| `weasis-dicom-3d/.../viewer3d/vr/TextureSliceDataBuffer.java` | Off-heap FFM `Arena` staging buffer |
| `weasis-distributions/script/launch-options.sh` | jlink modules and jpackage options |
