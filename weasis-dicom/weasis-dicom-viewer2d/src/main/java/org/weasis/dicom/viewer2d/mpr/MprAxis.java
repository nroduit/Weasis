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
import java.io.File;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mip.MipView.Type;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public class MprAxis {

  private final SliceOrientation viewOrientation;
  private final AxisDirection axisDirection;
  private final Matrix4d transformation;
  private MprView mprView;
  private VolImageIO rawIO;
  private DicomImageElement imageElement;
  private double positionAlongAxis;
  private int thicknessExtension;
  private double axisAngle = 0.0;

  public MprAxis(SliceOrientation sliceOrientation) {
    this.viewOrientation = sliceOrientation;
    this.thicknessExtension = 0;
    this.positionAlongAxis = 0; // From -sliceSize / 2.0 to sliceSize / 2.0, 0 is the center
    this.transformation = new Matrix4d();
    this.axisDirection = new AxisDirection(sliceOrientation);
  }

  public void setPositionAlongAxis(double positionAlongAxis) {
    this.positionAlongAxis = positionAlongAxis;
  }

  public double getPositionAlongAxis() {
    return positionAlongAxis;
  }

  public void setAxisAngle(double axisAngle) {
    this.axisAngle = axisAngle;
  }

  public double getAxisAngle() {
    return axisAngle;
  }

  public void reset() {
    axisAngle = 0.0;
    resetPositionAlongAxis();
    setThicknessExtension(0);
    updateImage();
  }

  public void resetPositionAlongAxis() {
    this.positionAlongAxis = 0;
  }

  public int getSliceIndex() {
    if (rawIO == null) {
      return 0;
    }
    int sliceSize = rawIO.getVolume().getSliceSize();
    int index = (int) (positionAlongAxis + sliceSize / 2.0);
    int sliceIndex;
    if (axisDirection.isInvertedDirection()) {
      sliceIndex = sliceSize - index;
    } else {
      sliceIndex = index;
    }
    return sliceIndex;
  }

  public void setSliceIndex(int sliceIndex) {
    if (rawIO == null) {
      return;
    }
    int sliceSize = rawIO.getVolume().getSliceSize();
    int index;
    if (axisDirection.isInvertedDirection()) {
      index = sliceSize - sliceIndex;
    } else {
      index = sliceIndex;
    }
    this.positionAlongAxis = index - sliceSize / 2.0;
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
      Vector3d crossHair = mprView.mprController.getVolumeCrossHair();
      Quaterniond rotation = mprView.mprController.getRotation();
      transformation.set(getCombinedTransformation(rotation, crossHair));
      updateImage();
    }
  }

  public Matrix4d getCombinedTransformation(Quaterniond rotation, Vector3d volumeCenter) {
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
      crossHair = getMprView().mprController.getVolumeCrossHair();
    } else {
      crossHair = new Vector3d(volumeCenter);
    }
    Vector3d volCenter = new Vector3d(sliceImageSize / 2.0);
    Vector3d crossHairOffset = new Vector3d(crossHair).sub(volCenter);
    Vector3d t1 =
        new Vector3d(center)
            .add(crossHairOffset.x, sliceImageSize - crossHair.y - volCenter.y, crossHairOffset.z);

    Matrix4d matrix4d = new Matrix4d();
    Quaterniond r = new Quaterniond(rotation);
    double axisAngle = getMprView().mprController.getMprAxis(viewOrientation).getAxisAngle();

    switch (viewOrientation) {
      case AXIAL -> {
        Vector3d t2 = new Vector3d(volCenter);
        t2.x += crossHairOffset.x;
        t2.y -= crossHairOffset.y;
        t2.z = 0;

        r.rotateZ(-axisAngle);
        matrix4d.translate(t1).rotate(r).translate(t2.negate());
      }
      case CORONAL -> {
        r.rotateY(-axisAngle);
        matrix4d.translate(t1).rotate(r).translate(t1.negate());
        matrix4d.translate(center).rotateX(Math.toRadians(90)).translate(volCenter.negate());
        matrix4d.translate(0, 0, crossHair.y);
      }
      case SAGITTAL -> {
        r.rotateX(-axisAngle);
        matrix4d.translate(t1).rotate(r).translate(t1.negate());
        matrix4d
            .translate(center)
            .rotateY(Math.toRadians(90))
            .rotateZ(Math.toRadians(90))
            .translate(volCenter.negate());
        matrix4d.translate(0, 0, crossHair.x);
      }
    }
    return matrix4d;
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
      mprView.getImageLayer().setImage(null, null);
      imageElement.removeImageFromCache();
      mprView.setImage(imageElement);
      // mprView.center();
      mprView.repaint();
    }
  }

  public DicomImageElement getImageElement() {
    return imageElement;
  }

  public SliceOrientation getViewOrientation() {
    return viewOrientation;
  }

  public Matrix4d getTransformation() {
    return transformation;
  }

  public void setRawIO(VolImageIO rawIO) {
    this.rawIO = rawIO;
    this.imageElement =
        new DicomImageElement(rawIO, 0) {
          @Override
          public boolean saveToFile(File output) {
            return saveToFile(mediaIO, output);
          }
        };
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
    if (viewOrientation == SliceOrientation.AXIAL) {
      position.z = positionValue;
    } else if (viewOrientation == SliceOrientation.CORONAL) {
      position.y = positionValue;
    } else {
      position.x = positionValue;
    }
  }

  public Vector3d getAxisDirection(boolean vertical) {
    Vector3d direction =
        new Vector3d(vertical ? axisDirection.getAxisY() : axisDirection.getAxisX());
    mprView.mprController.getRotation().transform(direction);
    return direction;
  }

  public Color getAxisDColor(boolean vertical) {
    Vector3d direction = getAxisDirection(vertical);
    return axisDirection.getDirectionColor(direction);
  }
}
