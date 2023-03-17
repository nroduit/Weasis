/*
 * Copyright (c) 2012 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.joml.Vector3d;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.ImageOrientation.Plan;
import org.weasis.dicom.viewer3d.geometry.VolumeGeometry;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;

public class DicomVolTexture extends VolumeTexture implements MediaSeriesGroup {

  private final TagW tagID;
  private final Map<TagW, Object> tags;
  private final MediaSeries<DicomImageElement> series;
  private final Comparator<DicomImageElement> seriesComparator;
  private final VolumeGeometry volumeGeometry;
  private final PropertyChangeSupport changeSupport;

  private String pixelValueUnit;

  private int levelMin;
  private int levelMax;
  private Unit pixelSpacingUnit;

  public DicomVolTexture(
      int width,
      int height,
      int depth,
      PixelFormat pixelFormat,
      MediaSeries<DicomImageElement> series,
      Comparator<DicomImageElement> sorter,
      PropertyChangeSupport changeSupport) {
    super(width, height, depth, pixelFormat);
    this.volumeGeometry = new VolumeGeometry();
    this.series = Objects.requireNonNull(series);
    this.changeSupport = changeSupport;
    this.levelMin = -1024;
    this.levelMax = 3071;
    this.pixelSpacingUnit = Unit.PIXEL;

    tags = new HashMap<>();
    tagID = series.getTagID();
    tags.put(tagID, series.getTagValue(tagID));

    seriesComparator = sorter;

    Modality modality = getModality();
    pixelValueUnit = TagD.getTagValue(this, Tag.RescaleType, String.class);
    if (pixelValueUnit == null) {
      // For some other modalities like PET
      pixelValueUnit = TagD.getTagValue(this, Tag.Units, String.class);
    }
    if (pixelValueUnit == null && modality == Modality.CT) {
      pixelValueUnit = "HU";
    }
  }

  public Modality getModality() {
    return Modality.getModality(TagD.getTagValue(this, Tag.Modality, String.class));
  }

  public MediaSeries<DicomImageElement> getSeries() {
    return series;
  }

  public int getLevelMin() {
    return levelMin;
  }

  public void setLevelMin(int levelMin) {
    this.levelMin = levelMin;
  }

  public int getLevelMax() {
    return levelMax;
  }

  public void setLevelMax(int levelMax) {
    this.levelMax = levelMax;
  }

  @Override
  public TagW getTagID() {
    return tagID;
  }

  public Unit getPixelSpacingUnit() {
    return pixelSpacingUnit;
  }

  @Override
  public void setTag(TagW tag, Object value) {
    if (tag != null && !tag.equals(tagID)) {
      tags.put(tag, value);
    }
  }

  @Override
  public boolean containTagKey(TagW tag) {
    return tags.containsKey(tag);
  }

  @Override
  public Object getTagValue(TagW tag) {
    if (containTagKey(tag)) {
      return tags.get(tag);
    }
    return series.getTagValue(tag);
  }

  @Override
  public TagW getTagElement(int id) {
    for (TagW e : tags.keySet()) {
      if (e.getId() == id) {
        return e;
      }
    }
    return null;
  }

  @Override
  public void dispose() {
    // Do nothing
  }

  @Override
  public boolean matchIdValue(Object valueID) {
    return Objects.equals(tags.get(tagID), valueID);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tags.get(tagID));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return Objects.equals(tags.get(tagID), ((MediaSeriesGroup) o).getTagValue(tagID));
  }

  @Override
  public void setTagNoNull(TagW tag, Object value) {
    if (tag != null && value != null && !tag.equals(tagID)) {
      tags.put(tag, value);
    }
  }

  public List<PresetWindowLevel> getPresetList(
      boolean pixelPadding, Preset volumePreset, boolean originalLut) {
    Object media = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);

    if (media instanceof DicomImageElement imgElement) {
      DefaultWlPresentation wlp = new DefaultWlPresentation(null, pixelPadding);
      List<PresetWindowLevel> presets = new ArrayList<>(imgElement.getPresetList(wlp));
      if (!originalLut) {
        // Modify auto level according to the volume LUT
        for (int i = 0; i < presets.size(); i++) {
          PresetWindowLevel p = presets.get(i);
          if (p.getKeyCode() == 0x30) {
            double ww = (double) volumePreset.getColorMax() - volumePreset.getColorMin();
            PresetWindowLevel autoLevel =
                new PresetWindowLevel(
                    "Auto Level [Image]", ww, volumePreset.getColorMin() + ww / 2, LutShape.LINEAR);
            autoLevel.setKeyCode(0x30);
            presets.set(i, autoLevel);
            break;
          }
        }
      }
      return presets;
    }
    return Collections.emptyList();
  }

  public Collection<LutShape> getLutShapeCollection(boolean pixelPadding) {
    Object media = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);

    if (media instanceof DicomImageElement imgElement) {
      DefaultWlPresentation wlp = new DefaultWlPresentation(null, pixelPadding);
      return imgElement.getLutShapeCollection(wlp);
    }
    return Collections.emptyList();
  }

  public Comparator<DicomImageElement> getSeriesComparator() {
    return seriesComparator;
  }

  public VolumeGeometry getVolumeGeometry() {
    return volumeGeometry;
  }

  public Object getTagValue(TagW tag, int currentSlice) {
    if (depth == series.size(null)) {
      Object media = series.getMedia(currentSlice, null, seriesComparator);
      if (media instanceof DicomImageElement dicomImageElement) {
        return dicomImageElement.getTagValue(tag);
      }
    }
    return null;
  }

  public boolean isPhotometricInterpretationInverse() {
    Object media = series.getMedia(MEDIA_POSITION.FIRST, null, seriesComparator);
    if (media instanceof DicomImageElement dicomImageElement) {
      return dicomImageElement.isPhotometricInterpretationInverse(null);
    }
    return false;
  }

  public String getPixelValueUnit() {
    return pixelValueUnit;
  }

  public void notifyFullyLoaded() {
    firePropertyChange(this, DicomVolTextureFactory.FULLY_LOADED, this);
  }

  public void notifyPartiallyLoaded() {
    firePropertyChange(this, DicomVolTextureFactory.PARTIALLY_LOADED, this);
  }

  public boolean isReadyForDisplay() {
    return getId() > 0;
  }

  @Override
  public Iterator<Map.Entry<TagW, Object>> getTagEntrySetIterator() {
    return tags.entrySet().iterator();
  }

  protected void firePropertyChange(final Object source, final String name, final Object newValue) {
    PropertyChangeEvent event = new PropertyChangeEvent(source, name, null, newValue);
    GuiExecutor.instance().execute(() -> changeSupport.firePropertyChange(event));
  }

  public void setPixelSpacingUnit(Unit pixelSpacingUnit) {
    this.pixelSpacingUnit = pixelSpacingUnit;
  }

  public Plan getSlicePlan() {
    double[] or = getVolumeGeometry().getOrientationPatient();
    if (or != null && or.length == 6) {
      Vector3d vr = new Vector3d(or);
      Vector3d vc = new Vector3d(or[3], or[4], or[5]);
      return ImageOrientation.getPlan(vr, vc);
    }
    return null;
  }
}
