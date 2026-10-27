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
import java.util.ArrayList;
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
import org.weasis.dicom.viewer2d.subtraction.SubtractionSpec.Dsa;
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
      SubtractionCommands.Mask.class,
      SubtractionCommands.Shift.class,
      SubtractionCommands.Phases.class,
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

    @Option(
        names = "--motion-correction",
        arity = "0..1",
        fallbackValue = "true",
        paramLabel = "<on>",
        description =
            "Moves the mask of each slice onto it by the translation phase correlation measures"
                + " (true when given without a value; false switches it off).")
    Boolean motionCorrection;

    @Option(names = "--off", description = "Leaves the preview, the plain series stays shown.")
    boolean off;

    @Override
    protected void run() {
      if (off && (mask != null || factor != null || motionCorrection != null)) {
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
      } else if (mask != null
          || factor != null
          || motionCorrection != null
          || !(view instanceof SubtractionView)) {
        // A bare call on a plain pane starts the preview with the proposed mask
        View2dCommands.focus(resolved);
        view = start(resolved, view);
        if (motionCorrection != null && view instanceof SubtractionView preview) {
          preview.setParameters(
              preview.getMask(), preview.getFactor(), preview.getDsa(), motionCorrection);
        }
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
      } else if (DsaMask.isRun(view.getSeries())) {
        // A run is its own mask: the frames its DICOM names, else the first one
        return SubtractionController.enterPreview(
            resolved.container(),
            view,
            view.getSeries(),
            factor == null ? 1.0 : factor,
            DsaMask.proposeMask(view.getSeries()));
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
        preview.setParameters(
            maskSeries,
            factor == null ? preview.getFactor() : factor,
            mask == null ? preview.getDsa() : null);
        return preview;
      }
      return SubtractionController.enterPreview(
          resolved.container(), view, maskSeries, factor == null ? 1.0 : factor, null);
    }

    /** The display state of the pane, plus what it previews. */
    private static JsonObject state(
        ImageViewerPlugin<DicomImageElement> container, DefaultView2d<DicomImageElement> view) {
      JsonObject state = ViewDisplayState.of(container, view);
      if (!(view instanceof SubtractionView preview) || preview.getMask() == null) {
        return state;
      }
      JsonObjectBuilder json = Json.createObjectBuilder(state);
      JsonObjectBuilder subtraction =
          Json.createObjectBuilder()
              .add("mask", uidOf(preview.getMask())) // NON-NLS
              .add("factor", preview.getFactor()) // NON-NLS
              .add("path", preview.isResampled() ? "resampled" : "direct") // NON-NLS
              .add("motionCorrection", preview.isMotionCorrection()); // NON-NLS
      if (preview.getDsa() != null) {
        subtraction.add("dsa", preview.getDsa().toJson()); // NON-NLS
      }
      json.add("subtraction", subtraction); // NON-NLS
      return json.build();
    }
  }

  /** A verb changing the DSA mask of the run a pane shows, entering its preview when needed. */
  abstract static class RunVerb extends AbstractVerb {
    @Mixin ViewOptions target;

    /** The mask after the change, from the one the preview shows (unshifted first frame). */
    abstract Dsa change(Dsa current);

    @Override
    protected void run() {
      Target shown = GuiExecutor.invokeAndRethrow(this::apply);
      if (shown.view() instanceof SubtractionView preview) {
        preview.awaitDifference(5000);
      }
      print(
          GuiExecutor.invokeAndRethrow(() -> Preview.state(shown.container(), shown.view()))
              .toString());
    }

    private Target apply() {
      Target resolved = target.withImage();
      DefaultView2d<DicomImageElement> view = resolved.view();
      MediaSeries<DicomImageElement> series = view.getSeries();
      if (!DsaMask.isRun(series)) {
        throw new IllegalStateException("The pane does not show an X-ray run of several frames");
      }
      View2dCommands.focus(resolved);
      if (view instanceof SubtractionView preview && preview.getDsa() != null) {
        preview.setParameters(series, preview.getFactor(), change(preview.getDsa()));
        return new Target(resolved.container(), preview);
      }
      Dsa dsa = change(DsaMask.proposeMask(series));
      return new Target(
          resolved.container(),
          SubtractionController.enterPreview(resolved.container(), view, series, 1.0, dsa));
    }

    /** Frame numbers written as {@code 3}, {@code 2-4} or {@code 1,3,5}. */
    static List<Integer> frames(String text) {
      List<Integer> frames = new ArrayList<>();
      for (String part : text.split(",")) {
        String[] range = part.trim().split("-", 2);
        try {
          int first = Integer.parseInt(range[0].trim());
          int last = range.length > 1 ? Integer.parseInt(range[1].trim()) : first;
          if (first < 1 || last < first) {
            throw new NumberFormatException();
          }
          for (int frame = first; frame <= last; frame++) {
            frames.add(frame);
          }
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException(
              "Frames are numbers from 1, a range 'a-b' or a list 'a,b,c': " + text);
        }
      }
      return frames;
    }
  }

  @Command(
      name = "mask",
      description =
          "Sets the mask of the X-ray run a pane shows, taken from its own frames, and previews the"
              + " subtraction: one frame, several frames averaged, or a time-interval offset. The"
              + " state of the pane comes back with the mask under 'subtraction'.")
  @Since("5.0")
  static class Mask extends RunVerb {
    @Option(
        names = "--frames",
        paramLabel = "<frames>",
        description =
            "Mask frame numbers from 1: one ('3'), a range averaged ('2-4') or a list ('1,3,5').")
    String frames;

    @Option(
        names = "--tid-offset",
        paramLabel = "<count>",
        description =
            "Time-interval mask: each frame minus the frame that many positions before it; negative"
                + " for the frame after it.")
    Integer tidOffset;

    @Override
    Dsa change(Dsa current) {
      if ((frames == null) == (tidOffset == null)) {
        throw invalid("Give --frames or --tid-offset");
      }
      try {
        return frames != null
            ? current.withFrames(frames(frames))
            : new Dsa(
                List.of(),
                tidOffset,
                current.firstFrame(),
                current.lastFrame(),
                current.shiftRows(),
                current.shiftColumns());
      } catch (IllegalArgumentException e) {
        throw invalid(e.getMessage());
      }
    }
  }

  @Command(
      name = "shift",
      description =
          "Moves the mask of the X-ray run a pane shows by a sub-pixel shift, to correct the"
              + " patient's motion between the mask and the contrast frames, and previews the"
              + " subtraction. Positive values move the mask down and to the right.")
  @Since("5.0")
  static class Shift extends RunVerb {
    @Option(
        names = "--rows",
        paramLabel = "<pixels>",
        description = "Vertical shift of the mask in pixels (default: unchanged).")
    Double rows;

    @Option(
        names = "--columns",
        paramLabel = "<pixels>",
        description = "Horizontal shift of the mask in pixels (default: unchanged).")
    Double columns;

    @Override
    Dsa change(Dsa current) {
      if (rows == null && columns == null) {
        throw invalid("Give --rows and/or --columns");
      }
      return current.withShift(
          rows == null ? current.shiftRows() : rows,
          columns == null ? current.shiftColumns() : columns);
    }
  }

  @Command(
      name = "phases",
      description =
          "The phases of a dynamic series stored as one series (temporal position, trigger time,"
              + " or repeated slice positions): what --phase and --mask-phase count.")
  @Since("5.0")
  static class Phases extends AbstractVerb {
    @Option(
        names = "--series",
        paramLabel = "<series-uid>",
        description = "A series from 'dicom:list' (default: the series of the selected pane).")
    SeriesUid series;

    @Override
    protected void run() {
      print(
          GuiExecutor.invokeAndRethrow(
              () -> {
                String uid = series == null ? selectedSeriesUid() : series.value();
                JsonArrayBuilder phases = Json.createArrayBuilder();
                List<PhaseGrouper.Phase> found = SubtractionService.phases(uid);
                for (PhaseGrouper.Phase phase : found) {
                  JsonObjectBuilder json =
                      Json.createObjectBuilder()
                          .add("index", phase.index()) // NON-NLS
                          .add("count", phase.count()); // NON-NLS
                  if (phase.time() != null) {
                    json.add("time", phase.time().toString()); // NON-NLS
                  }
                  phases.add(json);
                }
                return Json.createObjectBuilder()
                    .add("series", uid) // NON-NLS
                    .add("dynamic", found.size() > 1) // NON-NLS
                    .add("phases", phases) // NON-NLS
                    .build()
                    .toString();
              }));
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
        names = "--phase",
        paramLabel = "<phase-index>",
        description =
            "Phase of a dynamic series to subtract from, from 'subtraction:phases' (default: every"
                + " slice of the series).")
    Integer phase;

    @Option(
        names = "--mask-phase",
        paramLabel = "<phase-index>",
        description =
            "Phase of the mask series to subtract (default: the first phase when the mask is the"
                + " series itself).")
    Integer maskPhase;

    @Option(
        names = "--all-phases",
        description =
            "Build one series per phase of the series minus the mask phase; the first one opens.")
    boolean allPhases;

    @Option(
        names = "--mip",
        description = "Open the result as a maximum intensity projection through all its slices.")
    boolean mip;

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

    @Option(
        names = "--mask-frames",
        paramLabel = "<frames>",
        description =
            "DSA: mask frames of the run itself, from 1 ('3', '2-4' averaged, '1,3,5'); the series"
                + " is an X-ray run (default for a run: the frames its DICOM names, else frame 1).")
    String maskFrames;

    @Option(
        names = "--tid-offset",
        paramLabel = "<count>",
        description = "DSA: time-interval mask, each frame minus the frame that many before it.")
    Integer tidOffset;

    @Option(
        names = "--shift",
        paramLabel = "<rows,columns>",
        description = "DSA: sub-pixel shift of the mask in pixels, down and right when positive.")
    String shift;

    @Option(
        names = "--motion-correction",
        description =
            "Move the mask of each slice onto it by the translation phase correlation measures"
                + " (default: what the pane previews, else off).")
    boolean motionCorrection;

    @Option(names = "--no-open", description = "Build the series without opening it.")
    boolean noOpen;

    @Override
    protected void run() throws IOException {
      if (allPhases && phase != null) {
        throw invalid("--all-phases cannot be combined with --phase");
      }
      if ((maskFrames != null || tidOffset != null || shift != null)
          && (mask != null || phase != null || maskPhase != null || allPhases)) {
        throw invalid("The DSA options go with the run alone, not with --mask or phases");
      }
      SubtractionSpec spec = GuiExecutor.invokeAndRethrow(this::spec);
      if (motionCorrection) {
        spec = spec.withMotionCorrection(true);
      }
      JsonObjectBuilder body;
      Derived shown;
      if (allPhases) {
        List<Derived> all = SubtractionService.createAll(spec);
        JsonArrayBuilder derived = Json.createArrayBuilder();
        all.forEach(d -> derived.add(describe(d)));
        body =
            Json.createObjectBuilder()
                .add("mask", spec.maskUid()) // NON-NLS
                .add("maskPhase", spec.maskPhase() == null ? 0 : spec.maskPhase()) // NON-NLS
                .add("derived", derived); // NON-NLS
        shown = all.getFirst();
      } else {
        shown = SubtractionService.create(spec);
        body = describe(shown);
      }
      if (!noOpen) {
        Derived opened = shown;
        JsonObject target =
            GuiExecutor.invokeAndRethrow(
                () -> {
                  // The preview of what was built gives its pane to the series
                  SubtractionController.selectedPreview()
                      .filter(p -> p.spec().derivedSeriesUid().equals(opened.seriesUid()))
                      .ifPresent(SubtractionView::exitPreview);
                  JsonObject pane = SubtractionService.open(opened);
                  return mip ? SubtractionService.projectSelectedPane(opened) : pane;
                });
        if (target != null) {
          body.add("target", target); // NON-NLS
        }
        if (mip) {
          body.add(
              "mip", // NON-NLS
              Json.createObjectBuilder()
                  .add("type", "max") // NON-NLS
                  .add("thickness", opened.slices())); // NON-NLS
        }
      }
      print(body.build().toString());
    }

    /** The operands as given, the defaults read from the selected pane and the study. EDT. */
    private SubtractionSpec spec() {
      String seriesUid = series == null ? selectedSeriesUid() : series.value();
      String maskUid;
      Integer maskPhaseIndex = maskPhase;
      SubtractionView preview = SubtractionController.selectedPreview().orElse(null);
      boolean previewed = preview != null && seriesUid.equals(uidOf(preview.getSeries()));
      Dsa dsa = runMask(seriesUid, previewed ? preview : null);
      boolean motion = previewed && preview.isMotionCorrection();
      if (dsa != null) {
        try {
          return new SubtractionSpec(
              seriesUid,
              seriesUid,
              null,
              null,
              factor == null ? (previewed ? preview.getFactor() : 1.0) : factor,
              offset == null ? 0.0 : offset,
              absolute,
              signed,
              dsa,
              motion);
        } catch (IllegalArgumentException e) {
          throw invalid(e.getMessage());
        }
      }
      if (mask == null && previewed) {
        // What the pane previews is what is built
        SubtractionSpec shown = preview.spec();
        maskUid = shown.maskUid();
        if (maskPhaseIndex == null) {
          maskPhaseIndex = shown.maskPhase();
        }
        if (factor == null) {
          factor = preview.getFactor();
        }
      } else if (mask == null) {
        MediaSeries<DicomImageElement> source = selectedOrResolved(seriesUid);
        MediaSeries<DicomImageElement> proposed =
            SubtractionService.proposeMask(source, SubtractionService.compatibleMasks(source))
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "No series of the study is on the grid of the series: give --mask"));
        maskUid = uidOf(proposed);
        if (maskPhaseIndex == null) {
          maskPhaseIndex =
              PhaseGrouper.phaseOf(proposed).map(PhaseGrouper.Phase::index).orElse(null);
        }
      } else {
        maskUid = mask.value();
      }
      if (maskPhaseIndex == null && seriesUid.equals(maskUid)) {
        maskPhaseIndex = 1;
      }
      try {
        return new SubtractionSpec(
            seriesUid,
            maskUid,
            phase,
            maskPhaseIndex,
            factor == null ? 1.0 : factor,
            offset == null ? 0.0 : offset,
            absolute,
            signed,
            null,
            motion);
      } catch (IllegalArgumentException e) {
        throw invalid(e.getMessage());
      }
    }

    /**
     * The DSA mask of a run: the options, else what the pane previews, else the frames the DICOM of
     * the run names when no other mask is asked; null for a series that is not a run.
     */
    private Dsa runMask(String seriesUid, SubtractionView preview) {
      boolean asked = maskFrames != null || tidOffset != null || shift != null;
      if (!asked && preview != null && preview.getDsa() != null) {
        return preview.getDsa();
      }
      if (!asked && (mask != null || phase != null || maskPhase != null || allPhases)) {
        return null;
      }
      MediaSeries<DicomImageElement> source = selectedOrResolved(seriesUid);
      if (!DsaMask.isRun(source)) {
        if (asked) {
          throw invalid("The DSA options need an X-ray run of several frames");
        }
        return null;
      }
      Dsa dsa =
          preview != null && preview.getDsa() != null
              ? preview.getDsa()
              : DsaMask.proposeMask(source);
      try {
        if (maskFrames != null) {
          dsa = dsa.withFrames(RunVerb.frames(maskFrames));
        } else if (tidOffset != null) {
          dsa = new Dsa(List.of(), tidOffset, dsa.firstFrame(), dsa.lastFrame(), 0, 0);
        }
        if (shift != null) {
          String[] parts = shift.split(",", 2);
          dsa =
              dsa.withShift(
                  Double.parseDouble(parts[0].trim()),
                  parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 0);
        }
      } catch (IllegalArgumentException e) {
        throw invalid(
            "--shift is 'rows,columns' in pixels and frames count from 1: " + e.getMessage());
      }
      return dsa;
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

  static String selectedSeriesUid() {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    if (view == null || view.getSeries() == null) {
      throw new IllegalStateException("No series in the selected pane: give --series");
    }
    return uidOf(view.getSeries());
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
            .add(source(derived.source(), derived.spec().phase(), "series")) // NON-NLS
            .add(source(derived.mask(), derived.spec().maskPhase(), "mask"))); // NON-NLS
    body.add("factor", derived.spec().factor()) // NON-NLS
        .add("offset", derived.spec().offset()) // NON-NLS
        .add("absolute", derived.spec().absolute()) // NON-NLS
        .add("signed", derived.spec().signed()) // NON-NLS
        .add("path", derived.path()) // NON-NLS
        .add("slices", derived.slices()) // NON-NLS
        .add("clipped", derived.clipped()) // NON-NLS
        .add("sameFrameOfReference", derived.sameFrameOfReference()); // NON-NLS
    if (derived.spec().dsa() != null) {
      body.add("dsa", derived.spec().dsa().toJson()); // NON-NLS
    }
    if (derived.spec().motionCorrection()) {
      body.add(
          "motionCorrection", // NON-NLS
          Json.createObjectBuilder()
              .add("slices", derived.corrected()) // NON-NLS
              .add("maxShift", derived.maxShift())); // NON-NLS
    }
    return body;
  }

  private static JsonObjectBuilder source(
      MediaSeries<DicomImageElement> series, Integer phase, String role) {
    JsonObjectBuilder json = Json.createObjectBuilder().add("series", uidOf(series)); // NON-NLS
    JsonUtil.addIfPresent(
        json,
        "description",
        TagD.getTagValue(series, Tag.SeriesDescription, String.class)); // NON-NLS
    if (phase != null) {
      json.add("phase", phase); // NON-NLS
    }
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
