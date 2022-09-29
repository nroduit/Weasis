/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.event.ListDataEvent;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.graphic.imp.PixelInfoGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.AngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.CobbAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.FourPointsAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.OpenAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.area.EllipseGraphic;
import org.weasis.core.ui.model.graphic.imp.area.ObliqueRectangleGraphic;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.graphic.imp.area.ThreePointsCircleGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.ParallelLineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PerpendicularLineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.pref.ViewSetting;
import org.weasis.core.ui.util.WtoolBar;

public class MeasureToolBar extends WtoolBar {

  public static final SelectGraphic selectionGraphic = new SelectGraphic();

  public static final List<Graphic> measureGraphicList = new ArrayList<>();
  public static final List<Graphic> drawGraphicList = new ArrayList<>();

  static {
    WProperties p = BundleTools.SYSTEM_PREFERENCES;
    if (p.getBooleanProperty("weasis.measure.selection", true)) {
      measureGraphicList.add(selectionGraphic);
    }
    if (p.getBooleanProperty("weasis.measure.line", true)) {
      measureGraphicList.add(new LineGraphic());
    }
    if (p.getBooleanProperty("weasis.measure.polyline", true)) {
      measureGraphicList.add(new PolylineGraphic());
    }
    if (p.getBooleanProperty("weasis.measure.rectangle", true)) {
      measureGraphicList.add(new ObliqueRectangleGraphic());
    }
    if (p.getBooleanProperty("weasis.measure.ellipse", true)) {
      measureGraphicList.add(new EllipseGraphic());
    }
    if (p.getBooleanProperty("weasis.measure.threeptcircle", true)) {
      measureGraphicList.add(new ThreePointsCircleGraphic());
    }
    if (p.getBooleanProperty("weasis.measure.polygon", true)) {
      measureGraphicList.add(new PolygonGraphic());
    }
    if (p.getBooleanProperty("weasis.measure.perpendicular", true)) {
      measureGraphicList.add(new PerpendicularLineGraphic());
    }
    if (p.getBooleanProperty("weasis.measure.parallele", true)) {
      measureGraphicList.add(new ParallelLineGraphic());
    }
    if (p.getBooleanProperty("weasis.measure.angle", true)) {
      measureGraphicList.add(new AngleToolGraphic());
    }
    if (p.getBooleanProperty("weasis.measure.openangle", true)) {
      measureGraphicList.add(new OpenAngleToolGraphic());
    }
    if (p.getBooleanProperty("weasis.measure.fourptangle", true)) {
      measureGraphicList.add(new FourPointsAngleToolGraphic());
    }
    if (p.getBooleanProperty("weasis.measure.cobbangle", true)) {
      measureGraphicList.add(new CobbAngleToolGraphic());
    }
    if (p.getBooleanProperty("weasis.measure.pixelinfo", true)) {
      measureGraphicList.add(new PixelInfoGraphic());
    }
    measureGraphicList.forEach(g -> g.setLayerType(LayerType.MEASURE));

    if (p.getBooleanProperty("weasis.draw.selection", true)) {
      drawGraphicList.add(selectionGraphic);
    }
    if (p.getBooleanProperty("weasis.draw.line", true)) {
      drawGraphicList.add(
          new LineGraphic() {
            @Override
            public int getKeyCode() {
              return 0;
            }
          });
    }
    if (p.getBooleanProperty("weasis.draw.polyline", true)) {
      drawGraphicList.add(new PolylineGraphic());
    }
    if (p.getBooleanProperty("weasis.draw.rectangle", true)) {
      drawGraphicList.add(new ObliqueRectangleGraphic());
    }
    if (p.getBooleanProperty("weasis.draw.ellipse", true)) {
      drawGraphicList.add(new EllipseGraphic());
    }
    if (p.getBooleanProperty("weasis.draw.threeptcircle", true)) {
      drawGraphicList.add(new ThreePointsCircleGraphic());
    }
    if (p.getBooleanProperty("weasis.draw.polygon", true)) {
      drawGraphicList.add(
          new PolygonGraphic() {
            @Override
            public int getKeyCode() {
              return 0;
            }
          });
    }
    drawGraphicList.forEach(
        g -> {
          g.setLayerType(LayerType.DRAW);
          g.setLabelVisible(false);
        });

    if (p.getBooleanProperty("weasis.draw.textGrahic", true)) {
      Graphic graphic = new AnnotationGraphic();
      graphic.setLayerType(LayerType.ANNOTATION);
      drawGraphicList.add(graphic);
    }
    selectionGraphic.setLayerType(LayerType.TEMP_DRAW);
  }

  protected final JButton jButtondelete = new JButton();
  protected final ImageViewerEventManager<?> eventManager;

  public MeasureToolBar(final ImageViewerEventManager<?> eventManager, int index) {
    super(Messages.getString("MeasureToolBar.title"), index);
    if (eventManager == null) {
      throw new IllegalArgumentException("EventManager cannot be null");
    }
    this.eventManager = eventManager;

    MeasureToolBar.measureGraphicList.forEach(
        g -> MeasureToolBar.applyDefaultSetting(MeasureTool.viewSetting, g));
    MeasureToolBar.drawGraphicList.forEach(
        g -> MeasureToolBar.applyDefaultSetting(MeasureTool.viewSetting, g));
    @SuppressWarnings("rawtypes")
    Optional<ComboItemListener> measure =
        eventManager.getAction(ActionW.DRAW_MEASURE, ComboItemListener.class);
    measure.ifPresent(comboItemListener -> add(buildButton(comboItemListener)));
    @SuppressWarnings("rawtypes")
    Optional<ComboItemListener> draw =
        eventManager.getAction(ActionW.DRAW_GRAPHICS, ComboItemListener.class);
    draw.ifPresent(comboItemListener -> add(buildButton(comboItemListener)));

    if (measure.isPresent() || draw.isPresent()) {
      jButtondelete.setToolTipText(Messages.getString("MeasureToolBar.del"));
      jButtondelete.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.SELECTION_DELETE));
      jButtondelete.addActionListener(
          e -> {
            GraphicModel gm = eventManager.getSelectedViewPane().getGraphicManager();
            if (gm.getSelectedGraphics().isEmpty()) {
              gm.setSelectedAllGraphics();
            }
            gm.deleteSelectedGraphics(eventManager.getSelectedViewPane(), Boolean.TRUE);
          });
      if (measure.isPresent()) {
        measure.get().registerActionState(jButtondelete);
      } else
        draw.ifPresent(comboItemListener -> comboItemListener.registerActionState(jButtondelete));
      add(jButtondelete);
    }
  }

  public static void applyDefaultSetting(ViewSetting setting, Graphic graphic) {
    if (graphic instanceof DragGraphic g) {
      g.setLineThickness((float) setting.getLineWidth());
      g.setPaint(setting.getLineColor());
    }
  }

  private DropDownButton buildButton(ComboItemListener<?> action) {
    boolean draw = action.getActionW() == ActionW.DRAW_GRAPHICS;

    MeasureGroupMenu menu = new MeasureGroupMenu(action.getActionW());
    action.registerActionState(menu);

    for (RadioMenuItem item : menu.getRadioMenuItemListCopy()) {
      if (item.getUserObject() instanceof Graphic g && g.getKeyCode() != 0) {
        item.setAccelerator(KeyStroke.getKeyStroke(g.getKeyCode(), g.getModifier()));
      }
    }

    DropDownButton dropDownButton =
        new DropDownButton(
            action.getActionW().cmd(),
            buildIcon(
                selectionGraphic,
                draw
                    ? ResourceUtil.getToolBarIcon(ActionIcon.DRAW_TOP_LEFT)
                    : ResourceUtil.getToolBarIcon(ActionIcon.MEASURE_TOP_LEFT)),
            menu) {
          @Override
          protected JPopupMenu getPopupMenu() {
            JPopupMenu m =
                (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
            m.setInvoker(this);
            return m;
          }
        };
    menu.setButton(dropDownButton);
    action.registerActionState(dropDownButton);

    dropDownButton.setToolTipText(
        draw
            ? Messages.getString("MeasureToolBar.drawing_tools")
            : Messages.getString("MeasureToolBar.tools"));

    // when user press the measure icon, set the action to measure
    dropDownButton.addActionListener(
        e -> {
          ImageViewerPlugin<?> view = eventManager.getSelectedView2dContainer();
          if (view != null) {
            @SuppressWarnings("rawtypes")
            final ViewerToolBar toolBar = view.getViewerToolBar();
            if (toolBar != null) {
              String cmd = draw ? ActionW.DRAW.cmd() : ActionW.MEASURE.cmd();
              if (!toolBar.isCommandActive(cmd)) {
                MouseActions mouseActions = eventManager.getMouseActions();
                mouseActions.setAction(MouseActions.T_LEFT, cmd);
                view.setMouseActions(mouseActions);
                toolBar.changeButtonState(MouseActions.T_LEFT, cmd);
              }
            }
          }
        });
    return dropDownButton;
  }

  public static Icon buildIcon(final Graphic graphic, Icon bckIcon) {
    final Icon smallIcon;
    if (graphic == null) {
      smallIcon = null;
    } else {
      if (graphic.getIcon() instanceof FlatSVGIcon flatSVGIcon) {
        smallIcon = flatSVGIcon.derive(18, 18);
      } else {
        smallIcon = graphic.getIcon();
      }
    }
    return ViewerToolBar.getDopButtonIcon(bckIcon, smallIcon);
  }

  static class MeasureGroupMenu extends GroupRadioMenu<Graphic> {
    private final ActionW action;
    private JButton button;

    public MeasureGroupMenu(ActionW action) {
      super();
      this.action = action;
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
      super.contentsChanged(e);
      changeButtonState();
    }

    public void changeButtonState() {
      Object sel = dataModel.getSelectedItem();
      if (button != null && sel instanceof Graphic graphic) {
        FlatSVGIcon drawIcon =
            action == ActionW.DRAW_GRAPHICS
                ? ResourceUtil.getToolBarIcon(ActionIcon.DRAW_TOP_LEFT)
                : ResourceUtil.getToolBarIcon(ActionIcon.MEASURE_TOP_LEFT);
        Icon icon = buildIcon(graphic, drawIcon);
        button.setIcon(icon);
        button.setActionCommand(sel.toString());
      }
    }

    public JButton getButton() {
      return button;
    }

    public void setButton(JButton button) {
      this.button = button;
    }
  }
}
