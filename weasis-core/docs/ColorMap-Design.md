# Color map redesign: declarative JSON maps for 2D, fusion and 3D

Every color map in Weasis (2D pseudo-color, fusion overlay, 3D volume rendering preset) is
described by one declarative model, `ColorMap`, stored as JSON control points and compiled on
demand into the lookup table each renderer needs.

## Main concepts

A map is a list of **stops** on one position axis carrying two independent piecewise curves,
color and alpha. A stop may carry only a color or only an alpha; two stops at the same position
make a hard edge. The other properties say how positions are read and how the map is used.

| Property | Meaning |
|---|---|
| `id` | Stable identifier, independent of the display name; defaults, shadowing and presentation-state references key on it (`weasis.` bundled, `weasis.vr.` volume presets, `user.` user maps) |
| `type` | `sequential`, `diverging`, `cyclic`, `qualitative` or `transfer`. Drives the editor and menus; only `cyclic` changes sampling (values wrap instead of clamping). `transfer` declares that the alpha curve is part of the intent |
| `domain.kind` | `relative`: positions in `[0, 1]` over the current window (legacy behaviour). `absolute`: positions in a unit (HU, SUVbw, Gy…), window/level rescales the map. `fixed`: like absolute, but window/level never remaps the colors. `percent`: positions are a percentage of a reference supplied by the consumer |
| `bits` | Index resolution of the compiled table: 8, 12 or 16 |
| `space` / `interpolation` | Blending space (`rgb`, `hsl`, `lab`) and curve shape (`linear`, `step`, `sampled`) |
| `outside` | Colors below the first stop, above the last stop, and for NaN |
| `modality`, `default` | Menu filter and the map picked when a series of that modality opens |
| `category`, `tags`, `hidden` | Menu section, editor search keywords, kept out of menus but resolvable |
| `lighting`, per-stop materials | 3D only: shading, specular power, gradient opacity; ambient/diffuse/specular per stop |
| `metadata` | Free-form provenance, e.g. the DICOM palette UID and content label |

A legacy flat table (`.txt`, DICOM Palette Color LUT) is the same document with `sampled`
interpolation and one stop per entry.

## Key abstractions

| Class | Module / package | Responsibility |
|---|---|---|
| `ColorMap`, `ColorStop`, `ColorMapDomain`, `Lighting`, `GradientOpacity`, `MaterialPreset` | weasis-core-img, `org.weasis.opencv.op.lut.colormap` | Immutable model (records and enums), no JSON dependency |
| `ColorMapSampler` | same | Evaluates color, alpha and material at a domain value |
| `ColorMapCompiler` | same | Pure compilation: `toByteLut`, `toBgr`/`toAbgr`, `toRgba` (3D texture), `toLookupTable` (16-bit indexed `LookupTableCV`), `windowToDomain` |
| `ColorMapEdits` | same | Pure edit operations used by the editor |
| `ByteLut` | weasis-core-img, `org.weasis.opencv.op.lut` | The 256-entry table every consumer already uses; its nullable `source()` points back to the `ColorMap` so aware consumers take the wide path |
| `ColorMapJson` | weasis-core, `org.weasis.core.api.image.lut` | JSON codec |
| `ColorMapRegistry` | same | All known maps, their `Origin`, queries, change notification |
| `WindowOp`, `PseudoColorOp` | weasis-core, `org.weasis.core.api.image` | 2D window stage and color lookup |
| `ColorMapEditorDialog`, `ColorMapEditorHost`, `ColorMapFormat`, `ColorMapRadioMenu` | weasis-core, `org.weasis.core.ui.editor.image.lut` | Shared editor, its host contract, file formats, 2D LUT menus |
| `FusionOp`, `FusionWindow`, `FusionController` | weasis-dicom-viewer2d, `org.weasis.dicom.viewer2d.fusion` | Overlay alpha and window from the map |
| `Preset`, `VolumePresetHost`, `PresetCost`, `LegacyVolumePresets` | weasis-dicom-3d, `org.weasis.dicom.viewer3d.vr` | Volume rendering presets compiled from maps |
| `DicomColorPalette` | weasis-dicom-codec, `org.weasis.dicom.codec.utils` | Color Palette IOD read and write |

## JSON files

Documents use the envelope `{"schema": 1, "maps": [...]}`. A bare map object or bare array is
still read, unknown fields are ignored, and a newer major schema is refused.

| File | Content |
|---|---|
| `weasis-core/src/main/resources/colormaps.json` | Built-in 2D maps (clinical, the PS3.6 well-known DICOM palettes, classic, scientific) |
| `weasis-dicom/weasis-dicom-3d/viewer3d/src/main/resources/volumeColorMaps.json` | Built-in volume rendering presets, contributed to the registry by the 3D viewer |
| `customColorMaps.json` in the preference directory | User maps, 2D and 3D, pushed to the remote preference store |

The legacy `.txt` table is an import format only. A user's former `customVolumePresets.json` is
migrated once by `LegacyVolumePresets`.

## Registry

`ColorMapRegistry` holds maps from four origins: bundled, contributed (another bundle, via
`addBuiltIn`), imported during the session (`addImported`, e.g. a DICOM palette met while
loading) and user. A user map shadows any map with the same id. `query(Query)` is the single
filter (modality, volume, origins, category, text, hidden); `revision()` and listeners let menus
rebuild only when something changed. `findByDicomUid` resolves palettes referenced by
presentation states.

## Data flow

```
colormaps.json / volumeColorMaps.json / customColorMaps.json / DICOM palette
                         │
                         ▼
                 ColorMapRegistry ──► ColorMapRadioMenu / PresetRadioMenu / editor
                         │ selected ColorMap
                         ▼
                 ColorMapCompiler
      ┌──────────────────┼─────────────────────┐
      ▼                  ▼                     ▼
 2D: WindowOp ──►   Fusion: FusionOp       3D: Preset
     PseudoColorOp       (alpha curve,         (RGBA texture,
     (ByteLut or         FusionWindow)         lighting, gradient
     16-bit LookupTableCV)                     opacity)
```

- **2D.** Selecting a map propagates its bit depth (`DefaultView2d`): `WindowOp.P_OUTPUT_BITS`
  asks the window stage for a 12- or 16-bit index image, including the DICOM VOI stage, and
  `PseudoColorOp.P_INPUT_BITS` makes the lookup use a compiled `LookupTableCV` instead of the
  256-entry `ByteLut`. A `fixed` map sets `WindowOp.P_OUTPUT_RANGE` to its domain so the user's
  window/level does not remap its colors. Output stays 8-bit RGB, so later operations are
  unaffected.
- **Fusion.** A transfer map's alpha curve replaces the built-in opacity ramp (legacy tables keep
  it). A map anchored to physical values declares the overlay window through `FusionWindow`:
  SUVbw, the series pixel unit, or a percentage of `TagW.PercentReference` (set by the RT module
  from the prescription for dose grids).
- **3D.** `Preset` compiles colors and lighting from the map through the shared sampler; the
  texture width is capped (`Preset.MAX_TEXTURE_WIDTH`), alpha is corrected for the sampling
  rate, and `Preset.getVisibleRange()` lets rays skip samples outside the non-transparent range.
- **DICOM exchange.** `DicomColorPalette` turns a Color Palette object into a sampled map and
  writes one back; `DicomMediaIO` registers palettes met while loading; `PRManager` applies the
  palette of a Pseudo-Color Softcopy Presentation State and the superimposed series, palette and
  opacity of a Blending Softcopy Presentation State.

## Conventions and invariants

- The JSON is the preset; runtime parameters (overlay opacity, invert, current window, threshold
  sliders) are parameters of the compile call and live in the view state, not in the document.
- Compilation is per image, not per map: `absolute` and `percent` maps are indexed by stored value
  through the image's modality transform, so caches must key on those parameters too.
- The default path is 8 bits: grayscale and 8-bit maps keep the `Core.LUT` path and its cost.
- `ByteLut` keeps its two-argument constructor and `ActionW.LUT` stays a `ByteLut` action; code
  that only knows `ByteLut` keeps working, and code that knows the model reads `source()`.
- Color maps apply to single-channel images; compiled tables emit 8 bits per channel.
- On a plain 2D view a map's alpha is ignored; transparency matters only for overlays and 3D.
- Built-in maps are read-only; editing starts from a user copy.
- Model classes stay free of JSON and UI code; weasis-core stays free of dcm4che (DICOM formats
  are registered by the DICOM viewer).

## Extension points

- **Contribute maps**: a bundle calls `ColorMapRegistry.addBuiltIn(...)` with maps read by
  `ColorMapJson`.
- **Host the editor**: implement `ColorMapEditorHost` (`preview`, `currentLut`, `mapsChanged`,
  and optionally `valueRange`, `histogram`, `pickValue`, `formats`). Existing hosts:
  `ViewerColorMapHost` (2D), `DicomColorMapHost` (DICOM viewer), `VolumePresetHost` (3D).
- **Add a file format**: implement `ColorMapFormat` (`read`, optionally `write`) and return it from
  the host's `formats()`; built-in formats are in `ColorMapFormats`.
