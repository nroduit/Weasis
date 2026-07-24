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

import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.geometry.ImageOrientation.Plan;
import org.weasis.dicom.viewer2d.SegComponentFactory;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.dicom.viewer3d.ActionVol;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;

public class DicomVolTexture extends VolumeTexture implements MediaSeriesGroup {

  private final TagW tagID;
  private final Map<TagW, Object> tags;
  private final PropertyChangeSupport changeSupport;
  private final Vector3d scale;
  private final Volume<?, ?> volume;
  private final PropertyChangeListener crossHairRelay;

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
    this.crossHairRelay =
        evt -> firePropertyChange(this, ActionVol.MPR_CROSSHAIR.cmd(), evt.getNewValue());
  }

  /**
   * Registers the MPR crosshair relay on the underlying {@link Volume} so that crosshair events
   * fired by the MPR controller are forwarded to listeners of this texture. Duplicate registrations
   * are silently ignored by {@link Volume#addCrossHairChangeListener}.
   */
  public void registerCrossHairRelay() {
    volume.addCrossHairChangeListener(crossHairRelay);
  }

  /**
   * Unregisters the MPR crosshair relay from the underlying {@link Volume}. Safe to call even when
   * not currently registered.
   */
  public void unregisterCrossHairRelay() {
    volume.removeCrossHairChangeListener(crossHairRelay);
  }

  /**
   * Resizes a slice to the exact texture dimensions. A ratio-based zoom can produce an off-by-one
   * size from floating-point truncation, which corrupts the {@code glTexSubImage3D} upload.
   */
  public PlanarImage getScaledImage(PlanarImage image) {
    PlanarImage output = image;
    if (output != null && (width != output.width() || height != output.height())) {
      output =
          ImageTransformer.scale(
              output.toMat(), new Dimension(width, height), Imgproc.INTER_LINEAR);
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
    unregisterCrossHairRelay();
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

  public PresetWindowLevel getDefaultPreset(Preset volumePreset) {

    double ww = (double) volumePreset.getColorMax() - volumePreset.getColorMin();
    PresetWindowLevel autoLevel =
        new PresetWindowLevel(
            "Auto Level", // NON-NLS
            ww,
            volumePreset.getColorMin() + ww / 2,
            LutShape.LINEAR);
    autoLevel.setKeyCode(0x30);
    return autoLevel;
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

  /**
   * Subscribes a listener to property-change events fired on this volume texture. This includes
   * load notifications ({@code FULLY_LOADED}, {@code PARTIALLY_LOADED}) and MPR crosshair events
   * ({@code "mpr.crosshair"}).
   */
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    changeSupport.addPropertyChangeListener(listener);
  }

  /** Removes a previously registered property-change listener. */
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    changeSupport.removePropertyChangeListener(listener);
  }

  public Plan getSlicePlan() {
    return Plan.AXIAL;
  }

  public List<SpecialElementRegion> getSegmentations() {
    return SegComponentFactory.getRelatedSegments(
        SpecialElementRegion.class, volume.getStack().getSeries());
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
