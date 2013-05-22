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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.JPopupMenu;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.iod.module.pr.GraphicAnnotationModule;
import org.dcm4che2.iod.module.pr.GraphicLayerModule;
import org.dcm4che2.iod.module.pr.GraphicObject;
import org.dcm4che2.iod.module.pr.TextObject;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
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
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.explorer.DicomModel;

public class PRManager {

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
        DicomObject dcmobj = reader.getDcmobj();
        if (dcmobj != null) {
            GraphicAnnotationModule[] gams = GraphicAnnotationModule.toGraphicAnnotationModules(dcmobj);
            Map<String, GraphicLayerModule> glms = GraphicLayerModule.toGraphicLayerMap(dcmobj);
            if (gams != null && glms != null) {
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

                layers = new ArrayList<Layer>(gams.length);
                for (GraphicAnnotationModule gram : gams) {
                    // TODO filter sop
                    gram.getImageSOPInstanceReferences();
                    String graphicLayerName = gram.getGraphicLayer();
                    GraphicLayerModule glm = glms.get(graphicLayerName);
                    if (glm == null) {
                        continue;
                    }
                    Identifier layerId =
                        new Identifier(310 + glm.getGraphicLayerOrder(), glm.getGraphicLayer() + " [DICOM]");
                    DragLayer layer = new DragLayer(view.getLayerModel(), layerId);
                    // layer.setLocked(true);
                    layers.add(layer);
                    Color rgb =
                        PresentationStateReader.getRGBColor(glm.getGraphicLayerRecommendedDisplayGrayscaleValue(),
                            glm.getFloatLab(), glm.getGraphicLayerRecommendedDisplayRGBValue());

                    GraphicObject[] gos = gram.getGraphicObjects();
                    if (gos != null) {
                        for (GraphicObject go : gos) {
                            Graphic graphic;
                            try {
                                graphic = buildGraphicFromPR(go, rgb, false, width, height, true, inverse, false);
                                if (graphic != null) {
                                    layer.addGraphic(graphic);
                                }
                            } catch (InvalidShapeException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                    }

                    TextObject[] txos = gram.getTextObjects();
                    if (txos != null) {
                        for (TextObject txo : txos) {
                            String[] lines = EscapeChars.convertToLines(txo.getUnformattedTextValue());
                            // MATRIX not implemented
                            boolean isDisp = "DISPLAY".equalsIgnoreCase(txo.getBoundingBoxAnnotationUnits());
                            float[] topLeft = txo.getBoundingBoxTopLeftHandCorner();
                            float[] bottomRight = txo.getBoundingBoxBottomRightHandCorner();
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

                            float[] anchor = txo.getAnchorPoint();
                            if (anchor != null && anchor.length == 2) {
                                // MATRIX not implemented
                                boolean disp = "DISPLAY".equalsIgnoreCase(txo.getAnchorPointAnnotationUnits()); //$NON-NLS-1$
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
                                        new AnnotationGraphic(txo.getAnchorPointVisibility() ? ptAnchor : null, ptBox,
                                            1.0f, rgb, true);
                                    line.setLabel(lines, view);
                                    layer.addGraphic(line);
                                } catch (InvalidShapeException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
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
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                            }
                        }

                    }
                }
            }

        }
        return layers;
    }

    public static Graphic buildGraphicFromPR(GraphicObject go, Color color, boolean labelVisible, double width,
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
        boolean isDisp = dcmSR ? false : "DISPLAY".equalsIgnoreCase(go.getGraphicAnnotationUnits());

        String type = go.getGraphicType();
        Graphic shape = null;
        float[] points = go.getGraphicData();
        if (isDisp && inverse != null) {
            float[] dstpoints = new float[points.length];
            inverse.transform(points, 0, dstpoints, 0, points.length / 2);
            points = dstpoints;
        }
        if (GraphicObject.POLYLINE.equalsIgnoreCase(type)) {
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
                                new PolygonGraphic(handlePointList, color, 1.0f, labelVisible, go.getGraphicFilled());
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
                        shape = new NonEditableGraphic(path, 1.0f, color, labelVisible, go.getGraphicFilled());
                    }
                }
            }
        } else if (GraphicObject.ELLIPSE.equalsIgnoreCase(type)) {
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
                            go.getGraphicFilled());
                } else {
                    shape = new NonEditableGraphic(ellipse, 1.0f, color, labelVisible, go.getGraphicFilled());
                }
            }
        } else if (GraphicObject.CIRCLE.equalsIgnoreCase(type)) {
            if (points != null && points.length == 4) {
                double x = isDisp ? points[0] * width : points[0];
                double y = isDisp ? points[1] * height : points[1];
                Ellipse2D ellipse = new Ellipse2D.Double();
                double dist = euclideanDistance(points, 0, 2, isDisp, width, height);
                ellipse.setFrameFromCenter(x, y, x + dist, y + dist);
                if (canBeEdited) {
                    shape = new EllipseGraphic(ellipse.getFrame(), 1.0f, color, labelVisible, go.getGraphicFilled());
                } else {
                    shape = new NonEditableGraphic(ellipse, 1.0f, color, labelVisible, go.getGraphicFilled());
                }
            }
        } else if (GraphicObject.POINT.equalsIgnoreCase(type)) {
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
        } else if (GraphicObject.INTERPOLATED.equalsIgnoreCase(type)) {
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
                        shape = new NonEditableGraphic(path, 1.0f, color, labelVisible, go.getGraphicFilled());
                    }
                }
            }
        }
        return shape;
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
            // TODO duplicate KO in model if multiple studies references
            List<DicomSpecialElement> list = DicomModel.getPrSpecialElements(series);
            if (list != null && list.size() > 0) {

                Object key = img.getKey();

                List<DicomSpecialElement> prList =
                    DicomSpecialElement.getPRfromSopUID((String) series.getTagValue(TagW.SeriesInstanceUID),
                        (String) img.getTagValue(TagW.SOPInstanceUID), key instanceof Integer ? (Integer) key + 1
                            : null, list);
                if (prList.size() > 0) {
                    SeriesComparator<DicomSpecialElement> time = new SeriesComparator<DicomSpecialElement>() {

                        @Override
                        public int compare(DicomSpecialElement m1, DicomSpecialElement m2) {
                            Date date1 = (Date) m1.getTagValue(TagW.SeriesDate);
                            Date date2 = (Date) m2.getTagValue(TagW.SeriesDate);
                            if (date1 != null && date2 != null) {
                                // inverse time
                                return date2.compareTo(date1);
                            }

                            Integer val1 = (Integer) m1.getTagValue(TagW.SeriesNumber);
                            Integer val2 = (Integer) m2.getTagValue(TagW.SeriesNumber);
                            if (val1 == null || val2 == null) {
                                return 0;
                            }
                            // inverse number
                            return val1 > val2 ? -1 : (val1 == val2 ? 0 : 1);
                        }
                    };

                    Collections.sort(prList, time);

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
                                            view.setPresentationState(item.getUserObject());
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
