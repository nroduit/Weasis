# Weasis Memory Management

How Weasis bounds the memory a DICOM viewer needs beyond the JVM heap, and how the image caches
and the 3D viewer share one native-memory budget.

## Table of Contents

- [Why memory needs explicit management](#why-memory-needs-explicit-management)
- [The three memory arenas](#the-three-memory-arenas)
- [The native-memory budget](#the-native-memory-budget)
- [The image caches](#the-image-caches)
- [Pinning: protecting the visible images](#pinning-protecting-the-visible-images)
- [Images of the display chain](#images-of-the-display-chain)
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
| **JVM heap** | Swing UI, the pf4j plugin framework, DICOM metadata, Java pixel copies, the MPR `Volume` | Garbage collector | `-Xmx` / `-XX:MaxRAMPercentage` |
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

## Images of the display chain

The display op chain of a view keeps the input and output image of every operation between
passes, so that an operation with nothing new is skipped (see
[Display Op Chain](Display-Op-Chain.md)). When an operation runs again, the output it replaced is
released at once, instead of waiting for the `Cleaner`, when the operation owns its output and
nothing in the chain refers to it anymore; the source image, which belongs to the image cache,
and the last output, which the view displays, are never released by the chain. Other
intermediate images are freed by the `Cleaner` at GC time.

## Global coordination between consumers

`MemoryManager` (`org.weasis.core.api.util`) is a process-wide **accountant**; it never allocates
or frees memory. Every native-memory source implements `NativeMemoryConsumer`
(`usedNativeMemory()`) and registers with it. The manager holds the global budget and exposes
`getUsedNativeMemory()`, `getAvailableNativeMemory()`, `isMemoryAvailable()` and `getPressure()`.

Consumers play one of two roles:

- **Elastic**: the `NativeCache` instances. They evict unpinned entries when either their own
  budget or the global budget is exceeded.
- **Rigid**: an in-progress 3D volume load. `VolumeBuilder` registers a consumer reporting the
  memory of the slices it holds for the duration of a build and unregisters it when the build
  ends.

While a volume loads, aggregate usage rises and the image cache yields room on its next `put`.
Eviction is `put`-driven, not immediate.

The MPR `Volume` stores its data in a `ChunkedArray` on the JVM heap (in a temporary file when the
heap cannot hold it); it is governed by the GC and is deliberately **not** a `MemoryManager`
consumer, which would double-count the heap. Volumes are built and kept by one `VolumeService`
(`weasis-dicom-viewer2d`, `org.weasis.dicom.viewer2d.mpr`): one volume per series and
rectification, built once on a background worker and handed out under leases to the MPR, the
volume rendering, the fusion reslicing, a subtraction mask or a reformat. A volume stays while a
lease holds it; released, it waits for the next consumer until the unreferenced volumes exceed a
share of the heap, when the least recently used leave, and it goes with its series. Requests build
before prefetches, which a hanging protocol asks for the series its next steps reslice.

## The 3D viewer

The 3D viewer (`weasis-dicom-3d`) does not use `java.nio` direct buffers for volumes:

- **GPU / VRAM**: the volume is uploaded as a 3D texture; `VolumeBuilder` checks `glGetError()`
  after each upload.
- **No staging copy**: the slices are prepared as OpenCV images and sent to the texture straight
  from their native memory, one layer per slice (`TextureSliceDataBuffer` views each slice as an
  FFM segment). The loader holds at most `SystemMemory.getVolumeStagingMemory()` of slices before
  it sends them and waits for the transfer.

`-XX:MaxDirectMemorySize` does **not** apply to this memory; the staging bound is the only
control. A smaller bound lowers the transient spike; a larger one means fewer upload round-trips.

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
