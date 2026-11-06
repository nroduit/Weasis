/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.source;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The frames of one local instance, read by position from a channel kept open; safe for concurrent
 * reads.
 */
final class LocalInstanceFrames implements BytesWithImageDescriptor, AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(LocalInstanceFrames.class);

  private final FileChannel channel;
  private final ImageDescriptor descriptor;
  private final String transferSyntax;
  private final VR pixelDataVR;
  private final boolean bigEndian;
  private final FrameIndex index;

  LocalInstanceFrames(
      FileChannel channel,
      ImageDescriptor descriptor,
      String transferSyntax,
      VR pixelDataVR,
      boolean bigEndian,
      FrameIndex index) {
    this.channel = channel;
    this.descriptor = descriptor;
    this.transferSyntax = transferSyntax;
    this.pixelDataVR = pixelDataVR;
    this.bigEndian = bigEndian;
    this.index = index;
  }

  @Override
  public ByteBuffer getBytes(int frame) throws IOException {
    if (frame < 0 || frame >= index.frames()) {
      throw new IllegalArgumentException("No frame " + frame);
    }
    return index.read(channel, frame);
  }

  @Override
  public ImageDescriptor getImageDescriptor() {
    return descriptor;
  }

  @Override
  public String getTransferSyntax() {
    return transferSyntax;
  }

  @Override
  public boolean isBigEndian() {
    return bigEndian;
  }

  @Override
  public VR getPixelDataVR() {
    return pixelDataVR;
  }

  @Override
  public void close() {
    try {
      channel.close();
    } catch (IOException e) {
      LOGGER.warn("Closing {}", descriptor.getSopInstanceUID(), e);
    }
  }
}
