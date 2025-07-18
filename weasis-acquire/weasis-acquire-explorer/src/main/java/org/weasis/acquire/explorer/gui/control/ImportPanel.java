/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.control;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingWorker.StateValue;
import org.weasis.acquire.explorer.AcquireImageStatus;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.AcquireMediaInfo;
import org.weasis.acquire.explorer.ImportTask;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.core.bean.SeriesGroup.Type;
import org.weasis.acquire.explorer.gui.central.ImageGroupPane;
import org.weasis.acquire.explorer.gui.dialog.AcquireImportDialog;
import org.weasis.acquire.explorer.gui.list.AcquireThumbnailListPane;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.tp.raven.spinner.SpinnerProgress;

public class ImportPanel extends JPanel {

  public static final ExecutorService IMPORT_IMAGES =
      ThreadUtil.buildNewSingleThreadExecutor("ImportImage");

  private final JButton importBtn = new JButton(Messages.getString("ImportPanel.import"));
  private final SpinnerProgress progressBar = new SpinnerProgress();

  private final ImageGroupPane centralPane;

  public ImportPanel(AcquireThumbnailListPane<MediaElement> mainPanel, ImageGroupPane centralPane) {
    this.centralPane = centralPane;

    importBtn.setPreferredSize(GuiUtils.getDimension(150, 40));
    importBtn.addActionListener(
        _ -> {
          List<MediaElement> selection = mainPanel.getSelectedValuesList();
          List<MediaElement> nonImageElements =
              selection.stream().filter(media -> !(media instanceof ImageElement)).toList();
          if (!nonImageElements.isEmpty()) {
            SeriesGroup seriesGroup = null;
            for (MediaElement mediaElement : nonImageElements) {
              var type = Type.fromMimeType(mediaElement);
              if (type != null) {
                AcquireMediaInfo info = AcquireManager.findByMedia(mediaElement);
                if (info != null) {
                  seriesGroup = new SeriesGroup(type);
                  AcquireManager.importMedia(info, seriesGroup);
                  info.setStatus(AcquireImageStatus.SUBMITTED);
                }
              }
            }
            if (seriesGroup != null) {
              AcquireManager.getInstance().notifySeriesSelection(seriesGroup);
            }
          }
          List<ImageElement> selectedImages = AcquireManager.toImageElement(selection);
          if (!selectedImages.isEmpty()) {
            AcquireImportDialog dialog = new AcquireImportDialog(this, selectedImages);
            GuiUtils.showCenterScreen(dialog, WinUtil.getParentWindow(mainPanel));
          }
        });
    add(importBtn);
    add(progressBar);

    progressBar.setVisible(false);
    progressBar.setStringPainted(true);
  }

  public ImageGroupPane getCentralPane() {
    return centralPane;
  }

  public void importImageList(
      Collection<ImageElement> toImport, SeriesGroup searchedSeries, int maxRangeInMinutes) {

    ImportTask importTask = new ImportTask(toImport, searchedSeries, maxRangeInMinutes);

    importTask.addPropertyChangeListener(
        evt -> {
          if ("progress".equals(evt.getPropertyName())) {
            int progress = (Integer) evt.getNewValue();
            progressBar.setValue(progress);

          } else if ("state".equals(evt.getPropertyName())) {
            if (StateValue.STARTED == evt.getNewValue()) {
              importBtn.setEnabled(false);
              progressBar.setVisible(true);
              progressBar.setValue(0);

            } else if (StateValue.DONE == evt.getNewValue()) {
              importBtn.setEnabled(true);
              progressBar.setVisible(false);
            }
          }
        });

    IMPORT_IMAGES.execute(importTask);
  }

  public boolean isLoading() {
    return !importBtn.isEnabled();
  }
}
