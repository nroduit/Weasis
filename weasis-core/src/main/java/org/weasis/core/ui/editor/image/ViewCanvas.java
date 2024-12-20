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

import bibliothek.gui.dock.control.focus.DefaultFocusRequest;
import bibliothek.gui.dock.control.focus.FocusController;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp.Interpolation;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.service.UICore;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.utils.ImageLayerChangeListener;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.core.ui.model.utils.bean.PanPoint.State;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;

public interface ViewCanvas<E extends ImageElement>
    extends Canvas,
        Image2DViewer<E>,
        PropertyChangeListener,
        FocusListener,
        ImageLayerChangeListener<E>,
        KeyListener {

  String ZOOM_TYPE_CMD = "zoom.type";
  int CENTER_POINTER = 1 << 1;
  int HIGHLIGHTED_POINTER = 1 << 2;

  // Border
  Border focusBorder =
      BorderFactory.createMatteBorder(1, 1, 1, 1, IconColor.ACTIONS_YELLOW.getColor());
  Border viewBorder = BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY);

  // Specific points
  PanPoint highlightedPosition = new PanPoint(State.CENTER);
  PanPoint startedDragPoint = new PanPoint(State.DRAGSTART);

  // Graphics for building a pointer
  Color pointerColor1 = Color.black;
  Color pointerColor2 = Color.white;
  Ellipse2D pointerCircle = new Ellipse2D.Double(-27.0, -27.0, 54.0, 54.0);
  Line2D pointerLeft = new Line2D.Double(-40.0, 0.0, -5.0, 0.0);
  Line2D pointerRight = new Line2D.Double(5.0, 0.0, 40.0, 0.0);
  Line2D pointerUp = new Line2D.Double(0.0, -40.0, 0.0, -5.0);
  Line2D pointerDown = new Line2D.Double(0.0, 5.0, 0.0, 40.0);

  default MouseActionAdapter getAction(Feature<?> action) {
    Optional<?> a = getEventManager().getAction(action);
    if (a.isPresent() && a.get() instanceof MouseActionAdapter actionAdapter) {
      return actionAdapter;
    }
    return null;
  }

  default void addModifierMask(String action, int mask) {
    MouseActionAdapter adapter = getMouseAdapter(action);
    if (adapter != null) {
      adapter.setButtonMaskEx(adapter.getButtonMaskEx() | mask);
      if (ActionW.WINLEVEL.cmd().equals(action)) {
        MouseActionAdapter win = getMouseAdapter(ActionW.WINDOW.cmd());
        if (win != null) {
          win.setButtonMaskEx(win.getButtonMaskEx() | mask);
        }
      }
    }
  }

  default void resetMouseAdapter() {
    for (ActionState adapter : getEventManager().getAllActionValues()) {
      if (adapter instanceof MouseActionAdapter mouseActionAdapter) {
        mouseActionAdapter.setButtonMaskEx(0);
      }
    }
  }

  default void addMouseAdapter(String actionName, int buttonMask) {
    MouseActionAdapter adapter = getMouseAdapter(actionName);
    if (adapter == null) {
      return;
    }
    JComponent c = getJComponent();
    adapter.setButtonMaskEx(adapter.getButtonMaskEx() | buttonMask);
    if (adapter instanceof GraphicMouseHandler) {
      c.addKeyListener(getDrawingsKeyListeners());
    } else if (adapter instanceof PannerListener pannerListener) {
      pannerListener.reset();
      c.addKeyListener(pannerListener);
    }

    if (actionName.equals(ActionW.WINLEVEL.cmd())) {
      // For window/level action set window action on x-axis
      MouseActionAdapter win = getAction(ActionW.WINDOW);
      if (win != null) {
        win.setButtonMaskEx(win.getButtonMaskEx() | buttonMask);
        win.setMoveOnX(true);
        c.addMouseListener(win);
        c.addMouseMotionListener(win);
      }
      // set level action with inverse progression (moving the cursor down will decrease the values)
      adapter.setInverse(
          getEventManager().getOptions().getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
    } else if (actionName.equals(ActionW.WINDOW.cmd())) {
      adapter.setMoveOnX(false);
    } else if (actionName.equals(ActionW.LEVEL.cmd())) {
      adapter.setInverse(
          getEventManager().getOptions().getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
    }
    c.addMouseListener(adapter);
    c.addMouseMotionListener(adapter);
  }

  default void disableMouseAndKeyListener(Component c) {
    MouseListener[] listener = c.getMouseListeners();

    MouseMotionListener[] motionListeners = c.getMouseMotionListeners();
    KeyListener[] keyListeners = c.getKeyListeners();
    MouseWheelListener[] wheelListeners = c.getMouseWheelListeners();
    for (MouseListener mouseListener : listener) {
      c.removeMouseListener(mouseListener);
    }
    for (MouseMotionListener motionListener : motionListeners) {
      c.removeMouseMotionListener(motionListener);
    }
    for (KeyListener keyListener : keyListeners) {
      c.removeKeyListener(keyListener);
    }
    for (MouseWheelListener wheelListener : wheelListeners) {
      c.removeMouseWheelListener(wheelListener);
    }
  }

  default void enableMouseAndKeyListener(MouseActions actions) {
    disableMouseAndKeyListener();
    iniDefaultMouseListener();
    iniDefaultKeyListener();
    // Set the buttonMask to 0 of all the actions
    resetMouseAdapter();

    getJComponent().setCursor(DefaultView2d.DEFAULT_CURSOR);

    addMouseAdapter(actions.getLeft(), InputEvent.BUTTON1_DOWN_MASK); // left mouse button
    if (actions.getMiddle().equals(actions.getLeft())) {
      // If mouse action is already registered, only add the modifier mask
      addModifierMask(actions.getMiddle(), InputEvent.BUTTON2_DOWN_MASK);
    } else {
      addMouseAdapter(actions.getMiddle(), InputEvent.BUTTON2_DOWN_MASK); // middle mouse button
    }
    if (actions.getRight().equals(actions.getLeft())
        || actions.getRight().equals(actions.getMiddle())) {
      // If mouse action is already registered, only add the modifier mask
      addModifierMask(actions.getRight(), InputEvent.BUTTON3_DOWN_MASK);
    } else {
      addMouseAdapter(actions.getRight(), InputEvent.BUTTON3_DOWN_MASK); // right mouse button
    }
    getJComponent().addMouseWheelListener(getMouseAdapter(actions.getWheel()));
  }

  MouseActionAdapter getMouseAdapter(String command);

  default boolean isDrawActionActive() {
    ViewerPlugin<?> container = WinUtil.getParentOfClass(getJComponent(), ViewerPlugin.class);
    if (container != null) {
      final ViewerToolBar<?> toolBar = container.getViewerToolBar();
      if (toolBar != null) {
        return toolBar.isCommandActive(ActionW.MEASURE.cmd())
            || toolBar.isCommandActive(ActionW.DRAW.cmd());
      }
    }
    return false;
  }

  void registerDefaultListeners();

  void copyActionWState(HashMap<String, Object> actionsInView);

  ImageViewerEventManager<E> getEventManager();

  void updateSynchState();

  PixelInfo getPixelInfo(Point p);

  Panner<E> getPanner();

  void setSeries(MediaSeries<E> series);

  void setSeries(MediaSeries<E> newSeries, E selectedMedia);

  default void setFocused(Boolean focused) {
    boolean isFocused = LangUtil.getNULLtoFalse(focused);
    MediaSeries<E> series = getSeries();
    if (series != null) {
      series.setFocused(isFocused);
    }
    if (isFocused && getBorder() == viewBorder) {
      setBorder(focusBorder);
    } else if (!isFocused && getBorder() == focusBorder) {
      setBorder(viewBorder);
    }

    if (isFocused) {
      if (WinUtil.getParentOfClass(getJComponent(), ViewerPlugin.class)
          instanceof ViewerPlugin<?> viewerPlugin) {
        FocusController focusController =
            UICore.getInstance().getDockingControl().getController().getFocusController();
        focusController.focus(
            new DefaultFocusRequest(viewerPlugin.getDockable().intern(), getJComponent(), true));
      } else {
        // Delay the request focus
        Timer timer = new Timer();
        timer.schedule(
            new TimerTask() {
              @Override
              public void run() {
                SwingUtilities.invokeLater(() -> getJComponent().requestFocus());
              }
            },
            500);
      }
    }
  }

  void setBorder(Border focusBorder);

  Border getBorder();

  LayerAnnotation getInfoLayer();

  int getTileOffset();

  void setTileOffset(int tileOffset);

  /** Center the image into the view. */
  void center();

  /**
   * Set the offset from the center of the view. (0,0) will center the image into the view.
   *
   * @param modelOffsetX the X-offset
   * @param modelOffsetY the Y-offset
   */
  void setCenter(Double modelOffsetX, Double modelOffsetY);

  void moveOrigin(PanPoint point);

  Comparator<E> getCurrentSortComparator();

  void setActionsInView(String action, Object value);

  void setActionsInView(String action, Object value, Boolean repaint);

  void setSelected(Boolean selected);

  default Font getFont() {
    // required when used getGraphics().getFont() in DefaultGraphicLabel
    return MeasureTool.viewSetting.getFont();
  }

  Font getLayerFont();

  void setDrawingsVisibility(Boolean visible);

  Object getLensActionValue(String action);

  void changeZoomInterpolation(Interpolation interpolation);

  OpManager getDisplayOpManager();

  void disableMouseAndKeyListener();

  void iniDefaultMouseListener();

  void iniDefaultKeyListener();

  int getPointerType();

  void setPointerType(int pointerType);

  void addPointerType(int i);

  void resetPointerType(int i);

  Point2D getHighlightedPosition();

  default void drawPointer(Graphics2D g, double x, double y, boolean circle) {
    Object[] oldRenderingHints = GuiUtils.setRenderingHints(g, true, true, false);
    float[] dash = {5.0f};
    g.translate(x, y);
    g.setStroke(new BasicStroke(3.0f));
    g.setPaint(pointerColor1);
    if (circle) {
      g.draw(pointerCircle);
    }
    g.draw(pointerLeft);
    g.draw(pointerRight);
    g.draw(pointerUp);
    g.draw(pointerDown);

    g.setStroke(
        new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 5.0f, dash, 0.0f));
    g.setPaint(pointerColor2);
    if (circle) {
      g.draw(pointerCircle);
    }
    g.draw(pointerLeft);
    g.draw(pointerRight);
    g.draw(pointerUp);
    g.draw(pointerDown);
    g.translate(-x, -y);
    GuiUtils.resetRenderingHints(g, oldRenderingHints);
  }

  List<Action> getExportActions();

  void resetZoom();

  void resetPan();

  void reset();

  List<ViewButton> getViewButtons();

  void closeLens();

  void updateCanvas(boolean triggerViewModelChangeListeners);

  void updateGraphicSelectionListener(ImageViewerPlugin<E> viewerPlugin);

  boolean requiredTextAntialiasing();

  JPopupMenu buildGraphicContextMenu(MouseEvent evt, List<Graphic> selected);

  JPopupMenu buildContextMenu(MouseEvent evt);

  default JPopupMenu buildLeftMouseActionMenu() {
    JPopupMenu popupMenu = new JPopupMenu();
    TitleMenuItem itemTitle =
        new TitleMenuItem(Messages.getString("left.mouse.actions") + StringUtil.COLON);
    popupMenu.add(itemTitle);
    popupMenu.setLabel(MouseActions.T_LEFT);
    ImageViewerEventManager<E> eventManager = getEventManager();
    String action = eventManager.getMouseActions().getLeft();
    ButtonGroup groupButtons = new ButtonGroup();
    int count = popupMenu.getComponentCount();
    ImageViewerPlugin<E> view = eventManager.getSelectedView2dContainer();
    if (view != null) {
      final ViewerToolBar<?> toolBar = view.getViewerToolBar();
      if (toolBar != null) {
        ActionListener leftButtonAction =
            event -> {
              if (event.getSource() instanceof JRadioButtonMenuItem item) {
                toolBar.changeButtonState(MouseActions.T_LEFT, item.getActionCommand());
              }
            };

        List<Feature<?>> actionsButtons = ViewerToolBar.actionsButtons;
        synchronized (actionsButtons) {
          for (Feature<?> b : actionsButtons) {
            if (eventManager.isActionEnabled(b)) {
              JRadioButtonMenuItem radio =
                  new JRadioButtonMenuItem(b.getTitle(), b.getIcon(), b.cmd().equals(action));
              GuiUtils.applySelectedIconEffect(radio);
              radio.setActionCommand(b.cmd());
              radio.setAccelerator(KeyStroke.getKeyStroke(b.getKeyCode(), b.getModifier()));
              // Trigger the selected mouse action
              radio.addActionListener(toolBar);
              // Update the state of the button in the toolbar
              radio.addActionListener(leftButtonAction);
              popupMenu.add(radio);
              groupButtons.add(radio);
            }
          }
        }
      }
    }

    if (count < popupMenu.getComponentCount()) {
      popupMenu.add(new JSeparator());
    }
    return popupMenu;
  }

  default int addSeparatorToPopupMenu(JPopupMenu popupMenu, int count) {
    if (count < popupMenu.getComponentCount()) {
      popupMenu.add(new JSeparator());
      return popupMenu.getComponentCount();
    }
    return count;
  }

  boolean hasValidContent();

  default void drawPointer(Graphics2D g, int pointerType) {
    if (pointerType < 1) {
      return;
    }
    if ((pointerType & CENTER_POINTER) == CENTER_POINTER) {
      drawPointer(
          g,
          (getJComponent().getWidth() - 1) * 0.5,
          (getJComponent().getHeight() - 1) * 0.5,
          false);
    }
    if ((pointerType & HIGHLIGHTED_POINTER) == HIGHLIGHTED_POINTER
        && highlightedPosition.isHighlightedPosition()) {
      // Display the position in the center of the pixel (constant position even with a high zoom
      // factor)
      double offsetX =
          modelToViewLength(highlightedPosition.getX() + 0.5 - getViewModel().getModelOffsetX());
      double offsetY =
          modelToViewLength(highlightedPosition.getY() + 0.5 - getViewModel().getModelOffsetY());
      drawPointer(g, offsetX, offsetY, true);
    }
  }

  default void defaultKeyPressed(ImageViewerEventManager<?> eventManager, KeyEvent e) {
    if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_SPACE) {
      eventManager.nextLeftMouseAction();
    } else if (e.getModifiers() == 0
        && (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_I)) {
      eventManager.fireSeriesViewerListeners(
          new SeriesViewerEvent(
              eventManager.getSelectedView2dContainer(), null, null, EVENT.TOGGLE_INFO));
    } else if (e.getKeyCode() == KeyEvent.VK_F11) {
      ImageViewerPlugin<E> c = (ImageViewerPlugin<E>) eventManager.getSelectedView2dContainer();
      if (c != null) {
        c.maximizedSelectedImagePane(c.getSelectedImagePane(), null);
      }
    } else if (e.isAltDown() && e.getKeyCode() == KeyEvent.VK_L) {
      // Counterclockwise
      eventManager
          .getAction(ActionW.ROTATION)
          .ifPresent(a -> a.setSliderValue((a.getSliderValue() + 270) % 360));
    } else if (e.isAltDown() && e.getKeyCode() == KeyEvent.VK_R) {
      // Clockwise
      eventManager
          .getAction(ActionW.ROTATION)
          .ifPresent(a -> a.setSliderValue((a.getSliderValue() + 90) % 360));
    } else if (e.isAltDown() && e.getKeyCode() == KeyEvent.VK_F) {
      // Flip horizontal
      eventManager.getAction(ActionW.FLIP).ifPresent(f -> f.setSelected(!f.isSelected()));
    } else {
      Optional<Feature<? extends ActionState>> feature =
          eventManager.getLeftMouseActionFromKeyEvent(e.getKeyCode(), e.getModifiers());
      if (feature.isPresent()) {
        eventManager.changeLeftMouseAction(feature.get().cmd());
      } else {
        eventManager.keyPressed(e);
      }
    }
  }
}
