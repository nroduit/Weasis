/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import org.weasis.core.api.gui.task.SeriesProgressMonitor;
import org.weasis.core.api.media.data.Series;

public class DicomSeriesProgressMonitor extends SeriesProgressMonitor {

  private volatile boolean wadoRequest;
  private byte[] header;

  public DicomSeriesProgressMonitor(final Series series, InputStream in, boolean wadoRequest) {
    super(series, in);
    this.wadoRequest = wadoRequest;
    if (wadoRequest) {
      header = new byte[512];
    }
  }

  @Override
  public int read(byte[] b) throws IOException {
    int nr = super.read(b);
    if (wadoRequest) {
      /*
       * header.length (512) is an empirical value: 132 is the magic number position + something to be sure to get
       * Transfer Syntax UID (0x0002, 0x0010)
       */
      if (nread < header.length) {
        System.arraycopy(b, 0, header, nread - nr, nr);
      } else {
        wadoRequest = false;
        int length = header.length - (nread - nr);
        System.arraycopy(b, 0, header, nread - nr, length);
        readMetaInfo(this, header);
        header = null;
      }
    }
    return nr;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int nr = super.read(b, off, len);
    if (wadoRequest) {
      if (nread < header.length) {
        System.arraycopy(b, off, header, nread - nr, nr);
      } else {
        wadoRequest = false;
        int length = header.length - (nread - nr);
        System.arraycopy(b, off, header, nread - nr, length);
        readMetaInfo(this, header);
        header = null;
      }
    }
    return nr;
  }

  public static void readMetaInfo(DicomSeriesProgressMonitor progress, byte[] b)
      throws IOException {
    if (b == null) {
      return;
    }
    int byteOffset;
    if (b.length > 132 && "DICM".equals(new String(b, 128, 4, StandardCharsets.UTF_8))) {
      byteOffset = 132;
    } else {
      InterruptedIOException exc = new InterruptedIOException("Not a DICOM file");
      exc.bytesTransferred = progress.nread;
      progress.series.setFileSize(progress.series.getFileSize() - progress.nread);
      progress.nread = 0;
      throw exc;
    }

    int endByteOffset = b.length - 1;
    while (byteOffset < endByteOffset) {
      int group = extractUnsigned16(b, byteOffset);
      int element = extractUnsigned16(b, byteOffset + 2);
      byteOffset += 4;
      if ((group == 0x0002 && element > 0x0010) || group > 0x0002) {
        break;
      }
      byte[] vr = {b[byteOffset], b[byteOffset + 1]};
      byteOffset += 2;

      int vl;
      if (isShortValueLengthVR(vr)) {
        vl = extractUnsigned16(b, byteOffset);
        byteOffset += 2;
      } else {
        // 2 reserved bytes
        // cast to int (should not be a big number in meta information)
        vl = (int) extractUnsigned32(b, byteOffset + 2);
        byteOffset += 6;
      }
      // (0x0002, 0x0010) Transfer Syntax UID
      if (element == 0x0010 && vl != 0 && byteOffset + vl < b.length) {
        break;
      }
      byteOffset += vl;
    }
  }

  static int extractUnsigned16(byte[] b, int offset) {
    if (offset + 1 >= b.length) {
      return 0;
    }
    int v1 = b[offset] & 0xff;
    int v2 = b[offset + 1] & 0xff;
    return (v2 << 8) | v1;
  }

  static long extractUnsigned32(byte[] b, int offset) {
    if (offset + 4 >= b.length) {
      return 0;
    }
    long v1 = ((long) b[offset]) & 0xff;
    long v2 = ((long) b[offset + 1]) & 0xff;
    long v3 = ((long) b[offset + 2]) & 0xff;
    long v4 = ((long) b[offset + 3]) & 0xff;
    return (((((v4 << 8) | v3) << 8) | v2) << 8) | v1;
  }

  static boolean isShortValueLengthVR(byte[] vr) {
    return vr[0] == 'A' && (vr[1] == 'E' || vr[1] == 'S' || vr[1] == 'T') // NON-NLS
        || vr[0] == 'C' && vr[1] == 'S' // NON-NLS
        || vr[0] == 'D' && (vr[1] == 'A' || vr[1] == 'S' || vr[1] == 'T') // NON-NLS
        || vr[0] == 'F' && (vr[1] == 'D' || vr[1] == 'L') // NON-NLS
        || vr[0] == 'I' && vr[1] == 'S' // NON-NLS
        || vr[0] == 'L' && (vr[1] == 'O' || vr[1] == 'T') // NON-NLS
        || vr[0] == 'P' && vr[1] == 'N' // NON-NLS
        || vr[0] == 'S' && (vr[1] == 'H' || vr[1] == 'L' || vr[1] == 'S' || vr[1] == 'T') // NON-NLS
        || vr[0] == 'T' && vr[1] == 'M' // NON-NLS
        || vr[0] == 'U' && (vr[1] == 'I' || vr[1] == 'L' || vr[1] == 'S'); // NON-NLS
  }
}
