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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JOptionPane;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.SeriesResolver;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewDisplayState;
import org.weasis.core.ui.editor.image.ViewportPane;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.exp.ExplorerTask;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

/**
 * Headless entry point of the reformats: builds the reformat of a series in a plane as a series of
 * the same study, keeps the ones of the session, removes them and opens them in a pane. The same
 * source and plane always give the same series.
 */
public final class ReformatService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReformatService.class);

  /** A reformat this session built. */
  public record Derived(
      String sourceUid,
      Plane plane,
      DicomSeries series,
      MediaSeries<DicomImageElement> source,
      Path cacheDir,
      int slices,
      double spacing) {

    public String seriesUid() {
      return ReformatBuilder.derivedSeriesUid(sourceUid, plane);
    }

    boolean isRegistered() {
      return modelOf(series) instanceof DicomModel model
          && model.getSeriesNode(seriesUid()) != null;
    }

    /** The spec a session keeps: the source and the plane. */
    public JsonObject spec() {
      return Json.createObjectBuilder()
          .add("series", sourceUid) // NON-NLS
          .add("plane", plane.name()) // NON-NLS
          .build();
    }
  }

  private static final Map<String, Derived> DERIVED = new ConcurrentHashMap<>();

  private ReformatService() {}

  /** The plane of that name, case ignored. */
  public static Plane plane(String name) {
    try {
      return Plane.valueOf(name.toUpperCase());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new IllegalArgumentException("The plane is AXIAL, CORONAL or SAGITTAL: " + name);
    }
  }

  /**
   * Builds the reformat of the series in the plane, or returns the one this session already built
   * when the explorer still holds it. Off the event dispatch thread.
   */
  public static Derived create(String sourceUid, Plane plane) throws IOException {
    Derived known = DERIVED.get(ReformatBuilder.derivedSeriesUid(sourceUid, plane));
    if (known != null && known.isRegistered()) {
      return known;
    }
    MediaSeries<DicomImageElement> source = resolve(sourceUid);
    if (source.getTagValue(TagW.Derivation) != null) {
      throw new IllegalStateException("A series Weasis derived is not reformatted again");
    }
    ReformatBuilder.Result built = ReformatBuilder.build(source, plane);
    Derived derived =
        new Derived(
            sourceUid,
            plane,
            built.series(),
            source,
            built.cacheDir(),
            built.slices(),
            built.spacing());
    GuiExecutor.invokeAndRethrow(() -> register(derived));
    DERIVED.put(derived.seriesUid(), derived);
    LOGGER.info(
        "Reformat {} built: {} slices at {} mm",
        derived.seriesUid(),
        built.slices(),
        built.spacing());
    return derived;
  }

  private static void register(Derived derived) {
    if (!(modelOf(derived.source()) instanceof DicomModel model)) {
      throw new IllegalStateException("The series is not held by the DICOM explorer");
    }
    MediaSeriesGroup study = model.getParent(derived.source(), DicomModel.study);
    if (study == null) {
      throw new IllegalStateException("The series has no study");
    }
    DicomSeries series = derived.series();
    series.setTag(TagW.ExplorerModel, model);
    model.addHierarchyNode(study, series);
    model.firePropertyChange(
        new ObservableEvent(ObservableEvent.BasicAction.ADD, model, null, series));
  }

  public static Optional<Derived> find(String seriesUid) {
    Derived derived = seriesUid == null ? null : DERIVED.get(seriesUid);
    return derived != null && derived.isRegistered() ? Optional.of(derived) : Optional.empty();
  }

  public static List<Derived> list() {
    return DERIVED.values().stream().filter(Derived::isRegistered).toList();
  }

  /** Removes the reformat from the explorer and its slices from the cache. */
  public static boolean delete(String seriesUid) {
    Derived derived = DERIVED.remove(seriesUid);
    if (derived == null) {
      return false;
    }
    GuiExecutor.invokeAndRethrow(
        () -> {
          if (modelOf(derived.series()) instanceof DicomModel model) {
            MediaSeriesGroup node = model.getSeriesNode(seriesUid);
            if (node != null) {
              model.removeSeries(node);
            }
          }
        });
    FileUtil.delete(derived.cacheDir());
    return true;
  }

  /**
   * Shows the reformat in the selected pane when a 2D viewer of the same patient is selected, else
   * in a new viewer; returns the pane it went to when it went to one. EDT only.
   */
  public static JsonObject open(Derived derived) {
    DicomSeries series = derived.series();
    DataExplorerModel model = modelOf(series);
    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    if (container instanceof View2dContainer viewer
        && model instanceof DicomModel dicomModel
        && viewer.getSelectedViewCanvas() != null
        && viewer.getGroupID() != null
        && viewer
            .getGroupID()
            .equals(dicomModel.getParent(series, model.getTreeModelNodeForNewPlugin()))) {
      ViewCanvas<DicomImageElement> view = viewer.getSelectedViewCanvas();
      viewer.setSeriesToView(view, series);
      viewer.setSelectedImagePane(view);
      return ViewDisplayState.target(viewer, view).build();
    }
    ViewerPluginBuilder.openInDefaultViewer(series, model, ViewerOpenOptions.defaults());
    return null;
  }

  /** The JSON of a reformat for the commands. */
  public static JsonObjectBuilder describe(Derived derived) {
    return Json.createObjectBuilder()
        .add("series", derived.seriesUid()) // NON-NLS
        .add("derivation", ReformatBuilder.DERIVATION) // NON-NLS
        .add(
            "description", // NON-NLS
            String.valueOf(TagD.getTagValue(derived.series(), Tag.SeriesDescription, String.class)))
        .add("source", derived.sourceUid()) // NON-NLS
        .add("plane", derived.plane().name()) // NON-NLS
        .add("slices", derived.slices()) // NON-NLS
        .add("spacing", derived.spacing()); // NON-NLS
  }

  /** Whether the series can be resliced: enough slices with a geometry. */
  public static boolean canReformat(MediaSeries<DicomImageElement> series) {
    if (series == null || series.size(null) < DefaultView2d.MINIMAL_IMAGES_FOR_3D) {
      return false;
    }
    DicomImageElement middle = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    return middle != null && middle.getSliceGeometry() != null;
  }

  /** The acquired series behind a reformat, else the series itself. */
  public static MediaSeries<DicomImageElement> sourceOf(MediaSeries<DicomImageElement> series) {
    return find(TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class))
        .map(Derived::source)
        .orElse(series);
  }

  /** The plane a series lies in: the one of a reformat, else the acquisition plane. */
  public static Plane planeOf(MediaSeries<DicomImageElement> series) {
    Optional<Derived> derived = find(TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class));
    if (derived.isPresent()) {
      return derived.get().plane();
    }
    DicomImageElement middle = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    return middle == null ? Plane.AXIAL : Plane.getAcquisitionPlane(middle);
  }

  /**
   * Shows the pane's series in another plane, on the slice through the point at the centre of the
   * slice shown: the acquired series when the plane is its own, else its reformat, built in the
   * background when the service has none yet. EDT only.
   */
  public static void switchPlane(
      ImageViewerPlugin<DicomImageElement> container,
      ViewCanvas<DicomImageElement> view,
      Plane plane) {
    MediaSeries<DicomImageElement> shown = view == null ? null : view.getSeries();
    if (shown == null || plane == null || plane == planeOf(shown)) {
      return;
    }
    MediaSeries<DicomImageElement> source = sourceOf(shown);
    Vector3d point = centerOf(view.getImage());
    if (plane == planeOf(source)) {
      show(container, view, source, point);
      return;
    }
    String sourceUid = TagD.getTagValue(source, Tag.SeriesInstanceUID, String.class);
    DataExplorerModel model = modelOf(source);
    ExplorerTask<Derived, Void> task =
        new ExplorerTask<>(Messages.getString("ReformatMenu.build"), false) {
          @Override
          protected Derived doInBackground() throws Exception {
            fire(ObservableEvent.BasicAction.LOADING_START);
            return create(sourceUid, plane);
          }

          @Override
          protected void done() {
            fire(ObservableEvent.BasicAction.LOADING_STOP);
            try {
              if (view.getSeries() == shown) {
                show(container, view, get().series(), point);
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (Exception e) {
              Throwable cause = e.getCause() == null ? e : e.getCause();
              LOGGER.error("Reformat failed", cause);
              JOptionPane.showMessageDialog(
                  GuiUtils.getUICore().getApplicationWindow(),
                  Messages.getString("ReformatMenu.failed") + "\n" + cause.getMessage(),
                  Messages.getString("ReformatMenu.title"),
                  JOptionPane.ERROR_MESSAGE);
            }
          }

          private void fire(ObservableEvent.BasicAction action) {
            if (model != null) {
              GuiExecutor.execute(
                  () -> model.firePropertyChange(new ObservableEvent(action, model, null, this)));
            }
          }
        };
    task.execute();
  }

  private static void show(
      ImageViewerPlugin<DicomImageElement> container,
      ViewCanvas<DicomImageElement> view,
      MediaSeries<DicomImageElement> series,
      Vector3d point) {
    ViewportPane<DicomImageElement> pane = container.getOwningPane(view);
    if (pane != null && pane.isTiled()) {
      pane.setSeries(series);
    } else {
      view.setSeries(series, point == null ? null : sliceThrough(series, point));
    }
    series.setOpen(true);
  }

  /** The patient-space point at the centre of an image, null without geometry. */
  public static Vector3d centerOf(DicomImageElement image) {
    GeometryOfSlice geometry = image == null ? null : image.getSliceGeometry();
    if (geometry == null) {
      return null;
    }
    Vector3d size = geometry.getDimensions();
    Vector3d spacing = geometry.getVoxelSpacing();
    Vector3d center = new Vector3d(geometry.getTLHC());
    center.fma(size.x / 2 * spacing.x, geometry.getRow());
    center.fma(size.y / 2 * spacing.y, geometry.getColumn());
    return center;
  }

  /** The slice of the series whose plane passes nearest to the point, null without geometry. */
  public static DicomImageElement sliceThrough(
      MediaSeries<DicomImageElement> series, Vector3d point) {
    DicomImageElement nearest = null;
    double best = Double.MAX_VALUE;
    for (DicomImageElement image : series.copyOfMedias(null, null)) {
      GeometryOfSlice geometry = image.getSliceGeometry();
      if (geometry == null) {
        continue;
      }
      Vector3d offset = new Vector3d(point).sub(geometry.getTLHC());
      double distance = Math.abs(geometry.getNormal().dot(offset));
      if (distance < best) {
        best = distance;
        nearest = image;
      }
    }
    return nearest;
  }

  @SuppressWarnings("unchecked")
  private static MediaSeries<DicomImageElement> resolve(String uid) {
    MediaSeries<? extends MediaElement> series =
        SeriesResolver.ofApplication()
            .resolve(uid)
            .orElseThrow(() -> new IllegalArgumentException("Unknown series: " + uid));
    if (series.getMedia(MediaSeries.MEDIA_POSITION.FIRST, null, null)
        instanceof DicomImageElement) {
      return (MediaSeries<DicomImageElement>) series;
    }
    throw new IllegalStateException("The series holds no image: " + uid);
  }

  private static DataExplorerModel modelOf(MediaSeries<?> series) {
    return series.getTagValue(TagW.ExplorerModel) instanceof DataExplorerModel model ? model : null;
  }
}
