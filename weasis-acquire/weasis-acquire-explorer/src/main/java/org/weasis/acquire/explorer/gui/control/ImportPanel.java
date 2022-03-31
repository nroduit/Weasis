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
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.ImportTask;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.ImageGroupPane;
import org.weasis.acquire.explorer.gui.dialog.AcquireImportDialog;
import org.weasis.acquire.explorer.gui.list.AcquireThumbnailListPane;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.util.ThreadUtil;

public class ImportPanel extends JPanel {

  public static final ExecutorService IMPORT_IMAGES =
      ThreadUtil.buildNewSingleThreadExecutor("ImportImage");

  private final JButton importBtn = new JButton(Messages.getString("ImportPanel.import"));
  private final CircularProgressBar progressBar = new CircularProgressBar(0, 100);

  private final ImageGroupPane centralPane;

  // TODO create ACTION object fpr import
  // so wherever it's called (button / popup/ menuBar ,,) it can be disabled/enabled from the ACTION
  // object

  public ImportPanel(AcquireThumbnailListPane<MediaElement> mainPanel, ImageGroupPane centralPane) {
    this.centralPane = centralPane;

    importBtn.setPreferredSize(GuiUtils.getDimension(150, 40));
    importBtn.addActionListener(
        e -> {
          List<ImageElement> selected =
              AcquireManager.toImageElement(mainPanel.getSelectedValuesList());
          if (!selected.isEmpty()) {
            AcquireImportDialog dialog = new AcquireImportDialog(this, selected);
            GuiUtils.showCenterScreen(dialog, WinUtil.getParentWindow(mainPanel));
          }
        });
    add(importBtn);
    add(progressBar);

    progressBar.setVisible(false);
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
