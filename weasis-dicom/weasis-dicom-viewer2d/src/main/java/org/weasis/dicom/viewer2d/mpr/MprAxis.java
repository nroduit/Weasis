/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.awt.Color;
import java.util.Objects;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.viewer2d.mip.MipView.Type;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

public class MprAxis {

  private final Plane plane;
  private final AxisDirection axisDirection;
  private final Matrix4d transformation;
  private MprView mprView;
  private VolImageIO rawIO;
  private DicomImageElement imageElement;
  private int thicknessExtension;

  public MprAxis(Plane plane) {
    this.plane = plane;
    this.thicknessExtension = 0;
    this.transformation = new Matrix4d();
    this.axisDirection = new AxisDirection(plane);
  }

  public void reset() {
    setThicknessExtension(0);
    updateImage();
  }

  public int getSliceIndex() {
    if (rawIO == null || mprView == null) {
      return 0;
    }
    int sliceSize = rawIO.getVolume().getSliceSize();
    int index = (int) (mprView.mprController.getAxesControl().getCenterAlongAxis(mprView));
    int sliceIndex;
    if (axisDirection.isInvertedDirection()) {
      sliceIndex = sliceSize - index;
    } else {
      sliceIndex = index;
    }
    return sliceIndex;
  }

  public void setSliceIndex(int sliceIndex) {
    if (rawIO == null || mprView == null) {
      return;
    }
    int sliceSize = rawIO.getVolume().getSliceSize();
    int index;
    if (axisDirection.isInvertedDirection()) {
      index = sliceSize - sliceIndex;
    } else {
      index = sliceIndex;
    }
    mprView.mprController.getAxesControl().setCenterAlongAxis(mprView, index);
  }

  public AxisDirection getAxisDirection() {
    return axisDirection;
  }

  public void setTransformation(Matrix4d transformation) {
    this.transformation.set(transformation);
    updateImage();
  }

  private void updateRotation() {
    if (mprView != null) {
      Vector3d crossHair = mprView.mprController.getCrossHairPosition();
      Quaterniond rotation = mprView.mprController.getRotation(plane);
      transformation.set(getRealVolumeTransformation(rotation, crossHair));
      updateImage();
    }
  }

  public Matrix4d getRealVolumeTransformation(Quaterniond rotation, Vector3d volumeCenter) {
    if (rawIO == null) {
      return new Matrix4d();
    }
    Volume<?> volume = rawIO.getVolume();
    int sliceImageSize = volume.getSliceSize();
    Vector3d voxelRatio = volume.getVoxelRatio();
    Vector3d volSize = new Vector3d(volume.getSize()).mul(voxelRatio);
    Vector3d center = new Vector3d(volSize).mul(0.5);
    Vector3d crossHair;
    if (volumeCenter == null) {
      crossHair = getMprView().mprController.getCrossHairPosition();
    } else {
      crossHair = new Vector3d(volumeCenter);
    }
    Vector3d volCenter = new Vector3d(sliceImageSize / 2.0);
    Vector3d crossHairOffset = new Vector3d(crossHair).sub(volCenter);
    Vector3d t1 = new Vector3d(center).add(crossHairOffset);

    Matrix4d matrix4d = new Matrix4d();
    Quaterniond r = new Quaterniond(rotation);
    matrix4d
        .translate(t1)
        .rotate(r)
        .translate(t1.negate())
        .translate(center)
        .translate(volCenter.negate());
    applyPlaneSpecificTransformations(matrix4d, plane, crossHair);
    return matrix4d;
  }

  private void applyPlaneSpecificTransformations(Matrix4d matrix, Plane plane, Vector3d crossHair) {
    switch (plane) {
      case AXIAL -> {
        matrix.translate(0, 0, crossHair.z);
      }
      case CORONAL -> {
        matrix.rotateX(-Math.toRadians(90)).scale(1.0, -1.0, 1.0).translate(0, 0, crossHair.y);
      }
      case SAGITTAL -> {
        matrix.rotateY(Math.toRadians(90)).rotateZ(Math.toRadians(90)).translate(0, 0, crossHair.x);
      }
    }
  }

  public void setThicknessExtension(int extend) {
    if (extend > 0
        && mprView != null
        && mprView.mprController.getMipTypeOption().getSelectedItem() == Type.NONE) {
      mprView.mprController.getMipTypeOption().setSelectedItem(Type.MAX);
    }
    this.thicknessExtension = extend;
  }

  public int getThicknessExtension() {
    if (mprView == null
        || mprView.mprController.getMipTypeOption().getSelectedItem() == Type.NONE) {
      return 0;
    }
    return thicknessExtension;
  }

  public void updateImage() {
    if (mprView != null) {
      GeometryOfSlice oldGeometry = imageElement.getSliceGeometry();
      GraphicModel oldModel = (GraphicModel) imageElement.getTagValue(TagW.PresentationModel);

      mprView.getImageLayer().setImage(null, null);
      imageElement.removeImageFromCache();
      mprView.setImage(imageElement);

      GeometryOfSlice geometry = imageElement.getSliceGeometry();
      if (!Objects.equals(oldGeometry, geometry)) {
        GraphicModel model = rawIO.getGraphicModel(geometry);
        imageElement.setTag(TagW.PresentationModel, model);
        mprView.updateGraphicManager(imageElement, true);

        if (oldModel != null && oldModel.hasSerializableGraphics()) {
          rawIO.setGraphicModel(oldGeometry, oldModel);
        }
      }
      // mprView.center();
      mprView.repaint();
    }
  }

  public DicomImageElement getImageElement() {
    return imageElement;
  }

  public Plane getPlane() {
    return plane;
  }

  public Matrix4d getTransformation() {
    return transformation;
  }

  public void setRawIO(VolImageIO rawIO) {
    this.rawIO = rawIO;
    this.imageElement = new DicomImageElement(rawIO, 0);
  }

  public VolImageIO getRawIO() {
    return rawIO;
  }

  public void dispose() {
    if (imageElement != null) {
      imageElement.removeImageFromCache();
      imageElement.dispose();
    }
  }

  public void setMprView(MprView mprView) {
    this.mprView = mprView;
  }

  public MprView getMprView() {
    return mprView;
  }

  public boolean isAdjusting() {
    return mprView != null && mprView.mprController.isAdjusting();
  }

  public void changePositionAlongAxis(Vector3d position, double positionValue) {
    if (plane == Plane.AXIAL) {
      position.z = positionValue;
    } else if (plane == Plane.CORONAL) {
      position.y = positionValue;
    } else {
      position.x = positionValue;
    }
  }

  public Vector3d getAxisDirection(boolean vertical) {
    Vector3d direction =
        new Vector3d(vertical ? axisDirection.getAxisY() : axisDirection.getAxisX());
    mprView.mprController.getAxesControl().getGlobalRotation().transform(direction);
    return direction;
  }

  public Color getAxisDColor(boolean vertical) {
    Vector3d direction = getAxisDirection(vertical);
    return axisDirection.getDirectionColor(direction);
  }
}
