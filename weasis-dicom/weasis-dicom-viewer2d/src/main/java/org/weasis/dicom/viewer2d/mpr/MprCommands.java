/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.ui.editor.SeriesResolver;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewDisplayState;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.SeriesUid;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.mpr.ReformatService.Derived;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command namespace {@code mpr}: multiplanar reformats built as series of the study, read like any
 * series. Every verb prints a JSON body.
 */
@Extension
@Command(
    name = "mpr",
    description =
        "Multiplanar reformats of a series built as series of the study (a coronal or sagittal"
            + " stack from an axial one), scrolled, windowed, measured and sent like any series.",
    subcommands = {
      MprCommands.Reformat.class,
      MprCommands.ListDerived.class,
      MprCommands.Delete.class,
      MprCommands.Volumes.class,
      MprCommands.Prefetch.class
    })
public class MprCommands implements CommandExtension {

  @Override
  public String getNamespace() {
    return "mpr"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  @Command(
      name = "reformat",
      description =
          "Builds the reformat of a series in a standard plane as a new series of the study, on an"
              + " isotropic grid at the finest spacing of the source, and opens it in the selected"
              + " pane. The same series and plane always give the same result.")
  @Since("5.0")
  static class Reformat extends AbstractVerb {
    @Option(
        names = "--series",
        paramLabel = "<series-uid>",
        description = "The series reformatted, from 'dicom:list' (default: the selected pane's).")
    SeriesUid series;

    @Option(
        names = "--plane",
        required = true,
        paramLabel = "<plane>",
        description = "AXIAL, CORONAL or SAGITTAL; not the plane the series already lies in.")
    String plane;

    @Option(names = "--no-open", description = "Build the series without opening it.")
    boolean noOpen;

    @Override
    protected void run() throws IOException {
      MprView.Plane target;
      try {
        target = ReformatService.plane(plane);
      } catch (IllegalArgumentException e) {
        throw invalid(e.getMessage());
      }
      String uid =
          series == null
              ? GuiExecutor.invokeAndRethrow(Reformat::selectedSeriesUid)
              : series.value();
      // The plane of the acquired series behind a reformat is the series itself, shown again
      MediaSeries<DicomImageElement> source =
          GuiExecutor.invokeAndRethrow(() -> ReformatService.sourceOf(resolve(uid)));
      if (ReformatService.planeOf(source) == target) {
        JsonObject pane = GuiExecutor.invokeAndRethrow(() -> showInSelectedPane(source, target));
        JsonObjectBuilder body =
            Json.createObjectBuilder()
                .add(
                    "series",
                    String.valueOf(
                        TagD.getTagValue(source, Tag.SeriesInstanceUID, String.class))) // NON-NLS
                .add("plane", target.name()) // NON-NLS
                .add("acquired", true); // NON-NLS
        if (pane != null) {
          body.add("target", pane); // NON-NLS
        }
        print(body.build().toString());
        return;
      }
      Derived derived =
          ReformatService.create(
              TagD.getTagValue(source, Tag.SeriesInstanceUID, String.class), target);
      JsonObjectBuilder body = ReformatService.describe(derived);
      if (!noOpen) {
        JsonObject pane =
            GuiExecutor.invokeAndRethrow(() -> showInSelectedPane(derived.series(), target));
        if (pane == null) {
          pane = GuiExecutor.invokeAndRethrow(() -> ReformatService.open(derived));
        }
        if (pane != null) {
          body.add("target", pane); // NON-NLS
        }
      }
      print(body.build().toString());
    }

    /**
     * Switches the selected pane to that plane when it shows the series or another plane of it, on
     * the slice through the centre of the one shown, and returns its state; null otherwise.
     */
    private static JsonObject showInSelectedPane(
        MediaSeries<DicomImageElement> series, MprView.Plane plane) {
      ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
      ImageViewerPlugin<DicomImageElement> container =
          EventManager.getInstance().getSelectedView2dContainer();
      if (view == null
          || view.getSeries() == null
          || container == null
          || ReformatService.sourceOf(view.getSeries()) != ReformatService.sourceOf(series)) {
        return null;
      }
      ReformatService.switchPlane(container, view, plane);
      return ViewDisplayState.target(container, view).build();
    }

    @SuppressWarnings("unchecked")
    private static MediaSeries<DicomImageElement> resolve(String uid) {
      MediaSeries<? extends MediaElement> resolved =
          SeriesResolver.ofApplication()
              .resolve(uid)
              .orElseThrow(
                  () ->
                      CommandFailure.unknownHandle(
                          "Unknown series: " + uid, "dicom:list")); // NON-NLS
      return (MediaSeries<DicomImageElement>) resolved;
    }

    static String selectedSeriesUid() {
      ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
      if (view == null || view.getSeries() == null) {
        throw new IllegalStateException("No series in the selected pane: give --series");
      }
      return TagD.getTagValue(view.getSeries(), Tag.SeriesInstanceUID, String.class);
    }
  }

  @Command(name = "list", description = "The reformats built in this session.")
  @Since("5.0")
  static class ListDerived extends AbstractVerb {
    @Override
    protected void run() {
      var series = Json.createArrayBuilder();
      ReformatService.list().forEach(d -> series.add(ReformatService.describe(d)));
      print(Json.createObjectBuilder().add("series", series).build().toString()); // NON-NLS
    }
  }

  @Command(
      name = "volumes",
      description =
          "The volumes the service holds for the MPR, the volume rendering and the derived series:"
              + " built once per series and kept, within a budget, for the next consumer.")
  @Since("5.0")
  static class Volumes extends AbstractVerb {
    @Override
    protected void run() {
      var volumes = Json.createArrayBuilder();
      for (VolumeCache.Info info : VolumeService.getInstance().snapshot()) {
        if (info.key() instanceof VolumeService.Key key) {
          JsonObjectBuilder entry =
              Json.createObjectBuilder()
                  .add("series", String.valueOf(key.uid())) // NON-NLS
                  .add("rectified", key.rectified()) // NON-NLS
                  .add(
                      "state", // NON-NLS
                      info.failed() ? "failed" : info.built() ? "ready" : "building") // NON-NLS
                  .add("leases", info.leases()) // NON-NLS
                  .add("bytes", info.bytes()); // NON-NLS
          JsonUtil.addIfPresent(
              entry,
              "description", // NON-NLS
              TagD.getTagValue(key.series(), Tag.SeriesDescription, String.class));
          volumes.add(entry);
        }
      }
      print(
          Json.createObjectBuilder()
              .add("budget", VolumeService.getInstance().budget()) // NON-NLS
              .add("volumes", volumes) // NON-NLS
              .build()
              .toString());
    }
  }

  @Command(
      name = "prefetch",
      description =
          "Builds the volume of a series in the background, after the requests under way, so a"
              + " later MPR, rendering or reformat finds it ready; nothing when the slices need a"
              + " rectification question.")
  @Since("5.0")
  static class Prefetch extends AbstractVerb {
    @Option(
        names = "--series",
        paramLabel = "<series-uid>",
        description = "The series, from 'dicom:list' (default: the selected pane's).")
    SeriesUid series;

    @Override
    protected void run() {
      String uid =
          series == null
              ? GuiExecutor.invokeAndRethrow(Reformat::selectedSeriesUid)
              : series.value();
      MediaSeries<? extends MediaElement> resolved =
          SeriesResolver.ofApplication()
              .resolve(uid)
              .orElseThrow(
                  () ->
                      CommandFailure.unknownHandle(
                          "Unknown series: " + uid, "dicom:list")); // NON-NLS
      new MprVolumePrefetch().prefetch(resolved);
      print(
          Json.createObjectBuilder()
              .add("series", uid) // NON-NLS
              .add("queued", true) // NON-NLS
              .build()
              .toString());
    }
  }

  @Command(
      name = "delete",
      description = "Removes a reformat from the explorer and its slices from the cache.")
  @Since("5.0")
  static class Delete extends AbstractVerb {
    @Option(
        names = "--series",
        required = true,
        paramLabel = "<series-uid>",
        description = "A reformat series, from 'mpr:list'.")
    SeriesUid series;

    @Override
    protected void run() {
      Derived derived =
          ReformatService.find(series.value())
              .orElseThrow(
                  () ->
                      CommandFailure.unknownHandle(
                          "No reformat series '" + series.value() + "'", "mpr:list")); // NON-NLS
      print(
          Json.createObjectBuilder()
              .add("series", derived.seriesUid()) // NON-NLS
              .add("deleted", ReformatService.delete(derived.seriesUid())) // NON-NLS
              .build()
              .toString());
    }
  }
}
