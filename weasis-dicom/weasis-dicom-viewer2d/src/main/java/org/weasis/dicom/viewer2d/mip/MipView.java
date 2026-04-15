/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mip;

import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.ImageIcon;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.task.TaskInterruptionException;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.Feature.SliderChangeListenerValue;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.View2dFactory;

/**
 * MIP (Maximum/Mean/Minimum Intensity Projection) view with responsive UI. Heavy work runs in
 * background threads; EDT is used only for UI updates.
 */
public class MipView extends View2d {
  private static final Logger LOGGER = LoggerFactory.getLogger(MipView.class);

  public static final ImageIcon MIP_ICON_SETTING = ResourceUtil.getIcon(OtherIcon.VIEW_MIP);

  public static final class MipViewType extends Feature<MipView.Type> {
    public MipViewType(String title, String command, int keyEvent, int modifier, Cursor cursor) {
      super(title, command, keyEvent, modifier, cursor);
    }
  }

  public static final MipViewType MIP =
      new MipViewType(Messages.getString("MipView.mip"), "mip", 0, 0, null); // NON-NLS
  public static final SliderChangeListenerValue MIP_THICKNESS =
      new SliderChangeListenerValue(
          Messages.getString("MipView.img_extend"), "mip_thick", 0, 0, null); // NON-NLS

  public enum Type {
    NONE(Messages.getString("MipPopup.none"), 0),
    MIN(Messages.getString("MipPopup.min"), 1),
    MEAN(Messages.getString("MipPopup.mean"), 2),
    MAX(Messages.getString("MipPopup.max"), 3);

    final String title;
    final int id;

    Type(String title, int id) {
      this.title = title;
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  private final Path viewCacheDir = createViewCacheDir();

  private Thread process;

  private volatile DicomImageElement centerImage; // NOSONAR volatile only guarantees visibility
  private volatile int currentIndex = 0;
  private volatile Type lastBuiltType;
  private volatile Integer lastBuiltExtend;
  private volatile boolean pendingCrosslineUpdate = false;

  public MipView(ImageViewerEventManager<DicomImageElement> eventManager) {
    super(eventManager);
    // Remove PR and KO buttons
    getViewButtons().clear();
    getViewButtons().add(buildMipButton());
  }

  public ViewButton buildMipButton() {
    ViewButton button =
        new ViewButton(
            (invoker, x, y) -> {
              javax.swing.JPopupMenu popup = MipMenu.buildViewButtonPopup(this);
              popup.show(invoker, x, y);
            },
            ResourceUtil.getIcon(OtherIcon.VIEW_MIP, 24, 24),
            Messages.getString("MipPopup.title"));
    button.setVisible(true);
    button.setPosition(GridBagConstraints.NORTHEAST);
    return button;
  }

  @Override
  protected void initActionWState() {
    super.initActionWState();
    // Propagate the preset
    OpManager disOp = getDisplayOpManager();
    disOp.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), false);
  }

  public void initMIPSeries(ViewCanvas<DicomImageElement> selView) {
    if (selView != null) {
      setSeries(selView.getSeries(), selView.getImage());

      actionsInView.put(MIP_THICKNESS.cmd(), 2);
      actionsInView.put(MipView.MIP.cmd(), MipView.Type.MAX);
      actionsInView.put("no.ko", true);
      actionsInView.put(ActionW.SORT_STACK.cmd(), selView.getActionValue(ActionW.SORT_STACK.cmd()));
      actionsInView.put(
          ActionW.INVERSE_STACK.cmd(), selView.getActionValue(ActionW.INVERSE_STACK.cmd()));
      actionsInView.put(
          ActionW.FILTERED_SERIES.cmd(), selView.getActionValue(ActionW.FILTERED_SERIES.cmd()));
    }
  }

  @Override
  protected void setImage(DicomImageElement img) {
    if (img == getImage()) {
      return; // No change, skip everything
    }
    // Resolve and cache the index of the incoming image inside the filtered/sorted original series
    // so that SeriesBuilder uses the exact same position as the center of the slab, independently
    // of when the scroll-slider EDT update arrives.
    if (series instanceof DicomSeries s) {
      @SuppressWarnings("unchecked")
      int idx =
          s.getImageIndex(
              img,
              (Filter<DicomImageElement>) actionsInView.get(ActionW.FILTERED_SERIES.cmd()),
              getCurrentSortComparator());
      if (idx >= 0) {
        currentIndex = idx;
      }
    }

    Type mipType = (Type) getActionValue(MipView.MIP.cmd());
    if (mipType == null || mipType == Type.NONE || series == null || img == null) {
      setMip(img);
      return;
    }

    Integer extend = (Integer) getActionValue(MIP_THICKNESS.cmd());
    if (img == centerImage && mipType == lastBuiltType && Objects.equals(extend, lastBuiltExtend)) {
      return;
    }
    pendingCrosslineUpdate = !Objects.equals(extend, lastBuiltExtend);
    buildMip(this);
  }

  /**
   * Records the original-series image at the center of the MIP slab together with the parameters
   * used to produce it. Called from the background build thread. The three values are read together
   * by {@link #setImage} to detect a no-op rebuild.
   */
  public void setCenterImage(DicomImageElement img, Type mipType, Integer extend) {
    centerImage = img;
    lastBuiltType = mipType;
    lastBuiltExtend = extend;
  }

  /**
   * Returns the 0-based index of the <em>centre image</em> of the current MIP slab inside the
   * original series.
   *
   * <p>{@link #currentIndex} is updated every time {@link #setImage(DicomImageElement)} resolves
   * the received image's position in the filtered/sorted series, so it always reflects the correct
   * slab-centre position without requiring another series lookup here.
   */
  @Override
  public int getFrameIndex() {
    return currentIndex;
  }

  public boolean isProcessRunning() {
    return process != null;
  }

  protected synchronized void stopCurrentProcess() {
    Thread t = process;
    if (t != null) {
      process = null;
      t.interrupt();
    }
  }

  public void exitMipMode(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {
    // Reset current process
    setActionsInView(MipView.MIP.cmd(), null);
    setActionsInView(MipView.MIP_THICKNESS.cmd(), null);

    setMip(null);
    FileUtil.delete(viewCacheDir);

    // When no explicit target image is given, restore the slab-centre position so the replacement
    // view opens at the same frame the user was looking at rather than jumping to the first image.
    DicomImageElement targetImage = selectedDicom != null ? selectedDicom : centerImage;

    ImageViewerPlugin<DicomImageElement> container = getEventManager().getSelectedView2dContainer();
    container.setSelectedAndGetFocus();
    View2d newView2d = new View2d(getEventManager());
    newView2d.registerDefaultListeners();
    newView2d.setSeries(series, targetImage);
    container.replaceView(this, newView2d);

    pendingCrosslineUpdate = true;
    updateCrosslines();
  }

  public static void buildMip(final MipView view) {
    Runnable runnable = buildMipRunnable(view, false);
    if (runnable != null) {
      view.process = Thread.ofVirtual().name(Messages.getString("MipView.build")).start(runnable);
    }
  }

  public static Runnable buildMipRunnable(final MipView view, final boolean fullSeries) {
    if (view == null) {
      return null;
    }
    view.stopCurrentProcess();

    Type mipType = (Type) view.getActionValue(MipView.MIP.cmd());
    Integer extend = (Integer) view.getActionValue(MIP_THICKNESS.cmd());
    MediaSeries<DicomImageElement> ser = view.series;
    if (ser == null || extend == null || extend <= 0 || mipType == null || mipType == Type.NONE) {
      return null; // Nothing to compute for NONE mode or missing data
    }

    return () -> {
      final List<DicomImageElement> dicoms = new ArrayList<>();
      try {
        SeriesBuilder.applyMipParameters(view, ser, dicoms, mipType, extend, fullSeries);
      } catch (TaskInterruptionException e) {
        dicoms.clear();
        LOGGER.info(e.getMessage());
      } catch (Throwable t) {
        dicoms.clear();
        AuditLog.logError(LOGGER, t, "Mip rendering error"); // NON-NLS
      } finally {
        GuiExecutor.execute(() -> view.handleMipResult(dicoms, ser));
      }
    };
  }

  private void handleMipResult(
      List<DicomImageElement> dicomImages, MediaSeries<DicomImageElement> ser) {
    if (dicomImages.isEmpty()) {
      return;
    }
    if (dicomImages.size() == 1) {
      setMip(dicomImages.getFirst());
      return;
    }

    DicomImageElement first = dicomImages.getFirst();
    DicomSeries s = new DicomSeries(TagD.getTagValue(first, Tag.SeriesInstanceUID, String.class));
    s.addAll(dicomImages);
    first.getMediaReader().writeMetaData(s);

    DataExplorerModel model = (DataExplorerModel) ser.getTagValue(TagW.ExplorerModel);
    if (model instanceof DicomModel dicomModel) {
      MediaSeriesGroup study = dicomModel.getParent(ser, DicomModel.study);
      if (study != null) {
        s.setTag(TagW.ExplorerModel, dicomModel);
        dicomModel.addHierarchyNode(study, s);
        dicomModel.firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.ADD, dicomModel, null, s));
      }

      SeriesViewerFactory factory = GuiUtils.getUICore().getViewerFactory(View2dFactory.NAME);
      new ViewerPluginBuilder(
              factory,
              List.of(s),
              model,
              ViewerOpenOptions.builder().placement(ViewerPlacement.newTab()).build())
          .open();
    }
  }

  protected void setMip(DicomImageElement dicom) {
    DicomImageElement oldImage = getImage();
    if (dicom != null) {
      if (oldImage != null) {
        // Preserve the current zoom level when updating the MIP slab (e.g. while scrolling).
        // Without this guard, DefaultView2d.resetZoom() would reset to BEST_FIT on every update
        // because the scroll handler restores ZOOM_TYPE_CMD before the async build finishes.
        Object oldZoomType = actionsInView.get(ViewCanvas.ZOOM_TYPE_CMD);
        actionsInView.put(ViewCanvas.ZOOM_TYPE_CMD, ZoomType.CURRENT);
        super.setImage(dicom);
        actionsInView.put(ViewCanvas.ZOOM_TYPE_CMD, oldZoomType);
      } else {
        super.setImage(dicom);
      }
    }

    if (oldImage == null) {
      eventManager.updateComponentsListener(this);
    } else {
      cleanupOldImageAsync(oldImage);
    }
    updateCrosslines();
  }

  void activateCrosslinesUpdate() {
    pendingCrosslineUpdate = true;
  }

  void updateCrosslines() {
    // Force drawing crosslines only when the MIP thickness changed
    if (pendingCrosslineUpdate) {
      pendingCrosslineUpdate = false;
      eventManager
          .getAction(ActionW.SCROLL_SERIES)
          .ifPresent(a -> a.stateChanged(a.getSliderModel()));
    }
  }

  private void cleanupOldImageAsync(DicomImageElement oldImage) {
    Path filePath = oldImage.getFilePath();
    if (filePath == null || !filePath.startsWith(viewCacheDir)) {
      return;
    }
    Thread.ofVirtual()
        .name("MIP-Cleanup")
        .start(
            () -> {
              try {
                oldImage.dispose();
                oldImage.removeImageFromCache();
              } catch (Throwable t) {
                LOGGER.warn("Error during MIP cleanup", t);
              }
            });
  }

  /** Returns the per-view cache directory where this view writes its MIP temp files. */
  public Path getCacheDir() {
    return viewCacheDir;
  }

  /**
   * Creates a unique subdirectory under {@link SeriesBuilder#MIP_CACHE_DIR} for this view instance.
   * Falls back to the shared root directory if creation fails (safe: the old shared-directory
   * behavior is preserved in that unlikely case).
   */
  private static Path createViewCacheDir() {
    try {
      return Files.createTempDirectory(SeriesBuilder.MIP_CACHE_DIR, "view_"); // NON-NLS
    } catch (IOException e) {
      LOGGER.warn("Cannot create per-view MIP cache directory, falling back to shared dir", e);
      return SeriesBuilder.MIP_CACHE_DIR;
    }
  }
}
