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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.io.BulkDataDescriptor;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.SlideBuilder;

/**
 * Whole slide instances read from local files: each header is parsed once to build the slides and a
 * frame index, then frames are read by position.
 */
public final class LocalFrameStore implements FrameStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(LocalFrameStore.class);

  private final Map<String, LocalInstanceFrames> instances;
  private final List<Slide> slides;

  private LocalFrameStore(Map<String, LocalInstanceFrames> instances, List<Slide> slides) {
    this.instances = instances;
    this.slides = slides;
  }

  /**
   * Opens the whole slide instances among the files; files of another SOP class or that cannot be
   * read are skipped.
   */
  public static LocalFrameStore open(Collection<Path> files) {
    Map<String, LocalInstanceFrames> instances = new LinkedHashMap<>();
    List<Attributes> headers = new ArrayList<>();
    for (Path file : files) {
      try {
        Attributes header = readHeader(file);
        if (!SlideBuilder.isWholeSlide(header)) {
          continue;
        }
        String uid = header.getString(Tag.SOPInstanceUID);
        if (instances.containsKey(uid)) {
          LOGGER.debug("Skip duplicate instance {}: {}", uid, file);
          continue;
        }
        instances.put(uid, openFrames(file, header));
        headers.add(header);
      } catch (IOException | RuntimeException e) {
        LOGGER.warn("Cannot read the whole slide file {}", file, e);
      }
    }
    return new LocalFrameStore(instances, SlideBuilder.build(headers));
  }

  /** The slides of the files, the largest first. */
  public List<Slide> slides() {
    return slides;
  }

  @Override
  public BytesWithImageDescriptor frames(String sopInstanceUID) throws IOException {
    LocalInstanceFrames frames = instances.get(sopInstanceUID);
    if (frames == null) {
      throw new IOException("No local file for instance " + sopInstanceUID);
    }
    return frames;
  }

  @Override
  public void close() {
    instances.values().forEach(LocalInstanceFrames::close);
  }

  // The pixel data stays in the file as offsets (BulkData), as the DICOM reader keeps it; the
  // descriptor of dcm4che leaves the native library unloaded
  private static Attributes readHeader(Path file) throws IOException {
    try (InputStream in = Files.newInputStream(file);
        DicomInputStream dis = new DicomInputStream(in)) {
      dis.setIncludeBulkData(IncludeBulkData.URI);
      dis.setBulkDataDescriptor(BulkDataDescriptor.PIXELDATA);
      dis.setURI(file.toUri().toString());
      Attributes fmi = dis.readFileMetaInformation();
      Attributes header = dis.readDataset();
      String tsuid = fmi == null ? dis.getTransferSyntax() : fmi.getString(Tag.TransferSyntaxUID);
      header.setString(Tag.TransferSyntaxUID, VR.UI, tsuid);
      return header;
    }
  }

  private static LocalInstanceFrames openFrames(Path file, Attributes header) throws IOException {
    ImageDescriptor descriptor = new ImageDescriptor(header);
    String tsuid = header.getString(Tag.TransferSyntaxUID);
    VR.Holder vr = new VR.Holder();
    Object pixelData = header.getValue(Tag.PixelData, vr);
    FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
    try {
      FrameIndex index;
      boolean bigEndian;
      if (pixelData instanceof Fragments fragments) {
        bigEndian = fragments.bigEndian();
        index =
            FrameIndex.ofFragments(
                fragmentItems(fragments),
                descriptor.getFrames(),
                offsetTable(header, fragments, channel),
                channel);
      } else if (pixelData instanceof BulkData bulk) {
        bigEndian = bulk.bigEndian();
        int frameLength =
            descriptor
                .getPhotometricInterpretation()
                .frameLength(
                    descriptor.getColumns(),
                    descriptor.getRows(),
                    descriptor.getSamples(),
                    descriptor.getBitsAllocated());
        index = FrameIndex.ofNative(bulk.offset(), frameLength, descriptor.getFrames());
      } else {
        throw new IOException("No pixel data in the file");
      }
      return new LocalInstanceFrames(channel, descriptor, tsuid, vr.vr, bigEndian, index);
    } catch (IOException | RuntimeException e) {
      channel.close();
      throw e;
    }
  }

  // Empty fragment items carry no data (and no offset): they are left out
  private static List<BulkData> fragmentItems(Fragments fragments) {
    List<BulkData> items = new ArrayList<>(fragments.size());
    for (int i = 1; i < fragments.size(); i++) {
      if (fragments.get(i) instanceof BulkData bulk) {
        items.add(bulk);
      }
    }
    return items;
  }

  /** Frame offsets of the Extended Offset Table, else of the Basic Offset Table; null if none. */
  private static long[] offsetTable(Attributes header, Fragments fragments, FileChannel channel)
      throws IOException {
    ByteBuffer extended = bytes(header.getValue(Tag.ExtendedOffsetTable), channel);
    if (extended != null && extended.remaining() >= Long.BYTES) {
      long[] table = new long[extended.remaining() / Long.BYTES];
      extended.asLongBuffer().get(table);
      return table;
    }
    ByteBuffer basic = fragments.isEmpty() ? null : bytes(fragments.getFirst(), channel);
    if (basic != null && basic.remaining() >= Integer.BYTES) {
      long[] table = new long[basic.remaining() / Integer.BYTES];
      for (int i = 0; i < table.length; i++) {
        table[i] = Integer.toUnsignedLong(basic.getInt());
      }
      return table;
    }
    return null;
  }

  // Encapsulated pixel data is always little endian (PS3.5 A.4)
  private static ByteBuffer bytes(Object value, FileChannel channel) throws IOException {
    ByteBuffer buffer =
        switch (value) {
          case byte[] b when b.length > 0 -> ByteBuffer.wrap(b);
          case BulkData bulk when bulk.length() > 0 -> {
            ByteBuffer b = ByteBuffer.allocate(bulk.length());
            FrameIndex.readFully(channel, b, bulk.offset());
            yield b.flip();
          }
          case null, default -> null;
        };
    return buffer == null ? null : buffer.order(ByteOrder.LITTLE_ENDIAN);
  }
}
