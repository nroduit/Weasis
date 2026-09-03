# Measurement tools

How the graphic tools of the **Draw & Measure** panel are described, discovered, persisted and
driven without the UI. The classes live in `org.weasis.core.ui.model.graphic`.

## Tools and the registry

A tool is a `GraphicToolDescriptor`: a stable **key** (`weasis.line`, `myplugin.tool`), a
`ToolCategory` (`MEASURE`, `DRAW` or `ADVANCED`), a supplier of a configured prototype `Graphic`,
the class bound to its XML element, the system property that hides it and an optional default
shortcut. Keys, not class names or localized labels, identify tools in preferences, profiles and
commands.

`GraphicRegistry` holds the tools and is the single source for the toolbar dropdowns, the panel
grids, the preference pages, the XML binding and the commands. It is populated by
`GraphicToolProvider`s: `BuiltinGraphicTools` registers the core palette first, then each
provider published as an OSGi service adds its own tools. The registry notifies listeners on every
change so open viewers rebuild their palettes. A tool is hidden by setting its hide property to
`false` (`weasis.measure.<name>` and `weasis.draw.<name>` for the built-in tools,
`weasis.tool.<key>` otherwise).

Every palette tool, built-in or contributed, gets an entry in `ShortcutManager` when it is
registered (`graphic.<key>`; the four shortcuts that existed before keep their former ids), with
the default key of its descriptor or none. The key a `Graphic` answers to is resolved through the
registry, so a tool class never declares its own shortcut.

## Tool panels

A descriptor may carry a panel factory. The Draw & Measure tool shows that panel in a collapsible
section while the tool is selected in the palette or one of its graphics is selected in the view,
and refreshes it through `ToolPanel` as the graphic is edited. The cardiothoracic ratio tool
(drawing step, view position warning) is the built-in example; a plugin tool gets the same section
by giving its descriptor a panel. Independently of the tool, the panel plots the intensity profile of any
selected open measurement (`IntensityProfile`), sampled along its path. Each sample keeps the
centre of its pixel, so pointing at the curve reads the sample and marks that pixel on the image
through the probe position of the view (`ViewCanvas.setProbePosition`), a transient mark that is
not a graphic and is never stored.

The freehand trace can be magnetic: while Shift is held, `EdgeSnapper` computes, on a window
around the last anchor, the cheapest path to the cursor. It is a live wire (intelligent scissors):
the image is normalized to the window / level of the view, so the edges that count are the ones
the user sees, and each step is weighted by gradient magnitude, Laplacian and gradient direction.
The search advances only as far as the cursor and resumes from there, and the cursor is pulled
onto the nearest edge.

## Tool profiles

A `MeasurementProfile` is a named palette: the tools of both palettes in order, defaults for new
graphics (style, whether a plain drag draws an upright or an oblique rectangle, and the decimals
of the measured values), which
measurements are labelled on the image and which pixel statistics are computed;
sections left out keep the current settings. `MeasurementProfileRegistry` merges the bundled
profiles, an optional site document (`weasis.measure.profiles.config`, file or URL) and the
user's own (`measurementProfiles.json` in the preference folder), later documents overriding by
id, and holds the active choice: an explicit profile, or the automatic one for the modality of
the selected series with the default profile as fallback. When several profiles name a modality
the user's own wins, then the site's, then the bundled one; within one origin, the first of the
document. The palettes of every viewer type
follow the active profile; `MeasurementProfiles` applies its defaults, labels and statistics and
captures the current settings as a new profile. A profile is an overlay (`ProfileOverlay`): what
it replaces is kept aside and put back before the next profile is applied and before the
preferences are saved, so a section a profile leaves out always shows the user's own setting and
a profile never becomes the user's preference. Only a setting that still holds the profile's value
is put back: one the user changed while the profile was active is the user's own and is kept. Profiles are edited, imported and exported from
the Draw & Measure preferences.

## Measurements

Each `Measurement` has a stable **key** (`length`, `area`, `stats.mean`), a localized name and the
flags that say whether it is computed and shown on the image. Preferences store the on-image label
choice per tool key and measurement key; files written with the numeric ids of earlier versions
are still read.

A measurement also has a `QuantityKind` (length, coordinate, area, angle, ratio, count, pixel
value, pixel statistic, moment), derived from its key unless the tool states it. `MeasureFormat`
is the one place that turns a value into text, for the labels on the image and for the table: by
default the decimals follow the kind and what the image can resolve — a length one digit finer
than the pixel size in the display unit, a pixel value no finer than the step between two stored
values, a ratio with two decimals, the rest by significant digits — or a fixed number when the
user asks for one. Thousands are never grouped. A `MeasureItem` always keeps the unrounded value,
which is what copies and exports read. `MeasureTables` gives every table of measured values (the
Draw & Measure panel, the histogram and segmentation statistics) that rendering and the copy
menu.

## Patient space

A graphic edited on an image that knows its position in the patient (a DICOM image with
orientation and position) also carries a `SpatialAnchor`: its handle points in patient
coordinates, the frame of reference and the normal of the plane it was drawn on. The image
exposes that position as a `PlaneGeometry` through its measurable layer; images without one keep
2D pixel points only. The anchor is refreshed each time the graphic is edited in a view, written
in the XML next to the pixel points, and is what lets a measurement be located on another plane
of the same frame of reference.

The Draw & Measure tool lists the values of the selected graphic. Its context menu copies one
value or the whole table with its header, at full precision. Under the values it recalls the origin of the pixel size and the slice thickness when
the image provides them.

## Visibility on the image

No line color stands out on every image, so measurements and drawings are painted over a dark
halo (`GraphicOutline`, a preference, on by default): the line carries its own dark surround and
its color only has to be bright. Labels are outlined the same way. The color of new graphics is a
preference whose default lives in `ViewSetting`; `Graphic.DEFAULT_COLOR` is only the color of a
stored graphic that states none and never changes. Graphics of a DICOM presentation state keep
the style of their author; on export the halo becomes the `OUTLINED` shadow style of the line
style sequence.

## Persistence

The model is written by `XmlSerializer` as a `<presentation>` element: references, layers and
graphics, one element per graphic named after its tool. The root carries a `version` attribute;
a file without it was written by an earlier version and is read the same way. Changes to the
format are additive: new tools add elements, new properties add attributes with defaults. A
graphic of a tool that is not installed is kept as is and written back unchanged, so a file never
loses content by passing through an installation that lacks a plugin. The binding context is
built from the registry and follows it.

For DICOM images the same XML travels gzipped in a private tag of the Presentation State produced
by the explorer, next to the standard graphic annotation module.

## Headless use

`MeasurementService` is the entry point shared by the panel and the commands: build a graphic from
a tool key and image points, add it to a view, compute its values in a display unit, get the pixel
statistics of an area, and read or write the presentation model.

## Adding a tool

Implement the graphic (usually a subclass of `AbstractDragGraphic` or `AbstractDragGraphicArea`)
with an `@XmlRootElement` naming its element and `Measurement` constants with keys, publish a
`GraphicToolProvider` returning its descriptor, and add its icon. Nothing in the core needs to
change.
