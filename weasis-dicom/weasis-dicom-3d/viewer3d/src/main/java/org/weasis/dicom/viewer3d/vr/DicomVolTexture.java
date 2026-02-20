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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.ZoomOp.Interpolation;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.MathUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.geometry.ImageOrientation.Plan;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;

public class DicomVolTexture extends VolumeTexture implements MediaSeriesGroup {

  private final TagW tagID;
  private final Map<TagW, Object> tags;
  private final PropertyChangeSupport changeSupport;
  private final SimpleOpManager manager;
  private final Vector3d scale;
  private final Volume<?, ?> volume;

  private String pixelValueUnit;

  public DicomVolTexture(
      Vector3i size, Volume<?, ?> v, PixelFormat pixelFormat, PropertyChangeSupport changeSupport) {
    super(size, pixelFormat);
    this.volume = v;
    this.changeSupport = Objects.requireNonNull(changeSupport);

    tags = new HashMap<>();
    tagID = v.getStack().getSeries().getTagID();
    tags.put(tagID, v.getStack().getSeries().getTagValue(tagID));

    Modality modality = getModality();
    pixelValueUnit = TagD.getTagValue(this, Tag.RescaleType, String.class);
    if (pixelValueUnit == null) {
      // For some other modalities like PET
      pixelValueUnit = TagD.getTagValue(this, Tag.Units, String.class);
    }
    if (pixelValueUnit == null && modality == Modality.CT) {
      pixelValueUnit = "HU";
    }

    Vector3i volSize = v.getSize();
    this.scale =
        new Vector3d(
            (double) width / volSize.x, (double) height / volSize.y, (double) depth / volSize.z);
    Vector3d tex = v.getVoxelRatio();
    tex.x /= scale.x;
    tex.y /= scale.y;
    tex.z /= scale.z;
    setTexelSize(tex);
    if ((MathUtil.isDifferent(scale.x, 1.0) || MathUtil.isDifferent(scale.y, 1.0))) {
      this.manager = new SimpleOpManager();
      ZoomOp node = new ZoomOp();
      node.setParam(ZoomOp.P_RATIO_X, scale.x);
      node.setParam(ZoomOp.P_RATIO_Y, scale.y);
      node.setParam(ZoomOp.P_INTERPOLATION, Interpolation.BILINEAR);
      manager.addImageOperationAction(node);
    } else {
      this.manager = null;
    }
  }

  public PlanarImage getScaledImage(PlanarImage image) {
    PlanarImage output = null;
    if (image != null) {
      output = image;
      if (manager != null) {
        manager.setFirstNode(output);
        output = manager.process().orElse(null);
        manager.clearNodeIOCache();
      } else {
        if (width != output.width() || height != output.height()) {
          output = ImageCV.fromMat(output.toMat().submat(new Rect(0, 0, width, height)));
        }
      }
    }
    return output;
  }

  public Vector3d getScale() {
    return scale;
  }

  public Modality getModality() {
    return Modality.getModality(TagD.getTagValue(this, Tag.Modality, String.class));
  }

  public MediaSeries<DicomImageElement> getSeries() {
    return volume.getStack().getSeries();
  }

  public double getLevelMin() {
    return volume.getMinimumAsDouble();
  }

  public double getLevelMax() {
    return volume.getMaximumAsDouble();
  }

  public Volume<?, ?> getVolume() {
    return volume;
  }

  @Override
  public TagW getTagID() {
    return tagID;
  }

  public Unit getPixelSpacingUnit() {
    return Unit.PIXEL;
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
    return volume.getStack().getSeries().getTagValue(tag);
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
    DicomImageElement media = volume.getStack().getMiddleImage();

    DefaultWlPresentation wlp = new DefaultWlPresentation(null, pixelPadding);
    List<PresetWindowLevel> presets = new ArrayList<>(media.getPresetList(wlp));
    if (!originalLut) {
      // Modify auto level according to the volume LUT
      for (int i = 0; i < presets.size(); i++) {
        PresetWindowLevel p = presets.get(i);
        if (p.getKeyCode() == 0x30) {
          double ww = (double) volumePreset.getColorMax() - volumePreset.getColorMin();
          PresetWindowLevel autoLevel =
              new PresetWindowLevel(
                  "Auto Level [Image]", // NON-NLS
                  ww,
                  volumePreset.getColorMin() + ww / 2,
                  LutShape.LINEAR);
          autoLevel.setKeyCode(0x30);
          presets.set(i, autoLevel);
          break;
        }
      }
    }
    return presets;
  }

  public Collection<LutShape> getLutShapeCollection(boolean pixelPadding) {
    DicomImageElement imgElement = volume.getStack().getMiddleImage();

    DefaultWlPresentation wlp = new DefaultWlPresentation(null, pixelPadding);
    return imgElement.getLutShapeCollection(wlp);
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
    GuiExecutor.execute(() -> changeSupport.firePropertyChange(event));
  }

  public Plan getSlicePlan() {
    return Plan.AXIAL;
  }

  public List<SpecialElementRegion> getSegmentations() {
    String seriesUID =
        TagD.getTagValue(volume.getStack().getSeries(), Tag.SeriesInstanceUID, String.class);
    Set<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
    if (list != null && !list.isEmpty()) {
      return HiddenSeriesManager.getHiddenElementsFromSeries(
          SpecialElementRegion.class, list.toArray(new String[0]));
    }
    return Collections.emptyList();
  }

  public Mat getEmptyImage() {
    int type;
    if (pixelFormat == PixelFormat.RGB8) {
      type = CvType.CV_8UC3;
    } else if (pixelFormat == PixelFormat.RGBA8) {
      type = CvType.CV_8UC4;
    } else if (pixelFormat == PixelFormat.BYTE) {
      type = CvType.CV_8UC1;
    } else {
      type = CvType.CV_16UC1;
    }
    return Mat.zeros(new Size(width, height), type);
  }
}
