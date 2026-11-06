/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.model;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;

/** Synthetic whole slide headers for the tests. */
public final class SyntheticSlides {

  private SyntheticSlides() {}

  static final String FOR_UID = "1.2.3.4"; // NON-NLS

  public static Attributes instance(String uid, String imageType, int columns, int rows, int tile) {
    Attributes dcm = new Attributes();
    dcm.setString(Tag.SOPClassUID, VR.UI, UID.VLWholeSlideMicroscopyImageStorage);
    dcm.setString(Tag.SOPInstanceUID, VR.UI, uid);
    dcm.setString(Tag.FrameOfReferenceUID, VR.UI, FOR_UID);
    dcm.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", imageType, "NONE"); // NON-NLS
    dcm.setInt(Tag.TotalPixelMatrixColumns, VR.UL, columns);
    dcm.setInt(Tag.TotalPixelMatrixRows, VR.UL, rows);
    dcm.setInt(Tag.Columns, VR.US, tile);
    dcm.setInt(Tag.Rows, VR.US, tile);
    return dcm;
  }

  public static Attributes tiledFull(String uid, int columns, int rows, int tile) {
    Attributes dcm = instance(uid, "VOLUME", columns, rows, tile); // NON-NLS
    dcm.setString(Tag.DimensionOrganizationType, VR.CS, "TILED_FULL"); // NON-NLS
    int frames = Math.ceilDiv(columns, tile) * Math.ceilDiv(rows, tile);
    dcm.setInt(Tag.NumberOfFrames, VR.IS, frames);
    return dcm;
  }

  /** A sparse instance whose frames sit at the given 1-based {column, row, z} positions. */
  public static Attributes sparse(
      String uid, int columns, int rows, int tile, double[]... positions) {
    Attributes dcm = instance(uid, "VOLUME", columns, rows, tile); // NON-NLS
    dcm.setInt(Tag.NumberOfFrames, VR.IS, positions.length);
    Sequence perFrame = dcm.newSequence(Tag.PerFrameFunctionalGroupsSequence, positions.length);
    for (double[] p : positions) {
      Attributes position = new Attributes();
      position.setInt(Tag.ColumnPositionInTotalImagePixelMatrix, VR.SL, (int) p[0]);
      position.setInt(Tag.RowPositionInTotalImagePixelMatrix, VR.SL, (int) p[1]);
      position.setDouble(Tag.ZOffsetInSlideCoordinateSystem, VR.DS, p.length > 2 ? p[2] : 0.0);
      Attributes item = new Attributes();
      item.newSequence(Tag.PlanePositionSlideSequence, 1).add(position);
      perFrame.add(item);
    }
    return dcm;
  }

  public static void opticalPaths(Attributes dcm, String... ids) {
    Sequence seq = dcm.newSequence(Tag.OpticalPathSequence, ids.length);
    for (String id : ids) {
      Attributes item = new Attributes();
      item.setString(Tag.OpticalPathIdentifier, VR.SH, id);
      seq.add(item);
    }
  }
}
