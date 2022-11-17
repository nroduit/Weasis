/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.task;

import java.awt.Component;
import javax.swing.ProgressMonitor;

public class TaskMonitor extends ProgressMonitor {

  private volatile boolean showProgression;
  private volatile boolean aborting;

  public TaskMonitor(Component parentComponent, Object message, String note, int min, int max) {
    super(parentComponent, message, note, min, max);
    this.showProgression = true;
    this.aborting = false;
  }

  public boolean isShowProgression() {
    return showProgression;
  }

  public void setShowProgression(boolean showProgression) {
    this.showProgression = showProgression;
  }

  public boolean isAborting() {
    return aborting || isCanceled();
  }

  public void setAborting(boolean aborting) {
    this.aborting = aborting;
  }
}
