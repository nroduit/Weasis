/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.export;

import java.awt.Window;

/** A source whose frames are known in advance: the task asks for frame <i>i</i> and gets it. */
public abstract class PullSourceBuilder implements FrameSourceBuilder {

  protected abstract FrameSource build(FrameGrabber grabber, int frameDurationMs);

  @Override
  public void run(Window parent, FrameGrabber grabber, int frameDurationMs, FrameSink sink) {
    FrameSource source = build(grabber, frameDurationMs);
    try {
      AnimationProgressDialog.run(
          parent, title(), new AnimationExportTask(source, sink, grabber.getFrameSize()));
    } finally {
      grabber.release();
    }
  }
}
