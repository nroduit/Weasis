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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.task.TaskInterruptionException;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.View2dFactory;

public class MipView extends View2d {
  private static final Logger LOGGER = LoggerFactory.getLogger(MipView.class);

  public static final ImageIcon MIP_ICON_SETTING = ResourceUtil.getIcon(OtherIcon.VIEW_MIP);
  public static final ActionW MIP =
      new ActionW(Messages.getString("MipView.mip"), "mip", 0, 0, null); // NON-NLS
  public static final ActionW MIP_THICKNESS =
      new ActionW(Messages.getString("MipView.img_extend"), "mip_thick", 0, 0, null); // NON-NLS

  public enum Type {
    MIN,
    MEAN,
    MAX
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

  public void initMIPSeries(ViewCanvas<?> selView) {
    if (selView != null) {
      actionsInView.put(ActionW.SORT_STACK.cmd(), selView.getActionValue(ActionW.SORT_STACK.cmd()));
      actionsInView.put(
          ActionW.INVERSE_STACK.cmd(), selView.getActionValue(ActionW.INVERSE_STACK.cmd()));
      actionsInView.put(
          ActionW.FILTERED_SERIES.cmd(), selView.getActionValue(ActionW.FILTERED_SERIES.cmd()));
      MediaSeries s = selView.getSeries();
      setSeries(s, null);
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
    final Thread t = process;
    if (t != null) {
      process = null;
      t.interrupt();
    }
  }

  public void exitMipMode(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {
    // reset current process
    this.setActionsInView(MipView.MIP.cmd(), null);
    this.setActionsInView(MipView.MIP_THICKNESS.cmd(), null);

    setMip(null);
    File mipDir =
        AppProperties.buildAccessibleTempDirectory(
            AppProperties.FILE_CACHE_DIR.getName(), "mip"); // NON-NLS
    FileUtil.deleteDirectoryContents(mipDir, 1, 0);

    ImageViewerPlugin<DicomImageElement> container =
        this.getEventManager().getSelectedView2dContainer();
    container.setSelectedAndGetFocus();
    View2d newView2d = new View2d(this.getEventManager());
    newView2d.registerDefaultListeners();
    newView2d.setSeries(series, selectedDicom);
    container.replaceView(this, newView2d);
  }

  public static void buildMip(final MipView view, final boolean fullSeries) {
    if (view == null) {
      return;
    }
    view.stopCurrentProcess();

    final Type mipType = (Type) view.getActionValue(MipView.MIP.cmd());
    final Integer extend = (Integer) view.getActionValue(MIP_THICKNESS.cmd());
    final MediaSeries<DicomImageElement> ser = view.series;
    if (ser == null || extend == null || mipType == null) {
      return;
    }

    Runnable runnable =
        () -> {
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
            // Following actions need to be executed in EDT thread
            GuiExecutor.instance()
                .execute(
                    () -> {
                      if (dicoms.size() == 1) {
                        view.setMip(dicoms.get(0));
                      } else if (dicoms.size() > 1) {
                        DicomImageElement dcm = dicoms.get(0);
                        Series s =
                            new DicomSeries(
                                TagD.getTagValue(dcm, Tag.SeriesInstanceUID, String.class));
                        s.addAll(dicoms);
                        dcm.getMediaReader().writeMetaData(s);
                        DataExplorerModel model =
                            (DataExplorerModel) ser.getTagValue(TagW.ExplorerModel);
                        if (model instanceof DicomModel dicomModel) {
                          MediaSeriesGroup study = dicomModel.getParent(ser, DicomModel.study);
                          if (study != null) {
                            s.setTag(TagW.ExplorerModel, dicomModel);
                            dicomModel.addHierarchyNode(study, s);
                            dicomModel.firePropertyChange(
                                new ObservableEvent(
                                    ObservableEvent.BasicAction.ADD, dicomModel, null, s));
                          }

                          View2dFactory factory = new View2dFactory();
                          ViewerPluginBuilder.openSequenceInPlugin(factory, s, model, false, false);
                        }
                      }
                    });
          }
        };

    view.process = new Thread(runnable, Messages.getString("MipView.build"));
    view.process.start();
  }

  protected void setMip(DicomImageElement dicom) {
    DicomImageElement oldImage = getImage();
    if (dicom != null) {
      // Trick: call super to change the image as "this" method is empty
      super.setImage(dicom);
    }

    if (oldImage == null) {
      eventManager.updateComponentsListener(MipView.this);
    } else {
      // Force drawing crosslines without changing the slice position
      ActionState sequence = eventManager.getAction(ActionW.SCROLL_SERIES);
      if (sequence instanceof SliderCineListener cineAction) {
        cineAction.stateChanged(cineAction.getSliderModel());
      }
      // Close stream
      oldImage.dispose();
      oldImage.removeImageFromCache();
      // Delete file in cache
      File file = oldImage.getFile();
      if (file != null) {
        FileUtil.delete(file);
      }
    }
  }
}
