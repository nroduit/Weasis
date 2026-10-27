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

## Phases of a dynamic series

An operand is a phase, not only a series. `PhaseGrouper` finds the phases of a series stored as
one series: the images sharing one Temporal Position Identifier, else one Trigger Time, else the
k-th acquisition at every slice position when every position was acquired the same number of
times. Each phase is handed out as a transient series of the parent's images, so it pairs,
previews and builds like a series of its own, and the service lists the phases of a dynamic series
first among the masks of that series. The spec names the phases (`phase`, `maskPhase`), a build of
every phase minus the mask phase gives one derived series per phase, and the result can open as a
maximum intensity projection through all its slices.

## Digital subtraction angiography

An X-ray run (XA, RF) is subtracted from its own frames: the mask is one frame, the average of
several, or a time-interval offset (the mask of a frame is the frame some positions before or
after it), and it may be moved by a sub-pixel shift to follow the patient between the mask and
the contrast frames. `DsaMask` reads what the run itself says: the Mask Subtraction Sequence
(`AVG_SUB`, `TID`, `REV_TID`, with its mask frames, applicable frame range and sub-pixel shift)
gives the mask a reader starts from, else frame 1, and a Recommended Viewing Mode of `SUB` opens
the run as its subtraction preview. The spec carries this as `SubtractionSpec.Dsa`, the pairing
takes every frame of the range with its mask frames, the builder averages the mask frames once
and moves the mask with a bilinear translation (positive values down and right, edge pixels
repeated), and the derived run opens on the signed window, iodine dark on mid-gray as the
console shows it. The section of the image tool lists the frames of a run as masks, with a
*Current frame* button and the two shift sliders; `subtraction:mask` and `subtraction:shift`
drive the preview of a pane, `subtraction:create` takes `--mask-frames`, `--tid-offset` and
`--shift`. The derived run is single-frame instances of the run's SOP class with its cine rate;
no Mask Subtraction Sequence is written on them, since a reader applying it would subtract the
result again.

## Resampled path and motion correction

When the grids of the two series differ (matrix, spacing, orientation, or slice positions that
do not match) but both lie in one frame of reference, the pairing takes the **resampled path**:
every slice of the series keeps the nearest mask slice as its reference, and the mask values are
resliced from the volume of the mask series (`FusionVolumeBuilder`, `FusionVolumeResampler`, the
same reslicing the fusion uses) onto the grid of each slice, built once per mask by
`SubtractionBuilder.MaskVolume`. Outside the mask volume the difference is zero. The derived
series is labelled *(resampled)* and the command reports `path: resampled`. Without a frame of
reference the grids must match, as before.

**Motion correction** is an opt-in of the spec (`motionCorrection`): for every pair, phase
correlation under a Hann window measures the translation between the mask and the slice, and the
mask is moved by it before the subtraction, bilinear, edge pixels repeated. A weak correlation or
a shift beyond a tenth of the image is ignored. The result counts the corrected slices and the
largest shift, which the command reports under `motionCorrection`, and the Derivation Description
says the mask was moved. It is a rigid, per-slice, in-plane correction: the elastic registration
of a breast workstation is out of scope.

## Classes

| Class | Role |
|---|---|
| `SubtractionSpec` | What is computed: the two series (or phases, or a run and the frames of its mask), the mask weight `k`, an offset, the absolute-value and signed-window flags. The same spec always names the same derived Series and SOP Instance UIDs, so a subtraction created twice is one object |
| `SubtractionCompatibility` | Whether and how a mask can be subtracted: the *direct path* (same modality, same study, one matrix, one pixel spacing and orientation, and a mask slice within half a slice spacing of every slice of the series), the *resampled path* when the grids differ in one frame of reference, and the frame pairing of a run. Anything else is a `Refusal` with its reason |
| `SubtractionBuilder` | Computes each pair on modality-LUT values in 32-bit floats, the mask read from its slices or resliced from its volume, moved by the motion correction when asked, stores the difference as signed 16-bit with the rescale slope of the series (a difference of two stored values is then exact), counts the clipped pixels, writes the DICOM attributes of the derived instances and measures the default window on the middle slice |
| `SubtractionService` | Headless entry point: the compatible masks of a series and the one a reader would pick, `create`, `list`, `find`, `delete`, and `open` in the selected pane. Keeps the derived series of the session with their spec |
| `PhaseGrouper` | The phases of a dynamic series stored as one series, each as a transient series that remembers its parent and index |
| `DsaMask`, `FrameMask` | What the DICOM of an X-ray run says about subtracting it (mask sequence, recommended viewing mode, frame order), and the entries of the mask combo for the frames of a run |
| `SubtractionView` | The live preview of a pane: a `View2d` scrolling the source series and showing the difference of each slice, with its own cache directory |
| `SubtractionCommands` | The `subtraction:` namespace (`preview`, `mask`, `shift`, `phases`, `create`, `list`, `info`, `delete`) over the view and the service |
| `SubtractionAction`, `SubtractionController` | The preview toggle, mask combo, weight and mask-shift sliders of the *Subtraction* section of the image tool, and the glue that enters or updates the preview and runs the service off the EDT with the explorer's loading indicator |
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

## Lifecycle

A derived series belongs to the study of its source in the explorer, removable with
`subtraction:delete`, and the session of a display that shows it keeps its spec: on restore
`SubtractionDerivations`, the `DerivedSeriesProvider` of the plugin, builds it again under the same
identifiers before the panes are rebuilt.

## Hanging protocols

A display set whose Weasis extension says `derive: subtraction` with a mask image set hangs the
subtraction of the two matched series: the hanging-protocol applier asks `SubtractionDerivations`
to build it from the two Series Instance UIDs before the step is hung. The built-in *MR breast*
protocol shows the pre-contrast T1, the post-contrast T1 and their subtraction.

## Invariants

- A subtraction never changes its operands; the derived series is a new object of the study.
- The pairing never guesses an alignment: grids that differ pair only through their geometry, in
  one frame of reference; a different frame of reference, or no frame of reference with a
  different grid, is a refusal with its reason, which the command returns as `not-applicable`
  and the panel shows.
- A view of a hanging layout lives in a `ViewportPane`; replacing it (preview, MIP) goes through
  `ImageViewerPlugin.replaceView`, which swaps the stack canvas of the pane.
- The user interface calls the service the commands call, never the reverse.
