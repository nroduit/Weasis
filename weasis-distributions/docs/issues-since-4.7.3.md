# GitHub issues to create for changes since v4.7.3 (branch 4.8)

Baseline: [v4.7.3](https://github.com/nroduit/Weasis/tree/v4.7.3) (2026-08-25). Target milestone: **4.8.0**.
Changes already tracked by an issue (#894–#898) are not repeated. #909 exists but has no description yet; the proposed one is first.

---

## [#909](https://github.com/nroduit/Weasis/issues/909) Add support for better caching DICOM files from network shares and optical media

**Labels:** Type: Feature — **Milestone:** 4.8.0 — *existing issue, description below*

### Description

Importing or exporting DICOM files on a network share (SMB/NFS mount, mapped drive, UNC path) or from a CD/DVD is very slow, because every file operation is a network round trip or a slow seek. The importer did several `stat` calls and two opens per file, the exporter created directories per file and wrote the DICOMDIR with tiny seeks, and DICOMDIR references were resolved with a real-path lookup and a directory listing per record.

Changes:

- **Detection:** new `NetworkFileSystems` in `weasis-core` tells whether a path is on a network share or optical media, using the OSHI mount table with a NIO `FileStore` fallback. Paths from the file cache and drag and drop are resolved UNC-aware.
- **Import:** `LoadLocalDicom` walks the tree once, parses headers on a small thread pool, and copies files from slow media into the local cache so that images are read from the share only once. Controlled by the new preference `weasis.import.dicom.network.cache` (default `true`). The import dialog lists optical and removable media through OSHI without probing network drives.
- **DICOMDIR:** new `DicomDirFileResolver` resolves referenced files from a single listing and rejects references escaping the DICOMDIR root; the DICOMDIR file is staged locally on slow storage.
- **Export:** new `ExportTarget` creates directories once and, on a network share, writes files and the DICOMDIR locally before moving them in bulk. Local file ingestion uses `Files.copy` instead of a 4 KiB loop.
- **Dependencies:** `weasis-core-img` 5.0.0.1 and `weasis-dicom-tools` 5.35.1 use 64 KiB buffers and drop redundant file probes.

---

## Hanging-protocol style viewport layouts (stack / tiled) with synchronization across tiled views

**Labels:** Type: Enhancement — **Milestone:** 4.8.0

### Description

The viewer grid is currently made of independent `ViewCanvas` cells: one series per cell, and the layout menu only chooses how many cells are shown. This does not map to the DICOM Hanging Protocol notion of a *viewport*, where one region of the screen can either show a single stack to scroll through, or a tiled grid of consecutive images of the same series.

This change introduces a `ViewportPane` in `weasis-core` that acts as one slot of the viewer's outer grid and operates in two modes:

- **Stack** (default): exactly one canvas, the user scrolls through the series. This is the behaviour of previous versions.
- **Tiled n×m**: the pane is split into a grid of canvases that display consecutive images of the same series; scrolling advances the whole tile set.

User-visible changes:

- New **Viewport Layout** menu in the viewer toolbar with *Stack* and *Tiled n×m* entries.
- New **Apply to all tiled views** option in the synchronization menu, so that window/level, zoom, pan and other synchronized actions propagate to every tile of a viewport.
- `ImageViewerPlugin`, `ViewerToolBar`, `ImageViewerEventManager` and `DicomSynchManager` are refactored to address viewports rather than raw canvases. All containers (2D, MPR, 3D, ECG, SR, audio) are updated accordingly.
- The `dcmview2d:synch` console command is removed.

Backport of `4016e0031` from branch 5.0.

---

## Secure and robust DICOMDIR file resolution

**Labels:** Type: Enhancement — **Milestone:** 4.8.0

### Description

`DicomDirLoader` resolved the files referenced by a DICOMDIR through `java.io.File` string concatenation. A crafted DICOMDIR could reference a path outside its own directory (`..` segments or absolute paths), and unresolved references were silently skipped, which made import problems from CD/DVD or network shares hard to diagnose.

Changes:

- Referenced files are resolved with `java.nio.file.Path`, normalized and checked to stay inside the DICOMDIR root; any reference escaping that root is rejected and logged.
- Unresolvable references are logged with the offending record so that users can report why a series is missing.
- Thumbnail loading from DICOMDIR icons follows the same resolution rules.
- New `DicomDirTraversalTest` and `ThumbnailManagerTest` cover the traversal cases.

This is a prerequisite of #909 (caching from network shares and optical media).

---

## Replace usbdrivedetector with OSHI for removable-drive detection

**Labels:** Type: Enhancement — **Milestone:** 4.8.0

### Description

Removable-drive detection, used by the DICOM import *Browse* panel to list CD/DVD and USB media, relied on `net.samuelcampos:usbdrivedetector`, which is unmaintained and, on Windows, spawns a PowerShell subprocess through `powershell-lib-java`.

It is replaced by [OSHI](https://github.com/oshi/oshi) (`oshi-core-ffm`, which uses `java.lang.foreign` so it needs neither JNA nor a per-platform native library). The jars are embedded in the `weasis-core` bundle.

- `HardwareInfo` lists mounted volumes and classifies removable ones per OS: Windows reports removability directly; Linux and macOS are matched on the media mount points (`/media`, `/run/media`, `/mnt`, `/Volumes`). Network and pseudo file systems are excluded.
- `RemovableDriveWatcher` polls on a daemon thread and reports connect/disconnect events on the EDT, so the Browse panel is not blocked by the first scan.
- Fixes two long-standing bugs in `BrowsePanel`: the detector was created in a try-with-resources and closed before any event could fire, and drive removal mutated the backing list directly, bypassing the combo-box model events.

---

## Improve system resources information with OSHI

**Labels:** Type: Enhancement — **Milestone:** 4.8.0

### Description

The resource monitor had no reliable source for hardware information: the CPU was judged on logical processors, and the graphics adapters were only known once a 3D view had been opened.

The hardware probe introduced for removable-drive detection (OSHI) is reused to describe the machine:

- The resource snapshot carries the processor name and the physical core count, so `ResourceAdvisor` reasons on real cores instead of hyper-threads.
- The dashboard shows the machine model, a real OS string, the processor and the graphics adapters, available even when no 3D view was opened, plus available memory including reclaimable page cache.
- Total memory still comes from `com.sun.management` so that the cgroup limit is honoured inside containers.

---

## Replace Jackson with Jakarta JSON-P for JSON processing

**Labels:** Type: Enhancement — **Milestone:** 4.8.0

### Description

JSON handling (DICOMweb responses, manifests, configuration and preferences) depended on `jackson-databind` and `jackson-annotations`, which are large, evolve quickly and require reflective data binding.

All JSON processing is migrated to the Jakarta JSON Processing API (`jakarta.json-api` 2.1.3) with Eclipse Parsson (`parsson` 1.1.7) as implementation, both embedded in the `weasis-core` bundle. Jackson is removed from `weasis-parent`, `weasis-launcher` and `weasis-core`, which reduces the distribution size and the attack surface, and removes the corresponding `Import-Package` requirements for plugins.

Plugin authors using Jackson through the Weasis class path must now ship their own copy or move to `jakarta.json`.

---

## Replace `eu.essilab.lablib.checkboxtree` with an internal implementation

**Labels:** Type: Enhancement — **Milestone:** 4.8.0

### Description

The check-box trees used by the segmentation tool, the DICOM explorer filters and the export/import dialogs were provided by the third-party `eu.essilab.lablib.checkboxtree` library, which is no longer maintained and does not follow FlatLaf theming.

A lightweight check-box tree is now implemented in `weasis-core` (`org.weasis.core.ui.util` tree components) with the same tri-state semantics (checked, unchecked, partially checked with propagation to parents and children). All callers, including `SegmentationTool`, are migrated and the external dependency is removed.

---

## Replace the Scribejava OAuth2 service with a custom implementation

**Labels:** Type: Enhancement — **Milestone:** 4.8.0

### Description

OAuth2 / OpenID Connect authentication for DICOMweb and Viewer Hub used the Scribejava library. It brought its own HTTP client and thread pool, duplicated what the built-in `JavaNetHttpClient` already provides, and made it difficult to apply the request stall guard introduced for #899.

`OAuth2ServiceFactory` and the authorization-code / client-credentials flows are reimplemented on top of `JavaNetHttpClient`. Consequences:

- The `scribejava` 8.3.3 dependency and its IDEA external annotations are removed.
- The request stall guard is ported onto the new client: the request half of an exchange is bounded by *time without progress* (`inactivityTimeout`) instead of a whole-exchange deadline, so a large STOW-RS send on a slow link is only aborted when bytes stop flowing.
- The `getConnectTimeout` / `getReadTimeout` aliases deprecated in 4.7.3 are removed in favour of `getConnectTimeoutMillis` / `getInactivityTimeoutMillis`.
- Service invalidation on authentication changes (#896) is preserved.

---

## Raise minimal base version to 4.8.0 and update weasis-dicom-tools and weasis-core-img

**Labels:** Type: Enhancement — **Milestone:** 4.8.0

### Description

Bundles built for 4.8 rely on the new `ViewportPane`, JSON-P and OSHI APIs and must not be loaded by an older launcher. `base.minimal.version` is raised from 4.7.0 to 4.8.0.

Dependencies are updated to `weasis-dicom-tools` 5.35.1 and `weasis-core-img` 5.0.0.1, which carry the I/O fixes needed for reading from network shares (#909).

---

## `LauncherTest` fails when weasis-core is built outside the reactor

**Labels:** Type: Bug — **Milestone:** 4.8.0

### Description

Running `mvn -pl weasis-core test` (without `-am`) fails with:

```
Cannot load weasis configuration file!
```

`ConfigData` looks for `conf/base.json` next to the `weasis-launcher` code source. That resolves to `weasis-launcher/target` when the module comes from the reactor, but to the jar in the local Maven repository when `weasis-core` is built alone, where no `conf/` directory exists.

The test now sets `felix.config.properties`, which `ConfigData` honours ahead of the code-source lookup, to a `base.json` owned by the test. As a side effect the Felix bundle cache is created under `target/` instead of `~/.weasis`.
