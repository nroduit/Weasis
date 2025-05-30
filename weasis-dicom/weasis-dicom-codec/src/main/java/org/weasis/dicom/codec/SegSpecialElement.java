/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.awt.Color;
import java.awt.Point;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.CIELab;
import org.dcm4che3.img.util.DicomUtils;
import org.joml.Vector3d;
import org.opencv.core.Core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegMeasurableLayer;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.ui.util.StructToolTipTreeNode;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.geometry.VectorUtils;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.macro.Code;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.seg.Region;
import org.weasis.opencv.seg.RegionAttributes;
import org.weasis.opencv.seg.Segment;

public class SegSpecialElement extends HiddenSpecialElement
    implements SpecialElementReferences, SpecialElementRegion {
  private static final Logger LOGGER = LoggerFactory.getLogger(SegSpecialElement.class);
  static final DecimalFormat roundDouble = new DecimalFormat("0.######");

  static {
    roundDouble.setRoundingMode(RoundingMode.HALF_UP);
    roundDouble.setGroupingUsed(false);
  }

  private final Map<String, Map<String, Set<SegContour>>> refMap = new HashMap<>();
  private final Map<Integer, Set<SegContour>> roiMap = new HashMap<>();
  private final Map<String, Set<SegContour>> postitionMap = new HashMap<>();
  private final Map<Integer, SegRegion<DicomImageElement>> segAttributes = new HashMap<>();

  private volatile float opacity = 1.0f;
  private volatile boolean visible = true;

  public SegSpecialElement(DicomMediaIO mediaIO) {
    super(mediaIO);
  }

  public static DefaultMutableTreeNode buildStructRegionNode(SegRegion<?> contour) {
    return new StructToolTipTreeNode(contour, false) {
      @Override
      public String getToolTipText() {
        SegRegion<?> seg = (SegRegion<?>) getUserObject();
        StringBuilder buf = new StringBuilder();
        buf.append(GuiUtils.HTML_START);
        buf.append("<b>");
        buf.append(seg.getLabel());
        buf.append("</b>");
        buf.append(GuiUtils.HTML_BR);
        buf.append("Algorithm type");
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(seg.getType());
        buf.append(GuiUtils.HTML_BR);
        String algoName = seg.getAlgorithmName();
        if (StringUtil.hasText(algoName)) {
          buf.append("Algorithm name");
          buf.append(StringUtil.COLON_AND_SPACE);
          buf.append(algoName);
          buf.append(GuiUtils.HTML_BR);
        }
        List<String> categories = seg.getCategories();
        if (categories != null && !categories.isEmpty()) {
          buf.append("Categories");
          buf.append(StringUtil.COLON_AND_SPACE);
          buf.append(String.join(", ", categories));
          buf.append(GuiUtils.HTML_BR);
        }
        List<String> anatomicRegionCodes = seg.getAnatomicRegionCodes();
        if (anatomicRegionCodes != null && !anatomicRegionCodes.isEmpty()) {
          buf.append("Anatomic regions");
          buf.append(StringUtil.COLON_AND_SPACE);
          buf.append(String.join(", ", anatomicRegionCodes));
          buf.append(GuiUtils.HTML_BR);
        }
        buf.append("Voxel count");
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(DecFormatter.allNumber(seg.getNumberOfPixels()));
        buf.append(GuiUtils.HTML_BR);
        SegMeasurableLayer<?> layer = seg.getMeasurableLayer();
        if (layer != null) {
          MeasurementsAdapter adapter =
              layer.getMeasurementAdapter(layer.getSourceImage().getPixelSpacingUnit());
          buf.append("Volume (%s3)".formatted(adapter.getUnit()));
          buf.append(StringUtil.COLON_AND_SPACE);
          double ratio = adapter.getCalibRatio();
          buf.append(
              DecFormatter.twoDecimal(
                  seg.getNumberOfPixels() * ratio * ratio * layer.getThickness()));
          buf.append(GuiUtils.HTML_BR);
        }
        buf.append(GuiUtils.HTML_END);
        return buf.toString();
      }
    };
  }

  @Override
  protected void initLabel() {
    StringBuilder buf = new StringBuilder();
    Integer val = TagD.getTagValue(this, Tag.InstanceNumber, Integer.class);
    if (val != null) {
      buf.append("[");
      buf.append(val);
      buf.append("] ");
    }
    Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
    String item = dicom.getString(Tag.ContentLabel);
    if (item != null) {
      buf.append(item);
    }
    item = dicom.getString(Tag.ContentDescription);
    if (item != null) {
      buf.append(" - ");
      buf.append(item);
    }
    label = buf.toString();
  }

  public Map<Integer, Set<SegContour>> getRoiMap() {
    return roiMap;
  }

  public Map<String, Map<String, Set<SegContour>>> getRefMap() {
    return refMap;
  }

  @Override
  public Map<String, Set<SegContour>> getPositionMap() {
    return postitionMap;
  }

  public Map<Integer, SegRegion<DicomImageElement>> getSegAttributes() {
    return segAttributes;
  }

  @Override
  public boolean isVisible() {
    return visible;
  }

  @Override
  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  @Override
  public float getOpacity() {
    return opacity;
  }

  @Override
  public void setOpacity(float opacity) {
    this.opacity = Math.max(0.0f, Math.min(opacity, 1.0f));
    updateOpacityInSegAttributes(this.opacity);
  }

  @Override
  public void initReferences(String originSeriesUID) {
    refMap.clear();
    Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
    if (dicom != null) {
      Function<String, Map<String, Set<SegContour>>> addSeries =
          seriesUID -> refMap.computeIfAbsent(seriesUID, _ -> new HashMap<>());
      HiddenSeriesManager.getInstance().extractReferencedSeries(dicom, originSeriesUID, addSeries);
    }
  }

  @Override
  public ResourceIconPath getIconPath() {
    return OtherIcon.SEGMENTATION;
  }

  public void initContours(DicomSeries series, List<DicomSeries> refSeriesList) {
    roiMap.clear();

    Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
    String segmentType = dicom.getString(Tag.SegmentationType);
    if ("FRACTIONAL".equals(segmentType)) {
      // TODO: handle fractional segmentations
    }

    // Locate the name and number of each ROI
    Map<SegRegion<DicomImageElement>, Point> regionPosition = new HashMap<>();
    Sequence segSeq = dicom.getSequence(Tag.SegmentSequence);

    if (segSeq != null && series != null) {
      for (Attributes seg : segSeq) {
        int nb = seg.getInt(Tag.SegmentNumber, -1);
        String segmentLabel = seg.getString(Tag.SegmentLabel, "" + nb);

        int[] colorRgb =
            CIELab.dicomLab2rgb(
                DicomUtils.getIntArrayFromDicomElement(
                    seg, Tag.RecommendedDisplayCIELabValue, null));
        Color rgbColor = RegionAttributes.getColor(colorRgb, nb, opacity);

        SegRegion<DicomImageElement> attributes = new SegRegion<>(nb, segmentLabel, rgbColor);
        attributes.setInteriorOpacity(0.2f);
        attributes.setDescription(seg.getString(Tag.SegmentDescription));
        attributes.setType(seg.getString(Tag.SegmentAlgorithmType));
        attributes.setAlgorithmName(seg.getString(Tag.SegmentAlgorithmName));

        Sequence regionSeq = seg.getSequence(Tag.AnatomicRegionSequence);
        if (regionSeq != null) {
          attributes.setAnatomicRegionCodes(
              Code.toCodeMacros(regionSeq).stream().map(Code::getCodeMeaning).toList());
        }

        Sequence categorySeq = seg.getSequence(Tag.SegmentedPropertyCategoryCodeSequence);
        if (categorySeq != null) {
          attributes.setCategories(
              Code.toCodeMacros(categorySeq).stream().map(Code::getCodeMeaning).toList());
        }

        segAttributes.put(nb, attributes);
        regionPosition.put(attributes, new Point(-1, -1));
      }
    }

    Sequence perFrameSeq = dicom.getSequence(Tag.PerFrameFunctionalGroupsSequence);
    if (perFrameSeq != null) {
      if (perFrameSeq.size() > 1000) {
        // TODO reimplement in a more dynamic way instead of loading all contours in memory
        LOGGER.warn(
            "Segmentation contains more than 400 frames, skipping because of performance issues");
        return;
      }

      processPerFrameSequence(series, refSeriesList, regionPosition, perFrameSeq);
    }

    // Calculate Measurable Layers for Regions
    regionPosition.forEach(
        (region, p) -> {
          SegMeasurableLayer<DicomImageElement> measurableLayer = getMeasurableLayer(series, p);
          region.setMeasurableLayer(measurableLayer);
        });
  }

  private void processPerFrameSequence(
      DicomSeries series,
      List<DicomSeries> refSeriesList,
      Map<SegRegion<DicomImageElement>, Point> regionPosition,
      Sequence perFrameSeq) {
    int index = 0;
    for (Attributes frame : perFrameSeq) {
      index++;
      // Map SOPInstanceUID to its associated frames (including ReferencedFrameNumber)
      Map<String, List<Integer>> sopUIDToFramesMap = new ConcurrentHashMap<>();
      Sequence derivationSeq = frame.getSequence(Tag.DerivationImageSequence);
      if (derivationSeq != null) {
        for (Attributes derivation : derivationSeq) {
          HiddenSeriesManager.addSourceImage(derivation, sopUIDToFramesMap);
        }
      }

      DicomImageElement binaryMask = series.getMedia(index - 1, null, null);
      if (binaryMask != null) {
        Attributes refSeqNb = frame.getNestedDataset(Tag.SegmentIdentificationSequence);
        if (refSeqNb != null) {
          Integer segmentNumber =
              DicomUtils.getIntegerFromDicomElement(refSeqNb, Tag.ReferencedSegmentNumber, null);
          if (segmentNumber != null) {
            SegRegion<?> region = segAttributes.get(segmentNumber);
            if (region != null) {
              buildGraphic(binaryMask, index, region);
              Point p = regionPosition.get(region);
              if (p != null) {
                if (p.x == -1) {
                  p.x = index - 1;
                } else {
                  p.y = index - 1;
                }
              }
            }
          }
        }
      }

      Set<SegContour> contour = roiMap.get(index);
      if (contour != null && !contour.isEmpty()) {
        associateContours(refSeriesList, sopUIDToFramesMap, frame, contour, index);
      }
    }
  }

  private void associateContours(
      List<DicomSeries> refSeriesList,
      Map<String, List<Integer>> sopUIDToFramesMap,
      Attributes frame,
      Set<SegContour> contours,
      int index) {

    // If the map is empty, attempt to populate `sopUIDToFramesMap` from the reference series list
    if (sopUIDToFramesMap.isEmpty() && !refSeriesList.isEmpty()) {
      Attributes planePosition = frame.getNestedDataset(Tag.PlanePositionSequence);
      if (planePosition != null) {
        double[] imagePositionPatient = planePosition.getDoubles(Tag.ImagePositionPatient);
        if (imagePositionPatient != null) {
          refSeriesList.forEach(
              refSeries -> {
                for (DicomImageElement dcm : refSeries.getMedias(null, null)) {
                  double[] imagePosition =
                      TagD.getTagValue(dcm, Tag.ImagePositionPatient, double[].class);
                  if (isWithinTolerance(imagePosition, imagePositionPatient, 0.01)) {
                    String sopUID = TagD.getTagValue(dcm, Tag.SOPInstanceUID, String.class);
                    if (sopUID != null) {
                      int frames = dcm.getMediaReader().getMediaElementNumber();
                      if (frames > 1 && dcm.getKey() instanceof Integer intVal) {
                        List<Integer> frameList = sopUIDToFramesMap.computeIfAbsent(sopUID, _ -> new ArrayList<>());
                        if (!frameList.contains(intVal)) {
                          frameList.add(intVal);
                        }
                      } else {
                        sopUIDToFramesMap.putIfAbsent(sopUID, Collections.emptyList());
                      }
                    }
                  }
                }
              });
        }
      }
    }

    if (sopUIDToFramesMap.isEmpty()) {
      // If no SOPInstanceUIDs can be determined, map the index to an empty contour set
      roiMap.put(index, new LinkedHashSet<>());
    } else {
      refMap.forEach(
          (key, _) -> {
            Map<String, Set<SegContour>> map = refMap.get(key);
            if (map != null) {
              sopUIDToFramesMap.forEach(
                  (sopUID, frames) -> {
                    Set<SegContour> list = map.get(sopUID);
                    if (list != null) {
                      if (frames.isEmpty()) {
                        // If no frames are specified, add all contours
                        list.addAll(contours);
                      } else {
                        frames.forEach(frameNumber -> {
                          // Assuming frame-specific key mapping logic is required
                          String frameSpecificKey = sopUID + "_" + frameNumber;
                          map.computeIfAbsent(frameSpecificKey, _ -> new LinkedHashSet<>())
                              .addAll(contours);
                        });
                      }
                    }
                  });
            }
          });
    }
  }

  private static boolean isWithinTolerance(double[] array1, double[] array2, double tolerance) {
    if (array1 == null || array2 == null || array1.length != array2.length) {
      return false;
    }
    for (int i = 0; i < array1.length; i++) {
      if (Math.abs(array1[i] - array2[i]) > tolerance) {
        return false;
      }
    }
    return true;
  }

  private void addPositionMap(Attributes frame, Set<SegContour> contour) {
    Attributes refPos = frame.getNestedDataset(Tag.PlanePositionSequence);
    if (refPos != null) {
      double[] pos =
          DicomUtils.getDoubleArrayFromDicomElement(
              refPos,
              Tag.ImagePositionPatient,
              TagD.getTagValue(mediaIO, Tag.ImagePositionPatient, double[].class));
      if (pos != null && pos.length == 3) {
        Vector3d pPos = new Vector3d(pos);
        Attributes refImgPos = frame.getNestedDataset(Tag.PlaneOrientationSequence);
        double[] imagePosition =
            DicomUtils.getDoubleArrayFromDicomElement(
                refImgPos,
                Tag.ImageOrientationPatient,
                TagD.getTagValue(mediaIO, Tag.ImageOrientationPatient, double[].class));
        if (imagePosition != null && imagePosition.length == 6) {
          Vector3d vr = new Vector3d(imagePosition);
          Vector3d vc = new Vector3d(imagePosition[3], imagePosition[4], imagePosition[5]);
          Vector3d normal = VectorUtils.computeNormalOfSurface(vr, vc);
          normal.mul(pPos);
          String position = normal.toString(roundDouble).replace("-0 ", "0 ");
          Set<SegContour> set = postitionMap.computeIfAbsent(position, _ -> new LinkedHashSet<>());
          set.addAll(contour);
        }
      }
    }
  }

  private SegMeasurableLayer<DicomImageElement> getMeasurableLayer(DicomSeries series, Point p) {
    if (series != null && series.size(null) > 0 && p != null && p.x >= 0 && p.y >= 0) {
      DicomImageElement first = series.getMedia(p.x, null, null);
      DicomImageElement last = series.getMedia(p.y, null, null);
      DicomImageElement img = series.getMedia(p.y - (p.y - p.x) / 2, null, null);
      if (img != null && first != null && last != null) {
        double thickness = DicomMediaUtils.getThickness(first, last);
        if (thickness <= 0.0) {
          thickness = 1.0;
        } else {
          thickness /= (p.y - p.x) + 1;
        }
        return new SegMeasurableLayer<>(img, thickness);
      }
    }
    return null;
  }

  private void buildGraphic(DicomImageElement binaryMask, int id, SegRegion<?> region) {
    PlanarImage binary = binaryMask.getImage();
    List<Segment> segmentList = Region.buildSegmentList(binary);
    int nbPixels = Core.countNonZero(binary.toMat());
    ImageConversion.releasePlanarImage(binary);
    if (!segmentList.isEmpty()) {
      SegContour contour = new SegContour(String.valueOf(id), segmentList, nbPixels);
      region.addPixels(contour);
      contour.setAttributes(region);
      roiMap.computeIfAbsent(id, _ -> new LinkedHashSet<>()).add(contour);
    }
  }
}
