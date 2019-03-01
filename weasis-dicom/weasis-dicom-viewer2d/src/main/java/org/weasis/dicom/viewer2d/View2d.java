/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.viewer2d;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
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

import org.dcm4che3.data.Tag;
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
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.FilterOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.dialog.MeasureDialog;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
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
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineWithGapGraphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
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
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.OverlayOp;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.display.ShutterOp;
import org.weasis.dicom.codec.display.WindowAndPresetsOp;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.ImageOrientation.Label;
import org.weasis.dicom.codec.geometry.IntersectSlice;
import org.weasis.dicom.codec.geometry.IntersectVolume;
import org.weasis.dicom.codec.geometry.LocalizerPoster;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.MimeSystemAppFactory;
import org.weasis.dicom.explorer.SeriesSelectionModel;
import org.weasis.dicom.explorer.pr.PrGraphicUtil;
import org.weasis.dicom.viewer2d.KOComponentFactory.KOViewButton;
import org.weasis.dicom.viewer2d.KOComponentFactory.KOViewButton.eState;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;
import org.weasis.opencv.data.PlanarImage;

public class View2d extends DefaultView2d<DicomImageElement> {
    private static final long serialVersionUID = 8334123827855840782L;

    private static final Logger LOGGER = LoggerFactory.getLogger(View2d.class);

    public static final ImageIcon KO_ICON = new ImageIcon(View2d.class.getResource("/icon/22x22/dcm-KO.png")); //$NON-NLS-1$
    public static final ImageIcon PR_ICON = new ImageIcon(View2d.class.getResource("/icon/22x22/dcm-PR.png")); //$NON-NLS-1$

    private final Dimension oldSize;
    private final ContextMenuHandler contextMenuHandler = new ContextMenuHandler();

    protected final KOViewButton koStarButton;

    public View2d(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);

        SimpleOpManager manager = imageLayer.getDisplayOpManager();
        manager.addImageOperationAction(new WindowAndPresetsOp());
        manager.addImageOperationAction(new FilterOp());
        manager.addImageOperationAction(new PseudoColorOp());
        manager.addImageOperationAction(new ShutterOp());
        manager.addImageOperationAction(new OverlayOp());
        // Zoom and Rotation must be the last operations for the lens
        manager.addImageOperationAction(new AffineTransformOp());

        infoLayer = new InfoLayer(this);
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
                View2d.this.componentResized();
            }
        });
    }

    private void componentResized() {
        Double currentZoom = (Double) actionsInView.get(ActionW.ZOOM.cmd());
        /*
         * Negative value means a default value according to the zoom type (pixel size, best fit...). Set again to
         * default value to compute again the position. For instance, the image cannot be center aligned until the view
         * has been repaint once (because the size is null).
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

    @Override
    protected void initActionWState() {
        super.initActionWState();
        actionsInView.put(ActionW.SORTSTACK.cmd(), SortSeriesStack.instanceNumber);

        // Preprocessing
        actionsInView.put(ActionW.CROP.cmd(), null);

        OpManager disOp = getDisplayOpManager();
        disOp.setParamValue(ShutterOp.OP_NAME, ShutterOp.P_SHOW, true);
        disOp.setParamValue(OverlayOp.OP_NAME, OverlayOp.P_SHOW, true);
        disOp.setParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), true);
        disOp.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), true);
        disOp.setParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd(), null);

        initKOActionWState();
    }

    protected void initKOActionWState() {
        actionsInView.put(ActionW.KO_FILTER.cmd(), false);
        actionsInView.put(ActionW.KO_TOOGLE_STATE.cmd(), false);
        actionsInView.put(ActionW.KO_SELECTION.cmd(), ActionState.NoneLabel.NONE);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (series == null) {
            return;
        }

        PlanarImage dispImage = imageLayer.getDisplayImage();
        OpManager disOp = imageLayer.getDisplayOpManager();
        final String name = evt.getPropertyName();
        if (name.equals(ActionW.SYNCH.cmd())) {
            SynchEvent synch = (SynchEvent) evt.getNewValue();
            SynchData synchData = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
            boolean tile = synchData != null && SynchData.Mode.TILE.equals(synchData.getMode());
            if (synchData != null && Mode.NONE.equals(synchData.getMode())) {
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

                        if (node != null) {
                            node.setParam(ActionW.WINDOW.cmd(), preset.getWindow());
                            node.setParam(ActionW.LEVEL.cmd(), preset.getLevel());
                            node.setParam(ActionW.LUT_SHAPE.cmd(), preset.getLutShape());
                            // When series synchronization, do not synch preset from other series
                            if (img == null || !img.containsPreset(preset)) {
                                List<PresetWindowLevel> presets =
                                    (List<PresetWindowLevel>) actionsInView.get(PRManager.PR_PRESETS);
                                if (presets == null || !presets.contains(preset)) {
                                    preset = null;
                                }
                            }
                            node.setParam(ActionW.PRESET.cmd(), preset);
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
                } else if (command.equals(ActionW.KO_SELECTION.cmd())) {
                    int frameIndex = tile
                        ? LangUtil.getNULLtoFalse((Boolean) synch.getView().getActionValue(ActionW.KO_FILTER.cmd())) ? 0
                            : synch.getView().getFrameIndex() - synch.getView().getTileOffset()
                        : -1;
                    KOManager.updateKOFilter(this, val,
                        (Boolean) (tile ? synch.getView().getActionValue(ActionW.KO_FILTER.cmd()) : null), frameIndex);
                } else if (command.equals(ActionW.KO_FILTER.cmd())) {
                    int frameIndex = tile ? LangUtil.getNULLtoFalse((Boolean) val) ? 0
                        : synch.getView().getFrameIndex() - synch.getView().getTileOffset() : -1;
                    KOManager.updateKOFilter(this,
                        tile ? synch.getView().getActionValue(ActionW.KO_SELECTION.cmd()) : null, (Boolean) val,
                        frameIndex);
                } else if (command.equals(ActionW.CROSSHAIR.cmd())) {
                    if (series != null && val instanceof Point2D.Double) {
                        Point2D.Double p = (Point2D.Double) val;
                        GeometryOfSlice sliceGeometry = this.getImage().getDispSliceGeometry();
                        String fruid = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
                        if (sliceGeometry != null && fruid != null) {
                            Point3d p3 = Double.isNaN(p.x) ? null : sliceGeometry.getPosition(p);
                            ImageViewerPlugin<DicomImageElement> container =
                                this.eventManager.getSelectedView2dContainer();
                            if (container != null) {
                                List<ViewCanvas<DicomImageElement>> viewpanels = container.getImagePanels();
                                if (p3 != null) {
                                    for (ViewCanvas<DicomImageElement> v : viewpanels) {
                                        MediaSeries<DicomImageElement> s = v.getSeries();
                                        if (s == null) {
                                            continue;
                                        }
                                        if (v instanceof View2d
                                            && fruid.equals(TagD.getTagValue(s, Tag.FrameOfReferenceUID))) {
                                            if (v != container.getSelectedImagePane()) {
                                                DicomImageElement imgToUpdate = v.getImage();
                                                if (imgToUpdate != null) {
                                                    GeometryOfSlice geometry = imgToUpdate.getDispSliceGeometry();
                                                    if (geometry != null) {
                                                        Vector3d vn = geometry.getNormal();
                                                        // vn.absolute();
                                                        double location = p3.x * vn.x + p3.y * vn.y + p3.z * vn.z;
                                                        DicomImageElement img = s.getNearestImage(location, 0,
                                                            (Filter<DicomImageElement>) actionsInView
                                                                .get(ActionW.FILTERED_SERIES.cmd()),
                                                            v.getCurrentSortComparator());
                                                        if (img != null) {
                                                            ((View2d) v).setImage(img);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                for (ViewCanvas<DicomImageElement> v : viewpanels) {
                                    MediaSeries<DicomImageElement> s = v.getSeries();
                                    if (s == null) {
                                        continue;
                                    }
                                    if (v instanceof View2d
                                        && fruid.equals(TagD.getTagValue(s, Tag.FrameOfReferenceUID)) && LangUtil
                                            .getNULLtoTrue((Boolean) actionsInView.get(LayerType.CROSSLINES.name()))) {
                                        ((View2d) v).computeCrosshair(p3);
                                        v.getJComponent().repaint();
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
        DicomImageElement img = getImage();
        if (img != null) {
            Object key = img.getKey();
            List<PRSpecialElement> prList =
                DicomModel.getPrSpecialElements(series, TagD.getTagValue(img, Tag.SOPInstanceUID, String.class),
                    key instanceof Integer ? (Integer) key + 1 : null);
            if (!prList.isEmpty()) {
                setPresentationState(prList.get(0), false);
            }
        }
    }

    void setPresentationState(Object val, boolean newImage) {

        Object old = actionsInView.get(ActionW.PR_STATE.cmd());
        if (!newImage && Objects.equals(old, val)) {
            return;
        }

        actionsInView.put(ActionW.PR_STATE.cmd(), val);
        PresentationStateReader pr =
            val instanceof PRSpecialElement ? new PresentationStateReader((PRSpecialElement) val) : null;
        actionsInView.put(PresentationStateReader.TAG_PR_READER, pr);
        boolean spatialTransformation = actionsInView.get(ActionW.PREPROCESSING.cmd()) != null;
        actionsInView.put(ActionW.PREPROCESSING.cmd(), null);

        DicomImageElement m = getImage();
        // Reset display parameter
        ((DefaultViewModel) getViewModel()).setEnableViewModelChangeListeners(false);
        imageLayer.setEnableDispOperations(false);
        imageLayer.fireOpEvent(new ImageOpEvent(ImageOpEvent.OpEvent.ResetDisplay, series, m, null));

        boolean changePixConfig = LangUtil.getNULLtoFalse((Boolean) actionsInView.get(PRManager.TAG_CHANGE_PIX_CONFIG));
        if (m != null) {
            // Restore the original image pixel size
            if (changePixConfig) {
                m.initPixelConfiguration();
                ActionState spUnitAction = eventManager.getAction(ActionW.SPATIAL_UNIT);
                if (spUnitAction instanceof ComboItemListener) {
                    ((ComboItemListener) spUnitAction).setSelectedItem(m.getPixelSpacingUnit());
                }
            }
            deletePrLayers();

            // Restore presets
            actionsInView.remove(PRManager.PR_PRESETS);

            // Restore zoom
            actionsInView.remove(PRManager.TAG_PR_ZOOM);
            actionsInView.remove(PresentationStateReader.TAG_PR_ROTATION);
            actionsInView.remove(PresentationStateReader.TAG_PR_FLIP);

            // Reset crop
            updateCanvas(false);
            getImageLayer().setOffset(null);
        }
        // If no Presentation State use the current image
        if (pr == null) {
            // Keeps KO properties (series level)
            Object ko = actionsInView.get(ActionW.KO_SELECTION.cmd());
            Object filter = actionsInView.get(ActionW.FILTERED_SERIES.cmd());
            OpManager disOp = getDisplayOpManager();
            Object preset = disOp.getParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd());
            initActionWState();
            setActionsInView(ActionW.KO_SELECTION.cmd(), ko);
            setActionsInView(ActionW.FILTERED_SERIES.cmd(), filter);
            // Set the image spatial unit
            if (m != null) {
                setActionsInView(ActionW.SPATIAL_UNIT.cmd(), m.getPixelSpacingUnit());
            }
            disOp.setParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd(), preset);
            resetZoom();
            resetPan();
        } else {
            PRManager.applyPresentationState(this, pr, m);
        }

        Rectangle area = (Rectangle) actionsInView.get(ActionW.CROP.cmd());
        if (area != null && !area.equals(getViewModel().getModelArea())) {
            ((DefaultViewModel) getViewModel()).adjustMinViewScaleFromImage(area.width, area.height);
            getViewModel().setModelArea(new Rectangle(0, 0, area.width, area.height));
            getImageLayer().setOffset(new Point(area.x, area.y));
        }

        SimpleOpManager opManager = (SimpleOpManager) actionsInView.get(ActionW.PREPROCESSING.cmd());
        imageLayer.setPreprocessing(opManager);
        if (opManager != null || spatialTransformation) {
            // Reset preprocessing cache
            imageLayer.getDisplayOpManager().setFirstNode(imageLayer.getSourceRenderedImage());
        }

        if (pr != null) {
            imageLayer.fireOpEvent(new ImageOpEvent(ImageOpEvent.OpEvent.ApplyPR, series, m, actionsInView));
            actionsInView.put(ActionW.ROTATION.cmd(), actionsInView.get(PresentationStateReader.TAG_PR_ROTATION));
            actionsInView.put(ActionW.FLIP.cmd(), actionsInView.get(PresentationStateReader.TAG_PR_FLIP));
        }

        Double zoom = (Double) actionsInView.get(PRManager.TAG_PR_ZOOM);
        // Special Cases: -200.0 => best fit, -100.0 => real world size
        if (zoom != null && MathUtil.isDifferent(zoom, -200.0) && MathUtil.isDifferent(zoom, -100.0)) {
            actionsInView.put(ViewCanvas.ZOOM_TYPE_CMD, ZoomType.CURRENT);
            zoom(zoom);
        } else if (zoom != null) {
            actionsInView.put(ViewCanvas.ZOOM_TYPE_CMD,
                MathUtil.isEqual(zoom, -100.0) ? ZoomType.REAL : ZoomType.BEST_FIT);
            zoom(0.0);
        } else if (changePixConfig || spatialTransformation) {
            zoom(0.0);
        }

        ((DefaultViewModel) getViewModel()).setEnableViewModelChangeListeners(true);
        imageLayer.setEnableDispOperations(true);
        eventManager.updateComponentsListener(this);
    }

    public void updateKOButtonVisibleState() {

        Collection<KOSpecialElement> koElements = DicomModel.getKoSpecialElements(getSeries());
        boolean koElementExist = koElements != null && !koElements.isEmpty();
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
            needToRepaint = true;
        }

        if (koElementExist) {
            needToRepaint = updateKOselectedState(getImage());
        }

        if (needToRepaint) {
            // Required to update KO bar (toggle button state)
            if (eventManager instanceof EventManager) {
                ((EventManager) eventManager).updateKeyObjectComponentsListener(this);
            }
            repaint();
        }
    }

    /**
     * @param newImg
     * @param img
     *            , newImg
     * @return true if the state has changed and if the view or at least the KO button need to be repaint
     */

    protected boolean updateKOselectedState(DicomImageElement img) {

        eState previousState = koStarButton.getState();

        // evaluate koSelection status for every Image change
        KOViewButton.eState newSelectionState = eState.UNSELECTED;

        Object selectedKO = getActionValue(ActionW.KO_SELECTION.cmd());

        if (img != null) {
            String sopInstanceUID = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
            String seriesInstanceUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);

            if (sopInstanceUID != null && seriesInstanceUID != null) {
                Integer frame = TagD.getTagValue(img, Tag.InstanceNumber, Integer.class);
                if (selectedKO instanceof KOSpecialElement) {
                    KOSpecialElement koElement = (KOSpecialElement) selectedKO;
                    if (koElement.containsSopInstanceUIDReference(seriesInstanceUID, sopInstanceUID, frame)) {
                        newSelectionState = eState.SELECTED;
                    }
                } else {
                    Collection<KOSpecialElement> koElements = DicomModel.getKoSpecialElements(getSeries());

                    if (koElements != null) {
                        for (KOSpecialElement koElement : koElements) {
                            if (koElement.containsSopInstanceUIDReference(seriesInstanceUID, sopInstanceUID, frame)) {
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

        return previousState != newSelectionState;
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
                TagD.getTagValue(series, Tag.Modality));
        }

        updateKOButtonVisibleState();
    }

    @Override
    protected void setImage(DicomImageElement img) {
        boolean newImg = img != null && !img.equals(imageLayer.getSourceImage());
        if (newImg) {
            deletePrLayers();
            PrGraphicUtil.applyPresentationModel(img);
        }
        super.setImage(img);

        if (newImg) {
            updatePrButtonState(img);
            updateKOselectedState(img);
        }
    }

    private void deletePrLayers() {
        // Delete previous PR Layers
        List<GraphicLayer> dcmLayers = (List<GraphicLayer>) actionsInView.get(PRManager.TAG_DICOM_LAYERS);
        if (dcmLayers != null) {
            // Prefer to delete by type because layer uid can change
            for (GraphicLayer layer : dcmLayers) {
                graphicManager.deleteByLayer(layer);
            }
            actionsInView.remove(PRManager.TAG_DICOM_LAYERS);
        }
    }

    void updatePR() {
        DicomImageElement img = imageLayer.getSourceImage();
        if (img != null) {
            updatePrButtonState(img);
        }
    }

    private synchronized void updatePrButtonState(DicomImageElement img) {
        Object oldPR = getActionValue(ActionW.PR_STATE.cmd());
        ViewButton prButton = PRManager.buildPrSelection(this, series, img);
        getViewButtons().removeIf(b -> b == null || b.getIcon() == View2d.PR_ICON);
        if (prButton != null) {
            getViewButtons().add(prButton);
        } else if (oldPR instanceof PRSpecialElement) {
            setPresentationState(null, true);
            actionsInView.put(ActionW.PR_STATE.cmd(), oldPR);
        } else if (ActionState.NoneLabel.NONE.equals(oldPR)) {
            // No persistence for NONE
            actionsInView.put(ActionW.PR_STATE.cmd(), null);
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
            GeometryOfSlice sliceGeometry = image.getDispSliceGeometry();
            if (sliceGeometry != null) {
                ViewCanvas<DicomImageElement> view2DPane = eventManager.getSelectedViewPane();
                MediaSeries<DicomImageElement> selSeries = view2DPane == null ? null : view2DPane.getSeries();
                if (selSeries != null) {
                    // Get the current image of the selected Series
                    DicomImageElement selImage = view2DPane.getImage();
                    // Get the first and the last image of the selected Series according to Slice Location

                    DicomImageElement firstImage = null;
                    DicomImageElement lastImage = null;
                    double min = Double.MAX_VALUE;
                    double max = -Double.MAX_VALUE;
                    final Iterable<DicomImageElement> list = selSeries.getMedias(
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

                    GraphicLayer layer = AbstractGraphicModel.getOrBuildLayer(this, LayerType.CROSSLINES);
                    // IntersectSlice: display a line representing the center of the slice
                    IntersectSlice slice = new IntersectSlice(sliceGeometry);
                    if (firstImage != null && firstImage != lastImage) {
                        addCrossline(firstImage, layer, slice, false);
                    }
                    if (lastImage != null && firstImage != lastImage) {
                        addCrossline(lastImage, layer, slice, false);
                    }
                    if (selImage != null) {
                        // IntersectVolume: display a rectangle to show the slice thickness
                        if (!addCrossline(selImage, layer, new IntersectVolume(sliceGeometry), true)) {
                            // When the volume limits are outside the image, get the only the intersection
                            addCrossline(selImage, layer, slice, true);
                        }
                    }
                    repaint();
                }
            }
        }

    }

    protected boolean addCrossline(DicomImageElement selImage, GraphicLayer layer, LocalizerPoster localizer,
        boolean center) {
        GeometryOfSlice sliceGeometry = selImage.getDispSliceGeometry();
        if (sliceGeometry != null) {
            List<Point2D.Double> pts = localizer.getOutlineOnLocalizerForThisGeometry(sliceGeometry);
            if (pts != null && !pts.isEmpty()) {
                Color color = center ? Color.blue : Color.cyan;
                try {
                    Graphic graphic;
                    if (pts.size() == 2) {
                        graphic = new LineGraphic().buildGraphic(pts);
                    } else {
                        graphic = new PolygonGraphic().buildGraphic(pts);
                    }
                    graphic.setPaint(color);
                    graphic.setLabelVisible(Boolean.FALSE);
                    graphic.setLayer(layer);

                    graphicManager.addGraphic(graphic);
                    return true;
                } catch (InvalidShapeException e) {
                    LOGGER.error("Building crossline", e); //$NON-NLS-1$
                }
            }
        }
        return false;
    }

    @Override
    public synchronized void enableMouseAndKeyListener(MouseActions actions) {
        disableMouseAndKeyListener();
        iniDefaultMouseListener();
        iniDefaultKeyListener();
        // Set the butonMask to 0 of all the actions
        resetMouseAdapter();

        this.setCursor(DefaultView2d.DEFAULT_CURSOR);

        addMouseAdapter(actions.getLeft(), InputEvent.BUTTON1_DOWN_MASK); // left mouse button
        if (actions.getMiddle().equals(actions.getLeft())) {
            // If mouse action is already registered, only add the modifier mask
            addModifierMask(actions.getMiddle(), InputEvent.BUTTON2_DOWN_MASK);
        } else {
            addMouseAdapter(actions.getMiddle(), InputEvent.BUTTON2_DOWN_MASK);// middle mouse button
        }
        if (actions.getRight().equals(actions.getLeft()) || actions.getRight().equals(actions.getMiddle())) {
            // If mouse action is already registered, only add the modifier mask
            addModifierMask(actions.getRight(), InputEvent.BUTTON3_DOWN_MASK);
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
        if (adapter == graphicMouseHandler) {
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
            // set level action with inverse progression (moving the cursor down will decrease the values)
            adapter.setInverse(eventManager.getOptions().getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
        } else if (actionName.equals(ActionW.WINDOW.cmd())) {
            adapter.setMoveOnX(false);
        } else if (actionName.equals(ActionW.LEVEL.cmd())) {
            adapter.setInverse(eventManager.getOptions().getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
        }
        this.addMouseListener(adapter);
        this.addMouseMotionListener(adapter);
    }

    private void addModifierMask(String action, int mask) {
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

    protected MouseActionAdapter getMouseAdapter(String command) {
        if (command.equals(ActionW.CONTEXTMENU.cmd())) {
            return contextMenuHandler;
        } else if (command.equals(ActionW.WINLEVEL.cmd())) {
            return getAction(ActionW.LEVEL);
        }

        Optional<ActionW> actionKey = eventManager.getActionKey(command);
        if (!actionKey.isPresent()) {
            return null;
        }

        if (actionKey.get().isDrawingAction()) {
            return graphicMouseHandler;
        }
        return eventManager.getAction(actionKey.get(), MouseActionAdapter.class).orElse(null);
    }

    public void computeCrosshair(Point3d p3) {
        DicomImageElement image = this.getImage();
        if (image != null) {
            graphicManager.deleteByLayerType(LayerType.CROSSLINES);
            GraphicLayer layer = AbstractGraphicModel.getOrBuildLayer(this, LayerType.CROSSLINES);
            GeometryOfSlice sliceGeometry = image.getDispSliceGeometry();
            if (sliceGeometry != null) {
                SliceOrientation sliceOrientation = this.getSliceOrientation();
                if (sliceOrientation != null && p3 != null) {
                    Point2D p = sliceGeometry.getImagePosition(p3);
                    Tuple3d dimensions = sliceGeometry.getDimensions();
                    boolean axial = SliceOrientation.AXIAL.equals(sliceOrientation);
                    Point2D centerPt = new Point2D.Double(p.getX(), p.getY());

                    List<Point2D.Double> pts = new ArrayList<>();
                    pts.add(new Point2D.Double(p.getX(), 0.0));
                    pts.add(new Point2D.Double(p.getX(), dimensions.x));

                    boolean sagittal = SliceOrientation.SAGITTAL.equals(sliceOrientation);
                    Color color1 = sagittal ? Color.GREEN : Color.BLUE;
                    addCrosshairLine(layer, pts, color1, centerPt);

                    List<Point2D.Double> pts2 = new ArrayList<>();
                    Color color2 = axial ? Color.GREEN : Color.RED;
                    pts2.add(new Point2D.Double(0.0, p.getY()));
                    pts2.add(new Point2D.Double(dimensions.y, p.getY()));
                    addCrosshairLine(layer, pts2, color2, centerPt);

                    PlanarImage dispImg = image.getImage();
                    if (dispImg != null) {
                        Rectangle2D rect = new Rectangle2D.Double(0, 0, dispImg.width() * image.getRescaleX(),
                            dispImg.height() * image.getRescaleY());
                        addRectangle(layer, rect, axial ? Color.RED : sagittal ? Color.BLUE : Color.GREEN);
                    }
                }
            }
        }
    }

    protected void addCrosshairLine(GraphicLayer layer, List<Point2D.Double> pts, Color color, Point2D center) {
        if (pts != null && !pts.isEmpty()) {
            try {
                Graphic graphic;
                if (pts.size() == 2) {
                    LineWithGapGraphic line = new LineWithGapGraphic();
                    line.setCenterGap(center);
                    line.setGapSize(50);
                    graphic = line.buildGraphic(pts);
                } else {
                    graphic = new PolygonGraphic().buildGraphic(pts);
                }
                graphic.setPaint(color);
                graphic.setLabelVisible(Boolean.FALSE);
                graphic.setLayer(layer);

                graphicManager.addGraphic(graphic);
            } catch (InvalidShapeException e) {
                LOGGER.error("Add crosshair line", e); //$NON-NLS-1$
            }

        }
    }

    protected void addRectangle(GraphicLayer layer, Rectangle2D rect, Color color) {
        if (rect != null && layer != null) {
            try {
                Graphic graphic = new RectangleGraphic().buildGraphic(rect);
                graphic.setPaint(color);
                graphic.setLabelVisible(Boolean.FALSE);
                graphic.setFilled(Boolean.FALSE);
                graphic.setLayer(layer);

                graphicManager.addGraphic(graphic);

            } catch (InvalidShapeException e) {
                LOGGER.error("Add rectangle", e); //$NON-NLS-1$
            }
        }
    }

    public SliceOrientation getSliceOrientation() {
        SliceOrientation sliceOrientation = null;
        MediaSeries<DicomImageElement> s = getSeries();
        if (s != null) {
            Object img = s.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
            if (img instanceof DicomImageElement) {
                double[] v = TagD.getTagValue((DicomImageElement) img, Tag.ImageOrientationPatient, double[].class);
                if (v != null && v.length == 6) {
                    Label orientation = ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(v[0],
                        v[1], v[2], v[3], v[4], v[5]);
                    if (ImageOrientation.Label.AXIAL.equals(orientation)) {
                        sliceOrientation = SliceOrientation.AXIAL;
                    } else if (ImageOrientation.Label.CORONAL.equals(orientation)) {
                        sliceOrientation = SliceOrientation.CORONAL;
                    } else if (ImageOrientation.Label.SAGITTAL.equals(orientation)) {
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
        graphicMouseHandler.setButtonMaskEx(0);
    }

    protected MouseActionAdapter getAction(ActionW action) {
        ActionState a = eventManager.getAction(action);
        if (a instanceof MouseActionAdapter) {
            return (MouseActionAdapter) a;
        }
        return null;
    }

    @Override
    protected void fillPixelInfo(final PixelInfo pixelInfo, final DicomImageElement imageElement, final double[] c) {
        if (c != null && c.length >= 1) {
            boolean pixelPadding = LangUtil.getNULLtoTrue(
                (Boolean) getDisplayOpManager().getParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd()));

            PresentationStateReader prReader =
                (PresentationStateReader) getActionValue(PresentationStateReader.TAG_PR_READER);
            for (int i = 0; i < c.length; i++) {
                c[i] = imageElement.pixel2mLUT(c[i], prReader, pixelPadding);
            }
            pixelInfo.setValues(c);
        }
    }

    @Override
    public void handleLayerChanged(ImageLayer layer) {
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

    protected JPopupMenu buildGraphicContextMenu(final MouseEvent evt, final List<Graphic> selected) {
        if (selected != null) {
            final JPopupMenu popupMenu = new JPopupMenu();
            TitleMenuItem itemTitle = new TitleMenuItem(Messages.getString("View2d.selection"), popupMenu.getInsets()); //$NON-NLS-1$
            popupMenu.add(itemTitle);
            popupMenu.addSeparator();
            boolean graphicComplete = true;
            if (selected.size() == 1) {
                final Graphic graph = selected.get(0);
                if (graph instanceof DragGraphic) {
                    final DragGraphic absgraph = (DragGraphic) graph;
                    if (!absgraph.isGraphicComplete()) {
                        graphicComplete = false;
                    }
                    if (absgraph.getVariablePointsNumber()) {
                        if (graphicComplete) {
                            /*
                             * Convert mouse event point to real image coordinate point (without geometric
                             * transformation)
                             */
                            final MouseEventDouble mouseEvt = new MouseEventDouble(View2d.this,
                                MouseEvent.MOUSE_RELEASED, evt.getWhen(), 16, 0, 0, 0, 0, 1, true, 1);
                            mouseEvt.setSource(View2d.this);
                            mouseEvt.setImageCoordinates(getImageCoordinatesFromMouse(evt.getX(), evt.getY()));
                            final int ptIndex = absgraph.getHandlePointIndex(mouseEvt);
                            if (ptIndex >= 0) {
                                JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.rmv_pt")); //$NON-NLS-1$
                                menuItem.addActionListener(e -> absgraph.removeHandlePoint(ptIndex, mouseEvt));
                                popupMenu.add(menuItem);

                                menuItem = new JMenuItem(Messages.getString("View2d.draw_pt")); //$NON-NLS-1$
                                menuItem.addActionListener(e -> {
                                    absgraph.forceToAddPoints(ptIndex);
                                    MouseEventDouble evt2 =
                                        new MouseEventDouble(View2d.this, MouseEvent.MOUSE_PRESSED, evt.getWhen(), 16,
                                            evt.getX(), evt.getY(), evt.getXOnScreen(), evt.getYOnScreen(), 1, true, 1);
                                    graphicMouseHandler.mousePressed(evt2);
                                });
                                popupMenu.add(menuItem);
                                popupMenu.add(new JSeparator());
                            }
                        } else if (graphicMouseHandler.getDragSequence() != null
                            && Objects.equals(absgraph.getPtsNumber(), Graphic.UNDEFINED)) {
                            final JMenuItem item2 = new JMenuItem(Messages.getString("View2d.stop_draw")); //$NON-NLS-1$
                            item2.addActionListener(e -> {
                                MouseEventDouble event =
                                    new MouseEventDouble(View2d.this, 0, 0, 16, 0, 0, 0, 0, 2, true, 1);
                                graphicMouseHandler.getDragSequence().completeDrag(event);
                                graphicMouseHandler.mouseReleased(event);
                            });
                            popupMenu.add(item2);
                            popupMenu.add(new JSeparator());
                        }
                    }
                }
            }

            if (graphicComplete) {
                JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.delete_sel")); //$NON-NLS-1$
                menuItem
                    .addActionListener(e -> View2d.this.getGraphicManager().deleteSelectedGraphics(View2d.this, true));
                popupMenu.add(menuItem);

                menuItem = new JMenuItem(Messages.getString("View2d.cut")); //$NON-NLS-1$
                menuItem.addActionListener(e -> {
                    DefaultView2d.GRAPHIC_CLIPBOARD.setGraphics(selected);
                    View2d.this.getGraphicManager().deleteSelectedGraphics(View2d.this, false);
                });
                popupMenu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2d.copy")); //$NON-NLS-1$
                menuItem.addActionListener(e -> DefaultView2d.GRAPHIC_CLIPBOARD.setGraphics(selected));
                popupMenu.add(menuItem);
                popupMenu.add(new JSeparator());
            }

            // TODO separate AbstractDragGraphic and ClassGraphic for properties
            final ArrayList<DragGraphic> list = new ArrayList<>();
            for (Graphic graphic : selected) {
                if (graphic instanceof DragGraphic) {
                    list.add((DragGraphic) graphic);
                }
            }

            if (selected.size() == 1) {
                final Graphic graph = selected.get(0);
                JMenuItem item = new JMenuItem(Messages.getString("View2d.to_front")); //$NON-NLS-1$
                item.addActionListener(e -> graph.toFront());
                popupMenu.add(item);
                item = new JMenuItem(Messages.getString("View2d.to_back")); //$NON-NLS-1$
                item.addActionListener(e -> graph.toBack());
                popupMenu.add(item);
                popupMenu.add(new JSeparator());

                if (graphicComplete && graph instanceof LineGraphic) {

                    final JMenuItem calibMenu = new JMenuItem(Messages.getString("View2d.chg_calib")); //$NON-NLS-1$
                    calibMenu.addActionListener(e -> {
                        String title = Messages.getString("View2d.clibration"); //$NON-NLS-1$
                        CalibrationView calibrationDialog = new CalibrationView((LineGraphic) graph, View2d.this, true);
                        ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2d.this);
                        int res = JOptionPane.showConfirmDialog(ColorLayerUI.getContentPane(layer), calibrationDialog,
                            title, JOptionPane.OK_CANCEL_OPTION);
                        if (layer != null) {
                            layer.hideUI();
                        }
                        if (res == JOptionPane.OK_OPTION) {
                            calibrationDialog.applyNewCalibration();
                        }
                    });
                    popupMenu.add(calibMenu);
                    popupMenu.add(new JSeparator());
                }
            }

            if (!list.isEmpty()) {
                JMenuItem properties = new JMenuItem(Messages.getString("View2d.draw_prop")); //$NON-NLS-1$
                properties.addActionListener(e -> {
                    ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2d.this);
                    JDialog dialog = new MeasureDialog(View2d.this, list);
                    ColorLayerUI.showCenterScreen(dialog, layer);
                });
                popupMenu.add(properties);
            }
            return popupMenu;
        }
        return null;
    }

    protected JPopupMenu buildContexMenu(final MouseEvent evt) {
        JPopupMenu popupMenu = new JPopupMenu();
        TitleMenuItem itemTitle =
            new TitleMenuItem(Messages.getString("View2d.left_mouse") + StringUtil.COLON, popupMenu.getInsets()); //$NON-NLS-1$
        popupMenu.add(itemTitle);
        popupMenu.setLabel(MouseActions.T_LEFT);
        String action = eventManager.getMouseActions().getLeft();
        ButtonGroup groupButtons = new ButtonGroup();
        int count = popupMenu.getComponentCount();
        ImageViewerPlugin<DicomImageElement> view = eventManager.getSelectedView2dContainer();
        if (view != null) {
            final ViewerToolBar<?> toolBar = view.getViewerToolBar();
            if (toolBar != null) {
                ActionListener leftButtonAction = event -> {
                    if (event.getSource() instanceof JRadioButtonMenuItem) {
                        JRadioButtonMenuItem item = (JRadioButtonMenuItem) event.getSource();
                        toolBar.changeButtonState(MouseActions.T_LEFT, item.getActionCommand());
                    }
                };

                List<ActionW> actionsButtons = ViewerToolBar.actionsButtons;
                synchronized (actionsButtons) {
                    for (int i = 0; i < actionsButtons.size(); i++) {
                        ActionW b = actionsButtons.get(i);
                        if (eventManager.isActionRegistered(b)) {
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
        }

        if (count < popupMenu.getComponentCount()) {
            popupMenu.add(new JSeparator());
            count = popupMenu.getComponentCount();
        }

        if (DefaultView2d.GRAPHIC_CLIPBOARD.hasGraphics()) {
            JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.paste_draw")); //$NON-NLS-1$
            menuItem.addActionListener(e -> copyGraphicsFromClipboard());
            popupMenu.add(menuItem);
        }

        if (count < popupMenu.getComponentCount()) {
            popupMenu.add(new JSeparator());
            count = popupMenu.getComponentCount();
        }

        if (eventManager instanceof EventManager) {
            EventManager manager = (EventManager) eventManager;
            JMVUtils.addItemToMenu(popupMenu, manager.getPresetMenu("weasis.contextmenu.presets")); //$NON-NLS-1$
            JMVUtils.addItemToMenu(popupMenu, manager.getLutShapeMenu("weasis.contextmenu.lutShape")); //$NON-NLS-1$
            JMVUtils.addItemToMenu(popupMenu, manager.getLutMenu("weasis.contextmenu.lut")); //$NON-NLS-1$
            JMVUtils.addItemToMenu(popupMenu, manager.getLutInverseMenu("weasis.contextmenu.invertLut")); //$NON-NLS-1$
            JMVUtils.addItemToMenu(popupMenu, manager.getFilterMenu("weasis.contextmenu.filter")); //$NON-NLS-1$

            if (count < popupMenu.getComponentCount()) {
                popupMenu.add(new JSeparator());
                count = popupMenu.getComponentCount();
            }

            JMVUtils.addItemToMenu(popupMenu, manager.getZoomMenu("weasis.contextmenu.zoom")); //$NON-NLS-1$
            JMVUtils.addItemToMenu(popupMenu, manager.getOrientationMenu("weasis.contextmenu.orientation")); //$NON-NLS-1$
            JMVUtils.addItemToMenu(popupMenu, manager.getSortStackMenu("weasis.contextmenu.sortstack")); //$NON-NLS-1$

            if (count < popupMenu.getComponentCount()) {
                popupMenu.add(new JSeparator());
            }

            JMVUtils.addItemToMenu(popupMenu, manager.getResetMenu("weasis.contextmenu.reset")); //$NON-NLS-1$
        }

        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.contextmenu.close", true)) { //$NON-NLS-1$
            JMenuItem close = new JMenuItem(Messages.getString("View2d.close")); //$NON-NLS-1$
            close.addActionListener(e -> View2d.this.setSeries(null, null));
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
                final List<Graphic> selected = View2d.this.getGraphicManager().getSelectedGraphics();
                if (!selected.isEmpty() && isDrawActionActive()) {
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
                || support.isDataFlavorSupported(UriListFlavor.flavor)) {
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
            // Not supported by some OS
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                } catch (Exception e) {
                    LOGGER.error("Get dragable files", e); //$NON-NLS-1$
                }
                return dropDicomFiles(files);
            }
            // When dragging a file or group of files
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4899516
            else if (support.isDataFlavorSupported(UriListFlavor.flavor)) {
                try {
                    // Files with spaces in the filename trigger an error
                    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6936006
                    String val = (String) transferable.getTransferData(UriListFlavor.flavor);
                    files = UriListFlavor.textURIListToFileList(val);
                } catch (Exception e) {
                    LOGGER.error("Get dragable URIs", e); //$NON-NLS-1$
                }
                return dropDicomFiles(files);
            }

            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            DataExplorerModel model = null;
            SeriesSelectionModel selList = null;
            if (dicomView != null) {
                selList = ((DicomExplorer) dicomView).getSelectionList();
            }
            Optional<ViewerPlugin<?>> pluginOp = UIManager.VIEWER_PLUGINS.stream()
                .filter(p -> p instanceof View2dContainer && ((View2dContainer) p).isContainingView(View2d.this))
                .findFirst();
            if(!pluginOp.isPresent()) {
                return false;
            }
            
            View2dContainer selPlugin = (View2dContainer) pluginOp.get();
            Series seq;
            try {
                seq = (Series) transferable.getTransferData(Series.sequenceDataFlavor);
                if (seq == null) {
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
                    if (p1 == null) {
                        return false;
                    }
                    if (p1.equals(selPlugin.getGroupID())) {
                        p2 = p1;
                    }

                    if (!p1.equals(p2)) {
                        SeriesViewerFactory plugin = UIManager.getViewerFactory(selPlugin);
                        if (plugin != null && !(plugin instanceof MimeSystemAppFactory)) {
                            ViewerPluginBuilder.openSequenceInPlugin(plugin, seq, model, true, true);
                        }
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
                LOGGER.error("Get dragable series", e); //$NON-NLS-1$
                return false;
            } finally {
                if (selList != null) {
                    selList.setOpenningSeries(false);
                }
            }
            if (selList != null) {
                selList.setOpenningSeries(true);
            }

            if (SynchData.Mode.TILE.equals(selPlugin.getSynchView().getSynchData().getMode())) {
                selPlugin.addSeries(seq);
                if (selList != null) {
                    selList.setOpenningSeries(false);
                }
                return true;
            }

            setSeries(seq);
            // Getting the focus has a delay and so it will trigger the view selection later
            if (selPlugin.isContainingView(View2d.this)) {
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

                LoadLocalDicom dicom = new LoadLocalDicom(files.stream().toArray(File[]::new), true, model);
                DicomModel.LOADING_EXECUTOR.execute(dicom);
                return true;
            }
            return false;
        }
    }
}
