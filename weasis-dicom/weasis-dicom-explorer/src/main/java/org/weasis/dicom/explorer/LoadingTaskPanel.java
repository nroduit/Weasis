/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.dicom.explorer.wado.DownloadManager;

public class LoadingTaskPanel extends JPanel {

  private final ExplorerTask<?, ?> task;
  private final boolean globalLoadingManager;
  private final JLabel message = new JLabel();

  public LoadingTaskPanel(ExplorerTask<?, ?> task) {
    this.task = task;
    this.globalLoadingManager = task.isGlobalLoadingManager();
    init();
  }

  public LoadingTaskPanel(boolean globalLoadingManager) {
    this.task = null;
    this.globalLoadingManager = globalLoadingManager;
    init();
  }

  private void init() {
    if (globalLoadingManager) {
      JButton globalResumeButton = new JButton(ResourceUtil.getIcon(ActionIcon.EXECUTE));
      globalResumeButton.setToolTipText(Messages.getString("DicomExplorer.resume_all"));
      globalResumeButton.addActionListener(e -> DownloadManager.resume());
      this.add(globalResumeButton);

      JButton globalStopButton = new JButton(ResourceUtil.getIcon(ActionIcon.SUSPEND));
      globalStopButton.setToolTipText(Messages.getString("DicomExplorer.stop_all"));
      globalStopButton.addActionListener(e -> DownloadManager.stop());
      this.add(globalStopButton);
    } else {
      JButton cancelButton = new JButton(ResourceUtil.getIcon(ActionIcon.SUSPEND));
      cancelButton.setToolTipText(Messages.getString("LoadingTaskPanel.stop_process"));
      cancelButton.addActionListener(
          e -> {
            message.setText(Messages.getString("LoadingTaskPanel.abording"));
            if (task != null) {
              task.cancel();
            }
          });
      this.add(cancelButton);
      if (task != null) {
        CircularProgressBar globalProgress = task.getBar();
        this.add(globalProgress);
        globalProgress.setIndeterminate(true);
      }
    }
    if (task != null) {
      message.setText(task.getMessage());
    }
    this.add(message);
    this.revalidate();
    this.repaint();
  }

  public void setMessage(String msg) {
    message.setText(msg);
  }

  public ExplorerTask getTask() {
    return task;
  }
}
