package org.weasis.dicom.viewer2d;

import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.JPopupMenu;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.EscapeChars;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.ShowPopup;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.graphic.AnnotationGraphic;
import org.weasis.core.ui.graphic.DragLayer;
import org.weasis.core.ui.graphic.EllipseGraphic;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.InvalidShapeException;
import org.weasis.core.ui.graphic.NonEditableGraphic;
import org.weasis.core.ui.graphic.PointGraphic;
import org.weasis.core.ui.graphic.PolygonGraphic;
import org.weasis.core.ui.graphic.PolylineGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayer.Identifier;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.Layer;
import org.weasis.core.ui.graphic.model.LayerModel;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;

public class PRManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PRManager.class);

    public static final String POINT = "POINT";
    public static final String POLYLINE = "POLYLINE";
    public static final String INTERPOLATED = "INTERPOLATED";
    public static final String CIRCLE = "CIRCLE";
    public static final String ELLIPSE = "ELLIPSE";

    // private PresentationStateReader prReader;
    // private DicomImageElement image;
    //
    // private PRManager(PresentationStateReader reader, DicomImageElement img) {
    // if (reader == null || img == null) {
    // throw new IllegalArgumentException("Arguments cannot be null");
    // }throws IllegalStateException
    // this.image = img;
    // this.prReader = reader;
    // }

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
            ArrayList<AbstractLayer.Identifier> list = new ArrayList<AbstractLayer.Identifier>();
            for (Layer layer : layers) {
                list.add(layer.getIdentifier());
                layerModel.addLayer((AbstractLayer) layer);
                event.setShareObject(layer.getIdentifier());
                eventManager.fireSeriesViewerListeners(event);
            }
            view.setActionsInView(PresentationStateReader.TAG_DICOM_LAYERS, list);
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
                Map<String, Attributes> glms = new HashMap<String, Attributes>(layerSeqs.size());
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
                AffineTransform inverse = null;
                if (rotation != 0 || flip) {
                    // Create inverse transformation
                    inverse = AffineTransform.getTranslateInstance(0, 0);
                    if (flip) {
                        inverse.scale(-1.0, 1.0);
                        inverse.translate(-1.0, 0.0);
                    }
                    if (rotation != 0) {
                        inverse.rotate(Math.toRadians(rotation), 0.5, 0.5);
                    }
                }

                layers = new ArrayList<Layer>(gams.size());
                for (Attributes gram : gams) {
                    // TODO filter sop
                    Sequence refImgs = gram.getSequence(Tag.ReferencedImageSequence);
                    String graphicLayerName = gram.getString(Tag.GraphicLayer);
                    Attributes glm = glms.get(graphicLayerName);
                    if (glm == null) {
                        continue;
                    }
                    Identifier layerId =
                        new Identifier(310 + glm.getInt(Tag.GraphicLayerOrder, 0), graphicLayerName + " [DICOM]");
                    DragLayer layer = new DragLayer(view.getLayerModel(), layerId);
                    // TODO should be an option
                    layer.setLocked(true);
                    layers.add(layer);
                    Color rgb =
                        PresentationStateReader.getRGBColor(glm.getInt(
                            Tag.GraphicLayerRecommendedDisplayGrayscaleValue, 255), DicomMediaUtils
                            .getFloatArrayFromDicomElement(glm, Tag.GraphicLayerRecommendedDisplayCIELabValue, null),
                            DicomMediaUtils.getIntAyrrayFromDicomElement(glm,
                                Tag.GraphicLayerRecommendedDisplayRGBValue, null));

                    Sequence gos = gram.getSequence(Tag.GraphicObjectSequence);

                    if (gos != null) {
                        for (Attributes go : gos) {
                            Graphic graphic;
                            try {
                                graphic = buildGraphicFromPR(go, rgb, false, width, height, true, inverse, false);
                                if (graphic != null) {
                                    layer.addGraphic(graphic);
                                }
                            } catch (InvalidShapeException e) {
                                LOGGER.error("Cannot create graphic: {}", e.getMessage());
                            }
                        }
                    }

                    Sequence txos = gram.getSequence(Tag.TextObjectSequence);
                    if (txos != null) {
                        for (Attributes txo : txos) {
                            String[] lines = EscapeChars.convertToLines(txo.getString(Tag.UnformattedTextValue));
                            // MATRIX not implemented
                            boolean isDisp = "DISPLAY".equalsIgnoreCase(txo.getString(Tag.BoundingBoxAnnotationUnits));
                            float[] topLeft = txo.getFloats(Tag.BoundingBoxTopLeftHandCorner);
                            float[] bottomRight = txo.getFloats(Tag.BoundingBoxBottomRightHandCorner);
                            Rectangle2D rect = null;
                            if (topLeft != null && bottomRight != null) {
                                rect =
                                    new Rectangle2D.Double(topLeft[0], topLeft[1], bottomRight[0] - topLeft[0],
                                        bottomRight[1] - topLeft[1]);
                                if (isDisp) {
                                    rect.setFrame(rect.getX() * width, rect.getY() * height, rect.getWidth() * width,
                                        rect.getHeight() * height);
                                    if (inverse != null) {
                                        float[] dstPt1 = new float[2];
                                        float[] dstPt2 = new float[2];
                                        inverse.transform(topLeft, 0, dstPt1, 0, 1);
                                        inverse.transform(bottomRight, 0, dstPt2, 0, 1);
                                        rect.setFrameFromDiagonal(dstPt1[0] * width, dstPt1[1] * height, dstPt2[0]
                                            * width, dstPt2[1] * height);
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
                                    line =
                                        new AnnotationGraphic(getBooleanValue(txo, Tag.AnchorPointVisibility)
                                            ? ptAnchor : null, ptBox, 1.0f, rgb, true);
                                    line.setLabel(lines, view);
                                    layer.addGraphic(line);
                                } catch (InvalidShapeException e) {
                                    LOGGER.error("Cannot create annotation: {}", e.getMessage());
                                }
                            } else if (rect != null) {
                                AnnotationGraphic g;
                                try {
                                    g =
                                        new AnnotationGraphic(null, new Point2D.Double(rect.getCenterX(),
                                            rect.getCenterY()), 1.0f, rgb, true);
                                    g.setLabel(lines, view);
                                    layer.addGraphic(g);
                                } catch (InvalidShapeException e) {
                                    LOGGER.error("Cannot create annotation: {}", e.getMessage());
                                }
                            }
                        }

                    }
                }
            }

        }
        return layers;
    }

    public static Graphic buildGraphicFromPR(Attributes go, Color color, boolean labelVisible, double width,
        double height, boolean canBeEdited, AffineTransform inverse, boolean dcmSR) throws InvalidShapeException {
        /*
         * For DICOM SR
         * 
         * Graphic Type: POINT, POLYLINE (always closed), MULTIPOINT, CIRCLE and ELLIPSE
         * 
         * Coordinates are always pixel coordinates
         */

        /*
         * For DICOM PR
         * 
         * Graphic Type: POINT, POLYLINE, INTERPOLATED, CIRCLE and ELLIPSE
         * 
         * MATRIX not implemented
         */
        boolean isDisp = dcmSR ? false : "DISPLAY".equalsIgnoreCase(go.getString(Tag.GraphicAnnotationUnits));

        String type = go.getString(Tag.GraphicType);
        Graphic shape = null;
        float[] points = DicomMediaUtils.getFloatArrayFromDicomElement(go, Tag.GraphicData, null);
        if (isDisp && inverse != null) {
            float[] dstpoints = new float[points.length];
            inverse.transform(points, 0, dstpoints, 0, points.length / 2);
            points = dstpoints;
        }
        if (POLYLINE.equalsIgnoreCase(type)) {
            if (points != null) {
                int size = points.length / 2;
                if (size >= 2) {
                    if (canBeEdited) {
                        List<Point2D.Double> handlePointList = new ArrayList<Point2D.Double>(size);
                        for (int i = 0; i < size; i++) {
                            double x = isDisp ? points[i * 2] * width : points[i * 2];
                            double y = isDisp ? points[i * 2 + 1] * height : points[i * 2 + 1];
                            handlePointList.add(new Point2D.Double(x, y));
                        }
                        if (dcmSR) {
                            // Always close polyline for DICOM SR
                            if (!handlePointList.get(0).equals(handlePointList.get(size - 1))) {
                                handlePointList.add((Point2D.Double) handlePointList.get(0).clone());
                            }
                        }
                        // Closed when the first point is the same as the last point
                        if (handlePointList.get(0).equals(handlePointList.get(size - 1))) {
                            shape =
                                new PolygonGraphic(handlePointList, color, 1.0f, labelVisible, getBooleanValue(go,
                                    Tag.GraphicFilled));
                        } else {
                            shape = new PolylineGraphic(handlePointList, color, 1.0f, labelVisible);
                        }

                    } else {
                        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, size);
                        double x = isDisp ? points[0] * width : points[0];
                        double y = isDisp ? points[1] * height : points[1];
                        path.moveTo(x, y);
                        for (int i = 1; i < size; i++) {
                            x = isDisp ? points[i * 2] * width : points[i * 2];
                            y = isDisp ? points[i * 2 + 1] * height : points[i * 2 + 1];
                            path.lineTo(x, y);
                        }
                        if (dcmSR) {
                            // Always close polyline for DICOM SR
                            path.closePath();
                        }
                        shape =
                            new NonEditableGraphic(path, 1.0f, color, labelVisible, getBooleanValue(go,
                                Tag.GraphicFilled));
                    }
                }
            }
        } else if (ELLIPSE.equalsIgnoreCase(type)) {
            if (points != null && points.length == 8) {
                double majorX1 = isDisp ? points[0] * width : points[0];
                double majorY1 = isDisp ? points[1] * height : points[1];
                double majorX2 = isDisp ? points[2] * width : points[2];
                double majorY2 = isDisp ? points[3] * height : points[3];
                double cx = (majorX1 + majorX2) / 2;
                double cy = (majorY1 + majorY2) / 2;
                double rx = euclideanDistance(points, 0, 2, isDisp, width, height) / 2;
                double ry = euclideanDistance(points, 4, 6, isDisp, width, height) / 2;
                double rotation;
                if (majorX1 == majorX2) {
                    rotation = Math.PI / 2;
                } else if (majorY1 == majorY2) {
                    rotation = 0;
                } else {
                    rotation = Math.atan2(majorY2 - cy, majorX2 - cx);
                }
                Shape ellipse = new Ellipse2D.Double();
                ((Ellipse2D) ellipse).setFrameFromCenter(cx, cy, cx + rx, cy + ry);
                if (rotation != 0) {
                    AffineTransform rotate = AffineTransform.getRotateInstance(rotation, cx, cy);
                    ellipse = rotate.createTransformedShape(ellipse);
                }
                // Only ellipse without rotation can be edited
                if (canBeEdited && rotation == 0) {
                    shape =
                        new EllipseGraphic(((Ellipse2D) ellipse).getFrame(), 1.0f, color, labelVisible,
                            getBooleanValue(go, Tag.GraphicFilled));
                } else {
                    shape =
                        new NonEditableGraphic(ellipse, 1.0f, color, labelVisible, getBooleanValue(go,
                            Tag.GraphicFilled));
                }
            }
        } else if (CIRCLE.equalsIgnoreCase(type)) {
            if (points != null && points.length == 4) {
                double x = isDisp ? points[0] * width : points[0];
                double y = isDisp ? points[1] * height : points[1];
                Ellipse2D ellipse = new Ellipse2D.Double();
                double dist = euclideanDistance(points, 0, 2, isDisp, width, height);
                ellipse.setFrameFromCenter(x, y, x + dist, y + dist);
                if (canBeEdited) {
                    shape =
                        new EllipseGraphic(ellipse.getFrame(), 1.0f, color, labelVisible, getBooleanValue(go,
                            Tag.GraphicFilled));
                } else {
                    shape =
                        new NonEditableGraphic(ellipse, 1.0f, color, labelVisible, getBooleanValue(go,
                            Tag.GraphicFilled));
                }
            }
        } else if (POINT.equalsIgnoreCase(type)) {
            if (points != null && points.length == 2) {
                double x = isDisp ? points[0] * width : points[0];
                double y = isDisp ? points[1] * height : points[1];
                int pointSize = 3;

                if (canBeEdited) {
                    shape = new PointGraphic(new Point2D.Double(x, y), 1.0f, color, labelVisible, true, pointSize);
                } else {
                    Ellipse2D ellipse =
                        new Ellipse2D.Double(x - pointSize / 2.0f, y - pointSize / 2.0f, pointSize, pointSize);
                    shape = new NonEditableGraphic(ellipse, 1.0f, color, labelVisible, true);
                }
            }
        } else if ("MULTIPOINT".equalsIgnoreCase(type)) { //$NON-NLS-1$
            if (points != null && points.length >= 2) {
                int size = points.length / 2;
                int pointSize = 3;
                Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, size);

                for (int i = 0; i < size; i++) {
                    double x = isDisp ? points[i * 2] * width : points[i * 2];
                    double y = isDisp ? points[i * 2 + 1] * height : points[i * 2 + 1];
                    Ellipse2D ellipse =
                        new Ellipse2D.Double(x - pointSize / 2.0f, y - pointSize / 2.0f, pointSize, pointSize);
                    path.append(ellipse, false);
                }
                shape = new NonEditableGraphic(path, 1.0f, color, labelVisible, true);
            }
        } else if (INTERPOLATED.equalsIgnoreCase(type)) {
            if (points != null && points.length >= 2) {
                // Only non editable graphic (required control point tool)
                if (points != null) {
                    int size = points.length / 2;
                    if (size >= 2) {
                        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, size);
                        double lx = isDisp ? points[0] * width : points[0];
                        double ly = isDisp ? points[1] * height : points[1];
                        path.moveTo(lx, ly);
                        for (int i = 1; i < size; i++) {
                            double x = isDisp ? points[i * 2] * width : points[i * 2];
                            double y = isDisp ? points[i * 2 + 1] * height : points[i * 2 + 1];

                            double dx = lx - x;
                            double dy = ly - y;
                            double dist = Math.sqrt(dx * dx + dy * dy);
                            double ux = -dy / dist;
                            double uy = dx / dist;

                            // Use 1/4 distance in the perpendicular direction
                            double cx = (lx + x) * 0.5 + dist * 0.25 * ux;
                            double cy = (ly + y) * 0.5 + dist * 0.25 * uy;

                            path.quadTo(cx, cy, x, y);
                            lx = x;
                            ly = y;
                        }
                        shape =
                            new NonEditableGraphic(path, 1.0f, color, labelVisible, getBooleanValue(go,
                                Tag.GraphicFilled));
                    }
                }
            }
        }
        return shape;
    }

    /** Indicate if the graphic is to be filled in */
    public static boolean getBooleanValue(Attributes dcmobj, int tag) {
        String grFill = dcmobj.getString(tag);
        if (grFill == null) {
            return false;
        }
        return grFill.equalsIgnoreCase("Y");
    }

    private static double euclideanDistance(float[] points, int p1, int p2, boolean isDisp, double width, double height) {
        float dx = points[p1] - points[p2];
        float dy = points[p1 + 1] - points[p2 + 1];
        if (isDisp) {
            dx *= width;
            dy *= height;
        }
        return Math.sqrt(dx * dx + dy * dy);
    }

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
                DicomModel.getPrSpecialElements(series, (String) img.getTagValue(TagW.SOPInstanceUID),
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
                                            view.setPresentationState((PRSpecialElement) (val instanceof PRSpecialElement
                                                ? val : null));
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
