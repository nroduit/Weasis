# Color map redesign: declarative JSON maps for 2D, fusion and 3D

## Status (14 September 2026, all uncommitted)

| Area | Implemented | Left |
|---|---|---|
| Model (weasis-core-img) | `ColorMap` with id, category, tags, hidden, domain kinds, independent color/alpha curves, materials, lighting, metadata; sampler; compiler to `ByteLut`, ABGR, RGBA texture, 16-bit `LookupTableCV`; `ColorMapEdits`; `MaterialPreset`; Lab/HSB blending shared with `CIELab` of dicom-tools | Two-dimensional transfer functions (out of scope) |
| JSON | Schema 1 envelope, bare forms still read, unknown fields ignored, newer schema refused | Preserving unknown fields on rewrite |
| Registry (weasis-core) | Origins bundled/contributed/imported/user, id shadowing, `Query` with cache, revision and listeners, user file with remote mirror, DICOM UID lookup | Removing an imported map from the session |
| Built-in maps | 25 maps in `colormaps.json`: clinical (PET SUV, CT Tissues, Isodose, Doppler), the eight well-known PS3.6 palettes parsed from Annex B with their UIDs and labels (exact to the entry, tested), classic re-authored (Rainbow, Spectrum, Fire, Ice, Cool, Bone, NIH), scientific (Viridis, Magma, Inferno, Plasma, Cividis, Turbo from Google's table within one level) | Nothing |
| 2D pipeline | `PseudoColorOp` 16-bit path, `WindowOp` output bits and fixed range, DICOM VOI at 12/16 bits (dicom-tools), fixed-domain maps anchored to HU; `percent` maps resolve through `TagW.PercentReference` in fusion | `percent` domain on the primary (non-fusion) view |
| 2D menus and legend | `ColorMapRadioMenu` sections in both viewers, revision-based refresh, modality filter, fixed-domain color bar with unit ticks | Unit ticks for absolute maps on the primary view |
| Fusion | Alpha from transfer maps, windows declared by SUVbw, pixel-unit and percent-of-max maps, PT default map, menu from the registry | 16-bit overlay index (resampler emits bytes) |
| Editor | Shared dialog: quick panel, palette, range, bands, threshold, undo/redo, stop table, curve with histogram, eyedropper, lighting row, materials, visible-voxel readout, filters by text/modality/origin/category, formats per host | Templates ("New from template"), CT histogram landmarks, cost badge |
| 3D | Presets are transfer maps (`volumeColorMaps.json`, schema 1), texture cap 4096, registry contribution, shared editor through `VolumePresetHost`, old custom file migrated once, composite alpha corrected for the sampling rate, gradient opacity (`lighting.gradientOpacity`, "Edge emphasis" in the editor), empty-space hint from the preset's visible range, cost badge in the preset menu | Nothing |
| DICOM exchange | Color Palette IOD import/export (`DicomColorPalette`), palettes met while loading registered for the session, Pseudo-Color PS applies its palette, Blending Softcopy PS drives the fusion overlay (superimposed series, its window, the palette, the relative opacity), RT dose colorwash through fusion with the Isodose map anchored to the prescription | Nothing |
| Legacy | `.txt` tables removed from resources, import only; `customVolumePresets.json` migrated | Nothing |

## Problem

| | 2D (`PseudoColorOp`) | Fusion (`FusionOp`) | 3D (`vr.Preset`) |
|---|---|---|---|
| Model | `ByteLut` = `byte[3][256]` BGR | `ByteLutAlpha` = `byte[4][256]` ABGR | RGBA table, width = intensity span |
| Index | windowed 8-bit value | windowed 8-bit value | real intensity (HU) via shader |
| Source | flat `.txt`, 256 lines `R G B` | derived from a `ByteLut` + hardcoded alpha ramp | `volumePresets.json` control points |
| Alpha | none | fixed ramp (1 % / 10 % of the range) | per control point |
| Modality | none | none | yes |
| Editor | none | none | `VolumeLutEditorDialog` |

Consequences: color maps cannot be anchored to physical units (SUV, HU, Gy), a wide
window through 256 colors bands on hue-sweeping maps, fusion thresholds are not editable,
and the 3D editor cannot be reused. The 8-bit clamp is a single line:
`DicomImageAdapter.getVOILookup` requests `bitsStored = 8` from `LookupTableUtils.createVoiLut`,
which itself supports 16-bit output.

## Goals

1. One declarative model (`ColorMap`) that *compiles* to every table the code needs today:
   `ByteLut`, `ByteLutAlpha`, a 16-bit-indexed `LookupTableCV`, and the 3D RGBA texture.
2. Stored as JSON control points ("stops"), never as flat value lists. Flat `.txt` files keep
   loading and are exposed as sampled maps.
3. Maps carry type (sequential, diverging, cyclic, qualitative, transfer), modality scope,
   domain (relative to the window or absolute in a unit), optional alpha, optional 3D lighting.
4. Nothing existing breaks: `ByteLut`, `ActionW.LUT`, `.txt` resources, `volumePresets.json`,
   `customVolumePresets.json`, plugin code using `PseudoColorOp.P_LUT` all keep working.
5. One editor, shared by 2D, fusion and 3D, living in `weasis-core`.

## JSON model

```json
{
  "name": "PET SUV",
  "type": "sequential",
  "modality": ["PT", "NM"],
  "default": true,
  "domain": { "kind": "absolute", "unit": "SUVbw", "min": 0, "max": 10 },
  "bits": 12,
  "space": "rgb",
  "interpolation": "linear",
  "stops": [
    { "pos": 0.0,  "color": "#000000", "alpha": 0.0 },
    { "pos": 2.5,  "alpha": 0.0 },
    { "pos": 2.5,  "alpha": 1.0 },
    { "pos": 2.5,  "color": "#0000ff", "group": "Uptake" },
    { "pos": 6.0,  "color": "#ffff00" },
    { "pos": 1.0,  "color": "#ff0000" }
  ],
  "outside": { "low": "transparent", "high": "#ffffff", "nan": "transparent" },
  "lighting": { "shade": true, "specularPower": 10 }
}
```

Field semantics:

- `id`: stable identifier, independent of the display name (`weasis.` prefix for bundled maps,
  `weasis.vr.` for volume presets, `user.` for user maps, `legacy.` for imported tables). Defaults,
  shadowing of a bundled map by a user copy and presentation-state references key on it.
- `category`: menu section when modality is not the natural split ("Clinical", "Classic",
  "Scientific", "Volume"); `tags`: keywords for the editor search; `hidden`: kept out of menus
  but resolvable, e.g. a palette a presentation state references.
- `type`: what kind of data the map is meant to show, the taxonomy of matplotlib and
  `net.mahdilamb.colormap`. It does not change how colors are computed, except for `cyclic`; it
  drives the editor, the menu grouping and the conventions a map follows.
  - `sequential`: an ordered quantity with a low and a high end (uptake, density, perfusion).
    The map runs in one direction, ideally with monotonically increasing brightness so "more"
    reads as "brighter". Hot Iron, Viridis and the PET ramps.
  - `diverging`: a quantity with a meaningful center (zero flow, a difference between two
    studies, a z-score). The center is neutral and the two sides take two distinct hues that
    intensify away from it, so sign and magnitude both show. Doppler flow. The editor offers a
    center stop for this type.
  - `cyclic`: a quantity that wraps around (a phase, an angle). The ends meet, and the sampler
    wraps a value beyond the domain instead of clamping it, so 370 degrees shows as 10. The one
    type with an effect on sampling.
  - `qualitative`: categories, not amounts (tissue classes, labels, dose bands). Colors are meant
    to be told apart, not ordered, so the map uses step interpolation and each stop holds until
    the next; a legend lists names rather than a gradient. CT Tissues, Isodose, PET 20 Step.
  - `transfer`: a map whose alpha curve matters as much as its colors (fusion overlays, volume
    rendering presets). It declares that transparency is part of the intent: thresholds are
    meaningful, fusion takes its alpha from the map, and the editor shows the threshold and
    opacity controls. PET SUV and every volume preset.

  Two consequences: reversing or discretizing a qualitative or cyclic map changes what its
  categories or wrap point mean, so the editor should not do it casually; and a transfer map can
  serve a plain 2D view, where its alpha is ignored, while a sequential map placed in fusion
  gets the default opacity ramp because it declares nothing about transparency.
- `modality`: list of DICOM modality codes, or absent for "ALL". Used to filter the menu.
  `default: true` marks the map picked when a series of that modality opens, like the
  `default` flag of `volumePresets.json`.
- `domain.kind`:
  - `relative`: `pos` is in `[0, 1]` over the current window. This is the legacy behaviour.
  - `absolute`: `pos` is in `unit` (HU, SUVbw, Gy, ms, mm2/s). `min`/`max` give the default
    window, exactly like the 3D "Auto level" derives it from the first and last point.
    Window/level then rescales the map linearly, which is what 3D and 3D Slicer do.
    - `fixed`: like `absolute`, but window/level never remaps the colors. For tissue
      classification (calcium above 130 HU) and thresholded overlays, where moving the window
      would silently change the meaning of a color.
      Implemented: selecting a fixed map sets `WindowOp.P_OUTPUT_RANGE` to the domain range, and
      both render paths then window over that range with a linear shape (values outside saturate
      to the end colors) while the user's window/level stays untouched for everything else.
  - `percent`: `pos` is a percentage of a `reference` the consumer supplies at compile time:
    `max` (image or series maximum, the NM convention), `prescription` (RT dose), `window`.
- Stops carry two independent piecewise-linear curves, color and alpha, on one position axis;
  a stop may omit `color` (opacity-only point, as `PresetPoint` allows today) or `alpha`.
  Two stops at the same position make a hard edge, the VTK convention. `group` is an optional
  editing label preserved from the 3D presets; it does not affect compilation.
- `bits`: index resolution of the compiled table, 8 (default for `relative`, keeps today's
  cost and look), 12 or 16 (default for `absolute`). The compiled table never exceeds the
  stored pixel range of the image, so 16 means "one entry per stored value".
- `space`: interpolation color space, `rgb` (default), `hsl` or `lab`. `lab` gives the
  perceptually uniform ramps clinicians are increasingly asked to use instead of rainbow.
- `interpolation`: `linear` (default), `step` (qualitative, isodose bands), `spline`.
- `stops[].color`: `#rrggbb`; `alpha` optional, defaults to 1. Per-stop `ambient`, `diffuse`,
  `specular` are accepted only when `lighting` is present (3D).
- `outside`: colors for values below the first stop, above the last stop and for NaN
  (float parametric maps). `transparent` is meaningful for overlays only; the primary image
  path ignores alpha and composites over black.
- `reversed` is not stored: it is the existing invert toggle applied at compile time.

A sampled map (imported `.txt`, matplotlib list, DICOM Palette Color LUT) is the same
document with `"interpolation": "sampled"` and one stop per entry; the editor shows it read-only
until "Convert to stops" is used.

### Files

- Built-in: `weasis-core/src/main/resources/colormaps.json`, one schema-1 document with the
  clinical maps (PET SUV, CT Tissues, Isodose, Doppler flow), the eight well-known DICOM
  palettes of PS3.6 Annex B (category "DICOM", ids `weasis.dicom.<label>`, well-known SOP
  Instance UIDs and content labels as metadata; the four table-defined palettes were parsed
  from the standard and reduced to the breakpoints of their linear segments, exact to the
  entry; the four segmented ones (Spring, Summer, Fall, Winter) are their two or three segment
  endpoints, within one level of the decoder's integer expansion since the standard leaves that
  rounding open; `DicomWellKnownPalettesTest` checks both against `dicomPalettesReference.json`), the classic
  palettes re-authored as stops (Rainbow, Spectrum, Fire, Ice, Cool, Bone, NIH) and the
  perceptually uniform scientific maps (Viridis, Magma, Inferno, Plasma, Cividis, 10 anchors
  blended in Lab) and Turbo, reduced from Google's 256-sample table (Apache 2.0) to 39 stops
  that stay within one 8-bit level of it (`TurboColorMapTest`). The legacy `.txt` tables are gone; the flat table survives only as an
  import format.
- User: `<pref dir>/customColorMaps.json`, one array, pushed to the remote preference store
  the way `customVolumePresets.json` is today (one file per user keeps the remote store simple).
- 3D: `volumeColorMaps.json` (schema 1, category "Volume", ids `weasis.vr.<modality>.<name>`)
  replaces the former group/point file; `Preset` is built only from maps. A user's old
  `customVolumePresets.json` is migrated once into the registry (`LegacyVolumePresets`) and
  renamed `.migrated`.

## Java model (weasis-core-img, package `org.weasis.opencv.op.lut.colormap`)

Implemented, Java 17, no JSON dependency (the codec lives in `weasis-core`, phase 2):

```
record ColorMap(String name, ColorMapType type, Set<String> modalities, boolean defaultForModality,
                ColorMapDomain domain, int bits, InterpolationSpace space, Interpolation interpolation,
                List<ColorStop> stops, OutsideColors outside, Lighting lighting)   // + Builder
record ColorStop(double position, Color color, Float alpha, Material material, String group)
record ColorMapDomain(DomainKind kind, String unit, double min, double max, String reference)
record Rgba(float red, float green, float blue, float alpha)
record Material(float ambient, float diffuse, float specular); record Lighting(boolean shade, float specularPower)
record OutsideColors(Rgba low, Rgba high, Rgba nan)
enum ColorMapType, DomainKind, Interpolation {LINEAR, STEP, SAMPLED}, InterpolationSpace {RGB, HSL, LAB}
```

- `ColorMap.builder(name)` defaults: sequential, all modalities, relative domain, 8 bits for a
  relative domain and 16 otherwise, RGB, linear, clamp outside, no lighting. Stops are sorted
  (stable, so duplicates keep authoring order and make a hard edge).
- `ColorMap.fromByteLut` / `fromBgrTable` wrap a legacy table as a `SAMPLED` map, one stop per
  entry; compiling it back to 256 entries is byte-identical (tested on every `ColorLut`).
- `ColorMap.reversed()` mirrors the color curve and keeps the alpha curve; reversing twice is
  an equal map. This is the 2D "invert" toggle; the 3D color negative stays in the 3D code.
- `ColorMapSampler` (from `map.sampler()`) evaluates color, alpha and material at a domain
  value: independent curves, step or linear, cyclic wrap, outside and NaN colors. A map with no
  color stops is white, with no alpha stops opaque.

`ColorMapCompiler` (static, pure; callers cache by map, window and range):

- `toByteLut(map)`: 256 BGR entries named after the map. Feeds every existing consumer.
- `toBgr(map, n)` / `toAbgr(map, n)`: BGR or ABGR planes, the `ByteLut` / `ByteLutAlpha`
  layouts, sampled over the default range.
- `toRgba(map, n)` and `toRgba(map, minInput, maxInput, inputToDomain)`: interleaved RGBA8, the
  3D texture layout, over the default range or one texel per input value.
- `toLookupTable(map, minInput, maxInput, inputToDomain, withAlpha)`: `LookupTableCV` with
  B, G, R (, A) byte bands and `offset = minInput`, so `lookup(Mat)` turns a 16-bit
  single-channel image into `CV_8UC3` / `CV_8UC4` in one pass with no new native code.
- `windowToDomain(map, wmin, wmax)`: the linear remap of a display window onto the default
  range, for `RELATIVE` and `ABSOLUTE` maps. `FIXED` and `PERCENT` callers pass their own
  modality or reference transform as `inputToDomain`.

`ByteLut` gains a third, nullable component `ColorMap source` with the existing two-argument
constructor kept as the canonical way to build it. Every current call site compiles unchanged,
`ActionW.LUT` stays `ComboItemListenerValue<ByteLut>`, and consumers that know about the new
model (`PseudoColorOp`, `FusionOp`, the color bar) read `source()` to take the wide path.

## weasis-core (implemented)

- `ColorMapJson`: `toJson` / `fromJson`, `readAll(InputStream|Path)` for one object or an array,
  `write(Path, Collection)`; colors as `#rgb`, `#rrggbb`, `#rrggbbaa` or `transparent`; enums in
  lower case; lenient numbers (strings accepted). Built on `JsonUtil`.
- `ColorMapRegistry`: built-in `colormaps.json` of the bundle (PET SUV, CT Tissues, Isodose as
  first examples of the absolute, fixed and percent domains), legacy `.txt` tables of the
  `luts` resource folder as sampled maps, user maps in `customColorMaps.json`; `find`,
  `mapsFor(modality)`, `defaultFor(modality)`, cached `byteLut(map)`, `saveUserMap`,
  `deleteUserMap`. User maps shadow built-ins of the same name.
- `PseudoColorOp`: an 8-bit source keeps the `Core.LUT` path; a 16-bit single-channel source
  is indexed over `[0, 2^P_INPUT_BITS - 1]` through the map behind the `ByteLut`
  (`ByteLut.source()`, or the table wrapped as a sampled map), one compiled table cached per
  op. The identity LUT rescales to 8-bit gray. `WindowOp.P_OUTPUT_BITS` and the matching
  `ImageElement.getDefaultRenderedImage(..., bits)` produce that 16-bit index image on the
  non-DICOM path. The DICOM path still emits 8 bits until `DicomImageAdapter.getVOILookup`
  (weasis-dicom-tools) takes the bit depth as a parameter.

## Pipeline changes (2D)

`WindowAndPresetsOp -> FilterOp -> PseudoColorOp -> ...` stays. Two switches:

1. `WindowOp` accepts `P_OUTPUT_BITS` (default 8). The event manager sets 12 or 16 when the
   selected `ByteLut.source()` asks for it. The DICOM path forwards the value instead of the
   hardcoded 8 in `DicomImageAdapter.getVOILookup`; the non-DICOM path rescales to `CV_16U`.
   `FilterOp` kernels run on 16-bit with OpenCV as they do on 8-bit.
2. `PseudoColorOp` keeps the `Core.LUT` path for an 8-bit source and a 256-entry map, and uses
   `ColorMap.toLookup(...)` when the source is 16-bit. Output is 8-bit RGB either way, so
   `ShutterOp`, `OverlayOp`, `FusionOp`, `AffineTransformOp` are untouched.

The color bar in `AbstractInfoLayer` reuses `FusionColorBar` so an absolute map shows ticks in
its unit (SUV, HU, Gy), and the fusion legend and the 2D legend become one component.

Grayscale and every existing map default to `bits = 8`, so the common path is byte-identical to
today and costs nothing more.

## Fusion

`FusionOp` drops `ALPHA_TRANSPARENT_BELOW` / `ALPHA_OPAQUE_FROM` in favour of the map's alpha
stops; the built-in fusion maps carry the same 1 % / 10 % ramp so the default look is unchanged.
An absolute `PT` map in `SUVbw` provides the default `FusionWindow` (the code already has
`TagW.SuvFactor` and `FusionWindow.displayUnit`), which is how thresholds such as "transparent
below SUV 2.5" become a preset instead of a manual window.

## 3D

`Preset` becomes a thin compiled view: `colors` from `ColorMap.toRgbaTexture(width)`,
`LightingMap` from the lighting stops. `VolumeLutEditorDialog` keeps only the lighting columns
and delegates the rest to the shared editor.

## Editor (weasis-core, `ColorMapEditorDialog`)

Goal: a radiologist or an integrator creates or adapts a map in under a minute without
understanding stops, and an advanced user still has full control. The current 3D dialog is
the starting point (it already has a draggable curve, a swatch table and a debounced live
preview); the generic parts move to `weasis-core` and get the additions below.

### Two levels, one document

**Quick panel** (default, always visible). Everything is a slider or a chooser and edits the
same stop list underneath:

- *Palette*: a gallery of gradient strips grouped by type (sequential, diverging, cyclic,
  qualitative, transfer), with "Reverse" and "Bands" (discretize into N steps) toggles.
- *Range*: two handles on a histogram of the current image or volume, in the image unit
  (HU, SUVbw, Gy). Dragging the handles moves the first and last stop; the histogram is
  the cue that tells users where the tissues are, and it is what the 3D editor lacks today.
- *Threshold and opacity* (transfer maps only): "transparent below" and "fully opaque
  from" handles, plus the global opacity. These are the two numbers every PET reader wants.
- *Domain*: relative / absolute / fixed / percent, unit, and the modality chips with the
  "default for this modality" check box.

**Advanced panel** (collapsible, `CollapsiblePanel` as in `ImageTool`): the stop table with
color swatches and the color/alpha curve for direct manipulation. Double-click adds a stop,
drag moves it, Delete removes it, arrow keys nudge, Shift-drag moves a group, positions snap
to integer units. Color and alpha are two curves on the same axis, so an alpha step does
not create a color stop.

### Starting points, not blank pages

- *New from template*: pick a type and a palette, and the domain is pre-filled from the
  modality's default window (`presets.xml`), so a "CT bone rainbow" is two clicks.
- *Duplicate built-in*: built-ins are read-only; "Duplicate" creates the user copy with the
  name suffixed, which is how most maps will be created.
- *Task templates*: "PET SUV threshold", "Isodose bands (N levels, % of prescription)",
  "CT tissue classes" (air, lung, fat, water, soft tissue, blood, bone), "Label map".
- *Pick from the image*: an eyedropper reads the value under the cursor on the active view
  and adds a stop there, with the pixel's current color pre-filled. Placing a stop at "this
  vessel" beats typing 180 HU.

### Feedback while editing

- Live preview on the active view through `ActionW.LUT` with the existing debounce, and
  the same color bar the view draws, with unit ticks.
- Undo/redo on the document (the current dialog has none), Escape reverts to the last save.
- A validity line: stops out of the image range, unit mismatch with the current modality,
  alpha everywhere zero, more than 256 stops on a `bits = 8` map.

### Import, export, sharing

- Import: legacy `.txt`, a pasted list of hex colors or floats (matplotlib `_cm_listed`
  style), the DICOM Palette Color LUT of the current image, a 3D volume preset.
- Formats come from the host: the 3D viewer offers JSON only; the 2D viewers import JSON, DICOM
  color palettes (DICOM viewer) and the legacy `.txt` table, and export JSON and DICOM. The flat
  table is import only: it cannot hold alpha, domain or metadata.
- "Save as user map" writes `<pref dir>/colormaps/<name>.json`, pushes it to the remote
  preference store and refreshes every LUT menu. A deployment can ship maps through the
  same folder listed in `weasis.config` as the `.txt` folder is today.

The `net.mahdilamb.colormap` library is not added as a dependency (it ships 150 maps as Java
classes and would need an OSGi wrapper). Its model is borrowed: types, positions in a
navigable map, low/high/NaN colors, reversed wrapper. A handful of its perceptually uniform
maps (viridis, magma, inferno, cividis, turbo; permissive licenses) are shipped as JSON stops.

## 3D: helping users get realistic and efficient renderings

The renderer already does per-light Blinn-Phong with a per-intensity lighting map
(ambient, diffuse, specular from the stops), on-the-fly central-difference gradients,
`opacityFactor`, `depthSampleNumber` and ray dithering. What users lack is guidance: the
lighting columns are three unlabeled floats per point, and nothing tells them why a preset
is slow or looks flat. The 3D editor adds, on top of the shared editor:

### Realism

- *Materials instead of numbers*: a per-group "Material" chooser (bone, soft tissue,
  vessel, skin, glass, metal, matte) that fills ambient, diffuse, specular and specular
  power with known-good values, with the raw columns still available under Advanced. The
  JSON keeps the numbers, so nothing changes in the format.
- *Gradient opacity*: an optional second 1D curve, `gradientOpacity` stops over the
  normalized gradient magnitude, multiplying alpha. This is the classic Levoy edge
  enhancement (VTK's gradient opacity function) and the single biggest step from
  "colored fog" to surfaces. It is one texture and one multiply in the shader, not a 2D
  transfer function, so it fits the schema and the `lighting` block.
- *Light preview*: the shading preferences (`ShadingPrefDialog`) are reachable from the
  editor, and the live preview reflects them, so material and light are tuned together.
- *Histogram with tissue landmarks*: for CT the histogram shows labeled HU landmarks (air,
  fat, water, soft tissue, bone); stops snap to them.

### Efficiency

- *Opacity correction for the sampling rate*: alpha is corrected as
  `1 - (1 - a)^(stepRef / step)`, as the segmentation path already does, so a preset looks
  the same at interactive and at full quality and users stop compensating by editing alpha.
- *Visible-voxel estimate*: the editor multiplies the volume histogram by the alpha curve
  and shows "x % of voxels visible". A preset that keeps air or noise slightly visible is
  the usual cause of slow, hazy renderings, and this number makes it obvious.
- *Empty-space hint from the map*: the compiled map exposes the lowest and highest
  intensity with non-zero alpha; the renderer uploads them so rays skip samples outside
  that range before the texture fetch.
- *Texture width cap*: `Preset` allocates one texel per intensity unit (a map from -1000 to
  3000 HU is 4000 texels). The compiled texture is capped at 4096 with linear filtering,
  so a map over the full 16-bit range no longer allocates 65536 texels.
- *Quality badge*: the preset list shows a small cost indicator derived from the visible
  fraction and the presence of gradient opacity and specular lighting.

## Runtime parameters stay out of the JSON

Global overlay opacity, a threshold slider, the invert toggle and the current window are
parameters of the compile call, not of the document. The JSON is the preset; the view state
holds what the user changes with sliders, exactly as `FusionOp` separates the LUT from
`P_OPACITY_OVERLAY` today.

## Compilation is per image, not per map

For an `absolute` or `percent` map the table is indexed by the *stored* value and the
position is computed through the image's modality transform (rescale slope and intercept,
`TagW.SuvFactor`, dose grid scaling). The cache key therefore includes the modality LUT
parameters and the reference value. Stored values are at most 16 bits in the current
pipeline (`getLutParameters` rejects more), so the table never exceeds 65536 entries.

Float pixel data (Parametric Map IOD, some dose grids) is not accepted by
`LookupTableCV.lookup`. The window stage quantizes it to `CV_16U` over the window first
(`convertTo` after `patchNaNs`), keeping a NaN mask so `outside.nan` can be applied after
the lookup. This is the one case that needs new code in the window stage rather than reuse.

## Cases covered

| Case | What makes it work |
|---|---|
| PET / SPECT / perfusion overlay with absolute thresholds | `absolute` domain in SUVbw, alpha stops, fusion default window |
| Parametric maps (ADC, CBF, T1, float pixel data) | `absolute` domain in unit, `outside.nan`, 16-bit index |
| CT tissue and calcium coloring, rainbow over 4000 HU | `absolute` in HU, `bits = 12`, no banding |
| RT dose colorwash | `absolute` in Gy, `step` interpolation, alpha |
| Pseudo-Color / Blending Presentation State | import Palette Color LUT as a sampled 16-bit map |
| Existing users and plugins | `.txt` still loaded, `ByteLut` API unchanged, 8-bit default path |

## Not covered, on purpose

- Two-dimensional transfer functions (opacity from gradient magnitude in volume rendering).
  The current 3D `Preset` does not have them either; they would be a separate texture.
- Per-channel curves for RGB sources. Color maps apply to single-channel images only; an RGB
  image keeps today's per-channel `Core.LUT` behaviour.
- 16-bit *output* precision (10-bit panels, GSDF). The compiled tables emit 8 bits per
  channel; `color` is `#rrggbb`. A sampled 16-bit Palette Color LUT loses its low bits in
  storage, which is invisible on an 8-bit display.
- Translated map names. Names are data, as the `.txt` names are today.

## Phases

1. `weasis-core-img`: `ColorMap` model, sampler, compiler, tests. **Done.**
2. `weasis-core`: JSON codec, registry, `WindowOp` output bits, `PseudoColorOp` 16-bit path.
   **Done**: both viewers' LUT menus are fed by the registry (the DICOM menu re-filters on the
   displayed image's modality); selecting a map propagates its bit depth to `WindowOp` and
   `PseudoColorOp` (`DefaultView2d`); the DICOM VOI stage honours it
   (`DicomImageReadParam.setOutputBits`, `WindLevelParameters`, `DicomImageAdapter`,
   `ImageRendering` float path in weasis-dicom-tools 5.35.1-SNAPSHOT, forwarded by
   `DicomImageElement`), falling back to 8 bits when a presentation LUT follows; the editor
   (`org.weasis.core.ui.editor.image.lut`: `ColorMapEditorDialog`, `ColorMapCurvePanel`,
   `ViewerColorMapHost`) with quick settings, palette, range, bands, threshold, undo/redo,
   stop table, import of `.txt`/JSON and export, opened from the LUT menus of both viewers;
   `ColorMapEdits` (weasis-core-img) holds the pure edit operations.
   Deferred to phase 3, with the fusion work that provides the unit and window: the shared
   color bar with unit ticks; the eyedropper needs a view mouse mode and is deferred with it;
   the perceptually uniform palettes still need their reference data.
3. `weasis-dicom-viewer2d` fusion. **Done**: a transfer map's alpha curve replaces the built-in
   ramp (`FusionOp.buildAlphaLut`, legacy tables keep the 1 % / 10 % ramp); a map anchored to
   physical values declares the fusion window (`FusionWindow.fromMap`: SUVbw through the SUV
   factor, the series' own pixel unit directly, percent of the data maximum) and it is applied
   when the map is selected (`FusionController.applyLut`), on the provisional slice window and on
   the refined volume window; the fusion menu lists every map for all, PT or NM and defaults to the
   map flagged default for PT (the built-in PET SUV), else the hot-metal PET table. The 2D color
   bar draws a fixed-domain map over its own range with unit ticks (`AbstractInfoLayer`). The
   editor's eyedropper (`ColorMapEditorHost.pickValue`, `ViewerColorMapHost`) adds a stop at the
   real value clicked on the view, for maps anchored to physical values.
   Still 8-bit inside the fusion overlay (the normalized PET image is a byte image); a 16-bit
   overlay index would need the volume resampler to emit 16 bits.
4. `weasis-dicom-3d`. **Done**: `VolumePresets` converts `volumePresets.json` presets to
   transfer maps (absolute domain in HU for CT, groups as stop labels, lighting as materials) and
   back; `Preset` compiles its colors and lighting from the map through the shared sampler, with
   the color texture capped at 4096 texels (the shader normalizes by the output range, so the
   texel count is free); the built-in presets are contributed to the registry at bundle start
   and user volume maps of the registry appear in the preset menu; the old
   `VolumeLutEditorDialog` is removed and the menu opens the shared editor through
   `VolumePresetHost` (live preview as a preset, histogram of the volume, modality); the editor
   gained the "3D" row (volume preset, shade, specular power), material columns and a
   `MaterialPreset` chooser, the histogram backdrop and the visible-voxel readout; the
   composite ray path corrects alpha for the sampling rate against a 1024 reference so quality
   changes keep the look. The legacy `customVolumePresets.json` is still read; new presets are
   saved by the registry in `customColorMaps.json`.
   Gradient opacity: `Lighting.gradientOpacity` is a curve of opacity factor by normalized
   gradient magnitude (`GradientOpacity`, JSON `lighting.gradientOpacity: [{magnitude, factor}]`);
   the editor's "Edge emphasis" spinner writes the two-point form (homogeneous regions keep
   `1 - emphasis`, edges above magnitude 0.3 keep everything). `Preset` samples it into 32 values
   uploaded as a uniform array; the composite path computes one raw gradient per visible sample,
   multiplies alpha by the interpolated factor, and reuses the same gradient for shading.
   Empty-space hint: `Preset.getVisibleRange()` gives the lowest and highest normalized LUT
   coordinate with non-zero alpha (open-ended when a texture end is opaque, since clamped
   coordinates read that texel); the composite loop skips the color and lighting fetches outside
   it. Cost badge: `PresetCost.estimate` turns the share of voxels a preset leaves visible (its
   alpha curve integrated over a full-range volume histogram cached per volume,
   `VolumeHistogram`) into a level of 1 to 3, plus one level when every visible sample needs a
   gradient (shading or edge emphasis); `PresetRadioMenu` appends one to three dots to each
   preset with a tooltip giving the visible share, refreshed each time the menu is built.
5. DICOM exchange and presentation states. **Done**: `ColorMap.metadata` (JSON `metadata`
   block, keys `dicom.sopInstanceUID`, `dicom.contentLabel`, `dicom.contentDescription`,
   `dicom.contentCreator`); `ColorMapFormat` extension point on the editor host with the built-in
   JSON and `.txt` formats (`ColorMapFormats`) and the DICOM palette format registered by
   `DicomColorMapHost`; `DicomColorPalette` (weasis-dicom-codec) reads a Color Palette object or
   any dataset with palette tags into a sampled map and writes a 256-entry (4096 for a wide map)
   16-bit palette reusing the map's UID; `DicomMediaIO` registers a Color Palette object met while
   loading as a session map (`ColorMapRegistry.addImported`, found back by
   `findByDicomUid`); a Pseudo-Color Softcopy Presentation State applies its palette
   (`PrDicomObject.getPaletteColorLut` in weasis-dicom-tools, `PRManager`, `PseudoColorOp` on
   `APPLY_PR`). Blending Softcopy PS (done): `PrDicomObject` parses the Blending Sequence into two
   `BlendingLayer`s (position, study, referenced series, VOI), exposes the relative opacity and
   the palette UID, takes the palette from the top level or the superimposed item, uses the
   underlying layer's VOI as the state's VOI, and reports both layers' series as references so
   the explorer attaches the state to the images of both sets. `PRManager.applyBlending`, when
   the view shows the underlying set, finds the superimposed series in the model, windows it by
   its layer (else the series default), colors it with the palette as a uniformly opaque map
   (DICOM blends with one relative opacity, so no per-value alpha; a UID-only reference resolves
   through the registry, e.g. a well-known palette) and applies a `FusionState` with the
   relative opacity as overlay opacity. Applied from the superimposed set, or with the other
   series not loaded, the state is left as a plain grayscale one and logged.
   RT dose colorwash (done): the fusion pipeline carries the dose grid. The codec derives each
   dose frame's position from the Grid Frame Offset Vector (relative offsets along the plane
   normal, or absolute z), so the grid is a proper volume; `HiddenSpecialElement.getImageSeries`
   lets a hidden element expose its frames and `Dose` does, so `FusionController.compatibleSeries`
   also offers the dose grids referencing the displayed CT; `RTDOSE` is an accepted overlay
   modality. `RtSet` writes `TagW.PercentReference` (prescription in raw pixel units, i.e.
   rxDose / (DoseGridScaling * 100)) on the dose frames, and `FusionWindow` resolves a
   `percent` map with reference `prescription` through it, so the Isodose map, now the RTDOSE
   default, gives a window of 0 to 120 % of the prescription with the fusion color bar in
   percent. The RT tool's dose panel has a Colorwash check box that applies that fusion state
   and mirrors the view's state; the fusion tool itself lists the dose grid and switches to the
   modality's default map when an overlay series is chosen.
   Original plan:
   - **Color Palette IOD import/export** (PS3.3 A.58, SOP class 1.2.840.10008.5.1.4.39.1).
     Import yields a sampled map over a relative domain (segmented data expanded with the
     existing weasis-dicom-tools code, 16-bit entries reduced to 8-bit colors); Content Label
     becomes the name, Content Description, creator and SOP Instance UID are kept as metadata,
     the ICC profile is ignored. Export samples the map (256 entries, 4096 for a 12-bit map)
     as non-segmented 8-bit data with a fresh UID; alpha, physical domain, modality scope and
     blend space cannot be represented and the user is told the file is "colors only".
   - Model additions: an optional metadata map on `ColorMap`, serialized as a `dicom` block
     (`sopInstanceUID`, `contentLabel`, `description`, `creator`), and an importer/exporter
     extension point on `ColorMapEditorHost` so the DICOM viewer registers a
     `DicomColorPalette` reader/writer living in weasis-dicom-codec while weasis-core stays free
     of dcm4che.
   - The DICOM explorer recognizes the Color Palette SOP class on load and offers to add the
     palette as a user map. The eight well-known palettes of PS3.6 (HOT_IRON, PET,
     HOT_METAL_BLUE, PET_20_STEP, SPRING, SUMMER, FALL, WINTER) are bundled as built-in JSON
     with their UIDs, replacing the three overlapping `.txt` tables.
   - **Pseudo-Color and Blending Softcopy Presentation States**: the palette is resolved by
     UID from the registry (built-in, user or imported) and applied after the VOI stage; the
     Blending PS supplies the relative opacity of the overlay.
   - RT dose colorwash on the `percent` domain.

## Registry, grouping and menus (September 2026)

- Every entry has an `Origin`: bundled, contributed (another bundle), imported (this session,
  e.g. a DICOM palette) or user. A user map shadows any map with the same id.
- `ColorMapRegistry.Query(modality, volume, origins, category, text, includeHidden)` is the one
  filter; results are cached until the registry changes. `revision()` and listeners let the
  menus rebuild only when something changed, and pick up palettes imported while loading.
- `ColorMapRadioMenu` (weasis-core) gives every 2D LUT menu the same sections: identity and
  gray plus the maps of the displayed modality at the root, general maps, one submenu per
  category, "Other modalities", "Imported", "User". The 3D preset menu keeps its modality
  grouping over `Preset.getAllPresets()` (built-in plus user and imported volume maps).
- The editor filters by text, modality, origin and category, and shows the origin in its title.
