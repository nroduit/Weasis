# Color Management

Color images declare the colors of their pixels with an ICC profile. Weasis converts the displayed
colors from that profile to the profile of the display, as the DICOM color pipeline expects
(PS3.4 Annex N: the pipeline ends in the profile connection space, the display converts to its own
profile). Grayscale is not concerned: its consistency comes from the calibration of the display to
the Grayscale Standard Display Function (PS3.14).

## Table of Contents

- [Concepts](#concepts)
- [Where the profiles come from](#where-the-profiles-come-from)
- [The conversion](#the-conversion)
- [Display operation](#display-operation)
- [Files written](#files-written)
- [Settings and commands](#settings-and-commands)

## Concepts

| Abstraction | Responsibility |
|---|---|
| `IccProfile` | The validated bytes of a profile, identified by their fingerprint and interned, so a series whose every instance carries the profile holds it once. |
| `ColorTransform` | A conversion between two profiles baked into tables, applied to 8-bit BGR pixels: identity, matrix or cube. |
| `ColorTransforms` | Builds the conversion of a pair with the platform color management module and keeps it per pair. |
| `ColorManagement` | The switch, the display profile, and the conversion of a source profile to the display. |
| `ColorProfileOp` | The last operation of the display chain: converts the displayed pixels to the display profile. |
| `IccImageFiles` | Writes image files that carry the profile of their colors. |

**No profile means sRGB**, on both sides: an untagged color image is sRGB, and so is a display
without a configured profile. Nothing is converted until an image declares other colors or a
display profile is set; then an untagged image is converted from sRGB to the display, and a tagged
image from its profile to the display (sRGB when no display profile is set).

## Where the profiles come from

The profile of a media is the `TagW.IccProfile` tag of its element.

| Source | Read by |
|---|---|
| DICOM image or presentation state | `DicomMediaIO`: ICC Profile (0028,2000) of the ICC Profile module (PS3.3 C.11.15), at the top level, or in the first item of the Optical Path Sequence for whole slide images |
| Other image file | `ImageCVIO`: the profile embedded in the file (PNG `iCCP`, JPEG `APP2`, WebP, AVIF), returned by OpenCV with the decoded pixels |

A blob that is not a profile (wrong size field, no `acsp` signature) is ignored with a warning, and
the image is then sRGB.

When a presentation state with the ICC Profile module is applied (Color, Pseudo-Color, Blending),
its profile describes the colors it produces and replaces the profile of the image; a presentation
state without the module keeps the profile of the image.

## The conversion

The platform module (LittleCMS behind `ColorConvertOp`) computes the conversion; calling it on every
frame would cost more than a frame period at 30 fps, so it is sampled once into tables:

- a pair that changes no color by more than half an 8-bit level is the **identity**, and the
  operation passes the image through;
- two matrix/TRC profiles (sRGB, Adobe RGB, Display P3, most monitor and camera profiles) give the
  **matrix** path: one decode table per channel to linear light, a 3×3 matrix, and one encode table
  per channel indexed over 16 bits of linear light. It is kept only when it matches the module
  within one level on a grid of colors, since a profile may also carry LUT tags the module prefers;
- any other pair gives the **cube** path: a 33³ grid interpolated trilinearly. When the display is a
  matrix/TRC profile, the grid holds the unclipped XYZ of the source and the display matrix and
  encode tables are applied per pixel, because interpolating colors already clipped to the display
  gamut is off by tens of levels next to the gamut boundary.

The tables are applied to the native buffer of the image through the FFM API, rows in parallel,
reading the input and writing a new image: the operation never modifies its input.

## Display operation

`ColorProfileOp` is the last operation of the 2D views and of the lens, after the zoom, so it runs
on the displayed pixels only and after fusion, overlays and redaction. It converts 8-bit
three-channel output only. Its source profile follows the image and the presentation state through
the image operation events. Its revision also moves when the switch or the display profile
changes, so the pass `DisplayColor` runs on every open view after such a change converts again
(see [Display Op Chain](Display-Op-Chain.md)).

## Files written

Each file states the colors of its pixels once, and the declaration matches the pixels:

| File | Pixels | Profile |
|---|---|---|
| Image rendered off-screen (the original image of the screenshot dialog, image export of DICOM) | converted to sRGB by a `ColorProfileOp` targeting sRGB at the end of `buildSimpleOpManager` | none: an untagged file is sRGB |
| Capture of what is displayed (view of the screenshot dialog, `view2d:capture`) | the values sent to the display | the profile of the monitor of the view (of the main window for a layout or a window), when one other than sRGB is set, in PNG, and in JPEG when it fits in one `APP2` segment |
| DICOM object made by the dicomizer (Acquire) | the values of the original file | ICC Profile (0028,2000) of the original file, with the Input Device class the module requires; the sRGB Input Device profile and Color Space `SRGB` when the file has none or is sRGB |
| Color Palette exported from the color map editor | — | the sRGB Input Device profile |

## Settings and commands

| Setting | Meaning |
|---|---|
| `weasis.color.management` | convert the displayed color images (default on) |
| `weasis.color.display.profile` | ICC profile file of the display of this workstation (machine scope; empty: sRGB): the default of every monitor |
| `weasis.screen.<id>.profile` | ICC profile file of one monitor, by device id; a local setting like the screen roles, never in the remote preferences |

All are in the Monitors page of the preferences. The `color:` namespace reads and changes them:
`color:status` gives the switch, the display profile, the monitors with a profile of their own
and, for a view, its source profile, its monitor and the kind of conversion applied; `color:set`
switches the conversion, sets the display profile, or with `--screen <id>` gives a monitor its own
profile (`--display-profile`) or takes it back (`--auto`).

## Whole slide images

The slide viewer paints tiles, not the image of the display chain, so it converts by itself
(`weasis-dicom-wsi`). `SlideBuilder` reads the ICC Profile (0028,2000) of each item of the Optical
Path Sequence, and `Slide.iccProfile(path)` answers the profile of an optical path. At each paint
`TiledImageLayer` asks the view for the conversion of the path shown to the monitor of the view
(`ColorManagement.toDisplay(profile, screenId)`) and applies it when it builds the paint image of
a tile, the thumbnail and the background color. The tiles of the cache keep their stored values;
when the conversion changes (switch, profile, monitor, optical path) the paint images are dropped
and built again. Gray tiles are not converted.

## A profile per monitor

A view converts to the profile of the monitor it is on. `DefaultView2d` listens to the
`graphicsConfiguration` property of its component and gives the device id to its `ColorProfileOp`
(`P_DISPLAY_ID`) when the view lands on another monitor, which renders the image again; the lens
copies the id of its view. `ColorManagement.displayProfile(id)` answers the file of that monitor,
or the display profile of the workstation when it has none. A change of any profile moves
`ColorManagement.generation()`, so every color operation is computed again.
A window that spans two monitors is converted for the one Java reports as its screen.
