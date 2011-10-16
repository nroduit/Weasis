package org.weasis.dicom.viewer2d;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Map;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.iod.module.pr.GraphicAnnotationModule;
import org.dcm4che2.iod.module.pr.GraphicLayerModule;
import org.dcm4che2.iod.module.pr.GraphicObject;
import org.dcm4che2.iod.module.pr.TextObject;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.ui.graphic.AnnotationGraphic;
import org.weasis.core.ui.graphic.DragLayer;
import org.weasis.core.ui.graphic.model.Layer;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PresentationStateReader;

public class PRManager {
    // private PresentationStateReader prReader;
    // private DicomImageElement image;
    //
    // private PRManager(PresentationStateReader reader, DicomImageElement img) {
    // if (reader == null || img == null) {
    // throw new IllegalArgumentException("Arguments cannot be null");
    // }
    // this.image = img;
    // this.prReader = reader;
    // }

    public static void applyPresentationState(View2d view, PresentationStateReader reader, DicomImageElement img) {
        if (view == null || reader == null || img == null) {
            return;
        }
        reader.readSpatialTransformationModule();
        reader.readDisplayArea(img);
        readGraphicAnnotation(view, reader, img);
    }

    private static ArrayList<Layer> readGraphicAnnotation(View2d view, PresentationStateReader reader,
        DicomImageElement img) {
        ArrayList<Layer> layers = null;
        DicomObject dcmobj = reader.getDcmobj();
        if (dcmobj != null) {
            GraphicAnnotationModule[] gams = GraphicAnnotationModule.toGraphicAnnotationModules(dcmobj);
            Map<String, GraphicLayerModule> glms = GraphicLayerModule.toGraphicLayerMap(dcmobj);
            if (gams != null && glms != null) {
                int rotation = (Integer) reader.getTagValue(ActionW.ROTATION.cmd(), 0);
                boolean flip = (Boolean) reader.getTagValue(ActionW.FLIP.cmd(), false);
                Rectangle area = (Rectangle) reader.getTagValue(ActionW.CROP.cmd(), null);
                layers = new ArrayList<Layer>(gams.length);
                for (GraphicAnnotationModule gram : gams) {
                    String graphicLayerName = gram.getGraphicLayer();
                    GraphicLayerModule glm = glms.get(graphicLayerName);
                    if (glm == null) {
                        continue;
                    }

                    DragLayer layer = new DragLayer(view.getLayerModel(), 1300 + glm.getGraphicLayerOrder());
                    view.getLayerModel().addLayer(layer);
                    Color rgb =
                        PresentationStateReader.toRGB(glm.getGraphicLayerRecommendedDisplayGrayscaleValue(),
                            glm.getFloatLab(), glm.getGraphicLayerRecommendedDisplayRGBValue());

                    GraphicObject[] gos = gram.getGraphicObjects();
                    if (gos != null) {
                        for (GraphicObject go : gos) {
                            boolean isDisp = ("DISPLAY".equalsIgnoreCase(go.getGraphicAnnotationUnits()));

                        }
                    }

                    TextObject[] txos = gram.getTextObjects();
                    if (txos != null) {
                        for (TextObject txo : txos) {
                            String unformatted = txo.getUnformattedTextValue();
                            String[] lines = PresentationStateReader.convertToLines(unformatted);

                            float[] anchor = txo.getAnchorPoint();
                            if (anchor != null) {
                                if ("DISPLAY".equalsIgnoreCase(txo.getAnchorPointAnnotationUnits())) {

                                }
                            } else {
                                float[] topLeft = txo.getBoundingBoxTopLeftHandCorner();
                                float[] bottomRight = txo.getBoundingBoxBottomRightHandCorner();
                                if (topLeft != null && bottomRight != null) {
                                    Rectangle2D rect =
                                        new Rectangle2D.Double(topLeft[0], topLeft[1], bottomRight[0] - topLeft[0],
                                            bottomRight[1] - topLeft[1]);
                                    Rectangle2D modelArea = view.getViewModel().getModelArea();
                                    if ("DISPLAY".equalsIgnoreCase(txo.getBoundingBoxAnnotationUnits())) {
                                        double width = area == null ? modelArea.getWidth() : area.getWidth();
                                        double height = area == null ? modelArea.getHeight() : area.getHeight();
                                        if (rotation != 0 || flip) {
                                            // Create inverse transformation
                                            AffineTransform inverse = AffineTransform.getTranslateInstance(0, 0);
                                            if (flip) {
                                                inverse.scale(-1.0, 1.0);
                                                inverse.translate(-1.0, 0.0);
                                            }
                                            if (rotation != 0) {
                                                inverse.rotate(Math.toRadians(rotation), 0.5, 0.5);
                                            }

                                            float[] dstPt1 = new float[2];
                                            float[] dstPt2 = new float[2];
                                            inverse.transform(topLeft, 0, dstPt1, 0, 1);
                                            inverse.transform(bottomRight, 0, dstPt2, 0, 1);
                                            rect.setFrameFromDiagonal(dstPt1[0] * width, dstPt1[1] * height, dstPt2[0]
                                                * width, dstPt2[1] * height);
                                        } else {
                                            rect.setFrame(rect.getX() * width, rect.getY() * height, rect.getWidth()
                                                * width, rect.getHeight() * height);
                                        }

                                    } else {
                                        if ("MATRIX".equalsIgnoreCase(txo.getBoundingBoxAnnotationUnits())) {
                                            // TODO implement ?
                                        }
                                        // PIXEL relative
                                        if (area != null) {
                                            rect.setFrameFromDiagonal(topLeft[0] - area.getX(),
                                                topLeft[1] - area.getY(), bottomRight[0] - area.getX(), bottomRight[1]
                                                    - area.getY());
                                        }
                                    }
                                    AnnotationGraphic g = new AnnotationGraphic(null, rect, lines, 1, rgb, false);
                                    layer.addGraphic(g);
                                    // g.setLabel(lines, view, new Point2D.Double(rect.getX(), rect.getY()));
                                }
                            }
                        }
                    }
                }

            }

        }
        return layers;
    }
}
