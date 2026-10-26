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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.Capability;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.ImagePolygon;
import org.weasis.core.api.command.ImageRect;
import org.weasis.core.api.command.InputPath;
import org.weasis.core.api.command.Needs;
import org.weasis.core.api.command.OutputPath;
import org.weasis.core.api.command.SemanticType;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MaskRegion;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.PixelMask;
import org.weasis.core.api.media.data.TagCategory;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ViewDisplayState;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.KarnakMasks;
import org.weasis.dicom.codec.PixelAttestation;
import org.weasis.dicom.codec.PixelMaskMatcher;
import org.weasis.dicom.codec.PixelMaskMatcher.Candidate;
import org.weasis.dicom.codec.PixelPrivacy;
import org.weasis.dicom.codec.Redaction;
import org.weasis.dicom.codec.Redaction.Mask;
import org.weasis.dicom.codec.Redaction.Scope;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.View2dCommands.Target;
import org.weasis.dicom.viewer2d.View2dCommands.ViewOptions;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Command namespace {@code redact}: what a view hides over its pixels, and the device library it
 * can come from. Every verb prints a JSON body, so a script or an agent reads the result rather
 * than the screen. Writing the library is refused while the masking configuration is locked by the
 * site.
 */
@Extension
@Command(
    name = "redact",
    description =
        "Regions burned over the pixels of a view, and the device library they can come from.",
    subcommands = {
      RedactionCommands.ListRegions.class,
      RedactionCommands.Add.class,
      RedactionCommands.Clear.class,
      RedactionCommands.Attest.class,
      RedactionCommands.Library.class,
      RedactionCommands.Match.class,
      RedactionCommands.Apply.class,
      RedactionCommands.Save.class,
      RedactionCommands.Delete.class,
      RedactionCommands.Export.class,
      RedactionCommands.Import.class
    })
public class RedactionCommands implements CommandExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(RedactionCommands.class);

  /** Identifies one entry of the device library of masks. */
  public record EntryId(String value) implements SemanticType {

    private static final String EXPECTED = "an entry id from 'redact:library' or 'redact:match'";

    public EntryId {
      value = SemanticType.handle(value, EXPECTED);
    }

    public static EntryId parse(String text) {
      return new EntryId(text);
    }

    @Override
    public String toString() {
      return value;
    }
  }

  /** Identifies one masking profile of the configuration. */
  public record MaskingProfileId(String value) implements SemanticType {

    private static final String EXPECTED = "a masking profile id from the configuration";

    public MaskingProfileId {
      value = SemanticType.handle(value, EXPECTED);
    }

    public static MaskingProfileId parse(String text) {
      return new MaskingProfileId(text);
    }

    @Override
    public String toString() {
      return value;
    }
  }

  @Override
  public String getNamespace() {
    return "redact"; // NON-NLS
  }

  @Override
  public boolean isRelevantFor(String viewerTypeId) {
    return viewerTypeId != null && !"3d".equals(viewerTypeId); // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  @Command(name = "list", description = "The regions hidden on the selected view.")
  @Since("5.0")
  static class ListRegions extends AbstractVerb {
    @Mixin ViewOptions target;

    @Override
    protected void run() {
      print(GuiExecutor.invokeAndRethrow(() -> regions(redactable(target.withImage()))));
    }
  }

  /** Exclusive shape of a region, in image pixels. */
  static class Region {
    @Option(
        names = "--rect",
        paramLabel = "<image-rect>",
        description = "Axis-aligned rectangle, in image pixels.")
    ImageRect rect;

    @Option(
        names = "--ellipse",
        paramLabel = "<image-rect>",
        description = "Ellipse inscribed in this rectangle.")
    ImageRect ellipse;

    @Option(
        names = "--polygon",
        paramLabel = "<image-polygon>",
        description = "Closed outline as x,y pairs (three at least).")
    ImagePolygon polygon;

    Shape toShape() {
      if (rect != null) {
        return new Rectangle2D.Double(rect.x(), rect.y(), rect.width(), rect.height());
      }
      if (ellipse != null) {
        return new Ellipse2D.Double(ellipse.x(), ellipse.y(), ellipse.width(), ellipse.height());
      }
      List<Point2D> values = polygon.points();
      if (values.size() < 3) {
        throw new IllegalArgumentException("--polygon needs at least three x,y pairs");
      }
      Path2D path = new Path2D.Double();
      path.moveTo(values.getFirst().getX(), values.getFirst().getY());
      for (Point2D point : values.subList(1, values.size())) {
        path.lineTo(point.getX(), point.getY());
      }
      path.closePath();
      return path;
    }
  }

  @Command(name = "add", description = "Hides an area of the selected view.")
  @Since("5.0")
  static class Add extends AbstractVerb {
    @Mixin ViewOptions target;

    @ArgGroup(exclusive = true, multiplicity = "1")
    Region region;

    @Mixin ScopeOption scope;

    @Override
    protected void run() {
      Shape shape;
      try {
        shape = region.toShape();
      } catch (IllegalArgumentException e) {
        throw invalid(e.getMessage());
      }
      print(
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = redactable(target.withImage());
                DefaultView2d<DicomImageElement> view = resolved.view();
                Scope value = scope.scope;
                Taggable owner = value == Scope.SERIES ? view.getSeries() : view.getImage();
                Redaction.materialize(view.getImage(), view.getSeries(), owner);
                Redaction.add(owner, shape);
                if (value == Scope.SERIES) {
                  Redaction.unsuppressFrames(view.getSeries());
                }
                refreshViews(view);
                return regions(resolved);
              }));
    }
  }

  @Command(name = "clear", description = "Removes the regions of the selected image or series.")
  @Since("5.0")
  static class Clear extends AbstractVerb {
    @Mixin ViewOptions target;

    @Mixin ScopeOption scope;

    @Override
    protected void run() {
      print(
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = redactable(target.withImage());
                DefaultView2d<DicomImageElement> view = resolved.view();
                Redaction.reset(view.getImage(), view.getSeries(), scope.scope);
                refreshViews(view);
                return regions(resolved);
              }));
    }
  }

  @Command(
      name = "attest",
      description =
          "Records that a person reviewed the pixels of the selected image or series and found no"
              + " burned-in identity, so it may be captured or exported without a redaction;"
              + " --withdraw takes it back. Held until the study is unloaded.")
  @Since("5.0")
  static class Attest extends AbstractVerb {
    @Mixin ViewOptions target;

    @Mixin ScopeOption scope;

    @Option(
        names = "--by",
        paramLabel = "<name>",
        description = "Who reviewed the pixels; required unless --withdraw.")
    @Needs(Capability.PIXEL_ATTEST)
    String by;

    @Option(
        names = "--reason",
        paramLabel = "<text>",
        description = "What the review was, such as the catalog and the date it was recorded.")
    String reason;

    @Option(names = "--withdraw", description = "Takes back the attestation of that scope.")
    boolean withdraw;

    @Override
    protected void run() {
      if (withdraw == StringUtil.hasText(by)) {
        throw invalid("give --by <name> to attest, or --withdraw alone to take it back");
      }
      print(
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = redactable(target.withImage());
                DefaultView2d<DicomImageElement> view = resolved.view();
                Taggable owner = scope.scope == Scope.SERIES ? view.getSeries() : view.getImage();
                if (withdraw) {
                  PixelAttestation.clear(owner);
                } else {
                  if (PixelPrivacy.of(view.getImage()) == PixelPrivacy.BURNED_IN) {
                    throw new IllegalStateException(
                        "The image declares burned-in identification (Burned In Annotation or"
                            + " Recognizable Visual Features = YES); only a redaction can hide it");
                  }
                  PixelAttestation attestation = PixelAttestation.of(by, reason);
                  PixelAttestation.write(owner, attestation);
                  LOGGER.info(
                      "Pixels of the {} {} attested free of burned-in identity by {} ({}), origin {}",
                      scope.scope,
                      TagD.getTagValue(
                          owner,
                          scope.scope == Scope.SERIES ? Tag.SeriesInstanceUID : Tag.SOPInstanceUID,
                          String.class),
                      attestation.by(),
                      attestation.reason(),
                      CommandContext.current().origin());
                }
                return regions(resolved);
              }));
    }
  }

  @Command(name = "library", description = "The device entries of the masking document.")
  @Since("5.0")
  static class Library extends AbstractVerb {
    @Override
    protected void run() {
      MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
      JsonArrayBuilder entries = Json.createArrayBuilder();
      for (PixelMask mask : registry.masks().entries()) {
        entries.add(describe(mask, registry));
      }
      print(
          Json.createObjectBuilder()
              .add("locked", registry.isLocked()) // NON-NLS
              .add("masks", entries) // NON-NLS
              .build()
              .toString());
    }
  }

  @Command(
      name = "match",
      description = "The device entries that fit the selected view, and why the others do not.")
  @Since("5.0")
  static class Match extends AbstractVerb {
    @Mixin ViewOptions target;

    @Override
    protected void run() {
      print(
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = redactable(target.withImage());
                DefaultView2d<DicomImageElement> view = resolved.view();
                MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
                String profileId = registry.sessionProfile().id();
                JsonArrayBuilder entries = Json.createArrayBuilder();
                for (Candidate candidate :
                    PixelMaskMatcher.evaluate(view.getSeries(), view.getImage(), profileId)) {
                  JsonObjectBuilder entry = describe(candidate.mask(), registry);
                  entry.add("applies", candidate.applies()); // NON-NLS
                  if (!candidate.applies()) {
                    entry.add("reason", SemanticType.valueOf(candidate.reason())); // NON-NLS
                  }
                  entries.add(entry);
                }
                return Json.createObjectBuilder()
                    .add("target", ViewDisplayState.target(resolved.container(), view)) // NON-NLS
                    .add("profile", profileId) // NON-NLS
                    .add("masks", entries) // NON-NLS
                    .build()
                    .toString();
              }));
    }
  }

  @Command(
      name = "apply",
      description =
          "Hides the regions of a device entry on the selected view, all of them: a chosen entry "
              + "is applied as it was drawn, where 'match' proposes only what the profile in force "
              + "does not keep.")
  @Since("5.0")
  static class Apply extends AbstractVerb {
    @Mixin ViewOptions target;

    @Option(
        names = "--entry",
        required = true,
        paramLabel = "<entry-id>",
        description = "Device entry from 'redact:library'.")
    EntryId id;

    @Mixin ScopeOption scope;

    @Override
    protected void run() {
      print(
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = redactable(target.withImage());
                DefaultView2d<DicomImageElement> view = resolved.view();
                MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
                PixelMask entry =
                    registry
                        .pixelMask(id.value())
                        .orElseThrow(
                            () ->
                                CommandFailure.unknownHandle(
                                    "No device entry with the id '" + id + "'",
                                    "redact:library")); // NON-NLS
                Mask mask = Redaction.of(entry, view.getImage());
                if (mask == null) {
                  throw new IllegalStateException("This entry has no region for this image");
                }
                Redaction.apply(view.getImage(), view.getSeries(), scope.scope, mask);
                refreshViews(view);
                return regions(resolved);
              }));
    }
  }

  @Command(
      name = "save",
      description = "Saves the regions of the selected view as a device entry of the library.")
  @Since("5.0")
  static class Save extends AbstractVerb {
    @Mixin ViewOptions target;

    @Option(
        names = "--name",
        paramLabel = "<name>",
        description = "Name of the entry (default: the station name of the series).")
    String name;

    @Option(
        names = "--entry",
        paramLabel = "<entry-id>",
        description = "Entry to replace (default: a new one).")
    EntryId id;

    @Override
    protected void run() throws IOException {
      PixelMask entry = GuiExecutor.invokeAndRethrow(this::build);
      MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
      registry.saveUserMask(entry);
      print(describe(entry, registry).build().toString());
    }

    private PixelMask build() {
      DefaultView2d<DicomImageElement> view = target.view();
      Mask mask = Redaction.effective(view.getImage(), view.getSeries());
      if (mask == null || mask.isEmpty()) {
        throw new IllegalStateException("Nothing is hidden on this view");
      }
      Integer columns = TagD.getTagValue(view.getImage(), Tag.Columns, Integer.class);
      Integer rows = TagD.getTagValue(view.getImage(), Tag.Rows, Integer.class);
      String station = TagD.getTagValue(view.getSeries(), Tag.StationName, String.class);
      String modality = TagD.getTagValue(view.getSeries(), Tag.Modality, String.class);
      if (columns == null || rows == null || !StringUtil.hasText(station)) {
        throw new IllegalStateException("This series names no device or no image size");
      }
      String entryName = StringUtil.hasText(name) ? name : station;
      // Without an explicit id the entry is a new one: saving twice for the same device would
      // otherwise drop the regions of the first save without saying so
      String entryId = id == null ? DeviceMaskDialog.uniqueId(entryName) : id.value();
      List<MaskRegion> regions =
          mask.shapes().stream()
              .map(shape -> MaskRegion.of(shape, columns, rows, TagCategory.DIRECT_ID))
              .toList();
      return new PixelMask(
          entryId,
          entryName,
          new PixelMask.DeviceKey(modality, station, null, null, null),
          new PixelMask.Reference(columns, rows),
          regions,
          List.of(),
          true);
    }
  }

  @Command(name = "delete", description = "Removes a device entry from the user library.")
  @Since("5.0")
  static class Delete extends AbstractVerb {
    @Option(
        names = "--entry",
        required = true,
        paramLabel = "<entry-id>",
        description = "Device entry from 'redact:library'.")
    EntryId id;

    @Override
    protected void run() throws IOException {
      boolean deleted = MaskingModelRegistry.getInstance().deleteUserMask(id.value());
      print(Json.createObjectBuilder().add("deleted", deleted).build().toString()); // NON-NLS
    }
  }

  @Command(name = "export", description = "Writes the device library to a file.")
  @Since("5.0")
  static class Export extends AbstractVerb {
    @Option(
        names = "--out",
        required = true,
        paramLabel = "<output-path>",
        description = "File to write.")
    OutputPath file;

    @Option(
        names = "--force",
        description = "Replace the file when it exists and was not written by this session.")
    @Since("5.0")
    boolean force;

    @Option(names = "--karnak", description = "Write the masks block of a Karnak profile.")
    boolean karnak;

    @Option(
        names = "--complete",
        description = "Write a whole Karnak profile, not only its masks block.")
    boolean complete;

    @Option(
        names = "--masking-profile",
        paramLabel = "<masking-profile-id>",
        description = "Masking profile of a Karnak export (default: the session one).")
    @Since("5.0")
    MaskingProfileId profile;

    @Override
    protected void run() throws IOException {
      MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
      if (!karnak) {
        registry.userModel().write(file.value());
        print(written(Json.createObjectBuilder()).build().toString());
        return;
      }
      MaskingProfile maskingProfile = requireProfile(registry);
      List<KarnakMasks.Loss> losses = new ArrayList<>();
      KarnakMasks.write(
          file.value(),
          registry.masks().entries(),
          maskingProfile,
          KarnakMasks.BLACK,
          complete,
          losses);
      JsonArrayBuilder lost = Json.createArrayBuilder();
      losses.forEach(
          loss ->
              lost.add(
                  Json.createObjectBuilder()
                      .add("mask", loss.maskId() == null ? "" : loss.maskId()) // NON-NLS
                      .add("message", loss.message()))); // NON-NLS
      print(
          written(Json.createObjectBuilder())
              .add("maskingProfile", maskingProfile.id()) // NON-NLS
              .add("losses", lost) // NON-NLS
              .build()
              .toString());
    }

    /**
     * The profile asked for, or the one of the session; an unknown id fails, it never falls back.
     */
    private MaskingProfile requireProfile(MaskingModelRegistry registry) {
      if (profile == null) {
        return registry.sessionProfile();
      }
      return registry
          .profile(profile.value())
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "No masking profile with the id '"
                          + profile
                          + "'. Profiles: "
                          + registry.profiles().entries().stream()
                              .map(MaskingProfile::id)
                              .toList()));
    }

    /** Names the file that was written, as every verb that writes one does, and registers it. */
    private JsonObjectBuilder written(JsonObjectBuilder json) {
      CommandContext.current().addFile(file.value());
      return json.add("file", file.toAbsolutePath().toString()); // NON-NLS
    }
  }

  @Command(name = "import", description = "Adds the device entries of a file to the user library.")
  @Since("5.0")
  static class Import extends AbstractVerb {
    @Option(
        names = "--file",
        required = true,
        paramLabel = "<input-path>",
        description = "Document to read.")
    InputPath file;

    @Option(names = "--karnak", description = "Read the masks block of a Karnak profile.")
    boolean karnak;

    @Override
    protected void run() throws IOException {
      MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
      List<PixelMask> masks =
          karnak
              ? KarnakMasks.read(file.value(), new PixelMask.Reference(1024, 768))
              : MaskingModelRegistry.readImport(file.value()).masks();
      for (PixelMask mask : masks) {
        registry.saveUserMask(mask);
      }
      print(Json.createObjectBuilder().add("imported", masks.size()).build().toString()); // NON-NLS
    }
  }

  /** Where a change applies: the displayed image or the whole series. */
  static class ScopeOption {
    @Option(
        names = "--scope",
        paramLabel = "<scope>",
        description = "Where it applies: ${COMPLETION-CANDIDATES}.")
    Scope scope = Scope.IMAGE;
  }

  /**
   * The resolved pane when its view burns redaction regions. A reconstruction (MPR) does not: its
   * planes are not those the regions were drawn on, so nothing is shown or stored there.
   */
  private static Target redactable(Target resolved) {
    if (RedactionToolBar.redactable(resolved.view()) == null) {
      throw new IllegalStateException(
          "The view does not hide regions (a reconstruction never does)"); // NON-NLS
    }
    return resolved;
  }

  private static String regions(Target resolved) {
    DefaultView2d<DicomImageElement> view = resolved.view();
    Mask mask = Redaction.effective(view.getImage(), view.getSeries());
    JsonObjectBuilder body =
        Json.createObjectBuilder()
            .add("target", ViewDisplayState.target(resolved.container(), view)); // NON-NLS
    JsonUtil.addIfPresent(body, "entry", mask == null ? null : mask.origin()); // NON-NLS
    body.add("regionCount", mask == null ? 0 : mask.shapes().size()); // NON-NLS
    JsonArrayBuilder regions = Json.createArrayBuilder();
    if (mask != null) {
      mask.shapes().stream().map(RedactionCommands::bounds).forEach(regions::add);
    }
    body.add("regions", regions); // NON-NLS
    PixelAttestation attestation = PixelAttestation.effective(view.getImage(), view.getSeries());
    if (attestation != null) {
      JsonObjectBuilder attested =
          Json.createObjectBuilder()
              .add("by", attestation.by()) // NON-NLS
              .add("at", attestation.at().toString()); // NON-NLS
      JsonUtil.addIfPresent(attested, "reason", attestation.reason()); // NON-NLS
      body.add("attestation", attested); // NON-NLS
    }
    body.add(
        "reviewRequired", // NON-NLS
        Redaction.requiresReview(view.getImage(), view.getSeries()));
    return body.build().toString();
  }

  /**
   * The thumbnail is built outside the view pipeline and cached, so a change has to say so: an
   * image change counts too, since the thumbnail shows the middle image with its own regions.
   */
  private static void refreshViews(DefaultView2d<DicomImageElement> view) {
    RedactionToolBar.refreshOpenViews();
    Redaction.refreshThumbnail(view.getSeries());
  }

  private static JsonObjectBuilder describe(PixelMask mask, MaskingModelRegistry registry) {
    JsonObjectBuilder entry =
        Json.createObjectBuilder()
            .add("id", mask.id()) // NON-NLS
            .add("name", mask.name()) // NON-NLS
            .add("regionCount", mask.regions().size()) // NON-NLS
            .add("enabled", mask.enabled()) // NON-NLS
            .add("source", SemanticType.valueOf(registry.masks().origin(mask.id()))); // NON-NLS
    JsonUtil.addIfPresent(entry, "modality", mask.match().modality()); // NON-NLS
    JsonUtil.addIfPresent(entry, "stationName", mask.match().stationName()); // NON-NLS
    entry.add(
        "reference", // NON-NLS
        Json.createObjectBuilder()
            .add("columns", mask.reference().columns()) // NON-NLS
            .add("rows", mask.reference().rows())); // NON-NLS
    return entry;
  }

  private static JsonObject bounds(Shape shape) {
    Rectangle2D box = shape.getBounds2D();
    return Json.createObjectBuilder()
        .add("x", JsonUtil.decimal((float) box.getX())) // NON-NLS
        .add("y", JsonUtil.decimal((float) box.getY())) // NON-NLS
        .add("width", JsonUtil.decimal((float) box.getWidth())) // NON-NLS
        .add("height", JsonUtil.decimal((float) box.getHeight())) // NON-NLS
        .build();
  }
}
