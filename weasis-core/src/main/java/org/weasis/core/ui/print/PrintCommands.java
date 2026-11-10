/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.Capability;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.Term;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.Needs;
import org.weasis.core.api.command.OutputPath;
import org.weasis.core.api.command.PaneIndex;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.command.ViewId;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.service.UICore;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.print.PageSpec.Orientation;
import org.weasis.core.ui.util.ExportLayout;
import org.weasis.core.ui.util.PrintOptions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Command namespace {@code print}: the destinations, a page built from a viewer, its rendering to a
 * file and its sending to a printer.
 */
@Extension
@Command(
    name = "print",
    description = "Print a viewer layout: destinations, page, preview rendering, sending.",
    subcommands = {
      PrintCommands.Destinations.class,
      PrintCommands.Page.class,
      PrintCommands.Render.class,
      PrintCommands.Send.class
    })
@Since("5.0")
public class PrintCommands implements CommandExtension {

  private static final String PNG = "png"; // NON-NLS

  private Supplier<List<PrintDestination>> destinations = PrintDestinations::all;
  private Supplier<Optional<ImageViewerPlugin<?>>> selectedViewer = PrintCommands::selected;
  private PrintJobs jobs = PrintJobs.getInstance();

  @Override
  public List<Term> getTerms() {
    return List.of(
        Term.of("position", "where an image sits in its cell: center or top-left")); // NON-NLS
  }

  @Override
  public String getNamespace() {
    return "print"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  /** Replaces the destination lookup, for headless tests. */
  PrintCommands withDestinations(Supplier<List<PrintDestination>> supplier) {
    this.destinations = supplier;
    return this;
  }

  /** Replaces the selected viewer lookup, for headless tests. */
  PrintCommands withSelectedViewer(Supplier<Optional<ImageViewerPlugin<?>>> supplier) {
    this.selectedViewer = supplier;
    return this;
  }

  PrintCommands withJobs(PrintJobs registry) {
    this.jobs = registry;
    return this;
  }

  private static Optional<ImageViewerPlugin<?>> selected() {
    UICore core = GuiUtils.getUICore();
    if (core == null) {
      return Optional.empty();
    }
    Optional<ViewerPlugin<?>> plugin = core.getSelectedPlugin();
    return plugin.filter(ImageViewerPlugin.class::isInstance).map(p -> (ImageViewerPlugin<?>) p);
  }

  enum Background {
    WHITE(Color.WHITE),
    BLACK(Color.BLACK);

    final Color color;

    Background(Color color) {
      this.color = color;
    }

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  enum Text {
    AUTO(null),
    SMALL(TextSize.SMALL),
    NORMAL(TextSize.NORMAL),
    LARGE(TextSize.LARGE);

    final TextSize size;

    Text(TextSize size) {
      this.size = size;
    }

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  enum Position {
    CENTER,
    TOP_LEFT;

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  enum Toggle {
    ON,
    OFF;

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** The destination named by {@code --to}, resolved by id or by title. */
  PrintDestination destination(String idOrTitle) {
    List<PrintDestination> all = destinations.get();
    return all.stream()
        .filter(d -> d.id().equalsIgnoreCase(idOrTitle))
        .findFirst()
        .or(() -> all.stream().filter(d -> d.title().equalsIgnoreCase(idOrTitle)).findFirst())
        .orElseThrow(
            () ->
                CommandFailure.unknownHandle(
                    "Unknown print destination: " + idOrTitle, "print:destinations")); // NON-NLS
  }

  PrintJob job(String id) {
    return jobs.find(id)
        .orElseThrow(
            () ->
                CommandFailure.unknownHandle("Unknown print job: " + id, "print:page")); // NON-NLS
  }

  /** The paper named by {@code --paper}: a paper of the destination, else of the catalogue. */
  static Paper paper(String name, PrintDestination destination) {
    List<Paper> candidates = destination == null ? Paper.CATALOGUE : destination.papers();
    return candidates.stream()
        .filter(p -> p.id().equalsIgnoreCase(name))
        .findFirst()
        .or(() -> Paper.fromFilmSizeId(name.toUpperCase(Locale.ROOT)))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown paper: "
                        + name
                        + " (one of "
                        + String.join(", ", candidates.stream().map(Paper::id).toList())
                        + ")"));
  }

  static JsonObjectBuilder describe(PrintDestination d) {
    JsonArrayBuilder papers = Json.createArrayBuilder();
    d.papers().forEach(p -> papers.add(p.id()));
    String kind =
        d instanceof SystemPrinterDestination
            ? "printer" // NON-NLS
            : d.id().substring(0, Math.max(0, d.id().indexOf(':')));
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("id", d.id()) // NON-NLS
            .add("title", d.title()) // NON-NLS
            .add("kind", kind) // NON-NLS
            .add("color", d.isColor()) // NON-NLS
            .add("defaultPaper", d.defaultPaper().id()) // NON-NLS
            .add("papers", papers); // NON-NLS
    if (d instanceof SystemPrinterDestination printer) {
      json.add("default", printer.isSystemDefault()); // NON-NLS
    }
    OptionalInt dpi = d.resolutionDpi();
    if (dpi.isPresent()) {
      json.add("resolutionDpi", dpi.getAsInt()); // NON-NLS
    }
    return json;
  }

  static JsonObjectBuilder describe(PageSpec spec) {
    var area = spec.printableArea();
    return Json.createObjectBuilder()
        .add("paper", spec.paper().id()) // NON-NLS
        .add("orientation", spec.orientation().name().toLowerCase(Locale.ROOT)) // NON-NLS
        .add("widthMm", round(spec.widthMm())) // NON-NLS
        .add("heightMm", round(spec.heightMm())) // NON-NLS
        .add("printableWidthMm", round(area.getWidth())) // NON-NLS
        .add("printableHeightMm", round(area.getHeight())); // NON-NLS
  }

  /** What the job will look like on {@code destination}, or at 300 dpi when none is named. */
  static JsonObjectBuilder describe(PrintJob job, PrintDestination destination) {
    PageSpec spec = job.spec(destination);
    PrintOptions options = job.options();
    OptionalInt known = destination == null ? OptionalInt.empty() : destination.resolutionDpi();
    int dpi =
        Math.clamp(
            known.orElse(PrintResolution.DEFAULT_DPI),
            PrintResolution.MIN_DPI,
            PrintResolution.maxDpi());
    JsonObjectBuilder json =
        Json.createObjectBuilder().add("job", job.id()).add("cells", job.cellCount()); // NON-NLS
    job.viewId().ifPresent(v -> json.add("view", v)); // NON-NLS
    if (destination != null) {
      json.add("destination", destination.id()); // NON-NLS
    }
    json.add("page", describe(spec)); // NON-NLS
    JsonObjectBuilder opts =
        Json.createObjectBuilder()
            .add("textSize", textName(options.getTextSize())) // NON-NLS
            .add(
                "background", // NON-NLS
                Color.BLACK.equals(options.getBackground()) ? "black" : "white") // NON-NLS
            .add("annotations", options.isShowingAnnotations()) // NON-NLS
            .add("position", options.isCenter() ? "center" : "top-left") // NON-NLS
            .add("header", options.isHeader()); // NON-NLS
    job.masking().ifPresent(m -> opts.add("masking", m.id())); // NON-NLS
    json.add("options", opts); // NON-NLS
    json.add("pages", job.pageCount()); // NON-NLS
    if (job.pages() instanceof SeriesPages<?> series) {
      json.add("content", "series") // NON-NLS
          .add("frames", series.frameCount()) // NON-NLS
          .add("grid", series.cols() + "x" + series.rows()); // NON-NLS
    } else {
      json.add("content", job.cellCount() == 1 ? "pane" : "layout"); // NON-NLS
    }
    json.add("pixelReview", job.requiresPixelReview()); // NON-NLS
    PageFigures.of(job.layout(), spec, options, dpi)
        .ifPresent(
            f ->
                json.add(
                    "figures", // NON-NLS
                    Json.createObjectBuilder()
                        .add("cellWidthMm", round(f.cellWidthMm())) // NON-NLS
                        .add("cellHeightMm", round(f.cellHeightMm())) // NON-NLS
                        .add("imagePixelsPerInch", round(f.imagePixelsPerInch())) // NON-NLS
                        .add("magnified", f.magnified()) // NON-NLS
                        .add("textSize", textName(f.textSize())) // NON-NLS
                        .add("dpi", dpi) // NON-NLS
                        .add("dpiAssumed", known.isEmpty()))); // NON-NLS
    return json;
  }

  private static Optional<Path> pdf(PrintDestination destination) {
    return destination instanceof PdfFileDestination pdf ? pdf.file() : Optional.empty();
  }

  private static String textName(TextSize size) {
    return size == null ? "auto" : size.name().toLowerCase(Locale.ROOT); // NON-NLS
  }

  private static double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  @Command(
      name = "destinations",
      description =
          "Lists where a page can go: the system printers (the default one first) and the DICOM"
              + " printers, with their papers and resolution.")
  static class Destinations extends AbstractVerb {
    @ParentCommand PrintCommands owner;

    @Override
    protected void run() {
      List<PrintDestination> all = owner.destinations.get();
      JsonArrayBuilder list = Json.createArrayBuilder();
      all.forEach(d -> list.add(describe(d)));
      print(
          Json.createObjectBuilder()
              .add("destinations", list) // NON-NLS
              .add("count", all.size()) // NON-NLS
              .build()
              .toString());
    }
  }

  @Command(
      name = "page",
      description =
          "Builds a print job from a viewer, the selected one by default: its whole layout, or one"
              + " pane. Returns the job handle, the page and the cell figures.")
  static class Page extends AbstractVerb {
    @ParentCommand PrintCommands owner;

    @Option(
        names = "--view",
        paramLabel = "<view-id>",
        description = "View id from 'workspace:list' (default: the selected view).")
    ViewId viewId;

    @Option(
        names = "--pane",
        paramLabel = "<pane-index>",
        description = "Print only this pane of the layout, counting from 1.")
    PaneIndex pane;

    @Option(
        names = "--to",
        paramLabel = "<destination>",
        description =
            "Destination id or title from 'print:destinations': its papers and printable area"
                + " apply (default: default margins on the paper of the locale).")
    String to;

    @Option(
        names = "--paper",
        paramLabel = "<paper>",
        description = "Paper id, such as A4, Letter or 14x17in (default: the destination's).")
    String paper;

    @Option(
        names = "--orientation",
        paramLabel = "<portrait|landscape>",
        description = "Page orientation (default: landscape when the layout is wider than tall).")
    Orientation orientation;

    @Option(
        names = "--text",
        paramLabel = "<auto|small|normal|large>",
        description = "Text size of annotations and labels on paper (default: auto, 8 pt or 6 pt).")
    Text text = Text.AUTO;

    @Option(
        names = "--background",
        paramLabel = "<white|black>",
        description = "Page background (default: the destination's, white for paper).")
    Background background;

    @Option(
        names = "--annotations",
        paramLabel = "<on|off>",
        description = "Print the overlay and the graphics (default: on).")
    Toggle annotations = Toggle.ON;

    @Option(
        names = "--position",
        paramLabel = "<center|top-left>",
        description = "Where an image sits in its cell (default: center).")
    Position position = Position.CENTER;

    @Option(
        names = "--masking",
        paramLabel = "<profile-id>",
        description = "Identity masking profile applied to the page (default: none).")
    String masking;

    @Option(
        names = "--header",
        paramLabel = "<on|off>",
        description =
            "Header line with patient, study and page number above the cells (default: off).")
    @Since("5.0")
    Toggle header = Toggle.OFF;

    @Option(
        names = "--series",
        description =
            "Print the frames of the series of the pane (--pane, else the selected one) on pages"
                + " of --grid cells, instead of the layout as shown.")
    @Since("5.0")
    boolean series;

    @Option(
        names = "--grid",
        paramLabel = "<cols>x<rows>",
        description = "Cells per page with --series (default: 4x3).")
    @Since("5.0")
    String grid = "4x3"; // NON-NLS

    @Option(
        names = "--step",
        paramLabel = "<n>",
        description = "With --series, one frame out of n (default: 1).")
    @Since("5.0")
    int step = 1;

    @Option(
        names = "--frames",
        paramLabel = "<first>-<last>",
        description = "With --series, the range of frames, counting from 1 (default: all).")
    @Since("5.0")
    String frames;

    @Override
    protected void run() {
      PrintDestination destination = to == null ? null : owner.destination(to);
      MaskingProfile profile =
          masking == null
              ? null
              : MaskingModelRegistry.getInstance()
                  .profile(masking)
                  .orElseThrow(() -> invalid("Unknown masking profile: " + masking));
      Paper chosen =
          paper != null
              ? paper(paper, destination)
              : destination != null
                  ? destination.defaultPaper()
                  : SystemPrinterDestination.localeDefault();
      PrintOptions options = new PrintOptions();
      options.setTextSize(text.size);
      options.setShowingAnnotations(annotations == Toggle.ON);
      options.setCenter(position == Position.CENTER);
      options.setHeader(header == Toggle.ON);
      options.setBackground(
          background != null
              ? background.color
              : destination != null ? destination.defaultBackground() : Color.WHITE);

      PageSource pages =
          GuiExecutor.invokeAndRethrow(() -> IdentityMask.callForCommand(this::pageSource));
      ExportLayout<? extends ImageElement> first =
          GuiExecutor.invokeAndRethrow(() -> IdentityMask.callForCommand(() -> pages.page(0)));
      Orientation chosenOrientation =
          orientation != null
              ? orientation
              : destination == null
                  ? PrintJob.defaultOrientation(first)
                  : destination
                      .preferredOrientation()
                      .orElseGet(() -> PrintJob.defaultOrientation(first));
      PrintJob job =
          owner.jobs.create(
              viewId == null ? null : viewId.value(),
              pages,
              chosen,
              chosenOrientation,
              options,
              profile);
      print(describe(job, destination).build().toString());
    }

    private PageSource pageSource() {
      if (series) {
        return seriesPages();
      }
      return PageSource.of(exportLayout());
    }

    /** The frames of the series of the pane, on pages of the grid asked for. */
    private PageSource seriesPages() {
      ImageViewerPlugin<?> viewer = viewer();
      List<? extends ViewCanvas<?>> panes = viewer.getImagePanels();
      ViewCanvas<?> canvas;
      if (pane == null) {
        canvas = viewer.getSelectedViewCanvas();
        if (canvas == null && !panes.isEmpty()) {
          canvas = panes.getFirst();
        }
      } else {
        if (pane.value() > panes.size()) {
          throw new IllegalArgumentException(
              "Pane index out of range: " + pane + " (" + panes.size() + " panes)");
        }
        canvas = panes.get(pane.toOffset());
      }
      if (canvas == null || canvas.getSeries() == null) {
        throw new IllegalStateException("The pane shows no series");
      }
      int[] size = gridSize(grid);
      int[] range = frameRange(frames);
      return seriesOf(canvas, size[0], size[1], range[0], range[1], step);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PageSource seriesOf(
        ViewCanvas<?> canvas, int cols, int rows, int first, int last, int step) {
      return new SeriesPages((ViewCanvas) canvas, cols, rows, first, last, step);
    }

    private ExportLayout<? extends ImageElement> exportLayout() {
      ImageViewerPlugin<?> viewer = viewer();
      if (pane == null) {
        if (viewer.getImagePanels().isEmpty()) {
          throw new IllegalStateException("The viewer has no pane to print");
        }
        return exportOf(viewer);
      }
      List<? extends ViewCanvas<?>> panes = viewer.getImagePanels();
      if (pane.value() > panes.size()) {
        throw new IllegalArgumentException(
            "Pane index out of range: " + pane + " (" + panes.size() + " panes)");
      }
      return exportOf(panes.get(pane.toOffset()));
    }

    /** {@code <cols>x<rows>}, both at least 1. */
    static int[] gridSize(String text) {
      String[] parts =
          text == null ? new String[0] : text.toLowerCase(Locale.ROOT).split("x"); // NON-NLS
      try {
        if (parts.length == 2) {
          int cols = Integer.parseInt(parts[0].trim());
          int rows = Integer.parseInt(parts[1].trim());
          if (cols >= 1 && rows >= 1 && cols * rows <= 64) {
            return new int[] {cols, rows};
          }
        }
      } catch (NumberFormatException e) {
        // reported below
      }
      throw new IllegalArgumentException("--grid must be <cols>x<rows>, such as 4x3: " + text);
    }

    /** {@code <first>-<last>} counting from 1; {@code null} or empty means every frame. */
    static int[] frameRange(String text) {
      if (text == null || text.isBlank()) {
        return new int[] {1, 0};
      }
      String[] parts = text.split("-", -1);
      try {
        if (parts.length == 2) {
          int first = parts[0].isBlank() ? 1 : Integer.parseInt(parts[0].trim());
          int last = parts[1].isBlank() ? 0 : Integer.parseInt(parts[1].trim());
          if (first >= 1 && (last == 0 || last >= first)) {
            return new int[] {first, last};
          }
        } else if (parts.length == 1) {
          int only = Integer.parseInt(parts[0].trim());
          if (only >= 1) {
            return new int[] {only, only};
          }
        }
      } catch (NumberFormatException e) {
        // reported below
      }
      throw new IllegalArgumentException("--frames must be <first>-<last>, such as 1-40: " + text);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ExportLayout<? extends ImageElement> exportOf(ImageViewerPlugin<?> viewer) {
      return new ExportLayout((ImageViewerPlugin) viewer);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ExportLayout<? extends ImageElement> exportOf(ViewCanvas<?> canvas) {
      return new ExportLayout((ViewCanvas) canvas);
    }

    private ImageViewerPlugin<?> viewer() {
      if (viewId == null) {
        return owner
            .selectedViewer
            .get()
            .orElseThrow(() -> new IllegalStateException("No image viewer is selected"));
      }
      WorkspaceManager manager =
          GuiUtils.getUICore() == null ? null : GuiUtils.getUICore().getWorkspaceManager();
      return Optional.ofNullable(manager)
          .flatMap(m -> m.findView(viewId.value()))
          .filter(ImageViewerPlugin.class::isInstance)
          .map(v -> (ImageViewerPlugin<?>) v)
          .orElseThrow(
              () ->
                  CommandFailure.unknownHandle(
                      "Unknown image viewer id: " + viewId, "workspace:list")); // NON-NLS
    }
  }

  @Command(
      name = "render",
      description =
          "Renders the page of a job to a PNG file at a resolution, as the printer would receive"
              + " it: the preview of an agent and the test surface of a layout.")
  static class Render extends AbstractVerb {
    @ParentCommand PrintCommands owner;

    @Option(
        names = "--job",
        paramLabel = "<job-id>",
        required = true,
        description = "Job from 'print:page'.")
    String jobId;

    @Option(
        names = "--to",
        paramLabel = "<destination>",
        description = "Render for this destination: its printable area and resolution apply.")
    String to;

    @Option(
        names = "--dpi",
        paramLabel = "<dpi>",
        description = "Resolution of the raster (default: the destination's, else 150).")
    Integer dpi;

    @Option(
        names = "--gray",
        description = "Render in 8-bit gray, as a gray film printer receives it.")
    boolean gray;

    @Option(
        names = "--page",
        paramLabel = "<n>",
        description = "The page to render, counting from 1 (default: 1).")
    @Since("5.0")
    int page = 1;

    @Option(
        names = "--out",
        paramLabel = "<output-path>",
        required = true,
        description = "The PNG file to write.")
    OutputPath out;

    @Override
    protected void run() throws IOException {
      PrintJob job = owner.job(jobId);
      PrintDestination destination = to == null ? null : owner.destination(to);
      int resolution =
          Math.clamp(
              dpi != null
                  ? dpi
                  : destination == null
                      ? 150
                      : destination.resolutionDpi().orElse(PrintResolution.DEFAULT_DPI),
              PrintResolution.MIN_DPI,
              PrintResolution.maxDpi());
      if (CommandContext.current().isRemote() && job.requiresPixelReview()) {
        throw new IllegalStateException(
            "An image of the page may carry burned-in identification; apply a redaction, or have"
                + " a person review it (redact:attest), before rendering it remotely");
      }
      if (page < 1 || page > job.pageCount()) {
        throw invalid("--page must be between 1 and " + job.pageCount());
      }
      PageSpec spec = job.spec(destination);
      BufferedImage raster =
          IdentityMask.callForCommand(
              () ->
                  job.render(
                      spec,
                      resolution,
                      gray || (destination != null && !destination.isColor()),
                      page - 1));
      Path file = out.toAbsolutePath();
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      if (!ImageIO.write(raster, PNG, file.toFile())) {
        throw new IOException("Cannot write " + file);
      }
      print(
          Json.createObjectBuilder()
              .add("job", job.id()) // NON-NLS
              .add("file", file.toString()) // NON-NLS
              .add("page", page) // NON-NLS
              .add("pages", job.pageCount()) // NON-NLS
              .add("width", raster.getWidth()) // NON-NLS
              .add("height", raster.getHeight()) // NON-NLS
              .add("dpi", resolution) // NON-NLS
              .add("page", describe(spec)) // NON-NLS
              .build()
              .toString());
    }
  }

  @Command(
      name = "send",
      description =
          "Prints a job on a destination and releases it. Sending patient images to a device is"
              + " closed to callers unless the network.write policy of this instance allows it,"
              + " as for dicom:send.")
  @Needs(Capability.SEND_DICOM)
  static class Send extends AbstractVerb {
    @ParentCommand PrintCommands owner;

    @Option(
        names = "--job",
        paramLabel = "<job-id>",
        required = true,
        description = "Job from 'print:page'.")
    String jobId;

    @Option(
        names = "--to",
        paramLabel = "<destination>",
        required = true,
        description = "Destination id or title from 'print:destinations'.")
    String to;

    @Option(names = "--copies", paramLabel = "<n>", description = "Number of copies (default: 1).")
    int copies = 1;

    @Option(
        names = "--out",
        paramLabel = "<output-path>",
        description = "The file to write, for the PDF file destination (file:pdf).")
    @Since("5.0")
    OutputPath out;

    @Option(
        names = "--dpi",
        paramLabel = "<dpi>",
        description = "Resolution of the pages written to the file (default: 300).")
    @Since("5.0")
    Integer dpi;

    @Override
    protected void run() throws Exception {
      PrintJob job = owner.job(jobId);
      PrintDestination destination = owner.destination(to);
      if (copies < 1) {
        throw invalid("--copies must be at least 1");
      }
      if (destination instanceof PdfFileDestination pdf) {
        if (out == null) {
          throw invalid("--out is required for the PDF file destination");
        }
        pdf.setFile(out.toAbsolutePath());
        if (dpi != null) {
          pdf.setResolution(dpi);
        }
      } else if (out != null || dpi != null) {
        throw invalid("--out and --dpi apply to the PDF file destination only");
      }
      JsonObjectBuilder json = describe(job, destination);
      pdf(destination).ifPresent(f -> json.add("file", f.toString())); // NON-NLS
      job.send(destination, copies);
      owner.jobs.remove(job.id());
      print(json.add("copies", copies).add("sent", true).build().toString()); // NON-NLS
    }
  }
}
