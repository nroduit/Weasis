/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.BoundedRangeModel;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.BasicActionState;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.SliderCineListener.TIME;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.FilterOp;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.SynchCineEvent;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.launcher.Launcher;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.utils.DicomResource;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomExplorer.ListPosition;
import org.weasis.dicom.explorer.DicomExportAction;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.dicom.viewer2d.mpr.MprAxis;
import org.weasis.dicom.viewer2d.mpr.MprContainer;
import org.weasis.dicom.viewer2d.mpr.MprController;
import org.weasis.dicom.viewer2d.mpr.MprView;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.ColorLut;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;

/**
 * The event processing center for this application. This class responses for loading data sets,
 * processing the events from the utility menu that includes changing the operation scope, the
 * layout, window/level, rotation angle, zoom factor, starting/stoping the cining-loop etc.
 */
public class EventManager extends ImageViewerEventManager<DicomImageElement>
    implements ActionListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);

  public static final List<String> functions =
      Collections.unmodifiableList(
          Arrays.asList(
              "zoom", // NON-NLS
              "wl", // NON-NLS
              "move", // NON-NLS
              "scroll", // NON-NLS
              "layout", // NON-NLS
              "mouseLeftAction", // NON-NLS
              "synch", // NON-NLS
              "reset")); // NON-NLS

  /** The single instance of this singleton class. */
  private static EventManager instance;

  /**
   * Return the single instance of this class. This method guarantees the singleton property of this
   * class.
   */
  public static synchronized EventManager getInstance() {
    if (instance == null) {
      instance = new EventManager();
    }
    return instance;
  }

  /** The default private constructor to guarantee the singleton property of this class. */
  private EventManager() {
    // Initialize actions with a null value. These are used by mouse or keyevent actions.
    setAction(new BasicActionState(ActionW.WINLEVEL));
    setAction(new BasicActionState(ActionW.CONTEXTMENU));
    setAction(new BasicActionState(ActionW.NO_ACTION));
    setAction(new BasicActionState(ActionW.DRAW));
    setAction(new BasicActionState(ActionW.MEASURE));
    setAction(new BasicActionState(ActionW.VOLUME));

    setAction(getMoveTroughSliceAction(20.0, TIME.SECOND, 0.1));
    setAction(newLoopSweepAction());
    setAction(newWindowAction());
    setAction(newLevelAction());
    setAction(newRotateAction());
    setAction(newZoomAction());

    setAction(newFlipAction());
    setAction(newInverseLutAction());
    setAction(newInverseStackAction());
    setAction(newLensAction());
    setAction(newLensZoomAction());
    setAction(newDrawOnlyOnceAction());
    setAction(newDefaulPresetAction());

    setAction(newPresetAction());
    setAction(newLutShapeAction());
    setAction(newLutAction());
    setAction(newFilterAction());
    setAction(newSortStackAction());
    setAction(
        newLayoutAction(View2dContainer.DEFAULT_LAYOUT_LIST.toArray(new GridBagLayoutModel[0])));
    setAction(newSynchAction(View2dContainer.DEFAULT_SYNCH_LIST.toArray(new SynchView[0])));
    getAction(ActionW.SYNCH)
        .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(SynchView.DEFAULT_STACK));
    setAction(newMeasurementAction(MeasureToolBar.getMeasureGraphicList().toArray(new Graphic[0])));
    setAction(newDrawAction(MeasureToolBar.getDrawGraphicList().toArray(new Graphic[0])));
    setAction(newSpatialUnit(Unit.values()));
    setAction(newPanAction());
    setAction(newCrosshairAction());
    setAction(new BasicActionState(ActionW.RESET));
    setAction(new BasicActionState(ActionW.SHOW_HEADER));

    setAction(newKOToggleAction());
    setAction(newKOFilterAction());
    setAction(newKOSelectionAction());

    final BundleContext context = AppProperties.getBundleContext(this.getClass());
    Preferences prefs = BundlePreferences.getDefaultPreferences(context);
    zoomSetting.applyPreferences(prefs);
    mouseActions.applyPreferences(prefs);

    if (prefs != null) {
      Preferences prefNode = prefs.node("mouse.sensivity");
      getSliderPreference(prefNode, ActionW.WINDOW, 1.25);
      getSliderPreference(prefNode, ActionW.LEVEL, 1.25);
      getSliderPreference(prefNode, ActionW.SCROLL_SERIES, 0.1);
      getSliderPreference(prefNode, ActionW.ROTATION, 0.25);
      getSliderPreference(prefNode, ActionW.ZOOM, 0.1);

      /*
       * Get first the local value if existed, otherwise try to get the default server configuration and finally if
       * no value take the default value in parameter.
       */
      prefNode = prefs.node("other"); // NON-NLS
      WProperties.setProperty(
          options, WindowOp.P_APPLY_WL_COLOR, prefNode, Boolean.TRUE.toString());
      WProperties.setProperty(options, WindowOp.P_INVERSE_LEVEL, prefNode, Boolean.TRUE.toString());
      WProperties.setProperty(options, PRManager.PR_APPLY, prefNode, Boolean.FALSE.toString());

      WProperties.setProperty(options, View2d.P_CROSSHAIR_MODE, prefNode, "1");
      WProperties.setProperty(options, View2d.P_CROSSHAIR_CENTER_GAP, prefNode, "40");
    }

    initializeParameters();
  }

  private void initializeParameters() {
    enableActions(false);
  }

  private ComboItemListener<KernelData> newFilterAction() {
    return new ComboItemListener<>(ActionW.FILTER, KernelData.getAllFilters()) {

      @Override
      public void itemStateChanged(Object object) {
        if (object instanceof KernelData) {
          firePropertyChange(
              ActionW.SYNCH.cmd(),
              null,
              new SynchEvent(getSelectedViewPane(), action.cmd(), object));
        }
      }
    };
  }

  @Override
  protected SliderCineListener getMoveTroughSliceAction(
      double speed, TIME time, double mouseSensitivity) {
    return new SliderCineListener(ActionW.SCROLL_SERIES, 1, 2, 1, speed, time, mouseSensitivity) {

      @Override
      public void stateChanged(BoundedRangeModel model) {

        ViewCanvas<DicomImageElement> view2d = null;
        Series<DicomImageElement> series = null;
        SynchCineEvent mediaEvent = null;
        DicomImageElement image = null;
        Optional<ToggleButtonListener> defaultPresetAction = getAction(ActionW.DEFAULT_PRESET);
        boolean isDefaultPresetSelected =
            defaultPresetAction.isEmpty() || defaultPresetAction.get().isSelected();

        if (selectedView2dContainer != null) {
          view2d = selectedView2dContainer.getSelectedImagePane();
        }

        if (view2d instanceof MprView mprView) {
          MprContainer mprContainer = (MprContainer) selectedView2dContainer;
          MprController controller = mprContainer.getMprController();
          MprAxis axis = controller.getMprAxis(mprView.getSliceOrientation());
          int index = model.getValue() - 1;
          axis.setSliceIndex(index);
          boolean oldAdjusting = controller.isAdjusting();
          controller.setAdjusting(model.getValueIsAdjusting());
          axis.updateImage();
          image = axis.getImageElement();
          controller.setAdjusting(oldAdjusting);
          mediaEvent = new SynchCineEvent(view2d, image, index);
        } else if (view2d != null && view2d.getSeries() instanceof Series) {
          series = (Series<DicomImageElement>) view2d.getSeries();
          if (series != null) {
            // Model contains display value, value-1 is the index value of a sequence
            int index = model.getValue() - 1;
            image =
                series.getMedia(
                    index,
                    (Filter<DicomImageElement>)
                        view2d.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                    view2d.getCurrentSortComparator());
            mediaEvent = new SynchCineEvent(view2d, image, index);
            // Ensure to load image before calling the default preset (requires pixel min and max)
            if (image != null && !image.isImageAvailable()) {
              image.getImage();
            }
          }
        }
        if (image != null) {
          double[] frameTimes = (double[]) image.getTagValue(TagD.get(Tag.FrameTimeVector));
          if (frameTimes != null && frameTimes.length > 1) {
            Double cineRate = TagD.getTagValue(image, Tag.CineRate, Double.class);
            if (cineRate != null) {
              setSpeed(cineRate);
            }
          }
        }

        Optional<ComboItemListener<GridBagLayoutModel>> layoutAction = getAction(ActionW.LAYOUT);
        Optional<ComboItemListener<SynchView>> synchAction = getAction(ActionW.SYNCH);

        if (image != null
            && layoutAction.isPresent()
            && View2dFactory.getViewTypeNumber(
                    (GridBagLayoutModel) layoutAction.get().getSelectedItem(), ViewCanvas.class)
                > 1
            && synchAction.isPresent()) {

          SynchView synchview = (SynchView) synchAction.get().getSelectedItem();
          if (synchview.getSynchData().isActionEnable(ActionW.SCROLL_SERIES.cmd())) {
            double[] val = (double[]) image.getTagValue(TagW.SlicePosition);
            if (val != null) {
              mediaEvent.setLocation(val[0] + val[1] + val[2]);
            }
          } else {
            if (selectedView2dContainer != null) {
              final List<ViewCanvas<DicomImageElement>> panes =
                  selectedView2dContainer.getImagePanels();
              for (ViewCanvas<DicomImageElement> p : panes) {
                Boolean cutlines = (Boolean) p.getActionValue(ActionW.SYNCH_CROSSLINE.cmd());
                if (cutlines != null && cutlines) {
                  double[] val = (double[]) image.getTagValue(TagW.SlicePosition);
                  if (val != null) {
                    mediaEvent.setLocation(val[0] + val[1] + val[2]);
                  } else {
                    return; // Do not throw event
                  }
                  break;
                }
              }
            }
          }
        }

        Optional<SliderChangeListener> windowAction = getAction(ActionW.WINDOW);
        Optional<SliderChangeListener> levelAction = getAction(ActionW.LEVEL);
        if (view2d != null
            && image != view2d.getImage()
            && image != null
            && windowAction.isPresent()
            && levelAction.isPresent()) {
          Optional<? extends ComboItemListener<?>> presetAction = getAction(ActionW.PRESET);
          PresetWindowLevel oldPreset =
              presetAction.isPresent()
                  ? (PresetWindowLevel) presetAction.get().getSelectedItem()
                  : null;
          PresetWindowLevel newPreset = null;
          boolean pixelPadding =
              LangUtil.getNULLtoTrue(
                  (Boolean)
                      view2d
                          .getDisplayOpManager()
                          .getParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd()));
          PRSpecialElement pr =
              Optional.ofNullable(view2d.getActionValue(ActionW.PR_STATE.cmd()))
                  .filter(PRSpecialElement.class::isInstance)
                  .map(PRSpecialElement.class::cast)
                  .orElse(null);
          DefaultWlPresentation wlp =
              new DefaultWlPresentation(pr == null ? null : pr.getPrDicomObject(), pixelPadding);

          List<PresetWindowLevel> newPresetList = image.getPresetList(wlp);

          // Assume the image cannot display when win =1 and level = 0
          if (oldPreset != null
              || (windowAction.get().getSliderValue() <= 1
                  && levelAction.get().getSliderValue() == 0)) {
            if (isDefaultPresetSelected) {
              newPreset = image.getDefaultPreset(wlp);
            } else {
              if (oldPreset != null) {
                for (PresetWindowLevel preset : newPresetList) {
                  if (preset.getName().equals(oldPreset.getName())) {
                    newPreset = preset;
                    break;
                  }
                }
              }
              // set default preset when the old preset is not available anymore
              if (newPreset == null) {
                newPreset = image.getDefaultPreset(wlp);
                isDefaultPresetSelected = true;
              }
            }
          }

          Optional<? extends ComboItemListener<?>> lutShapeAction = getAction(ActionW.LUT_SHAPE);
          double windowValue =
              newPreset == null ? windowAction.get().getRealValue() : newPreset.getWindow();
          double levelValue =
              newPreset == null ? levelAction.get().getRealValue() : newPreset.getLevel();
          LutShape lutShapeItem =
              newPreset == null
                  ? lutShapeAction.map(c -> (LutShape) c.getSelectedItem()).orElse(null)
                  : newPreset.getLutShape();

          Double levelMin =
              (Double)
                  view2d
                      .getDisplayOpManager()
                      .getParamValue(WindowOp.OP_NAME, ActionW.LEVEL_MIN.cmd());
          Double levelMax =
              (Double)
                  view2d
                      .getDisplayOpManager()
                      .getParamValue(WindowOp.OP_NAME, ActionW.LEVEL_MAX.cmd());

          if (levelMin == null || levelMax == null) {
            levelMin = Math.min(levelValue - windowValue / 2.0, image.getMinValue(wlp));
            levelMax = Math.max(levelValue + windowValue / 2.0, image.getMaxValue(wlp));
          } else {
            levelMin = Math.min(levelMin, image.getMinValue(wlp));
            levelMax = Math.max(levelMax, image.getMaxValue(wlp));
          }

          // FIX : setting actionInView here without firing a propertyChange avoid another call to
          // imageLayer.updateImageOperation(WindowOp.name.....
          // TODO pass to mediaEvent with PR and KO

          ImageOpNode node = view2d.getDisplayOpManager().getNode(WindowOp.OP_NAME);
          if (node != null) {
            node.setParam(ActionW.PRESET.cmd(), newPreset);
            node.setParam(ActionW.DEFAULT_PRESET.cmd(), isDefaultPresetSelected);
            node.setParam(ActionW.WINDOW.cmd(), windowValue);
            node.setParam(ActionW.LEVEL.cmd(), levelValue);
            node.setParam(ActionW.LEVEL_MIN.cmd(), levelMin);
            node.setParam(ActionW.LEVEL_MAX.cmd(), levelMax);
            node.setParam(ActionW.LUT_SHAPE.cmd(), lutShapeItem);
          }
          updateWindowLevelComponentsListener(image, view2d);
        }

        firePropertyChange(ActionW.SYNCH.cmd(), null, mediaEvent);
        if (image != null) {
          fireSeriesViewerListeners(
              new SeriesViewerEvent(selectedView2dContainer, series, image, EVENT.SELECT));
        }

        updateKeyObjectComponentsListener(view2d);
      }

      private double getImageCineRate(
          ViewCanvas<DicomImageElement> view2d, Series<DicomImageElement> series, int index) {
        DicomImageElement image =
            series.getMedia(
                index,
                (Filter<DicomImageElement>) view2d.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                view2d.getCurrentSortComparator());
        if (image != null) {
          Double cineRate = TagD.getTagValue(image, Tag.CineRate, Double.class);
          if (cineRate != null) {
            return cineRate;
          }
        }
        return 0.0;
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        if (isActionEnabled()) {
          setSliderValue(getSliderValue() + e.getWheelRotation());
        }
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
    boolean isDefaultPresetSelected = false;
    Optional<? extends ComboItemListener<?>> presetAction = getAction(ActionW.PRESET);
    if (ActionW.PRESET.cmd().equals(cmd) && object instanceof PresetWindowLevel preset) {
      getAction(ActionW.WINDOW)
          .ifPresent(a -> a.setSliderValue(a.toSliderValue(preset.getWindow()), false));
      getAction(ActionW.LEVEL)
          .ifPresent(a -> a.setSliderValue(a.toSliderValue(preset.getLevel()), false));
      getAction(ActionW.LUT_SHAPE)
          .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(preset.getLutShape()));

      PresetWindowLevel defaultPreset =
          presetAction
              .map(comboItemListener -> (PresetWindowLevel) comboItemListener.getFirstItem())
              .orElse(null);
      isDefaultPresetSelected = preset.equals(defaultPreset);
    } else {
      presetAction.ifPresent(
          a ->
              a.setSelectedItemWithoutTriggerAction(
                  object instanceof PresetWindowLevel ? object : null));
    }

    Optional<ToggleButtonListener> defaultPresetAction = getAction(ActionW.DEFAULT_PRESET);
    if (defaultPresetAction.isPresent()) {
      defaultPresetAction.get().setSelectedWithoutTriggerAction(isDefaultPresetSelected);
      SynchEvent evt =
          new SynchEvent(
              getSelectedViewPane(),
              ActionW.DEFAULT_PRESET.cmd(),
              defaultPresetAction.get().isSelected());
      evt.put(cmd, object);
      firePropertyChange(ActionW.SYNCH.cmd(), null, evt);
    }

    if (selectedView2dContainer != null) {
      fireSeriesViewerListeners(
          new SeriesViewerEvent(selectedView2dContainer, null, null, EVENT.WIN_LEVEL));
    }
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
        ActionW.LUT_SHAPE, DicomImageElement.DEFAULT_LUT_FUNCTIONS.toArray(new LutShape[0])) {

      @Override
      public void itemStateChanged(Object object) {
        updatePreset(action.cmd(), object);
      }
    };
  }

  private ToggleButtonListener newDefaulPresetAction() {
    return new ToggleButtonListener(ActionW.DEFAULT_PRESET, true) {
      @Override
      public void actionPerformed(boolean selected) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
      }
    };
  }

  private ToggleButtonListener newKOToggleAction() {
    return new ToggleButtonListener(ActionW.KO_TOGGLE_STATE, false) {
      @Override
      public void actionPerformed(boolean newSelectedState) {

        boolean hasKeyObjectReferenceChanged =
            KOManager.setKeyObjectReference(newSelectedState, getSelectedViewPane());

        if (!hasKeyObjectReferenceChanged) {
          // If KO Toogle State hasn't changed this action should be reset to its previous state,
          // that is the
          // current view's actionValue
          this.setSelectedWithoutTriggerAction(
              (Boolean) getSelectedViewPane().getActionValue(ActionW.KO_TOGGLE_STATE.cmd()));
        }
      }
    };
  }

  private ComboItemListener<Object> newKOSelectionAction() {
    return new ComboItemListener<>(
        ActionW.KO_SELECTION, new ActionState.NoneLabel[] {ActionState.NoneLabel.NONE}) {
      @Override
      public void itemStateChanged(Object object) {
        koAction(action, object);
      }
    };
  }

  private ToggleButtonListener newKOFilterAction() {
    return new ToggleButtonListener(ActionW.KO_FILTER, false) {
      @Override
      public void actionPerformed(boolean selected) {
        koAction(action, selected);
      }
    };
  }

  private void koAction(Feature<?> action, Object selected) {
    Optional<ComboItemListener<SynchView>> synchAction = getAction(ActionW.SYNCH);
    SynchView synchView =
        synchAction
            .map(comboItemListener -> (SynchView) comboItemListener.getSelectedItem())
            .orElse(null);
    boolean tileMode =
        synchView != null && SynchData.Mode.TILE.equals(synchView.getSynchData().getMode());
    ViewCanvas<DicomImageElement> selectedView = getSelectedViewPane();
    if (tileMode) {
      if (selectedView2dContainer instanceof View2dContainer container && selectedView != null) {
        boolean filterSelection = selected instanceof Boolean;
        Object selectedKO =
            filterSelection ? selectedView.getActionValue(ActionW.KO_SELECTION.cmd()) : selected;
        Boolean enableFilter =
            (Boolean)
                (filterSelection ? selected : selectedView.getActionValue(ActionW.KO_FILTER.cmd()));
        ViewCanvas<DicomImageElement> viewPane = container.getSelectedImagePane();
        int frameIndex =
            LangUtil.getNULLtoFalse(enableFilter)
                ? 0
                : viewPane.getFrameIndex() - viewPane.getTileOffset();

        for (ViewCanvas<DicomImageElement> view : container.getImagePanels(true)) {
          if (!(view.getSeries() instanceof DicomSeries) || !(view instanceof View2d)) {
            continue;
          }

          KOManager.updateKOFilter(view, selectedKO, enableFilter, frameIndex, false);
        }

        container.updateTileOffset();
        if (!(selectedView.getSeries() instanceof DicomSeries)) {
          List<ViewCanvas<DicomImageElement>> panes = selectedView2dContainer.getImagePanels(false);
          if (!panes.isEmpty()) {
            selectedView2dContainer.setSelectedImagePane(panes.get(0));
            return;
          }
        }
      }
    }

    firePropertyChange(
        ActionW.SYNCH.cmd(), null, new SynchEvent(selectedView, action.cmd(), selected));

    if (tileMode) {
      Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES);
      if (cineAction.isPresent() && cineAction.get().isActionEnabled()) {
        SliderCineListener moveTroughSliceAction = cineAction.get();
        if (moveTroughSliceAction.getSliderValue() == 1) {
          moveTroughSliceAction.stateChanged(moveTroughSliceAction.getSliderModel());
        } else {
          moveTroughSliceAction.setSliderValue(1);
        }
      }
    }
  }

  private ComboItemListener<ByteLut> newLutAction() {
    List<ByteLut> luts = new ArrayList<>();
    luts.add(ColorLut.GRAY.getByteLut());
    ByteLutCollection.readLutFilesFromResourcesDir(
        luts, ResourceUtil.getResource(DicomResource.LUTS));
    // Set default first as the list has been sorted
    luts.addFirst(ColorLut.IMAGE.getByteLut());

    return new ComboItemListener<>(ActionW.LUT, luts.toArray(new ByteLut[0])) {

      @Override
      public void itemStateChanged(Object object) {
        firePropertyChange(
            ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(), object));
        if (selectedView2dContainer != null) {
          fireSeriesViewerListeners(
              new SeriesViewerEvent(selectedView2dContainer, null, null, EVENT.LUT));
        }
      }
    };
  }

  private ComboItemListener<SeriesComparator<DicomImageElement>> newSortStackAction() {
    return new ComboItemListener<>(ActionW.SORT_STACK, SortSeriesStack.getValues()) {

      @Override
      public void itemStateChanged(Object object) {
        firePropertyChange(
            ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(), object));
      }
    };
  }

  @Override
  public Optional<Feature<? extends ActionState>> getLeftMouseActionFromKeyEvent(
      int keyEvent, int modifier) {
    Optional<Feature<? extends ActionState>> feature =
        super.getLeftMouseActionFromKeyEvent(keyEvent, modifier);
    if (feature.isEmpty()) {
      return feature;
    }
    // Only return the action if it is enabled
    if (getAction(feature.get()).filter(ActionState::isActionEnabled).isPresent()) {
      return feature;
    } else if (ActionW.KO_TOGGLE_STATE.equals(feature.get())
        && keyEvent == ActionW.KO_TOGGLE_STATE.getKeyCode()) {
      getAction(ActionW.KO_TOGGLE_STATE).ifPresent(b -> b.setSelected(!b.isSelected()));
    }
    return Optional.empty();
  }

  @Override
  public void keyTyped(KeyEvent e) {
    // Do nothing
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (!commonDisplayShortcuts(e)) {
      int keyEvent = e.getKeyCode();
      int modifiers = e.getModifiers();
      boolean isMpr = selectedView2dContainer instanceof MprContainer;

      if (keyEvent == KeyEvent.VK_LEFT && !e.isAltDown()) {
        if (e.isControlDown()) {
          moveStudy(ListPosition.PREVIOUS);
        } else {
          moveSeries(ListPosition.PREVIOUS);
        }
      } else if (keyEvent == KeyEvent.VK_RIGHT && !e.isAltDown()) {
        if (e.isControlDown()) {
          moveStudy(ListPosition.NEXT);
        } else {
          moveSeries(ListPosition.NEXT);
        }
      } else if (keyEvent == KeyEvent.VK_UP && !e.isAltDown() && e.isControlDown()) {
        movePatient(ListPosition.PREVIOUS);
      } else if (keyEvent == KeyEvent.VK_DOWN && !e.isAltDown() && e.isControlDown()) {
        movePatient(ListPosition.NEXT);
      } else if (keyEvent == KeyEvent.VK_PAGE_UP) {
        if (e.isControlDown()) {
          moveStudy(ListPosition.FIRST);
        } else {
          moveSeries(ListPosition.FIRST);
        }
      } else if (keyEvent == KeyEvent.VK_PAGE_DOWN) {
        if (e.isControlDown()) {
          moveStudy(ListPosition.LAST);
        } else {
          moveSeries(ListPosition.LAST);
        }
      } else if (keyEvent == KeyEvent.VK_HOME && e.isControlDown()) {
        movePatient(ListPosition.FIRST);
      } else if (keyEvent == KeyEvent.VK_END && e.isControlDown()) {
        movePatient(ListPosition.LAST);
      } else if (isMpr && keyEvent == KeyEvent.VK_A && e.isAltDown()) {
        if (selectedView2dContainer.getSelectedImagePane() instanceof MprView mprView) {
          mprView.recenterAxis(false);
        }
      } else if (isMpr && keyEvent == KeyEvent.VK_S && e.isAltDown()) {
        if (selectedView2dContainer.getSelectedImagePane() instanceof MprView mprView) {
          boolean showCenter = MprView.getViewProperty(mprView, MprView.SHOW_CROSS_CENTER);
          mprView.showCrossCenter(!showCenter, false);
        }
      } else if (isMpr && keyEvent == KeyEvent.VK_D && e.isAltDown()) {
        if (selectedView2dContainer.getSelectedImagePane() instanceof MprView mprView) {
          boolean showCrossLines = MprView.getViewProperty(mprView, MprView.HIDE_CROSSLINES);
          mprView.showCrossLines(showCrossLines, false);
        }
      } else if (isMpr && keyEvent == KeyEvent.VK_C && e.isAltDown()) {
        if (selectedView2dContainer.getSelectedImagePane() instanceof MprView mprView) {
          mprView.recenterAxis(true);
        }
      } else if (isMpr && keyEvent == KeyEvent.VK_V && e.isAltDown()) {
        if (selectedView2dContainer.getSelectedImagePane() instanceof MprView mprView) {
          boolean showCenter = mprView.getAllViewsProperty(MprView.SHOW_CROSS_CENTER);
          mprView.showCrossCenter(!showCenter, true);
        }
      } else if (isMpr && keyEvent == KeyEvent.VK_B && e.isAltDown()) {
        if (selectedView2dContainer.getSelectedImagePane() instanceof MprView mprView) {
          boolean showCrossLines = mprView.getAllViewsProperty(MprView.HIDE_CROSSLINES);
          mprView.showCrossLines(showCrossLines, true);
        }
      } else {
        keyPreset(keyEvent, modifiers);
        triggerDrawingToolKeyEvent(keyEvent, modifiers);
      }
    }
  }

  private DicomExplorer getDicomExplorer() {
    DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (dicomView instanceof DicomExplorer dicom) {
      return dicom;
    }
    return null;
  }

  private void movePatient(ListPosition position) {
    ViewCanvas<DicomImageElement> view = getSelectedViewPane();
    if (view != null) {
      DicomExplorer dicom = getDicomExplorer();
      if (dicom != null) {
        MediaSeries<? extends MediaElement> series = dicom.movePatient(view, position);
        ImageViewerPlugin<DicomImageElement> container = getSelectedView2dContainer();
        fireSeriesViewerListeners(
            new SeriesViewerEvent(container, series, null, EVENT.SELECT_VIEW));
      }
    }
  }

  private void moveStudy(ListPosition position) {
    ViewCanvas<DicomImageElement> view = getSelectedViewPane();
    if (view != null) {
      DicomExplorer dicom = getDicomExplorer();
      if (dicom != null) {
        MediaSeries<? extends MediaElement> series = dicom.moveStudy(view, position);
        fireSeriesViewerListeners(
            new SeriesViewerEvent(getSelectedView2dContainer(), series, null, EVENT.SELECT_VIEW));
      }
    }
  }

  private void moveSeries(ListPosition position) {
    ViewCanvas<DicomImageElement> view = getSelectedViewPane();
    if (view != null) {
      DicomExplorer dicom = getDicomExplorer();
      if (dicom != null) {
        MediaSeries<? extends MediaElement> series = dicom.moveSeries(view, position);
        fireSeriesViewerListeners(
            new SeriesViewerEvent(getSelectedView2dContainer(), series, null, EVENT.SELECT_VIEW));
      }
    }
  }

  @Override
  public void keyReleased(KeyEvent e) {
    // Do nothing
  }

  private void keyPreset(int keyEvent, int modifiers) {
    Optional<? extends ComboItemListener<?>> presetAction = getAction(ActionW.PRESET);
    if (modifiers == 0 && presetAction.isPresent() && presetAction.get().isActionEnabled()) {
      ComboItemListener<?> presetComboListener = presetAction.get();
      DefaultComboBoxModel<?> model = presetComboListener.getModel();
      for (int i = 0; i < model.getSize(); i++) {
        PresetWindowLevel val = (PresetWindowLevel) model.getElementAt(i);
        if (val.getKeyCode() == keyEvent) {
          presetComboListener.setSelectedItem(val);
          return;
        }
      }
    }
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
      }
    }
  }

  /** process the action events. */
  @Override
  public void actionPerformed(ActionEvent evt) {
    cinePlay(evt.getActionCommand());
  }

  private void cinePlay(String command) {
    if (command != null) {
      if (command.equals(ActionW.CINESTART.cmd())) {
        getAction(ActionW.SCROLL_SERIES).ifPresent(SliderCineListener::start);
      } else if (command.equals(ActionW.CINESTOP.cmd())) {
        getAction(ActionW.SCROLL_SERIES).ifPresent(SliderCineListener::stop);
      }
    }
  }

  @Override
  public String resolvePlaceholders(String template) {
    return DicomExportAction.resolvePlaceholders(template, this);
  }

  @Override
  public void dicomExportAction(Launcher launcher) {
    if (launcher != null && launcher.getConfiguration().isDicomSelectionAction()) {
      DicomExplorer dicom = getDicomExplorer();
      if (dicom != null) {
        DicomModel dicomModel = (DicomModel) dicom.getDataExplorerModel();
        DicomExportAction action = new DicomExportAction(launcher, dicomModel);
        try {
          action.execute();
        } catch (IOException e) {
          LOGGER.error("Copy DICOM failed", e);
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
      // property change,
      // otherwise the value is adjusted by the BoundedRangeModel
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
        }
      }
    }
  }

  @Override
  public synchronized boolean updateComponentsListener(ViewCanvas<DicomImageElement> view2d) {
    if (view2d == null) {
      return false;
    }

    if (selectedView2dContainer == null
        || view2d != selectedView2dContainer.getSelectedImagePane()) {
      return false;
    }

    clearAllPropertyChangeListeners();
    Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES);

    if (view2d.getSourceImage() == null) {
      enableActions(false);
      if (view2d.getSeries() != null) {
        // Let scrolling if only one image is corrupted in the series
        cineAction.ifPresent(a -> a.enableAction(true));
      }
      View2dContainer.UI.updateDynamicTools(view2d.getSeries());
      return false;
    }

    if (!enabledAction) {
      enableActions(true);
    }

    View2dContainer.UI.updateDynamicTools(view2d.getSeries());

    OpManager dispOp = view2d.getDisplayOpManager();

    updateWindowLevelComponentsListener(view2d.getImage(), view2d);

    getAction(ActionW.LUT)
        .ifPresent(
            a ->
                a.setSelectedItemWithoutTriggerAction(
                    dispOp.getParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT)));
    getAction(ActionW.INVERT_LUT)
        .ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    (Boolean)
                        dispOp.getParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT_INVERSE)));
    getAction(ActionW.FILTER)
        .ifPresent(
            a ->
                a.setSelectedItemWithoutTriggerAction(
                    dispOp.getParamValue(FilterOp.OP_NAME, FilterOp.P_KERNEL_DATA)));
    getAction(ActionW.ROTATION)
        .ifPresent(
            a -> a.setSliderValue((Integer) view2d.getActionValue(ActionW.ROTATION.cmd()), false));
    getAction(ActionW.FLIP)
        .ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    LangUtil.getNULLtoFalse((Boolean) view2d.getActionValue(ActionW.FLIP.cmd()))));

    getAction(ActionW.ZOOM)
        .ifPresent(
            a ->
                a.setRealValue(
                    Math.abs((Double) view2d.getActionValue(ActionW.ZOOM.cmd())), false));
    getAction(ActionW.SPATIAL_UNIT)
        .ifPresent(
            a ->
                a.setSelectedItemWithoutTriggerAction(
                    view2d.getActionValue(ActionW.SPATIAL_UNIT.cmd())));

    getAction(ActionW.LENS)
        .ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    (Boolean) view2d.getActionValue(ActionW.LENS.cmd())));
    Double lensZoom = (Double) view2d.getLensActionValue(ActionW.ZOOM.cmd());
    if (lensZoom != null) {
      getAction(ActionW.LENS_ZOOM).ifPresent(a -> a.setRealValue(Math.abs(lensZoom), false));
    }

    boolean isMprOrOblique =
        selectedView2dContainer instanceof MprContainer c
            && c.getMprController().getVolume() != null;
    MediaSeries<DicomImageElement> series = view2d.getSeries();
    int maxSlice;
    int currentSlice;
    if (isMprOrOblique && view2d instanceof MprView mprView) {
      MprContainer mprContainer = (MprContainer) selectedView2dContainer;
      MprController controller = mprContainer.getMprController();
      Volume<?> volume = controller.getVolume();
      maxSlice = volume.getSliceSize();
      MprAxis axis = controller.getMprAxis(mprView.getSliceOrientation());
      currentSlice = axis.getSliceIndex();
    } else {
      maxSlice =
          series.size(
              (Filter<DicomImageElement>) view2d.getActionValue(ActionW.FILTERED_SERIES.cmd()));
      currentSlice = view2d.getFrameIndex() + 1;
    }
    cineAction.ifPresent(a -> a.setSliderMinMaxValue(1, maxSlice, currentSlice, false));

    Double cineRate = TagD.getTagValue(view2d.getImage(), Tag.CineRate, Double.class);
    cineAction.ifPresent(
        a -> {
          a.setSpeed(cineRate == null ? 20.0 : cineRate);
        });
    int playbackSequencing = getPlaybackSequencing(view2d);
    getAction(ActionW.CINE_SWEEP).ifPresent(a -> a.setSelected(playbackSequencing == 1));

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
    getAction(ActionW.VOLUME).ifPresent(a -> a.enableAction(series.isSuitableFor3d()));

    getAction(ActionW.CROSSHAIR).ifPresent(a -> a.enableAction(!isMprOrOblique));
    updateKeyObjectComponentsListener(view2d);

    // register all actions for the selected view and for the other views register according to
    // synchview.
    ComboItemListener<SynchView> synchAction = getAction(ActionW.SYNCH).orElse(null);
    updateAllListeners(
        selectedView2dContainer,
        synchAction == null ? SynchView.NONE : (SynchView) synchAction.getSelectedItem());

    view2d.updateGraphicSelectionListener(selectedView2dContainer);
    return true;
  }

  private static int getPlaybackSequencing(ViewCanvas<DicomImageElement> view2d) {
    Integer playbackSequencing =
        TagD.getTagValue(view2d.getImage(), Tag.PreferredPlaybackSequencing, Integer.class);
    return playbackSequencing == null ? 0 : playbackSequencing;
  }

  public void updateKeyObjectComponentsListener(ViewCanvas<DicomImageElement> view2d) {
    if (view2d != null) {
      Optional<? extends ComboItemListener<Object>> koSelectionAction =
          getAction(ActionW.KO_SELECTION);
      Optional<ToggleButtonListener> koToggleAction = getAction(ActionW.KO_TOGGLE_STATE);
      Optional<ToggleButtonListener> koFilterAction = getAction(ActionW.KO_FILTER);
      if (LangUtil.getNULLtoFalse((Boolean) view2d.getActionValue("no.ko"))) {
        koToggleAction.ifPresent(a -> a.enableAction(false));
        koFilterAction.ifPresent(a -> a.enableAction(false));
        koSelectionAction.ifPresent(a -> a.enableAction(false));
      } else {
        koToggleAction.ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    (Boolean) view2d.getActionValue(ActionW.KO_TOGGLE_STATE.cmd())));
        koFilterAction.ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    (Boolean) view2d.getActionValue(ActionW.KO_FILTER.cmd())));

        Object[] kos = KOManager.getKOElementListWithNone(view2d).toArray();
        boolean enable = kos.length > 1;
        if (enable) {
          koSelectionAction.ifPresent(a -> a.setDataListWithoutTriggerAction(kos));
          koSelectionAction.ifPresent(
              a ->
                  a.setSelectedItemWithoutTriggerAction(
                      view2d.getActionValue(ActionW.KO_SELECTION.cmd())));
        }
        koFilterAction.ifPresent(a -> a.enableAction(enable));
        koSelectionAction.ifPresent(a -> a.enableAction(enable));
      }
    }
  }

  private void updateWindowLevelComponentsListener(
      DicomImageElement image, ViewCanvas<DicomImageElement> view2d) {

    ImageOpNode node = view2d.getDisplayOpManager().getNode(WindowOp.OP_NAME);
    if (node != null) {
      int imageDataType = ImageConversion.convertToDataType(image.getImage().type());
      PresetWindowLevel preset = (PresetWindowLevel) node.getParam(ActionW.PRESET.cmd());
      boolean defaultPreset =
          LangUtil.getNULLtoTrue((Boolean) node.getParam(ActionW.DEFAULT_PRESET.cmd()));
      Double windowValue = (Double) node.getParam(ActionW.WINDOW.cmd());
      Double levelValue = (Double) node.getParam(ActionW.LEVEL.cmd());
      LutShape lutShapeItem = (LutShape) node.getParam(ActionW.LUT_SHAPE.cmd());
      boolean pixelPadding =
          LangUtil.getNULLtoTrue((Boolean) node.getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
      PrDicomObject prDicomObject =
          PRManager.getPrDicomObject(view2d.getActionValue(ActionW.PR_STATE.cmd()));
      DefaultWlPresentation wlp = new DefaultWlPresentation(prDicomObject, pixelPadding);

      getAction(ActionW.DEFAULT_PRESET)
          .ifPresent(a -> a.setSelectedWithoutTriggerAction(defaultPreset));

      Optional<SliderChangeListener> windowAction = getAction(ActionW.WINDOW);
      Optional<SliderChangeListener> levelAction = getAction(ActionW.LEVEL);
      if (windowAction.isPresent() && levelAction.isPresent()) {
        double window;
        double minLevel;
        double maxLevel;
        if (windowValue == null) {
          windowValue = windowAction.get().getRealValue();
        }
        if (levelValue == null) {
          levelValue = levelAction.get().getRealValue();
        }
        Double levelMin = (Double) node.getParam(ActionW.LEVEL_MIN.cmd());
        Double levelMax = (Double) node.getParam(ActionW.LEVEL_MAX.cmd());
        double levelLow = Math.min(levelValue - windowValue / 2.0, image.getMinValue(wlp));
        double levelHigh = Math.max(levelValue + windowValue / 2.0, image.getMaxValue(wlp));
        if (levelMin == null || levelMax == null) {
          minLevel = levelLow;
          maxLevel = levelHigh;
        } else {
          minLevel = Math.min(levelMin, levelLow);
          maxLevel = Math.max(levelMax, levelHigh);
        }
        window = Math.max(windowValue, maxLevel - minLevel);

        windowAction
            .get()
            .setRealMinMaxValue(
                imageDataType >= DataBuffer.TYPE_FLOAT ? 0.00001 : 1.0, window, windowValue, false);
        levelAction.get().setRealMinMaxValue(minLevel, maxLevel, levelValue, false);
      }

      List<PresetWindowLevel> presetList = image.getPresetList(wlp);
      if (prDicomObject != null) {
        List<PresetWindowLevel> prPresets =
            (List<PresetWindowLevel>) view2d.getActionValue(PRManager.PR_PRESETS);
        if (prPresets != null && !prPresets.isEmpty()) {
          presetList = prPresets;
        }
      }

      Optional<ComboItemListener<Object>> presetAction = getAction(ActionW.PRESET);
      if (presetAction.isPresent()) {
        presetAction
            .get()
            .setDataListWithoutTriggerAction(presetList == null ? null : presetList.toArray());
        presetAction.get().setSelectedItemWithoutTriggerAction(preset);
      }

      Optional<? extends ComboItemListener<Object>> lutShapeAction = getAction(ActionW.LUT_SHAPE);
      if (lutShapeAction.isPresent()) {
        Collection<LutShape> lutShapeList =
            imageDataType >= DataBuffer.TYPE_INT
                ? Collections.singletonList(LutShape.LINEAR)
                : image.getLutShapeCollection(wlp);
        if (prDicomObject != null
            && lutShapeList != null
            && lutShapeItem != null
            && !lutShapeList.contains(lutShapeItem)) {
          // Make a copy of the image list
          ArrayList<LutShape> newList = new ArrayList<>(lutShapeList.size() + 1);
          newList.add(lutShapeItem);
          newList.addAll(lutShapeList);
          lutShapeList = newList;
        }
        lutShapeAction
            .get()
            .setDataListWithoutTriggerAction(lutShapeList == null ? null : lutShapeList.toArray());
        lutShapeAction.get().setSelectedItemWithoutTriggerAction(lutShapeItem);
      }
    }
  }

  @Override
  protected boolean isCompatible(
      MediaSeries<DicomImageElement> series1, MediaSeries<DicomImageElement> series2) {
    // Have the two series the same image plane orientation
    return ImageOrientation.hasSameOrientation(series1, series2);
  }

  protected List<ViewCanvas<DicomImageElement>> getViews(
      ImageViewerPlugin<DicomImageElement> viewerPlugin,
      ViewCanvas<DicomImageElement> viewPane,
      boolean allVisible) {
    List<ViewCanvas<DicomImageElement>> views;
    if (viewPane != null && viewPane.getSeries() != null) {
      views = viewerPlugin.getImagePanels();
      views.remove(viewPane);
    } else {
      return Collections.emptyList();
    }

    if (allVisible && viewerPlugin instanceof View2dContainer) {
      List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
      synchronized (viewerPlugins) {
        for (final ViewerPlugin<?> p : viewerPlugins) {
          if (p instanceof View2dContainer plugin
              && plugin.getDockable().isShowing()
              && viewerPlugin != plugin
              && viewerPlugin.getGroupID().equals(plugin.getGroupID())
              && Mode.STACK.equals(plugin.getSynchView().getSynchData().getMode())) {
            views.addAll(plugin.getImagePanels());
          }
        }
      }
    }
    return views;
  }

  @Override
  public void updateAllListeners(
      ImageViewerPlugin<DicomImageElement> viewerPlugin, SynchView synchView) {
    clearAllPropertyChangeListeners();

    if (viewerPlugin != null) {
      ViewCanvas<DicomImageElement> viewPane = viewerPlugin.getSelectedImagePane();
      if (viewPane == null) {
        return;
      }
      SynchData synch = synchView.getSynchData();
      MediaSeries<DicomImageElement> series = viewPane.getSeries();
      if (series != null) {
        SynchData oldSynch = (SynchData) viewPane.getActionValue(ActionW.SYNCH_LINK.cmd());
        if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
          oldSynch = synch;
        }
        viewPane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
        addPropertyChangeListener(ActionW.SYNCH.cmd(), viewPane);

        Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES);
        // if (viewPane instanceof MipView) {
        // // Handle special case with MIP view, do not let scroll the series
        // moveTroughSliceAction.enableAction(false);
        // synchView = SynchView.NONE;
        // synch = synchView.getSynchData();
        // } else {
        cineAction.ifPresent(a -> a.enableAction(true));
        // }
        viewPane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);

        if (SynchView.NONE.equals(synchView)) {
          for (ViewCanvas<DicomImageElement> pane : getViews(viewerPlugin, viewPane, false)) {
            pane.getGraphicManager().deleteByLayerType(LayerType.CROSSLINES);

            MediaSeries<DicomImageElement> s = pane.getSeries();
            String fruid = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
            boolean specialView = pane instanceof MipView;
            if (s != null && fruid != null && !specialView) {
              if (fruid.equals(TagD.getTagValue(s, Tag.FrameOfReferenceUID))) {
                if (!ImageOrientation.hasSameOrientation(series, s)) {
                  pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), true);
                  propertySupport.addPropertyChangeListener(ActionW.SCROLL_SERIES.cmd(), pane);
                }
                // Force drawing crosslines without changing the slice position
                cineAction.ifPresent(a -> a.stateChanged(a.getSliderModel()));
              }
            }
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
            String fruid = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
            DicomImageElement img = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
            double[] val = img == null ? null : (double[]) img.getTagValue(TagW.SlicePosition);

            for (ViewCanvas<DicomImageElement> pane : getViews(viewerPlugin, viewPane, true)) {
              pane.getGraphicManager().deleteByLayerType(LayerType.CROSSLINES);

              MediaSeries<DicomImageElement> s = pane.getSeries();
              boolean specialView = pane instanceof MipView;
              if (s != null && fruid != null && val != null && !specialView) {
                boolean synchByDefault = fruid.equals(TagD.getTagValue(s, Tag.FrameOfReferenceUID));
                oldSynch = (SynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
                if (synchByDefault) {
                  if (ImageOrientation.hasSameOrientation(series, s)) {
                    pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);
                    // Only fully synch if no PR is applied (because can change pixel size)
                    if (pane.getActionValue(ActionW.PR_STATE.cmd()) == null
                        && hasSameSize(series, s)) {
                      // If the image has the same reference and the same spatial calibration, all
                      // the actions are synchronized
                      if (oldSynch == null
                          || oldSynch.isOriginal()
                          || !oldSynch.getMode().equals(synch.getMode())) {
                        oldSynch = synch.copy();
                      }
                    } else {
                      if (oldSynch == null
                          || oldSynch.isOriginal()
                          || !oldSynch.getMode().equals(synch.getMode())) {
                        oldSynch = synch.copy();
                        for (Entry<String, Boolean> a : oldSynch.getActions().entrySet()) {
                          a.setValue(false);
                        }
                        oldSynch.getActions().put(ActionW.SCROLL_SERIES.cmd(), true);
                      }
                    }
                  } else {
                    pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), true);
                    if (pane instanceof MprView) {
                      if (oldSynch == null
                          || oldSynch.isOriginal()
                          || !oldSynch.getMode().equals(synch.getMode())) {
                        oldSynch = synch.copy();
                      }
                    } else {
                      if (oldSynch == null
                          || oldSynch.isOriginal()
                          || !oldSynch.getMode().equals(synch.getMode())) {
                        oldSynch = synch.copy();
                        for (Entry<String, Boolean> a : oldSynch.getActions().entrySet()) {
                          a.setValue(false);
                        }
                        oldSynch.getActions().put(ActionW.SCROLL_SERIES.cmd(), true);
                      }
                    }
                  }
                  addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
                }
                pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), oldSynch);
                // pane.updateSynchState();
              }
            }
            // Force drawing crosslines without changing the slice position
            boolean isMprOrOblique = selectedView2dContainer instanceof MprContainer;
            if (!isMprOrOblique) {
              cineAction.ifPresent(a -> a.stateChanged(a.getSliderModel()));
            }

          } else if (Mode.TILE.equals(synch.getMode())) {
            final List<ViewCanvas<DicomImageElement>> panes =
                getViews(viewerPlugin, viewPane, false);
            // Limit the scroll
            final int maxShift =
                series.size(
                        (Filter<DicomImageElement>)
                            viewPane.getActionValue(ActionW.FILTERED_SERIES.cmd()))
                    - panes.size();
            cineAction.ifPresent(
                a ->
                    a.setSliderMinMaxValue(
                        1, Math.max(maxShift, 1), viewPane.getFrameIndex() + 1, false));

            Object selectedKO = viewPane.getActionValue(ActionW.KO_SELECTION.cmd());
            Boolean enableFilter = (Boolean) viewPane.getActionValue(ActionW.KO_FILTER.cmd());
            int frameIndex =
                LangUtil.getNULLtoFalse(enableFilter)
                    ? 0
                    : viewPane.getFrameIndex() - viewPane.getTileOffset();
            for (ViewCanvas<DicomImageElement> pane : panes) {
              oldSynch = (SynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
              if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
                oldSynch = synch.copy();
              }
              oldSynch.getActions().put(ActionW.KO_SELECTION.cmd(), true);
              oldSynch.getActions().put(ActionW.KO_FILTER.cmd(), true);
              KOManager.updateKOFilter(pane, selectedKO, enableFilter, frameIndex);

              pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), oldSynch);
              pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);
              addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
              // pane.updateSynchState();
            }

            if (LangUtil.getNULLtoFalse(enableFilter)) {
              KOManager.updateKOFilter(viewPane, selectedKO, enableFilter, frameIndex);
            }
          }
        }
      }

      // viewPane.updateSynchState();
    }
  }

  public static boolean hasSameSize(
      MediaSeries<DicomImageElement> series1, MediaSeries<DicomImageElement> series2) {
    // Test if the two series has the same size
    if (series1 != null && series2 != null) {
      DicomImageElement image1 = series1.getMedia(MEDIA_POSITION.MIDDLE, null, null);
      DicomImageElement image2 = series2.getMedia(MEDIA_POSITION.MIDDLE, null, null);
      if (image1 != null && image2 != null) {
        return image1.hasSameSize(image2);
      }
    }
    return false;
  }

  public void savePreferences(BundleContext bundleContext) {
    Preferences prefs = BundlePreferences.getDefaultPreferences(bundleContext);
    zoomSetting.savePreferences(prefs);
    // Mouse buttons preferences
    mouseActions.savePreferences(prefs);
    if (prefs != null) {
      // Mouse sensitivity
      Preferences prefNode = prefs.node("mouse.sensivity");
      setSliderPreference(prefNode, ActionW.WINDOW);
      setSliderPreference(prefNode, ActionW.LEVEL);
      setSliderPreference(prefNode, ActionW.SCROLL_SERIES);
      setSliderPreference(prefNode, ActionW.ROTATION);
      setSliderPreference(prefNode, ActionW.ZOOM);

      prefNode = prefs.node("other"); // NON-NLS
      BundlePreferences.putBooleanPreferences(
          prefNode,
          WindowOp.P_APPLY_WL_COLOR,
          options.getBooleanProperty(WindowOp.P_APPLY_WL_COLOR, true));
      BundlePreferences.putBooleanPreferences(
          prefNode,
          WindowOp.P_INVERSE_LEVEL,
          options.getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
      BundlePreferences.putBooleanPreferences(
          prefNode, PRManager.PR_APPLY, options.getBooleanProperty(PRManager.PR_APPLY, false));

      BundlePreferences.putIntPreferences(
          prefNode, View2d.P_CROSSHAIR_MODE, options.getIntProperty(View2d.P_CROSSHAIR_MODE, 1));
      BundlePreferences.putIntPreferences(
          prefNode,
          View2d.P_CROSSHAIR_CENTER_GAP,
          options.getIntProperty(View2d.P_CROSSHAIR_CENTER_GAP, 40));

      Preferences containerNode =
          prefs.node(View2dContainer.UI.clazz.getSimpleName().toLowerCase());
      InsertableUtil.savePreferences(View2dContainer.UI.toolBars, containerNode, Type.TOOLBAR);
      InsertableUtil.savePreferences(View2dContainer.UI.tools, containerNode, Type.TOOL);

      InsertableUtil.savePreferences(
          MprContainer.UI.toolBars,
          prefs.node(MprContainer.class.getSimpleName().toLowerCase()),
          Type.TOOLBAR);
    }
  }

  private void setSliderPreference(
      Preferences prefNode, Feature<? extends SliderChangeListener> action) {
    getAction(action)
        .ifPresent(
            s ->
                BundlePreferences.putDoublePreferences(
                    prefNode, action.cmd(), s.getMouseSensitivity()));
  }

  private void getSliderPreference(
      Preferences prefNode, Feature<? extends SliderChangeListener> action, double defVal) {
    getAction(action)
        .ifPresent(s -> s.setMouseSensitivity(prefNode.getDouble(action.cmd(), defVal)));
  }

  public MediaSeries<DicomImageElement> getSelectedSeries() {
    ViewCanvas<DicomImageElement> pane = getSelectedViewPane();
    if (pane != null) {
      return pane.getSeries();
    }
    return null;
  }

  public JMenu getResetMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
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
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<? extends ComboItemListener<?>> presetAction = getAction(ActionW.PRESET);
      if (presetAction.isPresent()) {
        menu =
            presetAction
                .get()
                .createUnregisteredRadioMenu(ActionW.PRESET.getTitle(), ActionW.WINLEVEL.getIcon());
        GuiUtils.applySelectedIconEffect(menu);
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
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<? extends ComboItemListener<?>> lutShapeAction = getAction(ActionW.LUT_SHAPE);
      if (lutShapeAction.isPresent()) {
        menu = lutShapeAction.get().createUnregisteredRadioMenu(ActionW.LUT_SHAPE.getTitle());
      }
    }
    return menu;
  }

  public JMenu getZoomMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<SliderChangeListener> zoomAction = getAction(ActionW.ZOOM);
      if (zoomAction.isPresent()) {
        menu = new JMenu(ActionW.ZOOM.getTitle());
        menu.setIcon(ActionW.ZOOM.getIcon());
        GuiUtils.applySelectedIconEffect(menu);
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
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<SliderChangeListener> rotateAction = getAction(ActionW.ROTATION);
      if (rotateAction.isPresent()) {
        menu = new JMenu(Messages.getString("View2dContainer.orientation"));
        menu.setIcon(ActionW.ROTATION.getIcon());
        GuiUtils.applySelectedIconEffect(menu);
        menu.setEnabled(rotateAction.get().isActionEnabled());

        if (rotateAction.get().isActionEnabled()) {
          JMenuItem menuItem = new JMenuItem(ActionW.RESET.getTitle());
          menuItem.addActionListener(e -> rotateAction.get().setSliderValue(0));
          menu.add(menuItem);
          menuItem = new JMenuItem(Messages.getString("View2dContainer.-90"));
          menuItem.setIcon(ResourceUtil.getIcon(ActionIcon.ROTATE_COUNTERCLOCKWISE));
          GuiUtils.applySelectedIconEffect(menuItem);
          menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.ALT_DOWN_MASK));
          menuItem.addActionListener(
              e ->
                  rotateAction
                      .get()
                      .setSliderValue((rotateAction.get().getSliderValue() + 270) % 360));
          menu.add(menuItem);
          menuItem = new JMenuItem(Messages.getString("View2dContainer.+90"));
          menuItem.setIcon(ResourceUtil.getIcon(ActionIcon.ROTATE_CLOCKWISE));
          GuiUtils.applySelectedIconEffect(menuItem);
          menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK));
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
            GuiUtils.applySelectedIconEffect(menuItem);
            menuItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK));
            menu.add(menuItem);
          }
        }
      }
    }
    return menu;
  }

  public JMenu getCineMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<SliderCineListener> scrollAction = getAction(ActionW.SCROLL_SERIES);
      if (scrollAction.isPresent()) {
        menu = new JMenu(Messages.getString("cine"));
        GuiUtils.applySelectedIconEffect(menu);
        menu.setEnabled(scrollAction.get().isActionEnabled());

        if (scrollAction.get().isActionEnabled()) {
          JMenuItem menuItem = new JMenuItem(ActionW.CINESTART.getTitle());
          menuItem.setIcon(ResourceUtil.getIcon(ActionIcon.EXECUTE));
          GuiUtils.applySelectedIconEffect(menuItem);
          menuItem.setActionCommand(ActionW.CINESTART.cmd());
          menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0));
          menuItem.addActionListener(EventManager.getInstance());
          menu.add(menuItem);

          menuItem = new JMenuItem(ActionW.CINESTOP.getTitle());
          menuItem.setIcon(ResourceUtil.getIcon(ActionIcon.SUSPEND));
          GuiUtils.applySelectedIconEffect(menuItem);
          menuItem.setActionCommand(ActionW.CINESTOP.cmd());
          menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0));
          menuItem.addActionListener(EventManager.getInstance());
          menu.add(menuItem);

          Optional<ToggleButtonListener> sweepAction = getAction(ActionW.CINE_SWEEP);
          if (sweepAction.isPresent()) {
            menu.add(new JSeparator());
            menuItem =
                sweepAction
                    .get()
                    .createUnregisteredJCCheckBoxMenuItem(
                        ActionW.CINE_SWEEP.getTitle(), ResourceUtil.getIcon(ActionIcon.LOOP));
            GuiUtils.applySelectedIconEffect(menuItem);
            menu.add(menuItem);
          }
        }
      }
    }
    return menu;
  }

  public JMenu getSortStackMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
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
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<ComboItemListener<ByteLut>> lutAction = getAction(ActionW.LUT);
      if (lutAction.isPresent()) {
        menu =
            lutAction
                .get()
                .createUnregisteredRadioMenu(
                    ActionW.LUT.getTitle(), ResourceUtil.getIcon(ActionIcon.LUT));
      }
    }
    return menu;
  }

  public JCheckBoxMenuItem getLutInverseMenu(String prop) {
    JCheckBoxMenuItem menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<ToggleButtonListener> inverseLutAction = getAction(ActionW.INVERT_LUT);
      if (inverseLutAction.isPresent()) {
        menu =
            inverseLutAction
                .get()
                .createUnregisteredJCCheckBoxMenuItem(
                    ActionW.INVERT_LUT.getTitle(), ResourceUtil.getIcon(ActionIcon.INVERSE_LUT));
      }
    }
    return menu;
  }

  public JMenu getFilterMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<ComboItemListener<KernelData>> filterAction = getAction(ActionW.FILTER);
      if (filterAction.isPresent()) {
        menu =
            filterAction
                .get()
                .createUnregisteredRadioMenu(
                    Messages.getString("ImageTool.filter"),
                    ResourceUtil.getIcon(ActionIcon.FILTER));
      }
    }
    return menu;
  }

  // ***** OSGI commands: dcmview2d:cmd ***** //

  public void zoom(String[] argv) throws IOException {
    final String[] usage = {
      "Change the zoom value of the selected image", // NON-NLS
      "Usage: dcmview2d:zoom (set VALUE | increase NUMBER | decrease NUMBER)", // NON-NLS
      "  -s --set=VALUE        [decimal value]  set a new value from 0.0 to 12.0 (zoom magnitude, 0.0 => default, -200.0 => best fit, -100.0 => real size)", // NON-NLS
      "  -i --increase=NUMBER  increase of some amount", // NON-NLS
      "  -d --decrease=NUMBER  decrease of some amount", // NON-NLS
      "  -? --help             show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help")
        || !opt.isOnlyOneOptionActivate("set", "increase", "decrease")) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(() -> zoomCommand(opt, args));
  }

  private void zoomCommand(Option opt, List<String> args) {
    try {
      Optional<SliderChangeListener> zoomAction = getAction(ActionW.ZOOM);
      if (zoomAction.isPresent()) {
        if (opt.isSet("increase")) { // NON-NLS
          zoomAction
              .get()
              .setSliderValue(
                  zoomAction.get().getSliderValue() + opt.getNumber("increase")); // NON-NLS
        } else if (opt.isSet("decrease")) { // NON-NLS
          zoomAction
              .get()
              .setSliderValue(
                  zoomAction.get().getSliderValue() - opt.getNumber("decrease")); // NON-NLS
        } else if (opt.isSet("set")) {
          double val3 = Double.parseDouble(opt.get("set"));
          if (val3 <= 0.0) {
            firePropertyChange(
                ActionW.SYNCH.cmd(),
                null,
                new SynchEvent(getSelectedViewPane(), ActionW.ZOOM.cmd(), val3));
          } else {
            zoomAction.get().setRealValue(val3);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Zoom command: {}", args.get(0), e);
    }
  }

  public void wl(String[] argv) throws IOException {
    final String[] usage = {
      "Change the window/level values of the selected image (increase or decrease into a normalized range of 4096)", // NON-NLS
      "Usage: dcmview2d:wl -- WIN LEVEL", // NON-NLS
      "WIN and LEVEL are Integer. It is mandatory to have '--' (end of options) for negative values", // NON-NLS
      "  -? --help       show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.size() != 2) {
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          try {
            Optional<SliderChangeListener> windowAction = getAction(ActionW.WINDOW);
            Optional<SliderChangeListener> levelAction = getAction(ActionW.LEVEL);
            if (windowAction.isPresent() && levelAction.isPresent()) {
              int win = windowAction.get().getSliderValue() + Integer.parseInt(args.get(0));
              int level = levelAction.get().getSliderValue() + Integer.parseInt(args.get(1));
              windowAction.get().setSliderValue(win);
              levelAction.get().setSliderValue(level);
            }
          } catch (Exception e) {
            LOGGER.error("Window/level command: {} {}", args.get(0), args.get(1), e);
          }
        });
  }

  public void move(String[] argv) throws IOException {
    final String[] usage = {
      "Pan the selected image", // NON-NLS
      "Usage: dcmview2d:move -- X Y", // NON-NLS
      "X and Y are Integer. It is mandatory to have '--' (end of options) for negative values", // NON-NLS
      "  -? --help       show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.size() != 2) {
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          try {
            int valx = Integer.parseInt(args.get(0));
            int valy = Integer.parseInt(args.get(1));
            getAction(ActionW.PAN)
                .ifPresent(a -> a.setPoint(new PanPoint(PanPoint.State.MOVE, valx, valy)));

          } catch (Exception e) {
            LOGGER.error("Move (x,y) command: {} {}", args.get(0), args.get(1), e);
          }
        });
  }

  public void scroll(String[] argv) throws IOException {
    final String[] usage = {
      "Scroll into the images of the selected series", // NON-NLS
      "Usage: dcmview2d:scroll ( -s NUMBER | -i NUMBER | -d NUMBER)", // NON-NLS
      "  -s --set=NUMBER       set a new value from 1 to series size", // NON-NLS
      "  -i --increase=NUMBER  increase of some amount", // NON-NLS
      "  -d --decrease=NUMBER  decrease of some amount", // NON-NLS
      "  -? --help             show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);

    if (opt.isSet("help")
        || !opt.isOnlyOneOptionActivate("set", "increase", "decrease")) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          try {
            Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES);
            if (cineAction.isPresent() && cineAction.get().isActionEnabled()) {
              SliderCineListener moveTroughSliceAction = cineAction.get();
              if (opt.isSet("increase")) { // NON-NLS
                moveTroughSliceAction.setSliderValue(
                    moveTroughSliceAction.getSliderValue() + opt.getNumber("increase")); // NON-NLS
              } else if (opt.isSet("decrease")) { // NON-NLS
                moveTroughSliceAction.setSliderValue(
                    moveTroughSliceAction.getSliderValue() - opt.getNumber("decrease")); // NON-NLS
              } else if (opt.isSet("set")) {
                moveTroughSliceAction.setSliderValue(opt.getNumber("set"));
              }
            }
          } catch (Exception e) {
            LOGGER.error("Scroll command error:", e);
          }
        });
  }

  public void layout(String[] argv) throws IOException {
    final String[] usage = {
      "Select a split-screen layout", // NON-NLS
      "Usage: dcmview2d:layout ( -n NUMBER | -i ID )", // NON-NLS
      "  -n --number=NUMBER  select the best matching number of views", // NON-NLS
      "  -i --id=ID          select the layout from its identifier", // NON-NLS
      "  -? --help           show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);

    if (opt.isSet("help") || !opt.isOnlyOneOptionActivate("number", "id")) { // NON-NLS
      opt.usage();
      return;
    }
    GuiExecutor.execute(
        () -> {
          try {
            if (opt.isSet("number")) { // NON-NLS
              if (selectedView2dContainer != null) {
                GridBagLayoutModel val1 =
                    selectedView2dContainer.getBestDefaultViewLayout(
                        opt.getNumber("number")); // NON-NLS
                getAction(ActionW.LAYOUT).ifPresent(a -> a.setSelectedItem(val1));
              }
            } else if (opt.isSet("id")) {
              if (selectedView2dContainer != null) {
                GridBagLayoutModel val2 = selectedView2dContainer.getViewLayout(opt.get("id"));
                if (val2 != null) {
                  getAction(ActionW.LAYOUT).ifPresent(a -> a.setSelectedItem(val2));
                }
              }
            }
          } catch (Exception e) {
            LOGGER.error("Layout command error", e);
          }
        });
  }

  public void mouseLeftAction(String[] argv) throws IOException {
    final String[] usage = {
      "Change the mouse left action", // NON-NLS
      "Usage: dcmview2d:mouseLeftAction COMMAND", // NON-NLS
      "COMMAND is (sequence|winLevel|zoom|pan|rotation|crosshair|measure|draw|contextMenu|none)", // NON-NLS
      "  -? --help       show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.size() != 1) {
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          String command = args.get(0);
          if (command != null) {
            try {
              if (command.startsWith("session")) { // NON-NLS
                AuditLog.LOGGER.info("source:telnet {}", command);
              } else {
                AuditLog.LOGGER.info(
                    "source:telnet mouse:{} action:{}", MouseActions.T_LEFT, command);
                excecuteMouseAction(command);
              }
            } catch (Exception e) {
              LOGGER.error("Mouse command: {}", command, e);
            }
          }
        });
  }

  private void excecuteMouseAction(String command) {
    if (!command.equals(mouseActions.getAction(MouseActions.T_LEFT))) {
      mouseActions.setAction(MouseActions.T_LEFT, command);
      ImageViewerPlugin<DicomImageElement> view = getSelectedView2dContainer();
      if (view != null) {
        view.setMouseActions(mouseActions);
        final ViewerToolBar toolBar = view.getViewerToolBar();
        if (toolBar != null) {
          // Test if mouse action exist and if not NO_ACTION is set
          Feature<?> action = toolBar.getToolBarAction(command);
          if (action == null) {
            command = ActionW.NO_ACTION.cmd();
          }
          toolBar.changeButtonState(MouseActions.T_LEFT, command);
        }
      }
    }
  }

  public void synch(String[] argv) throws IOException {
    final String[] usage = {
      "Set a synchronization mode", // NON-NLS
      "Usage: dcmview2d:synch VALUE", // NON-NLS
      "VALUE is " // NON-NLS
          + View2dContainer.DEFAULT_SYNCH_LIST.stream()
              .map(SynchView::getCommand) // NON-NLS
              .collect(Collectors.joining("|", "(", ")")),
      "  -? --help       show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.size() != 1) {
      opt.usage();
      return;
    }
    GuiExecutor.execute(
        () -> {
          String command = args.get(0);
          if (command != null) {
            ImageViewerPlugin<DicomImageElement> view = getSelectedView2dContainer();
            if (view != null) {
              try {
                Optional<ComboItemListener<SynchView>> synchAction = getAction(ActionW.SYNCH);
                if (synchAction.isPresent()) {
                  for (SynchView synch : view.getSynchList()) {
                    if (synch.getCommand().equals(command)) {
                      synchAction.get().setSelectedItem(synch);
                      return;
                    }
                  }
                  throw new IllegalArgumentException(command + " not found!");
                }
              } catch (Exception e) {
                LOGGER.error("Synch command: {}", command, e);
              }
            }
          }
        });
  }

  public void reset(String[] argv) throws IOException {
    final String[] usage = {
      "Reset image display", // NON-NLS
      "Usage: dcmview2d:reset (-a | COMMAND...)", // NON-NLS
      "COMMAND is (winLevel|zoom|pan|rotation)", // NON-NLS
      "  -a --all        reset to original display", // NON-NLS
      "  -? --help       show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.isEmpty() && !opt.isSet("all")) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          if (opt.isSet("all")) { // NON-NLS
            reset(ResetTools.ALL);
          } else {
            for (String command : args) {
              try {
                if (ActionW.WINLEVEL.cmd().equals(command)) {
                  reset(ResetTools.WL);
                } else if (ActionW.ZOOM.cmd().equals(command)) {
                  reset(ResetTools.ZOOM);
                } else if (ActionW.PAN.cmd().equals(command)) {
                  reset(ResetTools.PAN);
                } else {
                  LOGGER.warn("Reset command not found: {}", command);
                }
              } catch (Exception e) {
                LOGGER.error("Reset command: {}", command, e);
              }
            }
          }
        });
  }
}
