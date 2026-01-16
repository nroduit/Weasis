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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
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

  private Thread process;

  public MipView(ImageViewerEventManager<DicomImageElement> eventManager) {
    super(eventManager);
  }

  @Override
  protected void initActionWState() {
    super.initActionWState();
    actionsInView.put(ViewCanvas.ZOOM_TYPE_CMD, ZoomType.BEST_FIT);
    actionsInView.put(MIP_THICKNESS.cmd(), 2);
    actionsInView.put(MipView.MIP.cmd(), MipView.Type.MAX);
    actionsInView.put("no.ko", true);

    // Propagate the preset
    OpManager disOp = getDisplayOpManager();
    disOp.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), false);
  }

  public void initMIPSeries(ViewCanvas<DicomImageElement> selView) {
    if (selView != null) {
      actionsInView.put(ActionW.SORT_STACK.cmd(), selView.getActionValue(ActionW.SORT_STACK.cmd()));
      actionsInView.put(
          ActionW.INVERSE_STACK.cmd(), selView.getActionValue(ActionW.INVERSE_STACK.cmd()));
      actionsInView.put(
          ActionW.FILTERED_SERIES.cmd(), selView.getActionValue(ActionW.FILTERED_SERIES.cmd()));
      setSeries(selView.getSeries(), null);
    }
  }

  @Override
  protected void setImage(DicomImageElement img) {
    // Avoid listening synch events
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
    clearMipCache();

    ImageViewerPlugin<DicomImageElement> container = getEventManager().getSelectedView2dContainer();
    container.setSelectedAndGetFocus();
    View2d newView2d = new View2d(getEventManager());
    newView2d.registerDefaultListeners();
    newView2d.setSeries(series, selectedDicom);
    container.replaceView(this, newView2d);
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
    if (ser == null || extend == null || mipType == null) {
      return null;
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
      ViewerPluginBuilder.openSequenceInPlugin(factory, s, model, false, false);
    }
  }

  protected void setMip(DicomImageElement dicom) {
    DicomImageElement oldImage = getImage();
    if (dicom != null) {
      super.setImage(dicom);
    }

    if (oldImage == null) {
      eventManager.updateComponentsListener(MipView.this);
      return;
    }

    notifyCrossLines();
    cleanupOldImageAsync(oldImage);
  }

  private void notifyCrossLines() {
    eventManager
        .getAction(ActionW.SCROLL_SERIES)
        .ifPresent(action -> action.stateChanged(action.getSliderModel()));
  }

  private void cleanupOldImageAsync(DicomImageElement oldImage) {
    Thread.ofVirtual()
        .name("MIP-Cleanup")
        .start(
            () -> {
              try {
                oldImage.dispose();
                oldImage.removeImageFromCache();
                Optional.ofNullable(oldImage.getFilePath()).ifPresent(this::deleteQuietly);
              } catch (Throwable t) {
                LOGGER.warn("Error during MIP cleanup", t);
              }
            });
  }

  private void clearMipCache() {
    Path cacheDir = SeriesBuilder.MIP_CACHE_DIR;
    try (var paths = Files.list(SeriesBuilder.MIP_CACHE_DIR)) {
      paths.filter(Files::isRegularFile).forEach(this::deleteQuietly);
    } catch (IOException e) {
      LOGGER.warn("Cannot list MIP cache directory {}", cacheDir, e);
    }
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      LOGGER.warn("Cannot delete {}", path, e);
    }
  }
}
