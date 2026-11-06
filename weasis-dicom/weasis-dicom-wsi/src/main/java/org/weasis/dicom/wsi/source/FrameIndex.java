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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;
import org.dcm4che3.data.BulkData;

/**
 * Byte ranges of the frames of one instance in its file, so that reading a frame is one positional
 * read per fragment, without parsing the file again.
 */
final class FrameIndex {

  /** Length of an item header (tag and length) in encapsulated pixel data (PS3.5 A.4). */
  private static final int ITEM_HEADER = 8;

  private final long[] offsets;
  private final int[] lengths;
  // Index of the first segment of each frame, plus one past the last segment
  private final int[] frameStarts;

  private FrameIndex(long[] offsets, int[] lengths, int[] frameStarts) {
    this.offsets = offsets;
    this.lengths = lengths;
    this.frameStarts = frameStarts;
  }

  /** Native (uncompressed) pixel data: fixed-length frames one after the other. */
  static FrameIndex ofNative(long offset, int frameLength, int frames) {
    long[] offsets = new long[frames];
    int[] lengths = new int[frames];
    int[] starts = new int[frames + 1];
    for (int i = 0; i < frames; i++) {
      offsets[i] = offset + (long) i * frameLength;
      lengths[i] = frameLength;
      starts[i] = i;
    }
    starts[frames] = frames;
    return new FrameIndex(offsets, lengths, starts);
  }

  /**
   * Encapsulated pixel data (PS3.5 A.4): one fragment per frame, or several located by the offset
   * table, or else by the start of each codestream.
   *
   * @param fragments the fragment items after the Basic Offset Table
   * @param offsetTable frame offsets from the Basic or Extended Offset Table, or null when empty
   */
  static FrameIndex ofFragments(
      List<BulkData> fragments, int frames, long[] offsetTable, FileChannel channel)
      throws IOException {
    int count = fragments.size();
    long[] offsets = new long[count];
    int[] lengths = new int[count];
    for (int i = 0; i < count; i++) {
      offsets[i] = fragments.get(i).offset();
      lengths[i] = fragments.get(i).length();
    }
    int[] starts;
    if (count == frames) {
      starts = sequence(frames);
    } else if (frames == 1) {
      starts = new int[] {0};
    } else if (offsetTable != null && offsetTable.length == frames) {
      starts = startsFromTable(offsets, offsetTable);
    } else {
      starts = startsFromCodestreams(offsets, frames, channel);
    }
    if (starts.length != frames || count == 0 || starts[0] != 0) {
      throw new IOException("Cannot match %d fragments to %d frames".formatted(count, frames));
    }
    int[] frameStarts = new int[frames + 1];
    System.arraycopy(starts, 0, frameStarts, 0, frames);
    frameStarts[frames] = count;
    return new FrameIndex(offsets, lengths, frameStarts);
  }

  int frames() {
    return frameStarts.length - 1;
  }

  /** The encoded bytes of a frame, from 0, in a heap buffer whose array is exactly the frame. */
  ByteBuffer read(FileChannel channel, int frame) throws IOException {
    int from = frameStarts[frame];
    int to = frameStarts[frame + 1];
    int total = 0;
    for (int i = from; i < to; i++) {
      total = Math.addExact(total, lengths[i]);
    }
    ByteBuffer buffer = ByteBuffer.allocate(total);
    for (int i = from; i < to; i++) {
      buffer.limit(buffer.position() + lengths[i]);
      readFully(channel, buffer, offsets[i]);
    }
    return buffer.flip();
  }

  /** Fills the remaining space of the buffer from the file position. */
  static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
    while (buffer.hasRemaining()) {
      int n = channel.read(buffer, position);
      if (n < 0) {
        throw new EOFException("Value ends beyond the end of the file");
      }
      position += n;
    }
  }

  private static int[] sequence(int n) {
    int[] values = new int[n];
    for (int i = 0; i < n; i++) {
      values[i] = i;
    }
    return values;
  }

  // Table offsets count from the first byte of the first fragment item, item headers included
  private static int[] startsFromTable(long[] offsets, long[] table) throws IOException {
    long base = offsets[0] - ITEM_HEADER;
    int[] starts = new int[table.length];
    int fragment = 0;
    for (int frame = 0; frame < table.length; frame++) {
      long itemStart = base + table[frame];
      while (fragment < offsets.length && offsets[fragment] - ITEM_HEADER < itemStart) {
        fragment++;
      }
      if (fragment == offsets.length || offsets[fragment] - ITEM_HEADER != itemStart) {
        throw new IOException("Offset table entry %d matches no fragment".formatted(frame + 1));
      }
      starts[frame] = fragment;
    }
    return starts;
  }

  private static int[] startsFromCodestreams(long[] offsets, int frames, FileChannel channel)
      throws IOException {
    int[] starts = new int[offsets.length];
    int found = 0;
    ByteBuffer head = ByteBuffer.allocate(4);
    for (int i = 0; i < offsets.length; i++) {
      head.clear();
      channel.read(head, offsets[i]);
      if (head.position() == head.capacity() && isCodestreamStart(head.flip())) {
        starts[found++] = i;
      }
    }
    // Any other count is ambiguous: a fragment may start with bytes that look like a codestream
    return found == frames ? Arrays.copyOf(starts, found) : new int[0];
  }

  /** JPEG and JPEG-LS SOI, JPEG 2000 SOC or JP2 signature box, JPEG XL codestream signature. */
  static boolean isCodestreamStart(ByteBuffer head) {
    int b0 = head.get(0) & 0xFF;
    int b1 = head.get(1) & 0xFF;
    if (b0 == 0xFF) {
      return b1 == 0xD8 || b1 == 0x4F || b1 == 0x0A;
    }
    return head.getInt(0) == 0x0000000C;
  }
}
