/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.sr;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.pf4j.Extension;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicsExporter;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.pr.DicomPrSerializer;
import org.weasis.dicom.explorer.pr.PrGraphicUtil;
import org.weasis.dicom.explorer.sr.MeasurementReport.ImageRef;
import org.weasis.dicom.explorer.sr.MeasurementReport.Region;
import org.weasis.dicom.explorer.sr.MeasurementReport.Value;

/**
 * The DICOM formats of {@code measure:export}: {@code sr}, a TID 1500 Measurement Report with one
 * planar ROI group per graphic and its values as NUM items; {@code pr}, a Grayscale Softcopy
 * Presentation State carrying the graphics of the image as a reader of GSPS shows them.
 */
public final class DicomGraphicsExporters {
  private DicomGraphicsExporters() {}

  static DicomImageElement dicomImage(ViewCanvas<?> view) {
    if (view.getImage() instanceof DicomImageElement img) {
      return img;
    }
    throw new IllegalStateException("The pane shows no DICOM image");
  }

  /** The measurement report of the graphics of the pane. */
  @Extension
  public static final class Sr implements GraphicsExporter {
    @Override
    public String format() {
      return "sr"; // NON-NLS
    }

    @Override
    public String description() {
      return "A DICOM Structured Report, TID 1500 Measurement Report, one planar ROI per graphic";
    }

    @Override
    public Written export(ViewCanvas<?> view, List<Graphic> graphics, Path file)
        throws IOException {
      DicomImageElement img = dicomImage(view);
      Attributes source =
          img.getMediaReader() == null ? new Attributes() : img.getMediaReader().getDicomObject();
      ImageRef image =
          new ImageRef(
              TagD.getTagValue(img, Tag.StudyInstanceUID, String.class),
              TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class),
              TagD.getTagValue(img, Tag.SOPClassUID, String.class),
              TagD.getTagValue(img, Tag.SOPInstanceUID, String.class),
              null,
              false);
      Unit spatial = img.getPixelSpacingUnit() == null ? Unit.PIXEL : img.getPixelSpacingUnit();
      MeasurableLayer measurable = view.getMeasurableLayer();
      List<Region> regions = new ArrayList<>();
      int skipped = 0;
      for (Graphic graphic : graphics) {
        Region region = region(graphic, measurable, spatial);
        if (region == null) {
          skipped++;
        } else {
          regions.add(region);
        }
      }
      if (regions.isEmpty()) {
        throw new IllegalStateException("No graphic of the pane can be written as a region");
      }
      Attributes report = MeasurementReport.build(source, image, regions, "Weasis measurements"); // NON-NLS
      MeasurementReport.write(report, file);
      return new Written(
          file,
          regions.size(),
          skipped,
          Map.of("sopInstanceUID", report.getString(Tag.SOPInstanceUID))); // NON-NLS
    }

    private static Region region(Graphic graphic, MeasurableLayer measurable, Unit spatial) {
      DicomPrSerializer.GraphicTypeInfo info = DicomPrSerializer.getGraphicTypeInfo(graphic);
      String type = info.graphicType();
      List<Point2D> points = info.points();
      if (info.requiresShapeTransformation()) {
        points = flatten(graphic);
        type = PrGraphicUtil.POLYLINE;
      }
      if (type == null || points == null || points.isEmpty()) {
        return null;
      }
      List<MeasureItem> items =
          Measurement.computingAll(() -> graphic.computeMeasurements(measurable, true, spatial));
      List<Value> values = new ArrayList<>();
      for (MeasureItem item : items) {
        if (item.getValue() instanceof Number n) {
          MeasurementReport.value(
                  item.getMeasurement().getKind(),
                  item.getMeasurement().getKey(),
                  n.doubleValue(),
                  spatial)
              .ifPresent(values::add);
        }
      }
      String label = graphic.getUIName() == null ? graphic.getUuid() : graphic.getUIName();
      return new Region(label, label, type, DicomPrSerializer.getGraphicsPoints(points), values);
    }

    private static List<Point2D> flatten(Graphic graphic) {
      List<Point2D> points = new ArrayList<>();
      java.awt.geom.PathIterator it = graphic.getShape().getPathIterator(null, 0.5);
      double[] c = new double[6];
      while (!it.isDone()) {
        int kind = it.currentSegment(c);
        if (kind == java.awt.geom.PathIterator.SEG_MOVETO
            || kind == java.awt.geom.PathIterator.SEG_LINETO) {
          points.add(new Point2D.Double(c[0], c[1]));
        }
        it.next();
      }
      return points;
    }
  }

  /** The presentation state of the image, with every graphic of its model. */
  @Extension
  public static final class Pr implements GraphicsExporter {
    @Override
    public String format() {
      return "pr"; // NON-NLS
    }

    @Override
    public String description() {
      return "A DICOM Grayscale Softcopy Presentation State with every graphic of the image";
    }

    @Override
    public Written export(ViewCanvas<?> view, List<Graphic> graphics, Path file) {
      DicomImageElement img = dicomImage(view);
      GraphicModel model = view.getGraphicManager();
      Attributes pr =
          DicomPrSerializer.writePresentation(
              model, img, file.toFile(), UIDUtils.createUID(), UIDUtils.createUID());
      if (pr == null) {
        throw new IllegalStateException("The presentation state could not be written");
      }
      int count =
          (int)
              model.getAllGraphics().stream()
                  .filter(g -> g.getLayer() != null && g.getLayer().getType() != LayerType.DICOM_PR)
                  .count();
      return new Written(
          file, count, 0, Map.of("sopInstanceUID", pr.getString(Tag.SOPInstanceUID))); // NON-NLS
    }
  }
}
