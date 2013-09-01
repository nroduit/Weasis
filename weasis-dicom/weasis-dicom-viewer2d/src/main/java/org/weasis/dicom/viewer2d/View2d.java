/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.viewer2d;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.CropOperation;
import org.weasis.core.api.image.FilterOperation;
import org.weasis.core.api.image.FlipOperation;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.OperationsManager;
import org.weasis.core.api.image.PseudoColorOperation;
import org.weasis.core.api.image.RotationOperation;
import org.weasis.core.api.image.ShutterOperation;
import org.weasis.core.api.image.WindowLevelOperation;
import org.weasis.core.api.image.ZoomOperation;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.CalibrationView;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.PannerListener;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.BasicGraphic;
import org.weasis.core.ui.graphic.DragLayer;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.InvalidShapeException;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.core.ui.graphic.MeasureDialog;
import org.weasis.core.ui.graphic.PolygonGraphic;
import org.weasis.core.ui.graphic.RenderedImageLayer;
import org.weasis.core.ui.graphic.TempLayer;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.DefaultViewModel;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.core.ui.util.UriListFlavor;
import org.weasis.dicom.codec.DicomEncapDocSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomVideoSeries;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.display.CornerDisplay;
import org.weasis.dicom.codec.display.CornerInfoData;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.display.ModalityInfoData;
import org.weasis.dicom.codec.display.OverlayOperation;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.geometry.IntersectSlice;
import org.weasis.dicom.codec.geometry.LocalizerPoster;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.MimeSystemAppFactory;
import org.weasis.dicom.explorer.SeriesSelectionModel;
import org.weasis.dicom.explorer.pref.ModalityPrefView;
import org.weasis.dicom.viewer2d.KOManager.KOViewButton;
import org.weasis.dicom.viewer2d.KOManager.KOViewButton.eState;

public class View2d extends DefaultView2d<DicomImageElement> {
    private static final Logger LOGGER = LoggerFactory.getLogger(View2d.class);

    public static final ImageIcon KO_ICON = new ImageIcon(View2d.class.getResource("/icon/22x22/dcm-KO.png"));
    public static final ImageIcon PR_ICON = new ImageIcon(View2d.class.getResource("/icon/22x22/dcm-PR.png"));

    private final Dimension oldSize;
    private final ContextMenuHandler contextMenuHandler = new ContextMenuHandler();

    protected final KOViewButton koStarButton;

    public View2d(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        OperationsManager manager = imageLayer.getOperationsManager();
        manager.addImageOperationAction(new WindowLevelOperation());
        manager.addImageOperationAction(new OverlayOperation());
        manager.addImageOperationAction(new FilterOperation());
        manager.addImageOperationAction(new PseudoColorOperation());
        manager.addImageOperationAction(new ShutterOperation());
        // Zoom and Rotation must be the last operations for the lens
        manager.addImageOperationAction(new ZoomOperation());
        manager.addImageOperationAction(new RotationOperation());
        manager.addImageOperationAction(new FlipOperation());

        infoLayer = new InfoLayer(this);
        DragLayer layer = new DragLayer(getLayerModel(), AbstractLayer.CROSSLINES);
        layer.setLocked(true);
        getLayerModel().addLayer(layer);
        layer = new DragLayer(getLayerModel(), AbstractLayer.MEASURE);
        getLayerModel().addLayer(layer);
        TempLayer layerTmp = new TempLayer(getLayerModel());
        getLayerModel().addLayer(layerTmp);

        oldSize = new Dimension(0, 0);

        // TODO should be a lazy instantiation
        getViewButtons().add(KOManager.buildKoSelectionButton(this));
        koStarButton = KOManager.buildKoStarButton(this);
        koStarButton.setPosition(GridBagConstraints.NORTHEAST);
        getViewButtons().add(koStarButton);
    }

    @Override
    public void registerDefaultListeners() {
        super.registerDefaultListeners();
        setTransferHandler(new SequenceHandler());
        addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent e) {
                Double currentZoom = (Double) actionsInView.get(ActionW.ZOOM.cmd());
                // Resize in best fit window only if the previous value is also a best fit value.
                if (currentZoom <= 0.0) {
                    zoom(0.0);
                    center();
                }
                if (panner != null) {
                    panner.updateImageSize();
                }
                if (lens != null) {
                    int w = getWidth();
                    int h = getHeight();
                    if (w != 0 && h != 0) {
                        Rectangle bound = lens.getBounds();
                        if (oldSize.width != 0 && oldSize.height != 0) {
                            int centerx = bound.width / 2;
                            int centery = bound.height / 2;
                            bound.x = (bound.x + centerx) * w / oldSize.width - centerx;
                            bound.y = (bound.y + centery) * h / oldSize.height - centery;
                            lens.setLocation(bound.x, bound.y);
                        }
                        oldSize.width = w;
                        oldSize.height = h;
                    }
                    lens.updateZoom();
                }
            }
        });
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();

        actionsInView.put(ActionW.PRESET.cmd(), null);
        actionsInView.put(ActionW.DEFAULT_PRESET.cmd(), true);
        actionsInView.put(ActionW.LUT_SHAPE.cmd(), LutShape.LINEAR);
        actionsInView.put(ActionW.SORTSTACK.cmd(), SortSeriesStack.instanceNumber);
        actionsInView.put(ActionW.IMAGE_OVERLAY.cmd(), true);
        actionsInView.put(ActionW.IMAGE_PIX_PADDING.cmd(), true);
        actionsInView.put(ActionW.VIEWINGPROTOCOL.cmd(), Modality.ImageModality);
        actionsInView.put(ActionW.PR_STATE.cmd(), null);

        // Preprocessing
        actionsInView.put(ActionW.CROP.cmd(), null);

        setDefaultKOActionWState();
    }

    protected void setDefaultKOActionWState() {

        actionsInView.put(ActionW.KO_FILTER.cmd(), false);
        actionsInView.put(ActionW.KO_STATE.cmd(), false);

        // Set the more recent KO by default
        Collection<KOSpecialElement> koElements = DicomModel.getKoSpecialElements(getSeries());
        Object defaultKO = (koElements != null && koElements.size() > 0) ? koElements.iterator().next() : null;
        actionsInView.put(ActionW.KO_SELECTION.cmd(), defaultKO);

        if (defaultKO instanceof KOSpecialElement) {
            DicomImageElement dicomImage = getImage();

            if (dicomImage != null) {
                String sopInstanceUID = (String) dicomImage.getTagValue(TagW.SOPInstanceUID);
                String seriesInstanceUID = (String) dicomImage.getTagValue(TagW.SeriesInstanceUID);

                if (sopInstanceUID != null && seriesInstanceUID != null) {
                    KOSpecialElement koElement = (KOSpecialElement) defaultKO;
                    Set<String> sopInstanceUIDSet = koElement.getReferencedSOPInstanceUIDSet(seriesInstanceUID);

                    if (sopInstanceUIDSet != null && sopInstanceUIDSet.contains(sopInstanceUID)) {
                        actionsInView.put(ActionW.KO_STATE.cmd(), true);
                    }
                }
            }
        }

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (series == null) {
            return;
        }
        final String name = evt.getPropertyName();
        if (name.equals(ActionW.SYNCH.cmd())) {
            SynchEvent synch = (SynchEvent) evt.getNewValue();
            SynchData synchData = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
            if (synchData != null && Mode.None.equals(synchData.getMode())) {
                return;
            }
            for (Entry<String, Object> entry : synch.getEvents().entrySet()) {
                final String command = entry.getKey();
                final Object val = entry.getValue();
                if (synchData != null && !synchData.isActionEnable(command)) {
                    continue;
                }

                if (command.equals(ActionW.PRESET.cmd())) {
                    actionsInView.put(ActionW.PRESET.cmd(), val);

                    if (val instanceof PresetWindowLevel) {
                        PresetWindowLevel preset = (PresetWindowLevel) val;
                        DicomImageElement img = getImage();
                        if (img == null || !img.containsPreset(preset)) {
                            // When series synchronization, do not synch preset from other series
                            actionsInView.put(ActionW.PRESET.cmd(), null);
                        }
                        actionsInView.put(ActionW.WINDOW.cmd(), preset.getWindow());
                        actionsInView.put(ActionW.LEVEL.cmd(), preset.getLevel());
                        actionsInView.put(ActionW.LUT_SHAPE.cmd(), preset.getLutShape());
                        imageLayer.updateImageOperation(WindowLevelOperation.name);
                    }
                } else if (command.equals(ActionW.DEFAULT_PRESET.cmd())) {
                    actionsInView.put(ActionW.DEFAULT_PRESET.cmd(), val);
                } else if (command.equals(ActionW.LUT_SHAPE.cmd())) {
                    actionsInView.put(ActionW.LUT_SHAPE.cmd(), val);
                    imageLayer.updateImageOperation(WindowLevelOperation.name); // usefull ???
                } else if (command.equals(ActionW.SORTSTACK.cmd())) {
                    actionsInView.put(ActionW.SORTSTACK.cmd(), val);
                    sortStack(getCurrentSortComparator());
                } else if (command.equals(ActionW.VIEWINGPROTOCOL.cmd())) {
                    actionsInView.put(ActionW.VIEWINGPROTOCOL.cmd(), val);
                    repaint();
                } else if (command.equals(ActionW.INVERSESTACK.cmd())) {
                    actionsInView.put(ActionW.INVERSESTACK.cmd(), val);
                    sortStack(getCurrentSortComparator());
                } else if (command.equals(ActionW.KO_SELECTION.cmd())) {
                    setKeyObjectSelection(val);
                } else if (command.equals(ActionW.KO_FILTER.cmd())) {
                    setKeyObjectSelectionFilterState((Boolean) val);
                } else if (command.equals(ActionW.KO_STATE.cmd())) {
                    KOManager.toogleKoState(this);
                    updateKOselectedState();
                }
            }
        } else if (name.equals(ActionW.IMAGE_OVERLAY.cmd())) {
            actionsInView.put(ActionW.IMAGE_OVERLAY.cmd(), evt.getNewValue());
            imageLayer.updateImageOperation(OverlayOperation.name);
        }
    }

    @Override
    public void reset() {
        super.reset();
        setPresentationState(null);
    }

    @Deprecated
    public void setKeyObjectSelection(Object newVal) {

        Filter<DicomImageElement> sopInstanceUIDFilter = null;

        if ((Boolean) getActionValue(ActionW.KO_FILTER.cmd())) {
            sopInstanceUIDFilter =
                (newVal instanceof KOSpecialElement) ? ((KOSpecialElement) newVal).getSOPInstanceUIDFilter() : null;
        }

        actionsInView.put(ActionW.KO_SELECTION.cmd(), newVal);
        actionsInView.put(ActionW.FILTERED_SERIES.cmd(), sopInstanceUIDFilter);

        updateKOSelectionChange();

        // eventManager.updateComponentsListener(this); // needed to take care of the FILTERED_SERIES changes
        // setSeries(series, getImage());
    }

    public void setKeyObjectSelectionFilterState(Boolean newState) {

        Filter<DicomImageElement> sopInstanceUIDFilter = null;

        if (newState) {
            Object selectedKO = getActionValue(ActionW.KO_SELECTION.cmd());
            sopInstanceUIDFilter =
                (selectedKO instanceof KOSpecialElement) ? ((KOSpecialElement) selectedKO).getSOPInstanceUIDFilter()
                    : null;
        }

        actionsInView.put(ActionW.KO_FILTER.cmd(), newState);
        actionsInView.put(ActionW.FILTERED_SERIES.cmd(), sopInstanceUIDFilter);

        updateKOSelectionChange();

        // eventManager.updateComponentsListener(this);
        // setSeries(series, getImage());
    }

    void setPresentationState(PRSpecialElement val) {
        ImageViewerPlugin<DicomImageElement> pane = eventManager.getSelectedView2dContainer();
        if (pane != null) {
            pane.resetMaximizedSelectedImagePane(this);
        }
        // TODO use PR reader for other frame when changing image of the series
        PresentationStateReader pr = val == null ? null : new PresentationStateReader(val);
        actionsInView.put(ActionW.PR_STATE.cmd(), val);
        actionsInView.put(ActionW.PREPROCESSING.cmd(), null);

        ArrayList<AbstractLayer.Identifier> dcmLayers =
            (ArrayList<AbstractLayer.Identifier>) actionsInView.get(PresentationStateReader.TAG_DICOM_LAYERS);
        if (dcmLayers != null) {
            PRManager.deleteDicomLayers(dcmLayers, getLayerModel());
            actionsInView.remove(PresentationStateReader.TAG_DICOM_LAYERS);
        }
        DicomImageElement m = getImage();

        if (m != null) {
            // Restore the original image pixel size
            double[] prPixSize = (double[]) actionsInView.get(PresentationStateReader.TAG_OLD_PIX_SIZE);
            if (prPixSize != null && prPixSize.length == 2) {
                m.setPixelSize(prPixSize[0], prPixSize[1]);
                actionsInView.remove(PresentationStateReader.TAG_OLD_PIX_SIZE);
            }

            // Restore Modality LUT Sequence
            if (actionsInView.containsKey(PresentationStateReader.TAG_OLD_ModalityLUTData)) {
                m.setTag(TagW.ModalityLUTData, actionsInView.get(PresentationStateReader.TAG_OLD_ModalityLUTData));
                actionsInView.remove(PresentationStateReader.TAG_OLD_ModalityLUTData);
            } else {
                if (actionsInView.containsKey(PresentationStateReader.TAG_OLD_RescaleSlope)) {
                    m.setTag(TagW.RescaleSlope, actionsInView.get(PresentationStateReader.TAG_OLD_RescaleSlope));
                    m.setTag(TagW.RescaleIntercept, actionsInView.get(PresentationStateReader.TAG_OLD_RescaleIntercept));
                    m.setTag(TagW.RescaleType, actionsInView.get(PresentationStateReader.TAG_OLD_RescaleType));
                    actionsInView.remove(PresentationStateReader.TAG_OLD_RescaleSlope);
                    actionsInView.remove(PresentationStateReader.TAG_OLD_RescaleIntercept);
                    actionsInView.remove(PresentationStateReader.TAG_OLD_RescaleType);
                }
            }

            // Restore presets
            actionsInView.remove(PresentationStateReader.PR_PRESETS);

            // Reset crop
            final Rectangle modelArea = getImageBounds(m);
            if (!modelArea.equals(getViewModel().getModelArea())) {
                ((DefaultViewModel) getViewModel()).adjustMinViewScaleFromImage(modelArea.width, modelArea.height);
                getViewModel().setModelArea(modelArea);
            }
        }
        // If no Presentation State use the current image
        if (pr == null) {
            // Keeps KO properties (series level)
            Object ko = actionsInView.get(ActionW.KO_SELECTION.cmd());
            Object filter = actionsInView.get(ActionW.FILTERED_SERIES.cmd());
            initActionWState();
            setActionsInView(ActionW.KO_SELECTION.cmd(), ko);
            setActionsInView(ActionW.FILTERED_SERIES.cmd(), filter);
            if (ActionState.NONE_SERIES.equals(val)) {
                // Keeps no PS property (for all the series)
                actionsInView.put(ActionW.PR_STATE.cmd(), val);
            }
            setDefautWindowLevel(getImage());
            setShutter(m);
        } else {
            applyPresentationState(pr, m);
        }
        imageLayer.setPreprocessing((OperationsManager) actionsInView.get(ActionW.PREPROCESSING.cmd()));
        imageLayer.updateAllImageOperations();
        resetZoom();
        eventManager.updateComponentsListener(this);
    }

    private void applyPresentationState(PresentationStateReader reader, DicomImageElement img) {
        HashMap<TagW, Object> tags = reader.getDicom().geTags();
        // Set Modality LUT before creating presets
        Object mLUT = tags.get(TagW.ModalityLUTData);
        if (mLUT != null) {
            actionsInView.put(PresentationStateReader.TAG_OLD_ModalityLUTData, img.getTagValue(TagW.ModalityLUTData));
            img.setTag(TagW.ModalityLUTData, mLUT);
        } else {
            Object rs = tags.get(TagW.RescaleSlope);
            Object ri = tags.get(TagW.RescaleIntercept);
            Object rt = tags.get(TagW.RescaleType);
            if (rs != null && ri != null && rt != null) {
                actionsInView.put(PresentationStateReader.TAG_OLD_RescaleSlope, img.getTagValue(TagW.RescaleSlope));
                actionsInView.put(PresentationStateReader.TAG_OLD_RescaleIntercept,
                    img.getTagValue(TagW.RescaleIntercept));
                actionsInView.put(PresentationStateReader.TAG_OLD_RescaleType, img.getTagValue(TagW.RescaleType));
                img.setTag(TagW.RescaleSlope, rs);
                img.setTag(TagW.RescaleIntercept, ri);
                img.setTag(TagW.RescaleType, rt);
            }
        }

        PRManager.applyPresentationState(this, reader, img);
        actionsInView.put(ActionW.ROTATION.cmd(), reader.getTagValue(ActionW.ROTATION.cmd(), 0));
        actionsInView.put(ActionW.FLIP.cmd(), reader.getTagValue(ActionW.FLIP.cmd(), false));

        List<PresetWindowLevel> presets = (List<PresetWindowLevel>) reader.getTagValue(ActionW.PRESET.cmd(), null);
        if (presets != null && presets.size() > 0) {
            PresetWindowLevel p = presets.get(0);
            actionsInView.put(ActionW.WINDOW.cmd(), p.getWindow());
            actionsInView.put(ActionW.LEVEL.cmd(), p.getLevel());
            actionsInView.put(PresentationStateReader.PR_PRESETS, presets);
            actionsInView.put(ActionW.PRESET.cmd(), p);
            actionsInView.put(ActionW.LUT_SHAPE.cmd(), p.getLutShape());
            actionsInView.put(ActionW.DEFAULT_PRESET.cmd(), true);
        }

        setShutter(reader.getDicom());
        Rectangle area = (Rectangle) reader.getTagValue(ActionW.CROP.cmd(), null);

        double[] prPixSize = (double[]) reader.getTagValue(TagW.PixelSpacing.getName(), null);
        if (prPixSize != null && prPixSize.length == 2) {
            actionsInView.put(PresentationStateReader.TAG_OLD_PIX_SIZE, img.getDisplayPixelSize());
            img.setPixelSize(prPixSize[0], prPixSize[1]);
        }
        if (area != null) {
            Area shape = (Area) actionsInView.get(TagW.ShutterFinalShape.getName());
            if (shape != null) {
                Area trArea = new Area(shape);
                trArea.transform(AffineTransform.getTranslateInstance(-area.getX(), -area.getY()));
                actionsInView.put(TagW.ShutterFinalShape.getName(), trArea);
            }
            RenderedImage source = getSourceImage();
            if (source != null) {
                area =
                    area.intersection(new Rectangle(source.getMinX(), source.getMinY(), source.getWidth(), source
                        .getHeight()));
                if (area.width > 1 && area.height > 1 && !area.equals(getViewModel().getModelArea())) {
                    ((DefaultViewModel) getViewModel()).adjustMinViewScaleFromImage(area.width, area.height);
                    getViewModel().setModelArea(area);
                    OperationsManager manager = new OperationsManager(new ImageOperation() {

                        @Override
                        public RenderedImage getSourceImage() {
                            ImageElement image = getImage();
                            if (image == null) {
                                return null;
                            }
                            return image.getImage(null);
                        }

                        @Override
                        public ImageElement getImage() {
                            return View2d.this.getImage();
                        }

                        @Override
                        public Object getActionValue(String action) {
                            if (action == null) {
                                return null;
                            }
                            return actionsInView.get(action);
                        }

                        @Override
                        public MediaSeries getSeries() {
                            return View2d.this.getSeries();
                        }
                    });
                    manager.addImageOperationAction(new CropOperation());
                    actionsInView.put(ActionW.PREPROCESSING.cmd(), manager);
                }
            }
        }
        actionsInView.put(ActionW.CROP.cmd(), area);
        double zoom = (Double) reader.getTagValue(ActionW.ZOOM.cmd(), 0.0d);
        actionsInView.put(ActionW.ZOOM.cmd(), zoom == 0.0 ? -getBestFitViewScale() : zoom);

    }

    void updateKOButtonVisibleState() {

        Collection<KOSpecialElement> koElements = DicomModel.getKoSpecialElements(getSeries());
        boolean koElementExist = (koElements != null && koElements.size() > 0);

        boolean needToRepaint = false;

        for (ViewButton vb : getViewButtons()) {
            if (vb != null && vb.getIcon() == View2d.KO_ICON) {
                if (vb.isVisible() != koElementExist) {
                    vb.setVisible(koElementExist);
                    // repaint(getExtendedActionsBound());
                    needToRepaint = true;
                }
                break;
            }
        }

        if (koStarButton.isVisible() != koElementExist) {
            koStarButton.setVisible(koElementExist);

            // Force the KO annotation preference to be set visible or not depending if a KO object is loaded with any
            // reference on the series in this view, or if new KO were created for this patient
            infoLayer.setDisplayPreferencesValue(AnnotationsLayer.KEY_OBJECT, koElementExist);
            eventManager.fireSeriesViewerListeners(new SeriesViewerEvent(eventManager.getSelectedView2dContainer(),
                null, null, EVENT.SELECT_VIEW)); // call iniTreeValues in DisplayTool to update checkBox

            needToRepaint = true;
        }

        // if (koElementExist) {
        // needToRepaint = updateKOselectedState();
        // }

        // if (needToRepaint) {
        repaint();
        // }
    }

    /**
     * @return true if the state has changed and if the view or at least the KO button need to be repaint
     */
    @Deprecated
    protected boolean updateKOselectedState() {

        // TODO - should not be called here but in a manager listener dedicated to this job

        eState previousState = koStarButton.getState();

        // evaluate koSelection status for every Image change
        KOViewButton.eState newSelectionState = eState.UNSELECTED;

        Object selectedKO = getActionValue(ActionW.KO_SELECTION.cmd());
        DicomImageElement dicomImage = getImage();

        String sopInstanceUID = (String) dicomImage.getTagValue(TagW.SOPInstanceUID);
        String seriesInstanceUID = (String) dicomImage.getTagValue(TagW.SeriesInstanceUID);

        if (dicomImage != null && sopInstanceUID != null && seriesInstanceUID != null) {

            if ((selectedKO instanceof KOSpecialElement)) {
                KOSpecialElement koElement = (KOSpecialElement) selectedKO;
                Set<String> sopInstanceUIDSet = koElement.getReferencedSOPInstanceUIDSet(seriesInstanceUID);

                if (sopInstanceUIDSet != null && sopInstanceUIDSet.contains(sopInstanceUID)) {
                    newSelectionState = eState.SELECTED;
                }
            } else {
                Collection<KOSpecialElement> koElements = DicomModel.getKoSpecialElements(getSeries());

                if (koElements != null) {
                    for (KOSpecialElement koElement : koElements) {
                        Set<String> sopInstanceUIDSet = koElement.getReferencedSOPInstanceUIDSet(seriesInstanceUID);

                        if (sopInstanceUIDSet != null && sopInstanceUIDSet.contains(sopInstanceUID)) {
                            newSelectionState = eState.EXIST;
                            break;
                        }
                    }
                }
            }
        }

        koStarButton.setState(newSelectionState);

        // TODO - fix this DIRTY code
        Boolean selected = koStarButton.getState().equals(eState.SELECTED) ? true : false;
        actionsInView.put(ActionW.KO_STATE.cmd(), selected);
        ((ToggleButtonListener) eventManager.getAction(ActionW.KO_STATE)).setSelectedWithoutTriggerAction(selected);

        return (previousState != newSelectionState);
    }

    protected Rectangle getKOStarButtonBound(Graphics2D g2d) {

        // /////////////////////////////////////////////////////////////////////////////////////////////////
        // TODO - find a better way to get infoLayer TOP-RIGHT available position for drawing something else

        Modality mod = Modality.getModality((String) getSeries().getTagValue(TagW.Modality));
        ModalityInfoData infoData = ModalityPrefView.getModlatityInfos(mod);
        CornerInfoData corner = infoData.getCornerInfo(CornerDisplay.TOP_RIGHT);

        final float fontHeight = FontTools.getAccurateFontHeight(g2d);
        boolean anonymize = infoLayer.getDisplayPreferences(AnnotationsLayer.ANONYM_ANNOTATIONS);

        float drawY = 0;
        TagW[] infos = corner.getInfos();
        for (TagW tag : infos) {
            if (tag != null && (!anonymize || tag.getAnonymizationType() != 1)) {
                drawY += fontHeight;
            }
        }

        // /////////////////////////////////////////////////////////////////////////////////////////////////

        Icon koStarIcon = koStarButton.getIcon();

        int xPos = getWidth() - koStarIcon.getIconWidth() - infoLayer.getBorder();
        int yPos = (int) (Math.round(drawY) + (0.3 * fontHeight));

        return new Rectangle(xPos, yPos, koStarIcon.getIconWidth(), koStarIcon.getIconHeight());
    }

    @Deprecated
    @SuppressWarnings("unchecked")
    public void updateKOSelectionChange() {

        // TODO should not be called here but from event manager or view2dContainer !!!!
        // !!!!! this call must be done only from a the selected viewPane

        DicomSeries dicomSeries = (DicomSeries) getSeries();
        DicomImageElement currentImg = getImage();

        Object selectedKO = getActionValue(ActionW.KO_SELECTION.cmd());

        if (currentImg != null && dicomSeries != null && selectedKO instanceof KOSpecialElement) {

            ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
            SliderCineListener sliceAction = null;
            if (seqAction instanceof SliderCineListener) {
                sliceAction = (SliderCineListener) seqAction;
            } else {
                return;
            }

            if ((Boolean) getActionValue(ActionW.KO_FILTER.cmd())) {

                int newImageIndex = getFrameIndex();
                // The getFrameIndex() return a valid index for the current image displayed according to the current
                // FILTERED_SERIES and the current SortComparator

                // If the current image is not part anymore of the KO FILTERED_SERIES then it has been removed from the
                // selection. Hence, another image should be selected, that is the nearest.

                Filter<DicomImageElement> dicomFilter =
                    (Filter<DicomImageElement>) getActionValue(ActionW.FILTERED_SERIES.cmd());

                if (newImageIndex < 0) {

                    double[] val = (double[]) currentImg.getTagValue(TagW.SlicePosition);
                    double location = val[0] + val[1] + val[2];
                    Double offset = (Double) getActionValue(ActionW.STACK_OFFSET.cmd());
                    if (offset != null) {
                        location += offset;
                    }

                    if (dicomSeries.size(dicomFilter) > 0) {

                        newImageIndex =
                            dicomSeries.getNearestImageIndex(location, getTileOffset(), dicomFilter,
                                getCurrentSortComparator());
                    } else {
                        // If there is no more image in KO series filtered then disable the KO_FILTER
                        dicomFilter = null;
                        actionsInView.put(ActionW.KO_FILTER.cmd(), false);
                        actionsInView.put(ActionW.FILTERED_SERIES.cmd(), dicomFilter);
                        newImageIndex = getFrameIndex();
                    }
                }

                // Update the sliceAction component for the current selected View which fire a SCROLL_SERIES changeEvent
                // (see EventManager -> stateChanged()). This change will be handled by any DefaultView2d
                // object that listen this event change if the synchview Action is Enable

                // In case a new KO selection has been added the FILTERED_SERIES size will be updated in consequence
                // This avoids to call eventManager.updateComponentsListener since only moveTroughSliceAction should be
                // updated

                sliceAction.setMinMaxValue(1, dicomSeries.size(dicomFilter), newImageIndex + 1);
            }

            DicomModel dicomModel = (DicomModel) dicomSeries.getTagValue(TagW.ExplorerModel);

            // Fire an event since any view in the View2dContainner may have its KO selected state changed
            dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Update, this, null,
                selectedKO));
        }

        updateKOButtonVisibleState();
    }

    @Override
    public void setSeries(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {
        super.setSeries(series, selectedDicom);

        // TODO
        // JFrame frame = new JFrame();
        // frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        // JPanel pane = new JPanel();

        // layeredPane.setPreferredSize(new Dimension(200, 200));
        // pane.remove(layeredPane);
        // layeredPane.removeAll();
        // panner.setSize(200, 200);
        // layeredPane.add(panner, JLayeredPane.DEFAULT_LAYER);
        // pane.add(layeredPane);
        // panner.setBounds(0, 0, 200, 200);
        // pane.setBounds(0, 0, 200, 200);
        // frame.add(pane);
        // frame.pack();
        // frame.setVisible(true);

        if (series != null) {
            AuditLog.LOGGER.info("open:series nb:{} modality:{}", series.getSeriesNumber(), //$NON-NLS-1$
                series.getTagValue(TagW.Modality));
        }

        updateKOButtonVisibleState();
    }

    @Override
    protected void setImage(DicomImageElement img) {
        boolean newImg = img != null && !img.equals(imageLayer.getSourceImage());

        super.setImage(img);

        updateButtonState(img, newImg);
    }

    private void updateButtonState(DicomImageElement img, boolean newImg) {
        if (img == null || newImg) {
            // Remove old PR button
            for (int i = getViewButtons().size() - 1; i >= 0; i--) {
                ViewButton vb = getViewButtons().get(i);
                if (vb != null && (vb.getIcon() == View2d.PR_ICON)) {
                    getViewButtons().remove(i);
                }
            }
        }
        if (newImg) {
            Object oldPR = getActionValue(ActionW.PR_STATE.cmd());
            ViewButton prButton = PRManager.buildPrSelection(this, series, img);
            if (prButton != null) {
                getViewButtons().add(prButton);
            } else if (oldPR instanceof PRSpecialElement) {
                setPresentationState(null);
            }
        }
    }

    @Override
    protected void setDefautWindowLevel(DicomImageElement img) {
        if (img == null) {
            return;
        }

        if (!img.isImageAvailable()) {
            // Ensure to load image before calling the default preset that (requires pixel min and max)
            img.getImage();
        }
        boolean pixelPadding = JMVUtils.getNULLtoTrue(getActionValue(ActionW.IMAGE_PIX_PADDING.cmd()));
        PresetWindowLevel preset = img.getDefaultPreset(pixelPadding);
        if (preset != null) {
            actionsInView.put(ActionW.PRESET.cmd(), preset);
            actionsInView.put(ActionW.WINDOW.cmd(), preset.getWindow());
            actionsInView.put(ActionW.LEVEL.cmd(), preset.getLevel());
            actionsInView.put(ActionW.LUT_SHAPE.cmd(), preset.getLutShape());
            actionsInView.put(ActionW.DEFAULT_PRESET.cmd(), true);
        } else {
            super.setDefautWindowLevel(img);
        }
    }

    protected void sortStack(Comparator<DicomImageElement> sortComparator) {
        if (sortComparator != null) {
            // Only refresh UI components, Fix WEA-222
            eventManager.updateComponentsListener(this);
        }
    }

    @Override
    protected void computeCrosslines(double location) {
        DicomImageElement image = this.getImage();
        if (image != null) {
            GeometryOfSlice sliceGeometry = image.getSliceGeometry();
            if (sliceGeometry != null) {
                DefaultView2d<DicomImageElement> view2DPane = eventManager.getSelectedViewPane();
                MediaSeries<DicomImageElement> selSeries = view2DPane == null ? null : view2DPane.getSeries();
                if (selSeries != null) {
                    // Get the current image of the selected Series
                    DicomImageElement selImage = view2DPane.getImage();
                    // Get the first and the last image of the selected Series according to Slice Location

                    DicomImageElement firstImage = null;
                    DicomImageElement lastImage = null;
                    double min = Double.MAX_VALUE;
                    double max = -Double.MAX_VALUE;
                    final Iterable<DicomImageElement> list =
                        selSeries.getMedias(
                            (Filter<DicomImageElement>) view2DPane.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                            getCurrentSortComparator());
                    synchronized (selSeries) {
                        for (DicomImageElement dcm : list) {
                            double[] loc = (double[]) dcm.getTagValue(TagW.SlicePosition);
                            if (loc != null) {
                                double position = loc[0] + loc[1] + loc[2];
                                if (min > position) {
                                    min = position;
                                    firstImage = dcm;
                                }
                                if (max < position) {
                                    max = position;
                                    lastImage = dcm;
                                }
                            }
                        }
                    }

                    IntersectSlice slice = new IntersectSlice(sliceGeometry);
                    // IntersectVolume slice = new IntersectVolume(sliceGeometry);
                    if (firstImage != null) {
                        addCrossline(firstImage, slice, false);
                    }
                    if (lastImage != null) {
                        addCrossline(lastImage, slice, false);
                    }
                    if (selImage != null) {
                        addCrossline(selImage, slice, true);
                    }
                    repaint();
                }
            }
        }

    }

    protected void addCrossline(DicomImageElement selImage, LocalizerPoster localizer, boolean fill) {
        GeometryOfSlice sliceGeometry = selImage.getSliceGeometry();
        if (sliceGeometry != null) {
            List<Point2D.Double> pts = localizer.getOutlineOnLocalizerForThisGeometry(sliceGeometry);
            if (pts != null && pts.size() > 0) {
                Color color = fill ? Color.blue : Color.cyan;
                try {
                    Graphic graphic =
                        pts.size() == 2 ? new LineGraphic(pts.get(0), pts.get(1), 1.0f, color, false)
                            : new PolygonGraphic(pts, color, 1.0f, false, false);
                    AbstractLayer layer = getLayerModel().getLayer(AbstractLayer.CROSSLINES);
                    if (layer != null) {
                        layer.addGraphic(graphic);
                    }
                } catch (InvalidShapeException e) {
                    LOGGER.error(e.getMessage());
                }
            }
        }
    }

    @Override
    public synchronized void enableMouseAndKeyListener(MouseActions actions) {
        disableMouseAndKeyListener();
        iniDefaultMouseListener();
        iniDefaultKeyListener();
        // Set the butonMask to 0 of all the actions
        resetMouseAdapter();

        this.setCursor(AbstractLayerModel.DEFAULT_CURSOR);

        addMouseAdapter(actions.getLeft(), InputEvent.BUTTON1_DOWN_MASK); // left mouse button
        if (actions.getMiddle().equals(actions.getLeft())) {
            // If mouse action is already registered, only add the modifier mask
            MouseActionAdapter adapter = getMouseAdapter(actions.getMiddle());
            if (adapter != null) {
                adapter.setButtonMaskEx(adapter.getButtonMaskEx() | InputEvent.BUTTON2_DOWN_MASK);
            }
        } else {
            addMouseAdapter(actions.getMiddle(), InputEvent.BUTTON2_DOWN_MASK);// middle mouse button
        }
        if (actions.getRight().equals(actions.getLeft()) || actions.getRight().equals(actions.getMiddle())) {
            // If mouse action is already registered, only add the modifier mask
            MouseActionAdapter adapter = getMouseAdapter(actions.getRight());
            if (adapter != null) {
                adapter.setButtonMaskEx(adapter.getButtonMaskEx() | InputEvent.BUTTON3_DOWN_MASK);
            }
        } else {
            addMouseAdapter(actions.getRight(), InputEvent.BUTTON3_DOWN_MASK); // right mouse button
        }
        this.addMouseWheelListener(getMouseAdapter(actions.getWheel()));

        if (lens != null) {
            lens.enableMouseListener();
        }
    }

    private void addMouseAdapter(String actionName, int buttonMask) {
        MouseActionAdapter adapter = getMouseAdapter(actionName);
        if (adapter == null) {
            return;
        }
        adapter.setButtonMaskEx(adapter.getButtonMaskEx() | buttonMask);
        if (adapter == mouseClickHandler) {
            this.addKeyListener(drawingsKeyListeners);
        } else if (adapter instanceof PannerListener) {
            ((PannerListener) adapter).reset();
            this.addKeyListener((PannerListener) adapter);
        }

        if (actionName.equals(ActionW.WINLEVEL.cmd())) {
            // For window/level action set window action on x axis
            MouseActionAdapter win = getAction(ActionW.WINDOW);
            if (win != null) {
                win.setButtonMaskEx(win.getButtonMaskEx() | buttonMask);
                win.setMoveOnX(true);
                this.addMouseListener(win);
                this.addMouseMotionListener(win);
            }
            // set level action with inverse progression (move the cursor down will decrease the values)
            adapter.setInverse(true);
        } else if (actionName.equals(ActionW.WINDOW.cmd())) {
            adapter.setMoveOnX(false);
        } else if (actionName.equals(ActionW.LEVEL.cmd())) {
            adapter.setInverse(true);
        }
        this.addMouseListener(adapter);
        this.addMouseMotionListener(adapter);
    }

    protected MouseActionAdapter getMouseAdapter(String action) {
        if (action.equals(ActionW.MEASURE.cmd())) {
            return mouseClickHandler;
        } else if (action.equals(ActionW.PAN.cmd())) {
            return getAction(ActionW.PAN);
        } else if (action.equals(ActionW.CONTEXTMENU.cmd())) {
            return contextMenuHandler;
        } else if (action.equals(ActionW.WINDOW.cmd())) {
            return getAction(ActionW.WINDOW);
        } else if (action.equals(ActionW.LEVEL.cmd())) {
            return getAction(ActionW.LEVEL);
        } else if (action.equals(ActionW.WINLEVEL.cmd())) {
            return getAction(ActionW.LEVEL);
        } else if (action.equals(ActionW.SCROLL_SERIES.cmd())) {
            return getAction(ActionW.SCROLL_SERIES);
        } else if (action.equals(ActionW.ZOOM.cmd())) {
            return getAction(ActionW.ZOOM);
        } else if (action.equals(ActionW.ROTATION.cmd())) {
            return getAction(ActionW.ROTATION);
        }
        return null;
    }

    private void resetMouseAdapter() {
        for (ActionState adapter : eventManager.getAllActionValues()) {
            if (adapter instanceof MouseActionAdapter) {
                ((MouseActionAdapter) adapter).setButtonMaskEx(0);
            }
        }
        // reset context menu that is a field of this instance
        contextMenuHandler.setButtonMaskEx(0);
        mouseClickHandler.setButtonMaskEx(0);
    }

    protected MouseActionAdapter getAction(ActionW action) {
        ActionState a = eventManager.getAction(action);
        if (a instanceof MouseActionAdapter) {
            return (MouseActionAdapter) a;
        }
        return null;
    }

    @Override
    public String getPixelInfo(Point p, RenderedImageLayer<DicomImageElement> imgLayer) {
        DicomImageElement dicom = imgLayer.getSourceImage();
        StringBuffer message = new StringBuffer(" "); //$NON-NLS-1$
        if (dicom != null && imgLayer.getReadIterator() != null) {
            RenderedImage image = imgLayer.getSourceRenderedImage();
            Point realPoint =
                new Point((int) Math.ceil(p.x / dicom.getRescaleX() - 0.5), (int) Math.ceil(p.y / dicom.getRescaleY()
                    - 0.5));
            if (image != null && realPoint.x >= 0 && realPoint.y >= 0 && realPoint.x < image.getWidth()
                && realPoint.y < image.getHeight()) {
                try {
                    int[] c = { 0, 0, 0 };
                    imgLayer.getReadIterator().getPixel(realPoint.x, realPoint.y, c); // read the pixel

                    if (image.getSampleModel().getNumBands() == 1) {
                        boolean pixelPadding = JMVUtils.getNULLtoTrue(getActionValue(ActionW.IMAGE_PIX_PADDING.cmd()));
                        float val = dicom.pixel2mLUT(c[0], pixelPadding);
                        message.append((int) val);
                        if (dicom.getPixelValueUnit() != null) {
                            message.append(" " + dicom.getPixelValueUnit()); //$NON-NLS-1$
                        }
                    } else {
                        // TODO add preference for Pixel value type (RGB, IHS...) and pixel position (pix, real)
                        message.append("R=" + c[0] + " G=" + c[1] + " B=" + c[2]); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        // float[] ihs = IHSColorSpace.getInstance().fromRGB(new float[]
                        // {
                        // c[0] / 255f, c[1] / 255f, c[2] / 255f
                        // });
                        // c[0] = (int) (ihs[0] * 255f);
                        // c[1] = (int) ((ihs[1] / (Math.PI * 2)) * 255f);
                        // c[2] = (int) (ihs[2] * 255f);
                        //
                        // message.append(" (I = " + c[0] + ", H = " + c[1] + ", S = " + c[2] + ")");
                    }
                    message.append(" - (" + p.x + "," + p.y + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    // double[] pixelSpacing = (double[]) imageElement.getTagValue(TagW.PixelSpacing);
                    // Unit unit = pixelSpacing == null ? Unit.PIXEL : Unit.MILLIMETER;
                    // if (pixelSpacing != null) {
                    // message.append(" (" + DecFormater.twoDecimal(imageElement.getPixelSizeX() * p.x));
                    // message.append(" " + unit.getAbbreviation() + ",");
                    // message.append(DecFormater.twoDecimal(imageElement.getPixelSizeY() * p.y));
                    // message.append(" " + unit.getAbbreviation() + ")");
                    // }
                } catch (ArrayIndexOutOfBoundsException ex) {
                }
            } else {
                message.append(Messages.getString("View2d.out_img")); //$NON-NLS-1$
            }
        }
        return message.toString();
    }

    @Override
    public void handleLayerChanged(ImageLayer layer) {
        if (infoLayer.getDisplayPreferences(AnnotationsLayer.KEY_OBJECT)) {
            updateKOselectedState();
        }
        repaint();
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (!e.isTemporary()) {
            ImageViewerPlugin<DicomImageElement> pane = eventManager.getSelectedView2dContainer();
            if (pane != null && pane.isContainingView(this)) {
                pane.setSelectedImagePaneFromFocus(this);
            }
        }
    }

    private class SequenceHandler extends TransferHandler {

        public SequenceHandler() {
            super("series"); //$NON-NLS-1$
        }

        @Override
        public Transferable createTransferable(JComponent comp) {
            if (comp instanceof SeriesThumbnail) {
                MediaSeries<?> t = ((SeriesThumbnail) comp).getSeries();
                if (t instanceof Series) {
                    return t;
                }
            }
            return null;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) {
                return false;
            }
            if (support.isDataFlavorSupported(Series.sequenceDataFlavor)
                || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || support.isDataFlavorSupported(UriListFlavor.uriListFlavor)) {
                return true;
            }
            return false;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            Transferable transferable = support.getTransferable();

            List<File> files = null;
            // Not supported on Linux
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return dropDicomFiles(files);
            }
            // When dragging a file or group of files from a Gnome or Kde environment
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4899516
            else if (support.isDataFlavorSupported(UriListFlavor.uriListFlavor)) {
                try {
                    // Files with spaces in the filename trigger an error
                    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6936006
                    String val = (String) transferable.getTransferData(UriListFlavor.uriListFlavor);
                    files = UriListFlavor.textURIListToFileList(val);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return dropDicomFiles(files);
            }

            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            DataExplorerModel model = null;
            SeriesSelectionModel selList = null;
            if (dicomView != null) {
                selList = ((DicomExplorer) dicomView).getSelectionList();
            }
            ImageViewerPlugin<DicomImageElement> selPlugin = eventManager.getSelectedView2dContainer();

            Series seq;
            try {
                seq = (Series) transferable.getTransferData(Series.sequenceDataFlavor);
                // Do not add series without medias. BUG WEA-100
                if (seq == null || seq.size(null) == 0) {
                    return false;
                }
                model = (DataExplorerModel) seq.getTagValue(TagW.ExplorerModel);
                if (seq instanceof DicomSeries && model instanceof TreeModel) {
                    TreeModel treeModel = (TreeModel) model;
                    if (selList != null) {
                        selList.setOpenningSeries(true);
                    }
                    MediaSeriesGroup p1 = treeModel.getParent(seq, model.getTreeModelNodeForNewPlugin());
                    MediaSeriesGroup p2 = null;
                    ViewerPlugin openPlugin = null;
                    if (p1 == null) {
                        return false;
                    }
                    if (selPlugin instanceof View2dContainer
                        && ((View2dContainer) selPlugin).isContainingView(View2d.this)
                        && p1.equals(selPlugin.getGroupID())) {
                        p2 = p1;
                    } else {
                        synchronized (UIManager.VIEWER_PLUGINS) {
                            plugin: for (final ViewerPlugin<?> p : UIManager.VIEWER_PLUGINS) {
                                if (p1.equals(p.getGroupID())) {
                                    if (!((View2dContainer) p).isContainingView(View2d.this)) {
                                        openPlugin = p;
                                    }
                                    break plugin;
                                }
                            }
                        }
                    }

                    if (!p1.equals(p2)) {
                        SeriesViewerFactory plugin = UIManager.getViewerFactory(selPlugin);
                        if (plugin != null && !(plugin instanceof MimeSystemAppFactory)) {
                            ViewerPluginBuilder.openSequenceInPlugin(plugin, seq, model, true, true);
                        }
                        return false;
                    } else if (openPlugin != null) {
                        openPlugin.setSelectedAndGetFocus();
                        openPlugin.addSeries(seq);
                        // openPlugin.setSelected(true);
                        return false;
                    }
                } else if (seq instanceof DicomEncapDocSeries || seq instanceof DicomVideoSeries) {
                    ViewerPluginBuilder.openSequenceInDefaultPlugin(seq, model, true, true);
                    return true;
                } else {
                    // Not a DICOM Series
                    return false;
                }
            } catch (Exception e) {
                return false;
            } finally {
                if (selList != null) {
                    selList.setOpenningSeries(false);
                }
            }
            if (selList != null) {
                selList.setOpenningSeries(true);
            }

            if (selPlugin != null && SynchData.Mode.Tile.equals(selPlugin.getSynchView().getSynchData().getMode())) {
                selPlugin.addSeries(seq);
                if (selList != null) {
                    selList.setOpenningSeries(false);
                }
                return true;
            }

            setSeries(seq);
            // Getting the focus has a delay and so it will trigger the view selection later
            // requestFocusInWindow();
            if (selPlugin != null && selPlugin.isContainingView(View2d.this)) {
                selPlugin.setSelectedImagePaneFromFocus(View2d.this);
            }
            if (selList != null) {
                selList.setOpenningSeries(false);
            }
            return true;
        }

        private boolean dropDicomFiles(List<File> files) {
            if (files != null) {
                DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
                if (dicomView == null) {
                    return false;
                }
                DicomModel model = (DicomModel) dicomView.getDataExplorerModel();
                LoadLocalDicom dicom = new LoadLocalDicom(files.toArray(new File[files.size()]), true, model);
                DicomModel.loadingExecutor.execute(dicom);
                return true;
            }
            return false;
        }
    }

    protected JPopupMenu buildGraphicContextMenu(final MouseEvent evt, final ArrayList<Graphic> selected) {
        if (selected != null) {
            final JPopupMenu popupMenu = new JPopupMenu();
            TitleMenuItem itemTitle = new TitleMenuItem(Messages.getString("View2d.selection"), popupMenu.getInsets()); //$NON-NLS-1$
            popupMenu.add(itemTitle);
            popupMenu.addSeparator();
            boolean graphicComplete = true;
            if (selected.size() == 1) {
                final Graphic graph = selected.get(0);
                if (graph instanceof AbstractDragGraphic) {
                    final AbstractDragGraphic absgraph = (AbstractDragGraphic) graph;
                    if (!absgraph.isGraphicComplete()) {
                        graphicComplete = false;
                    }
                    if (absgraph.isVariablePointsNumber()) {
                        if (graphicComplete) {
                            /*
                             * Convert mouse event point to real image coordinate point (without geometric
                             * transformation)
                             */
                            final MouseEventDouble mouseEvt =
                                new MouseEventDouble(View2d.this, MouseEvent.MOUSE_RELEASED, evt.getWhen(), 16, 0, 0,
                                    0, 0, 1, true, 1);
                            mouseEvt.setSource(View2d.this);
                            mouseEvt.setImageCoordinates(getImageCoordinatesFromMouse(evt.getX(), evt.getY()));
                            final int ptIndex = absgraph.getHandlePointIndex(mouseEvt);
                            if (ptIndex >= 0) {
                                JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.rmv_pt")); //$NON-NLS-1$
                                menuItem.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        absgraph.removeHandlePoint(ptIndex, mouseEvt);
                                    }
                                });
                                popupMenu.add(menuItem);

                                menuItem = new JMenuItem(Messages.getString("View2d.draw_pt")); //$NON-NLS-1$
                                menuItem.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        absgraph.forceToAddPoints(ptIndex);
                                        MouseEventDouble evt2 =
                                            new MouseEventDouble(View2d.this, MouseEvent.MOUSE_PRESSED, evt.getWhen(),
                                                16, evt.getX(), evt.getY(), evt.getXOnScreen(), evt.getYOnScreen(), 1,
                                                true, 1);
                                        mouseClickHandler.mousePressed(evt2);
                                    }
                                });
                                popupMenu.add(menuItem);
                                popupMenu.add(new JSeparator());
                            }
                        } else if (ds != null && absgraph.getHandlePointTotalNumber() == BasicGraphic.UNDEFINED) {
                            final JMenuItem item2 = new JMenuItem(Messages.getString("View2d.stop_draw")); //$NON-NLS-1$
                            item2.addActionListener(new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    MouseEventDouble event =
                                        new MouseEventDouble(View2d.this, 0, 0, 16, 0, 0, 0, 0, 2, true, 1);
                                    ds.completeDrag(event);
                                    mouseClickHandler.mouseReleased(event);
                                }
                            });
                            popupMenu.add(item2);
                            popupMenu.add(new JSeparator());
                        }
                    }
                }
            }
            if (graphicComplete) {
                JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.delete_sel")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        View2d.this.getLayerModel().deleteSelectedGraphics(true);
                    }
                });
                popupMenu.add(menuItem);

                menuItem = new JMenuItem(Messages.getString("View2d.cut")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        AbstractLayerModel.GraphicClipboard.setGraphics(selected);
                        View2d.this.getLayerModel().deleteSelectedGraphics(false);
                    }
                });
                popupMenu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2d.copy")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        AbstractLayerModel.GraphicClipboard.setGraphics(selected);
                    }
                });
                popupMenu.add(menuItem);
                popupMenu.add(new JSeparator());
            }
            // TODO separate AbstractDragGraphic and ClassGraphic for properties
            final ArrayList<AbstractDragGraphic> list = new ArrayList<AbstractDragGraphic>();
            for (Graphic graphic : selected) {
                if (graphic instanceof AbstractDragGraphic) {
                    list.add((AbstractDragGraphic) graphic);
                }
            }

            if (selected.size() == 1) {
                final Graphic graph = selected.get(0);
                JMenuItem item = new JMenuItem(Messages.getString("View2d.to_front")); //$NON-NLS-1$
                item.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        graph.toFront();
                    }
                });
                popupMenu.add(item);
                item = new JMenuItem(Messages.getString("View2d.to_back")); //$NON-NLS-1$
                item.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        graph.toBack();
                    }
                });
                popupMenu.add(item);
                popupMenu.add(new JSeparator());

                if (graphicComplete && graph instanceof LineGraphic) {

                    final JMenuItem calibMenu = new JMenuItem(Messages.getString("View2d.chg_calib")); //$NON-NLS-1$
                    calibMenu.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            String title = Messages.getString("View2d.clibration"); //$NON-NLS-1$
                            CalibrationView calibrationDialog = new CalibrationView((LineGraphic) graph, View2d.this);
                            int res =
                                JOptionPane.showConfirmDialog(calibMenu, calibrationDialog, title,
                                    JOptionPane.OK_CANCEL_OPTION);
                            if (res == JOptionPane.OK_OPTION) {
                                calibrationDialog.applyNewCalibration();
                            }
                        }
                    });
                    popupMenu.add(calibMenu);
                    popupMenu.add(new JSeparator());
                }
            }
            if (list.size() > 0) {
                JMenuItem properties = new JMenuItem(Messages.getString("View2d.draw_prop")); //$NON-NLS-1$
                properties.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JDialog dialog = new MeasureDialog(View2d.this, list);
                        WinUtil.adjustLocationToFitScreen(dialog, evt.getLocationOnScreen());
                        dialog.setVisible(true);
                    }
                });
                popupMenu.add(properties);
            }
            return popupMenu;
        }
        return null;
    }

    protected JPopupMenu buildContexMenu(final MouseEvent evt) {
        JPopupMenu popupMenu = new JPopupMenu();
        TitleMenuItem itemTitle = new TitleMenuItem(Messages.getString("View2d.left_mouse"), popupMenu.getInsets()); //$NON-NLS-1$
        popupMenu.add(itemTitle);
        final EventManager event = EventManager.getInstance();
        popupMenu.setLabel(MouseActions.LEFT);
        String action = event.getMouseActions().getLeft();
        ButtonGroup groupButtons = new ButtonGroup();
        ImageViewerPlugin<DicomImageElement> view = eventManager.getSelectedView2dContainer();
        if (view != null) {
            final ViewerToolBar toolBar = view.getViewerToolBar();
            if (toolBar != null) {
                ActionListener leftButtonAction = new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (e.getSource() instanceof JRadioButtonMenuItem) {
                            JRadioButtonMenuItem item = (JRadioButtonMenuItem) e.getSource();
                            toolBar.changeButtonState(MouseActions.LEFT, item.getActionCommand());
                        }
                    }
                };

                List<ActionW> actionsButtons = ViewerToolBar.actionsButtons;
                synchronized (actionsButtons) {
                    for (int i = 0; i < actionsButtons.size(); i++) {
                        ActionW b = actionsButtons.get(i);
                        JRadioButtonMenuItem radio =
                            new JRadioButtonMenuItem(b.getTitle(), b.getIcon(), b.cmd().equals(action));
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
        if (AbstractLayerModel.GraphicClipboard.getGraphics() != null) {
            popupMenu.add(new JSeparator());
            JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.paste_draw")); //$NON-NLS-1$
            menuItem.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    List<Graphic> graphs = AbstractLayerModel.GraphicClipboard.getGraphics();
                    if (graphs != null) {
                        Rectangle2D area = View2d.this.getViewModel().getModelArea();
                        for (Graphic g : graphs) {
                            if (!g.getBounds(null).intersects(area)) {
                                int option =
                                    JOptionPane.showConfirmDialog(View2d.this,
                                        "At least one graphic is outside the image.\n Do you want to continue?"); //$NON-NLS-1$
                                if (option == JOptionPane.YES_OPTION) {
                                    break;
                                } else {
                                    return;
                                }
                            }
                        }
                        for (Graphic g : graphs) {
                            AbstractLayer layer = View2d.this.getLayerModel().getLayer(g.getLayerID());
                            if (layer != null) {
                                Graphic graph = g.deepCopy();
                                if (graph != null) {
                                    graph.updateLabel(true, View2d.this);
                                    layer.addGraphic(graph);
                                }
                            }
                        }
                        // Repaint all because labels are not drawn
                        View2d.this.getLayerModel().repaint();
                    }
                }
            });
            popupMenu.add(menuItem);
        }
        popupMenu.add(new JSeparator());

        // ActionState viewingAction = eventManager.getAction(ActionW.VIEWINGPROTOCOL);
        // if (viewingAction instanceof ComboItemListener) {
        // popupMenu.add(((ComboItemListener) viewingAction).createUnregisteredRadioMenu(Messages
        //                            .getString("View2dContainer.view_protocols"))); //$NON-NLS-1$
        // }

        WProperties p = BundleTools.SYSTEM_PREFERENCES;
        if (p.getBooleanProperty("weasis.contextmenu.presets", true)) {
            ActionState presetAction = eventManager.getAction(ActionW.PRESET);
            if (presetAction instanceof ComboItemListener) {
                JMenu menu =
                    ((ComboItemListener) presetAction).createUnregisteredRadioMenu(Messages
                        .getString("View2dContainer.presets"));//$NON-NLS-1$
                menu.setIcon(new ImageIcon(DefaultView2d.class.getResource("/icon/16x16/winLevel.png")));
                for (Component mitem : menu.getMenuComponents()) {
                    RadioMenuItem ritem = ((RadioMenuItem) mitem);
                    PresetWindowLevel preset = (PresetWindowLevel) ritem.getUserObject();
                    if (preset.getKeyCode() > 0) {
                        ritem.setAccelerator(KeyStroke.getKeyStroke(preset.getKeyCode(), 0));
                    }
                }
                popupMenu.add(menu);
            }
        }

        // if (p.getBooleanProperty("weasis.contextmenu.lut", true)) {
        // ActionState lutShapeAction = eventManager.getAction(ActionW.LUT_SHAPE);
        // if (lutShapeAction instanceof ComboItemListener) {
        //                popupMenu.add(((ComboItemListener) lutShapeAction).createMenu(ActionW.LUT_SHAPE.getTitle())); //$NON-NLS-1$
        // }
        // }

        if (p.getBooleanProperty("weasis.contextmenu.sortstack", true)) {
            ActionState stackAction = eventManager.getAction(ActionW.SORTSTACK);
            if (stackAction instanceof ComboItemListener) {
                JMenu menu =
                    ((ComboItemListener) stackAction).createUnregisteredRadioMenu(Messages
                        .getString("View2dContainer.sort_stack")); //$NON-NLS-1$
                ActionState invstackAction = eventManager.getAction(ActionW.INVERSESTACK);
                if (invstackAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) invstackAction).createUnregiteredJCheckBoxMenuItem(Messages
                        .getString("View2dContainer.inv_stack"))); //$NON-NLS-1$
                }
                popupMenu.add(menu);
            }
        }

        if (p.getBooleanProperty("weasis.contextmenu.orientation", true)) {
            ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
            if (rotateAction instanceof SliderChangeListener) {
                popupMenu.add(new JSeparator());
                JMenu menu = new JMenu(Messages.getString("View2dContainer.orientation")); //$NON-NLS-1$
                JMenuItem menuItem = new JMenuItem(Messages.getString("ResetTools.reset")); //$NON-NLS-1$
                final SliderChangeListener rotation = (SliderChangeListener) rotateAction;
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue(0);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.-90")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() - 90 + 360) % 360);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.+90")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() + 90) % 360);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.+180")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() + 180) % 360);
                    }
                });
                menu.add(menuItem);
                ActionState flipAction = eventManager.getAction(ActionW.FLIP);
                if (flipAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) flipAction).createUnregiteredJCheckBoxMenuItem(Messages
                        .getString("View2dContainer.flip_h"))); //$NON-NLS-1$
                }
                popupMenu.add(menu);
            }
        }

        popupMenu.add(new JSeparator());

        if (p.getBooleanProperty("weasis.contextmenu.reset", true)) {
            JMenu menu = ResetTools.createUnregisteredJMenu();
            menu.setIcon(new ImageIcon(DefaultView2d.class.getResource("/icon/16x16/reset.png")));
            popupMenu.add(menu);
        }

        if (p.getBooleanProperty("weasis.contextmenu.close", true)) {
            JMenuItem close = new JMenuItem(Messages.getString("View2d.close")); //$NON-NLS-1$
            close.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    event.getSelectedView2dContainer();
                    View2d.this.setSeries(null, null);
                }
            });
            popupMenu.add(close);
        }
        return popupMenu;
    }

    class ContextMenuHandler extends MouseActionAdapter {

        @Override
        public void mousePressed(final MouseEvent evt) {
            showPopup(evt);
        }

        @Override
        public void mouseReleased(final MouseEvent evt) {
            showPopup(evt);
        }

        private void showPopup(final MouseEvent evt) {
            // Context menu
            if ((evt.getModifiersEx() & getButtonMaskEx()) != 0) {
                JPopupMenu popupMenu = null;
                final ArrayList<Graphic> selected =
                    new ArrayList<Graphic>(View2d.this.getLayerModel().getSelectedGraphics());
                if (selected.size() > 0) {
                    popupMenu = View2d.this.buildGraphicContextMenu(evt, selected);
                } else if (View2d.this.getSourceImage() != null) {
                    popupMenu = View2d.this.buildContexMenu(evt);
                }
                if (popupMenu != null) {
                    popupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
                }
            }
        }
    }
}
