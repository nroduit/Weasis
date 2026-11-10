/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.GraphicId;
import org.weasis.core.api.command.InputPath;
import org.weasis.core.api.command.LayerName;
import org.weasis.core.api.command.OutputPath;
import org.weasis.core.api.command.ResultSchema;
import org.weasis.core.api.command.Returns;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.command.Term;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.GraphicToolDescriptor;
import org.weasis.core.ui.model.graphic.GraphicsExporter;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfileRegistry;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.serialize.XmlSerializer;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Command namespace {@code measure}: the tools of the registry, the measurement profiles, the
 * export of the graphics of a pane and the manual calibration, the headless side of the measurement
 * tools. Drawing and reading the graphics of a pane stay the {@code view2d:} verbs.
 */
@Extension
@Command(
    name = "measure",
    description =
        "Measurement tools: the tool registry, the profiles, the export of the graphics of a pane"
            + " and the manual calibration.",
    subcommands = {
      MeasureCommands.Tools.class,
      MeasureCommands.Profiles.class,
      MeasureCommands.Profile.class,
      MeasureCommands.Export.class,
      MeasureCommands.Calibrate.class
    })
@Since("5.0")
public class MeasureCommands implements CommandExtension {

  static final String XML = "xml"; // NON-NLS
  static final String CSV = "csv"; // NON-NLS

  @Override
  public String getNamespace() {
    return "measure"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  @Override
  public List<Term> getTerms() {
    return List.of(
        Term.of("tool key", "the stable id of a measurement or drawing tool, from measure:tools"));
  }

  @Override
  public boolean isRelevantFor(String viewerTypeId) {
    return viewerTypeId == null || !"3d".equals(viewerTypeId); // NON-NLS
  }

  /** The formats the core and the plugins offer, by id. */
  static Map<String, GraphicsExporter> exporters() {
    Map<String, GraphicsExporter> formats = new TreeMap<>();
    formats.put(XML, new XmlExporter());
    formats.put(CSV, new CsvExporter());
    var manager = GuiUtils.getUICore().getPluginManager();
    if (manager != null) {
      for (GraphicsExporter exporter : manager.getExtensions(GraphicsExporter.class)) {
        formats.putIfAbsent(exporter.format().toLowerCase(Locale.ROOT), exporter);
      }
    }
    return formats;
  }

  @Command(
      name = "tools",
      description =
          "The measurement and drawing tools of the registry: key, category, label, the"
              + " measurements each one computes and the points it takes.")
  @Since("5.0")
  static class Tools extends AbstractVerb implements Returns {
    @Option(
        names = "--category",
        paramLabel = "<measure|draw>",
        description = "Only the tools of this category.")
    String category;

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("tools", "array", "the tools, in the order of the palette") // NON-NLS
          .field("tools[].tool", String.class, "the tool key") // NON-NLS
          .field("tools[].category", String.class, "measure or draw") // NON-NLS
          .field("tools[].label", String.class, "the label shown") // NON-NLS
          .field("tools[].points", int.class, "points the tool takes at least") // NON-NLS
          .field("tools[].measurements", "array", "the measurement keys the tool computes") // NON-NLS
          .field("tools[].measurements[].key", String.class, "stable name of the measurement") // NON-NLS
          .field("tools[].measurements[].name", String.class, "English name") // NON-NLS
          .build();
    }

    @Override
    protected void run() {
      JsonArrayBuilder tools = Json.createArrayBuilder();
      GraphicRegistry registry = GraphicRegistry.getInstance();
      Class<?> selection = registry.selectionGraphic().getClass();
      for (GraphicToolDescriptor d : registry.descriptors()) {
        String cat = d.category().name().toLowerCase(Locale.ROOT);
        if (category != null && !category.equalsIgnoreCase(cat)) {
          continue;
        }
        Graphic prototype = d.prototype().get();
        if (selection.isInstance(prototype)) {
          continue; // the selection rectangle draws nothing
        }
        JsonArrayBuilder measurements = Json.createArrayBuilder();
        for (Measurement m : prototype.getMeasurementList()) {
          measurements.add(
              Json.createObjectBuilder()
                  .add("key", m.getKey()) // NON-NLS
                  .add("name", m.getName())); // NON-NLS
        }
        tools.add(
            Json.createObjectBuilder()
                .add("tool", d.key()) // NON-NLS
                .add("category", cat) // NON-NLS
                .add("label", prototype.getUIName() == null ? d.key() : prototype.getUIName()) // NON-NLS
                .add("points", d.minPoints()) // NON-NLS
                .add("measurements", measurements)); // NON-NLS
      }
      print(Json.createObjectBuilder().add("tools", tools).build().toString()); // NON-NLS
    }
  }

  @Command(
      name = "profiles",
      description = "The measurement profiles: built-in, site and user, and the one in use.")
  @Since("5.0")
  static class Profiles extends AbstractVerb implements Returns {
    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("selected", String.class, "the profile chosen by hand, absent when automatic") // NON-NLS
          .field("automatic", boolean.class, "true when the modality chooses the profile") // NON-NLS
          .field("profiles", "array", "the profiles") // NON-NLS
          .field("profiles[].id", String.class, "the profile id") // NON-NLS
          .field("profiles[].name", String.class, "the name shown") // NON-NLS
          .field("profiles[].modalities", "array", "the modalities it binds to") // NON-NLS
          .field("profiles[].tools", "array", "the tool keys of its palette") // NON-NLS
          .field("profiles[].origin", String.class, "built-in, site or user") // NON-NLS
          .field("profiles[].locked", boolean.class, "true when the site locks it") // NON-NLS
          .build();
    }

    @Override
    protected void run() {
      MeasurementProfileRegistry registry = MeasurementProfileRegistry.getInstance();
      JsonArrayBuilder profiles = Json.createArrayBuilder();
      for (MeasurementProfile p : registry.profiles()) {
        JsonArrayBuilder modalities = Json.createArrayBuilder();
        if (p.modalities() != null) {
          p.modalities().forEach(modalities::add);
        }
        JsonArrayBuilder tools = Json.createArrayBuilder();
        if (p.tools() != null) {
          p.tools().forEach(tools::add);
        }
        profiles.add(
            Json.createObjectBuilder()
                .add("id", p.id()) // NON-NLS
                .add("name", p.name()) // NON-NLS
                .add("modalities", modalities) // NON-NLS
                .add("tools", tools) // NON-NLS
                .add("origin", registry.origin(p.id()).name().toLowerCase(Locale.ROOT)) // NON-NLS
                .add("locked", registry.isLocked(p.id()))); // NON-NLS
      }
      JsonObjectBuilder json = Json.createObjectBuilder();
      if (registry.selectedId() != null) {
        json.add("selected", registry.selectedId()); // NON-NLS
      }
      print(
          json.add("automatic", registry.isAutomatic()) // NON-NLS
              .add("profiles", profiles) // NON-NLS
              .build()
              .toString());
    }
  }

  /** Exactly one action on the profiles. */
  static class ProfileAction {
    @Option(
        names = "--select",
        paramLabel = "<id>",
        description = "Use this profile whatever the modality.")
    String select;

    @Option(names = "--automatic", description = "Let the modality choose the profile again.")
    boolean automatic;

    @Option(
        names = "--export",
        paramLabel = "<output-path>",
        description = "Write the user profiles (or --id) as a JSON file.")
    OutputPath export;

    @Option(
        names = "--import",
        paramLabel = "<input-path>",
        description = "Read profiles from a JSON file into the user layer.")
    InputPath importFile;
  }

  @Command(
      name = "profile",
      description =
          "Chooses the measurement profile in use, or exports and imports profiles as JSON.")
  @Since("5.0")
  static class Profile extends AbstractVerb implements Returns {
    @ArgGroup(exclusive = true, multiplicity = "1")
    ProfileAction action;

    @Option(
        names = "--id",
        paramLabel = "<id>",
        description = "With --export: the profiles to write, by id (default: the user profiles).")
    List<String> ids;

    @Option(names = CommandModel.FORCE, description = "Replace an existing file.")
    boolean force;

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("selected", String.class, "the profile chosen by hand, absent when automatic") // NON-NLS
          .field("automatic", boolean.class, "true when the modality chooses the profile") // NON-NLS
          .field("file", String.class, "the file written or read") // NON-NLS
          .field("profiles", "array", "the ids written or read") // NON-NLS
          .build();
    }

    @Override
    protected void run() throws IOException {
      MeasurementProfileRegistry registry = MeasurementProfileRegistry.getInstance();
      JsonObjectBuilder json = Json.createObjectBuilder();
      if (action.select != null) {
        if (registry.profile(action.select).isEmpty()) {
          throw CommandFailure.unknownHandle(
              "Unknown profile: " + action.select, "measure:profiles"); // NON-NLS
        }
        GuiExecutor.invokeAndRethrow(() -> registry.select(action.select));
      } else if (action.automatic) {
        GuiExecutor.invokeAndRethrow(() -> registry.select(null));
      } else if (action.export != null) {
        Path file = action.export.value();
        if (Files.exists(file) && !force && !CommandContext.wasWritten(file)) {
          throw invalid("The file exists: " + file + " (--force replaces it)");
        }
        List<MeasurementProfile> profiles =
            ids == null || ids.isEmpty()
                ? registry.userProfiles()
                : ids.stream()
                    .map(
                        id ->
                            registry
                                .profile(id)
                                .orElseThrow(
                                    () ->
                                        CommandFailure.unknownHandle(
                                            "Unknown profile: " + id, "measure:profiles"))) // NON-NLS
                    .toList();
        registry.exportTo(file, profiles);
        CommandContext.current().addFile(file);
        json.add("file", file.toAbsolutePath().toString()); // NON-NLS
        JsonArrayBuilder written = Json.createArrayBuilder();
        profiles.forEach(p -> written.add(p.id()));
        json.add("profiles", written); // NON-NLS
      } else if (action.importFile != null) {
        List<MeasurementProfile> read = registry.importFrom(action.importFile.value());
        json.add("file", action.importFile.value().toAbsolutePath().toString()); // NON-NLS
        JsonArrayBuilder imported = Json.createArrayBuilder();
        read.forEach(p -> imported.add(p.id()));
        json.add("profiles", imported); // NON-NLS
      }
      if (registry.selectedId() != null) {
        json.add("selected", registry.selectedId()); // NON-NLS
      }
      print(json.add("automatic", registry.isAutomatic()).build().toString()); // NON-NLS
    }
  }

  @Command(
      name = "export",
      description =
          "Writes the graphics of a pane to a file: xml (the presentation model of Weasis), csv (the"
              + " measured values, one row per value) or a format a plugin adds, sr and pr with the"
              + " DICOM plugins ('measure:export --format ?' lists them).")
  @Since("5.0")
  static class Export extends AbstractVerb implements Returns {
    @Mixin PaneOptions target;

    @Option(
        names = "--format",
        paramLabel = "<format>",
        required = true,
        description = "xml, csv, or a format of a plugin; '?' lists the formats of this instance.")
    String format;

    @Option(
        names = "--out",
        paramLabel = "<output-path>",
        description = "The file to write.")
    OutputPath out;

    @Option(
        names = "--layer",
        paramLabel = "<layer-name>",
        description = "Only the graphics of this layer (default: the measurement and drawing layers).")
    LayerName layer;

    @Option(names = CommandModel.FORCE, description = "Replace an existing file.")
    boolean force;

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("formats", "array", "with --format ?: the formats of this instance") // NON-NLS
          .field("formats[].format", String.class, "the format id") // NON-NLS
          .field("formats[].description", String.class, "what it writes") // NON-NLS
          .field("format", String.class, "the format written") // NON-NLS
          .field("file", String.class, "the file written") // NON-NLS
          .field("graphics", int.class, "graphics written") // NON-NLS
          .field("skipped", int.class, "graphics the format could not take") // NON-NLS
          .field("details", "object", "what the format says about the file, e.g. its SOP Instance UID") // NON-NLS
          .build();
    }

    @Override
    protected void run() throws IOException {
      Map<String, GraphicsExporter> exporters = exporters();
      if ("?".equals(format)) {
        JsonArrayBuilder formats = Json.createArrayBuilder();
        exporters.forEach(
            (id, e) ->
                formats.add(
                    Json.createObjectBuilder()
                        .add("format", id) // NON-NLS
                        .add("description", e.description()))); // NON-NLS
        print(Json.createObjectBuilder().add("formats", formats).build().toString()); // NON-NLS
        return;
      }
      GraphicsExporter exporter = exporters.get(format.toLowerCase(Locale.ROOT));
      if (exporter == null) {
        throw invalid(
            "Unknown format '" + format + "'; this instance writes " + String.join(", ", exporters.keySet()));
      }
      if (out == null) {
        throw invalid("Give --out, the file to write");
      }
      Path file = out.value();
      if (Files.exists(file) && !force && !CommandContext.wasWritten(file)) {
        throw invalid("The file exists: " + file + " (--force replaces it)");
      }
      GraphicsExporter.Written written =
          GuiExecutor.invokeAndRethrow(
              () -> {
                ViewCanvas<?> view =
                    target.resolve().orElseThrow(() -> invalid("No view is selected: give --view"));
                List<Graphic> graphics = graphicsOf(view, layer);
                if (graphics.isEmpty()) {
                  throw new IllegalStateException("The pane has no graphic to export");
                }
                try {
                  return exporter.export(view, graphics, file);
                } catch (IOException e) {
                  throw new IllegalStateException("Cannot write " + file + ": " + e.getMessage(), e);
                }
              });
      CommandContext.current().addFile(written.file());
      JsonObjectBuilder details = Json.createObjectBuilder();
      written.details().forEach(details::add);
      print(
          Json.createObjectBuilder()
              .add("format", exporter.format()) // NON-NLS
              .add("file", written.file().toAbsolutePath().toString()) // NON-NLS
              .add("graphics", written.graphics()) // NON-NLS
              .add("skipped", written.skipped()) // NON-NLS
              .add("details", details) // NON-NLS
              .build()
              .toString());
    }
  }

  /** The graphics of the measurement and drawing layers of a pane, or of one layer by name. */
  static List<Graphic> graphicsOf(ViewCanvas<?> view, LayerName layer) {
    List<Graphic> graphics = new ArrayList<>();
    for (Graphic graphic : view.getGraphicManager().getAllGraphics()) {
      if (graphic.getLayer() == null) {
        continue;
      }
      if (layer != null) {
        if (layer.value().equals(graphic.getLayer().getName())) {
          graphics.add(graphic);
        }
      } else if (graphic.getLayer().getType() == LayerType.MEASURE
          || graphic.getLayer().getType() == LayerType.DRAW) {
        graphics.add(graphic);
      }
    }
    return graphics;
  }

  /** The spatial unit of the image of a pane: its calibration, else pixels. */
  static Unit spatialUnit(ViewCanvas<?> view) {
    ImageElement image = view.getImage();
    Unit unit = image == null ? null : image.getPixelSpacingUnit();
    return unit == null ? Unit.PIXEL : unit;
  }

  /** The presentation model of Weasis, every graphic of the image. */
  static final class XmlExporter implements GraphicsExporter {
    @Override
    public String format() {
      return XML;
    }

    @Override
    public String description() {
      return "The presentation model of Weasis, read back by the open of a study";
    }

    @Override
    public Written export(ViewCanvas<?> view, List<Graphic> graphics, Path file)
        throws IOException {
      try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
        XmlSerializer.writePresentation(view.getGraphicManager(), writer);
      } catch (jakarta.xml.bind.JAXBException e) {
        throw new IOException(e.getMessage(), e);
      }
      return new Written(file, view.getGraphicManager().getAllGraphics().size(), 0, Map.of());
    }
  }

  /** One row per measured value: graphic, tool, layer, key, name, value, unit. */
  static final class CsvExporter implements GraphicsExporter {
    @Override
    public String format() {
      return CSV;
    }

    @Override
    public String description() {
      return "The measured values, one row per value, with the graphic id, tool, layer and unit";
    }

    @Override
    public Written export(ViewCanvas<?> view, List<Graphic> graphics, Path file)
        throws IOException {
      Unit spatial = spatialUnit(view);
      var measurable = view.getMeasurableLayer();
      StringBuilder sb = new StringBuilder("graphic,tool,layer,key,name,value,unit\n"); // NON-NLS
      int skipped = 0;
      for (Graphic graphic : graphics) {
        List<MeasureItem> items =
            Measurement.computingAll(() -> graphic.computeMeasurements(measurable, true, spatial));
        if (items.isEmpty()) {
          skipped++;
          continue;
        }
        String tool =
            GraphicRegistry.getInstance().keyOf(graphic).orElse(graphic.getClass().getSimpleName());
        for (MeasureItem item : items) {
          if (!(item.getValue() instanceof Number n)) {
            continue;
          }
          sb.append(graphic.getUuid())
              .append(',')
              .append(cell(tool))
              .append(',')
              .append(cell(graphic.getLayer().getName()))
              .append(',')
              .append(cell(item.getMeasurement().getKey()))
              .append(',')
              .append(cell(item.getMeasurement().getName()))
              .append(',')
              .append(Double.toString(n.doubleValue()))
              .append(',')
              .append(cell(item.getUnit() == null ? "" : item.getUnit()))
              .append('\n');
        }
      }
      Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
      return new Written(file, graphics.size() - skipped, skipped, Map.of());
    }

    private static String cell(String text) {
      if (text == null) {
        return "";
      }
      return text.contains(",") || text.contains("\"") || text.contains("\n")
          ? "\"" + text.replace("\"", "\"\"") + "\""
          : text;
    }
  }

  @Command(
      name = "calibrate",
      description =
          "Calibrates the image of a pane from a line of known length: the pixel size follows the"
              + " line, the series with --series; --remove drops the user's calibration.")
  @Since("5.0")
  static class Calibrate extends AbstractVerb implements Returns {
    @Mixin PaneOptions target;

    @Option(
        names = "--graphic",
        paramLabel = "<graphic-id>",
        description = "A line graphic of the pane, from view2d:measure or view2d:graphics.")
    GraphicId graphic;

    @Option(
        names = "--length",
        paramLabel = "<n>",
        description = "The real length of the line, in --unit.")
    Double length;

    @Option(
        names = "--unit",
        paramLabel = "<unit>",
        description = "mm, cm, inch, micrometer… (default: mm).")
    String unit = "mm"; // NON-NLS

    @Option(names = "--series", description = "Calibrate every image of the series.")
    boolean series;

    @Option(names = "--remove", description = "Drop the user's calibration instead.")
    boolean remove;

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("pixelSize", double.class, "the size of one pixel after the call") // NON-NLS
          .field("unit", String.class, "the unit of pixelSize") // NON-NLS
          .field("series", boolean.class, "true when the series was calibrated") // NON-NLS
          .field("removed", boolean.class, "true when the user's calibration was dropped") // NON-NLS
          .build();
    }

    @Override
    protected void run() {
      if (!remove && (graphic == null || length == null)) {
        throw invalid("Give --graphic and --length, or --remove");
      }
      if (length != null && !(length > 0)) {
        throw invalid("--length is a positive number");
      }
      Unit spatial = remove ? null : unitOf(unit);
      String body =
          GuiExecutor.invokeAndRethrow(
              () -> {
                ViewCanvas<?> view =
                    target.resolve().orElseThrow(() -> invalid("No view is selected: give --view"));
                ImageElement image = view.getImage();
                if (image == null) {
                  throw new IllegalStateException("The pane shows no image");
                }
                if (remove) {
                  Calibration.remove(view, series);
                } else {
                  LineGraphic line =
                      view.getGraphicManager().getAllGraphics().stream()
                          .filter(g -> g.getUuid().equals(graphic.value()))
                          .filter(LineGraphic.class::isInstance)
                          .map(LineGraphic.class::cast)
                          .findFirst()
                          .orElseThrow(
                              () ->
                                  CommandFailure.unknownHandle(
                                      "No line graphic " + graphic + " in the pane", // NON-NLS
                                      "view2d:graphics")); // NON-NLS
                  Calibration.apply(view, Calibration.ratio(line, length), spatial, series);
                }
                return Json.createObjectBuilder()
                    .add("pixelSize", image.getPixelSize()) // NON-NLS
                    .add("unit", String.valueOf(image.getPixelSpacingUnit().getAbbreviation())) // NON-NLS
                    .add("series", series) // NON-NLS
                    .add("removed", remove) // NON-NLS
                    .build()
                    .toString();
              });
      print(body);
    }

    private static Unit unitOf(String text) {
      for (Unit u : Unit.values()) {
        if (u != Unit.PIXEL
            && (u.getAbbreviation().equalsIgnoreCase(text)
                || u.name().equalsIgnoreCase(text)
                || u.getFullName().equalsIgnoreCase(text))) {
          return u;
        }
      }
      throw new IllegalArgumentException(
          "Unknown unit '" + text + "': mm, cm, m, inch, micrometer…"); // NON-NLS
    }
  }
}
