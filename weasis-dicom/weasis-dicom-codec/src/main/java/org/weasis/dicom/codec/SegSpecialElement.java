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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.CIELab;
import org.dcm4che3.img.util.DicomUtils;
import org.opencv.core.Core;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegMeasurableLayer;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.macro.Code;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.seg.Region;
import org.weasis.opencv.seg.Segment;
import org.weasis.opencv.seg.SegmentAttributes;
import org.weasis.opencv.seg.SegmentCategory;

public class SegSpecialElement extends HiddenSpecialElement {

  private final Map<String, Map<String, List<SegContour>>> refMap = new HashMap<>();
  private final Map<Integer, List<SegContour>> roiMap = new HashMap<>();
  private final Map<Integer, SegRegion> segAttributes = new HashMap<>();

  private volatile float opacity = 1.0f;
  private volatile boolean visible = true;

  public SegSpecialElement(DicomMediaIO mediaIO) {
    super(mediaIO);
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

  public void initContours(DicomSeries series) {
    roiMap.clear();
    refMap.clear();

    Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
    String segmentType = dicom.getString(Tag.SegmentationType);
    if ("FRACTIONAL".equals(segmentType)) {
      // TODO: handle fractional segmentations
    }

    // Locate the name and number of each ROI
    Sequence segSeq = dicom.getSequence(Tag.SegmentSequence);
    List<String> sourceSopUIDList = new ArrayList<>();

    if (segSeq != null && series != null) {
      Sequence seriesRef = dicom.getSequence(Tag.ReferencedSeriesSequence);
      if (seriesRef != null) {
        for (Attributes ref : seriesRef) {
          String seriesUID = ref.getString(Tag.SeriesInstanceUID);
          if (StringUtil.hasText(seriesUID)) {
            String originSeriesUID = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
            List<String> list =
                HiddenSeriesManager.getInstance()
                    .reference2Series
                    .computeIfAbsent(seriesUID, _ -> new ArrayList<>());
            list.add(originSeriesUID);
            Map<String, List<SegContour>> map =
                refMap.computeIfAbsent(seriesUID, _ -> new HashMap<>());
            Sequence instanceSeq = ref.getSequence(Tag.ReferencedInstanceSequence);
            if (instanceSeq != null) {
              for (Attributes instance : instanceSeq) {
                String sopInstanceUID = instance.getString(Tag.ReferencedSOPInstanceUID);
                map.computeIfAbsent(sopInstanceUID, _ -> new ArrayList<>());
              }
            }
          }
        }
      } else {
        addSourceImage(dicom, sourceSopUIDList);
        String seriesUID = "1.3.6.1.4.1.14519.5.2.1.7695.1700.229054711046553504545787083659";
        String originSeriesUID = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
        List<String> list =
            HiddenSeriesManager.getInstance()
                .reference2Series
                .computeIfAbsent(seriesUID, _ -> new ArrayList<>());
        list.add(originSeriesUID);
        Map<String, List<SegContour>> map = refMap.computeIfAbsent(seriesUID, _ -> new HashMap<>());

        for (String sopUID : sourceSopUIDList) {
          map.computeIfAbsent(sopUID, _ -> new ArrayList<>());
        }
      }

      SegMeasurableLayer<DicomImageElement> measurableLayer = getMeasurableLayer(series);

      for (Attributes seg : segSeq) {
        int nb = seg.getInt(Tag.SegmentNumber, -1);
        String segmentLabel = seg.getString(Tag.SegmentLabel);
        String segmentAlgorithmType = seg.getString(Tag.SegmentAlgorithmType);

        Sequence regionSeq = seg.getSequence(Tag.AnatomicRegionSequence);
        if (regionSeq != null) {
          for (Attributes region : regionSeq) {
            Code regionCode = new Code(region);
            String regionCodeValue = regionCode.getCodeValue();
            String regionCodingSchemeDesignator = regionCode.getCodingSchemeDesignator();
            String regionCodeMeaning = regionCode.getCodeMeaning();
          }
        }

        Sequence categorySeq = seg.getSequence(Tag.SegmentedPropertyCategoryCodeSequence);
        if (categorySeq != null) {
          for (Attributes category : categorySeq) {
            Code categoryCode = new Code(category);
            String categoryCodeCodeMeaning = categoryCode.getCodeMeaning();
          }
        }
        String trackingUID = seg.getString(Tag.TrackingUID);
        Integer grayVal =
            DicomUtils.getIntegerFromDicomElement(seg, Tag.RecommendedDisplayGrayscaleValue, null);
        int[] colorRgb =
            CIELab.dicomLab2rgb(
                DicomUtils.getIntArrayFromDicomElement(
                    seg, Tag.RecommendedDisplayCIELabValue, null));
        Color rgbColor = SegmentAttributes.getColor(colorRgb, nb, opacity);
        String segmentAlgorithmName = seg.getString(Tag.SegmentAlgorithmName);
        String segmentDescription = seg.getString(Tag.SegmentDescription);

        SegmentAttributes attributes = new SegmentAttributes(rgbColor, true, 1f);
        attributes.setInteriorOpacity(0.2f);
        SegmentCategory category =
            new SegmentCategory(nb, segmentLabel, null, segmentAlgorithmType);
        SegRegion c = new SegRegion(String.valueOf(nb));
        c.setCategory(category);
        c.setAttributes(attributes);
        c.setMeasurableLayer(measurableLayer);
        segAttributes.put(nb, c);
      }
    }

    Sequence perFrameSeq = dicom.getSequence(Tag.PerFrameFunctionalGroupsSequence);
    if (perFrameSeq != null) {
      int frameCount = perFrameSeq.size();
      int index = 0;
      for (Attributes frame : perFrameSeq) {
        index++;
        List<String> sopUIDList = new ArrayList<>();
        Sequence derivationSeq = frame.getSequence(Tag.DerivationImageSequence);
        if (derivationSeq != null) {
          for (Attributes derivation : derivationSeq) {
            addSourceImage(derivation, sopUIDList);
          }
        }

        if (sopUIDList.isEmpty() && sourceSopUIDList.size() >= index) {
          for (int i = index - 1; i < sourceSopUIDList.size(); i = i + frameCount) {
            sopUIDList.add(sourceSopUIDList.get(i));
          }
        }

        Sequence refPos = frame.getSequence(Tag.PlanePositionSequence);
        if (refPos != null) {
          for (Attributes ref : refPos) {
            //              double[] pos = TagD.getTagValue(ref, Tag.ImagePositionPatient,
            // double[].class);
            //              if (pos != null && pos.length == 3) {
            //                series.getNearestImage(pos, null, null);
            //              }
          }
        }

        DicomImageElement binaryMask = series.getMedia(index - 1, null, null);
        if (binaryMask != null) {
          Attributes refSeqNb = frame.getNestedDataset(Tag.SegmentIdentificationSequence);
          if (refSeqNb != null) {
            Integer nb =
                DicomUtils.getIntegerFromDicomElement(refSeqNb, Tag.ReferencedSegmentNumber, null);
            if (nb != null) {
              SegRegion c = segAttributes.get(nb);
              if (c != null) {
                buildGraphic(binaryMask, index, c);
              }
            }
          }
        }

        List<SegContour> contour = roiMap.get(index);
        if (contour != null && !contour.isEmpty()) {
          refMap.forEach(
              (key, _) -> {
                Map<String, List<SegContour>> map = refMap.get(key);
                if (map != null) {
                  sopUIDList.forEach(
                      sopUID -> {
                        List<SegContour> list = map.get(sopUID);
                        if (list != null) {
                          list.addAll(contour);
                        }
                      });
                }
              });
        }
      }
    }
  }

  private SegMeasurableLayer<DicomImageElement> getMeasurableLayer(DicomSeries series) {
    if (series != null && series.size(null) > 0) {
      DicomImageElement first = series.getMedia(MEDIA_POSITION.FIRST, null, null);
      DicomImageElement last = series.getMedia(MEDIA_POSITION.LAST, null, null);
      DicomImageElement img = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
      if (img != null && first != null && last != null) {
        int size = series.size(null);
        double thickness = DicomMediaUtils.getThickness(first, last);
        if (thickness <= 0.0) {
          thickness = 1.0;
        } else {
          thickness /= size;
        }
        return new SegMeasurableLayer<>(img, thickness);
      }
    }
    return null;
  }

  private static void addSourceImage(Attributes derivation, List<String> sopUIDList) {
    Sequence srcSeq = derivation.getSequence(Tag.SourceImageSequence);
    if (srcSeq != null) {
      for (Attributes src : srcSeq) {
        sopUIDList.add(src.getString(Tag.ReferencedSOPInstanceUID));
      }
    }
  }

  private void buildGraphic(DicomImageElement binaryMask, int id, SegRegion region) {
    SegmentAttributes attributes = region.getAttributes();
    SegmentCategory category = region.getCategory();
    PlanarImage binary = binaryMask.getImage();
    List<Segment> segmentList = Region.buildSegmentList(binary);
    int nbPixels = Core.countNonZero(binary.toMat());
    ImageConversion.releasePlanarImage(binary);
    SegContour contour = new SegContour(String.valueOf(id), segmentList, nbPixels);
    region.addPixels(contour);
    contour.setAttributes(attributes);
    contour.setCategory(category);
    List<SegContour> list = roiMap.computeIfAbsent(id, _ -> new ArrayList<>());
    list.add(contour);
  }

  public Map<Integer, List<SegContour>> getRoiMap() {
    return roiMap;
  }

  public Map<String, Map<String, List<SegContour>>> getRefMap() {
    return refMap;
  }

  public Map<Integer, SegRegion> getSegAttributes() {
    return segAttributes;
  }

  public boolean isVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  public float getOpacity() {
    return opacity;
  }

  public void setOpacity(float opacity) {
    this.opacity = Math.max(0.0f, Math.min(opacity, 1.0f));
    ;
    int opacityValue = (int) (this.opacity * 255f);
    segAttributes
        .values()
        .forEach(
            c -> {
              Color color = c.getAttributes().getColor();
              color = new Color(color.getRed(), color.getGreen(), color.getBlue(), opacityValue);
              c.getAttributes().setColor(color);
            });
  }

  public boolean containsSopInstanceUIDReference(DicomImageElement img) {
    if (img != null) {
      String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
      if (seriesUID != null) {
        String sopInstanceUID = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
        Map<String, List<SegContour>> map = refMap.get(seriesUID);
        if (map != null && sopInstanceUID != null) {
          return map.containsKey(sopInstanceUID);
        }
      }
    }
    return false;
  }

  public Collection<SegContour> getContours(DicomImageElement img) {
    String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
    if (seriesUID != null) {
      String sopInstanceUID = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
      Map<String, List<SegContour>> map = refMap.get(seriesUID);
      if (map != null && sopInstanceUID != null) {
        List<SegContour> list = map.get(sopInstanceUID);
        if (list != null) {
          return list;
        }
      }
    }
    return Collections.emptyList();
  }
}
