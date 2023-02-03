/*
 * Copyright (c) 2013 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.swing.BoundedRangeModel;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.BasicActionState;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.PannerListener;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.ResetTools;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer3d.vr.DicomVolTexture;
import org.weasis.dicom.viewer3d.vr.Preset;
import org.weasis.dicom.viewer3d.vr.PresetRadioMenu;
import org.weasis.dicom.viewer3d.vr.RenderingLayer;
import org.weasis.dicom.viewer3d.vr.RenderingType;
import org.weasis.dicom.viewer3d.vr.View3d;
import org.weasis.dicom.viewer3d.vr.View3d.ViewType;
import org.weasis.opencv.op.lut.LutShape;

public class EventManager extends ImageViewerEventManager<DicomImageElement> {

  private static final int MIP_DEPTH_DEFAULT = 5;
  private static final int MIP_DEPTH_MAX = 100;

  private static EventManager instance;

  public static synchronized EventManager getInstance() {
    if (instance == null) {
      instance = new EventManager();
    }
    return instance;
  }

  private EventManager() {
    // Initialize actions with a null value. These are used by mouse or keyevent actions.
    setAction(new BasicActionState(ActionW.WINLEVEL));
    setAction(new BasicActionState(ActionW.CONTEXTMENU));
    setAction(new BasicActionState(ActionW.NO_ACTION));
    setAction(new BasicActionState(ActionW.DRAW));
    setAction(new BasicActionState(ActionW.MEASURE));

    setAction(newScrollSeries());
    setAction(newVolumeQuality());
    setAction(newWindowAction());
    setAction(newLevelAction());
    setAction(newRotateAction());
    setAction(newZoomAction());
    setAction(newMipDepth());

    setAction(newFlipAction());
    setAction(newDrawOnlyOnceAction());
    setAction(newVolumeSlicing());
    setAction(newVolumeShading());

    setAction(newPresetAction());
    setAction(newLutShapeAction());
    setAction(newLutAction());
    setAction(newSortStackAction());
    setAction(newInverseStackAction());
    setAction(
        newLayoutAction(View2dContainer.DEFAULT_LAYOUT_LIST.toArray(new GridBagLayoutModel[0])));
    setAction(newSynchAction(View2dContainer.DEFAULT_SYNCH_LIST.toArray(new SynchView[0])));
    getAction(ActionW.SYNCH)
        .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(SynchView.DEFAULT_STACK));
    setAction(newMeasurementAction(MeasureToolBar.measureGraphicList.toArray(new Graphic[0])));
    setAction(newDrawAction(MeasureToolBar.drawGraphicList.toArray(new Graphic[0])));
    setAction(newSpatialUnit(Unit.values()));
    setAction(newMipOption());

    setAction(buildPanAction());
    setAction(newCrosshairAction());
    setAction(new BasicActionState(ActionW.RESET));
    setAction(new BasicActionState(ActionW.SHOW_HEADER));

    final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
    Preferences prefs = BundlePreferences.getDefaultPreferences(context);
    zoomSetting.applyPreferences(prefs);
    mouseActions.applyPreferences(prefs);

    initializeParameters();
  }

  private void initializeParameters() {
    enableActions(false);
  }

  protected PannerListener buildPanAction() {
    return new PannerListener(ActionW.PAN, null) {

      @Override
      public void pointChanged(Point2D point) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), getActionW().cmd(), point));
      }

      @Override
      public void mousePressed(MouseEvent e) {
        int buttonMask = getButtonMaskEx();
        if ((e.getModifiersEx() & buttonMask) != 0) {
          pickPoint = e.getPoint();
        }
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        int buttonMask = getButtonMaskEx();
        if (!e.isConsumed() && (e.getModifiersEx() & buttonMask) != 0) {
          ViewCanvas panner = getViewCanvas(e);
          if (panner != null) {
            if (pickPoint != null && panner.getViewModel() != null) {
              Point pt = e.getPoint();
              setPoint(
                  new PanPoint(PanPoint.State.DRAGGING, pt.x - pickPoint.x, pt.y - pickPoint.y));
              pickPoint = pt;
              panner.addPointerType(ViewCanvas.CENTER_POINTER);
            }
          }
        }
      }
    };
  }

  private SliderChangeListener newScrollSeries() {
    return new SliderChangeListener(ActionVol.SCROLLING, 1, 100, 1, true, 0.1) {
      @Override
      public void stateChanged(BoundedRangeModel model) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), getActionW().cmd(), model.getValue()));
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        setSliderValue(getSliderValue() + e.getWheelRotation());
      }
    };
  }

  private SliderChangeListener newVolumeQuality() {
    return new SliderChangeListener(ActionVol.VOL_QUALITY, 128, 8192, 1024, false) {
      @Override
      public void stateChanged(BoundedRangeModel model) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), getActionW().cmd(), model.getValue()));
      }
    };
  }

  @Override
  protected SliderChangeListener newWindowAction() {
    return new SliderChangeListener(
        ActionW.WINDOW, WINDOW_SMALLEST, WINDOW_LARGEST, WINDOW_DEFAULT, true, 1.25) {

      @Override
      public void stateChanged(BoundedRangeModel model) {
        updatePreset(getActionW().cmd(), toModelValue(model.getValue()));
      }
    };
  }

  @Override
  protected SliderChangeListener newLevelAction() {
    return new SliderChangeListener(
        ActionW.LEVEL, LEVEL_SMALLEST, LEVEL_LARGEST, LEVEL_DEFAULT, true, 1.25) {

      @Override
      public void stateChanged(BoundedRangeModel model) {
        updatePreset(getActionW().cmd(), toModelValue(model.getValue()));
      }
    };
  }

  protected void updatePreset(String cmd, Object object) {
    String command = cmd;
    final PresetWindowLevel preset;
    Optional<ComboItemListener<Object>> presetAction = getAction(ActionW.PRESET);
    if (ActionW.PRESET.cmd().equals(command)
        && object instanceof PresetWindowLevel presetWindowLevel) {
      preset = presetWindowLevel;
      getAction(ActionW.WINDOW)
          .ifPresent(a -> a.setSliderValue(a.toSliderValue(preset.getWindow()), false));
      getAction(ActionW.LEVEL)
          .ifPresent(a -> a.setSliderValue(a.toSliderValue(preset.getLevel()), false));
      getAction(ActionW.LUT_SHAPE)
          .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(preset.getLutShape()));
    } else {
      preset = (PresetWindowLevel) (object instanceof PresetWindowLevel ? object : null);
      presetAction.ifPresent(a -> a.setSelectedItemWithoutTriggerAction(preset));
    }

    SynchEvent evt = new SynchEvent(getSelectedViewPane(), command, object);
    evt.put(ActionW.PRESET.cmd(), preset);
    firePropertyChange(ActionW.SYNCH.cmd(), null, evt);
  }

  private ComboItemListener<PresetWindowLevel> newPresetAction() {
    return new ComboItemListener<>(ActionW.PRESET, null) {

      @Override
      public void itemStateChanged(Object object) {
        updatePreset(getActionW().cmd(), object);
      }
    };
  }

  private ComboItemListener<LutShape> newLutShapeAction() {
    return new ComboItemListener<>(
        ActionW.LUT_SHAPE, LutShape.DEFAULT_FACTORY_FUNCTIONS.toArray(new LutShape[0])) {

      @Override
      public void itemStateChanged(Object object) {
        updatePreset(action.cmd(), object);
      }
    };
  }

  private ComboItemListener<Preset> newLutAction() {
    return new ComboItemListener<>(
        ActionVol.VOL_PRESET, Preset.basicPresets.toArray(new Preset[0])) {

      @Override
      public void itemStateChanged(Object object) {
        firePropertyChange(
            ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(), object));
        // FIXME use dispatch instead of static access
        //        if (selectedView2dContainer != null) {
        //          fireSeriesViewerListeners(
        //              new SeriesViewerEvent(selectedView2dContainer, null, null, EVENT.LUT));
        //        }
      }
    };
  }

  private ComboItemListener<SeriesComparator<DicomImageElement>> newSortStackAction() {
    return new ComboItemListener<>(ActionW.SORT_STACK, SortSeriesStack.getValues()) {

      @Override
      public void itemStateChanged(Object object) {
        ImageViewerPlugin<DicomImageElement> container =
            EventManager.getInstance().getSelectedView2dContainer();
        if (container != null) {
          container.addSeries(EventManager.getInstance().getSelectedSeries());
        }
      }
    };
  }

  @Override
  protected ToggleButtonListener newInverseStackAction() {
    return new ToggleButtonListener(ActionW.INVERSE_STACK, false) {

      @Override
      public void actionPerformed(boolean selected) {
        ImageViewerPlugin<DicomImageElement> container =
            EventManager.getInstance().getSelectedView2dContainer();
        if (container != null) {
          container.addSeries(EventManager.getInstance().getSelectedSeries());
        }
      }
    };
  }

  private ComboItemListener<RenderingType> newMipOption() {

    return new ComboItemListener<>(ActionVol.RENDERING_TYPE, RenderingType.values()) {

      @Override
      public void itemStateChanged(Object object) {
        getAction(ActionVol.MIP_DEPTH)
            .ifPresent(a -> a.enableAction(RenderingType.MIP.equals(object)));
        firePropertyChange(
            ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(), object));
      }
    };
  }

  private SliderChangeListener newMipDepth() {
    return new SliderChangeListener(
        ActionVol.MIP_DEPTH, 2, MIP_DEPTH_MAX, MIP_DEPTH_DEFAULT, true) {

      @Override
      public void stateChanged(BoundedRangeModel model) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), getActionW().cmd(), model.getValue()));
      }
    };
  }

  private ToggleButtonListener newVolumeShading() {
    return new ToggleButtonListener(ActionVol.VOL_SHADING, false) {
      @Override
      public void actionPerformed(boolean selected) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
      }
    };
  }

  private ToggleButtonListener newVolumeSlicing() {
    return new ToggleButtonListener(ActionVol.VOL_SLICING, false) {
      @Override
      public void actionPerformed(boolean selected) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
      }
    };
  }

  public MediaSeries<DicomImageElement> getSelectedSeries() {
    ViewCanvas<DicomImageElement> pane = getSelectedViewPane();
    if (pane != null) {
      return pane.getSeries();
    }
    return null;
  }

  @Override
  public void keyTyped(KeyEvent e) {}

  @Override
  public void keyPressed(KeyEvent e) {
    int keyEvent = e.getKeyCode();
    int modifiers = e.getModifiers();

    if (keyEvent == KeyEvent.VK_ESCAPE) {
      resetDisplay();
    } else {
      Optional<ComboItemListener<Object>> presetAction = getAction(ActionW.PRESET);
      if (modifiers == 0 && presetAction.isPresent() && presetAction.get().isActionEnabled()) {
        ComboItemListener presetComboListener = presetAction.get();
        DefaultComboBoxModel model = presetComboListener.getModel();
        for (int i = 0; i < model.getSize(); i++) {
          PresetWindowLevel val = (PresetWindowLevel) model.getElementAt(i);
          if (val.getKeyCode() == keyEvent) {
            presetComboListener.setSelectedItem(val);
            return;
          }
        }
      }

      triggerDrawingToolKeyEvent(keyEvent, modifiers);
    }
  }

  @Override
  public void keyReleased(KeyEvent e) {
    // Do nothing
  }

  @Override
  public void setSelectedView2dContainer(
      ImageViewerPlugin<DicomImageElement> selectedView2dContainer) {
    if (this.selectedView2dContainer != null) {
      this.selectedView2dContainer.setMouseActions(null);
    }

    ImageViewerPlugin<DicomImageElement> oldContainer = this.selectedView2dContainer;
    this.selectedView2dContainer = selectedView2dContainer;

    if (selectedView2dContainer != null) {
      Optional<ComboItemListener<SynchView>> synchAction = getAction(ActionW.SYNCH);
      Optional<ComboItemListener<GridBagLayoutModel>> layoutAction = getAction(ActionW.LAYOUT);
      if (oldContainer == null
          || !oldContainer.getClass().equals(selectedView2dContainer.getClass())) {
        synchAction.ifPresent(
            a ->
                a.setDataListWithoutTriggerAction(
                    selectedView2dContainer.getSynchList().toArray(new SynchView[0])));
        layoutAction.ifPresent(
            a ->
                a.setDataListWithoutTriggerAction(
                    selectedView2dContainer.getLayoutList().toArray(new GridBagLayoutModel[0])));
      }
      if (oldContainer != null) {
        ViewCanvas<DicomImageElement> pane = oldContainer.getSelectedImagePane();
        if (pane != null) {
          pane.setFocused(false);
        }
      }
      synchAction.ifPresent(
          a -> a.setSelectedItemWithoutTriggerAction(selectedView2dContainer.getSynchView()));
      layoutAction.ifPresent(
          a ->
              a.setSelectedItemWithoutTriggerAction(
                  selectedView2dContainer.getOriginalLayoutModel()));
      updateComponentsListener(selectedView2dContainer.getSelectedImagePane());
      selectedView2dContainer.setMouseActions(mouseActions);
      ViewCanvas<DicomImageElement> pane = selectedView2dContainer.getSelectedImagePane();
      if (pane != null) {
        pane.setFocused(true);
        fireSeriesViewerListeners(
            new SeriesViewerEvent(
                selectedView2dContainer, pane.getSeries(), null, EVENT.SELECT_VIEW));
      }
    }
  }

  @Override
  public boolean updateComponentsListener(ViewCanvas<DicomImageElement> view2d) {
    if (view2d == null) {
      return false;
    }

    if (selectedView2dContainer == null
        || view2d != selectedView2dContainer.getSelectedImagePane()) {
      return false;
    }

    clearAllPropertyChangeListeners();
    Optional<SliderChangeListener> cineAction = getAction(ActionVol.SCROLLING);

    if (!(view2d instanceof View3d canvas) || canvas.getVolTexture() == null) {
      enableActions(false);
      return false;
    }

    if (!enabledAction) {
      enableActions(true);
    }

    RenderingLayer rendering = canvas.getRenderingLayer();
    updateWindowLevelComponentsListener(canvas);

    getAction(ActionVol.VOL_PRESET)
        .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(canvas.getVolumePreset()));

    Optional<SliderChangeListener> rotation = getAction(ActionW.ROTATION);
    rotation.ifPresent(
        a -> a.setSliderValue((int) Math.toDegrees(canvas.getCamera().getRotation().angle())));
    getAction(ActionW.FLIP)
        .ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    (Boolean) canvas.getActionValue(ActionW.FLIP.cmd())));

    getAction(ActionW.ZOOM)
        .ifPresent(
            a -> a.setRealValue(Math.abs((Double) canvas.getActionValue(ActionW.ZOOM.cmd()))));
    getAction(ActionW.SPATIAL_UNIT)
        .ifPresent(
            a ->
                a.setSelectedItemWithoutTriggerAction(
                    canvas.getActionValue(ActionW.SPATIAL_UNIT.cmd())));

    Optional<ComboItemListener<RenderingType>> mipType = getAction(ActionVol.RENDERING_TYPE);
    mipType.ifPresent(a -> a.setSelectedItemWithoutTriggerAction(rendering.getRenderingType()));

    cineAction.ifPresent(
        a ->
            a.setSliderMinMaxValue(
                1,
                canvas.getVolTexture().getDepth() // FIXME
                ,
                view2d.getFrameIndex(),
                false));

    Optional<SliderChangeListener> mipThickness = getAction(ActionVol.MIP_DEPTH);
    mipThickness.ifPresent(
        a ->
            a.setSliderMinMaxValue(
                2,
                cineAction.map(SliderChangeListener::getSliderMax).orElse(1),
                (Integer) canvas.getActionValue(ActionVol.MIP_DEPTH.cmd()),
                false));

    boolean volume = ViewType.VOLUME3D.equals(canvas.getViewType());
    Optional<ToggleButtonListener> volumeLighting = getAction(ActionVol.VOL_SHADING);
    Optional<ToggleButtonListener> volumeSlicing = getAction(ActionVol.VOL_SLICING);
    Optional<SliderChangeListener> volumeQuality = getAction(ActionVol.VOL_QUALITY);
    if (volume) {
      volumeLighting.ifPresent(a -> a.setSelectedWithoutTriggerAction(rendering.isShading()));
      volumeSlicing.ifPresent(a -> a.setSelectedWithoutTriggerAction(rendering.isSlicing()));
      volumeQuality.ifPresent(a -> a.setSliderValue(rendering.getQuality(), false));
    }
    volumeLighting.ifPresent(a -> a.enableAction(volume));
    volumeSlicing.ifPresent(a -> a.enableAction(volume));
    volumeQuality.ifPresent(a -> a.enableAction(volume));

    mipType.ifPresent(
        a ->
            mipThickness.ifPresent(
                t -> t.enableAction(!volume || RenderingType.MIP.equals(a.getSelectedItem()))));
    //   mipType.ifPresent(a -> a.enableAction(!volume));
    cineAction.ifPresent(a -> a.enableAction(!volume));
    rotation.ifPresent(a -> a.enableAction(!volume));

    getAction(ActionW.SORT_STACK)
        .ifPresent(
            a ->
                a.setSelectedItemWithoutTriggerAction(
                    view2d.getActionValue(ActionW.SORT_STACK.cmd())));
    getAction(ActionW.INVERSE_STACK)
        .ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    (Boolean) view2d.getActionValue(ActionW.INVERSE_STACK.cmd())));

    // register all actions for the selected view and for the other views register according to
    // synchview.
    ComboItemListener<SynchView> synchAction = getAction(ActionW.SYNCH).orElse(null);
    updateAllListeners(
        selectedView2dContainer,
        synchAction == null ? SynchView.NONE : (SynchView) synchAction.getSelectedItem());

    view2d.updateGraphicSelectionListener(selectedView2dContainer);
    return true;
  }

  private void updateWindowLevelComponentsListener(View3d view2d) {

    DicomVolTexture series = view2d.getVolTexture();
    if (series != null) {
      PresetWindowLevel preset = (PresetWindowLevel) view2d.getActionValue(ActionW.PRESET.cmd());
      boolean pixelPadding = true;
      Double windowValue = (double) view2d.getRenderingLayer().getWindowWidth();
      Double levelValue = (double) view2d.getRenderingLayer().getWindowCenter();
      LutShape lutShapeItem = view2d.getRenderingLayer().getLutShape();

      Optional<SliderChangeListener> windowAction = getAction(ActionW.WINDOW);
      Optional<SliderChangeListener> levelAction = getAction(ActionW.LEVEL);
      if (windowAction.isPresent() && levelAction.isPresent()) {
        double minLevel = levelValue - windowValue / 2.0;
        double maxLevel = levelValue + windowValue / 2.0;
        double window = series.getLevelMax() - series.getLevelMin();

        windowAction.get().setRealMinMaxValue(1.0, window, windowValue, false);
        levelAction.get().setRealMinMaxValue(minLevel, maxLevel, levelValue, false);
      }

      List<PresetWindowLevel> presetList = series.getPresetList(pixelPadding);

      Optional<ComboItemListener<Object>> presetAction = getAction(ActionW.PRESET);
      if (presetAction.isPresent()) {
        presetAction
            .get()
            .setDataListWithoutTriggerAction(presetList == null ? null : presetList.toArray());
        presetAction.get().setSelectedItemWithoutTriggerAction(preset);
      }

      Optional<? extends ComboItemListener<Object>> lutShapeAction = getAction(ActionW.LUT_SHAPE);
      if (lutShapeAction.isPresent()) {
        Collection<LutShape> lutShapeList = series.getLutShapeCollection(pixelPadding);
        lutShapeAction
            .get()
            .setDataListWithoutTriggerAction(lutShapeList == null ? null : lutShapeList.toArray());
        if (lutShapeItem != null) {
          lutShapeAction.get().setSelectedItemWithoutTriggerAction(lutShapeItem);
        }
      }
    }
  }

  @Override
  public void resetDisplay() {
    reset(ResetTools.ALL);
  }

  public void reset(ResetTools action) {
    AuditLog.LOGGER.info("reset action:{}", action.name());
    if (ResetTools.ALL.equals(action)) {
      firePropertyChange(
          ActionW.SYNCH.cmd(),
          null,
          new SynchEvent(getSelectedViewPane(), ActionW.RESET.cmd(), true));
    } else if (ResetTools.ZOOM.equals(action)) {
      // Pass the value 0.0 (convention: default value according the zoom type) directly to the
      // property change, otherwise the value is adjusted by the BoundedRangeModel
      firePropertyChange(
          ActionW.SYNCH.cmd(),
          null,
          new SynchEvent(getSelectedViewPane(), ActionW.ZOOM.cmd(), 0.0));
    } else if (ResetTools.WL.equals(action)) {
      getAction(ActionW.PRESET).ifPresent(a -> a.setSelectedItem(a.getFirstItem()));
    } else if (ResetTools.PAN.equals(action)) {
      if (selectedView2dContainer != null) {
        ViewCanvas viewPane = selectedView2dContainer.getSelectedImagePane();
        if (viewPane != null) {
          viewPane.resetPan();
          viewPane.getJComponent().repaint();
        }
      }
    }
  }

  @Override
  public void updateAllListeners(
      ImageViewerPlugin<DicomImageElement> viewerPlugin, SynchView synchView) {
    clearAllPropertyChangeListeners();

    if (viewerPlugin != null) {
      ViewCanvas<DicomImageElement> viewPane = viewerPlugin.getSelectedImagePane();
      // if (viewPane == null || viewPane.getSeries() == null) {
      if (!(viewPane instanceof View3d canvas) || canvas.getVolTexture() == null) {
        return;
      }
      SynchData synch = synchView.getSynchData();
      MediaSeries<DicomImageElement> series = canvas.getVolTexture().getSeries();
      if (series != null) {
        SynchData oldSynch = (SynchData) viewPane.getActionValue(ActionW.SYNCH_LINK.cmd());
        if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
          oldSynch = synch;
        }
        viewPane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
        addPropertyChangeListener(ActionW.SYNCH.cmd(), viewPane);

        final List<ViewCanvas<DicomImageElement>> panes = viewerPlugin.getImagePanels();
        panes.remove(viewPane);
        viewPane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);

        if (SynchView.NONE.equals(synchView)
            || (viewPane instanceof View3d
                && ((View3d) viewPane).getViewType() == ViewType.VOLUME3D)) {
          for (int i = 0; i < panes.size(); i++) {
            ViewCanvas<DicomImageElement> pane = panes.get(i);
            pane.getGraphicManager().deleteByLayerType(LayerType.CROSSLINES);

            oldSynch = (SynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
            if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
              oldSynch = synch;
            }
            pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), oldSynch);
            // pane.updateSynchState();
          }
        } else {
          // TODO if Pan is activated than rotation is required
          if (Mode.STACK.equals(synch.getMode())) {
            for (int i = 0; i < panes.size(); i++) {
              ViewCanvas<DicomImageElement> pane = panes.get(i);
              pane.getGraphicManager().deleteByLayerType(LayerType.CROSSLINES);

              MediaSeries<DicomImageElement> s = pane.getSeries();
              if (s != null) {
                oldSynch = (SynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
                if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
                  oldSynch = synch.copy();
                }
                if (pane instanceof View3d && ((View3d) pane).getViewType() != ViewType.VOLUME3D) {
                  addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
                }

                pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), oldSynch);
                // pane.updateSynchState();
              }
            }
          }
        }
      }

      // viewPane.updateSynchState();
    }
  }

  public JMenu getResetMenu(String prop) {
    JMenu menu = null;
    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
      ButtonGroup group = new ButtonGroup();
      menu = new JMenu(ActionW.RESET.getTitle());
      menu.setIcon(ResourceUtil.getIcon(ActionIcon.RESET));
      GuiUtils.applySelectedIconEffect(menu);
      menu.setEnabled(getSelectedSeries() != null);

      if (menu.isEnabled()) {
        for (final ResetTools action : ResetTools.values()) {
          final JMenuItem item = new JMenuItem(action.toString());
          if (ResetTools.ALL.equals(action)) {
            item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
          }
          item.addActionListener(e -> reset(action));
          menu.add(item);
          group.add(item);
        }
      }
    }
    return menu;
  }

  public JMenu getPresetMenu(String prop) {
    JMenu menu = null;
    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
      Optional<ComboItemListener<Object>> presetAction = getAction(ActionW.PRESET);
      if (presetAction.isPresent()) {
        menu =
            presetAction
                .get()
                .createUnregisteredRadioMenu(ActionW.PRESET.getTitle(), ActionW.WINLEVEL.getIcon());
        for (Component mitem : menu.getMenuComponents()) {
          RadioMenuItem ritem = (RadioMenuItem) mitem;
          PresetWindowLevel preset = (PresetWindowLevel) ritem.getUserObject();
          if (preset.getKeyCode() > 0) {
            ritem.setAccelerator(KeyStroke.getKeyStroke(preset.getKeyCode(), 0));
          }
        }
      }
    }
    return menu;
  }

  public JMenu getLutShapeMenu(String prop) {
    JMenu menu = null;
    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
      Optional<? extends ComboItemListener<?>> lutShapeAction = getAction(ActionW.LUT_SHAPE);
      if (lutShapeAction.isPresent()) {
        menu = lutShapeAction.get().createUnregisteredRadioMenu(ActionW.LUT_SHAPE.getTitle());
      }
    }
    return menu;
  }

  public JMenu getZoomMenu(String prop) {
    JMenu menu = null;
    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
      Optional<SliderChangeListener> zoomAction = getAction(ActionW.ZOOM);
      if (zoomAction.isPresent()) {
        menu = new JMenu(ActionW.ZOOM.getTitle());
        menu.setIcon(ActionW.ZOOM.getIcon());
        menu.setEnabled(zoomAction.get().isActionEnabled());

        if (zoomAction.get().isActionEnabled()) {
          for (JMenuItem jMenuItem : ZoomToolBar.getZoomListMenuItems(this)) {
            menu.add(jMenuItem);
          }
        }
      }
    }
    return menu;
  }

  public JMenu getOrientationMenu(String prop) {
    JMenu menu = null;
    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
      Optional<SliderChangeListener> rotateAction = getAction(ActionW.ROTATION);
      if (rotateAction.isPresent()) {
        menu = new JMenu(Messages.getString("View2dContainer.orientation"));
        menu.setIcon(ActionW.ROTATION.getIcon());
        menu.setEnabled(rotateAction.get().isActionEnabled());

        if (rotateAction.get().isActionEnabled()) {
          JMenuItem menuItem = new JMenuItem(ActionW.RESET.getTitle());
          menuItem.addActionListener(e -> rotateAction.get().setSliderValue(0));
          menu.add(menuItem);
          menuItem = new JMenuItem(Messages.getString("View2dContainer.-90"));
          menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.ALT_DOWN_MASK));
          menuItem.addActionListener(
              e ->
                  rotateAction
                      .get()
                      .setSliderValue((rotateAction.get().getSliderValue() + 270) % 360));
          menu.add(menuItem);
          menuItem = new JMenuItem(Messages.getString("View2dContainer.+90"));
          menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.ALT_DOWN_MASK));
          menuItem.addActionListener(
              e ->
                  rotateAction
                      .get()
                      .setSliderValue((rotateAction.get().getSliderValue() + 90) % 360));
          menu.add(menuItem);
          menuItem = new JMenuItem(Messages.getString("View2dContainer.+180"));
          menuItem.addActionListener(
              e ->
                  rotateAction
                      .get()
                      .setSliderValue((rotateAction.get().getSliderValue() + 180) % 360));
          menu.add(menuItem);

          Optional<ToggleButtonListener> flipAction = getAction(ActionW.FLIP);
          if (flipAction.isPresent()) {
            menu.add(new JSeparator());
            menuItem =
                flipAction
                    .get()
                    .createUnregisteredJCCheckBoxMenuItem(
                        Messages.getString("View2dContainer.flip_h"),
                        ResourceUtil.getIcon(ActionIcon.FLIP));
            menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.ALT_DOWN_MASK));
            menu.add(menuItem);
          }
        }
      }
    }
    return menu;
  }

  public JMenu getSortStackMenu(String prop) {
    JMenu menu = null;
    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
      Optional<ComboItemListener<SeriesComparator<?>>> sortStackAction =
          getAction(ActionW.SORT_STACK);
      if (sortStackAction.isPresent()) {
        menu =
            sortStackAction
                .get()
                .createUnregisteredRadioMenu(Messages.getString("View2dContainer.sort_stack"));
        Optional<ToggleButtonListener> inverseStackAction = getAction(ActionW.INVERSE_STACK);
        if (inverseStackAction.isPresent()) {
          menu.add(new JSeparator());
          menu.add(
              inverseStackAction
                  .get()
                  .createUnregisteredJCCheckBoxMenuItem(
                      Messages.getString("View2dContainer.inv_stack")));
        }
      }
    }
    return menu;
  }

  public JMenu getLutMenu(String prop) {
    JMenu menu = null;
    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
      Optional<ComboItemListener<Preset>> action = getAction(ActionVol.VOL_PRESET);
      if (action.isPresent()) {
        Modality curModality = Modality.DEFAULT;
        if (getSelectedViewPane() instanceof View3d view3d) {
          curModality = view3d.getVolTexture().getModality();
        }
        PresetRadioMenu radioMenu = new PresetRadioMenu();
        radioMenu.setModel(action.get().getModel());
        menu =
            radioMenu.createMenu(
                ActionVol.VOL_PRESET.getTitle(), ResourceUtil.getIcon(ActionIcon.LUT), curModality);
      }
    }
    return menu;
  }

  public JMenu getVolumeTypeMenu(String prop) {
    JMenu menu = null;
    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
      Optional<ComboItemListener<RenderingType>> lutAction = getAction(ActionVol.RENDERING_TYPE);
      if (lutAction.isPresent()) {
        menu = lutAction.get().createUnregisteredRadioMenu(ActionVol.RENDERING_TYPE.getTitle());
      }
    }
    return menu;
  }

  public JCheckBoxMenuItem getShadingMenu(String prop) {
    JCheckBoxMenuItem menu = null;
    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
      Optional<ToggleButtonListener> shadingAction = getAction(ActionVol.VOL_SHADING);
      if (shadingAction.isPresent()) {
        menu =
            shadingAction
                .get()
                .createUnregisteredJCCheckBoxMenuItem(
                    ActionVol.VOL_SHADING.getTitle()
                    //      , ResourceUtil.getIcon(ActionIcon.VOL_SHADING)
                    );
      }
    }
    return menu;
  }
}
