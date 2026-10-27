/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.ui.editor.SeriesResolver;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewDisplayState;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.SeriesUid;
import org.weasis.dicom.explorer.StudyUid;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2dCommands;
import org.weasis.dicom.viewer2d.View2dCommands.Target;
import org.weasis.dicom.viewer2d.View2dCommands.ViewOptions;
import org.weasis.dicom.viewer2d.subtraction.SubtractionService.Derived;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Command namespace {@code subtraction}: the live preview of a pane and the derived series that
 * subtract one series of a study from another, read like any series. Every verb prints a JSON body.
 */
@Extension
@Command(
    name = "subtraction",
    description =
        "Derived series subtracting one series of a study from another (post-contrast minus"
            + " pre-contrast), scrolled, windowed, measured and sent like any series.",
    subcommands = {
      SubtractionCommands.Preview.class,
      SubtractionCommands.Create.class,
      SubtractionCommands.ListDerived.class,
      SubtractionCommands.Info.class,
      SubtractionCommands.Delete.class
    })
public class SubtractionCommands implements CommandExtension {

  @Override
  public String getNamespace() {
    return "subtraction"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  @Command(
      name = "preview",
      description =
          "Shows the series of a pane minus a mask live, slice by slice, before any series is"
              + " built: a call starts the preview (with the proposed mask unless --mask), --mask"
              + " and --factor change it, --off puts the plain series back. The state of the pane"
              + " comes back with the preview under 'subtraction'.")
  @Since("5.0")
  static class Preview extends AbstractVerb {
    @Mixin ViewOptions target;

    @Option(
        names = "--mask",
        paramLabel = "<series-uid>",
        description =
            "The series subtracted, from 'dicom:list' (default: the series of the study without"
                + " contrast agent, else the earliest compatible one).")
    SeriesUid mask;

    @Option(
        names = "--factor",
        paramLabel = "<number>",
        description = "Weight of the mask, 1 subtracts it fully (default: 1).")
    Double factor;

    @Option(names = "--off", description = "Leaves the preview, the plain series stays shown.")
    boolean off;

    @Override
    protected void run() {
      if (off && (mask != null || factor != null)) {
        throw invalid("--off cannot be combined with the other options");
      }
      Target shown = GuiExecutor.invokeAndRethrow(this::apply);
      if (shown.view() instanceof SubtractionView preview) {
        // The difference is computed off the EDT: report the pane once it shows it
        preview.awaitDifference(5000);
      }
      print(GuiExecutor.invokeAndRethrow(() -> state(shown.container(), shown.view())).toString());
    }

    /** The pane after the change: a preview entered or changed, or the plain view back. */
    private Target apply() {
      Target resolved = target.withImage();
      DefaultView2d<DicomImageElement> view = resolved.view();
      if (off) {
        if (view instanceof SubtractionView preview) {
          View2dCommands.focus(resolved);
          view = preview.exitPreview();
        }
      } else if (mask != null || factor != null || !(view instanceof SubtractionView)) {
        // A bare call on a plain pane starts the preview with the proposed mask
        View2dCommands.focus(resolved);
        view = start(resolved, view);
      }
      return new Target(resolved.container(), view);
    }

    /** Starts the preview of the pane, or changes the one it shows. */
    @SuppressWarnings("unchecked")
    private DefaultView2d<DicomImageElement> start(
        Target resolved, DefaultView2d<DicomImageElement> view) {
      MediaSeries<DicomImageElement> maskSeries;
      if (mask != null) {
        maskSeries =
            (MediaSeries<DicomImageElement>)
                SeriesResolver.ofApplication()
                    .resolve(mask.value())
                    .orElseThrow(
                        () ->
                            CommandFailure.unknownHandle(
                                "Unknown series: " + mask, "dicom:list")); // NON-NLS
      } else if (view instanceof SubtractionView preview) {
        maskSeries = preview.getMask();
      } else {
        maskSeries =
            SubtractionService.proposeMask(
                    view.getSeries(), SubtractionService.compatibleMasks(view.getSeries()))
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "No series of the study is on the grid of the series: give --mask"));
      }
      if (view instanceof SubtractionView preview) {
        preview.setParameters(maskSeries, factor == null ? preview.getFactor() : factor);
        return preview;
      }
      return SubtractionController.enterPreview(
          resolved.container(), view, maskSeries, factor == null ? 1.0 : factor);
    }

    /** The display state of the pane, plus what it previews. */
    private static JsonObject state(
        ImageViewerPlugin<DicomImageElement> container, DefaultView2d<DicomImageElement> view) {
      JsonObject state = ViewDisplayState.of(container, view);
      if (!(view instanceof SubtractionView preview) || preview.getMask() == null) {
        return state;
      }
      JsonObjectBuilder json = Json.createObjectBuilder(state);
      json.add(
          "subtraction", // NON-NLS
          Json.createObjectBuilder()
              .add("mask", uidOf(preview.getMask())) // NON-NLS
              .add("factor", preview.getFactor())); // NON-NLS
      return json.build();
    }
  }

  @Command(
      name = "create",
      description =
          "Builds the series minus the mask as a new series of the study, pixel by pixel on the"
              + " same grid (A - factor*B + offset, real values), and opens it in the selected"
              + " pane. The same operands and options always give the same series.")
  @Since("5.0")
  static class Create extends AbstractVerb {
    @Option(
        names = "--series",
        paramLabel = "<series-uid>",
        description =
            "The series subtracted from, from 'dicom:list' (default: the series of the selected"
                + " pane).")
    SeriesUid series;

    @Option(
        names = "--mask",
        paramLabel = "<series-uid>",
        description =
            "The series subtracted, from 'dicom:list' (default: the series of the study without"
                + " contrast agent, else the earliest compatible one).")
    SeriesUid mask;

    @Option(
        names = "--factor",
        paramLabel = "<number>",
        description =
            "Weight of the mask: 1 subtracts it fully, below 1 keeps part of the anatomy"
                + " (default: 1).")
    Double factor;

    @Option(
        names = "--offset",
        paramLabel = "<number>",
        description = "Value added to every difference (default: 0).")
    Double offset;

    @Option(names = "--absolute", description = "Store the absolute difference.")
    boolean absolute;

    @Option(
        names = "--signed",
        description =
            "Open with the signed window, negative differences visible, instead of black below"
                + " zero.")
    boolean signed;

    @Option(names = "--no-open", description = "Build the series without opening it.")
    boolean noOpen;

    @Override
    protected void run() throws IOException {
      SubtractionSpec spec = GuiExecutor.invokeAndRethrow(this::spec);
      Derived derived = SubtractionService.create(spec);
      JsonObjectBuilder body = describe(derived);
      if (!noOpen) {
        JsonObject target =
            GuiExecutor.invokeAndRethrow(
                () -> {
                  // The preview of what was built gives its pane to the series
                  SubtractionController.selectedPreview()
                      .filter(p -> p.spec().derivedSeriesUid().equals(derived.seriesUid()))
                      .ifPresent(SubtractionView::exitPreview);
                  return SubtractionService.open(derived);
                });
        if (target != null) {
          body.add("target", target); // NON-NLS
        }
      }
      print(body.build().toString());
    }

    /** The operands as given, the defaults read from the selected pane and the study. EDT. */
    private SubtractionSpec spec() {
      String seriesUid = series == null ? selectedSeriesUid() : series.value();
      String maskUid;
      SubtractionView preview = SubtractionController.selectedPreview().orElse(null);
      if (mask == null && preview != null && seriesUid.equals(uidOf(preview.getSeries()))) {
        // What the pane previews is what is built
        maskUid = uidOf(preview.getMask());
        if (factor == null) {
          factor = preview.getFactor();
        }
      } else if (mask == null) {
        MediaSeries<DicomImageElement> source = selectedOrResolved(seriesUid);
        List<MediaSeries<DicomImageElement>> candidates =
            SubtractionService.compatibleMasks(source);
        maskUid =
            SubtractionService.proposeMask(source, candidates)
                .map(SubtractionCommands::uidOf)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "No series of the study is on the grid of the series: give --mask"));
      } else {
        maskUid = mask.value();
      }
      try {
        return new SubtractionSpec(
            seriesUid,
            maskUid,
            factor == null ? 1.0 : factor,
            offset == null ? 0.0 : offset,
            absolute,
            signed);
      } catch (IllegalArgumentException e) {
        throw invalid(e.getMessage());
      }
    }

    private String selectedSeriesUid() {
      ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
      if (view == null || view.getSeries() == null) {
        throw new IllegalStateException("No series in the selected pane: give --series");
      }
      return uidOf(view.getSeries());
    }

    @SuppressWarnings("unchecked")
    private static MediaSeries<DicomImageElement> selectedOrResolved(String uid) {
      ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
      if (view != null && view.getSeries() != null && uid.equals(uidOf(view.getSeries()))) {
        return view.getSeries();
      }
      return (MediaSeries<DicomImageElement>)
          SeriesResolver.ofApplication()
              .resolve(uid)
              .orElseThrow(
                  () ->
                      CommandFailure.unknownHandle(
                          "Unknown series: " + uid, "dicom:list")); // NON-NLS
    }
  }

  @Command(name = "list", description = "The subtraction series built in this session.")
  @Since("5.0")
  static class ListDerived extends AbstractVerb {
    @Option(
        names = "--study",
        paramLabel = "<study-uid>",
        description = "Keep the series of one study, from 'dicom:list'.")
    StudyUid study;

    @Override
    protected void run() {
      JsonArrayBuilder series = Json.createArrayBuilder();
      for (Derived derived : SubtractionService.list(study == null ? null : study.value())) {
        series.add(describe(derived));
      }
      print(Json.createObjectBuilder().add("series", series).build().toString()); // NON-NLS
    }
  }

  @Command(name = "info", description = "What a subtraction series was computed from and how.")
  @Since("5.0")
  static class Info extends AbstractVerb {
    @Option(
        names = "--series",
        required = true,
        paramLabel = "<series-uid>",
        description = "A subtraction series, from 'subtraction:list'.")
    SeriesUid series;

    @Override
    protected void run() {
      print(describe(derived(series)).build().toString());
    }
  }

  @Command(
      name = "delete",
      description = "Removes a subtraction series from the explorer and its slices from the cache.")
  @Since("5.0")
  static class Delete extends AbstractVerb {
    @Option(
        names = "--series",
        required = true,
        paramLabel = "<series-uid>",
        description = "A subtraction series, from 'subtraction:list'.")
    SeriesUid series;

    @Override
    protected void run() {
      Derived derived = derived(series);
      boolean deleted = SubtractionService.delete(derived.seriesUid());
      print(
          Json.createObjectBuilder()
              .add("series", derived.seriesUid()) // NON-NLS
              .add("deleted", deleted) // NON-NLS
              .build()
              .toString());
    }
  }

  private static Derived derived(SeriesUid uid) {
    return SubtractionService.find(uid.value())
        .orElseThrow(
            () ->
                CommandFailure.unknownHandle(
                    "No subtraction series '" + uid + "'", "subtraction:list")); // NON-NLS
  }

  static JsonObjectBuilder describe(Derived derived) {
    JsonObjectBuilder body =
        Json.createObjectBuilder()
            .add("series", derived.seriesUid()) // NON-NLS
            .add("derivation", SubtractionBuilder.DERIVATION); // NON-NLS
    JsonUtil.addIfPresent(
        body,
        "description", // NON-NLS
        TagD.getTagValue(derived.series(), Tag.SeriesDescription, String.class));
    JsonUtil.addIfPresent(body, "study", SubtractionService.studyUid(derived.source())); // NON-NLS
    JsonUtil.addIfPresent(
        body,
        "modality",
        TagD.getTagValue(derived.series(), Tag.Modality, String.class)); // NON-NLS
    body.add(
        "sources", // NON-NLS
        Json.createArrayBuilder()
            .add(source(derived.source(), "series")) // NON-NLS
            .add(source(derived.mask(), "mask"))); // NON-NLS
    body.add("factor", derived.spec().factor()) // NON-NLS
        .add("offset", derived.spec().offset()) // NON-NLS
        .add("absolute", derived.spec().absolute()) // NON-NLS
        .add("signed", derived.spec().signed()) // NON-NLS
        .add("path", "direct") // NON-NLS
        .add("slices", derived.slices()) // NON-NLS
        .add("clipped", derived.clipped()) // NON-NLS
        .add("sameFrameOfReference", derived.sameFrameOfReference()); // NON-NLS
    return body;
  }

  private static JsonObjectBuilder source(MediaSeries<DicomImageElement> series, String role) {
    JsonObjectBuilder json = Json.createObjectBuilder().add("series", uidOf(series)); // NON-NLS
    JsonUtil.addIfPresent(
        json,
        "description",
        TagD.getTagValue(series, Tag.SeriesDescription, String.class)); // NON-NLS
    return json.add("role", role); // NON-NLS
  }

  static String uidOf(MediaSeries<?> series) {
    String uid = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    if (uid == null && series.getTagValue(TagW.SubseriesInstanceUID) instanceof String sub) {
      return sub;
    }
    return uid;
  }
}
