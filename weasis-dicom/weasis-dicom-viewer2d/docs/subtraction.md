# Subtraction

How Weasis subtracts one series of a study from another and reads the difference as a series of
its own: a contrast-enhanced MR acquisition minus its pre-contrast one, where enhancing tissue is
what remains on a black background.

All classes live in `org.weasis.dicom.viewer2d.subtraction`.

## Concept

A subtraction is a **derived series**, not a display mode of one view. The result must be
scrolled, windowed, measured, projected (MIP), reformatted (MPR), captured and sent to an archive
like any acquired series, and it must be computed on real values before the display chain
windows them. The derived series therefore takes the path the full-series MIP takes
(`mip.SeriesBuilder`): its slices are written as raw images to a cache directory, each one wrapped
in a `RawImageIO` reader carrying its own DICOM attributes, and the resulting `DicomSeries` is
added to the study of its source in the `DicomModel`, where the explorer lists it with a `SUB`
badge (`TagW.Derivation`).

```
  series A (post-contrast) ─┐
                            ├─► SubtractionCompatibility.pair ─► SubtractionBuilder.build ─► DicomSeries
  mask B (pre-contrast)   ─┘        slice pairs                   A − k·B + offset         study of A
```

## Live preview

As the MIP view does for a slab, `SubtractionView` shows the difference before any series exists:
it replaces the plain view of a pane, keeps scrolling the source series and, for every slice it is
asked to show, displays the difference computed off the EDT and cached in a directory of its own.
Changing the mask or its weight recomputes; the first slice of a parameter set opens on the
*Subtraction* preset and the next ones share its window. *Build series* materializes what the pane
shows through the service and hands the pane to the derived series; *Exit preview* puts the plain
view back. The preview is reached from the image tool section, the view button of the pane and
`subtraction:preview`; a `subtraction:create` without arguments builds what the selected pane
previews.

## Classes

| Class | Role |
|---|---|
| `SubtractionSpec` | What is computed: the two series, the mask weight `k`, an offset, the absolute-value and signed-window flags. The same spec always names the same derived Series and SOP Instance UIDs, so a subtraction created twice is one object |
| `SubtractionCompatibility` | Whether a mask can be subtracted without resampling (the *direct path*) and the pairing of the slices: same modality, same study, one frame of reference, one matrix, one pixel spacing and orientation, and a mask slice within half a slice spacing of every slice of the series. Anything else is a `Refusal` with its reason |
| `SubtractionBuilder` | Computes each pair on modality-LUT values in 32-bit floats, stores the difference as signed 16-bit with the rescale slope of the series (a difference of two stored values is then exact), counts the clipped pixels, writes the DICOM attributes of the derived instances and measures the default window on the middle slice |
| `SubtractionService` | Headless entry point: the compatible masks of a series and the one a reader would pick, `create`, `list`, `find`, `delete`, and `open` in the selected pane. Keeps the derived series of the session with their spec |
| `SubtractionView` | The live preview of a pane: a `View2d` scrolling the source series and showing the difference of each slice, with its own cache directory |
| `SubtractionCommands` | The `subtraction:` namespace (`preview`, `create`, `list`, `info`, `delete`) over the view and the service |
| `SubtractionAction`, `SubtractionController` | The preview toggle, mask combo and weight slider of the *Subtraction* section of the image tool, and the glue that enters or updates the preview and runs the service off the EDT with the explorer's loading indicator |
| `SubtractionMenu` | The *Subtract…* entry of the series context menu of the explorer, a `SeriesMenuContributor` extension |

## Value conventions

- Operands are read through `getUncachedModalityLutImage`: rescale slope and intercept already
  applied, so the difference is in the value domain of the modality.
- `result = A − k·B + offset`; `k = 1` and `offset = 0` subtract the mask fully. `k < 1` keeps part
  of the anatomy, as the *anatomical background* of angiography consoles does.
- Stored value = real difference ÷ rescale slope of `A`, rounded and saturated to the signed 16-bit
  range; the derived instances carry that slope and a zero intercept. The clipped pixel count is
  part of the result.
- Default window presets, in real values: *Subtraction* from black (zero when the 99th percentile
  is positive) to the 99th percentile of the middle slice, and *Signed* from the 1st to the 99th
  percentile; `--signed` puts the latter first. Both use `LINEAR_EXACT`.

## DICOM encoding

The derived instances copy the patient, study and series attributes of `A`, then set: the SOP
Class of `A` (the pixels stay in the modality's value domain), deterministic Series and SOP
Instance UIDs, Series Number of `A` plus 1000, Series Description `SUB <A> - <B>`, Image Type
`DERIVED\SECONDARY\<value 3 of A>\SUBTRACTION`, Derivation Description, Derivation Code Sequence
`(113062, DCM, "Pixel by pixel subtraction")`, a Source Image Sequence naming the two source
instances with purpose `(121322, DCM, "Source image for image processing operation")`, the
geometry of `A` (frame of reference, position, orientation, spacing, thickness), Photometric
Interpretation `MONOCHROME2`, Pixel Representation 1, and the window presets above. Export and
DICOM send treat the series as any other.

## Invariants

- A subtraction never changes its operands; the derived series is a new object of the study.
- The direct path refuses rather than guesses: a slice without a mask slice at its position, a
  different grid or a different frame of reference is a refusal with its reason, which the command
  returns as `not-applicable` and the panel shows.
- A view of a hanging layout lives in a `ViewportPane`; replacing it (preview, MIP) goes through
  `ImageViewerPlugin.replaceView`, which swaps the stack canvas of the pane.
- The user interface calls the service the commands call, never the reverse.
