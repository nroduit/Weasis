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

import java.awt.GridBagConstraints;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewSynchData;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.subtraction.SubtractionBuilder.Percentiles;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.Pairing;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.SlicePair;
import org.weasis.opencv.op.lut.DefaultWlPresentation;

/**
 * A view that shows the series minus a mask live, slice by slice, before any series is built: the
 * counterpart of the MIP view for the subtraction. It scrolls the source series and, for every
 * slice it is asked to show, displays the difference computed off the EDT and cached in a directory
 * of its own. Changing the mask or the weight recomputes; building the series or leaving the
 * preview puts a plain view of the source back in the pane.
 */
public class SubtractionView extends View2d {

  private static final Logger LOGGER = LoggerFactory.getLogger(SubtractionView.class);

  private final Path viewCacheDir = createViewCacheDir();

  /** The preview shown for each source slice, for the current mask and weight. EDT only. */
  private final Map<DicomImageElement, DicomImageElement> previews = new HashMap<>();

  private MediaSeries<DicomImageElement> mask;
  private double factor = 1.0;
  private Map<DicomImageElement, DicomImageElement> maskOf = Map.of();
  private MediaSeries<DicomImageElement> pairedSeries;
  private Percentiles window;

  private volatile DicomImageElement sourceImage;
  private volatile boolean showingDifference;
  private volatile int currentIndex;
  private Thread process;
  private boolean pendingSeriesChange;

  public SubtractionView(ImageViewerEventManager<DicomImageElement> eventManager) {
    super(eventManager);
    getViewButtons().clear();
    getViewButtons().add(buildButton());
  }

  private ViewButton buildButton() {
    ViewButton button =
        new ViewButton(
            (invoker, x, y) -> {
              JPopupMenu popup = new JPopupMenu();
              JMenuItem build = new JMenuItem(Messages.getString("SubtractionTool.subtract"));
              build.addActionListener(e -> buildSeries());
              JMenuItem exit = new JMenuItem(Messages.getString("SubtractionTool.exit"));
              exit.addActionListener(e -> exitPreview());
              popup.add(build);
              popup.add(exit);
              popup.show(invoker, x, y);
            },
            ResourceUtil.getIcon(ActionIcon.MINUS, 24, 24),
            Messages.getString("SubtractionTool.title"));
    button.setVisible(true);
    button.setPosition(GridBagConstraints.NORTHEAST);
    return button;
  }

  /** Takes the series and the slice of the view this one replaces, then starts the preview. */
  public void initPreview(
      ViewCanvas<DicomImageElement> selView, MediaSeries<DicomImageElement> mask, double factor) {
    this.mask = mask;
    this.factor = factor;
    actionsInView.put("no.ko", true); // NON-NLS
    actionsInView.put(ActionW.SORT_STACK.cmd(), selView.getActionValue(ActionW.SORT_STACK.cmd()));
    actionsInView.put(
        ActionW.INVERSE_STACK.cmd(), selView.getActionValue(ActionW.INVERSE_STACK.cmd()));
    actionsInView.put(
        ActionW.FILTERED_SERIES.cmd(), selView.getActionValue(ActionW.FILTERED_SERIES.cmd()));
    setSeries(selView.getSeries(), selView.getImage());
  }

  public MediaSeries<DicomImageElement> getMask() {
    return mask;
  }

  public double getFactor() {
    return factor;
  }

  /** The slice of the source series the view shows the difference of. */
  public DicomImageElement getSourceSlice() {
    return sourceImage;
  }

  /** The spec a build of what the view shows would use. */
  public SubtractionSpec spec() {
    return new SubtractionSpec(
        SubtractionCommands.uidOf(series),
        SubtractionCommands.uidOf(mask),
        PhaseGrouper.phaseOf(series).map(PhaseGrouper.Phase::index).orElse(null),
        PhaseGrouper.phaseOf(mask).map(PhaseGrouper.Phase::index).orElse(null),
        factor,
        0,
        false,
        false);
  }

  /** Changes the mask or its weight and recomputes the shown slice. EDT only. */
  public void setParameters(MediaSeries<DicomImageElement> mask, double factor) {
    if (mask == this.mask && factor == this.factor) {
      return;
    }
    this.mask = mask;
    this.factor = factor;
    showingDifference = false;
    discardPreviews();
    DicomImageElement source = sourceImage;
    if (source != null) {
      showDifference(source);
    }
  }

  @Override
  public void setSeries(MediaSeries<DicomImageElement> newSeries, DicomImageElement selectedMedia) {
    pendingSeriesChange = true;
    super.setSeries(newSeries, selectedMedia);
  }

  @Override
  public void setImage(DicomImageElement img) {
    // A synchronization hands the source slice back while its difference is shown or computed
    if (img == getImage() || img == sourceImage || previews.containsValue(img)) {
      return;
    }
    if (series instanceof DicomSeries s && img != null) {
      @SuppressWarnings("unchecked")
      int index =
          s.getImageIndex(
              img,
              (Filter<DicomImageElement>) actionsInView.get(ActionW.FILTERED_SERIES.cmd()),
              getCurrentSortComparator());
      if (index >= 0) {
        currentIndex = index;
      }
    }
    sourceImage = img;
    showingDifference = false;
    if (img == null || mask == null) {
      show(img);
      return;
    }
    showDifference(img);
  }

  /** Shows the cached difference of the slice, or computes it off the EDT first. */
  private void showDifference(DicomImageElement source) {
    DicomImageElement cached = previews.get(source);
    if (cached != null) {
      show(cached);
      return;
    }
    DicomImageElement maskSlice = pairs().get(source);
    if (maskSlice == null) {
      show(source);
      return;
    }
    stopCurrentProcess();
    SubtractionSpec spec = spec();
    SlicePair pair = new SlicePair(source, maskSlice);
    Percentiles shared = window;
    process =
        Thread.ofVirtual()
            .name(Messages.getString("SubtractionTool.build"))
            .start(
                () -> {
                  Thread me = Thread.currentThread();
                  try {
                    DicomImageElement preview =
                        SubtractionBuilder.preview(
                            spec, series, mask, pair, viewCacheDir, shared, currentIndex + 1);
                    GuiExecutor.execute(() -> accept(source, spec, preview));
                  } catch (IOException | RuntimeException e) {
                    // A computation stopped by a newer one says nothing; a failed one shows the
                    // source slice as it is
                    if (process == me && !me.isInterrupted()) {
                      LOGGER.error("Subtraction preview", e);
                      GuiExecutor.execute(
                          () -> {
                            if (sourceImage == source) {
                              show(source);
                            }
                          });
                    }
                  }
                });
  }

  /** Keeps a computed preview when the view still shows that slice with the same parameters. */
  private void accept(DicomImageElement source, SubtractionSpec spec, DicomImageElement preview) {
    if (source != sourceImage || !spec.equals(spec())) {
      preview.dispose();
      return;
    }
    boolean first = window == null;
    if (first) {
      window = firstWindow(preview);
    }
    previews.put(source, preview);
    show(preview);
    if (first) {
      selectDefaultPreset(preview);
    } else {
      showingDifference = true;
    }
  }

  /** The window of the first preview of a parameter set, shared by the next slices. */
  private static Percentiles firstWindow(DicomImageElement preview) {
    PresetWindowLevel preset = preview.getDefaultPreset(new DefaultWlPresentation(null, true));
    if (preset == null) {
      return null;
    }
    double half = preset.getWindow() / 2;
    return new Percentiles(preset.getLevel() - half, preset.getLevel() + half);
  }

  /**
   * The first difference of a parameter set opens on its own window, selected through the shared
   * preset action as a click would, once the listeners of the new image are in place.
   */
  private void selectDefaultPreset(DicomImageElement preview) {
    PresetWindowLevel preset = preview.getDefaultPreset(new DefaultWlPresentation(null, true));
    if (preset == null) {
      showingDifference = true;
      return;
    }
    GuiExecutor.execute(
        () -> {
          if (getImage() != preview) {
            return;
          }
          if (eventManager.getSelectedViewPane() == this) {
            eventManager.getAction(ActionW.PRESET).ifPresent(a -> a.setSelectedItem(preset));
          } else {
            setActionsInView(ActionW.PRESET.cmd(), preset, true);
          }
          showingDifference = true;
        });
  }

  /**
   * Waits, off the EDT, until the difference of the current slice is shown or the delay is over.
   */
  public boolean awaitDifference(long millis) {
    long deadline = System.currentTimeMillis() + millis;
    while (!showingDifference && System.currentTimeMillis() < deadline) {
      try {
        Thread.sleep(25);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return showingDifference;
  }

  private void show(DicomImageElement image) {
    showingDifference = false;
    DicomImageElement old = getImage();
    if (image != null && old != null && !pendingSeriesChange) {
      Object zoomType = actionsInView.get(ViewCanvas.ZOOM_TYPE_CMD);
      actionsInView.put(ViewCanvas.ZOOM_TYPE_CMD, ZoomType.CURRENT);
      super.setImage(image);
      actionsInView.put(ViewCanvas.ZOOM_TYPE_CMD, zoomType);
    } else {
      pendingSeriesChange = false;
      super.setImage(image);
    }
    if (old == null) {
      eventManager.updateComponentsListener(this);
    }
  }

  /** The mask slice of every slice of the series, paired once per series and mask. */
  private Map<DicomImageElement, DicomImageElement> pairs() {
    if (pairedSeries != series || maskOf.isEmpty()) {
      pairedSeries = series;
      maskOf = new HashMap<>();
      if (SubtractionCompatibility.pair(series, mask) instanceof Pairing pairing) {
        for (SlicePair pair : pairing.slices()) {
          maskOf.put(pair.image(), pair.mask());
        }
      } else {
        LOGGER.warn("The mask cannot be subtracted from the series shown");
      }
    }
    return maskOf;
  }

  private void discardPreviews() {
    stopCurrentProcess();
    List<DicomImageElement> old = new ArrayList<>(previews.values());
    previews.clear();
    maskOf = Map.of();
    window = null;
    Thread.ofVirtual()
        .name("subtraction-cleanup")
        .start(() -> old.forEach(DicomImageElement::dispose)); // NON-NLS
  }

  private synchronized void stopCurrentProcess() {
    Thread t = process;
    if (t != null) {
      process = null;
      t.interrupt();
    }
  }

  @Override
  public int getFrameIndex() {
    return currentIndex;
  }

  /** Builds the series the view previews and shows it in this pane. */
  public void buildSeries() {
    MediaSeries<DicomImageElement> source = series;
    MediaSeries<DicomImageElement> maskSeries = mask;
    double weight = factor;
    exitPreview();
    if (source != null && maskSeries != null) {
      SubtractionController.subtract(source, maskSeries, weight);
    }
  }

  /** Puts a plain view of the source series back in the pane, on the slice shown. */
  public View2d exitPreview() {
    stopCurrentProcess();
    DicomImageElement target = sourceImage;
    List<ViewCanvas<DicomImageElement>> manualSyncTargets = collectManualSyncTargets(this);
    ImageViewerPlugin<DicomImageElement> container = getEventManager().getSelectedView2dContainer();
    container.setSelectedAndGetFocus();
    View2d newView2d = new View2d(getEventManager());
    newView2d.registerDefaultListeners();
    newView2d.setSeries(series, target);
    container.replaceView(this, newView2d);
    for (ViewCanvas<DicomImageElement> syncTarget : manualSyncTargets) {
      newView2d.syncManuallyWith(syncTarget);
    }
    if (!manualSyncTargets.isEmpty()) {
      getEventManager().updateAllListeners(container, container.getSynchView());
    }
    container.setSelectedImagePane(newView2d);
    previews.clear();
    FileUtil.delete(viewCacheDir);
    return newView2d;
  }

  /**
   * Replaces the view with a preview of its series minus the mask, keeping its slice and its manual
   * synchronizations.
   */
  public static SubtractionView enter(
      ImageViewerPlugin<DicomImageElement> container,
      ViewCanvas<DicomImageElement> selView,
      MediaSeries<DicomImageElement> mask,
      double factor) {
    List<ViewCanvas<DicomImageElement>> manualSyncTargets = collectManualSyncTargets(selView);
    container.setSelectedAndGetFocus();
    SubtractionView view = new SubtractionView(selView.getEventManager());
    view.registerDefaultListeners();
    view.initPreview(selView, mask, factor);
    container.replaceView(selView, view);
    for (ViewCanvas<DicomImageElement> target : manualSyncTargets) {
      view.syncManuallyWith(target);
    }
    if (!manualSyncTargets.isEmpty()) {
      view.getEventManager().updateAllListeners(container, container.getSynchView());
    }
    container.setSelectedImagePane(view);
    return view;
  }

  @SuppressWarnings("unchecked")
  private static List<ViewCanvas<DicomImageElement>> collectManualSyncTargets(
      ViewCanvas<DicomImageElement> view) {
    List<ViewCanvas<DicomImageElement>> targets = new ArrayList<>();
    if (view.getActionsInView().get(ActionW.SYNCH_LINK.cmd()) instanceof ViewSynchData data
        && data.isManualSynchActivated()) {
      for (ViewSynchData.ManualSyncData msd : data.getManualSyncDataSet()) {
        targets.add((ViewCanvas<DicomImageElement>) msd.getTargetPane());
      }
    }
    return targets;
  }

  private static Path createViewCacheDir() {
    try {
      return Files.createTempDirectory(SubtractionBuilder.CACHE_DIR, "preview_"); // NON-NLS
    } catch (IOException e) {
      LOGGER.warn("Cannot create the preview cache directory, using the shared one", e);
      return SubtractionBuilder.CACHE_DIR;
    }
  }
}
