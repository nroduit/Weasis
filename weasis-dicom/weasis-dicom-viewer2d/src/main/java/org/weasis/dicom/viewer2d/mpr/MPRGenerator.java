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

import javax.swing.JOptionPane;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

public class MPRGenerator {

  public static void createMissingSeries(
      Thread thread, MprContainer mprContainer, final MprView view) {
    MediaSeries<DicomImageElement> series = view.getSeries();
    if (series == null) throw new IllegalStateException("No series");

    Plane plane = view.getPlane();
    if (plane == null) throw new IllegalStateException("No slice orientation");

    ObliqueMpr stack = new ObliqueMpr(plane, series, view, null);

    if (stack.getWidth() == 0 || stack.getHeight() == 0)
      throw new IllegalStateException("No image");

    BuildContext context = new BuildContext(thread, mprContainer, view);
    if (stack.isNonParallelSlices()) {
      confirmMessage(context, Messages.getString("SeriesBuilder.orientation_varying"));
    } else if (stack.isVariableSliceSpacing()) {
      confirmMessage(context, Messages.getString("SeriesBuilder.space"));
    }
    stack.generate(context);
  }

  public static void confirmMessage(BuildContext context, final String message) {
    boolean[] abort = context.getAbort();
    GuiExecutor.invokeAndWait(
        () -> {
          int usrChoice =
              JOptionPane.showConfirmDialog(
                  WinUtil.getValidComponent(context.getMainView()),
                  message + Messages.getString("SeriesBuilder.add_warn"),
                  MprFactory.NAME,
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.QUESTION_MESSAGE);
          if (usrChoice == JOptionPane.NO_OPTION) {
            abort[0] = true;
          } else {
            // bypass for other similar messages
            abort[1] = true;
          }
        });
    if (abort[0]) {
      throw new IllegalStateException(message);
    }
  }
}
