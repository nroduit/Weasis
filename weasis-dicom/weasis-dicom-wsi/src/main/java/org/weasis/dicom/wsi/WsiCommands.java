/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.ImagePoint;
import org.weasis.core.api.command.Magnification;
import org.weasis.core.api.command.OutputPath;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.editor.image.ViewDisplayState;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.pr.DicomPrSerializer;
import org.weasis.dicom.explorer.pr.PrGraphicUtil;
import org.weasis.dicom.explorer.sr.MeasurementReport;
import org.weasis.dicom.explorer.sr.MeasurementReport.ImageRef;
import org.weasis.dicom.explorer.sr.MeasurementReport.Region;
import org.weasis.dicom.explorer.sr.MeasurementReport.Value;
import org.weasis.dicom.viewer2d.View2dCommands;
import org.weasis.dicom.viewer2d.View2dCommands.Target;
import org.weasis.dicom.viewer2d.View2dCommands.ViewOptions;
import org.weasis.dicom.wsi.ann.BulkAnnotations.Group;
import org.weasis.dicom.wsi.model.PyramidLevel;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.WsiImageType;
import org.weasis.dicom.wsi.tile.TilePlan;
import org.weasis.dicom.wsi.view.TiledImageLayer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/** The {@code wsi:} namespace: what a whole slide is and where its view looks. */
@Extension
@Command(
    name = "wsi",
    description =
        "Whole slides (modality SM) shown in the slide viewer: the pyramid and the calibration of a"
            + " slide, and the place and magnification its view shows. Base level pixels are the"
            + " image coordinates of the 'view2d:' verbs on a slide.",
    subcommands = {
      WsiCommands.Info.class,
      WsiCommands.GoTo.class,
      WsiCommands.Annotations.class,
      WsiCommands.Export.class
    })
public class WsiCommands implements CommandExtension {

  /** Longest wait for the tiles of a view. */
  static final long COMPLETE_WAIT_MS = 10_000;

  private static final double MM_TO_MICROMETERS = 1000.0;

  @Override
  public String getNamespace() {
    return "wsi"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  /** The slide view of a pane, with its slide read. */
  record SlideTarget(Target target, WsiView2d view, Slide slide, TiledImageLayer layer) {
    static SlideTarget resolve(ViewOptions options) {
      Target target = options.resolve();
      if (!(target.view() instanceof WsiView2d view)) {
        throw new IllegalStateException("The pane does not show a whole slide");
      }
      Slide slide =
          view.slide()
              .orElseThrow(
                  () -> new IllegalStateException("The slide of the pane is not read yet"));
      TiledImageLayer layer =
          view.layer()
              .orElseThrow(
                  () -> new IllegalStateException("The slide of the pane is not read yet"));
      return new SlideTarget(target, view, slide, layer);
    }
  }

  @Command(
      name = "info",
      description =
          "Describes the slide of a pane: its size in base level pixels, pixel spacing, objective"
              + " magnification, pyramid levels, focal planes, optical paths and associated images,"
              + " and what the pane shows: zoom, magnification, centre, viewport, level and tiles.")
  @Since("5.0")
  static class Info extends AbstractVerb {
    @Mixin ViewOptions target;

    @Override
    protected void run() {
      print(
          GuiExecutor.invokeAndRethrow(
              () -> {
                SlideTarget resolved = SlideTarget.resolve(target);
                return Json.createObjectBuilder()
                    .add(
                        "target",
                        ViewDisplayState.target(resolved.target().container(), resolved.view()))
                    .add("slide", slide(resolved.slide()))
                    .add("display", display(resolved))
                    .build()
                    .toString();
              }));
    }
  }

  @Command(
      name = "goto",
      description =
          "Shows a place of the slide: --at centres the view on a base level pixel, --zoom sets the"
              + " scale (1: one base pixel per canvas pixel) or --objective the magnification shown"
              + " (20 for 20x), --focal-plane and --optical-path pick the plane of a Z-stack and the"
              + " channel of a multi-path slide; --complete answers once every tile of the view is"
              + " loaded, so that a capture is sharp. With no argument the display comes back"
              + " unchanged.")
  @Since("5.0")
  static class GoTo extends AbstractVerb {
    @Mixin ViewOptions target;

    @Option(
        names = "--at",
        paramLabel = "<image-point>",
        description = "Base level pixel x,y put at the centre of the view.")
    ImagePoint at;

    @Option(
        names = "--zoom",
        paramLabel = "<magnification>",
        description = "Scale of the view, 1 being one base level pixel per canvas pixel.")
    Magnification zoom;

    @Option(
        names = "--objective",
        paramLabel = "<number>",
        description =
            "Magnification shown, as an objective power: 20 shows the slide as a 20x objective"
                + " would (needs the objective power or the pixel spacing of the slide).")
    Double objective;

    @Option(
        names = "--focal-plane",
        paramLabel = "<index>",
        description = "Focal plane of a Z-stack shown, from 1 ('focalPlanes' of 'wsi:info').")
    Integer focalPlane;

    @Option(
        names = "--optical-path",
        paramLabel = "<optical-path-id>",
        description = "Optical path shown, by its identifier ('opticalPaths' of 'wsi:info').")
    String opticalPath;

    @Option(
        names = "--complete",
        description = "Answer once every tile of the view is loaded, after 10 s at the latest.")
    boolean complete;

    @Override
    protected void run() {
      if (zoom != null && objective != null) {
        throw invalid("Give --zoom or --objective, not both");
      }
      if (objective != null && objective <= 0) {
        throw invalid("--objective must be positive");
      }
      if (focalPlane != null && focalPlane < 1) {
        throw invalid("--focal-plane counts from 1");
      }
      SlideTarget resolved =
          GuiExecutor.invokeAndRethrow(
              () -> {
                SlideTarget slideTarget = SlideTarget.resolve(target);
                Double scale = scale(slideTarget.slide());
                Point2D centre = at == null ? null : at.toPoint();
                boolean plane = focalPlane != null || opticalPath != null;
                if (scale != null || centre != null || plane) {
                  View2dCommands.focus(slideTarget.target());
                }
                if (plane) {
                  WsiView2d view = slideTarget.view();
                  int path =
                      opticalPath == null
                          ? view.opticalPath()
                          : slideTarget.slide().base().opticalPaths().indexOf(opticalPath);
                  if (path < 0) {
                    throw new IllegalArgumentException(
                        "Unknown optical path "
                            + opticalPath
                            + ", the slide has "
                            + slideTarget.slide().base().opticalPaths());
                  }
                  view.setPlane(focalPlane == null ? view.focalPlane() : focalPlane - 1, path);
                }
                if (scale != null || centre != null) {
                  slideTarget.view().goTo(scale, centre);
                } else if (plane) {
                  slideTarget.view().goTo(null, null); // paints: the tiles of the plane are planned
                }
                return slideTarget;
              });
      if (complete) {
        resolved.layer().awaitPlanned(COMPLETE_WAIT_MS);
      }
      print(
          GuiExecutor.invokeAndRethrow(
              () ->
                  Json.createObjectBuilder()
                      .add(
                          "target",
                          ViewDisplayState.target(resolved.target().container(), resolved.view()))
                      .add("display", display(resolved))
                      .build()
                      .toString()));
    }

    private Double scale(Slide slide) {
      if (zoom != null) {
        return zoom.value();
      }
      if (objective == null) {
        return null;
      }
      double base =
          slide
              .magnification()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "The slide has neither an objective power nor a pixel spacing: use"
                              + " --zoom"));
      return objective / base;
    }
  }

  @Command(
      name = "annotations",
      description =
          "The bulk annotation groups of the slide of a pane (DICOM Microscopy Bulk Simple"
              + " Annotations of the study: nuclei, cells, regions), each with its label, graphic"
              + " type, count, colour and whether it is drawn; --group with --show or --hide draws"
              + " or hides groups (all of them without --group). The groups come back after the"
              + " change.")
  @Since("5.0")
  static class Annotations extends AbstractVerb {
    @Mixin ViewOptions target;

    @Option(
        names = "--group",
        split = ",",
        paramLabel = "<group-id>",
        description = "Groups by their id from the list, comma separated (default: every group).")
    List<String> groups;

    @Option(names = "--show", description = "Draw the groups.")
    boolean show;

    @Option(names = "--hide", description = "Hide the groups.")
    boolean hide;

    @Override
    protected void run() {
      if (show && hide) {
        throw invalid("Give --show or --hide, not both");
      }
      if (groups != null && !show && !hide) {
        throw invalid("--group needs --show or --hide");
      }
      print(
          GuiExecutor.invokeAndRethrow(
              () -> {
                SlideTarget resolved = SlideTarget.resolve(target);
                List<WsiView2d.AnnotationGroup> all = resolved.view().annotationGroups();
                if (show || hide) {
                  for (WsiView2d.AnnotationGroup group : selected(all)) {
                    group.setShown(show);
                  }
                  resolved.view().repaint();
                }
                JsonArrayBuilder json = Json.createArrayBuilder();
                int id = 1;
                for (WsiView2d.AnnotationGroup group : all) {
                  json.add(groupJson(id++, group));
                }
                return Json.createObjectBuilder()
                    .add(
                        "target",
                        ViewDisplayState.target(resolved.target().container(), resolved.view()))
                    .add("groups", json) // NON-NLS
                    .build()
                    .toString();
              }));
    }

    private List<WsiView2d.AnnotationGroup> selected(List<WsiView2d.AnnotationGroup> all) {
      if (groups == null) {
        return all;
      }
      List<WsiView2d.AnnotationGroup> found = new java.util.ArrayList<>();
      for (String id : groups) {
        WsiView2d.AnnotationGroup match = null;
        for (int i = 0; i < all.size(); i++) {
          WsiView2d.AnnotationGroup g = all.get(i);
          if (id.trim().equals(String.valueOf(i + 1)) || id.trim().equals(g.group().uid())) {
            match = g;
          }
        }
        if (match == null) {
          throw new IllegalArgumentException("Unknown annotation group " + id + ": see the list");
        }
        found.add(match);
      }
      return found;
    }

    private static JsonObject groupJson(int id, WsiView2d.AnnotationGroup group) {
      Group g = group.group();
      JsonObjectBuilder json =
          Json.createObjectBuilder()
              .add("id", id) // NON-NLS
              .add("label", g.label()) // NON-NLS
              .add("graphicType", g.type().name().toLowerCase(java.util.Locale.ROOT)) // NON-NLS
              .add("count", g.count()) // NON-NLS
              .add("shown", group.shown()) // NON-NLS
              .add("indexed", group.annotations().isReady()) // NON-NLS
              .add(
                  "color", // NON-NLS
                  "#%02x%02x%02x"
                      .formatted(g.color().getRed(), g.color().getGreen(), g.color().getBlue()));
      if (g.uid() != null) {
        json.add("uid", g.uid()); // NON-NLS
      }
      if (g.category() != null) {
        json.add("category", g.category()); // NON-NLS
      }
      if (g.propertyType() != null) {
        json.add("type", g.propertyType()); // NON-NLS
      }
      if (g.algorithm() != null) {
        json.add("algorithm", g.algorithm()); // NON-NLS
      }
      json.add("object", group.element().getLabel()); // NON-NLS
      return json.build();
    }
  }

  @Command(
      name = "export",
      description =
          "Writes the measurements and drawings of the slide of a pane as a DICOM Structured"
              + " Report (TID 1500 Measurement Report, one planar region per graphic on the base"
              + " level of the slide, lengths and areas in micrometers), a file that 'dicom:load'"
              + " brings back as regions over the slide and that other viewers read.")
  @Since("5.0")
  static class Export extends AbstractVerb {
    @Mixin ViewOptions target;

    @Option(
        names = "--out",
        required = true,
        paramLabel = "<output-path>",
        description = "The DICOM file to write (.dcm).")
    OutputPath out;

    @Option(
        names = "--force",
        description = "Replace the file when it exists and was not written by this session.")
    boolean force;

    @Override
    protected void run() throws IOException {
      Export export = this;
      record Built(Attributes report, int regions, int skipped, JsonObject target) {}
      Built built =
          GuiExecutor.invokeAndRethrow(
              () -> {
                SlideTarget resolved = SlideTarget.resolve(target);
                WsiView2d view = resolved.view();
                Slide slide = resolved.slide();
                DicomImageElement carrier = view.getImage();
                Attributes source =
                    carrier == null || carrier.getMediaReader() == null
                        ? new Attributes()
                        : carrier.getMediaReader().getDicomObject();
                String base = slide.base().instanceUIDs().iterator().next();
                ImageRef image =
                    new ImageRef(
                        TagD.getTagValue(view.getSeries(), Tag.StudyInstanceUID, String.class),
                        TagD.getTagValue(view.getSeries(), Tag.SeriesInstanceUID, String.class),
                        UID.VLWholeSlideMicroscopyImageStorage,
                        base,
                        null,
                        true);
                List<Region> regions = new ArrayList<>();
                int skipped = 0;
                MeasurableLayer measurable = view.getMeasurableLayer();
                for (Graphic graphic : view.getGraphicManager().getAllGraphics()) {
                  if (graphic.getLayer() == null
                      || !(graphic.getLayer().getType() == LayerType.MEASURE
                          || graphic.getLayer().getType() == LayerType.DRAW)) {
                    continue;
                  }
                  Region region = export.region(graphic, measurable);
                  if (region == null) {
                    skipped++;
                  } else {
                    regions.add(region);
                  }
                }
                if (regions.isEmpty()) {
                  throw new IllegalStateException(
                      "The pane has no measurement or drawing to export");
                }
                return new Built(
                    MeasurementReport.build(
                        source, image, regions, "Slide measurements"), // NON-NLS
                    regions.size(),
                    skipped,
                    ViewDisplayState.target(resolved.target().container(), view).build());
              });
      Path file = out.value();
      if (Files.exists(file) && !force && !CommandContext.wasWritten(file)) {
        throw invalid("The file exists: " + file + " (--force replaces it)");
      }
      MeasurementReport.write(built.report(), file);
      CommandContext.current().addFile(file);
      print(
          Json.createObjectBuilder()
              .add("target", built.target()) // NON-NLS
              .add("file", out.toAbsolutePath().toString()) // NON-NLS
              .add("sopInstanceUID", built.report().getString(Tag.SOPInstanceUID)) // NON-NLS
              .add("regions", built.regions()) // NON-NLS
              .add("skipped", built.skipped()) // NON-NLS
              .build()
              .toString());
    }

    // A graphic as a region on the base level, with the lengths and areas the report can name
    private Region region(Graphic graphic, MeasurableLayer measurable) {
      DicomPrSerializer.GraphicTypeInfo info = DicomPrSerializer.getGraphicTypeInfo(graphic);
      String type = info.graphicType();
      List<Point2D> points = info.points();
      if (info.requiresShapeTransformation()) {
        points = flatten(graphic.getShape());
        type = PrGraphicUtil.POLYLINE;
      }
      if (type == null || points == null || points.isEmpty()) {
        return null;
      }
      List<MeasureItem> items =
          Measurement.computingAll(
              () -> graphic.computeMeasurements(measurable, true, Unit.MICROMETER));
      List<Value> values = new ArrayList<>();
      for (MeasureItem item : items) {
        if (item.getValue() instanceof Number n) {
          MeasurementReport.value(
                  item.getMeasurement().getKind(),
                  item.getMeasurement().getKey(),
                  n.doubleValue(),
                  Unit.MICROMETER)
              .ifPresent(values::add);
        }
      }
      String label = graphic.getUIName() == null ? graphic.getUuid() : graphic.getUIName();
      return new Region(label, label, type, DicomPrSerializer.getGraphicsPoints(points), values);
    }

    // The outline of a shape as a polyline, closed when the shape is
    private static List<Point2D> flatten(java.awt.Shape shape) {
      List<Point2D> points = new ArrayList<>();
      if (shape == null) {
        return points;
      }
      double[] coords = new double[6];
      Point2D first = null;
      for (java.awt.geom.PathIterator it = shape.getPathIterator(null, 0.5);
          !it.isDone();
          it.next()) {
        int kind = it.currentSegment(coords);
        if (kind == java.awt.geom.PathIterator.SEG_CLOSE) {
          if (first != null) {
            points.add(first);
          }
        } else {
          Point2D p = new Point2D.Double(coords[0], coords[1]);
          if (kind == java.awt.geom.PathIterator.SEG_MOVETO) {
            first = p;
          }
          points.add(p);
        }
      }
      return points;
    }
  }

  static JsonObject slide(Slide slide) {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("width", slide.width()) // NON-NLS
            .add("height", slide.height()); // NON-NLS
    slide
        .base()
        .pixelSpacing()
        .ifPresent(
            s ->
                json.add(
                        "pixelSpacing", // NON-NLS
                        Json.createArrayBuilder()
                            .add(s.row() * MM_TO_MICROMETERS)
                            .add(s.column() * MM_TO_MICROMETERS))
                    .add("pixelSpacingUnit", "um")); // NON-NLS
    slide.objectiveLensPower().ifPresent(p -> json.add("objectivePower", p)); // NON-NLS
    slide.magnification().ifPresent(m -> json.add("magnification", m)); // NON-NLS
    JsonArrayBuilder levels = Json.createArrayBuilder();
    for (PyramidLevel level : slide.levels()) {
      levels.add(
          Json.createObjectBuilder()
              .add("index", level.index()) // NON-NLS
              .add("downsample", level.downsampleX()) // NON-NLS
              .add("width", level.totalColumns()) // NON-NLS
              .add("height", level.totalRows()) // NON-NLS
              .add("tileWidth", level.tileWidth()) // NON-NLS
              .add("tileHeight", level.tileHeight()) // NON-NLS
              .add("tilesAcross", level.tilesAcross()) // NON-NLS
              .add("tilesDown", level.tilesDown()) // NON-NLS
              .add("sparse", level.isSparse())); // NON-NLS
    }
    json.add("levels", levels); // NON-NLS
    json.add("focalPlanes", slide.base().focalPlanes()); // NON-NLS
    JsonArrayBuilder paths = Json.createArrayBuilder();
    slide.base().opticalPaths().forEach(paths::add);
    json.add("opticalPaths", paths); // NON-NLS
    JsonArrayBuilder images = Json.createArrayBuilder();
    for (WsiImageType type :
        new WsiImageType[] {WsiImageType.THUMBNAIL, WsiImageType.OVERVIEW, WsiImageType.LABEL}) {
      slide
          .associatedImage(type)
          .ifPresent(
              image ->
                  images.add(
                      Json.createObjectBuilder()
                          .add("type", type.name().toLowerCase(java.util.Locale.ROOT)) // NON-NLS
                          .add("width", image.width()) // NON-NLS
                          .add("height", image.height()))); // NON-NLS
    }
    json.add("associatedImages", images); // NON-NLS
    return json.build();
  }

  /** What the pane shows, on the event dispatch thread. */
  static JsonObject display(SlideTarget resolved) {
    WsiView2d view = resolved.view();
    Slide slide = resolved.slide();
    TiledImageLayer layer = resolved.layer();
    double scale = view.getViewModel().getViewScale();
    Point2D centre = view.viewCentre();
    Rectangle2D viewport = new Rectangle2D.Double();
    viewport.setFrameFromDiagonal(
        view.viewToModel(0.0, 0.0),
        view.viewToModel((double) view.getWidth(), (double) view.getHeight()));
    viewport.add(view.viewToModel((double) view.getWidth(), 0.0));
    viewport.add(view.viewToModel(0.0, (double) view.getHeight()));
    TilePlan plan = layer.plan();
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("zoom", scale) // NON-NLS
            .add("downsample", 1.0 / scale) // NON-NLS
            .add(
                "center", // NON-NLS
                Json.createObjectBuilder().add("x", centre.getX()).add("y", centre.getY()))
            .add(
                "viewport", // NON-NLS
                Json.createObjectBuilder()
                    .add("x", viewport.getX())
                    .add("y", viewport.getY())
                    .add("width", viewport.getWidth()) // NON-NLS
                    .add("height", viewport.getHeight())); // NON-NLS
    slide.magnification().ifPresent(m -> json.add("magnification", m * scale)); // NON-NLS
    json.add("focalPlane", view.focalPlane() + 1) // NON-NLS
        .add("opticalPath", slide.base().opticalPaths().get(view.opticalPath())); // NON-NLS
    json.add("level", plan.level()) // NON-NLS
        .add(
            "tiles", // NON-NLS
            Json.createObjectBuilder()
                .add("loaded", layer.readyTiles()) // NON-NLS
                .add("planned", plan.painted().size()) // NON-NLS
                .add("partial", plan.truncated())); // NON-NLS
    return json.build();
  }
}
