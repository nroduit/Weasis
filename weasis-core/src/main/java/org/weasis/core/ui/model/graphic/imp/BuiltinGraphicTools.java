/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComponent;
import org.weasis.core.ui.editor.image.dockable.CardiothoracicRatioPanel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicToolContext;
import org.weasis.core.ui.model.graphic.GraphicToolDescriptor;
import org.weasis.core.ui.model.graphic.GraphicToolProvider;
import org.weasis.core.ui.model.graphic.ToolCategory;
import org.weasis.core.ui.model.graphic.ToolPanel;
import org.weasis.core.ui.model.graphic.imp.angle.AngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.CobbAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.FourPointsAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.OpenAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.area.CircleGraphic;
import org.weasis.core.ui.model.graphic.imp.area.EllipseGraphic;
import org.weasis.core.ui.model.graphic.imp.area.ObliqueRectangleGraphic;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.graphic.imp.area.ThreePointsCircleGraphic;
import org.weasis.core.ui.model.graphic.imp.line.ArrowGraphic;
import org.weasis.core.ui.model.graphic.imp.line.BidirectionalGraphic;
import org.weasis.core.ui.model.graphic.imp.line.CardiothoracicRatioGraphic;
import org.weasis.core.ui.model.graphic.imp.line.FreehandGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.ParallelLineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PerpendicularLineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.RulerGraphic;
import org.weasis.core.ui.model.layer.LayerType;

/**
 * The tools shipped with the core, in palette order, with their stable keys. Advanced tools are not
 * in a palette: they exist for the graphics other tools produce and for their preferences.
 */
public final class BuiltinGraphicTools implements GraphicToolProvider {

  public static final SelectGraphic SELECTION = new SelectGraphic();

  static {
    SELECTION.setFilled(false);
    SELECTION.setLabelVisible(false);
    SELECTION.setLayerType(LayerType.TEMP_DRAW);
  }

  public static final String SELECT = "weasis.select"; // NON-NLS
  public static final String LINE = "weasis.line"; // NON-NLS
  public static final String POLYLINE = "weasis.polyline"; // NON-NLS
  public static final String RECTANGLE = "weasis.rectangle"; // NON-NLS
  public static final String ELLIPSE = "weasis.ellipse"; // NON-NLS
  public static final String CIRCLE = "weasis.circle"; // NON-NLS
  public static final String CIRCLE_3 = "weasis.circle3"; // NON-NLS
  public static final String POLYGON = "weasis.polygon"; // NON-NLS
  public static final String PERPENDICULAR = "weasis.perpendicular"; // NON-NLS
  public static final String PARALLEL = "weasis.parallel"; // NON-NLS
  public static final String ANGLE = "weasis.angle"; // NON-NLS
  public static final String OPEN_ANGLE = "weasis.open-angle"; // NON-NLS
  public static final String ANGLE_4 = "weasis.angle4"; // NON-NLS
  public static final String COBB_ANGLE = "weasis.cobb-angle"; // NON-NLS
  public static final String PROBE = "weasis.probe"; // NON-NLS
  public static final String BIDIRECTIONAL = "weasis.bidirectional"; // NON-NLS
  public static final String CTR = "weasis.ctr"; // NON-NLS
  public static final String FREEHAND = "weasis.freehand"; // NON-NLS
  public static final String ARROW = "weasis.arrow"; // NON-NLS
  public static final String RULER = "weasis.ruler"; // NON-NLS

  public static final String DRAW_SELECT = "weasis.draw.select"; // NON-NLS
  public static final String DRAW_LINE = "weasis.draw.line"; // NON-NLS
  public static final String DRAW_POLYLINE = "weasis.draw.polyline"; // NON-NLS
  public static final String DRAW_RECTANGLE = "weasis.draw.rectangle"; // NON-NLS
  public static final String DRAW_ELLIPSE = "weasis.draw.ellipse"; // NON-NLS
  public static final String DRAW_CIRCLE_3 = "weasis.draw.circle3"; // NON-NLS
  public static final String DRAW_FREEHAND = "weasis.draw.freehand"; // NON-NLS
  public static final String TEXT = "weasis.text"; // NON-NLS

  @Override
  public List<GraphicToolDescriptor> getTools() {
    return List.of(
        measure(SELECT, "selection", () -> SELECTION), // NON-NLS
        measure(LINE, "line", LineGraphic::new).withShortcut(KeyEvent.VK_D, 0), // NON-NLS
        measure(POLYLINE, "polyline", PolylineGraphic::new) // NON-NLS
            .withShortcut(KeyEvent.VK_Y, 0),
        measure(RECTANGLE, "rectangle", ObliqueRectangleGraphic::new), // NON-NLS
        measure(ELLIPSE, "ellipse", EllipseGraphic::new).withShortcut(KeyEvent.VK_E, 0), // NON-NLS
        measure(CIRCLE, "circle", CircleGraphic::new).withShortcut(KeyEvent.VK_O, 0), // NON-NLS
        measure(CIRCLE_3, "threeptcircle", ThreePointsCircleGraphic::new), // NON-NLS
        measure(PERPENDICULAR, "perpendicular", PerpendicularLineGraphic::new), // NON-NLS
        measure(PARALLEL, "parallele", ParallelLineGraphic::new), // NON-NLS
        measure(ANGLE, "angle", AngleToolGraphic::new).withShortcut(KeyEvent.VK_A, 0), // NON-NLS
        measure(OPEN_ANGLE, "openangle", OpenAngleToolGraphic::new), // NON-NLS
        measure(ANGLE_4, "fourptangle", FourPointsAngleToolGraphic::new), // NON-NLS
        measure(COBB_ANGLE, "cobbangle", CobbAngleToolGraphic::new), // NON-NLS
        measure(PROBE, "pixelinfo", PixelInfoGraphic::new), // NON-NLS
        measure(BIDIRECTIONAL, "bidirectional", BidirectionalGraphic::new) // NON-NLS
            .withShortcut(KeyEvent.VK_X, 0),
        measure(CTR, "ctr", CardiothoracicRatioGraphic::new) // NON-NLS
            .withPanel(ctx -> panel(new CardiothoracicRatioPanel(), ctx)),
        measure(FREEHAND, "freehand", FreehandGraphic::new), // NON-NLS
        advanced(POLYGON, "polygon", PolygonGraphic::new).withMinPoints(3), // NON-NLS
        draw(DRAW_SELECT, "selection", () -> SELECTION), // NON-NLS
        draw(DRAW_LINE, "line", LineGraphic::new), // NON-NLS
        draw(DRAW_POLYLINE, "polyline", PolylineGraphic::new), // NON-NLS
        draw(DRAW_RECTANGLE, "rectangle", ObliqueRectangleGraphic::new), // NON-NLS
        draw(DRAW_ELLIPSE, "ellipse", EllipseGraphic::new), // NON-NLS
        draw(DRAW_CIRCLE_3, "threeptcircle", ThreePointsCircleGraphic::new), // NON-NLS
        draw(ARROW, "arrow", ArrowGraphic::new), // NON-NLS
        draw(RULER, "ruler", RulerGraphic::new), // NON-NLS
        draw(DRAW_FREEHAND, "freehand", FreehandGraphic::new), // NON-NLS
        text());
  }

  private static GraphicToolDescriptor measure(
      String key, String legacyName, Supplier<Graphic> prototype) {
    return GraphicToolDescriptor.of(
            key, ToolCategory.MEASURE, () -> configure(prototype.get(), LayerType.MEASURE, true))
        .withHideProperty("weasis.measure." + legacyName); // NON-NLS
  }

  private static GraphicToolDescriptor draw(
      String key, String legacyName, Supplier<Graphic> prototype) {
    return GraphicToolDescriptor.of(
            key, ToolCategory.DRAW, () -> configure(prototype.get(), LayerType.DRAW, false))
        .withHideProperty("weasis.draw." + legacyName); // NON-NLS
  }

  /** A closed path is not drawn directly: it comes from closing a polyline or a freehand. */
  private static GraphicToolDescriptor advanced(
      String key, String legacyName, Supplier<Graphic> prototype) {
    return GraphicToolDescriptor.of(
            key, ToolCategory.ADVANCED, () -> configure(prototype.get(), LayerType.MEASURE, true))
        .withHideProperty("weasis.measure." + legacyName); // NON-NLS
  }

  private static GraphicToolDescriptor text() {
    return GraphicToolDescriptor.of(
            TEXT,
            ToolCategory.DRAW,
            () -> configure(new AnnotationGraphic(), LayerType.ANNOTATION, true))
        .withHideProperty("weasis.draw.text") // NON-NLS
        .withShortcut(KeyEvent.VK_B, 0);
  }

  private static <T extends JComponent & ToolPanel> JComponent panel(
      T panel, GraphicToolContext context) {
    panel.update(context);
    return panel;
  }

  private static Graphic configure(Graphic graphic, LayerType layerType, boolean labelVisible) {
    if (graphic == SELECTION) {
      return graphic;
    }
    graphic.setLayerType(layerType);
    graphic.setLabelVisible(labelVisible);
    return graphic;
  }
}
