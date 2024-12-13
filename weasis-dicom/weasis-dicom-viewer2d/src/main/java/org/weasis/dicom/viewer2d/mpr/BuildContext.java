/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

public class BuildContext {
  private final Thread thread;
  private final MprContainer mprContainer;
  private final MprView mainView;
  private final boolean[] abort = new boolean[] {false, false};

  public BuildContext(Thread thread, MprContainer mprContainer, MprView mainView) {
    this.thread = thread;
    this.mprContainer = mprContainer;
    this.mainView = mainView;
  }

  public boolean[] getAbort() {
    return abort;
  }

  public MprView getMainView() {
    return mainView;
  }

  public MprContainer getMprContainer() {
    return mprContainer;
  }

  public Thread getThread() {
    return thread;
  }
}
