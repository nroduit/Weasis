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

import java.awt.Component;
import javax.swing.JOptionPane;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

public class MPRGenerator {

  public static void createMissingSeries(MprContainer mprContainer, final MprView view) {
    MediaSeries<DicomImageElement> series = view.getSeries();
    if (series == null) throw new IllegalStateException("No series");

    Plane plane = view.getPlane();
    if (plane == null) throw new IllegalStateException("No slice orientation");

    ObliqueMpr stack = new ObliqueMpr(plane, series, view, null);

    if (stack.getWidth() == 0 || stack.getHeight() == 0)
      throw new IllegalStateException("No image");

    BuildContext context = new BuildContext(mprContainer, view);
    try {
      if (stack.isNonParallelSlices()) {
        confirmMessage(context, Messages.getString("SeriesBuilder.orientation_varying"));
      } else if (stack.isVariableSliceSpacing()) {
        confirmMessage(context, Messages.getString("SeriesBuilder.space"));
      } else if (stack.isTooFewSlicesForTransformation()) {
        confirmMessage(context, Messages.getString("SeriesBuilder.too_few_slices"));
      }
      // If yes is selected or none of the conditions are met, the volume is generated with
      // geometric rectification
      stack.createTransformedVolume();
    } catch (IllegalStateException e) {
      // If the user selects no then a basic volume with no rectification is generated
      stack.createBasicVolume();
    }
    stack.generate(context);
  }

  public static void confirmMessage(BuildContext context, final String message) {
    GuiExecutor.invokeAndWait(
        () -> {
          int usrChoice =
              JOptionPane.showConfirmDialog(
                  WinUtil.getValidComponent(context.getMainView()),
                  message + Messages.getString("SeriesBuilder.add_warn"),
                  MprFactory.NAME,
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.QUESTION_MESSAGE);
          context.setSkipRectification(usrChoice != JOptionPane.YES_OPTION);
        });
    if (context.isSkipRectification()) {
      throw new IllegalStateException(message);
    }
  }

  public static void confirmMessage(Component parent, final String message) {
    boolean[] skipRectification = {false};
    GuiExecutor.invokeAndWait(
        () -> {
          int usrChoice =
              JOptionPane.showConfirmDialog(
                  WinUtil.getValidComponent(parent),
                  message + Messages.getString("SeriesBuilder.add_warn"),
                  MprFactory.NAME,
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.QUESTION_MESSAGE);
          skipRectification[0] = (usrChoice != JOptionPane.YES_OPTION);
        });
    if (skipRectification[0]) {
      throw new IllegalStateException(message);
    }
  }
}
