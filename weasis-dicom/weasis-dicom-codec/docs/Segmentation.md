# DICOM Segmentation

How DICOM SEG objects (BINARY, FRACTIONAL and LABELMAP) are modelled in the codec and
displayed by the 2D viewer, the MPR views and the 3D volume renderer. User documentation:
https://nroduit.github.io/en/tutorials/dicom-segmentation/

## Model

A SEG file is loaded as a `SegSpecialElement` (`org.weasis.dicom.codec.seg`), a hidden special
element attached to the patient. It owns the list of segments and their presentation state
(visibility, colour, opacity) and lazily builds a `SegmentationVolume`.

`SegmentationVolume` is a multi-label 3D raster on the segmentation's own grid. Each voxel
holds a small storage id that maps to one segment, or to a combination of segments where
they overlap, so the number of segments a SEG may contain is not capped and overlaps are
represented without per-voxel bit masks. The volume carries its spatial metadata (origin,
axes, spacing) and can be resliced on any plane. `SegmentationVolumeBuilder` derives the grid
from the frames' orientation, position and spacing.

## Display paths

- **2D**: contours are extracted per slice and drawn as `SegContour` / `SegGraphic` graphics
  (`org.weasis.core.ui.model.graphic.imp.seg`); `SegMeasurableLayer` provides the pixel
  statistics of a segment. When the image and the SEG do not share a plane, the contours come
  from reslicing the volume.
- **MPR**: the volume is resliced on the view plane and drawn as an overlay.
- **3D**: the storage ids are uploaded as an integer texture together with a colour lookup
  table (`SegVolumeTexture` in `weasis-dicom-3d`), so the shader resolves a voxel colour with
  a single fetch.

## Visibility

`SegVisibilityPolicy` is the single place that decides whether a segmentation starts hidden:
a global preference, a keyword list matching descriptions that are not meant to be shown over
the anatomy, and a count threshold for patients or transfers carrying many segmentations. The
result is only a default; the segmentation tool lists hidden segmentations unchecked. The
rules are edited in the *Segmentation* preference page, shared by 2D, MPR and 3D.

## Resources

Volume builds are throttled by `SegBuildScheduler` (separate budgets for canonical builds and
for image-aligned resampling) and the cached canonical volumes are bounded by
`SegVolumeCache`, both sized for studies with dozens of SEG files. See
[Memory-Management.md](../../../weasis-core/docs/Memory-Management.md) for the surrounding
budgets.

## Extension points

`SegRegionTool` (`org.weasis.core.ui.util`) is the contract a tool implements to list and toggle
segment regions; `SegRegionTree` and `SegRegionCellRenderer` provide the default tree UI used by
the 2D and 3D segmentation tools.
