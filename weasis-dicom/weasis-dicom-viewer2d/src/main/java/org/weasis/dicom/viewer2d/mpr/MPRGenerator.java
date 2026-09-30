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
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.PromptPolicy;
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

  /**
   * Settles a rectification question by the container's {@link PromptPolicy}: asks the user under
   * {@code ASK}, else records the question as a warning and takes the policy's answer. A refusal
   * throws so the caller falls back to a basic volume.
   */
  public static void confirmMessage(BuildContext context, final String message) {
    MprContainer container = context.getMprContainer();
    container.addBuildWarning(message);
    boolean rectify =
        decideRectification(context.getMainView(), container.getPromptPolicy(), message);
    context.setSkipRectification(!rectify);
    if (!rectify) {
      throw new IllegalStateException(message);
    }
  }

  /** Same decision for a build without {@link BuildContext}, e.g. the 3D volume texture. */
  public static void confirmMessage(Component parent, PromptPolicy policy, final String message) {
    if (!decideRectification(parent, policy, message)) {
      throw new IllegalStateException(message);
    }
  }

  private static boolean decideRectification(
      Component parent, PromptPolicy policy, String message) {
    return switch (policy == null ? PromptPolicy.ASK : policy) {
      case ACCEPT -> true;
      case DECLINE -> false;
      case ASK -> askRectification(parent, message);
    };
  }

  private static boolean askRectification(Component parent, String message) {
    boolean[] accepted = {false};
    GuiExecutor.invokeAndWait(
        () ->
            accepted[0] =
                JOptionPane.showConfirmDialog(
                        WinUtil.getValidComponent(parent),
                        buildMessagePanel(message),
                        MprFactory.NAME,
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE)
                    == JOptionPane.YES_OPTION);
    return accepted[0];
  }

  private static JPanel buildMessagePanel(String message) {
    return GuiUtils.buildHelpMessagePanel(
        message + Messages.getString("warn.rectify.limitation"), "mpr/#volume-geometry"); // NON-NLS
  }
}
