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

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import java.awt.Color;
import java.awt.Shape;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.ICCProfile;
import org.dcm4che3.img.data.CIELab;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.ReferencedImage;
import org.weasis.core.ui.model.ReferencedSeries;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicLabel;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.graphic.imp.PointGraphic;
import org.weasis.core.ui.model.graphic.imp.area.EllipseGraphic;
import org.weasis.core.ui.model.graphic.imp.area.ObliqueRectangleGraphic;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.area.ThreePointsCircleGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.display.CharsetEncoding;

/**
 * DICOM Presentation State serializer for converting GraphicModel to DICOM PR objects.
 *
 * <p>This class handles the conversion of Weasis graphics and annotations to DICOM Presentation
 * State format, including proper handling of different graphic types, colors, and metadata.
 */
public class DicomPrSerializer {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomPrSerializer.class);

  private static final String PIXEL = "PIXEL";
  private static final String GSPS = "GSPS"; // NON-NLS
  private static final String DESCRIPTION = "Weasis Graphics and Annotations"; // NON-NLS
  private static final String YES = "Y"; // NON-NLS
  private static final String NO = "N"; // NON-NLS
  private static final String CARRIAGE_RETURN_LINE_FEED = "\r\n";
  private static final int DEFAULT_SERIES_NUMBER = 999;
  private static final Color DEFAULT_LINE_COLOR = Color.YELLOW;
  private static final double FLATTENING_TOLERANCE = 1.0;

  private DicomPrSerializer() {}

  /**
   * Writes a GraphicModel to a DICOM Presentation State file.
   *
   * @param model The GraphicModel to serialize
   * @param parentAttributes Parent DICOM attributes
   * @param outputFile Output file location
   * @param seriesInstanceUID Series Instance UID
   * @param sopInstanceUID SOP Instance UID
   * @return Created DICOM attributes or null if failed
   */
  public static Attributes writePresentation(
      GraphicModel model,
      Attributes parentAttributes,
      File outputFile,
      String seriesInstanceUID,
      String sopInstanceUID) {
    return writePresentation(
        model, parentAttributes, outputFile, seriesInstanceUID, sopInstanceUID, null);
  }

  /**
   * Writes a GraphicModel to a DICOM Presentation State file with offset support.
   *
   * @param model The GraphicModel to serialize
   * @param parentAttributes Parent DICOM attributes
   * @param outputFile Output file location
   * @param seriesInstanceUID Series Instance UID
   * @param sopInstanceUID SOP Instance UID
   * @param offset Optional offset to apply to graphics coordinates
   * @return Created DICOM attributes or null if failed
   */
  public static Attributes writePresentation(
      GraphicModel model,
      Attributes parentAttributes,
      File outputFile,
      String seriesInstanceUID,
      String sopInstanceUID,
      Point2D offset) {
    Objects.requireNonNull(model, "GraphicModel cannot be null");
    Objects.requireNonNull(outputFile, "Output file cannot be null");

    if (parentAttributes == null) {
      LOGGER.warn("Parent attributes are null, cannot create presentation state");
      return null;
    }
    try {
      return createPresentationState(
          model, parentAttributes, outputFile, seriesInstanceUID, sopInstanceUID, offset);
    } catch (Exception e) {
      LOGGER.error("Cannot write Presentation State to file: {}", outputFile.getAbsolutePath(), e);
      return null;
    }
  }

  /**
   * Writes a GraphicModel to a DICOM Presentation State file using image element.
   *
   * @param model The GraphicModel to serialize
   * @param img DICOM image element containing attributes
   * @param outputFile Output file location
   * @param seriesInstanceUID Series Instance UID
   * @param sopInstanceUID SOP Instance UID
   * @return Created DICOM attributes or null if failed
   */
  public static Attributes writePresentation(
      GraphicModel model,
      DicomImageElement img,
      File outputFile,
      String seriesInstanceUID,
      String sopInstanceUID) {
    Attributes imgAttributes = extractImageAttributes(img);
    return writePresentation(
        model, imgAttributes, outputFile, seriesInstanceUID, sopInstanceUID, null);
  }

  private static Attributes createPresentationState(
      GraphicModel model,
      Attributes parentAttributes,
      File outputFile,
      String seriesInstanceUID,
      String sopInstanceUID,
      Point2D offset)
      throws Exception {

    GraphicModel serializedModel = getModelForSerialization(model, offset);
    Attributes attributes = createDicomPR(parentAttributes, seriesInstanceUID, sopInstanceUID);

    addDisplayedAreaSelectionSequence(attributes, parentAttributes);
    addGraphicAnnotationSequence(attributes);

    writeCommonTags(attributes);
    writeReferences(attributes, serializedModel, parentAttributes.getString(Tag.SOPClassUID));
    writeGraphics(serializedModel, attributes);
    writePrivateTags(serializedModel, attributes);

    if (saveToFile(outputFile, attributes)) {
      return attributes;
    } else {
      throw new IOException("Failed to save DICOM PR to file: " + outputFile.getAbsolutePath());
    }
  }

  private static Attributes extractImageAttributes(DicomImageElement img) {
    if (img != null && img.getMediaReader() instanceof DcmMediaReader reader) {
      return reader.getDicomObject();
    }
    return null;
  }

  public static Attributes createDicomPR(
      Attributes dicomSourceAttribute, String seriesInstanceUID, String sopInstanceUID) {

    final int[] patientStudyAttributes = {
      Tag.SpecificCharacterSet,

      // Patient Level - Required
      Tag.PatientName,
      Tag.PatientID,
      Tag.PatientBirthDate,
      Tag.PatientSex,
      // Patient Level - Optional
      Tag.IssuerOfPatientID,
      Tag.PatientBirthTime,
      Tag.PatientAge,
      Tag.PatientWeight,
      Tag.PatientSize,
      Tag.PatientComments,
      Tag.AdditionalPatientHistory,

      // Study Level - Required
      Tag.StudyInstanceUID,
      Tag.StudyDate,
      Tag.StudyTime,
      Tag.AccessionNumber,
      Tag.ReferringPhysicianName,
      Tag.StudyDescription,
      Tag.StudyID,
      // Study Level - Optional
      Tag.IssuerOfAccessionNumberSequence,
      Tag.IssuerOfPatientIDQualifiersSequence,
      Tag.IssuerOfAccessionNumberSequence,
      Tag.PatientIdentityRemoved,
      Tag.DeidentificationMethod,
      Tag.DeidentificationMethodCodeSequence
    };

    Arrays.sort(patientStudyAttributes);
    Attributes pr = new Attributes(dicomSourceAttribute, patientStudyAttributes);

    String sopClassUID = determinePresentationStateSopClass(dicomSourceAttribute);
    pr.setString(Tag.SOPClassUID, VR.UI, sopClassUID);
    // Force UTF-8 character set for adding text in any language
    pr.setString(Tag.SpecificCharacterSet, VR.CS, CharsetEncoding.ISO_IR_192.getLabel());

    // Add mandatory ICC Profile for Color and Pseudo-Color Softcopy Presentation State
    if (!UID.GrayscaleSoftcopyPresentationStateStorage.equals(sopClassUID)) {
      addICCProfile(pr, dicomSourceAttribute);
    }

    pr.setString(
        Tag.SOPInstanceUID,
        VR.UI,
        StringUtil.hasText(sopInstanceUID) ? sopInstanceUID : UIDUtils.createUID());
    Date now = new Date();
    pr.setDate(Tag.PresentationCreationDateAndTime, now);
    pr.setDate(Tag.ContentDateAndTime, now);
    pr.setString(Tag.Modality, VR.CS, "PR");
    pr.setString(
        Tag.SeriesInstanceUID,
        VR.UI,
        StringUtil.hasText(seriesInstanceUID) ? seriesInstanceUID : UIDUtils.createUID());
    return pr;
  }

  public static String determinePresentationStateSopClass(Attributes parentAttributes) {
    if (parentAttributes == null) {
      return UID.GrayscaleSoftcopyPresentationStateStorage;
    }

    String photometricInterpretation = parentAttributes.getString(Tag.PhotometricInterpretation);
    if ("PALETTE COLOR".equals(photometricInterpretation)) { // NON-NLS
      return UID.PseudoColorSoftcopyPresentationStateStorage;
    }

    int samplesPerPixel = parentAttributes.getInt(Tag.SamplesPerPixel, 1);
    if (samplesPerPixel >= 3) {
      return UID.ColorSoftcopyPresentationStateStorage;
    }
    return UID.GrayscaleSoftcopyPresentationStateStorage;
  }

  private static void addICCProfile(Attributes pr, Attributes dicomSourceAttribute) {
    if (dicomSourceAttribute != null) {
      ColorSpace colorSpace = null;
      if (ICCProfile.isPresentIn(dicomSourceAttribute)) {
        var profile = ICCProfile.colorSpaceFactoryOf(dicomSourceAttribute).getColorSpace(0);
        colorSpace = profile.orElse(null);
      }
      try {
        if (colorSpace instanceof ICC_ColorSpace iccColorSpace) {
          var data = iccColorSpace.getProfile().getData();
          pr.setBytes(Tag.ICCProfile, VR.OB, data);
        } else {
          // Set sRGB ICC profile but do not embed it as it is generally the default profile.
          pr.setBytes(Tag.ICCProfile, VR.OB, null);
          pr.setString(Tag.ColorSpace, VR.CS, "sRGB");
        }
      } catch (Exception e) {
        LOGGER.warn("Cannot set default sRGB ICC profile", e);
      }
    }
  }

  /**
   * Adds Displayed Area Selection Sequence to the DICOM attributes. See <a
   * href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.10.4.html#table_C.10-4">C.10-4.
   * Displayed Area Module Attributes</a>
   */
  private static void addDisplayedAreaSelectionSequence(
      Attributes attributes, Attributes sourceAttributes) {
    Sequence displayedAreaSeq = attributes.newSequence(Tag.DisplayedAreaSelectionSequence, 1);
    Attributes displayedAreaItem = new Attributes();

    int imageWidth = sourceAttributes.getInt(Tag.Columns, 512);
    int imageHeight = sourceAttributes.getInt(Tag.Rows, 512);
    displayedAreaItem.setInt(
        Tag.DisplayedAreaBottomRightHandCorner, VR.SL, imageWidth, imageHeight);
    displayedAreaItem.setInt(Tag.DisplayedAreaTopLeftHandCorner, VR.SL, 1, 1);
    displayedAreaItem.setString(Tag.PresentationSizeMode, VR.CS, "SCALE TO FIT"); // NON-NLS
    double[] pixelSpacing =
        DicomUtils.getDoubleArrayFromDicomElement(
            sourceAttributes, Tag.PixelSpacing, new double[] {1.0, 1.0});
    displayedAreaItem.setDouble(Tag.PresentationPixelSpacing, VR.DS, pixelSpacing);
    displayedAreaSeq.add(displayedAreaItem);
  }

  private static void addGraphicAnnotationSequence(Attributes attributes) {
    // Create empty Graphic Annotation Sequence (Type 1)
    attributes.newSequence(Tag.GraphicAnnotationSequence, 0);
  }

  /**
   * Creates a model suitable for serialization by filtering and transforming graphics.
   *
   * @param model Original GraphicModel
   * @param offset Optional offset to apply to coordinates
   * @return Filtered GraphicModel for serialization
   */
  public static GraphicModel getModelForSerialization(GraphicModel model, Point2D offset) {
    XmlGraphicModel xmlModel = new XmlGraphicModel();
    xmlModel.setReferencedSeries(model.getReferencedSeries());
    for (Graphic graphic : model.getModels()) {
      if (isGraphicSerializable(graphic)) {
        Graphic processedGraphic = processGraphicForSerialization(graphic, offset);
        xmlModel.addGraphic(processedGraphic);
      }
    }
    return xmlModel;
  }

  private static boolean isGraphicSerializable(Graphic graphic) {
    return graphic.getLayer().getSerializable() && !graphic.getPts().isEmpty();
  }

  private static Graphic processGraphicForSerialization(Graphic graphic, Point2D offset) {
    if (offset == null) {
      return graphic;
    }

    Graphic processedGraphic = graphic.copy();
    applyOffsetToGraphic(processedGraphic, offset);
    processedGraphic.buildShape();

    GraphicLabel label = graphic.getGraphicLabel();
    if (label != null) {
      processedGraphic.setLabel(label);
    }
    return processedGraphic;
  }

  private static void applyOffsetToGraphic(Graphic graphic, Point2D offset) {
    for (Point2D point : graphic.getPts()) {
      point.setLocation(point.getX() - offset.getX(), point.getY() - offset.getY());
    }
  }

  private static void writePrivateTags(GraphicModel model, Attributes attributes) {
    try {
      JAXBContext jaxbContext = XmlSerializer.getJaxbContext(model.getClass());
      Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
      try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        jaxbMarshaller.marshal(model, outputStream);
        // jaxbMarshaller.marshal(model, System.out);
        attributes.setString(
            PresentationStateReader.PRIVATE_CREATOR_TAG,
            VR.LO,
            PresentationStateReader.PR_MODEL_ID);
        attributes.setBytes(
            PresentationStateReader.PR_MODEL_PRIVATE_TAG,
            VR.OB,
            GzipManager.gzipCompressToByte(outputStream.toByteArray()));
      }
    } catch (Exception | NoClassDefFoundError e) {
      LOGGER.error("Cannot save XML model to private tags", e);
    }
  }

  private static void writeCommonTags(Attributes attributes) {
    attributes.setInt(Tag.InstanceNumber, VR.IS, 1);
    attributes.setString(Tag.ContentCreatorName, VR.PN, AppProperties.WEASIS_USER);
    attributes.setString(Tag.ContentLabel, VR.CS, GSPS);
    attributes.setString(Tag.ContentDescription, VR.LO, DESCRIPTION);
    attributes.setInt(Tag.SeriesNumber, VR.IS, DEFAULT_SERIES_NUMBER);
    attributes.setString(Tag.SoftwareVersions, VR.LO, AppProperties.WEASIS_VERSION);
    attributes.setString(
        Tag.SeriesDescription, VR.LO, String.join(" ", AppProperties.WEASIS_NAME, GSPS));
    attributes.setString(Tag.PresentationLUTShape, VR.CS, "IDENTITY"); // NON-NLS

    setStationName(attributes);
  }

  private static void setStationName(Attributes attributes) {
    try {
      String hostName = InetAddress.getLocalHost().getHostName();
      // Limit station name to 16 characters as per DICOM VR.SH specification
      String stationName = hostName.length() > 16 ? hostName.substring(0, 16) : hostName;
      attributes.setString(Tag.StationName, VR.SH, stationName);
    } catch (UnknownHostException e) {
      LOGGER.warn("Cannot get host name for station name", e);
    }
  }

  private static void writeReferences(
      Attributes attributes, GraphicModel model, String sopClassUID) {
    List<ReferencedSeries> referencedSeries = model.getReferencedSeries();
    Sequence seriesSeq =
        attributes.newSequence(Tag.ReferencedSeriesSequence, referencedSeries.size());

    for (ReferencedSeries seriesRef : referencedSeries) {
      Attributes seriesAttributes = createReferencedSeriesAttributes(seriesRef, sopClassUID);
      seriesSeq.add(seriesAttributes);
    }
  }

  private static Attributes createReferencedSeriesAttributes(
      ReferencedSeries seriesRef, String sopClassUID) {
    Attributes seriesAttributes = new Attributes(2);
    seriesAttributes.setString(Tag.SeriesInstanceUID, VR.UI, seriesRef.getUuid());

    List<ReferencedImage> images = seriesRef.getImages();
    Sequence imageSeq = seriesAttributes.newSequence(Tag.ReferencedImageSequence, images.size());

    for (ReferencedImage imageRef : images) {
      Attributes imageAttributes = createReferencedImageAttributes(imageRef, sopClassUID);
      imageSeq.add(imageAttributes);
    }
    return seriesAttributes;
  }

  private static Attributes createReferencedImageAttributes(
      ReferencedImage imageRef, String sopClassUID) {
    Attributes imageAttributes = new Attributes(2);
    imageAttributes.setString(Tag.ReferencedSOPClassUID, VR.UI, sopClassUID);
    imageAttributes.setString(Tag.ReferencedSOPInstanceUID, VR.UI, imageRef.getUuid());

    List<Integer> frames = imageRef.getFrames();
    if (frames != null && !frames.isEmpty()) {
      // Convert to DICOM frame numbers (1-based)
      int[] frameNumbers = frames.stream().mapToInt(i -> i + 1).toArray();
      imageAttributes.setInt(Tag.ReferencedFrameNumber, VR.IS, frameNumbers);
    }

    return imageAttributes;
  }

  private static void writeGraphics(GraphicModel model, Attributes attributes) {
    List<GraphicLayer> layers = getSerializableLayers(model.getLayers());

    Sequence annotationSeq = attributes.newSequence(Tag.GraphicAnnotationSequence, layers.size());
    Sequence layerSeq = attributes.newSequence(Tag.GraphicLayerSequence, layers.size());

    for (int i = 0; i < layers.size(); i++) {
      GraphicLayer layer = layers.get(i);
      processGraphicLayer(model, layer, i, annotationSeq, layerSeq);
    }
  }

  private static List<GraphicLayer> getSerializableLayers(List<GraphicLayer> layers) {
    return layers.stream().filter(GraphicLayer::getSerializable).collect(Collectors.toList());
  }

  private static void processGraphicLayer(
      GraphicModel model,
      GraphicLayer layer,
      int layerIndex,
      Sequence annotationSeq,
      Sequence layerSeq) {
    String layerName = layer.getType().name();
    List<Graphic> graphics = getGraphicsByLayer(model, layer.getUuid());

    // Create layer definition
    Attributes layerAttributes = createLayerAttributes(layer, layerName, layerIndex);
    layerSeq.add(layerAttributes);

    // Create annotation for this layer
    Attributes annotationAttributes = createAnnotationAttributes(layerName, graphics);
    annotationSeq.add(annotationAttributes);
  }

  private static Attributes createLayerAttributes(
      GraphicLayer layer, String layerName, int layerIndex) {
    Attributes attributes = new Attributes(4);
    attributes.setString(Tag.GraphicLayer, VR.CS, layerName);
    attributes.setInt(Tag.GraphicLayerOrder, VR.IS, layerIndex);
    attributes.setString(Tag.GraphicLayerDescription, VR.LO, layer.toString());

    setLayerDisplayColor(attributes);
    return attributes;
  }

  private static void setLayerDisplayColor(Attributes attributes) {
    Color lineColor =
        Optional.ofNullable(MeasureTool.viewSetting.getLineColor()).orElse(DEFAULT_LINE_COLOR);
    int[] lab = CIELab.rgbToDicomLab(lineColor);
    attributes.setInt(Tag.GraphicLayerRecommendedDisplayCIELabValue, VR.US, lab);
  }

  private static Attributes createAnnotationAttributes(String layerName, List<Graphic> graphics) {
    Attributes annotationAttributes = new Attributes(3);
    annotationAttributes.setString(Tag.GraphicLayer, VR.CS, layerName);

    Sequence graphicSeq =
        annotationAttributes.newSequence(Tag.GraphicObjectSequence, graphics.size());
    Sequence textSeq = annotationAttributes.newSequence(Tag.TextObjectSequence, graphics.size());

    for (Graphic graphic : graphics) {
      buildDicomGraphic(graphic, graphicSeq, textSeq);
    }
    return annotationAttributes;
  }

  private static List<Graphic> getGraphicsByLayer(GraphicModel model, String layerUid) {
    return model.getModels().stream()
        .filter(g -> layerUid.equals(g.getLayer().getUuid()))
        .collect(Collectors.toList());
  }

  private static void buildDicomGraphic(Graphic graphic, Sequence graphicSeq, Sequence textSeq) {
    if (graphic instanceof AnnotationGraphic annotationGraphic) {
      buildAnnotationGraphic(annotationGraphic, textSeq);
    } else {
      buildStandardGraphic(graphic, graphicSeq, textSeq);
    }
  }

  private static void buildAnnotationGraphic(AnnotationGraphic graphic, Sequence textSeq) {
    Attributes attributes = createAnnotationAttributes(graphic);
    textSeq.add(attributes);
  }

  private static void buildStandardGraphic(Graphic graphic, Sequence graphicSeq, Sequence textSeq) {
    GraphicTypeInfo typeInfo = getGraphicTypeInfo(graphic);

    if (typeInfo.requiresShapeTransformation()) {
      transformShapeToContour(graphic, graphicSeq);
    } else {
      Attributes dcm = createBasicGraphicAttributes(graphic, typeInfo);
      graphicSeq.add(dcm);
    }

    buildGraphicLabel(graphic, textSeq);
  }

  private static GraphicTypeInfo getGraphicTypeInfo(Graphic graphic) {
    switch (graphic) {
      case ObliqueRectangleGraphic rectangleShape -> {
        boolean isEllipse = graphic instanceof EllipseGraphic;
        if (isEllipse) {
          return new GraphicTypeInfo(null, null, true);
        }

        List<Point2D> points = rectangleShape.getRectanglePointList();
        points.add(points.getFirst()); // Close the rectangle
        return new GraphicTypeInfo(PrGraphicUtil.POLYLINE, points, false);
      }
      case ThreePointsCircleGraphic _ -> {
        Point2D centerPt = GeomUtil.getCircleCenter(graphic.getPts());
        List<Point2D> points = Arrays.asList(centerPt, graphic.getPts().getFirst());
        return new GraphicTypeInfo(PrGraphicUtil.CIRCLE, points, false);
      }
      case PolygonGraphic _ -> {
        List<Point2D> points = new ArrayList<>(graphic.getPts());
        points.add(points.getFirst()); // Close the polygon

        return new GraphicTypeInfo(PrGraphicUtil.POLYLINE, points, false);
      }
      case PolylineGraphic _ -> {
        return new GraphicTypeInfo(PrGraphicUtil.POLYLINE, graphic.getPts(), false);
      }
      case PointGraphic _ -> {
        List<Point2D> points = Collections.singletonList(graphic.getPts().getFirst());
        return new GraphicTypeInfo(PrGraphicUtil.POINT, points, false);
      }
      case null, default -> {
        return new GraphicTypeInfo(null, null, true);
      }
    }
  }

  private static Attributes createBasicGraphicAttributes(
      Graphic graphic, GraphicTypeInfo typeInfo) {
    Attributes dcm = getBasicGraphicStructure(graphic);
    dcm.setString(Tag.GraphicType, VR.CS, typeInfo.graphicType());
    dcm.setDouble(Tag.GraphicData, VR.FL, getGraphicsPoints(typeInfo.points()));
    dcm.setInt(Tag.NumberOfGraphicPoints, VR.US, typeInfo.points().size());
    return dcm;
  }

  private static Attributes getBasicGraphicStructure(Graphic graphic) {
    Attributes dcm = new Attributes(6);
    dcm.setString(Tag.GraphicAnnotationUnits, VR.CS, PIXEL);
    dcm.setInt(Tag.GraphicDimensions, VR.US, 2);
    dcm.setString(Tag.GraphicFilled, VR.CS, graphic.getFilled() ? YES : NO);

    int[] labColor = getLabColor(graphic);
    addLineStyleSequence(dcm, graphic, labColor);

    if (graphic.getFilled()) {
      addFillStyleSequence(dcm, graphic.getFillOpacity(), labColor);
    }
    return dcm;
  }

  private static int[] getLabColor(Graphic graphic) {
    Color lineColor =
        graphic.getColorPaint() instanceof Color color
            ? color
            : MeasureTool.viewSetting.getLineColor();
    return CIELab.rgbToDicomLab(lineColor);
  }

  private static void addLineStyleSequence(Attributes dcm, Graphic graphic, int[] labColor) {
    Sequence styleSeq = dcm.newSequence(Tag.LineStyleSequence, 1);
    Attributes styleAttributes = new Attributes();

    styleAttributes.setFloat(Tag.LineThickness, VR.FL, graphic.getLineThickness());
    styleAttributes.setString(Tag.LineDashingStyle, VR.CS, "SOLID");
    styleAttributes.setString(Tag.ShadowStyle, VR.CS, "OFF");
    styleAttributes.setFloat(Tag.ShadowOffsetX, VR.FL, 0.0f);
    styleAttributes.setFloat(Tag.ShadowOffsetY, VR.FL, 0.0f);
    styleAttributes.setInt(Tag.ShadowColorCIELabValue, VR.US, labColor);
    styleAttributes.setFloat(Tag.ShadowOpacity, VR.FL, 0.0f);
    styleAttributes.setFloat(Tag.PatternOnOpacity, VR.FL, 1.0f);
    styleAttributes.setInt(Tag.PatternOnColorCIELabValue, VR.US, labColor);
    styleAttributes.setFloat(Tag.PatternOffOpacity, VR.FL, 0.0f);
    styleSeq.add(styleAttributes);
  }

  private static void addFillStyleSequence(Attributes dcm, float opacity, int[] labColor) {
    Sequence fillStyleSeq = dcm.newSequence(Tag.FillStyleSequence, 1);
    Attributes fillAttributes = new Attributes();

    fillAttributes.setString(Tag.FillMode, VR.CS, "SOLID");
    fillAttributes.setFloat(Tag.PatternOnOpacity, VR.FL, opacity);
    fillAttributes.setFloat(Tag.PatternOffOpacity, VR.FL, 0.0f);
    fillAttributes.setInt(Tag.PatternOnColorCIELabValue, VR.US, labColor);
    fillStyleSeq.add(fillAttributes);
  }

  private static double[] getGraphicsPoints(List<Point2D> pts) {
    double[] coordinates = new double[pts.size() * 2];
    for (int i = 0; i < pts.size(); i++) {
      Point2D point = pts.get(i);
      coordinates[i * 2] = point.getX();
      coordinates[i * 2 + 1] = point.getY();
    }
    return coordinates;
  }

  private static void buildGraphicLabel(Graphic graphic, Sequence textSeq) {
    if (!graphic.getLabelVisible()) {
      return;
    }
    GraphicLabel label = graphic.getGraphicLabel();
    if (label == null) {
      return;
    }

    Rectangle2D bounds = label.getTransformedBounds(null);
    String text = String.join(CARRIAGE_RETURN_LINE_FEED, label.getLabels());

    Attributes textAttributes = createLabelAttributes(bounds, text);
    textSeq.add(textAttributes);
  }

  private static Attributes createAnnotationAttributes(AnnotationGraphic graphic) {
    Rectangle2D bounds = graphic.getLabelBounds();
    Point2D anchor = graphic.getAnchorPoint();
    String text = String.join(CARRIAGE_RETURN_LINE_FEED, graphic.getLabels());

    Attributes attributes = new Attributes(7);
    attributes.setString(Tag.BoundingBoxAnnotationUnits, VR.CS, PIXEL);
    attributes.setFloat(Tag.AnchorPoint, VR.FL, (float) anchor.getX(), (float) anchor.getY());
    attributes.setString(Tag.AnchorPointVisibility, VR.CS, YES);
    attributes.setString(Tag.AnchorPointAnnotationUnits, VR.CS, PIXEL);
    attributes.setDouble(
        Tag.BoundingBoxTopLeftHandCorner, VR.FL, bounds.getMinX(), bounds.getMinY());
    attributes.setDouble(
        Tag.BoundingBoxBottomRightHandCorner, VR.FL, bounds.getMaxX(), bounds.getMaxY());
    attributes.setString(Tag.UnformattedTextValue, VR.ST, text);
    attributes.setString(Tag.BoundingBoxTextHorizontalJustification, VR.CS, "LEFT");

    int[] labColor = getLabColor(graphic);
    addLineStyleSequence(attributes, graphic, labColor);

    if (graphic.getFilled()) {
      addFillStyleSequence(attributes, graphic.getFillOpacity(), labColor);
    }
    return attributes;
  }

  private static Attributes createLabelAttributes(Rectangle2D bounds, String text) {
    Attributes attributes = new Attributes(4);
    attributes.setString(Tag.BoundingBoxAnnotationUnits, VR.CS, PIXEL);
    attributes.setDouble(
        Tag.BoundingBoxTopLeftHandCorner, VR.FL, bounds.getMinX(), bounds.getMinY());
    attributes.setDouble(
        Tag.BoundingBoxBottomRightHandCorner, VR.FL, bounds.getMaxX(), bounds.getMaxY());
    attributes.setString(Tag.BoundingBoxTextHorizontalJustification, VR.CS, "LEFT");
    attributes.setString(Tag.UnformattedTextValue, VR.ST, text);
    return attributes;
  }

  public static void transformShapeToContour(Graphic graphic, Sequence graphicSeq) {
    Shape shape = graphic.getShape();
    PathIterator iterator =
        new FlatteningPathIterator(shape.getPathIterator(null), FLATTENING_TOLERANCE);

    Attributes currentGraphic = null;
    List<Point2D> currentPoints = new ArrayList<>();
    double[] coords = new double[6];

    while (!iterator.isDone()) {
      int segmentType = iterator.currentSegment(coords);

      switch (segmentType) {
        case PathIterator.SEG_MOVETO -> {
          finalizePreviousGraphic(currentGraphic, graphicSeq, currentPoints);
          currentGraphic = getBasicGraphicStructure(graphic);
          currentPoints.clear();
          currentPoints.add(new Point2D.Double(coords[0], coords[1]));
        }
        case PathIterator.SEG_LINETO, PathIterator.SEG_CLOSE ->
            currentPoints.add(new Point2D.Double(coords[0], coords[1]));
        case PathIterator.SEG_CUBICTO, PathIterator.SEG_QUADTO ->
            throw new IllegalStateException("Cubic and quadratic segments should be flattened");
        default -> LOGGER.warn("Unsupported path segment type: {}", segmentType);
      }
      iterator.next();
    }
    finalizePreviousGraphic(currentGraphic, graphicSeq, currentPoints);
  }

  private static void finalizePreviousGraphic(
      Attributes attributes, Sequence graphicSeq, List<Point2D> points) {
    if (attributes != null && !points.isEmpty()) {
      attributes.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.POLYLINE);
      attributes.setDouble(Tag.GraphicData, VR.FL, getGraphicsPoints(points));
      attributes.setInt(Tag.NumberOfGraphicPoints, VR.US, points.size());
      graphicSeq.add(attributes);
    }
  }

  private static boolean saveToFile(File output, Attributes dataset) {
    if (dataset == null) {
      LOGGER.error("Cannot save null DICOM attributes");
      return false;
    }
    try (DicomOutputStream out = new DicomOutputStream(output)) {
      out.writeDataset(dataset.createFileMetaInformation(UID.ImplicitVRLittleEndian), dataset);
      LOGGER.debug("Successfully saved DICOM PR to: {}", output.getAbsolutePath());
      return true;
    } catch (IOException e) {
      LOGGER.error("Cannot write DICOM PR to file: {}", output.getAbsolutePath(), e);
      return false;
    }
  }

  /** Helper class to encapsulate graphic type information. */
  private record GraphicTypeInfo(
      String graphicType, List<Point2D> points, boolean requiresShapeTransformation) {}
}
