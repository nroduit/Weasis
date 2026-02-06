/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.cmpr;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import org.joml.Vector3d;
import org.opencv.core.Point3;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.dicom.viewer2d.mpr.MprView;

/**
 * Specialized polyline graphic for drawing curved MPR paths.
 * 
 * <p>This graphic extends PolylineGraphic to allow users to draw a curve in an MPR plane.
 * The 2D points are converted to 3D volume coordinates for panoramic image generation.
 */
@XmlType(name = "curvedMpr")
@XmlRootElement(name = "curvedMpr")
public class CurvedMprGraphic extends PolylineGraphic {

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_POLYLINE);

  public CurvedMprGraphic() {
    super();
  }

  public CurvedMprGraphic(CurvedMprGraphic graphic) {
    super(graphic);
  }

  @Override
  public CurvedMprGraphic copy() {
    return new CurvedMprGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return "Curved MPR Path";
  }

  /**
   * Convert the 2D polyline points to 3D volume coordinates.
   *
   * @param mprView the MprView containing the volume and coordinate transformation methods
   * @return list of 3D points in volume coordinates
   */
  public List<Vector3d> get3DPoints(MprView mprView) {
    List<Vector3d> points3D = new ArrayList<>();
    if (mprView == null) {
      return points3D;
    }

    for (Point2D pt : pts) {
      if (pt != null) {
        // Use getVolumeCoordinatesFromImage since graphic points are in image space,
        // not mouse/screen coordinates. This returns actual voxel coordinates.
        Point3 volCoord = mprView.getVolumeCoordinatesFromImage(pt.getX(), pt.getY());
        if (volCoord != null) {
          points3D.add(new Vector3d(volCoord.x, volCoord.y, volCoord.z));
        }
      }
    }
    return points3D;
  }

  /**
   * Generate a Curved MPR view from this graphic.
   *
   * @param mprView the source MprView
   */
  public void generateCurvedMpr(MprView mprView) {
    List<Vector3d> points3D = get3DPoints(mprView);
    if (points3D.size() >= 2) {
      CurvedMprFactory.openCurvedMpr(mprView, points3D);
    }
  }

  /**
   * Check if the graphic has enough points to generate a curved MPR.
   *
   * @return true if there are at least 2 valid points
   */
  public boolean canGenerateCurvedMpr() {
    int validPoints = 0;
    for (Point2D pt : pts) {
      if (pt != null) {
        validPoints++;
      }
    }
    return validPoints >= 2;
  }
}
