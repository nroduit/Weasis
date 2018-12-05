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
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.swing.ButtonGroup;
import javax.swing.JPopupMenu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.image.CropOp;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.util.CIELab;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.EscapeChars;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.AbstractGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.graphic.imp.PointGraphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.pr.PrGraphicUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;

public class PRManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PRManager.class);

    public static final String PR_APPLY = "weasis.apply.latest.pr"; //$NON-NLS-1$

    public static final String PR_PRESETS = "pr.presets"; //$NON-NLS-1$
    public static final String TAG_CHANGE_PIX_CONFIG = "change.pixel"; //$NON-NLS-1$
    public static final String TAG_PR_ZOOM = "original.zoom"; //$NON-NLS-1$
    public static final String TAG_DICOM_LAYERS = "pr.layers"; //$NON-NLS-1$

    public static void applyPresentationState(ViewCanvas<DicomImageElement> view, PresentationStateReader reader,
        DicomImageElement img) {
        if (view == null || reader == null || img == null) {
            return;
        }

        // TODO should move to the model
        Map<String, Object> actionsInView = view.getActionsInView();
        reader.applySpatialTransformationModule(actionsInView);
        List<PresetWindowLevel> presets = reader.getPresetCollection(img);
        ImageOpNode node = view.getDisplayOpManager().getNode(WindowOp.OP_NAME);
        if (node != null) {
            List<PresetWindowLevel> presetList =
                img.getPresetList(LangUtil.getNULLtoTrue((Boolean) node.getParam(ActionW.IMAGE_PIX_PADDING.cmd())));
            PresetWindowLevel autoPR = getAutoLevelPreset(presets);
            if (autoPR != null) {
                presets.remove(autoPR);
                PresetWindowLevel autoImg = getAutoLevelPreset(presetList);
                if (!autoPR.equals(autoImg)) {
                    // It happens when PR contains a new Modality LUT
                    String name = org.weasis.dicom.codec.Messages.getString("PresetWindowLevel.full"); //$NON-NLS-1$
                    presets.add(new PresetWindowLevel(name + " [PR]", autoPR.getWindow(), autoPR.getLevel(), //$NON-NLS-1$
                        autoPR.getShape()));
                }
            }
            presets.addAll(presetList);
        }
        PresetWindowLevel p = presets.get(0);
        actionsInView.put(ActionW.WINDOW.cmd(), p.getWindow());
        actionsInView.put(ActionW.LEVEL.cmd(), p.getLevel());
        actionsInView.put(PRManager.PR_PRESETS, presets);
        actionsInView.put(ActionW.PRESET.cmd(), p);
        actionsInView.put(ActionW.LUT_SHAPE.cmd(), p.getLutShape());
        actionsInView.put(ActionW.DEFAULT_PRESET.cmd(), true);

        applyPixelSpacing(view, reader, img);

        GraphicModel graphicModel = PrGraphicUtil.getPresentationModel(reader.getDcmobj());
        // GraphicModel graphicModel = null;
        List<GraphicLayer> layers =
            graphicModel == null ? readGraphicAnnotation(view, reader, img) : readXmlModel(view, graphicModel);

        if (layers != null) {
            view.setActionsInView(PRManager.TAG_DICOM_LAYERS, layers);
        }
    }

    private static PresetWindowLevel getAutoLevelPreset(List<PresetWindowLevel> presets) {
        for (PresetWindowLevel presetWindowLevel : presets) {
            if (presetWindowLevel.isAutoLevel()) {
                return presetWindowLevel;
            }
        }
        return null;
    }

    private static void applyPixelSpacing(ViewCanvas<DicomImageElement> view, PresentationStateReader reader,
        DicomImageElement img) {
        Map<String, Object> actionsInView = view.getActionsInView();
        reader.readDisplayArea(img);

        String presentationMode = TagD.getTagValue(reader, Tag.PresentationSizeMode, String.class);
        boolean trueSize = "TRUE SIZE".equalsIgnoreCase(presentationMode); //$NON-NLS-1$

        double[] prPixSize = TagD.getTagValue(reader, Tag.PresentationPixelSpacing, double[].class);
        if (prPixSize != null && prPixSize.length == 2 && prPixSize[0] > 0.0 && prPixSize[1] > 0.0) {
            if (trueSize) {
                changePixelSize(img, actionsInView, prPixSize);
                img.setPixelSpacingUnit(Unit.MILLIMETER);
                ActionState spUnitAction = EventManager.getInstance().getAction(ActionW.SPATIAL_UNIT);
                if (spUnitAction instanceof ComboItemListener) {
                    ((ComboItemListener) spUnitAction).setSelectedItem(Unit.MILLIMETER);
                }
            } else {
                applyAspectRatio(img, actionsInView, prPixSize);
            }
        }
        if (prPixSize == null) {
            int[] aspects = TagD.getTagValue(reader, Tag.PresentationPixelAspectRatio, int[].class);
            if (aspects != null && aspects.length == 2) {
                applyAspectRatio(img, actionsInView, new double[] { aspects[0], aspects[1] });
            }
        }

        int[] tlhc = TagD.getTagValue(reader, Tag.DisplayedAreaTopLeftHandCorner, int[].class);
        int[] brhc = TagD.getTagValue(reader, Tag.DisplayedAreaBottomRightHandCorner, int[].class);
        // TODO http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.10.4.html
        // http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.8.12.2.html
        String pixelOriginInterpretation = TagD.getTagValue(reader, Tag.PixelOriginInterpretation, String.class);

        if (tlhc != null && tlhc.length == 2 && brhc != null && brhc.length == 2) {
            // Lots of systems encode topLeft as 1,1, even when they mean 0,0
            if (tlhc[0] == 1) {
                tlhc[0] = 0;
            }
            if (tlhc[1] == 1) {
                tlhc[1] = 0;
            }
            Rectangle area = new Rectangle();
            double ratiox = img.getRescaleX();
            double ratioy = img.getRescaleY();
            area.setFrameFromDiagonal(getDisplayLength(tlhc[0], ratiox), getDisplayLength(tlhc[1], ratioy),
                getDisplayLength(brhc[0], ratiox), getDisplayLength(brhc[1], ratioy));

            PlanarImage source = view.getSourceImage();
            if (source != null) {
                Rectangle imgBouds = ImageConversion.getBounds(source);
                area = area.intersection(imgBouds);
                if (area.width > 1 && area.height > 1 && !area.equals(imgBouds)) {
                    SimpleOpManager opManager =
                        Optional.ofNullable((SimpleOpManager) actionsInView.get(ActionW.PREPROCESSING.cmd()))
                            .orElseGet(SimpleOpManager::new);
                    CropOp crop = new CropOp();
                    crop.setParam(CropOp.P_AREA, area);
                    opManager.addImageOperationAction(crop);
                    actionsInView.put(ActionW.PREPROCESSING.cmd(), opManager);
                }
            }
            actionsInView.put(ActionW.CROP.cmd(), area);
        }

        if ("SCALE TO FIT".equalsIgnoreCase(presentationMode)) { //$NON-NLS-1$
            actionsInView.put(PRManager.TAG_PR_ZOOM, -200.0);
        } else if ("MAGNIFY".equalsIgnoreCase(presentationMode)) { //$NON-NLS-1$
            Float val = TagD.getTagValue(reader, Tag.PresentationPixelMagnificationRatio, Float.class);
            actionsInView.put(PRManager.TAG_PR_ZOOM, val == null ? 1.0 : val);
        } else if (trueSize) {
            // Required to calibrate the screen in preferences
            actionsInView.put(PRManager.TAG_PR_ZOOM, -100.0);
        }
    }

    private static int getDisplayLength(int length, double ratio) {
        return (int) Math.ceil(length * ratio - 0.5);
    }

    private static void applyAspectRatio(DicomImageElement img, Map<String, Object> actionsInView, double[] aspects) {
        if (MathUtil.isDifferent(aspects[0], aspects[1])) {
            // set the aspects to the pixel size of the image to stretch the image rendering (square pixel)
            double[] pixelsize;
            if (aspects[1] < aspects[0]) {
                pixelsize = new double[] { aspects[0] / aspects[1], 1.0 };
            } else {
                pixelsize = new double[] { 1.0, aspects[1] / aspects[0] };
            }
            changePixelSize(img, actionsInView, pixelsize);
            img.setPixelSpacingUnit(Unit.PIXEL);
            // TODO update graphics
        }
    }

    private static void changePixelSize(DicomImageElement img, Map<String, Object> actionsInView, double[] prPixSize) {
        img.setPixelSize(prPixSize[1], prPixSize[0]);
        actionsInView.put(PRManager.TAG_CHANGE_PIX_CONFIG, true);

        ZoomOp node = img.getRectifyAspectRatioZoomOp();
        if (node != null) {
            SimpleOpManager process = new SimpleOpManager();
            process.addImageOperationAction(node);
            OpManager preprocessing = (OpManager) actionsInView.get(ActionW.PREPROCESSING.cmd());
            if (preprocessing != null) {
                for (ImageOpNode op : preprocessing.getOperations()) {
                    if (!node.getName().equals(op.getName())) {
                        process.addImageOperationAction(op);
                    }
                }
            }
            actionsInView.put(ActionW.PREPROCESSING.cmd(), process);
        }
    }

    private static ArrayList<GraphicLayer> readXmlModel(ViewCanvas<DicomImageElement> view, GraphicModel graphicModel) {
        ArrayList<GraphicLayer> layers = new ArrayList<>();
        int k = 0;
        for (GraphicLayer layer : graphicModel.getLayers()) {
            layer.setName(Optional.ofNullable(layer.getName()).orElseGet(layer.getType()::getDefaultName) + " [DICOM]"); //$NON-NLS-1$
            layer.setLocked(true);
            layer.setSerializable(false);
            layer.setLevel(270 + k++);
            layers.add(layer);
        }

        for (Graphic g : graphicModel.getModels()) {
            AbstractGraphicModel.addGraphicToModel(view, g.getLayer(), g);
        }
        return layers;

    }

    private static ArrayList<GraphicLayer> readGraphicAnnotation(ViewCanvas<DicomImageElement> view,
        PresentationStateReader reader, DicomImageElement img) {
        Map<String, Object> actionsInView = view.getActionsInView();

        ArrayList<GraphicLayer> layers = null;
        Attributes dcmobj = reader.getDcmobj();
        if (dcmobj != null) {
            Sequence gams = dcmobj.getSequence(Tag.GraphicAnnotationSequence);
            Sequence layerSeqs = dcmobj.getSequence(Tag.GraphicLayerSequence);

            if (gams != null && layerSeqs != null) {
                Map<String, Attributes> glms = new HashMap<>(layerSeqs.size());
                for (Attributes a : layerSeqs) {
                    glms.put(a.getString(Tag.GraphicLayer), a);
                }
                /*
                 * Apply spatial transformations (rotation, flip) AFTER when graphics are in PIXEL mode and BEFORE when
                 * graphics are in DISPLAY mode.
                 */
                int rotation = (Integer) actionsInView.getOrDefault(PresentationStateReader.TAG_PR_ROTATION, 0);
                boolean flip = (Boolean) actionsInView.getOrDefault(PresentationStateReader.TAG_PR_FLIP, false);
                Rectangle area = (Rectangle) actionsInView.get(ActionW.CROP.cmd());
                Rectangle2D modelArea = view.getViewModel().getModelArea();
                double width = area == null ? modelArea.getWidth() : area.getWidth();
                double height = area == null ? modelArea.getHeight() : area.getHeight();
                AffineTransform inverse = null;
                if (rotation != 0 || flip) {
                    double offsetx = area == null ? 0.0 : area.getX() / area.getWidth();
                    double offsety = area == null ? 0.0 : area.getY() / area.getHeight();
                    // Create inverse transformation for display coordinates (will convert in real coordinates)
                    inverse = AffineTransform.getTranslateInstance(offsetx, offsety);
                    if (flip) {
                        inverse.scale(-1.0, 1.0);
                        inverse.translate(-1.0, 0.0);
                    }
                    if (rotation != 0) {
                        inverse.rotate(Math.toRadians(rotation), 0.5, 0.5);
                    }
                }

                layers = new ArrayList<>();

                for (Attributes gram : gams) {
                    String graphicLayerName = gram.getString(Tag.GraphicLayer);
                    Attributes glm = glms.get(graphicLayerName);
                    if (glm == null || !PresentationStateReader.isModuleAppicable(gram, img)) {
                        continue;
                    }

                    GraphicLayer layer = new DefaultLayer(LayerType.DICOM_PR);
                    layer.setName(graphicLayerName + " [DICOM]"); //$NON-NLS-1$
                    layer.setSerializable(false);
                    layer.setLocked(true);
                    layer.setSelectable(false);
                    layer.setLevel(310 + glm.getInt(Tag.GraphicLayerOrder, 0));
                    layers.add(layer);

                    Integer grayVal = DicomMediaUtils.getIntegerFromDicomElement(glm,
                        Tag.GraphicLayerRecommendedDisplayGrayscaleValue, null);
                    float[] colorLab = CIELab.convertToFloatLab(DicomMediaUtils.getIntAyrrayFromDicomElement(glm,
                        Tag.GraphicLayerRecommendedDisplayCIELabValue, null));
                    int[] colorRgb = DicomMediaUtils.getIntAyrrayFromDicomElement(glm,
                        Tag.GraphicLayerRecommendedDisplayRGBValue, null);
                    if (colorRgb == null && colorLab == null && grayVal == null) {
                        Color c = Optional.ofNullable(MeasureTool.viewSetting.getLineColor()).orElse(Color.YELLOW);
                        colorRgb = new int[] { c.getRed(), c.getGreen(), c.getBlue() };
                    }

                    Color rgb =
                        PresentationStateReader.getRGBColor(grayVal == null ? 255 : grayVal, colorLab, colorRgb);

                    Sequence gos = gram.getSequence(Tag.GraphicObjectSequence);

                    if (gos != null) {
                        for (Attributes go : gos) {
                            Graphic graphic;
                            try {
                                graphic =
                                    PrGraphicUtil.buildGraphic(go, rgb, false, width, height, true, inverse, false);
                                if (graphic != null) {
                                    AbstractGraphicModel.addGraphicToModel(view, layer, graphic);
                                }
                            } catch (InvalidShapeException e) {
                                LOGGER.error("Cannot create graphic: " + e.getMessage(), e); //$NON-NLS-1$
                            }
                        }
                    }

                    Sequence txos = gram.getSequence(Tag.TextObjectSequence);
                    if (txos != null) {
                        for (Attributes txo : txos) {
                            Attributes style = txo.getNestedDataset(Tag.LineStyleSequence);
                            Float thickness = DicomMediaUtils.getFloatFromDicomElement(style, Tag.LineThickness, 1.0f);
                            if (style != null) {
                                float[] lab = CIELab.convertToFloatLab(style.getInts(Tag.PatternOnColorCIELabValue));
                                if (lab != null) {
                                    rgb = PresentationStateReader.getRGBColor(255, lab, (int[]) null);
                                }
                            }

                            String[] textLines = EscapeChars.convertToLines(txo.getString(Tag.UnformattedTextValue));
                            // MATRIX not implemented
                            boolean isDisp = "DISPLAY".equalsIgnoreCase(txo.getString(Tag.BoundingBoxAnnotationUnits)); //$NON-NLS-1$
                            float[] topLeft = txo.getFloats(Tag.BoundingBoxTopLeftHandCorner);
                            float[] bottomRight = txo.getFloats(Tag.BoundingBoxBottomRightHandCorner);
                            Rectangle2D rect = null;
                            if (topLeft != null && bottomRight != null) {
                                if (topLeft[0] > bottomRight[0]) {
                                    float b = topLeft[0];
                                    topLeft[0] = bottomRight[0];
                                    bottomRight[0] = b;
                                }
                                if (topLeft[1] > bottomRight[1]) {
                                    float b = topLeft[1];
                                    topLeft[1] = bottomRight[1];
                                    bottomRight[1] = b;
                                }
                                rect = new Rectangle2D.Double(topLeft[0], topLeft[1], bottomRight[0] - topLeft[0],
                                    bottomRight[1] - topLeft[1]);
                                if (isDisp) {
                                    rect.setRect(rect.getX() * width, rect.getY() * height, rect.getWidth() * width,
                                        rect.getHeight() * height);
                                    if (inverse != null) {
                                        float[] dstPt1 = new float[2];
                                        float[] dstPt2 = new float[2];
                                        inverse.transform(topLeft, 0, dstPt1, 0, 1);
                                        inverse.transform(bottomRight, 0, dstPt2, 0, 1);
                                        rect.setFrameFromDiagonal(dstPt1[0] * width, dstPt1[1] * height,
                                            dstPt2[0] * width, dstPt2[1] * height);
                                    }
                                }
                            }

                            float[] anchor = txo.getFloats(Tag.AnchorPoint);
                            if (anchor != null && anchor.length == 2) {
                                // MATRIX not implemented
                                boolean disp =
                                    "DISPLAY".equalsIgnoreCase(txo.getString(Tag.AnchorPointAnnotationUnits)); //$NON-NLS-1$
                                double x = disp ? anchor[0] * width : anchor[0];
                                double y = disp ? anchor[1] * height : anchor[1];
                                Point2D.Double ptAnchor = new Point2D.Double(x, y);
                                /*
                                 * Use the center of the box. Do not follow DICOM specs: displaying the bounding box
                                 * even the text doesn't match. Does not make sense!
                                 */
                                Point2D.Double ptBox =
                                    rect == null ? ptAnchor : new Point2D.Double(rect.getCenterX(), rect.getCenterY());
                                if (!PrGraphicUtil.getBooleanValue(txo, Tag.AnchorPointVisibility)) {
                                    ptAnchor = null;
                                }
                                if (ptAnchor != null && ptAnchor.equals(ptBox)) {
                                    ptBox = new Point2D.Double(ptAnchor.getX() + 20, ptAnchor.getY() + 50);
                                }

                                try {
                                    List<Point2D.Double> pts = new ArrayList<>(2);
                                    pts.add(ptAnchor);
                                    pts.add(ptBox);
                                    Graphic g = new AnnotationGraphic().buildGraphic(pts);
                                    g.setPaint(rgb);
                                    g.setLineThickness(thickness);
                                    g.setLabelVisible(Boolean.TRUE);
                                    g.setLabel(textLines, view);
                                    AbstractGraphicModel.addGraphicToModel(view, layer, g);
                                } catch (InvalidShapeException e) {
                                    LOGGER.error("Cannot create annotation: " + e.getMessage(), e); //$NON-NLS-1$
                                }
                            } else if (rect != null) {
                                try {
                                    Point2D.Double point = new Point2D.Double(rect.getMinX(), rect.getMinY());
                                    AbstractGraphic pt =
                                        (AbstractGraphic) new PointGraphic().buildGraphic(Arrays.asList(point));
                                    pt.setLineThickness(thickness);
                                    pt.setLabelVisible(Boolean.TRUE);
                                    AbstractGraphicModel.addGraphicToModel(view, layer, pt);
                                    pt.setShape(null, null);
                                    pt.setLabel(textLines, view, point);
                                } catch (InvalidShapeException e) {
                                    LOGGER.error("Cannot create annotation: " + e.getMessage(), e); //$NON-NLS-1$
                                }
                            }
                        }
                    }
                }
            }
        }
        return layers;
    }

    /** Indicate if the graphic is to be filled in */

    public static void deleteDicomLayers(List<GraphicLayer> layers, GraphicModel graphicManager) {
        if (layers != null) {
            for (GraphicLayer layer : layers) {
                graphicManager.deleteByLayer(layer);
            }
        }
    }

    public static ViewButton buildPrSelection(final View2d view, MediaSeries<DicomImageElement> series,
        DicomImageElement img) {
        if (view != null && series != null && img != null) {
            Object key = img.getKey();
            List<PRSpecialElement> prList =
                DicomModel.getPrSpecialElements(series, TagD.getTagValue(img, Tag.SOPInstanceUID, String.class),
                    key instanceof Integer ? (Integer) key + 1 : null);
            if (!prList.isEmpty()) {
                Object oldPR = view.getActionValue(ActionW.PR_STATE.cmd());
                if (oldPR == null && !view.getEventManager().getOptions().getBooleanProperty(PR_APPLY, false)) {
                    oldPR = ActionState.NoneLabel.NONE_SERIES;
                    view.setActionsInView(ActionW.PR_STATE.cmd(), oldPR);
                }
                if (!ActionState.NoneLabel.NONE_SERIES.equals(oldPR)) {
                    // Set the previous selected value, otherwise set the more recent PR by default
                    view.setPresentationState(prList.indexOf(oldPR) == -1 ? prList.get(0) : oldPR, true);
                }

                int offset = series.size(null) > 1 ? 2 : 1;
                final Object[] items = new Object[prList.size() + offset];
                items[0] = ActionState.NoneLabel.NONE;
                if (offset == 2) {
                    items[1] = ActionState.NoneLabel.NONE_SERIES;
                }
                for (int i = offset; i < items.length; i++) {
                    items[i] = prList.get(i - offset);
                }
                ViewButton prButton = new ViewButton((invoker, x, y) -> {
                    Object pr = view.getActionValue(ActionW.PR_STATE.cmd());
                    JPopupMenu popupMenu = new JPopupMenu();
                    TitleMenuItem itemTitle = new TitleMenuItem(ActionW.PR_STATE.getTitle(), popupMenu.getInsets());
                    popupMenu.add(itemTitle);
                    popupMenu.addSeparator();
                    ButtonGroup groupButtons = new ButtonGroup();

                    for (Object dcm : items) {
                        final RadioMenuItem menuItem = new RadioMenuItem(dcm.toString(), null, dcm, dcm == pr);
                        menuItem.addActionListener(e -> {
                            if (e.getSource() instanceof RadioMenuItem) {
                                RadioMenuItem item = (RadioMenuItem) e.getSource();
                                Object val = item.getUserObject();
                                view.setPresentationState(val, false);
                            }
                        });
                        groupButtons.add(menuItem);
                        popupMenu.add(menuItem);
                    }
                    popupMenu.show(invoker, x, y);
                }, View2d.PR_ICON);

                prButton.setVisible(true);
                return prButton;
            }
        }
        return null;
    }
}
