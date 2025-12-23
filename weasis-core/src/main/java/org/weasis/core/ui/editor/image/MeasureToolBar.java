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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.event.ListDataEvent;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
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

  private static final int ICON_SIZE = 18;

  public static final SelectGraphic selectionGraphic = createSelectionGraphic();
  private static final List<Graphic> measureGraphicList = createMeasureGraphicList();
  private static final List<Graphic> drawGraphicList = createDrawGraphicList();

  protected final JButton deleteButton = new JButton();
  protected final ImageViewerEventManager<?> eventManager;

  public MeasureToolBar(final ImageViewerEventManager<?> eventManager, int index) {
    super(Messages.getString("MeasureToolBar.drawing_tools"), index);
    this.eventManager = Objects.requireNonNull(eventManager, "EventManager cannot be null");
    MeasureTool.updateMeasureProperties();

    initializeToolbar();
  }

  private void initializeToolbar() {
    Optional<ComboItemListener<Graphic>> measure = eventManager.getAction(ActionW.DRAW_MEASURE);
    measure.ifPresent(listener -> add(buildButton(listener)));

    Optional<ComboItemListener<Graphic>> draw = eventManager.getAction(ActionW.DRAW_GRAPHICS);
    draw.ifPresent(listener -> add(buildButton(listener)));

    if (measure.isPresent() || draw.isPresent()) {
      setupDeleteButton(measure, draw);
    }
  }

  private void setupDeleteButton(
      Optional<ComboItemListener<Graphic>> measure, Optional<ComboItemListener<Graphic>> draw) {
    deleteButton.setToolTipText(Messages.getString("MeasureToolBar.del"));
    deleteButton.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.SELECTION_DELETE));
    deleteButton.addActionListener(_ -> handleDeleteAction());

    measure.or(() -> draw).ifPresent(listener -> listener.registerActionState(deleteButton));
    add(deleteButton);
  }

  private void handleDeleteAction() {
    GraphicModel gm = eventManager.getSelectedViewPane().getGraphicManager();
    if (gm.getSelectedGraphics().isEmpty()) {
      gm.setSelectedAllGraphics();
    }
    gm.deleteSelectedGraphics(eventManager.getSelectedViewPane(), Boolean.TRUE);
  }

  private DropDownButton buildButton(ComboItemListener<?> action) {
    boolean isDraw = action.getActionW() == ActionW.DRAW_GRAPHICS;
    Icon backgroundIcon =
        isDraw
            ? ResourceUtil.getToolBarIcon(ActionIcon.DRAW_TOP_LEFT)
            : ResourceUtil.getToolBarIcon(ActionIcon.MEASURE_TOP_LEFT);

    MeasureGroupMenu menu = new MeasureGroupMenu(action.getActionW());
    action.registerActionState(menu);
    configureMenuAccelerators(menu);

    DropDownButton dropDownButton = createDropDownButton(action, menu, backgroundIcon);
    menu.setButton(dropDownButton);
    action.registerActionState(dropDownButton);

    dropDownButton.setToolTipText(
        isDraw
            ? Messages.getString("MeasureToolBar.drawing_tools")
            : Messages.getString("MeasureToolBar.tools"));
    dropDownButton.addActionListener(_ -> handleButtonClick(isDraw));

    return dropDownButton;
  }

  private void configureMenuAccelerators(MeasureGroupMenu menu) {
    for (RadioMenuItem item : menu.getRadioMenuItemListCopy()) {
      if (item.getUserObject() instanceof Graphic g && g.getKeyCode() != 0) {
        item.setAccelerator(KeyStroke.getKeyStroke(g.getKeyCode(), g.getModifier()));
      }
    }
  }

  private DropDownButton createDropDownButton(
      ComboItemListener<?> action, MeasureGroupMenu menu, Icon backgroundIcon) {
    return new DropDownButton(
        action.getActionW().cmd(), buildIcon(selectionGraphic, backgroundIcon), menu) {
      @Override
      protected JPopupMenu getPopupMenu() {
        JPopupMenu popupMenu =
            (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
        popupMenu.setInvoker(this);
        return popupMenu;
      }
    };
  }

  private void handleButtonClick(boolean isDraw) {
    ImageViewerPlugin<?> view = eventManager.getSelectedView2dContainer();
    if (view == null) {
      return;
    }

    @SuppressWarnings("rawtypes")
    final ViewerToolBar toolBar = view.getViewerToolBar();
    if (toolBar == null) {
      return;
    }

    String cmd = isDraw ? ActionW.DRAW.cmd() : ActionW.MEASURE.cmd();
    if (!toolBar.isCommandActive(cmd)) {
      MouseActions mouseActions = eventManager.getMouseActions();
      mouseActions.setAction(MouseActions.T_LEFT, cmd);
      view.setMouseActions(mouseActions);
      toolBar.changeButtonState(MouseActions.T_LEFT, cmd);
    }
  }

  public static List<Graphic> getDrawGraphicList() {
    return drawGraphicList;
  }

  public static List<Graphic> getMeasureGraphicList() {
    return measureGraphicList;
  }

  public static void applyDefaultSetting(ViewSetting setting, Graphic graphic) {
    if (graphic instanceof DragGraphic g) {
      g.setLineThickness((float) setting.getLineWidth());
      g.setPaint(setting.getLineColor());
      g.setFilled(setting.isFilled());
      g.setFillOpacity(setting.getFillOpacity());
    }
  }

  public static Icon buildIcon(final Graphic graphic, Icon backgroundIcon) {
    if (graphic == null) {
      return ViewerToolBar.getDopButtonIcon(backgroundIcon, null);
    }

    Icon smallIcon =
        (graphic.getIcon() instanceof FlatSVGIcon flatSVGIcon)
            ? flatSVGIcon.derive(ICON_SIZE, ICON_SIZE)
            : graphic.getIcon();

    return ViewerToolBar.getDopButtonIcon(backgroundIcon, smallIcon);
  }

  private static SelectGraphic createSelectionGraphic() {
    SelectGraphic graphic = new SelectGraphic();
    graphic.setFilled(false);
    graphic.setLabelVisible(false);
    graphic.setLayerType(LayerType.TEMP_DRAW);
    return graphic;
  }

  private static List<Graphic> createMeasureGraphicList() {
    List<Graphic> graphics = new ArrayList<>();

    addGraphic(graphics, () -> selectionGraphic);
    addGraphic(graphics, LineGraphic::new);
    addGraphic(graphics, PolylineGraphic::new);
    addGraphic(graphics, ObliqueRectangleGraphic::new);
    addGraphic(graphics, EllipseGraphic::new);
    addGraphic(graphics, ThreePointsCircleGraphic::new);
    addGraphic(graphics, PolygonGraphic::new);
    addGraphic(graphics, PerpendicularLineGraphic::new);
    addGraphic(graphics, ParallelLineGraphic::new);
    addGraphic(graphics, AngleToolGraphic::new);
    addGraphic(graphics, OpenAngleToolGraphic::new);
    addGraphic(graphics, FourPointsAngleToolGraphic::new);
    addGraphic(graphics, CobbAngleToolGraphic::new);
    addGraphic(graphics, PixelInfoGraphic::new);

    graphics.forEach(g -> g.setLayerType(LayerType.MEASURE));
    return graphics;
  }

  private static List<Graphic> createDrawGraphicList() {
    List<Graphic> graphics = new ArrayList<>();

    addGraphic(graphics, () -> selectionGraphic);
    addGraphic(
        graphics,
        () ->
            new LineGraphic() {
              @Override
              public int getKeyCode() {
                return 0;
              }
            });
    addGraphic(graphics, PolylineGraphic::new);
    addGraphic(graphics, ObliqueRectangleGraphic::new);
    addGraphic(graphics, EllipseGraphic::new);
    addGraphic(graphics, ThreePointsCircleGraphic::new);
    addGraphic(
        graphics,
        () ->
            new PolygonGraphic() {
              @Override
              public int getKeyCode() {
                return 0;
              }
            });

    graphics.forEach(
        g -> {
          g.setLayerType(LayerType.DRAW);
          g.setLabelVisible(false);
        });

    addGraphic(
        graphics,
        () -> {
          Graphic graphic = new AnnotationGraphic();
          graphic.setLayerType(LayerType.ANNOTATION);
          return graphic;
        });

    return graphics;
  }

  private static void addGraphic(List<Graphic> list, Supplier<Graphic> graphicSupplier) {
    list.add(graphicSupplier.get());
  }

  static class MeasureGroupMenu extends GroupRadioMenu<Graphic> {
    private final Feature<? extends ActionState> action;
    private JButton button;

    public MeasureGroupMenu(Feature<? extends ActionState> action) {
      this.action = Objects.requireNonNull(action);
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
      super.contentsChanged(e);
      changeButtonState();
    }

    public void changeButtonState() {
      Object selectedItem = dataModel.getSelectedItem();
      if (button != null && selectedItem instanceof Graphic graphic) {
        FlatSVGIcon backgroundIcon =
            (action == ActionW.DRAW_GRAPHICS)
                ? ResourceUtil.getToolBarIcon(ActionIcon.DRAW_TOP_LEFT)
                : ResourceUtil.getToolBarIcon(ActionIcon.MEASURE_TOP_LEFT);
        Icon icon = buildIcon(graphic, backgroundIcon);
        button.setIcon(icon);
        button.setActionCommand(selectedItem.toString());
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
