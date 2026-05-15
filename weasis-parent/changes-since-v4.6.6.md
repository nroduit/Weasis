# Changes since v4.6.6 → next release v4.7.0

## 🆕 New Features

### OpenGL 3.3 Volume Rendering Backend (macOS support) — [#784](https://github.com/nroduit/Weasis/issues/784)
- Added a new FBO (Framebuffer Object) + Fragment Shader rendering pipeline using OpenGL 3.3 core profile.
- This enables **Volume Rendering on macOS** (previously blocked by deprecated OpenGL APIs).
- Introduced weighted-splatting accumulators and improved FBO texture sizing.
- The `Volume` class is now used as the primary input for all 3D rendering (MPR/MIP and VR).

### Hanging Protocols — Part I
- Changed the **view container layout** to lay the groundwork for Hanging Protocol support.
- This is the first step toward configurable automated layout of series on screen.

### Unified DICOM Import UX — [#793](https://github.com/nroduit/Weasis/issues/793)
- **DICOM-ZIP files can now be drag-and-dropped** directly into Weasis.
- All local DICOM import methods (folder, file, ZIP) have been consolidated into a single **"Local Device" dialog** for a more consistent user experience. So DICOM-ZIP can be imported via the same dialog as folders/files.

### Export Annotation Feature (finalized)
- The export of graphical annotations to external formats has been fully implemented and finalized.

### 4D Series Sub-Series Splitting Dialog — [#763](https://github.com/nroduit/Weasis/issues/763)
- A dialog to automatically divide a multi-phase 4D series into individual phase sub-series (usable in MPR, MIP, and VR).

### Integrated MIP Projection Mode in Standard 2D Viewer — [#795](https://github.com/nroduit/Weasis/issues/795)
- MIP projection mode is now available directly in the standard 2D viewer with full synchronization and slab geometry overlay.

### DICOM Export for MPR Views — [#796](https://github.com/nroduit/Weasis/issues/796)
- Added the ability to export MPR views as DICOM files with the current orientation.

### MPR Crosshair Cut Mode for 3D Volume Rendering — [#799](https://github.com/nroduit/Weasis/issues/799)
- Introduced a crosshair cut mode that allows interactive slicing/clipping of 3D volume rendering from MPR crosshair positions.

### Expanded DICOM Segmentation Support (BINARY, FRACTIONAL, LABELMAP) with MPR & 3D overlay
- DICOM SEG reader now supports the three `(0062,0001) SegmentationType` kinds defined by the standard:
  - **BINARY** (already supported, refactored),
  - **FRACTIONAL** (`PROBABILITY` and `OCCUPANCY`) — rendered through a new `FractionalOverlay` with per-segment alpha LUT (`ByteLutAlpha`),
  - **LABELMAP** — single frame per slice where the pixel value is the `SegmentNumber`, decoded via the new `LabelMapContourLoader` / `LabelMapScanner`.
- Segmentation overlays are now displayed not only in the 2D viewer but also in **MPR** (axial/coronal/sagittal/oblique) and **3D Volume Rendering** views. A new `SegmentationVolume` / `SegmentationVolumeBuilder` pipeline reslices SEG masks into the active reference volume so the overlay follows the MPR planes and the VR camera.
- New `SegVolumeTexture` exposes the segmentation volume as an OpenGL texture consumed by the volume shader (`vrFunctions.glsl`, `volumeFbo.frag`, `voxelUniforms*.glsl`).
- The RT module (`RTDose`) now reuses the same fractional overlay infrastructure for isodose rendering — see commit `426477213` ("Refactoring RT module and use fractional overlay for RTDose").
- Reorganized DICOM codec: SEG-related classes moved into a dedicated `org.weasis.dicom.codec.seg` package, with `HiddenSeriesManager` to track non-displayable series referenced by SEG/RT objects.

### System File Chooser — [#761](https://github.com/nroduit/Weasis/issues/761)
- Weasis now uses the **system native file chooser** instead of the Java common one, providing a more familiar file browsing experience on each platform.

### Customizable Keyboard Shortcuts — [#804](https://github.com/nroduit/Weasis/issues/804) *(uncommitted)*
- Added a centralized `ShortcutManager` to manage all keyboard shortcuts in the application.
- New **Shortcuts preferences panel** (`ShortcutPrefView`) allowing users to view, search, customize, and reset keyboard shortcuts.
- Supports conflict detection, per-user persistence, and export/import of shortcut configurations.

### Tab Opening Preferences for Studies — [#806](https://github.com/nroduit/Weasis/issues/806)
- Redesigned the tab opening preferences for studies to improve usability and handle different import contexts (e.g., new study vs. same study, local vs. network).
- Introduced TabFocusPolicy for configurable focus behavior when opening new viewer tabs.

### View Synchronization Overhaul — auto-sync, manual-sync, per-view configuration
A complete redesign of how synchronization is controlled, both globally and per view. Documented in the [View Synchronization tutorial](https://nroduit.github.io/en/tutorials/synch-view/).

- **Per-view auto-sync overlay button** {{< icon synch >}} on every eligible 2D view (bottom-right corner). Red = OFF, green = ON. Clicking it opens a per-view popup with:
  - a master *Synchronize this view* toggle,
  - independent per-action checkboxes for *Scroll*, *Pan*, *Zoom*, *Rotation*, *Flip*, *Window/Level* and *Spatial unit* (stay-open while you flip several),
  - *Apply to all synchronized views* — propagates this view's settings to every other view in the same FoR group,
  - an explicit *Close* item.
- **FoR color chip** — a small colored square painted at the center of the auto-sync button when the container holds two or more views with different *Frame of Reference UIDs*. Same UID ⇒ same color (deterministic palette of Kelly's 22-color contrast set, red/white removed), so spatial groupings are visible at a glance. The chip is reused as the icon on every *Apply to all synchronized views* menu entry to identify the source group.
- **Per-view manual-sync overlay button** (hand icon) for views that have no — or a different — FoR than their peers. Eligibility requires the same orientation. A click auto-joins an existing manual group, auto-links when there is a single candidate, or shows a multi-select picker when there are several. Once active, *Scroll* propagation is forced on and locked; other per-action toggles remain freely configurable.
- **MPR "Synchronize" submenu** added to the per-view configuration popup (settings icon, top-right of each MPR view). Same content as the per-view 2D popup *minus* the master toggle (synchronization between MPR planes is structural).
- **Toolbar drop-down popup overhaul** — the synchronization {{< icon synch >}} drop-down now also exposes the per-action checkboxes (mirroring the selected view) and an *Apply to all views* entry decorated with the selected view's FoR chip.
- **`None` synchronization mode removed** — the toolbar drop-down now lists only `Default Stack` and `Default Tile`. Use the master *Synchronize* checkbox in the drop-down popup to disable synchronization globally for the container.
- **Revised `Default Stack` defaults** — only *Scroll* is propagated by default; every other per-action toggle (Pan, Zoom, W/L, Rotation, Flip, Spatial unit) starts disabled and the user opts in explicitly. Previously W/L and Zoom were also on by default. `Default Tile` is unchanged: every per-action setting still propagates so a tile group stays in lock-step.

---

## 🔧 Improvements

### 3D / Volume Rendering
- `Volume` class overhauled: better min/max handling in subclasses, validation of parallel and regular slices, support for color images (byte & short), ability to **read and save 3D raw data**.
- Improved volume array structure and integration into the rendering pipeline.
- Improved FBO texture size management.

### MPR (Multi-Planar Reconstruction)
- Fixed **horizontal banding/striations** in coronal/sagittal MPR views — [#771](https://github.com/nroduit/Weasis/issues/771)
- Fixed **deformed axial images** in non-axially scanned volumes — [#752](https://github.com/nroduit/Weasis/issues/752)
- Fixed MPR geometry computation.
- Fixed move-center logic for rotated axes.
- Fixed volume intersection logic.
- Optimized metadata reading during MPR construction.
- The current view will also move when MPR is orthogonal — [#769](https://github.com/nroduit/Weasis/issues/769)
- **Segmentation overlay in MPR** — DICOM SEG masks (BINARY / FRACTIONAL / LABELMAP) are now resliced to follow the active MPR planes via `SegVolumeBuilder` and a dedicated `MprController`, so the same SEG shown on the source slice appears correctly on coronal, sagittal and oblique reconstructions.

### UI / UX
- **Toolbar layout** and **DICOM Explorer layout** improved (better spacing, alignment).
- Fixed split series selection behavior in multi-view.
- Improved the **info layer** display (corner annotations).
- Fixed **print layout** rendering according to the layout change. Still has some issues to be resolved [#509](https://github.com/nroduit/Weasis/issues/509).
- Refactored viewer initialization to use `ViewerOpenOptions` and enhanced split layout handling.

### Networking
- HTTP downloads now exclusively use `HttpClient` (removed legacy `HttpURLConnection` paths). Improves how images/series are downloaded.
- Implemented `JavaNetHttpClient` for **OAuth2** authentication flows.
- Refactored `org.weasis.core.api.net` package; added `URIUtils`.
- Fixed local file URI exception with `HttpClient`.

### Code & Build Quality
- Updated to **Java 25** (both compilation and runtime target).
- Refactored `org.weasis.core.api.image` and `org.weasis.core.api.net` packages.
- Replaced `File` with `Path` throughout for modern I/O usage.
- Updated to latest **weasis-dicom-tools** API (5.34.2).
- Fixed `bnd-maven-plugin` (OSGi bundle generation) compatibility issue.
- Fixed large properties-file loading issue.
- Updated **FlatLaf** to 3.7.1.

### Date/Time Picker
- Replaced the legacy date picker with a new custom `DateTimePicker` component featuring improved selection UX

### SpinnerProgress
- Added a new `SpinnerProgress` component for displaying indeterminate progress with a spinner animation, used in various loading contexts.

---

## 🐛 Bug Fixes

| Commit | Description |
|--------|-------------|
| [`f867c06`](https://github.com/nroduit/Weasis/commit/f867c06) | Redesign tab opening preferences for studies — [#806](https://github.com/nroduit/Weasis/issues/806) |
| [`ee8c27b`](https://github.com/nroduit/Weasis/commit/ee8c27b) | Fix very slow initialization of language settings |
| [`52618d2`](https://github.com/nroduit/Weasis/commit/52618d2) | Fix minor display in MPR/MIP/VR |
| [`86bf1d3`](https://github.com/nroduit/Weasis/commit/86bf1d3) | Fix local file URI exception with HttpClient |
| [`bda67cc`](https://github.com/nroduit/Weasis/commit/bda67cc) | Fix preferences initialization in certain launch contexts |
| [`77e7dbb`](https://github.com/nroduit/Weasis/commit/77e7dbb) | Fix store channel value in volume data |
| [`95190bf`](https://github.com/nroduit/Weasis/commit/95190bf) | Fix MPR geometry |
| [`0cc0790`](https://github.com/nroduit/Weasis/commit/0cc0790) | Fix print layout |
| [`a6522d4`](https://github.com/nroduit/Weasis/commit/a6522d4) | Fix horizontal banding/striations in MPR COR/SAG views — [#771](https://github.com/nroduit/Weasis/issues/771) |
| [`d1f99e0`](https://github.com/nroduit/Weasis/commit/d1f99e0) | Fix deformed axial image in non-axially scanned volumes — [#752](https://github.com/nroduit/Weasis/issues/752) |
| [`6e74788`](https://github.com/nroduit/Weasis/commit/6e74788) | Fix Optional type handling |
| [`2f7e43a`](https://github.com/nroduit/Weasis/commit/2f7e43a) | Fix params copy |
| [`0952bad`](https://github.com/nroduit/Weasis/commit/0952bad) | Fix large properties files |
| [`2cf44a1`](https://github.com/nroduit/Weasis/commit/2cf44a1) | Fix message issue |
| [`d522508`](https://github.com/nroduit/Weasis/commit/d52250866) | Fix wrong class call |
| [`dad5ed0`](https://github.com/nroduit/Weasis/commit/dad5ed0) | Fix orientation issue |
| [`6510528`](https://github.com/nroduit/Weasis/commit/651052814) | Fix DICOM presets not loaded for Breast Tomosynthesis — [#709](https://github.com/nroduit/Weasis/issues/709) |
| [`48b54a8`](https://github.com/nroduit/Weasis/commit/48b54a8) | Fix icon transparency |

---

## 🧪 Areas to Test for v4.7.0

1. **Volume Rendering on macOS (OpenGL 3.3 FBO pipeline)** — [#784](https://github.com/nroduit/Weasis/issues/784)
    - On macOS (Apple Silicon **and** Intel) verify the VR view opens without `GLException` and that the volume renders identically to Linux/Windows compute-shader output (same preset, same camera).
    - Force the FBO path on Linux/Windows with `-Dweasis.3d.force.fbo=true` and compare the result to the default compute-shader output — diffs should be limited to floating-point rounding, not visible artifacts.
    - Resize the VR window and the application window: the FBO texture must be reallocated correctly (no black borders, no stretched output, no GL error in the log).
    - Switch presets (MIP, MinIP, average, composite) and toggle the segmentation overlay to confirm the weighted-splatting accumulators behave correctly on both pipelines.
2. **MPR quality** — Load several CT/MR volumes including:
    - a regular axial CT,
    - a CT with **gantry tilt**,
    - an **oblique** acquisition,
    - a non-axially scanned MR (sagittal-acquired) — [#752](https://github.com/nroduit/Weasis/issues/752),
    - a thick-slab CT prone to **horizontal banding/striations** in the COR/SAG views — [#771](https://github.com/nroduit/Weasis/issues/771).

      For each volume, scroll all three planes, take a measurement (length & angle) on axial then on coronal/sagittal, and verify the values are consistent. No banding/striations, no deformation, no geometry mismatch with the source slice.
3. **Hanging Protocols groundwork (container layout)** — Open studies in the existing layouts (1×1, 2×1, 1×2, 2×2, 3×3, plus the explorer-driven layouts) and confirm:
    - no regression in the way series are placed in the views,
    - no regression in the **split series** behavior when several series are dropped onto the container,
    - the new `ViewerOpenOptions` path is used for both local and remote opens.
4. **DICOM import — Unified Local Device dialog** — [#793](https://github.com/nroduit/Weasis/issues/793)
    - Drag-and-drop a single DICOM file, a folder, and a `.zip` containing DICOMs onto the main window — all three should land in the same Local Device dialog.
    - Same three sources via the dialog itself (folder picker / file picker / typing the path).
    - DICOM-ZIP **with a password** — verify the password prompt appears and the series loads.
    - A nested ZIP (ZIP-of-ZIPs) and a malformed/empty ZIP — verify graceful error handling, no orphan loading panels left behind.
5. **Export annotations** — On a CT slice with several measurements (length, ellipse, polygon, angle, text annotation):
    - export to each supported format and reopen the result in an external viewer; confirm the geometry, units (px / mm) and labels are preserved,
    - re-import the exported file into Weasis and check round-trip fidelity.
6. **4D sub-series splitting** — [#763](https://github.com/nroduit/Weasis/issues/763), see also [#739](https://github.com/nroduit/Weasis/issues/739)
    - Open a multi-phase 4D series (cardiac CT, perfusion MR, dynamic contrast); open the split dialog and verify the suggested phase count matches the source.
    - After splitting, each phase sub-series must be openable in MPR, MIP and VR independently (no cross-phase blending).
    - Confirm the original series remains untouched and that the sub-series carry stable UIDs across application restarts.
7. **OAuth2 networking** — Configure a WADO-RS/DICOMweb endpoint backed by an OAuth2 IdP (Keycloak or equivalent):
    - first connection triggers the OAuth login; token is cached,
    - token refresh works after expiry without user intervention,
    - revoking the token server-side surfaces a clear error in the UI.

      Verify both the new `JavaNetHttpClient` path and confirm the legacy code path is no longer reachable (search for `HttpURLConnection` usage in DICOMweb queries).
8. **HTTP(S) downloads on the new `HttpClient`-only path** — Test:
    - HTTP and HTTPS WADO-URI downloads,
    - large series (>500 MB) — verify no memory bloat, progress bar advances,
    - server returning a redirect (302) — followed correctly,
    - **local `file://` URIs** (regression covered by commit `86bf1d3`) — must not throw,
    - cancellation mid-download — partial files cleaned up.
9. **Print layout** — [#509](https://github.com/nroduit/Weasis/issues/509)
    - Print a 2×2 layout and a 3×3 layout to PDF and to a real printer; verify scaling, aspect ratio, info-layer rendering and that no view is clipped.
    - Print with the new `DateTimePicker` open in the print options dialog — verify no exception.
    - Known remaining issues are tracked in #509 — record any **new** regressions separately.
10. **Preferences initialization** — Cover the cold-start and re-launch scenarios that previously threw NPE during `UICore` initialization (commit `bda67cc`):
    - first launch on a clean profile (no `~/.weasis/preferences/`),
    - launch with a partially written preferences file (simulate a previous crash),
    - launch with read-only preferences directory,
    - language change followed by restart (regression for commit `ee8c27b` — "very slow init of language settings").
11. **DICOM presets — Breast Tomosynthesis (DBT)** — [#709](https://github.com/nroduit/Weasis/issues/709)
    - Open a DBT series; verify the modality-specific W/L presets are listed in the W/L menu and apply correctly,
    - confirm the active preset is persisted across slice navigation and across series switches within the same study.
12. **Java 25 compatibility** — Run the full matrix:
    - build with JDK 25 (Temurin) and JDK 26 (CI target) — both must pass `mvn -B -ntp verify -Pcoverage`,
    - run `mvn spotless:check` — no formatter mismatch,
    - launch the packaged application on Windows / macOS (Intel & Apple Silicon) / Linux,
    - run the installer pipeline (`build-installer.yml` via `workflow_dispatch`) — MSI / PKG (notarized) / DEB / RPM all produced and installable,
    - verify all GitHub Actions jobs use JDK 25/26 (no leftover JDK 21 references).
13. **weasis-dicom-tools 5.34.2 upgrade** — Major refactor of image conversion and DICOM tooling. Test:
    - DICOM **import** of CT, MR, US, XA, MG, NM, PT, RT (plan/struct/dose), SR, KO,
    - DICOM **export** of single image, full series, and a multi-frame object,
    - **transcoding** between encapsulated transfer syntaxes (JPEG-Lossless, JPEG2000, RLE, HTJ2K when supported),
    - DICOMweb: **WADO-RS** retrieve, **STOW-RS** send,
    - DIMSE: **C-STORE**, **C-FIND**, **C-MOVE**, **C-GET** against a known SCP (dcm4chee or equivalent),
    - image processing pipeline: windowing, VOI/Modality LUT, palette color, MONOCHROME1 inversion, PR application, KO filter — verify pixel-perfect equivalence with v4.6.6 on a reference dataset.
14. **MIP projection in the 2D viewer** — [#795](https://github.com/nroduit/Weasis/issues/795)
    - Activate MIP on a CTA volume; verify the slab geometry overlay (thickness handles, orientation indicator) is drawn,
    - change slab thickness with the mouse and with the keyboard; the projection updates in real time,
    - synchronize the MIP view with a sibling 2D view (same FoR) — scroll & W/L propagate as expected,
    - switch between MIP / MinIP / Average and verify the projection mode label updates in the info layer.
15. **DICOM export from MPR** — [#796](https://github.com/nroduit/Weasis/issues/796)
    - Export an axial, coronal, sagittal and oblique MPR; reopen each exported series in Weasis and in an external viewer,
    - verify `ImageOrientationPatient` / `ImagePositionPatient` reflect the chosen plane,
    - export with and without burned-in annotations and confirm both behave correctly,
    - check the exported series appears under the correct study in the DICOM Explorer.
16. **MPR crosshair cut mode in VR** — [#799](https://github.com/nroduit/Weasis/issues/799)
    - With an MPR + VR layout, enable the crosshair cut mode on the VR view,
    - drag the MPR crosshairs and verify the clipping planes update in real time on the volume,
    - toggle individual axis cuts on/off — the volume must reveal/hide the corresponding half-space,
    - reset the crosshair to volume center — clipping planes must reset accordingly.
17. **System native file chooser** — [#761](https://github.com/nroduit/Weasis/issues/761)
    - On Windows, macOS and Linux (GTK), open every "Open"/"Save" dialog reachable from the UI (DICOM import, export annotations, screenshot, preferences import/export…) and verify the **OS-native** picker is shown (not the Java Swing one),
    - verify common shortcuts (recent files, drives/volumes, hidden files toggle on macOS) work,
    - verify the dialog respects the file-type filters declared by the caller.
18. **Customizable keyboard shortcuts** — [#804](https://github.com/nroduit/Weasis/issues/804)
    - Open the new **Shortcuts preferences panel**; verify all actions are listed and searchable,
    - rebind a shortcut to a new key, restart the application, confirm the new binding is persisted,
    - try to bind two actions to the same chord — conflict detection must warn,
    - export the shortcut configuration, reset to defaults, then re-import — bindings must be restored,
    - verify the rebound shortcuts actually trigger their actions in the viewer.
19. **Tab opening preferences for studies** — [#806](https://github.com/nroduit/Weasis/issues/806)
    - Test the four import contexts: **local + new study**, **local + same study**, **network + new study**, **network + same study**,
    - verify `TabFocusPolicy.autoByDuration` behaves consistently across these contexts (focus follows the most recent open within the configured time window),
    - exercise the redesigned preferences dialog — every option must be persisted across a restart and reflected in the runtime behavior.
20. **DICOM Segmentation (BINARY / FRACTIONAL / LABELMAP) in 2D, MPR and 3D**
    - Open a series with a **BINARY** SEG attached; verify each segment is selectable in the Segmentation tool, the contour overlay matches the source slice, and turning a segment off hides it in 2D, MPR and VR simultaneously.
    - Open a series with a **FRACTIONAL** SEG (PROBABILITY *and* OCCUPANCY when available); verify the alpha LUT renders intermediate values (not a hard 0/1 mask) and that the per-segment opacity slider in the Segmentation tool affects the blend.
    - Open a series with a **LABELMAP** SEG; verify all segment labels are picked up from the single per-slice frame and that segment numbering matches the source `SegmentSequence`.
    - Build an MPR from a series carrying a SEG; scroll/rotate the planes and verify the overlay tracks the reformatted geometry on axial, coronal, sagittal and oblique cuts (no offset, no aliasing on borders).
    - Open the same SEG in the 3D Volume Rendering view; verify the overlay is visible inside the volume, that the per-segment color/visibility toggles in the Segmentation tool propagate to VR, and that the `SegVolumeTexture` upload does not regress 3D performance.
    - Load an **RT Dose** with isodose levels; verify it now renders through the fractional-overlay path and looks visually equivalent to v4.6.6.
21. **View synchronization overhaul** — exercise every entry point and verify the new defaults:
    - Open a CT/PET pair sharing a FoR; confirm only *Scroll* propagates by default; toggle *Window/Level* and *Zoom* in the per-view popup and verify propagation; use *Apply to all synchronized views* and check that the **per-view** version stays inside the FoR group while the **toolbar drop-down** version applies to every sync-active view in the container.
    - Open three or more series with different FoRs in the same container; verify each auto-sync button shows a *distinct* color chip in its center, and that swapping the layout keeps each FoR's color stable. Containers with a single FoR must show **no** chip.
    - Manual sync: open two same-orientation series with no shared FoR; click the hand button and verify auto-link with a single candidate, the multi-select picker with multiple candidates, and the auto-join behaviour when joining an existing manual group. Confirm *Scroll* is forced on and locked.
    - MPR: open a volume in the MPR viewer and verify the {{< icon viewSettings >}} settings popup contains a **Synchronize** submenu *without* a master toggle, with the same per-action toggles + *Apply to all synchronized views* + *Close*.
    - Toolbar drop-down: confirm the `None` mode is gone and the master *Synchronize* checkbox correctly enables/disables synchronization globally without changing the active mode.
