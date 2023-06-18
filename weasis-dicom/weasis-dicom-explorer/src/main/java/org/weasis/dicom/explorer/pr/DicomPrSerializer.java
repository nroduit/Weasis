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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.data.CIELab;
import org.dcm4che3.io.DicomOutputStream;
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
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class DicomPrSerializer {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomPrSerializer.class);

  private static final String PIXEL = "PIXEL";

  private DicomPrSerializer() {}

  public static Attributes writePresentation(
      GraphicModel model,
      Attributes parentAttributes,
      File outputFile,
      String seriesInstanceUID,
      String sopInstanceUID) {
    return writePresentation(
        model, parentAttributes, outputFile, seriesInstanceUID, sopInstanceUID, null);
  }

  public static Attributes writePresentation(
      GraphicModel model,
      Attributes parentAttributes,
      File outputFile,
      String seriesInstanceUID,
      String sopInstanceUID,
      Point2D offset) {
    Objects.requireNonNull(model);
    Objects.requireNonNull(outputFile);

    if (parentAttributes != null) {
      try {
        GraphicModel m = getModelForSerialization(model, offset);
        Attributes attributes =
            DicomMediaUtils.createDicomPR(parentAttributes, seriesInstanceUID, sopInstanceUID);

        writeCommonTags(attributes);
        writeReferences(attributes, m, parentAttributes.getString(Tag.SOPClassUID));
        writeGraphics(m, attributes);
        writePrivateTags(m, attributes);

        saveToFile(outputFile, attributes);
        return attributes;
      } catch (Exception e) {
        LOGGER.error("Cannot write Presentation State : ", e);
      }
    }
    return null;
  }

  public static Attributes writePresentation(
      GraphicModel model,
      DicomImageElement img,
      File outputFile,
      String seriesInstanceUID,
      String sopInstanceUID) {
    Attributes imgAttributes =
        img.getMediaReader() instanceof DcmMediaReader
            ? img.getMediaReader().getDicomObject()
            : null;
    return writePresentation(
        model, imgAttributes, outputFile, seriesInstanceUID, sopInstanceUID, null);
  }

  public static GraphicModel getModelForSerialization(GraphicModel model, Point2D offset) {
    // Remove non-serializable graphics
    XmlGraphicModel xmlModel = new XmlGraphicModel();
    xmlModel.setReferencedSeries(model.getReferencedSeries());
    for (Graphic g : model.getModels()) {
      if (g.getLayer().getSerializable() && !g.getPts().isEmpty()) {
        if (offset != null) {
          Graphic graphic = g.copy();
          for (Point2D p : graphic.getPts()) {
            p.setLocation(p.getX() - offset.getX(), p.getY() - offset.getY());
          }
          GraphicLabel label = g.getGraphicLabel();
          graphic.buildShape();
          if (label != null) {
            graphic.setLabel(label);
          }
          xmlModel.addGraphic(graphic);
        } else {
          xmlModel.addGraphic(g);
        }
      }
    }
    return xmlModel;
  }

  private static void writePrivateTags(GraphicModel model, Attributes attributes) {
    try {
      JAXBContext jaxbContext = XmlSerializer.getJaxbContext(model.getClass());
      Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      jaxbMarshaller.marshal(model, outputStream);
      // jaxbMarshaller.marshal(model, System.out);
      attributes.setString(
          PresentationStateReader.PRIVATE_CREATOR_TAG, VR.LO, PresentationStateReader.PR_MODEL_ID);
      attributes.setBytes(
          PresentationStateReader.PR_MODEL_PRIVATE_TAG,
          VR.OB,
          GzipManager.gzipCompressToByte(outputStream.toByteArray()));
    } catch (Exception | NoClassDefFoundError e) {
      LOGGER.error("Cannot save xml: ", e);
    }
  }

  private static void writeCommonTags(Attributes attributes) {
    String gsps = "GSPS";
    attributes.setString(Tag.ContentCreatorName, VR.PN, AppProperties.WEASIS_USER);
    attributes.setString(Tag.ContentLabel, VR.CS, gsps);
    attributes.setString(Tag.ContentDescription, VR.LO, "Description"); // NON-NLS
    attributes.setInt(Tag.SeriesNumber, VR.IS, 999);
    try {
      attributes.setString(Tag.StationName, VR.SH, InetAddress.getLocalHost().getHostName());
    } catch (UnknownHostException e) {
      LOGGER.error("Cannot get host name: ", e);
    }
    attributes.setString(Tag.SoftwareVersions, VR.LO, AppProperties.WEASIS_VERSION);
    attributes.setString(
        Tag.SeriesDescription, VR.LO, String.join(" ", AppProperties.WEASIS_NAME, gsps));
  }

  private static void writeReferences(
      Attributes attributes, GraphicModel model, String sopClassUID) {
    Sequence seriesSeq =
        attributes.newSequence(Tag.ReferencedSeriesSequence, model.getReferencedSeries().size());
    for (ReferencedSeries seriesRef : model.getReferencedSeries()) {
      Attributes rfs = new Attributes(2);
      rfs.setString(Tag.SeriesInstanceUID, VR.UI, seriesRef.getUuid());

      Sequence imageSeq =
          rfs.newSequence(Tag.ReferencedImageSequence, seriesRef.getImages().size());
      for (ReferencedImage imageRef : seriesRef.getImages()) {
        Attributes rfi = new Attributes(2);
        rfi.setString(Tag.ReferencedSOPClassUID, VR.UI, sopClassUID);
        rfi.setString(Tag.ReferencedSOPInstanceUID, VR.UI, imageRef.getUuid());

        List<Integer> frames = imageRef.getFrames();
        if (frames != null && !frames.isEmpty()) {
          // convert to DICOM frame
          rfi.setInt(
              Tag.ReferencedFrameNumber, VR.IS, frames.stream().mapToInt(i -> i + 1).toArray());
        }

        imageSeq.add(rfi);
      }
      seriesSeq.add(rfs);
    }
  }

  private static void writeGraphics(GraphicModel model, Attributes attributes) {
    List<GraphicLayer> layers = model.getLayers();

    Sequence annotationSeq = attributes.newSequence(Tag.GraphicAnnotationSequence, layers.size());
    Sequence layerSeq = attributes.newSequence(Tag.GraphicLayerSequence, layers.size());
    for (int i = 0; i < layers.size(); i++) {
      GraphicLayer layer = layers.get(i);

      if (layer.getSerializable()) {
        String layerName = layer.getType().name();
        List<Graphic> graphics = getGraphicsByLayer(model, layer.getUuid());

        Attributes l = new Attributes(2);
        l.setString(Tag.GraphicLayer, VR.CS, layerName);
        l.setInt(Tag.GraphicLayerOrder, VR.IS, i);
        int[] lab =
            CIELab.rgbToDicomLab(
                Optional.ofNullable(MeasureTool.viewSetting.getLineColor()).orElse(Color.YELLOW));
        if (lab != null) {
          l.setInt(Tag.GraphicLayerRecommendedDisplayCIELabValue, VR.US, lab);
        }
        l.setString(Tag.GraphicLayerDescription, VR.LO, layer.toString());
        layerSeq.add(l);

        Attributes a = new Attributes(2);
        a.setString(Tag.GraphicLayer, VR.CS, layerName);
        Sequence graphicSeq = a.newSequence(Tag.GraphicObjectSequence, graphics.size());
        Sequence textSeq = a.newSequence(Tag.TextObjectSequence, graphics.size());

        for (Graphic graphic : graphics) {
          buildDicomGraphic(graphic, graphicSeq, textSeq);
        }
        annotationSeq.add(a);
      }
    }
  }

  private static List<Graphic> getGraphicsByLayer(GraphicModel model, String layerUid) {
    return model.getModels().stream()
        .filter(g -> layerUid.equals(g.getLayer().getUuid()))
        .collect(Collectors.toList());
  }

  private static double[] getGraphicsPoints(List<Point2D> pts) {
    double[] list = new double[pts.size() * 2];
    for (int i = 0; i < pts.size(); i++) {
      Point2D p = pts.get(i);
      list[i * 2] = p.getX();
      list[i * 2 + 1] = p.getY();
    }
    return list;
  }

  private static Attributes getBasicGraphic(Graphic graphic) {
    Attributes dcm = new Attributes(5);
    dcm.setString(Tag.GraphicAnnotationUnits, VR.CS, PIXEL);
    dcm.setInt(Tag.GraphicDimensions, VR.US, 2);
    dcm.setString(Tag.GraphicFilled, VR.CS, graphic.getFilled() ? "Y" : "N"); // NON-NLS

    Sequence style = dcm.newSequence(Tag.LineStyleSequence, 1);
    Attributes styles = new Attributes();
    styles.setFloat(Tag.LineThickness, VR.FL, graphic.getLineThickness());

    if (graphic.getColorPaint() instanceof Color) {
      Color color = (Color) graphic.getColorPaint();
      styles.setInt(Tag.PatternOnColorCIELabValue, VR.US, CIELab.rgbToDicomLab(color));
    }
    style.add(styles);
    return dcm;
  }

  private static void buildDicomGraphic(Graphic graphic, Sequence graphicSeq, Sequence textSeq) {
    Attributes dcm = getBasicGraphic(graphic);
    List<Point2D> pts;

    if (graphic instanceof ObliqueRectangleGraphic) {
      boolean ellipse = graphic instanceof EllipseGraphic;
      dcm.setString(
          Tag.GraphicType, VR.CS, ellipse ? PrGraphicUtil.ELLIPSE : PrGraphicUtil.POLYLINE);
      pts = ((ObliqueRectangleGraphic) graphic).getRectanglePointList();
    } else if (graphic instanceof ThreePointsCircleGraphic) {
      dcm.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.CIRCLE);
      Point2D centerPt = GeomUtil.getCircleCenter(graphic.getPts());
      pts = Arrays.asList(centerPt, graphic.getPts().get(0));
    } else if (graphic instanceof PolygonGraphic) {
      dcm.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.POLYLINE);
      pts = graphic.getPts();
      pts.add(pts.get(0));
    } else if (graphic instanceof PolylineGraphic) {
      dcm.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.POLYLINE);
      pts = graphic.getPts();
    } else if (graphic instanceof PointGraphic) {
      dcm.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.POINT);
      pts = Collections.singletonList(graphic.getPts().get(0));
    } else if (graphic instanceof AnnotationGraphic) {
      AnnotationGraphic g = (AnnotationGraphic) graphic;
      Attributes attributes = bluildLabelAndAnchor(g);
      textSeq.add(attributes);
      return;
    } else {
      transformShapeToContour(graphic, graphicSeq);
      bluildLabel(graphic, textSeq);
      return;
    }

    dcm.setDouble(Tag.GraphicData, VR.FL, getGraphicsPoints(pts));
    dcm.setInt(Tag.NumberOfGraphicPoints, VR.US, pts.size());
    graphicSeq.add(dcm);

    bluildLabel(graphic, textSeq);
  }

  private static void bluildLabel(Graphic graphic, Sequence textSeq) {
    if (graphic.getLabelVisible()) {
      GraphicLabel label = graphic.getGraphicLabel();
      if (label != null) {
        Rectangle2D bound = label.getTransformedBounds(null);

        Attributes text =
            bluildLabelAndBounds(
                bound, Arrays.stream(label.getLabels()).collect(Collectors.joining("\r\n")));
        textSeq.add(text);
      }
    }
  }

  private static Attributes bluildLabelAndAnchor(AnnotationGraphic g) {
    Rectangle2D bound = g.getLabelBounds();
    Point2D anchor = g.getAnchorPoint();
    String text = Arrays.stream(g.getLabels()).collect(Collectors.joining("\r\n"));

    Attributes attributes = new Attributes(7);
    attributes.setString(Tag.BoundingBoxAnnotationUnits, VR.CS, PIXEL);
    attributes.setFloat(Tag.AnchorPoint, VR.FL, (float) anchor.getX(), (float) anchor.getY());
    attributes.setString(Tag.AnchorPointVisibility, VR.CS, "Y"); // NON-NLS
    Sequence style = attributes.newSequence(Tag.LineStyleSequence, 1);
    Attributes styles = new Attributes();
    styles.setFloat(Tag.LineThickness, VR.FL, g.getLineThickness());

    if (g.getColorPaint() instanceof Color color) {
      int[] rgb = CIELab.rgbToDicomLab(color);
      styles.setInt(Tag.PatternOnColorCIELabValue, VR.US, rgb);
    }
    style.add(styles);
    attributes.setDouble(Tag.BoundingBoxTopLeftHandCorner, VR.FL, bound.getMinX(), bound.getMinY());
    attributes.setDouble(
        Tag.BoundingBoxBottomRightHandCorner, VR.FL, bound.getMaxX(), bound.getMaxY());
    // In text strings (value representation ST, LT, or UT) a new line shall be represented as CR
    // LF.
    // see http://dicom.nema.org/medical/dicom/current/output/chtml/part05/chapter_6.html
    attributes.setString(Tag.UnformattedTextValue, VR.ST, text);
    return attributes;
  }

  private static Attributes bluildLabelAndBounds(Rectangle2D bound, String text) {
    Attributes attributes = new Attributes(5);
    attributes.setString(Tag.BoundingBoxAnnotationUnits, VR.CS, PIXEL);
    attributes.setDouble(Tag.BoundingBoxTopLeftHandCorner, VR.FL, bound.getMinX(), bound.getMinY());
    attributes.setDouble(
        Tag.BoundingBoxBottomRightHandCorner, VR.FL, bound.getMaxX(), bound.getMaxY());

    // In text strings (value representation ST, LT, or UT) a new line shall be represented as CR
    // LF.
    // see http://dicom.nema.org/medical/dicom/current/output/chtml/part05/chapter_6.html
    attributes.setString(Tag.UnformattedTextValue, VR.ST, text);
    return attributes;
  }

  public static void transformShapeToContour(Graphic graphic, Sequence graphicSeq) {

    Shape shape = graphic.getShape();

    Attributes dcm = null;
    List<Point2D> points = new ArrayList<>();

    PathIterator iterator = new FlatteningPathIterator(shape.getPathIterator(null), 2);
    double[] pts = new double[6];
    while (!iterator.isDone()) {
      int segType = iterator.currentSegment(pts);
      switch (segType) {
        case PathIterator.SEG_MOVETO -> {
          addNewSubGraphic(dcm, graphicSeq, points);
          dcm = getBasicGraphic(graphic);
          points.add(new Point2D.Double(pts[0], pts[1]));
        }
        case PathIterator.SEG_LINETO, PathIterator.SEG_CLOSE -> points.add(
            new Point2D.Double(pts[0], pts[1]));
        case PathIterator.SEG_CUBICTO, PathIterator.SEG_QUADTO ->
        // should never append with FlatteningPathIterator
        throw new IllegalStateException("cubic iterator is not supported");
      }
      iterator.next();
    }
    addNewSubGraphic(dcm, graphicSeq, points);
  }

  private static void addNewSubGraphic(Attributes dcm, Sequence graphicSeq, List<Point2D> points) {
    if (dcm != null && dcm.getParent() == null) {
      dcm.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.POLYLINE);
      dcm.setDouble(Tag.GraphicData, VR.FL, getGraphicsPoints(points));
      dcm.setInt(Tag.NumberOfGraphicPoints, VR.US, points.size());
      graphicSeq.add(dcm);
      points.clear();
    }
  }

  private static boolean saveToFile(File output, Attributes dcm) {
    if (dcm != null) {
      try (DicomOutputStream out = new DicomOutputStream(output)) {
        out.writeDataset(dcm.createFileMetaInformation(UID.ImplicitVRLittleEndian), dcm);
        return true;
      } catch (IOException e) {
        LOGGER.error("Cannot write dicom PR into {}", output, e);
      }
    }
    return false;
  }
}
