/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pr;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.CIELab;
import org.dcm4che3.img.util.DicomObjectUtil;
import org.dcm4che3.img.util.DicomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.NonEditableGraphic;
import org.weasis.core.ui.model.graphic.imp.PointGraphic;
import org.weasis.core.ui.model.graphic.imp.area.EllipseGraphic;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.core.util.MathUtil;
import org.weasis.dicom.codec.PresentationStateReader;

/**
 * Utility class for handling DICOM Presentation State graphics conversion.
 *
 * <p>This class provides methods to convert DICOM PR graphics to Weasis graphics and vice versa,
 * supporting both editable and non-editable graphics with proper style handling.
 */
public class PrGraphicUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(PrGraphicUtil.class);

  // Graphic type constants
  public static final String POINT = "POINT";
  public static final String POLYLINE = "POLYLINE";
  public static final String INTERPOLATED = "INTERPOLATED";
  public static final String CIRCLE = "CIRCLE";
  public static final String ELLIPSE = "ELLIPSE";
  public static final String RECTANGLE = "RECTANGLE";
  public static final String MULTIPOINT = "MULTIPOINT";
  public static final String MULTILINE = "MULTILINE";
  public static final String INFINITELINE = "INFINITELINE";
  public static final String CUTLINE = "CUTLINE";
  public static final String RANGELINE = "RANGELINE";
  public static final String RULER = "RULER";
  public static final String AXIS = "AXIS";
  public static final String CROSSHAIR = "CROSSHAIR";
  public static final String ARROW = "ARROW";

  // Default values
  private static final int DEFAULT_POINT_SIZE = 3;
  private static final float DEFAULT_LINE_THICKNESS = 1.0f;

  private PrGraphicUtil() {}

  /**
   * Builds a graphic from DICOM attributes.
   *
   * @param attributes DICOM graphic attributes
   * @param defaultColor Default color if none specified
   * @param labelVisible Whether labels should be visible
   * @param width Image width for coordinate transformation
   * @param height Image height for coordinate transformation
   * @param canBeEdited Whether the graphic can be edited
   * @param inverse Inverse transformation matrix
   * @param dcmSR Whether this is for DICOM SR
   * @return Created graphic or null if creation failed
   * @throws InvalidShapeException If the shape cannot be created
   */
  public static Graphic buildGraphic(
      Attributes attributes,
      Color defaultColor,
      boolean labelVisible,
      double width,
      double height,
      boolean canBeEdited,
      AffineTransform inverse,
      boolean dcmSR)
      throws InvalidShapeException {
    if (attributes == null) {
      LOGGER.warn("Cannot build graphic from null attributes");
      return null;
    }

    try {
      GraphicContext context =
          new GraphicContext(
              attributes, defaultColor, labelVisible, width, height, canBeEdited, inverse, dcmSR);
      return buildGraphicByType(context);
    } catch (Exception e) {
      LOGGER.error("Error building graphic: {}", e.getMessage(), e);
      throw new InvalidShapeException("Failed to build graphic", e);
    }
  }

  /**
   * Builds a compound graphic from DICOM attributes.
   *
   * @param attributes DICOM compound graphic attributes
   * @param defaultColor Default color if none specified
   * @param labelVisible Whether labels should be visible
   * @param width Image width for coordinate transformation
   * @param height Image height for coordinate transformation
   * @param inverse Inverse transformation matrix
   * @return Created graphic or null if creation failed
   * @throws InvalidShapeException If the shape cannot be created
   */
  public static Graphic buildCompoundGraphic(
      Attributes attributes,
      Color defaultColor,
      boolean labelVisible,
      double width,
      double height,
      AffineTransform inverse)
      throws InvalidShapeException {

    if (attributes == null) {
      LOGGER.warn("Cannot build compound graphic from null attributes");
      return null;
    }

    try {
      CompoundGraphicContext context =
          new CompoundGraphicContext(
              attributes, defaultColor, labelVisible, width, height, inverse);

      return buildGraphicByType(context);
    } catch (Exception e) {
      LOGGER.error("Error building compound graphic: {}", e.getMessage(), e);
      throw new InvalidShapeException("Failed to build compound graphic", e);
    }
  }

  private static Graphic buildGraphicByType(BaseGraphicContext context)
      throws InvalidShapeException {
    String type = context.getType();
    if (type == null) {
      LOGGER.warn("Cannot build graphic with null type");
      return null;
    }

    GraphicBuilder builder = getGraphicBuilder(type.toUpperCase());
    if (builder == null) {
      LOGGER.warn("Unsupported graphic type: {}", type);
      return null;
    }

    return builder.build(context);
  }

  private static GraphicBuilder getGraphicBuilder(String type) {
    return switch (type) {
      case POINT -> PrGraphicUtil::buildPoint;
      case POLYLINE, MULTILINE -> PrGraphicUtil::buildPolyline;
      case INTERPOLATED -> PrGraphicUtil::buildInterpolated;
      case CIRCLE -> PrGraphicUtil::buildCircle;
      case ELLIPSE -> PrGraphicUtil::buildEllipse;
      case MULTIPOINT -> PrGraphicUtil::buildMultiPoint;
      case RECTANGLE -> PrGraphicUtil::buildRectangle;
      case RULER, ARROW -> PrGraphicUtil::buildLine;
      default -> null;
    };
  }

  // ========== Graphic Builders ==========

  private static Graphic buildPoint(BaseGraphicContext context) throws InvalidShapeException {
    PointData data = validateAndExtractPointData(context);
    if (data == null) return null;

    if (context.canBeEdited()) {
      PointGraphic graphic = new PointGraphic();
      graphic.buildGraphic(Collections.singletonList(data.point()));
      graphic.setPointSize(DEFAULT_POINT_SIZE);
      context.applyProperties(graphic, true);
      return graphic;
    } else {
      return createPointShape(context, data.point());
    }
  }

  private static Graphic buildPolyline(BaseGraphicContext context) throws InvalidShapeException {
    PolylineData data = validateAndExtractPolylineData(context);
    if (data == null) return null;

    List<Point2D> points = data.points();
    boolean isClosed = data.isClosed(context.isDcmSR());

    if (context.canBeEdited()) {
      return createEditablePolyline(context, points, isClosed);
    } else {
      return createNonEditablePolyline(context, points, isClosed);
    }
  }

  private static Graphic buildInterpolated(BaseGraphicContext context) {
    PolylineData data = validateAndExtractPolylineData(context);
    if (data == null) return null;

    Path2D path = createInterpolatedPath(context, data.rawPoints());
    return createNonEditableGraphic(context, path, context.isFilled());
  }

  private static Graphic buildCircle(BaseGraphicContext context) throws InvalidShapeException {
    LineData data = validateAndExtractLineData(context);
    if (data == null) return null;

    double radius = data.start().distance(data.end());

    Ellipse2D ellipse = new Ellipse2D.Double();
    ellipse.setFrameFromCenter(
        data.start().getX(),
        data.start().getY(),
        data.start().getX() + radius,
        data.start().getY() + radius);

    return createEllipseGraphic(context, ellipse);
  }

  private static Graphic buildEllipse(BaseGraphicContext context) throws InvalidShapeException {
    EllipseData data = validateAndExtractEllipseData(context);
    if (data == null) return null;

    EllipseGeometry geometry = calculateEllipseGeometry(data);
    return createEllipseWithRotation(context, geometry);
  }

  private static Graphic buildMultiPoint(BaseGraphicContext context) {
    PolylineData data = validateAndExtractPolylineData(context);
    if (data == null) return null;

    Path2D path = createMultiPointPath(context, data.rawPoints());
    return createNonEditableGraphic(context, path, true);
  }

  private static Graphic buildRectangle(BaseGraphicContext context) throws InvalidShapeException {
    RectangleData data = validateAndExtractRectangleData(context);
    if (data == null) return null;

    Rectangle2D rectangle = createRectangle(data);
    return createRectangleGraphic(context, rectangle);
  }

  private static Graphic buildLine(BaseGraphicContext context) {
    LineData data = validateAndExtractLineData(context);
    if (data == null) return null;

    Path2D path = new Path2D.Double();
    path.moveTo(data.start().getX(), data.start().getY());
    path.lineTo(data.end().getX(), data.end().getY());

    return createNonEditableGraphic(context, path, false);
  }

  // ========== Data Validation and Extraction ==========

  private static PointData validateAndExtractPointData(BaseGraphicContext context) {
    float[] points = context.getPoints();
    if (points == null || points.length < 2) {
      return null;
    }
    Point2D point = context.transformPoint(points[0], points[1]);
    return new PointData(point, points);
  }

  private static LineData validateAndExtractLineData(BaseGraphicContext context) {
    float[] points = getFloats(context);
    if (points == null) {
      return null;
    }

    Point2D start = context.transformPoint(points[0], points[1]);
    Point2D end = context.transformPoint(points[2], points[3]);

    return new LineData(start, end, points);
  }

  private static float[] getFloats(BaseGraphicContext context) {
    float[] points = context.getPoints();
    if (points == null || points.length < 4) {
      return null;
    }
    return points;
  }

  private static PolylineData validateAndExtractPolylineData(BaseGraphicContext context) {
    float[] points = getFloats(context);
    if (points == null) {
      return null;
    }

    List<Point2D> transformedPoints = transformPoints(context, points);
    return new PolylineData(transformedPoints, points);
  }

  private static RectangleData validateAndExtractRectangleData(BaseGraphicContext context) {
    float[] points = getFloats(context);
    if (points == null) {
      return null;
    }

    Point2D topLeft = context.transformPoint(points[0], points[1]);
    Point2D bottomRight = context.transformPoint(points[2], points[3]);
    return new RectangleData(topLeft, bottomRight, points);
  }

  private static EllipseData validateAndExtractEllipseData(BaseGraphicContext context) {
    float[] pts = context.getPoints();
    if (pts == null || pts.length < 8) {
      return null;
    }
    Point2D major1 = context.transformPoint(pts[0], pts[1]);
    Point2D major2 = context.transformPoint(pts[2], pts[3]);
    Point2D minor1 = context.transformPoint(pts[4], pts[5]);
    Point2D minor2 = context.transformPoint(pts[6], pts[7]);
    return new EllipseData(major1, major2, minor1, minor2, pts);
  }

  // ========== Shape Creation Utilities ==========

  private static List<Point2D> transformPoints(BaseGraphicContext context, float[] points) {
    int size = points.length / 2;
    List<Point2D> handlePoints = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      Point2D point = context.transformPoint(points[i * 2], points[i * 2 + 1]);
      handlePoints.add(point);
    }
    return handlePoints;
  }

  private static Graphic createPointShape(BaseGraphicContext context, Point2D point) {
    Ellipse2D ellipse =
        new Ellipse2D.Double(
            point.getX() - DEFAULT_POINT_SIZE / 2.0,
            point.getY() - DEFAULT_POINT_SIZE / 2.0,
            DEFAULT_POINT_SIZE,
            DEFAULT_POINT_SIZE);
    return createNonEditableGraphic(context, ellipse, true);
  }

  private static Graphic createEditablePolyline(
      BaseGraphicContext context, List<Point2D> points, boolean isClosed)
      throws InvalidShapeException {
    if (!(context instanceof GraphicContext graphicContext)) {
      throw new InvalidShapeException("Cannot create editable polyline from non-graphic context");
    }
    if (isClosed) {
      PolygonGraphic graphic = new PolygonGraphic();
      graphic.buildGraphic(points);
      graphicContext.applyProperties(graphic, graphicContext.isFilled());
      return graphic;
    } else {
      PolylineGraphic graphic = new PolylineGraphic();
      graphic.buildGraphic(points);
      graphicContext.applyProperties(graphic, false);
      return graphic;
    }
  }

  private static Graphic createNonEditablePolyline(
      BaseGraphicContext context, List<Point2D> points, boolean isClosed) {
    Path2D path = createPath2D(points);
    if (context.isDcmSR() || isClosed) {
      path.closePath();
    }
    return createNonEditableGraphic(context, path, context.isFilled());
  }

  private static Graphic createRectangleGraphic(BaseGraphicContext context, Rectangle2D rect)
      throws InvalidShapeException {
    if (context instanceof GraphicContext graphicContext && context.canBeEdited()) {
      RectangleGraphic graphic = new RectangleGraphic();
      graphic.buildGraphic(rect);
      graphicContext.applyProperties(graphic, graphicContext.isFilled());
      return graphic;
    } else {
      return createNonEditableGraphic(context, rect, context.isFilled());
    }
  }

  private static Graphic createEllipseGraphic(BaseGraphicContext context, Ellipse2D ellipse)
      throws InvalidShapeException {
    if (context instanceof GraphicContext graphicContext && context.canBeEdited()) {
      EllipseGraphic graphic = new EllipseGraphic();
      graphic.buildGraphic(ellipse.getFrame());
      graphicContext.applyProperties(graphic, graphicContext.isFilled());
      return graphic;
    } else {
      return createNonEditableGraphic(context, ellipse, context.isFilled());
    }
  }

  private static Graphic createEllipseWithRotation(
      BaseGraphicContext context, EllipseGeometry geometry) throws InvalidShapeException {
    Ellipse2D ellipse = new Ellipse2D.Double();
    ellipse.setFrameFromCenter(
        geometry.cx(), geometry.cy(), geometry.cx() + geometry.rx(), geometry.cy() + geometry.ry());

    if (MathUtil.isDifferentFromZero(geometry.rotation())) {
      AffineTransform rotate =
          AffineTransform.getRotateInstance(geometry.rotation(), geometry.cx(), geometry.cy());
      Shape rotatedEllipse = rotate.createTransformedShape(ellipse);
      return createNonEditableGraphic(context, rotatedEllipse, context.isFilled());
    } else {
      return createEllipseGraphic(context, ellipse);
    }
  }

  private static Graphic createNonEditableGraphic(
      BaseGraphicContext context, Shape shape, boolean filled) {
    NonEditableGraphic graphic = new NonEditableGraphic(shape);
    context.applyProperties(graphic, filled);
    return graphic;
  }

  private static Path2D createPath2D(List<Point2D> points) {
    Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, points.size());
    if (!points.isEmpty()) {
      Point2D first = points.getFirst();
      path.moveTo(first.getX(), first.getY());
      for (int i = 1; i < points.size(); i++) {
        Point2D point = points.get(i);
        path.lineTo(point.getX(), point.getY());
      }
    }
    return path;
  }

  private static Path2D createInterpolatedPath(BaseGraphicContext context, float[] points) {
    List<Point2D> pointList = transformPoints(context, points);
    boolean closed = pointList.getFirst().equals(pointList.getLast());
    return InterpolatedPath2D.buildCentripetal(pointList, closed, 1.0);
  }

  private static Path2D createMultiPointPath(BaseGraphicContext context, float[] points) {

    int size = points.length / 2;
    Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, size);

    for (int i = 0; i < size; i++) {
      Point2D point = context.transformPoint(points[i * 2], points[i * 2 + 1]);
      Ellipse2D ellipse =
          new Ellipse2D.Double(
              point.getX() - DEFAULT_POINT_SIZE / 2.0,
              point.getY() - DEFAULT_POINT_SIZE / 2.0,
              DEFAULT_POINT_SIZE,
              DEFAULT_POINT_SIZE);
      path.append(ellipse, false);
    }
    return path;
  }

  private static Rectangle2D createRectangle(RectangleData data) {

    return new Rectangle2D.Double(
        Math.min(data.topLeft().getX(), data.bottomRight().getX()),
        Math.min(data.topLeft().getY(), data.bottomRight().getY()),
        Math.abs(data.bottomRight().getX() - data.topLeft().getX()),
        Math.abs(data.bottomRight().getY() - data.topLeft().getY()));
  }

  private static EllipseGeometry calculateEllipseGeometry(EllipseData data) {
    double cx = (data.major1().getX() + data.major2().getX()) / 2;
    double cy = (data.major1().getY() + data.major2().getY()) / 2;
    double rx = data.major1().distance(data.major2()) / 2;
    double ry = data.minor1().distance(data.minor2()) / 2;
    double rotation = calculateRotation(data.major1(), data.major2(), cx, cy);

    return new EllipseGeometry(cx, cy, rx, ry, rotation);
  }

  private static double calculateRotation(Point2D major1, Point2D major2, double cx, double cy) {
    if (MathUtil.isEqual(major1.getX(), major2.getX())) {
      return Math.PI / 2;
    } else if (MathUtil.isEqual(major1.getY(), major2.getY())) {
      return 0;
    } else {
      return Math.atan2(major2.getY() - cy, major2.getX() - cx);
    }
  }

  // ========== Utility Methods ==========

  public static boolean isDashedLine(Attributes style) {
    return style != null && "DASHED".equalsIgnoreCase(style.getString(Tag.LineDashingStyle));
  }

  public static Color getPatternColor(Attributes style, Color defaultColor) {
    if (style == null) {
      return defaultColor;
    }
    try {
      int[] labValues = style.getInts(Tag.PatternOnColorCIELabValue);
      if (labValues != null && labValues.length >= 3) {
        int[] rgb = CIELab.dicomLab2rgb(labValues);
        Color color = DicomObjectUtil.getRGBColor(0xFFFF, rgb);

        Float opacity = DicomUtils.getFloatFromDicomElement(style, Tag.PatternOnOpacity, null);
        if (opacity != null && opacity < 1.0f) {
          int alpha = Math.round(opacity * 255);
          color = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }
        return color;
      }
    } catch (Exception e) {
      LOGGER.warn("Failed to extract pattern color from style: {}", e.getMessage());
    }

    return defaultColor;
  }

  public static Color getFillPatternColor(Attributes fillStyle, Color defaultColor) {
    return fillStyle != null ? getPatternColor(fillStyle, defaultColor) : defaultColor;
  }

  public static GraphicModel getPresentationModel(Attributes attributes) {
    if (attributes == null) {
      return null;
    }
    String id = attributes.getString(PresentationStateReader.PRIVATE_CREATOR_TAG);
    if (PresentationStateReader.PR_MODEL_ID.equals(id)) {
      try {
        byte[] modelData = attributes.getBytes(PresentationStateReader.PR_MODEL_PRIVATE_TAG);
        if (modelData != null) {
          return XmlSerializer.buildPresentationModel(modelData);
        }
      } catch (Exception e) {
        LOGGER.error("Cannot extract binary presentation model", e);
      }
    }
    return null;
  }

  public static void applyPresentationModel(ImageElement img) {
    if (img == null) {
      return;
    }

    try {
      byte[] prBinary = TagW.getTagValue(img, TagW.PresentationModelBirary, byte[].class);
      if (prBinary != null) {
        GraphicModel model = XmlSerializer.buildPresentationModel(prBinary);
        if (model != null) {
          img.setTag(TagW.PresentationModel, model);
          img.setTag(TagW.PresentationModelBirary, null);
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed to apply presentation model", e);
    }
  }

  // ========== Data Records ==========

  private record PointData(Point2D point, float[] rawPoints) {

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PointData pointData = (PointData) o;
      return Objects.equals(point(), pointData.point())
          && Objects.deepEquals(rawPoints(), pointData.rawPoints());
    }

    @Override
    public int hashCode() {
      return Objects.hash(point(), Arrays.hashCode(rawPoints()));
    }

    @Override
    public String toString() {
      return "PointData{" // NON-NLS
          + "point=" // NON-NLS
          + point()
          + ", rawPoints=" // NON-NLS
          + Arrays.toString(rawPoints())
          + '}';
    }
  }

  private record LineData(Point2D start, Point2D end, float[] rawPoints) {

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      LineData lineData = (LineData) o;
      return Objects.equals(end(), lineData.end())
          && Objects.equals(start(), lineData.start())
          && Objects.deepEquals(rawPoints(), lineData.rawPoints());
    }

    @Override
    public int hashCode() {
      return Objects.hash(start(), end(), Arrays.hashCode(rawPoints()));
    }

    @Override
    public String toString() {
      return "LineData{" // NON-NLS
          + "start=" // NON-NLS
          + start()
          + ", end=" // NON-NLS
          + end()
          + ", rawPoints=" // NON-NLS
          + Arrays.toString(rawPoints())
          + '}';
    }
  }

  private record PolylineData(List<Point2D> points, float[] rawPoints) {
    public boolean isClosed(boolean isDcmSR) {
      if (isDcmSR && !points.getFirst().equals(points.getLast())) {
        points.add(new Point2D.Double(points.getFirst().getX(), points.getFirst().getY()));
      }
      return points.getFirst().equals(points.getLast());
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PolylineData that = (PolylineData) o;
      return Objects.deepEquals(rawPoints(), that.rawPoints())
          && Objects.equals(points(), that.points());
    }

    @Override
    public int hashCode() {
      return Objects.hash(points(), Arrays.hashCode(rawPoints()));
    }

    @Override
    public String toString() {
      return "PolylineData{" // NON-NLS
          + "points=" // NON-NLS
          + points()
          + ", rawPoints=" // NON-NLS
          + Arrays.toString(rawPoints())
          + '}';
    }
  }

  private record RectangleData(Point2D topLeft, Point2D bottomRight, float[] rawPoints) {

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      RectangleData that = (RectangleData) o;
      return Objects.equals(topLeft(), that.topLeft())
          && Objects.deepEquals(rawPoints(), that.rawPoints())
          && Objects.equals(bottomRight(), that.bottomRight());
    }

    @Override
    public int hashCode() {
      return Objects.hash(topLeft(), bottomRight(), Arrays.hashCode(rawPoints()));
    }

    @Override
    public String toString() {
      return "RectangleData{" // NON-NLS
          + "topLeft=" // NON-NLS
          + topLeft()
          + ", bottomRight=" // NON-NLS
          + bottomRight()
          + ", rawPoints=" // NON-NLS
          + Arrays.toString(rawPoints())
          + '}';
    }
  }

  private record EllipseData(
      Point2D major1, Point2D major2, Point2D minor1, Point2D minor2, float[] rawPoints) {

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      EllipseData that = (EllipseData) o;
      return Objects.equals(major1(), that.major1())
          && Objects.equals(major2(), that.major2())
          && Objects.equals(minor1(), that.minor1())
          && Objects.equals(minor2(), that.minor2())
          && Objects.deepEquals(rawPoints(), that.rawPoints());
    }

    @Override
    public int hashCode() {
      return Objects.hash(major1(), major2(), minor1(), minor2(), Arrays.hashCode(rawPoints()));
    }

    @Override
    public String toString() {
      return "EllipseData{" // NON-NLS
          + "major1=" // NON-NLS
          + major1()
          + ", major2=" // NON-NLS
          + major2()
          + ", minor1=" // NON-NLS
          + minor1()
          + ", minor2=" // NON-NLS
          + minor2()
          + ", rawPoints=" // NON-NLS
          + Arrays.toString(rawPoints())
          + '}';
    }
  }

  private record EllipseGeometry(double cx, double cy, double rx, double ry, double rotation) {}

  // ========== Functional Interface ==========

  @FunctionalInterface
  private interface GraphicBuilder {
    Graphic build(BaseGraphicContext context) throws InvalidShapeException;
  }

  // ========== Context Classes ==========

  private abstract static class BaseGraphicContext {
    protected final Attributes attributes;
    protected final Color defaultColor;
    protected final boolean labelVisible;
    protected final double width;
    protected final double height;
    protected final AffineTransform inverse;
    protected final boolean isDisplay;
    protected final String type;
    protected final Integer groupID;
    protected final boolean filled;
    protected final float thickness;
    protected final boolean dashed;
    protected final Color color;

    protected BaseGraphicContext(
        Attributes attributes,
        Color defaultColor,
        boolean labelVisible,
        double width,
        double height,
        AffineTransform inverse,
        int unitsTag,
        int typeTag) {
      this.attributes = attributes;
      this.defaultColor = defaultColor;
      this.labelVisible = labelVisible;
      this.width = width;
      this.height = height;
      this.inverse = inverse;
      this.isDisplay = "DISPLAY".equalsIgnoreCase(attributes.getString(unitsTag));
      this.type = attributes.getString(typeTag);
      this.groupID = DicomUtils.getIntegerFromDicomElement(attributes, Tag.GraphicGroupID, null);
      this.filled = PresentationStateReader.getBooleanValue(attributes, Tag.GraphicFilled);

      Attributes style = attributes.getNestedDataset(Tag.LineStyleSequence);
      this.thickness =
          DicomUtils.getFloatFromDicomElement(style, Tag.LineThickness, DEFAULT_LINE_THICKNESS);
      this.dashed = isDashedLine(style);

      Color styleColor = getPatternColor(style, defaultColor);
      Attributes fillStyle = attributes.getNestedDataset(Tag.FillStyleSequence);
      this.color = getFillPatternColor(fillStyle, styleColor);
    }

    public String getType() {
      return type;
    }

    public boolean isFilled() {
      return filled;
    }

    public boolean isDcmSR() {
      return false;
    }

    public boolean canBeEdited() {
      return false;
    }

    public float[] getPoints() {
      return DicomUtils.getFloatArrayFromDicomElement(attributes, Tag.GraphicData, null);
    }

    public Point2D transformPoint(float x, float y) {
      if (isDisplay) {
        x *= (float) width;
        y *= (float) height;
      }

      if (isDisplay && inverse != null) {
        float[] src = {x, y};
        float[] dst = new float[2];
        inverse.transform(src, 0, dst, 0, 1);
        return new Point2D.Double(dst[0], dst[1]);
      }

      return new Point2D.Double(x, y);
    }

    public void applyProperties(Graphic graphic, boolean filled) {
      if (graphic != null) {
        graphic.setLineThickness(thickness);
        graphic.setPaint(color);
        graphic.setLabelVisible(labelVisible);
        graphic.setClassID(groupID);
        graphic.setFilled(filled);
      }
    }
  }

  private static class GraphicContext extends BaseGraphicContext {
    private final boolean canBeEdited;
    private final boolean dcmSR;

    public GraphicContext(
        Attributes attributes,
        Color defaultColor,
        boolean labelVisible,
        double width,
        double height,
        boolean canBeEdited,
        AffineTransform inverse,
        boolean dcmSR) {
      super(
          attributes,
          defaultColor,
          labelVisible,
          width,
          height,
          inverse,
          Tag.GraphicAnnotationUnits,
          Tag.GraphicType);
      this.canBeEdited = canBeEdited;
      this.dcmSR = dcmSR;
    }

    @Override
    public boolean canBeEdited() {
      return canBeEdited;
    }

    @Override
    public boolean isDcmSR() {
      return dcmSR;
    }
  }

  private static class CompoundGraphicContext extends BaseGraphicContext {
    public CompoundGraphicContext(
        Attributes attributes,
        Color defaultColor,
        boolean labelVisible,
        double width,
        double height,
        AffineTransform inverse) {
      super(
          attributes,
          defaultColor,
          labelVisible,
          width,
          height,
          inverse,
          Tag.CompoundGraphicUnits,
          Tag.CompoundGraphicType);
    }
  }
}
