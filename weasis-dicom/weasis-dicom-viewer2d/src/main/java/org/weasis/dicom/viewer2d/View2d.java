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

import javax.media.jai.PlanarImage;
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
import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.CropOp;
import org.weasis.core.api.image.FilterOp;
import org.weasis.core.api.image.FlipOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.RotationOp;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.util.ImageLayer;
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
import org.weasis.core.ui.editor.image.PixelInfo;
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
import org.weasis.core.ui.graphic.LineWithGapGraphic;
import org.weasis.core.ui.graphic.MeasureDialog;
import org.weasis.core.ui.graphic.PolygonGraphic;
import org.weasis.core.ui.graphic.RectangleGraphic;
import org.weasis.core.ui.graphic.TempLayer;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.DefaultViewModel;
import org.weasis.core.ui.util.ColorLayerUI;
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
import org.weasis.dicom.codec.display.ModalityView;
import org.weasis.dicom.codec.display.OverlayOp;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.display.ShutterOp;
import org.weasis.dicom.codec.display.TagView;
import org.weasis.dicom.codec.display.WindowAndPresetsOp;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.IntersectSlice;
import org.weasis.dicom.codec.geometry.LocalizerPoster;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.MimeSystemAppFactory;
import org.weasis.dicom.explorer.SeriesSelectionModel;
import org.weasis.dicom.viewer2d.KOComponentFactory.KOViewButton;
import org.weasis.dicom.viewer2d.KOComponentFactory.KOViewButton.eState;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public class View2d extends DefaultView2d<DicomImageElement> {
    private static final long serialVersionUID = 8334123827855840782L;

    private static final Logger LOGGER = LoggerFactory.getLogger(View2d.class);

    public static final ImageIcon KO_ICON = new ImageIcon(View2d.class.getResource("/icon/22x22/dcm-KO.png"));
    public static final ImageIcon PR_ICON = new ImageIcon(View2d.class.getResource("/icon/22x22/dcm-PR.png"));

    private final Dimension oldSize;
    private final ContextMenuHandler contextMenuHandler = new ContextMenuHandler();

    protected final KOViewButton koStarButton;

    public View2d(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);

        SimpleOpManager manager = imageLayer.getDisplayOpManager();
        manager.addImageOperationAction(new WindowAndPresetsOp());
        manager.addImageOperationAction(new OverlayOp());
        manager.addImageOperationAction(new FilterOp());
        manager.addImageOperationAction(new PseudoColorOp());
        manager.addImageOperationAction(new ShutterOp());
        // Zoom and Rotation must be the last operations for the lens
        manager.addImageOperationAction(new ZoomOp());
        manager.addImageOperationAction(new RotationOp());
        manager.addImageOperationAction(new FlipOp());

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
        getViewButtons().add(KOComponentFactory.buildKoSelectionButton(this));
        koStarButton = KOComponentFactory.buildKoStarButton(this);
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
                /*
                 * Negative value means a default value according to the zoom type (pixel size, best fit...). Set again
                 * to default value to compute again the position. For instance, the image cannot be center aligned
                 * until the view has been repaint once (because the size is null).
                 */
                if (currentZoom <= 0.0) {
                    zoom(0.0);
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
        actionsInView.put(ActionW.SORTSTACK.cmd(), SortSeriesStack.instanceNumber);
        actionsInView.put(ActionW.PR_STATE.cmd(), null);

        // Preprocessing
        actionsInView.put(ActionW.CROP.cmd(), null);

        OpManager disOp = getDisplayOpManager();
        disOp.setParamValue(ShutterOp.OP_NAME, ShutterOp.P_SHOW, true);
        disOp.setParamValue(OverlayOp.OP_NAME, OverlayOp.P_SHOW, true);
        disOp.setParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), true);
        disOp.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), true);
        disOp.setParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd(), null);

        setDefaultKOActionWState();
    }

    protected void setDefaultKOActionWState() {

        actionsInView.put(ActionW.KO_FILTER.cmd(), false);
        actionsInView.put(ActionW.KO_TOOGLE_STATE.cmd(), false);
        actionsInView.put(ActionW.KO_SELECTION.cmd(), ActionState.NONE);

        // Set the more recent KO by default

        // Collection<KOSpecialElement> koElements = DicomModel.getKoSpecialElements(getSeries());
        // Object defaultKO = (koElements != null && koElements.size() > 0) ? koElements.iterator().next() : null;
        // actionsInView.put(ActionW.KO_SELECTION.cmd(), defaultKO);
        //
        // if (defaultKO instanceof KOSpecialElement) {
        // DicomImageElement dicomImage = getImage();
        //
        // if (dicomImage != null) {
        // String sopInstanceUID = (String) dicomImage.getTagValue(TagW.SOPInstanceUID);
        // String seriesInstanceUID = (String) dicomImage.getTagValue(TagW.SeriesInstanceUID);
        //
        // if (sopInstanceUID != null && seriesInstanceUID != null) {
        // KOSpecialElement koElement = (KOSpecialElement) defaultKO;
        // Set<String> sopInstanceUIDSet = koElement.getReferencedSOPInstanceUIDSet(seriesInstanceUID);
        //
        // if (sopInstanceUIDSet != null && sopInstanceUIDSet.contains(sopInstanceUID)) {
        // actionsInView.put(ActionW.KO_TOOGLE_STATE.cmd(), true);
        // }
        // }
        // }
        // }

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (series == null) {
            return;
        }

        RenderedImage dispImage = imageLayer.getDisplayImage();
        OpManager disOp = imageLayer.getDisplayOpManager();
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
                        ImageOpNode node = disOp.getNode(WindowOp.OP_NAME);

                        if (img == null || !img.containsPreset(preset)) {
                            // When series synchronization, do not synch preset from other series
                            node.setParam(ActionW.PRESET.cmd(), null);
                        }
                        if (node != null) {
                            boolean pixelPadding =
                                JMVUtils.getNULLtoTrue(disOp.getParamValue(WindowOp.OP_NAME,
                                    ActionW.IMAGE_PIX_PADDING.cmd()));
                            node.setParam(ActionW.WINDOW.cmd(), preset.getWindow());
                            node.setParam(ActionW.LEVEL.cmd(), preset.getLevel());
                            // node.setParam(ActionW.LEVEL_MIN.cmd(), img.getMinValue(pixelPadding));
                            // node.setParam(ActionW.LEVEL_MAX.cmd(), img.getMinValue(pixelPadding));
                            node.setParam(ActionW.LUT_SHAPE.cmd(), preset.getLutShape());
                        }
                        imageLayer.updateDisplayOperations();
                    }
                } else if (command.equals(ActionW.DEFAULT_PRESET.cmd())) {
                    disOp.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), val);
                } else if (command.equals(ActionW.LUT_SHAPE.cmd())) {
                    ImageOpNode node = disOp.getNode(WindowOp.OP_NAME);
                    if (node != null) {
                        node.setParam(ActionW.LUT_SHAPE.cmd(), val);
                    }
                    imageLayer.updateDisplayOperations();
                } else if (command.equals(ActionW.SORTSTACK.cmd())) {
                    actionsInView.put(ActionW.SORTSTACK.cmd(), val);
                    sortStack(getCurrentSortComparator());
                } else if (command.equals(ActionW.INVERSESTACK.cmd())) {
                    actionsInView.put(ActionW.INVERSESTACK.cmd(), val);
                    sortStack(getCurrentSortComparator());
                } else if (command.equals(ActionW.CROSSHAIR.cmd())) {
                    if (series != null && val instanceof Point2D.Double) {
                        Point2D.Double p = (Point2D.Double) val;
                        GeometryOfSlice sliceGeometry = this.getImage().getSliceGeometry();
                        String fruid = (String) series.getTagValue(TagW.FrameOfReferenceUID);
                        if (sliceGeometry != null && fruid != null) {
                            Point3d p3 = Double.isNaN(p.x) ? null : sliceGeometry.getPosition(p);
                            ImageViewerPlugin<DicomImageElement> container =
                                this.eventManager.getSelectedView2dContainer();
                            if (container != null) {
                                ArrayList<DefaultView2d<DicomImageElement>> viewpanels = container.getImagePanels();
                                if (p3 != null) {
                                    for (DefaultView2d<DicomImageElement> v : viewpanels) {
                                        MediaSeries<DicomImageElement> s = v.getSeries();
                                        if (s == null) {
                                            continue;
                                        }
                                        if (v instanceof View2d
                                            && fruid.equals(s.getTagValue(TagW.FrameOfReferenceUID))) {
                                            if (v != container.getSelectedImagePane()) {
                                                GeometryOfSlice geometry = v.getImage().getSliceGeometry();
                                                if (geometry != null) {
                                                    Vector3d vn = geometry.getNormal();
                                                    // vn.absolute();
                                                    double location = p3.x * vn.x + p3.y * vn.y + p3.z * vn.z;
                                                    DicomImageElement img =
                                                        s.getNearestImage(location, 0,
                                                            (Filter<DicomImageElement>) actionsInView
                                                                .get(ActionW.FILTERED_SERIES.cmd()), v
                                                                .getCurrentSortComparator());
                                                    if (img != null) {
                                                        ((View2d) v).setImage(img);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                for (DefaultView2d<DicomImageElement> v : viewpanels) {
                                    MediaSeries<DicomImageElement> s = v.getSeries();
                                    if (s == null) {
                                        continue;
                                    }
                                    if (v instanceof View2d && fruid.equals(s.getTagValue(TagW.FrameOfReferenceUID))) {
                                        ((View2d) v).computeCrosshair(p3);
                                        v.repaint();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (name.equals(ActionW.IMAGE_SHUTTER.cmd())) {
            if (disOp.setParamValue(ShutterOp.OP_NAME, ShutterOp.P_SHOW, evt.getNewValue())) {
                imageLayer.updateDisplayOperations();
            }
        } else if (name.equals(ActionW.IMAGE_OVERLAY.cmd())) {
            if (disOp.setParamValue(OverlayOp.OP_NAME, OverlayOp.P_SHOW, evt.getNewValue())) {
                imageLayer.updateDisplayOperations();
            }
        }

        if (lens != null) {
            if (dispImage != imageLayer.getDisplayImage()) {
                /*
                 * Transmit to the lens the command in case the source image has been freeze (for updating rotation and
                 * flip => will keep consistent display)
                 */
                lens.setCommandFromParentView(name, evt.getNewValue());
                lens.updateZoom();
            }
        }
    }

    @Override
    public void reset() {
        super.reset();
        setPresentationState(null);
    }

    void setPresentationState(Object val) {

        Object old = actionsInView.get(ActionW.PR_STATE.cmd());
        if (val == null && old == null
            || (old instanceof PresentationStateReader && ((PresentationStateReader) old).getDicom() == val)) {
            return;
        }
        PresentationStateReader pr =
            val instanceof PRSpecialElement ? new PresentationStateReader((PRSpecialElement) val) : null;
        actionsInView.put(ActionW.PR_STATE.cmd(), pr == null ? val : pr);
        actionsInView.put(ActionW.PREPROCESSING.cmd(), null);

        // Delete previous PR Layers
        ArrayList<AbstractLayer.Identifier> dcmLayers =
            (ArrayList<AbstractLayer.Identifier>) actionsInView.get(PresentationStateReader.TAG_DICOM_LAYERS);
        if (dcmLayers != null) {
            PRManager.deleteDicomLayers(dcmLayers, getLayerModel());
            actionsInView.remove(PresentationStateReader.TAG_DICOM_LAYERS);
        }

        DicomImageElement m = getImage();
        // Reset display parameter
        ((DefaultViewModel) getViewModel()).setEnableViewModelChangeListeners(false);
        imageLayer.setEnableDispOperations(false);
        imageLayer.fireOpEvent(new ImageOpEvent(ImageOpEvent.OpEvent.ResetDisplay, series, m, null));

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
            // setDefautWindowLevel(getImage());
            // setShutter(m);
        } else {
            applyPresentationState(pr, m);
        }

        Rectangle area = (Rectangle) actionsInView.get(ActionW.CROP.cmd());
        if (area != null && !area.equals(getViewModel().getModelArea())) {
            ((DefaultViewModel) getViewModel()).adjustMinViewScaleFromImage(area.width, area.height);
            getViewModel().setModelArea(area);
        }
        imageLayer.setPreprocessing((OpManager) actionsInView.get(ActionW.PREPROCESSING.cmd()));
        if (pr != null) {
            imageLayer.fireOpEvent(new ImageOpEvent(ImageOpEvent.OpEvent.ApplyPR, series, m, actionsInView));
            ImageOpNode rotation = imageLayer.getDisplayOpManager().getNode(RotationOp.OP_NAME);
            if (rotation != null) {
                rotation.setParam(RotationOp.P_ROTATE, actionsInView.get(ActionW.ROTATION.cmd()));
            }
            ImageOpNode flip = imageLayer.getDisplayOpManager().getNode(FlipOp.OP_NAME);
            if (flip != null) {
                flip.setParam(FlipOp.P_FLIP, actionsInView.get(ActionW.FLIP.cmd()));
            }
        }
        ZoomType type = (ZoomType) actionsInView.get(zoomTypeCmd);
        if (!ZoomType.CURRENT.equals(type)) {
            Double zoom = (Double) actionsInView.get(ActionW.ZOOM.cmd());
            zoom(zoom == null ? 0.0 : zoom);
        }

        ((DefaultViewModel) getViewModel()).setEnableViewModelChangeListeners(true);
        imageLayer.setEnableDispOperations(true);
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
            boolean pixelPadding =
                JMVUtils.getNULLtoTrue(getDisplayOpManager().getParamValue(WindowOp.OP_NAME,
                    ActionW.IMAGE_PIX_PADDING.cmd()));
            // actionsInView.put(ActionW.LEVEL_MIN.cmd(), img.getMinValue(pixelPadding));
            // actionsInView.put(ActionW.LEVEL_MAX.cmd(), img.getMinValue(pixelPadding));
            actionsInView.put(PresentationStateReader.PR_PRESETS, presets);
            actionsInView.put(ActionW.PRESET.cmd(), p);
            actionsInView.put(ActionW.LUT_SHAPE.cmd(), p.getLutShape());
            actionsInView.put(ActionW.DEFAULT_PRESET.cmd(), true);
        }

        // setShutter(reader.getDicom());
        Rectangle area = (Rectangle) reader.getTagValue(ActionW.CROP.cmd(), null);

        double[] prPixSize = (double[]) reader.getTagValue(TagW.PixelSpacing.getName(), null);
        if (prPixSize != null && prPixSize.length == 2) {
            actionsInView.put(PresentationStateReader.TAG_OLD_PIX_SIZE, img.getDisplayPixelSize());
            img.setPixelSize(prPixSize[0], prPixSize[1]);

            // if (area != null && img.getRescaleX() != img.getRescaleY()) {
            // area =
            // new Rectangle((int) Math.ceil(area.getX() * img.getRescaleX() - 0.5), (int) Math.ceil(area.getY()
            // * img.getRescaleY() - 0.5), (int) Math.ceil(area.getWidth() * img.getRescaleX() - 0.5),
            // (int) Math.ceil(area.getHeight() * img.getRescaleY() - 0.5));
            // }

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
                    SimpleOpManager manager = new SimpleOpManager();
                    CropOp crop = new CropOp();
                    crop.setParam(CropOp.P_AREA, area);
                    crop.setParam(CropOp.P_SHIFT_TO_ORIGIN, true);
                    manager.addImageOperationAction(crop);
                    actionsInView.put(ActionW.PREPROCESSING.cmd(), manager);
                }
            }
        }
        actionsInView.put(ActionW.CROP.cmd(), area);
        actionsInView.put(CropOp.P_SHIFT_TO_ORIGIN, true);
        double zoom = (Double) reader.getTagValue(ActionW.ZOOM.cmd(), 0.0d);
        actionsInView.put(ActionW.ZOOM.cmd(), zoom);
    }

    public void updateKOButtonVisibleState() {

        Collection<KOSpecialElement> koElements = DicomModel.getKoSpecialElements(getSeries());
        boolean koElementExist = (koElements != null && koElements.size() > 0);
        // TODO try a given parameter so it wouldn't have to be computed again

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

            // TODO for the following think of a better place like somewhere that listen dicomModel update
            // firePropertyChange since it concerns only current view's eventManager to be updated
            eventManager.fireSeriesViewerListeners(new SeriesViewerEvent(eventManager.getSelectedView2dContainer(),
                null, null, EVENT.SELECT_VIEW)); // call iniTreeValues in DisplayTool to update checkBox

            needToRepaint = true;
        }

        if (koElementExist) {
            needToRepaint = updateKOselectedState();
        }

        if (needToRepaint) {
            repaint();
        }
    }

    /**
     * @return true if the state has changed and if the view or at least the KO button need to be repaint
     */

    protected boolean updateKOselectedState() {

        eState previousState = koStarButton.getState();

        // evaluate koSelection status for every Image change
        KOViewButton.eState newSelectionState = eState.UNSELECTED;

        Object selectedKO = getActionValue(ActionW.KO_SELECTION.cmd());
        DicomImageElement dicomImage = getImage();

        if (dicomImage != null) {
            String sopInstanceUID = (String) dicomImage.getTagValue(TagW.SOPInstanceUID);
            String seriesInstanceUID = (String) dicomImage.getTagValue(TagW.SeriesInstanceUID);

            if (sopInstanceUID != null && seriesInstanceUID != null) {
                if ((selectedKO instanceof KOSpecialElement)) {
                    KOSpecialElement koElement = (KOSpecialElement) selectedKO;
                    if (koElement.containsSopInstanceUIDReference(seriesInstanceUID, sopInstanceUID)) {
                        newSelectionState = eState.SELECTED;
                    }
                } else {
                    Collection<KOSpecialElement> koElements = DicomModel.getKoSpecialElements(getSeries());

                    if (koElements != null) {
                        for (KOSpecialElement koElement : koElements) {
                            if (koElement.containsSopInstanceUIDReference(seriesInstanceUID, sopInstanceUID)) {
                                newSelectionState = eState.EXIST;
                                break;
                            }
                        }
                    }
                }
            }
        }

        koStarButton.setState(newSelectionState);

        Boolean selected = koStarButton.getState().equals(eState.SELECTED) ? true : false;
        actionsInView.put(ActionW.KO_TOOGLE_STATE.cmd(), selected);

        return (previousState != newSelectionState);
    }

    protected Rectangle getKOStarButtonBound(Graphics2D g2d) {

        // /////////////////////////////////////////////////////////////////////////////////////////////////
        // TODO - find a better way to get infoLayer TOP-RIGHT available position for drawing something else

        Modality mod = Modality.getModality((String) getSeries().getTagValue(TagW.Modality));
        ModalityInfoData infoData = ModalityView.getModlatityInfos(mod);
        CornerInfoData corner = infoData.getCornerInfo(CornerDisplay.TOP_RIGHT);

        final float fontHeight = FontTools.getAccurateFontHeight(g2d);
        boolean anonymize = infoLayer.getDisplayPreferences(AnnotationsLayer.ANONYM_ANNOTATIONS);

        float drawY = 0;
        TagView[] infos = corner.getInfos();
        for (TagView tag : infos) {
            if (tag != null) {
                drawY += fontHeight;
            }
        }

        // /////////////////////////////////////////////////////////////////////////////////////////////////

        Icon koStarIcon = koStarButton.getIcon();

        int xPos = getWidth() - koStarIcon.getIconWidth() - infoLayer.getBorder();
        int yPos = (int) (Math.round(drawY) + (0.3 * fontHeight));

        return new Rectangle(xPos, yPos, koStarIcon.getIconWidth(), koStarIcon.getIconHeight());
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

        updatePrButtonState(img, newImg);
    }

    void updatePR() {
        DicomImageElement img = imageLayer.getSourceImage();
        if (img != null) {
            updatePrButtonState(img, true);
        }
    }

    private void updatePrButtonState(DicomImageElement img, boolean newImg) {
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
            } else if (oldPR != null) {
                setPresentationState(null);
            }
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
        } else if (action.equals(ActionW.CROSSHAIR.cmd())) {
            return getAction(ActionW.CROSSHAIR);
        } else if (action.equals(ActionW.ROTATION.cmd())) {
            return getAction(ActionW.ROTATION);
        }
        return null;
    }

    public void computeCrosshair(Point3d p3) {
        DicomImageElement image = this.getImage();
        AbstractLayer layer = getLayerModel().getLayer(AbstractLayer.CROSSLINES);
        if (image != null && layer != null) {
            layer.deleteAllGraphic();
            GeometryOfSlice sliceGeometry = image.getSliceGeometry();
            if (sliceGeometry != null) {
                SliceOrientation sliceOrientation = this.getSliceOrientation();
                if (sliceOrientation != null && p3 != null) {
                    Point2D p = sliceGeometry.getImagePosition(p3);
                    Tuple3d dimensions = sliceGeometry.getDimensions();
                    boolean axial = SliceOrientation.AXIAL.equals((sliceOrientation));
                    Point2D centerPt = new Point2D.Double(p.getX(), p.getY());

                    List<Point2D.Double> pts = new ArrayList<Point2D.Double>();
                    pts.add(new Point2D.Double(p.getX(), 0.0));
                    pts.add(new Point2D.Double(p.getX(), dimensions.x));

                    boolean sagittal = SliceOrientation.SAGITTAL.equals(sliceOrientation);
                    Color color1 = sagittal ? Color.GREEN : Color.BLUE;
                    addCrosshairLine(layer, pts, color1, centerPt);

                    List<Point2D.Double> pts2 = new ArrayList<Point2D.Double>();
                    Color color2 = axial ? Color.GREEN : Color.RED;
                    pts2.add(new Point2D.Double(0.0, p.getY()));
                    pts2.add(new Point2D.Double(dimensions.y, p.getY()));
                    addCrosshairLine(layer, pts2, color2, centerPt);

                    RenderedImage dispImg = image.getImage();
                    if (dispImg != null) {
                        Rectangle2D rect =
                            new Rectangle2D.Double(dispImg.getMinX() * image.getRescaleX(), dispImg.getMinY()
                                * image.getRescaleY(), dispImg.getWidth() * image.getRescaleX(), dispImg.getHeight()
                                * image.getRescaleY());
                        addRectangle(layer, rect, axial ? Color.RED : sagittal ? Color.BLUE : Color.GREEN);
                    }
                }
            }
        }
    }

    protected void addCrosshairLine(AbstractLayer layer, List<Point2D.Double> pts, Color color, Point2D center) {
        if (pts != null && pts.size() > 0 && layer != null) {
            try {
                Graphic graphic =
                    pts.size() == 2 ? new LineWithGapGraphic(pts.get(0), pts.get(1), 1.0f, color, false, center, 75)
                        : new PolygonGraphic(pts, color, 1.0f, false, false);
                layer.addGraphic(graphic);
            } catch (InvalidShapeException e) {
                LOGGER.error(e.getMessage());
            }

        }
    }

    protected void addRectangle(AbstractLayer layer, Rectangle2D rect, Color color) {
        if (rect != null && layer != null) {
            try {
                layer.addGraphic(new RectangleGraphic(rect, 1.0f, color, false, false));
            } catch (InvalidShapeException e) {
                LOGGER.error(e.getMessage());
            }
        }
    }

    public SliceOrientation getSliceOrientation() {
        SliceOrientation sliceOrientation = null;
        MediaSeries<DicomImageElement> s = getSeries();
        if (s != null) {
            Object img = s.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
            if (img instanceof DicomImageElement) {
                double[] v = (double[]) ((DicomImageElement) img).getTagValue(TagW.ImageOrientationPatient);
                if (v != null && v.length == 6) {
                    String orientation =
                        ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(v[0], v[1], v[2], v[3],
                            v[4], v[5]);
                    if (ImageOrientation.LABELS[1].equals(orientation)) {
                        sliceOrientation = SliceOrientation.AXIAL;
                    } else if (ImageOrientation.LABELS[3].equals(orientation)) {
                        sliceOrientation = SliceOrientation.CORONAL;
                    } else if (ImageOrientation.LABELS[2].equals(orientation)) {
                        sliceOrientation = SliceOrientation.SAGITTAL;
                    }
                }
            }
        }
        return sliceOrientation;
    }

    protected void resetMouseAdapter() {
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
    protected PlanarImage getPreprocessedImage(DicomImageElement imageElement) {
        return imageElement.getImage();
    }

    @Override
    protected void fillPixelInfo(final PixelInfo pixelInfo, final DicomImageElement imageElement, final double[] c) {
        if (c != null && c.length >= 1) {
            if (c.length == 1) {
                boolean pixelPadding =
                    JMVUtils.getNULLtoTrue(getDisplayOpManager().getParamValue(WindowOp.OP_NAME,
                        ActionW.IMAGE_PIX_PADDING.cmd()));
                pixelInfo.setValues(new double[] { imageElement.pixel2mLUT((float) c[0], pixelPadding) });
            } else {
                super.fillPixelInfo(pixelInfo, imageElement, c);
            }
        }
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
                            ColorLayerUI layer =
                                ColorLayerUI.createTransparentLayerUI(WinUtil.getParentJFrame(View2d.this));
                            int res =
                                JOptionPane.showConfirmDialog(calibMenu, calibrationDialog, title,
                                    JOptionPane.OK_CANCEL_OPTION);
                            if (layer != null) {
                                layer.hideUI();
                            }
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
                        ColorLayerUI layer =
                            ColorLayerUI.createTransparentLayerUI(WinUtil.getParentJFrame(View2d.this));
                        JDialog dialog = new MeasureDialog(View2d.this, list);
                        WinUtil.adjustLocationToFitScreen(dialog, evt.getLocationOnScreen());
                        dialog.setVisible(true);
                        if (layer != null) {
                            layer.hideUI();
                        }
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
