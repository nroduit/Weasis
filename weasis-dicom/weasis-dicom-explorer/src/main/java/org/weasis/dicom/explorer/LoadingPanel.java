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

import java.awt.Component;
import java.util.ArrayList;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

public class LoadingPanel extends JPanel {

  private final ArrayList<ExplorerTask<?, ?>> tasks = new ArrayList<>();
  private final LoadingTaskPanel globalDownloadTask = new LoadingTaskPanel(true);

  public LoadingPanel() {
    this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    this.setAlignmentX(LEFT_ALIGNMENT);
    this.setAlignmentY(TOP_ALIGNMENT);
  }

  public boolean addTask(ExplorerTask<?, ?> task) {
    boolean update = false;
    if (task != null && !tasks.contains(task)) {
      tasks.add(task);
      if (task.isSubTask()) {
        if (getComponentZOrder(globalDownloadTask) == -1) {
          this.add(globalDownloadTask);
          update = true;
        }
        globalDownloadTask.setMessage(task.getMessage());
      } else {
        JPanel taskPanel = new LoadingTaskPanel(task);
        this.add(taskPanel);
        update = true;
      }
    }
    return update;
  }

  public boolean removeTask(ExplorerTask<?, ?> task) {
    boolean update = false;
    if (task != null) {
      tasks.remove(task);
      if (task.isSubTask()) {
        if (getDownloadTaskNumber() == 0) {
          this.remove(globalDownloadTask);
          update = true;
        }
      } else {
        for (Component c : getComponents()) {
          if (c instanceof LoadingTaskPanel loadingTaskPanel
              && task.equals(loadingTaskPanel.getTask())) {
            remove(c);
            task.stopProgress();
            update = true;
          }
        }
      }
    }
    return update;
  }

  public int getDownloadTaskNumber() {
    int i = 0;
    for (ExplorerTask<?, ?> explorerTask : tasks) {
      if (explorerTask.isSubTask()) {
        i++;
      }
    }
    return i;
  }
}
