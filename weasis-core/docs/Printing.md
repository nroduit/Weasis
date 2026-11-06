# Printing

How a layout of views becomes a page on a system printer, on a DICOM film printer or in the preview of the print dialog, and why all three receive the same thing.

## Page model

The page is described in millimeters, independently of any device, by the records of `org.weasis.core.ui.print`:

- `Paper` is a physical sheet or film: a size, a DICOM `FilmSizeID` and a Java `MediaSizeName` when the size has one.
  A catalog holds the common papers; any other size is a custom paper.
- `PageSpec` is a page: a paper, its orientation, the margins that bound the printable area, the gutter between cells, the background and the border of the cells.
- `PageLayout` places the cells of a `MigLayoutModel` in the printable area as a uniform grid with spans and gutters; a tiled pane is split into a sub-grid of its own.
  Each cell is a `CellBox`.
- `CellFit` fits what a view showed into a cell: a best-fit view keeps the aspect of its image, a zoomed view keeps the part of the image its canvas showed, a quarter turn swaps the sides.
- `TextSize` is the size of the overlay and of the graphic labels on paper, in points.
  Without a choice, a cell narrower than 60 mm prints small text.

## Renderer

`PageRenderer` draws the cells of an `ExportLayout` on a `Graphics2D` whose unit is the device pixel, given *pixels per millimeter* and nothing else about the destination.
Its three sinks differ only in that figure and in the color model of the raster: the preview renders at the scale that fits its panel, the system printer at the device resolution, the DICOM printer at the film matrix of the node.

Every view of the layout is an `ExportImage`, a detached copy of a view: its display operations, its state and a copy of its visible graphics, whose labels are laid out for the cell without touching the live view.
Two conventions keep text and image right at any resolution:

- **Logical pixels.** The view keeps the geometry of a 96 dpi screen and is drawn under a *render scale*, the device pixels per logical pixel.
  The scale folds the text size in: a point size and the base overlay font fix it, so the info layer, the labels and the strokes of the graphics need no font context.
- **One resampling.** The image is not drawn through the scaled graphics.
  The display pipeline renders it at the device resolution (`GraphicsPane.getRenderScale` scales the affine matrix and the bounds of the affine operation) and the raster is copied under the inverse scale, snapped to whole device pixels.
  The platform never resamples it; the affine operation does the single resampling, bicubic when the image is magnified, bilinear when it is reduced.

A cell whose raster would exceed the pixel budget of `PrintResolution` is rendered at the resolution that fits the budget and scaled up by the platform, instead of being split into bands.

## Resolution

The resolution is derived, shown to the user, never asked.
For a system printer, `PrintResolution` takes the resolution the job attributes request, else the one the print service reports, else the one behind the printer graphics, else 300 dpi, capped by the system property `weasis.print.max.dpi` (600 dpi by default).
A DICOM printer does not report its matrix: it is configured on the node.
The preview uses whatever fits its panel.

## Pages

A job prints a `PageSource`: one fixed page, an `ExportLayout`, or `SeriesPages`, the frames of the series of a view (every n-th one of a range) laid out columns by rows per page, each cell an export view of one frame with the display state of the view and fitted to its cell.
Pages are built on demand and one is kept at a time, so a long series costs one page of views, whichever sink reads it.
The header band of a `PageSpec`, when the options ask for it, holds one line above the cells: patient, study and page number, formatted through the active identity mask.

## Destinations and the dialog

A `PrintDestination` is where a page can go: it names its papers and their printable area, its resolution when known, and prints a layout through the renderer.
`SystemPrinterDestination` wraps a print service; the attribute set its native dialog returns (tray, duplex, driver resolution) is kept and sent with the job, and the `PageSpec` the dialog shows is built from the same `PrinterJob.getPageFormat` call the job uses, so the preview and the paper agree by construction.
Plugins contribute other destinations through the `PrintDestinationProvider` extension point.

`PrintDialog` owns the destination, the paper, the orientation, the copies, the content (the layout, the selected view, or the frames of a series on a grid of pages), the text size, the background, the header line and the masking profile.
`PagePreview` renders the page on a worker thread, debounced, one raster at a time, and `PageFigures` tells the user what the choice means: the size of a cell, the image pixels per inch of paper and whether the image is magnified.
The native dialog of the system is a secondary step (*Printer settings…*), whose choices are read back into the dialog.
The settings last used are remembered per destination in the machine persistence.

The system sink is `PagePrintable`: it scales the printer graphics from points to device pixels, fills a non-white background and hands each page to the renderer.
`PdfFileDestination` writes the pages to a PDF file at a chosen resolution through `PdfWriter`, one lossless raster per page, so a file does not depend on the virtual printer of the operating system.

The DICOM sink lives in the DICOM explorer plugin: `DicomPrintDestination` wraps a print node and `DicomPrint` renders the page as one film raster, gray or RGB, at the film resolution of the node and sends it as the single image box of a film box (Basic Grayscale or Color Print Management).
The film size, the orientation and the empty-image density of the film box come from the page; the other attributes (medium, priority, destination, magnification, smoothing, trim, border density) are the defaults of the node, adjusted for the job in the panel the destination adds to the dialog.

## Commands

The `print` namespace drives the same model without the dialog: `print:destinations` lists the destinations, `print:page` builds a `PrintJob` from a viewer (its layout or one pane) and keeps it in the bounded `PrintJobs` registry under a handle, `print:render` writes the page of a job to a PNG at a resolution, and `print:send` prints it on a destination and releases it.
A job holds the detached copy of the views, the paper, the orientation, the options and the masking profile, so rendering and sending agree with the preview.
For a remote caller the overlay is masked and a page whose pixels may carry identity (`PixelReviewAdvisor`) is not rendered; sending is gated by the network policy of the instance, like `dicom:send`.
