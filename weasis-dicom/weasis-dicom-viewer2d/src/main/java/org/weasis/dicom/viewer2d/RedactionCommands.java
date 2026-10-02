/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MaskRegion;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.PixelMask;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.KarnakMasks;
import org.weasis.dicom.codec.PixelMaskMatcher;
import org.weasis.dicom.codec.PixelMaskMatcher.Candidate;
import org.weasis.dicom.codec.Redaction;
import org.weasis.dicom.codec.Redaction.Mask;
import org.weasis.dicom.codec.Redaction.Scope;
import org.weasis.dicom.codec.TagD;

/**
 * The {@code redact:} verbs: what a view hides over its pixels, and the device library it can come
 * from. Every verb prints a JSON body, so a script or an agent reads the result rather than the
 * screen. Writing the library is refused while the masking configuration is locked by the site.
 */
public class RedactionCommands {

  public static final List<String> functions =
      List.of(
          "list", // NON-NLS
          "add", // NON-NLS
          "clear", // NON-NLS
          "library"); // NON-NLS

  /** The regions burned over the pixels of the selected view. */
  public void list() {
    GuiExecutor.execute(
        () -> {
          ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
          if (view == null) {
            print(error("No selected view"));
            return;
          }
          Mask mask = Redaction.effective(view.getImage(), view.getSeries());
          JsonObjectBuilder body = Json.createObjectBuilder();
          JsonUtil.addIfPresent(body, "origin", mask == null ? null : mask.origin()); // NON-NLS
          body.add("count", mask == null ? 0 : mask.shapes().size()); // NON-NLS
          JsonArrayBuilder regions = Json.createArrayBuilder();
          if (mask != null) {
            mask.shapes().stream().map(RedactionCommands::bounds).forEach(regions::add);
          }
          body.add("regions", regions); // NON-NLS
          print(body.build());
        });
  }

  public void add(String[] argv) throws IOException {
    final String[] usage = {
      "Hide an area of the selected image", // NON-NLS
      "Usage: redact:add (--rect | --ellipse | --polygon) COORDINATES [--scope SCOPE]", // NON-NLS
      "  -r --rect=X,Y,W,H       rectangle in image pixels", // NON-NLS
      "  -e --ellipse=X,Y,W,H    ellipse in image pixels", // NON-NLS
      "  -p --polygon=X1,Y1,...  closed outline in image pixels", // NON-NLS
      "  -s --scope=SCOPE        image (default) or series", // NON-NLS
      "  -? --help               show help" // NON-NLS
    };
    Option opt = Options.compile(usage).parse(argv);
    if (opt.isSet("help") // NON-NLS
        || !opt.isOnlyOneOptionActivate("rect", "ellipse", "polygon")) { // NON-NLS
      opt.usage();
      return;
    }
    GuiExecutor.execute(
        () -> {
          ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
          if (view == null) {
            print(error("No selected view"));
            return;
          }
          Shape shape = shape(opt);
          if (shape == null) {
            print(error("Invalid coordinates"));
            return;
          }
          Scope scope = scope(opt);
          var target = scope == Scope.SERIES ? view.getSeries() : view.getImage();
          Redaction.materialize(view.getImage(), view.getSeries(), target);
          Redaction.add(target, shape);
          if (scope == Scope.SERIES) {
            Redaction.unsuppressFrames(view.getSeries());
          }
          refreshViews(view, scope);
          list();
        });
  }

  public void clear(String[] argv) throws IOException {
    final String[] usage = {
      "Remove the regions of the selected image or series", // NON-NLS
      "Usage: redact:clear [--scope SCOPE]", // NON-NLS
      "  -s --scope=SCOPE   image (default) or series", // NON-NLS
      "  -? --help          show help" // NON-NLS
    };
    Option opt = Options.compile(usage).parse(argv);
    if (opt.isSet("help")) { // NON-NLS
      opt.usage();
      return;
    }
    GuiExecutor.execute(
        () -> {
          ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
          if (view == null) {
            print(error("No selected view"));
            return;
          }
          Scope scope = scope(opt);
          Redaction.reset(view.getImage(), view.getSeries(), scope);
          refreshViews(view, scope);
          list();
        });
  }

  public void library(String[] argv) throws IOException {
    final String[] usage = {
      "Device entries of the masking document", // NON-NLS
      "Usage: redact:library (list | match | apply | save | delete | export | import)"
          + " [OPTIONS]", // NON-NLS
      "  -i --id=ID            entry of apply or delete, or the one save replaces", // NON-NLS
      "  -n --name=NAME        name of the entry to save from the selected view", // NON-NLS
      "  -s --scope=SCOPE      where apply hides them: image (default) or series", // NON-NLS
      "  -f --file=FILE        document to import", // NON-NLS
      "  -o --out=FILE         file to export to", // NON-NLS
      "  -p --profile=ID       masking profile of an export (default: the session one)", // NON-NLS
      "  -k --karnak           read or write the masks block of a Karnak profile", // NON-NLS
      "  -c --complete         export a whole Karnak profile, not only its masks block", // NON-NLS
      "  -? --help             show help" // NON-NLS
    };
    Option opt = Options.compile(usage).parse(argv);
    List<String> args = opt.args();
    if (opt.isSet("help") || args.isEmpty()) { // NON-NLS
      opt.usage();
      return;
    }
    switch (args.getFirst()) {
      case "list" -> print(libraryList()); // NON-NLS
      case "match" -> GuiExecutor.execute(() -> print(libraryMatch())); // NON-NLS
      case "apply" -> GuiExecutor.execute(() -> print(libraryApply(opt))); // NON-NLS
      case "save" -> GuiExecutor.execute(() -> print(librarySave(opt))); // NON-NLS
      case "delete" -> print(libraryDelete(opt)); // NON-NLS
      case "export" -> print(libraryExport(opt)); // NON-NLS
      case "import" -> print(libraryImport(opt)); // NON-NLS
      default -> opt.usage();
    }
  }

  private static JsonObject libraryList() {
    MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
    JsonArrayBuilder entries = Json.createArrayBuilder();
    for (PixelMask mask : registry.masks().entries()) {
      entries.add(describe(mask, registry));
    }
    return Json.createObjectBuilder()
        .add("locked", registry.isLocked()) // NON-NLS
        .add("masks", entries) // NON-NLS
        .build();
  }

  private static JsonObject libraryMatch() {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    if (view == null) {
      return error("No selected view");
    }
    MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
    String profileId = registry.sessionProfile().id();
    JsonArrayBuilder entries = Json.createArrayBuilder();
    for (Candidate candidate :
        PixelMaskMatcher.evaluate(view.getSeries(), view.getImage(), profileId)) {
      JsonObjectBuilder entry = describe(candidate.mask(), registry);
      entry.add("applies", candidate.applies()); // NON-NLS
      if (!candidate.applies()) {
        entry.add("reason", candidate.reason().name()); // NON-NLS
      }
      entries.add(entry);
    }
    return Json.createObjectBuilder()
        .add("profile", profileId) // NON-NLS
        .add("masks", entries) // NON-NLS
        .build();
  }

  /**
   * Hides the regions of a named entry on the selected view, all of them: a chosen entry is applied
   * as it was drawn, where the library proposes only what the profile in force does not keep.
   */
  private static JsonObject libraryApply(Option opt) {
    if (!opt.isSet("id")) { // NON-NLS
      return error("An id is required");
    }
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    if (view == null) {
      return error("No selected view");
    }
    MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
    Optional<PixelMask> entry = registry.pixelMask(opt.get("id")); // NON-NLS
    if (entry.isEmpty()) {
      return error("No device entry with this id");
    }
    Mask mask = Redaction.of(entry.get(), view.getImage());
    if (mask == null) {
      return error("This entry has no region for this image");
    }
    Scope scope = scope(opt);
    Redaction.apply(view.getImage(), view.getSeries(), scope, mask);
    refreshViews(view, scope);
    return describe(entry.get(), registry).build();
  }

  /**
   * The thumbnail is built outside the view pipeline and cached, so a series change has to say so.
   */
  private static void refreshViews(ViewCanvas<DicomImageElement> view, Scope scope) {
    RedactionToolBar.refreshOpenViews();
    if (scope == Scope.SERIES) {
      Redaction.refreshThumbnail(view.getSeries());
    }
  }

  private static JsonObject librarySave(Option opt) {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    if (view == null) {
      return error("No selected view");
    }
    Mask mask = Redaction.effective(view.getImage(), view.getSeries());
    if (mask == null || mask.isEmpty()) {
      return error("Nothing is hidden on this view");
    }
    Integer columns =
        TagD.getTagValue(view.getImage(), org.dcm4che3.data.Tag.Columns, Integer.class);
    Integer rows = TagD.getTagValue(view.getImage(), org.dcm4che3.data.Tag.Rows, Integer.class);
    String station =
        TagD.getTagValue(view.getSeries(), org.dcm4che3.data.Tag.StationName, String.class);
    String modality =
        TagD.getTagValue(view.getSeries(), org.dcm4che3.data.Tag.Modality, String.class);
    if (columns == null || rows == null || !StringUtil.hasText(station)) {
      return error("This series names no device or no image size");
    }
    String name = opt.isSet("name") ? opt.get("name") : station; // NON-NLS
    // Without an explicit id the entry is a new one: saving twice for the same device would
    // otherwise drop the regions of the first save without saying so
    String id = opt.isSet("id") ? opt.get("id") : DeviceMaskDialog.uniqueId(name); // NON-NLS
    List<MaskRegion> regions =
        mask.shapes().stream()
            .map(
                shape ->
                    MaskRegion.of(
                        shape, columns, rows, org.weasis.core.api.media.data.TagCategory.DIRECT_ID))
            .toList();
    PixelMask entry =
        new PixelMask(
            id,
            name,
            new PixelMask.DeviceKey(modality, station, null, null, null),
            new PixelMask.Reference(columns, rows),
            regions,
            List.of(),
            true);
    try {
      MaskingModelRegistry.getInstance().saveUserMask(entry);
      return describe(entry, MaskingModelRegistry.getInstance()).build();
    } catch (IOException | RuntimeException e) {
      return error(e.getMessage());
    }
  }

  private static JsonObject libraryDelete(Option opt) {
    if (!opt.isSet("id")) { // NON-NLS
      return error("An id is required");
    }
    try {
      boolean deleted = MaskingModelRegistry.getInstance().deleteUserMask(opt.get("id")); // NON-NLS
      return Json.createObjectBuilder().add("deleted", deleted).build(); // NON-NLS
    } catch (IOException | RuntimeException e) {
      return error(e.getMessage());
    }
  }

  private static JsonObject libraryExport(Option opt) {
    if (!opt.isSet("out")) { // NON-NLS
      return error("An output file is required");
    }
    MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
    Path file = Path.of(opt.get("out")); // NON-NLS
    try {
      if (opt.isSet("karnak")) { // NON-NLS
        MaskingProfile profile =
            opt.isSet("profile") // NON-NLS
                ? registry.profile(opt.get("profile")).orElse(registry.sessionProfile()) // NON-NLS
                : registry.sessionProfile();
        List<KarnakMasks.Loss> losses = new ArrayList<>();
        KarnakMasks.write(
            file,
            registry.masks().entries(),
            profile,
            KarnakMasks.BLACK,
            opt.isSet("complete"),
            losses);
        JsonArrayBuilder lost = Json.createArrayBuilder();
        losses.forEach(
            loss ->
                lost.add(
                    Json.createObjectBuilder()
                        .add("mask", loss.maskId() == null ? "" : loss.maskId()) // NON-NLS
                        .add("message", loss.message()))); // NON-NLS
        return Json.createObjectBuilder()
            .add("file", file.toString()) // NON-NLS
            .add("profile", profile.id()) // NON-NLS
            .add("losses", lost) // NON-NLS
            .build();
      }
      registry.userModel().write(file);
      return Json.createObjectBuilder().add("file", file.toString()).build(); // NON-NLS
    } catch (IOException | RuntimeException e) {
      return error(e.getMessage());
    }
  }

  private static JsonObject libraryImport(Option opt) {
    if (!opt.isSet("file")) { // NON-NLS
      return error("A file is required");
    }
    MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
    Path file = Path.of(opt.get("file")); // NON-NLS
    try {
      List<PixelMask> masks;
      if (opt.isSet("karnak")) { // NON-NLS
        masks = KarnakMasks.read(file, new PixelMask.Reference(1024, 768));
      } else {
        masks = MaskingModelRegistry.readImport(file).masks();
      }
      for (PixelMask mask : masks) {
        registry.saveUserMask(mask);
      }
      return Json.createObjectBuilder().add("imported", masks.size()).build(); // NON-NLS
    } catch (IOException | RuntimeException e) {
      return error(e.getMessage());
    }
  }

  private static JsonObjectBuilder describe(PixelMask mask, MaskingModelRegistry registry) {
    JsonObjectBuilder entry =
        Json.createObjectBuilder()
            .add("id", mask.id()) // NON-NLS
            .add("name", mask.name()) // NON-NLS
            .add("regions", mask.regions().size()) // NON-NLS
            .add("enabled", mask.enabled()) // NON-NLS
            .add("origin", registry.masks().origin(mask.id()).name()); // NON-NLS
    JsonUtil.addIfPresent(entry, "modality", mask.match().modality()); // NON-NLS
    JsonUtil.addIfPresent(entry, "stationName", mask.match().stationName()); // NON-NLS
    entry.add(
        "reference", // NON-NLS
        Json.createObjectBuilder()
            .add("columns", mask.reference().columns()) // NON-NLS
            .add("rows", mask.reference().rows())); // NON-NLS
    return entry;
  }

  private static Shape shape(Option opt) {
    double[] values = numbers(opt);
    if (values == null) {
      return null;
    }
    if (opt.isSet("polygon")) { // NON-NLS
      if (values.length < 6 || values.length % 2 != 0) {
        return null;
      }
      Path2D path = new Path2D.Double();
      path.moveTo(values[0], values[1]);
      for (int i = 2; i < values.length; i += 2) {
        path.lineTo(values[i], values[i + 1]);
      }
      path.closePath();
      return path;
    }
    if (values.length != 4) {
      return null;
    }
    return opt.isSet("ellipse") // NON-NLS
        ? new Ellipse2D.Double(values[0], values[1], values[2], values[3])
        : new Rectangle2D.Double(values[0], values[1], values[2], values[3]);
  }

  private static double[] numbers(Option opt) {
    String text =
        opt.isSet("rect") // NON-NLS
            ? opt.get("rect") // NON-NLS
            : opt.isSet("ellipse") ? opt.get("ellipse") : opt.get("polygon"); // NON-NLS
    try {
      return Arrays.stream(text.split(","))
          .mapToDouble(v -> Double.parseDouble(v.trim()))
          .toArray();
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Scope scope(Option opt) {
    return opt.isSet("scope") && "series".equalsIgnoreCase(opt.get("scope")) // NON-NLS
        ? Scope.SERIES
        : Scope.IMAGE;
  }

  private static JsonObject bounds(Shape shape) {
    Rectangle2D box = shape.getBounds2D();
    return Json.createObjectBuilder()
        .add("x", JsonUtil.decimal((float) box.getX())) // NON-NLS
        .add("y", JsonUtil.decimal((float) box.getY())) // NON-NLS
        .add("w", JsonUtil.decimal((float) box.getWidth())) // NON-NLS
        .add("h", JsonUtil.decimal((float) box.getHeight())) // NON-NLS
        .build();
  }

  private static JsonObject error(String message) {
    return Json.createObjectBuilder().add("error", message).build(); // NON-NLS
  }

  private static void print(JsonObject body) {
    System.out.println(body); // NOSONAR the command output is the console
  }
}
