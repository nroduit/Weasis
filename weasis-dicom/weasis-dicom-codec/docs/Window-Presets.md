# Window/level presets

Concepts of the configured window/level presets (`org.weasis.dicom.codec.display`): the presets
offered after the window values carried by the image.

## Order of the preset list

For each image the list is:

1. the windows and VOI LUTs of the image, those of an applied presentation state first (PS3.3
   C.11.2 and C.11.8), each with its own explanation and VOI LUT Function;
2. *Auto Level*, the value range of the image;
3. the configured presets whose conditions the image meets.

The first entry is the default. A configured preset flagged `default` takes the first place only
when neither the image nor its presentation state carries a window or a VOI LUT.

## Model

`WindowPreset` is one configured preset, read and written by `WindowPresetJson`:

- a stable `id` (`weasis.`, `site.`, `user.` prefix), used for shadowing, for keeping the selected
  preset while scrolling and by commands;
- the modalities it applies to (Modality codes, upper case);
- window and level, either in modality values (after the Modality LUT, e.g. HU) or in percent of a
  range: the image range, or the series range from Smallest / Largest Pixel Value in Series;
- a LUT shape: a DICOM VOI LUT Function (LINEAR, LINEAR_EXACT, SIGMOID) or a Weasis extension;
- a shortcut digit (3 to 9; 0 to 2 belong to *Auto Level* and the windows of the image). The
  built-in CT presets use 3 to 7 and leave 8 and 9 free; when presets offered for an image share a
  digit, the one of the latest layer gets it;
- conditions (`When`): minimum Bits Stored, presence of a Modality LUT, anatomy.

## Layers

`WindowPresetRegistry` merges the presets by id, a later layer replacing an earlier preset in
place:

| Layer | Source |
|---|---|
| Built-in | `windowPresets.json` resource (CT presets) |
| Site | path or URL of the launcher preference `weasis.wl.presets.config` |
| User | `customWindowPresets.json` in the preference folder, written by the preferences page |

The site and user documents are read on configuration and on `reload()`, the site URL outside the
registry lock; saving a user preset only writes the user document and merges in memory. A document
that cannot be read leaves its layer unchanged, and an unreadable user document is kept aside
(`.bak`) before the next write replaces it.

The registry is the `ModalityPresetProvider` of weasis-dicom-tools, which asks it for the presets
of each image; listeners are told when a layer changes so open images refresh their list.

## Anatomy

Presets use the anatomy model of weasis-dicom-tools (`org.weasis.dicom.ref`): the image anatomy is
its resolved `AnatomicRegion` (Anatomic Region Sequence, else Body Part Examined), with its region
groups. Anatomy is written with the anatomy notation — a region group (`CHEST`), a coded value
(`SCT:10200004`) or a Body Part Examined term — and a token the notation does not resolve is
compared with the Body Part Examined of the image.

- `bodyPart` is a condition: the preset is offered only for that anatomy.
- `preferBodyPart` only orders: the presets preferred for the image anatomy come first, and the
  preset stays offered elsewhere. The built-in presets use it with region groups (Lung → `CHEST`).
