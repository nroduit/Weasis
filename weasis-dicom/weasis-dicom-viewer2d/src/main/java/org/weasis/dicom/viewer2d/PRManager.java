package org.weasis.dicom.viewer2d;

import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.JPopupMenu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.EscapeChars;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.ShowPopup;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.graphic.AnnotationGraphic;
import org.weasis.core.ui.graphic.DragLayer;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.InvalidShapeException;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayer.Identifier;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.Layer;
import org.weasis.core.ui.graphic.model.LayerModel;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.GraphicUtil;

public class PRManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PRManager.class);

    public static final String PR_PRESETS = "pr.presets"; //$NON-NLS-1$
    public static final String TAG_CHANGE_PIX_CONFIG = "change.pixel"; //$NON-NLS-1$
    public static final String TAG_PR_ZOOM = "original.zoom"; //$NON-NLS-1$
    public static final String TAG_DICOM_LAYERS = "prSpecialElement.layers"; //$NON-NLS-1$

    public static void applyPresentationState(View2d view, PresentationStateReader reader, DicomImageElement img) {
        if (view == null || reader == null || img == null) {
            return;
        }
        reader.readSpatialTransformationModule();
        reader.readDisplayArea(img);
        reader.readGrayscaleSoftcopyModule(img);
        ArrayList<Layer> layers = readGraphicAnnotation(view, reader, img);
        if (layers != null) {
            EventManager eventManager = EventManager.getInstance();
            SeriesViewerEvent event =
                new SeriesViewerEvent(eventManager.getSelectedView2dContainer(), null, null, EVENT.ADD_LAYER);
            AbstractLayerModel layerModel = view.getLayerModel();
            ArrayList<AbstractLayer.Identifier> list = new ArrayList<>();
            for (Layer layer : layers) {
                list.add(layer.getIdentifier());
                layerModel.addLayer((AbstractLayer) layer);
                event.setShareObject(layer.getIdentifier());
                eventManager.fireSeriesViewerListeners(event);
            }
            view.setActionsInView(PRManager.TAG_DICOM_LAYERS, list);
            view.getLayerModel().SortLayersFromLevel();
        }
    }

    private static ArrayList<Layer> readGraphicAnnotation(View2d view, PresentationStateReader reader,
        DicomImageElement img) {
        ArrayList<Layer> layers = null;
        Attributes dcmobj = reader.getDcmobj();
        if (dcmobj != null) {
            // GraphicAnnotationModule[] gams = GraphicAnnotationModule.toGraphicAnnotationModules(dcmItems);
            // Map<String, GraphicLayerModule> glms = GraphicLayerModule.toGraphicLayerMap(dcmItems);

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
                int rotation = (Integer) reader.getTagValue(ActionW.ROTATION.cmd(), 0);
                boolean flip = (Boolean) reader.getTagValue(ActionW.FLIP.cmd(), false);
                Rectangle area = (Rectangle) reader.getTagValue(ActionW.CROP.cmd(), null);
                Rectangle2D modelArea = view.getViewModel().getModelArea();
                double width = area == null ? modelArea.getWidth() : area.getWidth();
                double height = area == null ? modelArea.getHeight() : area.getHeight();
                double offsetx = area == null ? 0.0 : area.getX() / area.getWidth();
                double offsety = area == null ? 0.0 : area.getY() / area.getHeight();
                AffineTransform inverse = null;
                if (rotation != 0 || flip) {
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

                layers = new ArrayList<>(gams.size());
                for (Attributes gram : gams) {
                    // TODO filter sop
                    Sequence refImgs = gram.getSequence(Tag.ReferencedImageSequence);
                    String graphicLayerName = gram.getString(Tag.GraphicLayer);
                    Attributes glm = glms.get(graphicLayerName);
                    if (glm == null) {
                        continue;
                    }
                    Identifier layerId = new Identifier(graphicLayerName, 310 + glm.getInt(Tag.GraphicLayerOrder, 0),
                        graphicLayerName + " [DICOM]", true); //$NON-NLS-1$
                    DragLayer layer = new DragLayer(view.getLayerModel(), layerId);
                    // TODO should be an option
                    layer.setLocked(true);
                    layers.add(layer);
                    Color rgb = PresentationStateReader.getRGBColor(
                        glm.getInt(Tag.GraphicLayerRecommendedDisplayGrayscaleValue, 255),
                        DicomMediaUtils.getFloatArrayFromDicomElement(glm,
                            Tag.GraphicLayerRecommendedDisplayCIELabValue, null),
                        DicomMediaUtils.getIntAyrrayFromDicomElement(glm, Tag.GraphicLayerRecommendedDisplayRGBValue,
                            null));

                    Sequence gos = gram.getSequence(Tag.GraphicObjectSequence);

                    if (gos != null) {
                        for (Attributes go : gos) {
                            Graphic graphic;
                            try {
                                graphic =
                                    GraphicUtil.buildGraphicFromPR(go, rgb, false, width, height, true, inverse, false);
                                if (graphic != null) {
                                    layer.addGraphic(graphic);
                                }
                            } catch (InvalidShapeException e) {
                                LOGGER.error("Cannot create graphic: {}", e.getMessage()); //$NON-NLS-1$
                            }
                        }
                    }

                    Sequence txos = gram.getSequence(Tag.TextObjectSequence);
                    if (txos != null) {
                        for (Attributes txo : txos) {
                            String[] lines = EscapeChars.convertToLines(txo.getString(Tag.UnformattedTextValue));
                            // MATRIX not implemented
                            boolean isDisp = "DISPLAY".equalsIgnoreCase(txo.getString(Tag.BoundingBoxAnnotationUnits)); //$NON-NLS-1$
                            float[] topLeft = txo.getFloats(Tag.BoundingBoxTopLeftHandCorner);
                            float[] bottomRight = txo.getFloats(Tag.BoundingBoxBottomRightHandCorner);
                            Rectangle2D rect = null;
                            if (topLeft != null && bottomRight != null) {
                                rect = new Rectangle2D.Double(topLeft[0], topLeft[1], bottomRight[0] - topLeft[0],
                                    bottomRight[1] - topLeft[1]);
                                if (isDisp) {
                                    rect.setFrame(rect.getX() * width, rect.getY() * height, rect.getWidth() * width,
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
                                Double ptAnchor = new Point2D.Double(x, y);
                                /*
                                 * Use the center of the box. Do not follow DICOM specs: displaying the bounding box
                                 * even the text doesn't match. Does not make sense!
                                 */
                                Double ptBox =
                                    rect == null ? ptAnchor : new Point2D.Double(rect.getCenterX(), rect.getCenterY());
                                AnnotationGraphic line;
                                try {
                                    line = new AnnotationGraphic(
                                        GraphicUtil.getBooleanValue(txo, Tag.AnchorPointVisibility) ? ptAnchor : null,
                                        ptBox, 1.0f, rgb, true);
                                    line.setLabel(lines, view);
                                    layer.addGraphic(line);
                                } catch (InvalidShapeException e) {
                                    LOGGER.error("Cannot create annotation: {}", e.getMessage()); //$NON-NLS-1$
                                }
                            } else if (rect != null) {
                                AnnotationGraphic g;
                                try {
                                    g = new AnnotationGraphic(null,
                                        new Point2D.Double(rect.getCenterX(), rect.getCenterY()), 1.0f, rgb, true);
                                    g.setLabel(lines, view);
                                    layer.addGraphic(g);
                                } catch (InvalidShapeException e) {
                                    LOGGER.error("Cannot create annotation: {}", e.getMessage()); //$NON-NLS-1$
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

    public static void deleteDicomLayers(ArrayList<AbstractLayer.Identifier> layerIDs, LayerModel layerModel) {
        if (layerIDs != null && layerModel != null) {
            EventManager eventManager = EventManager.getInstance();
            SeriesViewerEvent event =
                new SeriesViewerEvent(eventManager.getSelectedView2dContainer(), null, null, EVENT.REMOVE_LAYER);
            for (Identifier id : layerIDs) {
                AbstractLayer layer = layerModel.getLayer(id);
                if (layer != null) {
                    layerModel.removeLayer(layer);
                    event.setShareObject(layer.getIdentifier());
                    eventManager.fireSeriesViewerListeners(event);
                }
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
            if (prList != null && prList.size() > 0) {
                if (prList.size() > 0) {

                    Object oldPR = view.getActionValue(ActionW.PR_STATE.cmd());
                    if (!ActionState.NONE_SERIES.equals(oldPR)) {
                        int index = prList.indexOf(oldPR);
                        index = index == -1 ? 0 : index;
                        // Set the previous selected value, otherwise set the more recent PR by default
                        view.setPresentationState(prList.get(index));
                    }

                    int offset = series.size(null) > 1 ? 2 : 1;
                    final Object[] items = new Object[prList.size() + offset];
                    items[0] = ActionState.NONE;
                    if (offset == 2) {
                        items[1] = ActionState.NONE_SERIES;
                    }
                    for (int i = offset; i < items.length; i++) {
                        items[i] = prList.get(i - offset);
                    }
                    ViewButton prButton = new ViewButton(new ShowPopup() {

                        @Override
                        public void showPopup(Component invoker, int x, int y) {
                            Object pr = view.getActionValue(ActionW.PR_STATE.cmd());
                            if (pr == null) {
                                pr = ActionState.NONE;
                            }
                            pr = (pr instanceof PresentationStateReader) ? ((PresentationStateReader) pr).getDicom()
                                : pr;
                            JPopupMenu popupMenu = new JPopupMenu();
                            TitleMenuItem itemTitle =
                                new TitleMenuItem(ActionW.PR_STATE.getTitle(), popupMenu.getInsets());
                            popupMenu.add(itemTitle);
                            popupMenu.addSeparator();
                            ButtonGroup groupButtons = new ButtonGroup();

                            for (Object dcm : items) {
                                final RadioMenuItem menuItem = new RadioMenuItem(dcm.toString(), null, dcm, dcm == pr);
                                menuItem.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        if (e.getSource() instanceof RadioMenuItem) {
                                            RadioMenuItem item = (RadioMenuItem) e.getSource();
                                            Object val = item.getUserObject();
                                            view.setPresentationState(val);
                                        }
                                    }
                                });
                                groupButtons.add(menuItem);
                                popupMenu.add(menuItem);
                            }
                            popupMenu.show(invoker, x, y);
                        }
                    }, View2d.PR_ICON);

                    prButton.setVisible(true);
                    return prButton;
                }
            }
        }
        return null;
    }
}
