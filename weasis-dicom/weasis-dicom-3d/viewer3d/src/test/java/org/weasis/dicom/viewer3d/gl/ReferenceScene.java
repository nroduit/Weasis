/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.gl;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4d;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.viewer3d.vr.AccumulationBuffers;
import org.weasis.dicom.viewer3d.vr.EnvironmentMap;
import org.weasis.dicom.viewer3d.vr.EnvironmentTexture;
import org.weasis.dicom.viewer3d.vr.Preset;
import org.weasis.dicom.viewer3d.vr.Program;
import org.weasis.dicom.viewer3d.vr.RenderingType;
import org.weasis.dicom.viewer3d.vr.SegVolumeTexture;
import org.weasis.dicom.viewer3d.vr.ShaderManager;
import org.weasis.dicom.viewer3d.vr.TextureData.PixelFormat;
import org.weasis.dicom.viewer3d.vr.VolumeTexture;

/**
 * Renders {@link SyntheticVolume} the way {@code View3d} does, without Swing: the same programs,
 * the same texture units and the same uniform names, driven by fixed values instead of the live
 * view state. It is the reference the OpenGL back-ends are compared against — it pins the shader
 * interface and the GL call sequence of a frame, not {@code View3d}'s uniform policy.
 *
 * <p>Both rendering paths are supported and produce a display image the same way the viewer does:
 * the ray-cast output texture is blitted by the quad program, here into an RGBA8 framebuffer that
 * stands in for the panel's back buffer, then read back with {@code glReadPixels}.
 */
final class ReferenceScene implements AutoCloseable {

  /** Which of the two implementations of the ray-caster is exercised. */
  enum RayCaster {
    COMPUTE,
    FBO
  }

  /** A frame to render. Everything the reference frames depend on is named here. */
  record Frame(
      String name,
      RenderingType type,
      boolean shading,
      boolean cinematic,
      int pathTracingFrames,
      boolean segOverlay,
      boolean crosshair) {

    static Frame of(String name, RenderingType type) {
      return new Frame(name, type, false, false, 0, false, false);
    }

    Frame shaded() {
      return new Frame(name, type, true, cinematic, pathTracingFrames, segOverlay, crosshair);
    }

    Frame cinematicLit() {
      return new Frame(name, type, true, true, pathTracingFrames, segOverlay, crosshair);
    }

    Frame pathTraced(int frames) {
      return new Frame(name, type, true, true, frames, segOverlay, crosshair);
    }

    Frame withSeg() {
      return new Frame(name, type, shading, cinematic, pathTracingFrames, true, crosshair);
    }

    Frame withCrosshair() {
      return new Frame(name, type, shading, cinematic, pathTracingFrames, segOverlay, true);
    }
  }

  private static final int COMPUTE_LOCAL_SIZE = 16;

  // Camera: CameraView.INITIAL, no pan, no zoom — the orientation a volume opens at.
  private static final Quaterniond ROTATION =
      new Quaterniond().rotationXYZ(Math.toRadians(-75), 0, Math.toRadians(15));
  private static final double FOV = 45;
  private static final double Z_NEAR = 0.1;
  private static final double Z_FAR = 10000;

  // Rendering state, fixed so the baseline never moves with a preference default.
  private static final int SAMPLE_COUNT = 1024;
  private static final float OPACITY = 1f;
  private static final float WINDOW_WIDTH = 4095f;
  private static final float WINDOW_CENTER = 2047.5f;
  private static final float[] BACKGROUND = {0.5f, 0.5f, 0.5f};
  private static final float[] LIGHT_COLOR = {1f, 1f, 1f};
  private static final float SPECULAR_POWER = 40f;
  private static final float[] KEY_LIGHT_DIR = {0.32f, 0.45f, 0.83f};
  private static final float SHADOW_STRENGTH = 0.6f;
  private static final int SHADOW_STEPS = 24;
  private static final float AO_STRENGTH = 0.4f;
  private static final int AO_STEPS = 3;
  private static final float EXPOSURE = 1f;
  private static final float ENV_STRENGTH = 1f;
  private static final int PT_MAX_BOUNCES = 4;

  /** {@code MajorantMap.TEXTURE_UNIT}, private to the renderer's package. */
  private static final int MAJORANT_UNIT = 11;

  private final Gl gl;
  private final RayCaster rayCaster;
  private final int width;
  private final int height;

  private final Program program;
  private final Program tracerProgram;
  private final Program denoiseProgram;
  private final Program quadProgram;
  private final VolumeTexture volume;
  private final Preset preset;
  private final EnvironmentTexture environment;
  private final AccumulationBuffers accumulation = new AccumulationBuffers();

  private final int outputTexture;
  private final int rayFbo;

  /** The production target: the same framebuffer, pack buffer and image pair a view uses. */
  private final RenderTarget display = new RenderTarget();

  private final int vertexBuffer;
  private final int vertexArray;
  private final int segTexture;
  private final int segColorTexture;

  /**
   * A single empty block: the tracer then walks the volume at its global bound, as while loading.
   */
  private final int majorantPlaceholder;

  /** Read live by the uniform setters, exactly as the view's own state is. */
  private Frame frame = Frame.of("composite", RenderingType.COMPOSITE); // NON-NLS

  private int frameIndex;

  /** Ray-cast samples along a ray, {@code RenderingLayer}'s default quality. */
  private int sampleCount = SAMPLE_COUNT;

  /**
   * Fraction of the target the ray-cast pass covers. Below 1 only on the FBO path, where the viewer
   * ray-casts the logical-resolution sub-rectangle while the camera is being dragged.
   */
  private float passScale = 1f;

  ReferenceScene(Gl gl, RayCaster rayCaster, int width, int height) {
    this.gl = gl;
    this.rayCaster = rayCaster;
    this.width = width;
    this.height = height;

    if (rayCaster == RayCaster.COMPUTE) {
      this.program = new Program("compute", ShaderManager.COMPUTE_SHADER); // NON-NLS
      this.tracerProgram =
          new Program("pathTracing", ShaderManager.PATH_TRACING_COMPUTE_SHADER); // NON-NLS
      this.denoiseProgram = new Program("denoise", ShaderManager.DENOISE_COMPUTE_SHADER); // NON-NLS
    } else {
      this.program =
          new Program(
              "fbo", ShaderManager.FBO_VERTEX_SHADER, ShaderManager.FBO_FRAGMENT_SHADER); // NON-NLS
      this.tracerProgram =
          new Program(
              "pathTracingFbo", // NON-NLS
              ShaderManager.FBO_VERTEX_SHADER,
              ShaderManager.PATH_TRACING_FBO_FRAGMENT_SHADER);
      this.denoiseProgram =
          new Program(
              "denoiseFbo", // NON-NLS
              ShaderManager.FBO_VERTEX_SHADER,
              ShaderManager.DENOISE_FBO_FRAGMENT_SHADER);
    }
    this.quadProgram =
        new Program("basic", ShaderManager.VERTEX_SHADER, ShaderManager.FRAGMENT_SHADER); // NON-NLS

    this.volume = uploadVolume();
    this.preset = Preset.getDefaultPreset(Modality.CT);
    this.preset.init(gl);
    this.environment = new EnvironmentTexture(EnvironmentMap.STUDIO.bake());
    this.environment.init(gl);

    int[] segNames = gl.glGenTextures(2);
    this.segTexture = segNames[0];
    this.segColorTexture = segNames[1];
    uploadSegmentation();
    this.majorantPlaceholder = uploadMajorantPlaceholder();

    this.outputTexture = allocateOutput();
    this.rayFbo = gl.glGenFramebuffers(1)[0];
    gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, rayFbo);
    gl.glFramebufferTexture2D(
        Gl.GL_FRAMEBUFFER, Gl.GL_COLOR_ATTACHMENT0, Gl.GL_TEXTURE_2D, outputTexture, 0);
    gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, 0);

    display.ensureSize(gl, width, height);

    // A core profile has no default vertex array object.
    this.vertexArray = gl.glGenVertexArray();
    gl.glBindVertexArray(vertexArray);
    this.vertexBuffer = uploadQuad();
    allocateUniforms();
    gl.glClearColor(BACKGROUND[0], BACKGROUND[1], BACKGROUND[2], 1f);
  }

  /** The vertices of the full-screen quad, as {@code View3d.vertexBufferData}. */
  private static final float[] QUAD = {
    -1f, -1f, 1f, -1f, -1f, 1f,
    -1f, 1f, 1f, -1f, 1f, 1f
  };

  private int uploadQuad() {
    int buffer = gl.glGenBuffers(1)[0];
    gl.glBindBuffer(Gl.GL_ARRAY_BUFFER, buffer);
    try (Arena arena = Arena.ofConfined()) {
      gl.glBufferData(
          Gl.GL_ARRAY_BUFFER,
          (long) QUAD.length * Float.BYTES,
          arena.allocateFrom(ValueLayout.JAVA_FLOAT, QUAD),
          Gl.GL_STATIC_DRAW);
    }
    return buffer;
  }

  private VolumeTexture uploadVolume() {
    int size = SyntheticVolume.SIZE;
    VolumeTexture texture =
        new VolumeTexture(new Vector3i(size, size, size), PixelFormat.UNSIGNED_SHORT);
    texture.init(gl);
    gl.glActiveTexture(Gl.GL_TEXTURE0);
    gl.glBindTexture(Gl.GL_TEXTURE_3D, texture.getId());
    gl.glPixelStorei(Gl.GL_UNPACK_ALIGNMENT, 1);
    try (Arena arena = Arena.ofConfined()) {
      gl.glTexSubImage3D(
          Gl.GL_TEXTURE_3D,
          0,
          0,
          0,
          0,
          size,
          size,
          size,
          texture.getFormat(),
          texture.getType(),
          arena.allocateFrom(ValueLayout.JAVA_SHORT, SyntheticVolume.values()));
    }
    gl.glPixelStorei(Gl.GL_UNPACK_ALIGNMENT, 4);
    texture.setRequiredBuilding(false);
    return texture;
  }

  // Storage-id volume on unit 4 and its colour table on unit 5, as SegVolumeTexture lays them out.
  private void uploadSegmentation() {
    int size = SyntheticVolume.SIZE;
    gl.glActiveTexture(Gl.GL_TEXTURE0 + SegVolumeTexture.SEG_TEXTURE_UNIT);
    gl.glBindTexture(Gl.GL_TEXTURE_3D, segTexture);
    gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_NEAREST);
    gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_NEAREST);
    gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_WRAP_S, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_WRAP_T, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_WRAP_R, Gl.GL_CLAMP_TO_EDGE);
    gl.glPixelStorei(Gl.GL_UNPACK_ALIGNMENT, 1);
    try (Arena arena = Arena.ofConfined()) {
      gl.glTexImage3D(
          Gl.GL_TEXTURE_3D,
          0,
          Gl.GL_R8UI,
          size,
          size,
          size,
          0,
          Gl.GL_RED_INTEGER,
          Gl.GL_UNSIGNED_BYTE,
          arena.allocateFrom(ValueLayout.JAVA_BYTE, SyntheticVolume.labels()));

      // Storage id 0 is the background, 1 and 2 the two segments, pre-blended as the codec does.
      byte[] lut = {
        0, 0, 0, 0, (byte) 220, 60, 60, (byte) 160, 60, (byte) 180, (byte) 220, (byte) 200
      };
      gl.glActiveTexture(Gl.GL_TEXTURE0 + SegVolumeTexture.SEG_COLOR_UNIT);
      gl.glBindTexture(Gl.GL_TEXTURE_2D, segColorTexture);
      gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_NEAREST);
      gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_NEAREST);
      gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_S, Gl.GL_CLAMP_TO_EDGE);
      gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_T, Gl.GL_CLAMP_TO_EDGE);
      gl.glTexImage2D(
          Gl.GL_TEXTURE_2D,
          0,
          Gl.GL_RGBA8,
          lut.length / 4,
          1,
          0,
          Gl.GL_RGBA,
          Gl.GL_UNSIGNED_BYTE,
          arena.allocateFrom(ValueLayout.JAVA_BYTE, lut));
    }
    gl.glPixelStorei(Gl.GL_UNPACK_ALIGNMENT, 4);
    gl.glActiveTexture(Gl.GL_TEXTURE0);
  }

  // RGBA32F for the compute path's image unit, RGBA16F for the FBO colour attachment, as in the
  // viewer.
  private int allocateOutput() {
    int format = rayCaster == RayCaster.COMPUTE ? Gl.GL_RGBA32F : Gl.GL_RGBA16F;
    int unit = rayCaster == RayCaster.COMPUTE ? 0 : 3;
    int texture = gl.glGenTextures(1)[0];
    gl.glActiveTexture(Gl.GL_TEXTURE0 + unit);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, texture);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_S, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_T, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_LINEAR);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_LINEAR);
    gl.glTexImage2D(
        Gl.GL_TEXTURE_2D, 0, format, width, height, 0, Gl.GL_RGBA, Gl.GL_FLOAT, MemorySegment.NULL);
    gl.glActiveTexture(Gl.GL_TEXTURE0);
    return texture;
  }

  private int uploadMajorantPlaceholder() {
    int texture = gl.glGenTextures(1)[0];
    gl.glActiveTexture(Gl.GL_TEXTURE0 + MAJORANT_UNIT);
    gl.glBindTexture(Gl.GL_TEXTURE_3D, texture);
    gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_NEAREST);
    gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_NEAREST);
    try (Arena arena = Arena.ofConfined()) {
      gl.glTexImage3D(
          Gl.GL_TEXTURE_3D,
          0,
          Gl.GL_R32F,
          1,
          1,
          1,
          0,
          Gl.GL_RED,
          Gl.GL_FLOAT,
          arena.allocateFrom(ValueLayout.JAVA_FLOAT, 0f));
    }
    gl.glActiveTexture(Gl.GL_TEXTURE0);
    return texture;
  }

  /** Renders one frame and returns the display image as packed ARGB, top row first. */
  int[] render(Frame requested) {
    draw(requested);
    return read();
  }

  /** The GPU stage of a frame: the ray-cast passes and the quad blit, without the read-back. */
  void draw(Frame requested) {
    this.frame = requested;
    this.frameIndex = 0;
    int passes = Math.max(1, requested.pathTracingFrames());
    boolean pathTracing = requested.pathTracingFrames() > 0;

    for (int pass = 0; pass < passes; pass++) {
      if (pathTracing) {
        accumulation.prepare(gl, width, height);
      } else {
        accumulation.bindHistory(gl);
      }
      Program active = pathTracing ? tracerProgram : program;
      active.use(gl);
      active.setUniforms(gl);
      volume.render(gl);
      preset.render(gl);
      bindSegTextures();
      environment.render(gl);
      if (pathTracing) {
        gl.glActiveTexture(Gl.GL_TEXTURE0 + MAJORANT_UNIT);
        gl.glBindTexture(Gl.GL_TEXTURE_3D, majorantPlaceholder);
        gl.glActiveTexture(Gl.GL_TEXTURE0);
        rayCast(
            accumulation.getWriteId(),
            accumulation.getFeatureWriteId(),
            accumulation.getMomentWriteId());
      } else {
        rayCast(0, 0, 0);
      }
      if (pathTracing) {
        accumulation.swap();
        frameIndex++;
        accumulation.bindHistory(gl);
        denoiseProgram.use(gl);
        denoiseProgram.setUniforms(gl);
        denoise();
      }
    }
    blit();
  }

  /**
   * The read-back stage: the display image as packed ARGB, bottom row first as {@code glReadPixels}
   * returns it — the viewer flips it in the quad pass, which costs nothing, so flipping it here
   * would only add a copy the measurement should not carry. The array is reused from one frame to
   * the next, so a caller that keeps it must copy it.
   */
  int[] read() {
    return readDisplay();
  }

  private void bindSegTextures() {
    gl.glActiveTexture(Gl.GL_TEXTURE0 + SegVolumeTexture.SEG_TEXTURE_UNIT);
    gl.glBindTexture(Gl.GL_TEXTURE_3D, segTexture);
    gl.glActiveTexture(Gl.GL_TEXTURE0 + SegVolumeTexture.SEG_COLOR_UNIT);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, segColorTexture);
    gl.glActiveTexture(Gl.GL_TEXTURE0);
  }

  private void rayCast(int accumulationId, int featureId, int momentId) {
    if (rayCaster == RayCaster.COMPUTE) {
      gl.glBindImageTexture(0, outputTexture, 0, false, 0, Gl.GL_WRITE_ONLY, Gl.GL_RGBA32F);
      if (accumulationId > 0) {
        gl.glBindImageTexture(1, accumulationId, 0, false, 0, Gl.GL_WRITE_ONLY, Gl.GL_RGBA32F);
        gl.glBindImageTexture(2, featureId, 0, false, 0, Gl.GL_WRITE_ONLY, Gl.GL_RGBA32F);
        gl.glBindImageTexture(3, momentId, 0, false, 0, Gl.GL_WRITE_ONLY, Gl.GL_RG32F);
      }
      dispatch();
    } else {
      gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, rayFbo);
      attachAccumulation(accumulationId, featureId, momentId);
      gl.glViewport(0, 0, passWidth(), passHeight());
      gl.glClear(Gl.GL_COLOR_BUFFER_BIT);
      drawQuad();
      gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, 0);
    }
  }

  private void denoise() {
    if (rayCaster == RayCaster.COMPUTE) {
      dispatch();
    } else {
      gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, rayFbo);
      attachAccumulation(0, 0, 0);
      gl.glViewport(0, 0, width, height);
      drawQuad();
      gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, 0);
    }
  }

  private int passWidth() {
    return Math.max(1, Math.round(width * passScale));
  }

  private int passHeight() {
    return Math.max(1, Math.round(height * passScale));
  }

  /** Samples per ray; the viewer takes this from the quality preference. */
  void setSampleCount(int sampleCount) {
    this.sampleCount = sampleCount;
  }

  /** Reduced ray-cast rectangle of an interactive frame. Only the FBO path renders below 1. */
  void setPassScale(float passScale) {
    if (rayCaster == RayCaster.COMPUTE && passScale != 1f) {
      throw new IllegalArgumentException("The compute path always fills its image");
    }
    this.passScale = passScale;
  }

  private void dispatch() {
    int groupsX = Math.max(1, (width + COMPUTE_LOCAL_SIZE - 1) / COMPUTE_LOCAL_SIZE);
    int groupsY = Math.max(1, (height + COMPUTE_LOCAL_SIZE - 1) / COMPUTE_LOCAL_SIZE);
    gl.glDispatchCompute(groupsX, groupsY, 1);
    gl.glMemoryBarrier(Gl.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
  }

  private void attachAccumulation(int colorId, int featureId, int momentId) {
    gl.glFramebufferTexture2D(
        Gl.GL_FRAMEBUFFER, Gl.GL_COLOR_ATTACHMENT1, Gl.GL_TEXTURE_2D, colorId, 0);
    gl.glFramebufferTexture2D(
        Gl.GL_FRAMEBUFFER, Gl.GL_COLOR_ATTACHMENT2, Gl.GL_TEXTURE_2D, featureId, 0);
    gl.glFramebufferTexture2D(
        Gl.GL_FRAMEBUFFER, Gl.GL_COLOR_ATTACHMENT3, Gl.GL_TEXTURE_2D, momentId, 0);
    if (colorId > 0) {
      gl.glDrawBuffers(
          Gl.GL_COLOR_ATTACHMENT0,
          Gl.GL_COLOR_ATTACHMENT1,
          Gl.GL_COLOR_ATTACHMENT2,
          Gl.GL_COLOR_ATTACHMENT3);
    } else {
      gl.glDrawBuffers(Gl.GL_COLOR_ATTACHMENT0);
    }
  }

  private void drawQuad() {
    gl.glEnableVertexAttribArray(0);
    gl.glBindBuffer(Gl.GL_ARRAY_BUFFER, vertexBuffer);
    gl.glVertexAttribPointer(0, 2, Gl.GL_FLOAT, false, 0, 0);
    gl.glDrawArrays(Gl.GL_TRIANGLES, 0, QUAD.length / 2);
    gl.glDisableVertexAttribArray(0);
  }

  // The quad pass of the viewer, into the render target a view paints from.
  private void blit() {
    display.bind(gl);
    gl.glClear(Gl.GL_COLOR_BUFFER_BIT | Gl.GL_DEPTH_BUFFER_BIT);

    quadProgram.use(gl);
    quadProgram.setUniforms(gl);
    gl.glEnable(Gl.GL_BLEND);
    gl.glBlendFunc(Gl.GL_SRC_ALPHA, Gl.GL_ONE_MINUS_SRC_ALPHA);
    int unit = rayCaster == RayCaster.COMPUTE ? 0 : 3;
    gl.glActiveTexture(Gl.GL_TEXTURE0 + unit);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, outputTexture);
    drawQuad();
    gl.glDisable(Gl.GL_BLEND);
    display.unbind(gl);
  }

  private int[] readDisplay() {
    display.read(gl);
    return ((java.awt.image.DataBufferInt) display.image().getRaster().getDataBuffer()).getData();
  }

  @Override
  public void close() {
    program.destroy(gl);
    tracerProgram.destroy(gl);
    denoiseProgram.destroy(gl);
    quadProgram.destroy(gl);
    volume.destroy(gl);
    preset.destroy(gl);
    environment.destroy(gl);
    accumulation.destroy(gl);
    display.destroy(gl);
    gl.glDeleteTextures(outputTexture, segTexture, segColorTexture, majorantPlaceholder);
    gl.glDeleteFramebuffers(rayFbo);
    gl.glDeleteBuffers(vertexBuffer);
    gl.glBindVertexArray(0);
    gl.glDeleteVertexArrays(vertexArray);
  }

  // ── Uniforms: the names and the order of View3d.initShaders ──

  private void allocateUniforms() {
    Matrix4d view = viewMatrix();
    Matrix4d projection = projectionMatrix();
    Vector4d lightOrigin = new Vector4d(0, 0, 0, 1).mul(new Matrix4d(view).invert());

    program.allocateUniform(
        gl, "viewMatrix", (g, loc) -> g.glUniformMatrix4fv(loc, false, floats(view)));
    program.allocateUniform(
        gl, "projectionMatrix", (g, loc) -> g.glUniformMatrix4fv(loc, false, floats(projection)));
    program.allocateUniform(gl, "depthSampleNumber", (g, loc) -> g.glUniform1i(loc, sampleCount));
    program.allocateUniform(gl, "lutShape", (g, loc) -> g.glUniform1ui(loc, 0));
    program.allocateUniform(gl, "backgroundColor", (g, loc) -> g.glUniform3fv(loc, BACKGROUND));
    for (int i = 0; i < 4; i++) {
      int index = i;
      program.allocateUniform(
          gl,
          "lights[%d].position".formatted(index), // NON-NLS
          (g, loc) ->
              g.glUniform4fv(
                  loc,
                  new float[] {
                    (float) lightOrigin.x,
                    (float) lightOrigin.y,
                    (float) lightOrigin.z,
                    (float) lightOrigin.w
                  }));
      program.allocateUniform(
          gl,
          "lights[%d].specularPower".formatted(index), // NON-NLS
          (g, loc) -> g.glUniform1f(loc, SPECULAR_POWER));
      program.allocateUniform(
          gl,
          "lights[%d].enabled".formatted(index), // NON-NLS
          (g, loc) -> g.glUniform1i(loc, index < 1 ? 1 : 0));
    }
    program.allocateUniform(gl, "lightColor", (g, loc) -> g.glUniform3fv(loc, LIGHT_COLOR));
    program.allocateUniform(gl, "shading", (g, loc) -> g.glUniform1i(loc, frame.shading() ? 1 : 0));
    program.allocateUniform(
        gl, "cinematic", (g, loc) -> g.glUniform1i(loc, frame.cinematic() ? 1 : 0));
    program.allocateUniform(gl, "keyLightDir", (g, loc) -> g.glUniform3fv(loc, KEY_LIGHT_DIR));
    program.allocateUniform(gl, "shadowStrength", (g, loc) -> g.glUniform1f(loc, SHADOW_STRENGTH));
    program.allocateUniform(gl, "aoStrength", (g, loc) -> g.glUniform1f(loc, AO_STRENGTH));
    program.allocateUniform(gl, "exposure", (g, loc) -> g.glUniform1f(loc, EXPOSURE));
    program.allocateUniform(gl, "shadowSteps", (g, loc) -> g.glUniform1i(loc, SHADOW_STEPS));
    program.allocateUniform(gl, "aoSteps", (g, loc) -> g.glUniform1i(loc, AO_STEPS));
    program.allocateUniform(gl, "cinematicScale", (g, loc) -> g.glUniform1f(loc, 1f));
    program.allocateUniform(
        gl, "envMap", (g, loc) -> g.glUniform1i(loc, EnvironmentTexture.TEXTURE_UNIT));
    program.allocateUniform(
        gl, "envEnabled", (g, loc) -> g.glUniform1i(loc, frame.cinematic() ? 1 : 0));
    program.allocateUniform(gl, "envStrength", (g, loc) -> g.glUniform1f(loc, ENV_STRENGTH));
    program.allocateUniform(
        gl, "envMaxLod", (g, loc) -> g.glUniform1f(loc, EnvironmentTexture.maxLod()));
    program.allocateUniform(
        gl, "envSh", (g, loc) -> g.glUniform3fv(loc, environment.getIrradianceSh()));
    program.allocateUniform(
        gl, "historyMap", (g, loc) -> g.glUniform1i(loc, AccumulationBuffers.HISTORY_UNIT));
    program.allocateUniform(
        gl, "featureMap", (g, loc) -> g.glUniform1i(loc, AccumulationBuffers.FEATURE_UNIT));
    program.allocateUniform(
        gl, "momentMap", (g, loc) -> g.glUniform1i(loc, AccumulationBuffers.MOMENT_UNIT));
    program.allocateUniform(gl, "frameIndex", (g, loc) -> g.glUniform1i(loc, frameIndex));
    program.allocateUniform(gl, "ptSamples", (g, loc) -> g.glUniform1i(loc, 1));
    program.allocateUniform(gl, "majorantMap", (g, loc) -> g.glUniform1i(loc, MAJORANT_UNIT));
    program.allocateUniform(gl, "ptMaxBounces", (g, loc) -> g.glUniform1i(loc, PT_MAX_BOUNCES));
    program.allocateUniform(gl, "ptMaxAlpha", (g, loc) -> g.glUniform1f(loc, preset.getMaxAlpha()));
    program.allocateUniform(
        gl, "texelSize", (g, loc) -> g.glUniform3fv(loc, new float[] {1, 1, 1}));
    program.allocateUniform(
        gl, "renderingType", (g, loc) -> g.glUniform1ui(loc, frame.type().getId()));
    program.allocateUniform(
        gl, "mipType", (g, loc) -> g.glUniform1ui(loc, frame.type().getMipTypeId()));
    program.allocateUniform(gl, "volTexture", (g, loc) -> g.glUniform1i(loc, 0));
    program.allocateUniform(gl, "colorMap", (g, loc) -> g.glUniform1i(loc, 1));
    program.allocateUniform(gl, "lightingMap", (g, loc) -> g.glUniform1i(loc, 2));
    program.allocateUniform(gl, "textureDataType", (g, loc) -> g.glUniform1ui(loc, 2));
    program.allocateUniform(gl, "opacityFactor", (g, loc) -> g.glUniform1f(loc, OPACITY));
    program.allocateUniform(
        gl, "inputLevelMin", (g, loc) -> g.glUniform1f(loc, SyntheticVolume.VALUE_MIN));
    program.allocateUniform(
        gl, "inputLevelMax", (g, loc) -> g.glUniform1f(loc, SyntheticVolume.VALUE_MAX));
    program.allocateUniform(gl, "outputLevelMin", (g, loc) -> g.glUniform1f(loc, 0));
    program.allocateUniform(
        gl, "outputLevelMax", (g, loc) -> g.glUniform1f(loc, preset.getWidth()));
    program.allocateUniform(
        gl, "visibleMin", (g, loc) -> g.glUniform1f(loc, preset.getVisibleRange()[0]));
    program.allocateUniform(
        gl, "visibleMax", (g, loc) -> g.glUniform1f(loc, preset.getVisibleRange()[1]));
    program.allocateUniform(
        gl,
        "gradientOpacityEnabled",
        (g, loc) -> g.glUniform1i(loc, preset.getGradientOpacityTable() != null ? 1 : 0));
    program.allocateUniform(
        gl,
        "gradientOpacity",
        (g, loc) -> {
          float[] table = preset.getGradientOpacityTable();
          if (table != null) {
            g.glUniform1fv(loc, table);
          }
        });
    program.allocateUniform(gl, "windowWidth", (g, loc) -> g.glUniform1f(loc, WINDOW_WIDTH));
    program.allocateUniform(gl, "windowCenter", (g, loc) -> g.glUniform1f(loc, WINDOW_CENTER));
    program.allocateUniform(gl, "ditherRay", (g, loc) -> g.glUniform1i(loc, 1));
    program.allocateUniform(gl, "overlayScale", (g, loc) -> g.glUniform1f(loc, 1f));
    program.allocateUniform(
        gl, "crosshairPos", (g, loc) -> g.glUniform3f(loc, 0.5f, 0.5f, 0.5f)); // NON-NLS
    program.allocateUniform(
        gl,
        "crosshairRot", // NON-NLS
        (g, loc) -> g.glUniformMatrix3fv(loc, false, new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1}));
    program.allocateUniform(
        gl,
        "crosshairVisible", // NON-NLS
        (g, loc) -> g.glUniform1i(loc, frame.crosshair() ? 1 : 0));
    program.allocateUniform(gl, "crosshairCutMode", (g, loc) -> g.glUniform1i(loc, 1)); // NON-NLS
    program.allocateUniform(
        gl,
        "segOverlayEnabled", // NON-NLS
        (g, loc) -> g.glUniform1i(loc, frame.segOverlay() ? 1 : 0));
    program.allocateUniform(gl, "segOnly", (g, loc) -> g.glUniform1i(loc, 0)); // NON-NLS
    program.allocateUniform(gl, "segMaskMode", (g, loc) -> g.glUniform1i(loc, 0)); // NON-NLS
    program.allocateUniform(gl, "segSegmentCount", (g, loc) -> g.glUniform1i(loc, 2)); // NON-NLS
    program.allocateUniform(
        gl, "segTexture", (g, loc) -> g.glUniform1i(loc, SegVolumeTexture.SEG_TEXTURE_UNIT));
    program.allocateUniform(
        gl, "segColorMap", (g, loc) -> g.glUniform1i(loc, SegVolumeTexture.SEG_COLOR_UNIT));

    quadProgram.init(gl);
    quadProgram.allocateUniform(
        gl, "compute", (g, loc) -> g.glUniform1i(loc, rayCaster == RayCaster.COMPUTE ? 0 : 3));
    quadProgram.allocateUniform(
        gl,
        "texScale",
        (g, loc) -> g.glUniform2f(loc, passWidth() / (float) width, passHeight() / (float) height));

    denoiseProgram.init(gl);
    denoiseProgram.shareUniforms(
        gl,
        program,
        "viewMatrix",
        "projectionMatrix",
        "texelSize",
        "overlayScale",
        "crosshairPos",
        "crosshairRot",
        "crosshairVisible",
        "crosshairCutMode",
        "exposure",
        "frameIndex",
        "historyMap",
        "featureMap",
        "momentMap");
    // As the view does on its first path-traced frame.
    tracerProgram.shareAllUniforms(gl, program);
  }

  /**
   * {@code Camera.getViewMatrix()} for {@code CameraView.INITIAL}, inverted as the view sends it.
   */
  private static Matrix4d viewMatrix() {
    double internalZoom = -FOV * Z_NEAR;
    return new Matrix4d()
        .setTranslation(new Vector3d(0, 0, internalZoom))
        .rotate(ROTATION)
        .translate(new Vector3d())
        .invert();
  }

  private Matrix4d projectionMatrix() {
    return new Matrix4d().setPerspective(FOV, width / (double) height, Z_NEAR, Z_FAR).invert();
  }

  private static float[] floats(Matrix4d matrix) {
    return matrix.get(new float[16]);
  }

  /** The programs a rendering path uses, for the test that checks they all built. */
  List<Program> programs() {
    return List.of(program, tracerProgram, denoiseProgram, quadProgram);
  }

  /** The frames the baseline is made of. */
  static List<Frame> frames() {
    List<Frame> frames = new ArrayList<>();
    frames.add(Frame.of("composite", RenderingType.COMPOSITE).shaded()); // NON-NLS
    frames.add(Frame.of("mip-max", RenderingType.MIP_MAX)); // NON-NLS
    frames.add(Frame.of("mip-min", RenderingType.MIP_MIN)); // NON-NLS
    frames.add(Frame.of("iso-surface", RenderingType.ISO2).shaded()); // NON-NLS
    frames.add(Frame.of("cinematic", RenderingType.COMPOSITE).cinematicLit()); // NON-NLS
    frames.add(Frame.of("path-tracing", RenderingType.PATH_TRACING).pathTraced(32)); // NON-NLS
    frames.add(Frame.of("seg-overlay", RenderingType.COMPOSITE).shaded().withSeg()); // NON-NLS
    frames.add(
        Frame.of("crosshair-cut", RenderingType.COMPOSITE).shaded().withCrosshair()); // NON-NLS
    return frames;
  }
}
