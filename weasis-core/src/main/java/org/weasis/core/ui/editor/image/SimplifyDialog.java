/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.model.graphic.DragGraphic;

/**
 * Raises the simplification tolerance of a traced path until its handles are workable again,
 * previewing the result on the view before it is applied. It runs the same {@link
 * GeomUtil#simplify} as the release of a freehand trace, so what the user gets is what the tool
 * itself would have produced with that tolerance.
 */
public final class SimplifyDialog {

  /** Below this many vertices there is nothing to simplify. */
  public static final int MIN_POINTS = 4;

  /** The slider works in tenths of an image pixel. */
  private static final int STEPS_PER_PIXEL = 10;

  private static final int MAX_TOLERANCE = 10;

  private SimplifyDialog() {}

  public static void show(ViewCanvas<?> view, DragGraphic graphic) {
    List<Point2D> original = copyOf(graphic.getPts());
    Integer pointNumber = graphic.getPtsNumber();
    if (original.size() <= MIN_POINTS) {
      return;
    }

    JSlider slider = new JSlider(1, MAX_TOLERANCE * STEPS_PER_PIXEL, STEPS_PER_PIXEL);
    JLabel kept = new JLabel();
    // The last tolerance the path survived, to clamp the slider on the ones that empty it
    int[] lastValid = {slider.getValue()};
    JPanel panel =
        GuiUtils.getVerticalBoxLayoutPanel(
            GuiUtils.getFlowLayoutPanel(
                new JLabel(Messages.getString("GraphicEditActions.simplify_tolerance"))),
            slider,
            GuiUtils.getFlowLayoutPanel(kept));

    slider.addChangeListener(
        _ -> {
          if (apply(view, graphic, original, slider.getValue(), kept)) {
            lastValid[0] = slider.getValue();
          } else {
            slider.setValue(lastValid[0]);
          }
        });
    apply(view, graphic, original, slider.getValue(), kept);

    int option =
        JOptionPane.showConfirmDialog(
            view.getJComponent(),
            panel,
            Messages.getString("GraphicEditActions.simplify_title"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);

    if (option != JOptionPane.OK_OPTION) {
      graphic.setPts(copyOf(original));
      graphic.setPointNumber(pointNumber);
      rebuild(view, graphic);
    }
  }

  /**
   * @return false when the tolerance would take the path below its minimum number of points
   */
  private static boolean apply(
      ViewCanvas<?> view, DragGraphic graphic, List<Point2D> original, int steps, JLabel kept) {
    double tolerance = (double) steps / STEPS_PER_PIXEL;
    List<Point2D> simplified = GeomUtil.simplify(original, tolerance);
    if (simplified.size() < graphic.getMinPoints()) {
      return false;
    }
    graphic.setPts(copyOf(simplified));
    graphic.setPointNumber(simplified.size());
    rebuild(view, graphic);
    kept.setText(
        String.format(
            Messages.getString("GraphicEditActions.simplify_points"),
            simplified.size(),
            original.size()));
    return true;
  }

  /** Rebuilds the shape and its label: the length or the area of the path has just changed. */
  private static void rebuild(ViewCanvas<?> view, DragGraphic graphic) {
    graphic.buildShape(null);
    graphic.updateLabel(Boolean.TRUE, view);
    view.getJComponent().repaint();
  }

  private static List<Point2D> copyOf(List<Point2D> points) {
    List<Point2D> copy = new ArrayList<>(points.size());
    for (Point2D point : points) {
      copy.add(point == null ? null : new Point2D.Double(point.getX(), point.getY()));
    }
    return copy;
  }
}
