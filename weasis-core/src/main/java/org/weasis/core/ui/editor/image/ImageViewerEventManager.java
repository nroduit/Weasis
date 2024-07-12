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

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.BoundedRangeModel;
import javax.swing.SwingUtilities;
import javax.swing.event.SwingPropertyChangeSupport;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.SliderCineListener.TIME;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.launcher.Launcher;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.core.ui.pref.ZoomSetting;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.PrintDialog;

public abstract class ImageViewerEventManager<E extends ImageElement> implements KeyListener {
  public static final int WINDOW_SMALLEST = 0;
  public static final int WINDOW_LARGEST = 4096;
  public static final int WINDOW_DEFAULT = 700;
  public static final int LEVEL_SMALLEST = 0;
  public static final int LEVEL_LARGEST = 4096;
  public static final int LEVEL_DEFAULT = 300;

  protected final ArrayList<SeriesViewerListener> seriesViewerListeners = new ArrayList<>();
  protected final MouseActions mouseActions = new MouseActions(null);
  protected final ZoomSetting zoomSetting = new ZoomSetting();
  protected final WProperties options = new WProperties();
  // Manages all PropertyChangeListeners in EDT
  protected final SwingPropertyChangeSupport propertySupport = new SwingPropertyChangeSupport(this);
  protected final HashMap<Feature<? extends ActionState>, ActionState> actions = new HashMap<>();

  protected volatile boolean enabledAction = true;
  protected ImageViewerPlugin<E> selectedView2dContainer;

  protected ImageViewerEventManager() {
    super();
  }

  public void setAction(ActionState action) {
    actions.put(action.getActionW(), action);
  }

  public void removeAction(Feature<?> action) {
    actions.remove(action);
  }

  protected SliderCineListener getMoveTroughSliceAction(
      double speed, final TIME time, double mouseSensitivity) {
    return new SliderCineListener(ActionW.SCROLL_SERIES, 1, 2, 1, speed, time, mouseSensitivity) {

      @Override
      public void stateChanged(BoundedRangeModel model) {

        ViewCanvas<ImageElement> view2d = null;
        Series<ImageElement> series = null;
        SynchCineEvent mediaEvent = null;
        ImageElement image = null;

        if (selectedView2dContainer != null) {
          view2d = (ViewCanvas<ImageElement>) selectedView2dContainer.getSelectedImagePane();
        }

        if (view2d != null && view2d.getSeries() instanceof Series) {
          series = (Series<ImageElement>) view2d.getSeries();
          if (series != null) {
            // Model contains display value, value-1 is the index value of a sequence
            int index = model.getValue() - 1;
            image =
                series.getMedia(
                    index,
                    (Filter<ImageElement>) view2d.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                    view2d.getCurrentSortComparator());
            mediaEvent = new SynchCineEvent(view2d, image, index);
            // Ensure to load image before calling the default preset (requires pixel min and max)
            if (image != null && !image.isImageAvailable()) {
              image.getImage();
            }
          }
        }

        firePropertyChange(ActionW.SYNCH.cmd(), null, mediaEvent);
        if (image != null) {
          fireSeriesViewerListeners(
              new SeriesViewerEvent(selectedView2dContainer, series, image, EVENT.SELECT));
        }
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        setSliderValue(getSliderValue() + e.getWheelRotation());
      }
    };
  }

  protected ToggleButtonListener newLoopSweepAction() {
    return new ToggleButtonListener(ActionW.CINE_SWEEP, false) {

      @Override
      public void actionPerformed(boolean selected) {
        getAction(ActionW.SCROLL_SERIES).ifPresent(a -> a.setSweeping(selected));
      }
    };
  }

  protected SliderChangeListener newWindowAction() {

    return new SliderChangeListener(
        ActionW.WINDOW, WINDOW_SMALLEST, WINDOW_LARGEST, WINDOW_DEFAULT, true, 1.25) {

      @Override
      public void stateChanged(BoundedRangeModel model) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(
                getSelectedViewPane(), getActionW().cmd(), toModelValue(model.getValue())));
        if (selectedView2dContainer != null) {
          fireSeriesViewerListeners(
              new SeriesViewerEvent(selectedView2dContainer, null, null, EVENT.WIN_LEVEL));
        }
      }
    };
  }

  protected SliderChangeListener newLevelAction() {
    return new SliderChangeListener(
        ActionW.LEVEL, LEVEL_SMALLEST, LEVEL_LARGEST, LEVEL_DEFAULT, true, 1.25) {

      @Override
      public void stateChanged(BoundedRangeModel model) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(
                getSelectedViewPane(), getActionW().cmd(), toModelValue(model.getValue())));
        if (selectedView2dContainer != null) {
          fireSeriesViewerListeners(
              new SeriesViewerEvent(selectedView2dContainer, null, null, EVENT.WIN_LEVEL));
        }
      }
    };
  }

  protected SliderChangeListener newRotateAction() {
    return new SliderChangeListener(ActionW.ROTATION, 0, 360, 0, true, 0.25) {

      @Override
      public void stateChanged(BoundedRangeModel model) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), getActionW().cmd(), model.getValue()));
      }

      @Override
      public String getValueToDisplay() {
        return getSliderValue() + " °";
      }
    };
  }

  protected SliderChangeListener newZoomAction() {

    return new SliderChangeListener(
        ActionW.ZOOM, DefaultViewModel.SCALE_MIN, DefaultViewModel.SCALE_MAX, 1.0, true, 0.3, 300) {

      @Override
      public void stateChanged(BoundedRangeModel model) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(
                getSelectedViewPane(), getActionW().cmd(), toModelValue(model.getValue())));
      }

      @Override
      public String getValueToDisplay() {
        return DecFormatter.percentTwoDecimal(getRealValue());
      }

      @Override
      public int toSliderValue(double viewScale) {
        double v = Math.log(viewScale) / Math.log(DefaultViewModel.SCALE_MAX) * getSliderMax();
        return (int) Math.round(v);
      }

      @Override
      public double toModelValue(int sliderValue) {
        double v = sliderValue / (double) getSliderMax();
        double viewScale = Math.exp(v * Math.log(DefaultViewModel.SCALE_MAX));
        return roundAndCropViewScale(
            viewScale, DefaultViewModel.SCALE_MIN, DefaultViewModel.SCALE_MAX);
      }
    };
  }

  protected PannerListener newPanAction() {
    return new PannerListener(ActionW.PAN, null) {

      @Override
      public void pointChanged(Point2D point) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), getActionW().cmd(), point));
      }
    };
  }

  protected CrosshairListener newCrosshairAction() {
    return new CrosshairListener(ActionW.CROSSHAIR, null) {

      @Override
      public void pointChanged(PanPoint point) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), getActionW().cmd(), point));
      }
    };
  }

  protected ToggleButtonListener newFlipAction() {
    return new ToggleButtonListener(ActionW.FLIP, false) {

      @Override
      public void actionPerformed(boolean selected) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
      }
    };
  }

  protected ToggleButtonListener newInverseLutAction() {
    return new ToggleButtonListener(ActionW.INVERT_LUT, false) {

      @Override
      public void actionPerformed(boolean selected) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
        if (selectedView2dContainer != null) {
          fireSeriesViewerListeners(
              new SeriesViewerEvent(selectedView2dContainer, null, null, EVENT.LUT));
        }
      }
    };
  }

  protected ToggleButtonListener newLensAction() {
    return new ToggleButtonListener(ActionW.LENS, false) {

      @Override
      public void actionPerformed(boolean selected) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
      }
    };
  }

  protected SliderChangeListener newLensZoomAction() {
    return new SliderChangeListener(
        ActionW.LENS_ZOOM,
        DefaultViewModel.SCALE_MIN,
        DefaultViewModel.SCALE_MAX,
        2.0,
        true,
        0.3,
        300) {

      @Override
      public void stateChanged(BoundedRangeModel model) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(
                getSelectedViewPane(), getActionW().cmd(), toModelValue(model.getValue())));
      }

      @Override
      public String getValueToDisplay() {
        return DecFormatter.percentTwoDecimal(getRealValue());
      }

      @Override
      public int toSliderValue(double viewScale) {
        double v = Math.log(viewScale) / Math.log(DefaultViewModel.SCALE_MAX) * getSliderMax();
        return (int) Math.round(v);
      }

      @Override
      public double toModelValue(int sliderValue) {
        double v = sliderValue / (double) getSliderMax();
        double viewScale = Math.exp(v * Math.log(DefaultViewModel.SCALE_MAX));
        return roundAndCropViewScale(
            viewScale, DefaultViewModel.SCALE_MIN, DefaultViewModel.SCALE_MAX);
      }
    };
  }

  protected ComboItemListener<GridBagLayoutModel> newLayoutAction(GridBagLayoutModel[] layouts) {
    return new ComboItemListener<>(
        ActionW.LAYOUT, Optional.ofNullable(layouts).orElseGet(() -> new GridBagLayoutModel[0])) {

      @Override
      public void itemStateChanged(Object object) {
        if (selectedView2dContainer != null
            && object instanceof GridBagLayoutModel gridBagLayoutModel) {
          // change layout
          clearAllPropertyChangeListeners();
          ViewCanvas<E> view = selectedView2dContainer.getSelectedImagePane();
          selectedView2dContainer.setLayoutModel(gridBagLayoutModel);
          if (!selectedView2dContainer.isContainingView(view)) {
            view = selectedView2dContainer.getSelectedImagePane();
          }
          selectedView2dContainer.setSelectedImagePane(view);
          getAction(ActionW.SYNCH)
              .ifPresent(
                  s -> selectedView2dContainer.setSynchView((SynchView) s.getSelectedItem()));
        }
      }
    };
  }

  protected ComboItemListener<SynchView> newSynchAction(SynchView[] synchViewList) {
    return new ComboItemListener<>(
        ActionW.SYNCH, Optional.ofNullable(synchViewList).orElseGet(() -> new SynchView[0])) {

      @Override
      public void itemStateChanged(Object object) {
        if (object instanceof SynchView synchView && selectedView2dContainer != null) {
          selectedView2dContainer.setSynchView(synchView);
        }
      }
    };
  }

  protected ToggleButtonListener newInverseStackAction() {
    return new ToggleButtonListener(ActionW.INVERSE_STACK, false) {

      @Override
      public void actionPerformed(boolean selected) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
      }
    };
  }

  protected static ComboItemListener<Graphic> newMeasurementAction(Graphic[] graphics) {
    return new ComboItemListener<>(
        ActionW.DRAW_MEASURE, Optional.ofNullable(graphics).orElseGet(() -> new Graphic[0])) {

      @Override
      public void itemStateChanged(Object object) {
        // Do nothing
      }
    };
  }

  protected static ComboItemListener<Graphic> newDrawAction(Graphic[] graphics) {
    return new ComboItemListener<>(
        ActionW.DRAW_GRAPHICS, Optional.ofNullable(graphics).orElseGet(() -> new Graphic[0])) {

      @Override
      public void itemStateChanged(Object object) {
        // Do nothing
      }
    };
  }

  protected ToggleButtonListener newDrawOnlyOnceAction() {
    return new ToggleButtonListener(ActionW.DRAW_ONLY_ONCE, true) {

      @Override
      public void actionPerformed(boolean selected) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
        MeasureTool.viewSetting.setDrawOnlyOnce(selected);
      }
    };
  }

  protected ComboItemListener<Unit> newSpatialUnit(Unit[] units) {
    return new ComboItemListener<>(
        ActionW.SPATIAL_UNIT, Optional.ofNullable(units).orElseGet(() -> new Unit[0])) {

      @Override
      public void itemStateChanged(Object object) {
        firePropertyChange(
            ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(), object));
      }
    };
  }

  public abstract boolean updateComponentsListener(ViewCanvas<E> viewCanvas);

  public static double roundAndCropViewScale(
      double viewScale, double minViewScale, double maxViewScale) {
    double ratio = viewScale;
    ratio *= 1000.0;
    double v = Math.floor(ratio);
    if (ratio - v >= 0.5) {
      v += 0.5;
    }
    ratio = v / 1000.0;
    if (ratio < minViewScale) {
      ratio = minViewScale;
    }
    if (ratio > maxViewScale) {
      ratio = maxViewScale;
    }
    return ratio;
  }

  /** Fire property change event. */
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    propertySupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  /** Add a property change listener. */
  public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    propertySupport.addPropertyChangeListener(propertyName, listener);
  }

  /** Remove a property change listener. */
  public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    propertySupport.removePropertyChangeListener(propertyName, listener);
  }

  public void addSeriesViewerListener(SeriesViewerListener listener) {
    if (!seriesViewerListeners.contains(listener)) {
      seriesViewerListeners.add(listener);
    }
  }

  /** Remove a property change listener. */
  public void removeSeriesViewerListener(SeriesViewerListener listener) {
    seriesViewerListeners.remove(listener);
  }

  public void fireSeriesViewerListeners(SeriesViewerEvent event) {
    if (event != null) {
      for (SeriesViewerListener listener : seriesViewerListeners) {
        listener.changingViewContentEvent(event);
      }
    }
  }

  public void clearAllPropertyChangeListeners() {
    PropertyChangeListener[] changeListeners = propertySupport.getPropertyChangeListeners();
    for (PropertyChangeListener propertyChangeListener : changeListeners) {
      propertySupport.removePropertyChangeListener(propertyChangeListener);
    }
  }

  public <T> Optional<T> getAction(Feature<T> feature) {
    if (feature != null) {
      return Optional.ofNullable((T) actions.get(feature));
    }
    return Optional.empty();
  }

  public boolean isActionRegistered(Feature<?> feature) {
    if (feature != null) {
      return actions.containsKey(feature);
    }
    return false;
  }

  public boolean isActionEnabled(Feature<?> feature) {
    if (feature != null) {
      ActionState action = actions.get(feature);
      if (action != null) {
        return action.isActionEnabled();
      }
    }
    return false;
  }

  public Optional<Feature<? extends ActionState>> getActionKey(String command) {
    if (command == null) {
      return Optional.empty();
    }
    return actions.keySet().stream().filter(k -> k.cmd().equals(command)).findFirst();
  }

  public <T> Optional<T> getActionFromActionKey(String command, Class<T> type) {
    if (command == null) {
      return Optional.empty();
    }
    Objects.requireNonNull(type);

    return actions.keySet().stream()
        .filter(k -> k.cmd().equals(command))
        .findFirst()
        .map(actions::get)
        .filter(type::isInstance)
        .map(type::cast);
  }

  public Optional<Feature<? extends ActionState>> getLeftMouseActionFromKeyEvent(
      int keyEvent, int modifier) {
    if (keyEvent == 0) {
      return Optional.empty();
    }
    return actions.keySet().stream()
        .filter(k -> k != null && k.getKeyCode() == keyEvent && k.getModifier() == modifier)
        .findFirst();
  }

  public void changeLeftMouseAction(String command) {
    ImageViewerPlugin<E> view = getSelectedView2dContainer();
    if (view != null) {
      ViewerToolBar<E> toolBar = view.getViewerToolBar();
      if (toolBar != null) {
        MouseActions mActions = getMouseActions();
        if (!command.equals(mActions.getAction(MouseActions.T_LEFT))) {
          mActions.setAction(MouseActions.T_LEFT, command);
          view.setMouseActions(mActions);
          toolBar.changeButtonState(MouseActions.T_LEFT, command);
        }
      }
    }
  }

  public void nextLeftMouseAction() {
    ImageViewerPlugin<E> view = getSelectedView2dContainer();
    if (view != null) {
      @SuppressWarnings("unchecked")
      ViewerToolBar<E> toolBar = view.getViewerToolBar();
      if (toolBar != null) {
        String command =
            ViewerToolBar.getNextCommand(
                    ViewerToolBar.actionsButtons, toolBar.getMouseLeft().getActionCommand())
                .cmd();
        changeLeftMouseAction(command);
      }
    }
  }

  public Optional<Feature<? extends ActionState>> getMouseAction(int modifiers) {
    Optional<Feature<? extends ActionState>> action = Optional.empty();
    // left mouse button, always active
    if ((modifiers & InputEvent.BUTTON1_DOWN_MASK) != 0) {
      action = getActionKey(mouseActions.getLeft());
    }
    // middle mouse button
    else if ((modifiers & InputEvent.BUTTON2_DOWN_MASK) != 0
        && ((mouseActions.getActiveButtons() & InputEvent.BUTTON2_DOWN_MASK) != 0)) {
      action = getActionKey(mouseActions.getMiddle());
    }
    // right mouse button
    else if ((modifiers & InputEvent.BUTTON3_DOWN_MASK) != 0
        && ((mouseActions.getActiveButtons() & InputEvent.BUTTON3_DOWN_MASK) != 0)) {
      action = getActionKey(mouseActions.getRight());
    }
    return action;
  }

  public Collection<ActionState> getAllActionValues() {
    return actions.values().stream().filter(Objects::nonNull).toList();
  }

  public MouseActions getMouseActions() {
    return mouseActions;
  }

  public ZoomSetting getZoomSetting() {
    return zoomSetting;
  }

  public WProperties getOptions() {
    return options;
  }

  public synchronized void enableActions(boolean enabled) {
    enabledAction = enabled;
    for (ActionState a : getAllActionValues()) {
      a.enableAction(enabled);
    }
  }

  public ViewCanvas<E> getSelectedViewPane() {
    ImageViewerPlugin<E> container = selectedView2dContainer;
    if (container != null) {
      return container.getSelectedImagePane();
    }
    return null;
  }

  public ImageViewerPlugin<E> getSelectedView2dContainer() {
    return selectedView2dContainer;
  }

  public boolean isSelectedView2dContainerInTileMode() {
    ImageViewerPlugin<E> container = selectedView2dContainer;
    if (container != null) {
      return SynchData.Mode.TILE.equals(container.getSynchView().getSynchData().getMode());
    }
    return false;
  }

  public void updateAllListeners(ImageViewerPlugin<E> viewerPlugin, SynchView synchView) {
    clearAllPropertyChangeListeners();
    if (viewerPlugin != null) {
      ViewCanvas<E> viewPane = viewerPlugin.getSelectedImagePane();
      if (viewPane == null) {
        return;
      }
      if (viewPane.getSeries() != null) {
        SynchData synch = synchView.getSynchData();
        viewPane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
        addPropertyChangeListener(ActionW.SYNCH.cmd(), viewPane);

        final List<ViewCanvas<E>> panes = viewerPlugin.getImagePanels();
        panes.remove(viewPane);
        if (SynchView.NONE.equals(synchView)) {
          for (ViewCanvas<E> pane : panes) {
            pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), synch);
          }
        } else if (Mode.STACK.equals(synch.getMode())) {
          // TODO if Pan is activated than rotation is required
          boolean hasLink = false;
          for (ViewCanvas<E> pane : panes) {
            boolean synchByDefault = isCompatible(viewPane.getSeries(), pane.getSeries());
            pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), synchByDefault ? synch.copy() : null);
            if (synchByDefault) {
              hasLink = true;
              addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
            }
          }
        } else if (Mode.TILE.equals(synch.getMode())) {
          for (ViewCanvas<E> pane : panes) {
            pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), synch.copy());
            addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
          }
        }
      }
    }
  }

  protected boolean commonDisplayShortcuts(KeyEvent e) {
    int keyEvent = e.getKeyCode();
    int modifiers = e.getModifiers();

    if (keyEvent == KeyEvent.VK_ESCAPE) {
      resetDisplay();
    } else if (keyEvent == ActionW.CINESTART.getKeyCode()
        && ActionW.CINESTART.getModifier() == modifiers) {
      Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES);
      if (cineAction.isPresent() && cineAction.get().isActionEnabled()) {
        if (cineAction.get().isCining()) {
          cineAction.get().stop();
        } else {
          cineAction.get().start();
        }
      }
    } else if (keyEvent == KeyEvent.VK_P && modifiers == 0) {
      ImageViewerPlugin<? extends ImageElement> view = getSelectedView2dContainer();
      if (view != null) {
        ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(view);
        PrintDialog<?> dialog =
            new PrintDialog<>(
                SwingUtilities.getWindowAncestor(view),
                Messages.getString("ImageViewerPlugin.print_layout"),
                this);
        ColorLayerUI.showCenterScreen(dialog, layer);
      }
    } else if (keyEvent == KeyEvent.VK_UP && !e.isAltDown() && !e.isControlDown()) {
      int shift = e.isShiftDown() ? 10 : 1;
      getAction(ActionW.SCROLL_SERIES).ifPresent(a -> a.setSliderValue(a.getSliderValue() - shift));
    } else if (keyEvent == KeyEvent.VK_DOWN && !e.isAltDown() && !e.isControlDown()) {
      int shift = e.isShiftDown() ? 10 : 1;
      getAction(ActionW.SCROLL_SERIES).ifPresent(a -> a.setSliderValue(a.getSliderValue() + shift));
    } else if (keyEvent == KeyEvent.VK_HOME && !e.isControlDown()) {
      getAction(ActionW.SCROLL_SERIES).ifPresent(a -> a.setSliderValue(a.getSliderMin()));
    } else if (keyEvent == KeyEvent.VK_END && !e.isControlDown()) {
      getAction(ActionW.SCROLL_SERIES).ifPresent(a -> a.setSliderValue(a.getSliderMax()));
    } else if (keyEvent == KeyEvent.VK_SUBTRACT && e.isControlDown()) {
      getAction(ActionW.ZOOM).ifPresent(a -> a.setSliderValue(a.getSliderValue() - 1));
    } else if (keyEvent == KeyEvent.VK_ADD && e.isControlDown()) {
      getAction(ActionW.ZOOM).ifPresent(a -> a.setSliderValue(a.getSliderValue() + 1));
    } else if (keyEvent == KeyEvent.VK_ENTER && e.isControlDown()) {
      firePropertyChange(
          ActionW.SYNCH.cmd(),
          null,
          new SynchEvent(getSelectedViewPane(), ActionW.ZOOM.cmd(), -200.0));
    } else {
      return false;
    }
    return true;
  }

  protected void triggerDrawingToolKeyEvent(int keyEvent, int modifiers) {
    triggerDrawActionKeyEvent(ActionW.DRAW_MEASURE, ActionW.MEASURE.cmd(), keyEvent, modifiers);
    triggerDrawActionKeyEvent(ActionW.DRAW_GRAPHICS, ActionW.DRAW.cmd(), keyEvent, modifiers);
  }

  private void triggerDrawActionKeyEvent(
      Feature<? extends ComboItemListener<Graphic>> action,
      String cmd,
      int keyEvent,
      int modifiers) {
    Optional<? extends ComboItemListener<Graphic>> drawAction = getAction(action);
    if (drawAction.isPresent() && drawAction.get().isActionEnabled()) {
      for (Object obj : drawAction.get().getAllItem()) {
        if (obj instanceof Graphic g) {
          if (g.getKeyCode() == keyEvent && g.getModifier() == modifiers) {
            ImageViewerPlugin<E> view = getSelectedView2dContainer();
            if (view != null) {
              final ViewerToolBar<?> toolBar = view.getViewerToolBar();
              if (toolBar != null && !toolBar.isCommandActive(cmd)) {
                mouseActions.setAction(MouseActions.T_LEFT, cmd);
                view.setMouseActions(mouseActions);
                toolBar.changeButtonState(MouseActions.T_LEFT, cmd);
              }
            }
            drawAction.get().setSelectedItem(obj);
            return;
          }
        }
      }
    }
  }

  protected boolean isCompatible(MediaSeries<E> series, MediaSeries<E> series2) {
    return true;
  }

  public void setSelectedView2dContainer(ImageViewerPlugin<E> selectedView2dContainer) {
    if (this.selectedView2dContainer != null) {
      this.selectedView2dContainer.setMouseActions(null);
    }

    this.selectedView2dContainer = selectedView2dContainer;
    if (selectedView2dContainer != null) {
      selectedView2dContainer.setMouseActions(mouseActions);
    }
  }

  public abstract void resetDisplay();

  public String resolvePlaceholders(String template) {
    return template;
  }

  public void dicomExportAction(Launcher launcher) {
    // Do nothing
  }
}
