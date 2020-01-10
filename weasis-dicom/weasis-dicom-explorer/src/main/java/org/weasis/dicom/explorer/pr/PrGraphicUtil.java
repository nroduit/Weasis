/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer.pr;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.CIELab;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.NonEditableGraphic;
import org.weasis.core.ui.model.graphic.imp.PointGraphic;
import org.weasis.core.ui.model.graphic.imp.area.EllipseGraphic;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class PrGraphicUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrGraphicUtil.class);

    public static final String POINT = "POINT"; //$NON-NLS-1$
    public static final String POLYLINE = "POLYLINE"; //$NON-NLS-1$
    public static final String INTERPOLATED = "INTERPOLATED"; //$NON-NLS-1$
    public static final String CIRCLE = "CIRCLE"; //$NON-NLS-1$
    public static final String ELLIPSE = "ELLIPSE"; //$NON-NLS-1$

    private PrGraphicUtil() {
    }

    public static Graphic buildGraphic(Attributes go, Color color, boolean labelVisible, double width, double height,
        boolean canBeEdited, AffineTransform inverse, boolean dcmSR) throws InvalidShapeException {
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
        boolean isDisp = dcmSR ? false : "DISPLAY".equalsIgnoreCase(go.getString(Tag.GraphicAnnotationUnits)); //$NON-NLS-1$

        String type = go.getString(Tag.GraphicType);
        Integer groupID = DicomMediaUtils.getIntegerFromDicomElement(go, Tag.GraphicGroupID, null);
        boolean filled = getBooleanValue(go, Tag.GraphicFilled);
        Attributes style = go.getNestedDataset(Tag.LineStyleSequence);
        Float thickness = DicomMediaUtils.getFloatFromDicomElement(style, Tag.LineThickness, 1.0f);
        Boolean dashed = style == null ? Boolean.FALSE : "DASHED".equalsIgnoreCase(style.getString(Tag.LinePattern)); //$NON-NLS-1$
        if (style != null) {
            float[] rgb = CIELab.convertToFloatLab(style.getInts(Tag.PatternOnColorCIELabValue));
            if (rgb != null) {
                color = PresentationStateReader.getRGBColor(255, rgb, (int[]) null);
            }
        }

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
                        List<Point2D.Double> handlePointList = new ArrayList<>(size);
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
                            shape = new PolygonGraphic().buildGraphic(handlePointList);
                            setProperties(shape, thickness, color, labelVisible, filled, groupID);
                        } else {
                            shape = new PolylineGraphic().buildGraphic(handlePointList);
                            setProperties(shape, thickness, color, labelVisible, Boolean.FALSE, groupID);
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
                        shape = new NonEditableGraphic(path);
                        setProperties(shape, thickness, color, labelVisible, filled, groupID);
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
                if (MathUtil.isEqual(majorX1, majorX2)) {
                    rotation = Math.PI / 2;
                } else if (MathUtil.isEqual(majorY1, majorY2)) {
                    rotation = 0;
                } else {
                    rotation = Math.atan2(majorY2 - cy, majorX2 - cx);
                }
                Shape ellipse = new Ellipse2D.Double();
                ((Ellipse2D) ellipse).setFrameFromCenter(cx, cy, cx + rx, cy + ry);
                if (MathUtil.isDifferentFromZero(rotation)) {
                    AffineTransform rotate = AffineTransform.getRotateInstance(rotation, cx, cy);
                    ellipse = rotate.createTransformedShape(ellipse);
                }
                // Only ellipse without rotation can be edited
                if (canBeEdited && Objects.equals(rotation, 0)) {
                    shape = new EllipseGraphic().buildGraphic(((Ellipse2D) ellipse).getFrame());
                    setProperties(shape, thickness, color, labelVisible, filled, groupID);
                } else {
                    shape = new NonEditableGraphic(ellipse);
                    setProperties(shape, thickness, color, labelVisible, filled, groupID);
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
                    shape = new EllipseGraphic().buildGraphic(ellipse.getFrame());
                    setProperties(shape, thickness, color, labelVisible, filled, groupID);
                } else {
                    shape = new NonEditableGraphic(ellipse);
                    setProperties(shape, thickness, color, labelVisible, filled, groupID);
                }
            }
        } else if (POINT.equalsIgnoreCase(type)) {
            if (points != null && points.length == 2) {
                double x = isDisp ? points[0] * width : points[0];
                double y = isDisp ? points[1] * height : points[1];
                int pointSize = 3;

                if (canBeEdited) {
                    shape = new PointGraphic().buildGraphic(Arrays.asList(new Point2D.Double(x, y)));
                    ((PointGraphic) shape).setPointSize(pointSize);
                    setProperties(shape, thickness, color, labelVisible, Boolean.TRUE, groupID);
                } else {
                    Ellipse2D ellipse =
                        new Ellipse2D.Double(x - pointSize / 2.0f, y - pointSize / 2.0f, pointSize, pointSize);
                    shape = new NonEditableGraphic(ellipse);
                    setProperties(shape, thickness, color, labelVisible, Boolean.TRUE, groupID);
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
                shape = new NonEditableGraphic(path);
                setProperties(shape, thickness, color, labelVisible, Boolean.TRUE, groupID);
            }
        } else if (INTERPOLATED.equalsIgnoreCase(type)) {
            if (points != null && points.length >= 2) {
                // Only non editable graphic (required control point tool)
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
                    shape = new NonEditableGraphic(path);
                    setProperties(shape, thickness, color, labelVisible, filled, groupID);
                }
            }
        }
        return shape;
    }

    public static boolean getBooleanValue(Attributes dcmobj, int tag) {
        return "Y".equalsIgnoreCase(dcmobj.getString(tag)); //$NON-NLS-1$
    }

    private static void setProperties(Graphic shape, Float thickness, Color color, Boolean labelVisible, Boolean filled,
        Integer classID) {
        shape.setLineThickness(thickness);
        shape.setPaint(color);
        shape.setLabelVisible(labelVisible);
        shape.setClassID(classID);
        shape.setFilled(filled);
    }

    private static double euclideanDistance(float[] points, int p1, int p2, boolean isDisp, double width,
        double height) {
        double dx = points[p1] - points[p2];
        double dy = points[p1 + 1] - points[p2 + 1];
        if (isDisp) {
            dx *= width;
            dy *= height;
        }
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static Graphic buildCompoundGraphic(Attributes go, Color color, boolean labelVisible, double width,
        double height, AffineTransform inverse) throws InvalidShapeException {
        /*
         *
         * Graphic Type: MULTILINE, INFINITELINE, CUTLINE, RANGELINE, RULER, AXIS, CROSSHAIR, ARROW, RECTANGLE and
         * ELLIPSE
         *
         * Coordinates are DISPLAY or PIXEL
         */
        boolean isDisp = "DISPLAY".equalsIgnoreCase(go.getString(Tag.CompoundGraphicUnits)); //$NON-NLS-1$

        String type = go.getString(Tag.CompoundGraphicType);
        String id = go.getString(Tag.CompoundGraphicInstanceID);
        Integer groupID = DicomMediaUtils.getIntegerFromDicomElement(go, Tag.GraphicGroupID, null);
        boolean filled = getBooleanValue(go, Tag.GraphicFilled);
        Attributes style = go.getNestedDataset(Tag.LineStyleSequence);
        Float thickness = DicomMediaUtils.getFloatFromDicomElement(style, Tag.LineThickness, 1.0f);
        Boolean dashed = style == null ? Boolean.FALSE : "DASHED".equalsIgnoreCase(style.getString(Tag.LinePattern)); //$NON-NLS-1$
        if (style != null) {
            float[] rgb = CIELab.convertToFloatLab(style.getInts(Tag.PatternOnColorCIELabValue));
            if (rgb != null) {
                color = PresentationStateReader.getRGBColor(255, rgb, (int[]) null);
            }
        }

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
                    Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, size);
                    double x = isDisp ? points[0] * width : points[0];
                    double y = isDisp ? points[1] * height : points[1];
                    path.moveTo(x, y);
                    for (int i = 1; i < size; i++) {
                        x = isDisp ? points[i * 2] * width : points[i * 2];
                        y = isDisp ? points[i * 2 + 1] * height : points[i * 2 + 1];
                        path.lineTo(x, y);
                    }
                    shape = new NonEditableGraphic(path);
                    setProperties(shape, thickness, color, labelVisible, filled, groupID);
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
                if (MathUtil.isEqual(majorX1, majorX2)) {
                    rotation = Math.PI / 2;
                } else if (MathUtil.isEqual(majorY1, majorY2)) {
                    rotation = 0;
                } else {
                    rotation = Math.atan2(majorY2 - cy, majorX2 - cx);
                }
                Shape ellipse = new Ellipse2D.Double();
                ((Ellipse2D) ellipse).setFrameFromCenter(cx, cy, cx + rx, cy + ry);
                if (MathUtil.isDifferentFromZero(rotation)) {
                    AffineTransform rotate = AffineTransform.getRotateInstance(rotation, cx, cy);
                    ellipse = rotate.createTransformedShape(ellipse);
                }
                shape = new NonEditableGraphic(ellipse);
                setProperties(shape, thickness, color, labelVisible, filled, groupID);
            }
        } else if (POINT.equalsIgnoreCase(type)) {
            if (points != null && points.length == 2) {
                double x = isDisp ? points[0] * width : points[0];
                double y = isDisp ? points[1] * height : points[1];
                int pointSize = 3;

                Ellipse2D ellipse =
                    new Ellipse2D.Double(x - pointSize / 2.0f, y - pointSize / 2.0f, pointSize, pointSize);
                shape = new NonEditableGraphic(ellipse);
                setProperties(shape, thickness, color, labelVisible, Boolean.TRUE, groupID);
            }
        }
        return shape;
    }

    public static GraphicModel getPresentationModel(Attributes dcmobj) {
        if (dcmobj != null) {
            String id = dcmobj.getString(PresentationStateReader.PRIVATE_CREATOR_TAG);
            if (PresentationStateReader.PR_MODEL_ID.equals(id)) {
                try {
                    return XmlSerializer
                        .buildPresentationModel(dcmobj.getBytes(PresentationStateReader.PR_MODEL_PRIVATE_TAG));
                } catch (Exception e) {
                    LOGGER.error("Cannot extract binary model: ", e); //$NON-NLS-1$
                }
            }
        }
        return null;
    }

    public static boolean applyPresentationModel(ImageElement img) {
        if (img != null) {
            byte[] prBinary = TagW.getTagValue(img, TagW.PresentationModelBirary, byte[].class);
            if (prBinary != null) {
                GraphicModel model = XmlSerializer.buildPresentationModel(prBinary);
                img.setTag(TagW.PresentationModel, model);
                img.setTag(TagW.PresentationModelBirary, null);
                return true;
            }
        }
        return false;
    }
}
