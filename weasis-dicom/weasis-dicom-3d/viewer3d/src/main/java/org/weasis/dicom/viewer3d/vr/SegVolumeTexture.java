/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.util.GLPixelStorageModes;
import java.nio.IntBuffer;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.codec.seg.SegmentationVolume;

/**
 * A 3D OpenGL texture that stores a {@link SegmentationVolume}'s per-voxel <em>storage IDs</em> as
 * {@code GL_R32UI} (unsigned-integer single-channel 3D texture), paired with a 1D colour lookup
 * texture ({@code GL_RGBA8}) mapping each storage ID to its (pre-blended) segment RGBA colour.
 *
 * <p>The segmentation texture is bound on texture unit {@value #SEG_TEXTURE_UNIT} and the colour
 * LUT on unit {@value #SEG_COLOR_UNIT}. These units are above the ones used by the volume renderer
 * (0=volTexture, 1=colorMap, 2=lightingMap, 3=FBO output).
 *
 * <p>Because storage IDs are discrete labels (one per segment, plus one per overlap combination),
 * the 3D texture uses {@code GL_NEAREST} filtering. The shader resolves a voxel's RGBA colour with
 * a single {@code texelFetch(segColorMap, ivec2(id, 0), 0)} — no per-bit blending at render time;
 * combination IDs already point to a pre-composited colour computed by {@link
 * SegmentationVolume#buildSegmentColorLUT()}.
 */
public final class SegVolumeTexture {

  private static final Logger LOGGER = LoggerFactory.getLogger(SegVolumeTexture.class);

  /** Texture unit for the 3D segmentation storage-ID volume. */
  public static final int SEG_TEXTURE_UNIT = 4;

  /** Texture unit for the 1D segment colour lookup texture. */
  public static final int SEG_COLOR_UNIT = 5;

  private final SegmentationVolume segVolume;
  private final Vector3d scale;

  private int segTextureId;
  private int segColorTextureId;
  private boolean needsUpload = true;
  private boolean needsColorUpdate = true;

  /**
   * Creates a new SegVolumeTexture.
   *
   * <p>Acquires one {@link SegmentationVolume#retain()} reference on {@code segVolume}. The
   * matching {@link SegmentationVolume#release()} is issued in {@link #destroy(GL2ES2)} so the CPU
   * buffers are kept alive exactly as long as this texture object lives.
   *
   * @param segVolume the segmentation volume to upload
   * @param scale scale factors (segVolume dims / texture dims) for volumes that were down-sampled
   *     for the GL texture. Use (1,1,1) if the segVolume matches the DicomVolTexture dimensions.
   */
  public SegVolumeTexture(SegmentationVolume segVolume, Vector3d scale) {
    this.segVolume = segVolume;
    this.scale = new Vector3d(scale);
    segVolume.retain(); // SegVolumeTexture owns one reference to the backing CPU data
  }

  public SegmentationVolume getSegVolume() {
    return segVolume;
  }

  /**
   * Initialises the OpenGL textures. Must be called from the GL thread (or while the shared context
   * is current).
   */
  public void init(GL2ES2 gl) {
    if (segTextureId <= 0) {
      IntBuffer buf = IntBuffer.allocate(2);
      gl.glGenTextures(2, buf);
      segTextureId = buf.get(0);
      segColorTextureId = buf.get(1);
    }

    int sizeX = segVolume.getSizeX();
    int sizeY = segVolume.getSizeY();
    int sizeZ = segVolume.getSizeZ();

    // ---- 3D segmentation storage-ID texture (GL_R32UI) ----
    gl.glActiveTexture(GL.GL_TEXTURE0 + SEG_TEXTURE_UNIT);
    gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, segTextureId);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_R, GL.GL_CLAMP_TO_EDGE);

    // Allocate the 3D texture (GL_R32UI = unsigned int, one channel)
    gl.glTexImage3D(
        GL2ES2.GL_TEXTURE_3D,
        0,
        GL2GL3.GL_R32UI,
        sizeX,
        sizeY,
        sizeZ,
        0,
        GL2GL3.GL_RED_INTEGER,
        GL.GL_UNSIGNED_INT,
        null);

    // ---- 1D segment colour LUT texture (RGBA8) ----
    gl.glActiveTexture(GL.GL_TEXTURE0 + SEG_COLOR_UNIT);
    gl.glBindTexture(GL.GL_TEXTURE_2D, segColorTextureId);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);

    int segCount = Math.max(segVolume.getSegmentCount(), 1);
    byte[] colorLut = segVolume.buildSegmentColorLUT();
    // LUT width = number of allocated storage IDs (= segments + overlap combinations + slot 0
    // for background). Derived directly from the LUT byte length so it stays in sync with
    // SegmentationVolume.buildSegmentColorLUT().
    int lutWidth = Math.max(colorLut.length / 4, 1);
    gl.glTexImage2D(
        GL.GL_TEXTURE_2D,
        0,
        GL.GL_RGBA8,
        lutWidth,
        1,
        0,
        GL.GL_RGBA,
        GL.GL_UNSIGNED_BYTE,
        Buffers.newDirectByteBuffer(colorLut).rewind());

    gl.glActiveTexture(GL.GL_TEXTURE0);
    needsColorUpdate = false;
  }

  /**
   * Uploads the full SegmentationVolume storage-ID data into the 3D texture, slice by slice. This
   * should be called from the GL thread after {@link #init(GL2ES2)}.
   */
  public void uploadVolumeData(GL2ES2 gl) {
    if (segTextureId <= 0) {
      init(gl);
    }

    int sizeX = segVolume.getSizeX();
    int sizeY = segVolume.getSizeY();
    int sizeZ = segVolume.getSizeZ();

    gl.glActiveTexture(GL.GL_TEXTURE0 + SEG_TEXTURE_UNIT);
    gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, segTextureId);

    GLPixelStorageModes storageModes = new GLPixelStorageModes();
    storageModes.setPackAlignment(gl, 1);

    for (int z = 0; z < sizeZ; z++) {
      int[] sliceData = segVolume.exportSliceBitmask(z);
      if (sliceData != null) {
        IntBuffer buffer = Buffers.newDirectIntBuffer(sliceData);
        gl.glTexSubImage3D(
            GL2ES2.GL_TEXTURE_3D,
            0,
            0,
            0,
            z,
            sizeX,
            sizeY,
            1,
            GL2GL3.GL_RED_INTEGER,
            GL.GL_UNSIGNED_INT,
            buffer);
      }
    }

    storageModes.restore(gl);
    gl.glActiveTexture(GL.GL_TEXTURE0);
    needsUpload = false;

    LOGGER.info(
        "Uploaded SegmentationVolume 3D texture: {}x{}x{} ({} segments)",
        sizeX,
        sizeY,
        sizeZ,
        segVolume.getSegmentCount());
  }

  /**
   * Uploads the full volume data using the shared GL context (for background loading). Call from
   * any thread — this acquires the shared context internally.
   */
  public void uploadVolumeDataAsync() {
    GLContext glContext = OpenglUtils.getDefaultGlContext();
    glContext.makeCurrent();
    try {
      GL2ES2 gl = glContext.getGL().getGL2ES2();
      uploadVolumeData(gl);
      gl.glFinish();
    } finally {
      glContext.release();
    }
  }

  /**
   * Updates only the segment colour LUT texture using the SegmentationVolume's built-in attributes.
   * Cheap compared to re-uploading the entire 3D volume.
   */
  public void updateColorLUT(GL2ES2 gl) {
    updateColorLUT(gl, segVolume.buildSegmentColorLUT());
  }

  /**
   * Updates only the segment colour LUT texture with the given externally-built LUT data. This
   * allows the caller to supply visibility / opacity overrides (e.g. from the UI tree).
   *
   * @param gl the current GL context
   * @param colorLut a byte[] of size (segmentCount * 4) in RGBA8 format
   */
  public void updateColorLUT(GL2ES2 gl, byte[] colorLut) {
    if (segColorTextureId <= 0) {
      return;
    }
    int lutWidth = Math.max(colorLut.length / 4, 1);

    gl.glActiveTexture(GL.GL_TEXTURE0 + SEG_COLOR_UNIT);
    gl.glBindTexture(GL.GL_TEXTURE_2D, segColorTextureId);
    gl.glTexImage2D(
        GL.GL_TEXTURE_2D,
        0,
        GL.GL_RGBA8,
        lutWidth,
        1,
        0,
        GL.GL_RGBA,
        GL.GL_UNSIGNED_BYTE,
        Buffers.newDirectByteBuffer(colorLut).rewind());
    gl.glActiveTexture(GL.GL_TEXTURE0);
    needsColorUpdate = false;
  }

  /** Marks the colour LUT as needing an update on the next render pass. */
  public void setNeedsColorUpdate() {
    this.needsColorUpdate = true;
  }

  /**
   * Binds the segmentation textures to their respective texture units for rendering. Call this
   * before dispatching the volume rendering shader.
   */
  public void bind(GL2ES2 gl) {
    if (segTextureId <= 0) {
      return;
    }

    if (needsUpload) {
      uploadVolumeData(gl);
    }
    if (needsColorUpdate) {
      updateColorLUT(gl);
    }

    gl.glActiveTexture(GL.GL_TEXTURE0 + SEG_TEXTURE_UNIT);
    gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, segTextureId);
    gl.glActiveTexture(GL.GL_TEXTURE0 + SEG_COLOR_UNIT);
    gl.glBindTexture(GL.GL_TEXTURE_2D, segColorTextureId);
    gl.glActiveTexture(GL.GL_TEXTURE0);
  }

  /** Returns {@code true} if the 3D texture has been successfully allocated and uploaded. */
  public boolean isReady() {
    return segTextureId > 0 && !needsUpload;
  }

  /** Returns the scale factors for mapping between segVolume and DicomVolTexture coordinates. */
  public Vector3d getScale() {
    return new Vector3d(scale);
  }

  /** Releases all OpenGL resources and the CPU-buffer reference. Must be called from the GL thread. */
  public void destroy(GL2ES2 gl) {
    if (segTextureId > 0) {
      gl.glDeleteTextures(2, new int[] {segTextureId, segColorTextureId}, 0);
      segTextureId = 0;
      segColorTextureId = 0;
    }
    // Release ownership of the backing CPU data. If no other consumer retains the volume
    // (e.g. MPR was never open or has already closed), this will call removeData().
    segVolume.release();
  }
}
