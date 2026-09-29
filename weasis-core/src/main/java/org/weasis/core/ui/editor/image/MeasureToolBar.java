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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.ToolCategory;
import org.weasis.core.ui.model.graphic.imp.BuiltinGraphicTools;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.pref.ViewSetting;
import org.weasis.core.ui.util.WtoolBar;

public class MeasureToolBar extends WtoolBar {

  /**
   * @deprecated use {@link GraphicRegistry#selectionGraphic()}.
   */
  @Deprecated(since = "4.8.0", forRemoval = true)
  public static final SelectGraphic selectionGraphic = BuiltinGraphicTools.SELECTION;

  protected final JButton deleteButton = new JButton();
  protected final ImageViewerEventManager<?> eventManager;

  public MeasureToolBar(final ImageViewerEventManager<?> eventManager, int index) {
    super(Messages.getString("MeasureToolBar.drawing_tools"), index);
    if (eventManager == null) {
      throw new IllegalArgumentException("EventManager cannot be null");
    }
    this.eventManager = eventManager;
    MeasureTool.updateMeasureProperties();

    Optional<ComboItemListener<Graphic>> measure = eventManager.getAction(ActionW.DRAW_MEASURE);
    measure.ifPresent(comboItemListener -> add(buildButton(comboItemListener)));
    Optional<ComboItemListener<Graphic>> draw = eventManager.getAction(ActionW.DRAW_GRAPHICS);
    draw.ifPresent(comboItemListener -> add(buildButton(comboItemListener)));

    if (measure.isPresent() || draw.isPresent()) {
      deleteButton.setToolTipText(Messages.getString("MeasureToolBar.del"));
      deleteButton.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.SELECTION_DELETE));
      deleteButton.addActionListener(
          e -> {
            GraphicModel gm = eventManager.getSelectedViewPane().getGraphicManager();
            if (gm.getSelectedGraphics().isEmpty()) {
              gm.setSelectedAllGraphics();
            }
            gm.deleteSelectedGraphics(eventManager.getSelectedViewPane(), Boolean.TRUE);
          });
      if (measure.isPresent()) {
        measure.get().registerActionState(deleteButton);
      } else
        draw.ifPresent(comboItemListener -> comboItemListener.registerActionState(deleteButton));
      add(deleteButton);
    }
  }

  /**
   * @deprecated use {@link GraphicRegistry#prototypes(ToolCategory)}; the list is read-only.
   */
  @Deprecated(since = "4.8.0", forRemoval = true)
  public static List<Graphic> getDrawGraphicList() {
    return GraphicRegistry.getInstance().prototypes(ToolCategory.DRAW);
  }

  /**
   * @deprecated use {@link GraphicRegistry#prototypes(ToolCategory)}; the list is read-only.
   */
  @Deprecated(since = "4.8.0", forRemoval = true)
  public static List<Graphic> getMeasureGraphicList() {
    return GraphicRegistry.getInstance().prototypes(ToolCategory.MEASURE);
  }

  public static void applyDefaultSetting(ViewSetting setting, Graphic graphic) {
    if (graphic instanceof DragGraphic g) {
      g.setLineThickness((float) setting.getLineWidth());
      g.setPaint(setting.getLineColor());
      g.setFilled(setting.isFilled());
      g.setFillOpacity(setting.getFillOpacity());
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
                BuiltinGraphicTools.SELECTION,
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
