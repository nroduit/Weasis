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

import com.jogamp.common.util.VersionNumber;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** {@link Gl} on top of JOGL, the binding used until the FFM one replaces it. */
public class JoglGl implements Gl {

  private static final int LOG_BUFFER_SIZE = 2048;

  private final GL2ES2 gl;
  private final boolean computeCapable;

  public JoglGl(GL gl) {
    this.gl = Objects.requireNonNull(gl).getGL2ES2();
    VersionNumber version = gl.getContext().getGLVersionNumber();
    // isGL4() is true of a 4.1 context too, which has no compute shaders.
    this.computeCapable = GlContext.isComputeCapable(version.getMajor(), version.getMinor());
  }

  /** The wrapped JOGL context, for the AWT drawables that still create their own. */
  public GL2ES2 unwrap() {
    return gl;
  }

  private GL2ES3 es3() {
    return gl.getGL2ES3();
  }

  private GL4 gl4() {
    return gl.getGL4();
  }

  // A native segment holds the pointer GL reads from; NULL means "allocate without initial data".
  private static ByteBuffer buffer(MemorySegment segment) {
    if (segment == null || MemorySegment.NULL.equals(segment) || segment.byteSize() == 0) {
      return null;
    }
    return segment.asByteBuffer();
  }

  @Override
  public boolean isComputeCapable() {
    return computeCapable;
  }

  // JOGL binds no glMaxShaderCompilerThreadsKHR; the fallback links synchronously.
  @Override
  public boolean enableParallelShaderCompile() {
    return false;
  }

  @Override
  public boolean isParallelShaderCompile() {
    return false;
  }

  // ── State and queries ──

  @Override
  public String glGetString(int name) {
    return gl.glGetString(name);
  }

  @Override
  public int glGetInteger(int pname) {
    int[] value = new int[1];
    gl.glGetIntegerv(pname, value, 0);
    return value[0];
  }

  @Override
  public int glGetError() {
    return gl.glGetError();
  }

  @Override
  public void glFinish() {
    gl.glFinish();
  }

  @Override
  public void glClear(int mask) {
    gl.glClear(mask);
  }

  @Override
  public void glClearColor(float red, float green, float blue, float alpha) {
    gl.glClearColor(red, green, blue, alpha);
  }

  @Override
  public void glViewport(int x, int y, int width, int height) {
    gl.glViewport(x, y, width, height);
  }

  @Override
  public void glEnable(int cap) {
    gl.glEnable(cap);
  }

  @Override
  public void glDisable(int cap) {
    gl.glDisable(cap);
  }

  @Override
  public void glBlendFunc(int sfactor, int dfactor) {
    gl.glBlendFunc(sfactor, dfactor);
  }

  @Override
  public void glPixelStorei(int pname, int param) {
    gl.glPixelStorei(pname, param);
  }

  // ── Textures ──

  @Override
  public int[] glGenTextures(int count) {
    int[] names = new int[count];
    gl.glGenTextures(count, names, 0);
    return names;
  }

  @Override
  public void glDeleteTextures(int... textures) {
    gl.glDeleteTextures(textures.length, textures, 0);
  }

  @Override
  public void glActiveTexture(int texture) {
    gl.glActiveTexture(texture);
  }

  @Override
  public void glBindTexture(int target, int texture) {
    gl.glBindTexture(target, texture);
  }

  @Override
  public void glTexParameteri(int target, int pname, int param) {
    gl.glTexParameteri(target, pname, param);
  }

  @Override
  public void glTexImage2D(
      int target,
      int level,
      int internalFormat,
      int width,
      int height,
      int border,
      int format,
      int type,
      MemorySegment pixels) {
    gl.glTexImage2D(
        target, level, internalFormat, width, height, border, format, type, buffer(pixels));
  }

  @Override
  public void glTexImage3D(
      int target,
      int level,
      int internalFormat,
      int width,
      int height,
      int depth,
      int border,
      int format,
      int type,
      MemorySegment pixels) {
    gl.glTexImage3D(
        target, level, internalFormat, width, height, depth, border, format, type, buffer(pixels));
  }

  @Override
  public void glTexSubImage3D(
      int target,
      int level,
      int xOffset,
      int yOffset,
      int zOffset,
      int width,
      int height,
      int depth,
      int format,
      int type,
      MemorySegment pixels) {
    gl.glTexSubImage3D(
        target,
        level,
        xOffset,
        yOffset,
        zOffset,
        width,
        height,
        depth,
        format,
        type,
        buffer(pixels));
  }

  @Override
  public void glBindImageTexture(
      int unit, int texture, int level, boolean layered, int layer, int access, int format) {
    gl4().glBindImageTexture(unit, texture, level, layered, layer, access, format);
  }

  @Override
  public void glReadPixels(
      int x, int y, int width, int height, int format, int type, MemorySegment pixels) {
    ByteBuffer destination = buffer(pixels);
    if (destination == null) {
      // No destination means a pixel pack buffer is bound and the pointer is an offset into it,
      // which JOGL expects through its own overload.
      gl.glReadPixels(x, y, width, height, format, type, 0L);
    } else {
      gl.glReadPixels(x, y, width, height, format, type, destination);
    }
  }

  @Override
  public void glGetTexImage(int target, int level, int format, int type, MemorySegment pixels) {
    gl.getGL2GL3().glGetTexImage(target, level, format, type, buffer(pixels));
  }

  // ── Framebuffers ──

  @Override
  public int[] glGenFramebuffers(int count) {
    int[] names = new int[count];
    gl.glGenFramebuffers(count, names, 0);
    return names;
  }

  @Override
  public void glDeleteFramebuffers(int... framebuffers) {
    gl.glDeleteFramebuffers(framebuffers.length, framebuffers, 0);
  }

  @Override
  public void glBindFramebuffer(int target, int framebuffer) {
    gl.glBindFramebuffer(target, framebuffer);
  }

  @Override
  public void glFramebufferTexture2D(
      int target, int attachment, int textureTarget, int texture, int level) {
    gl.glFramebufferTexture2D(target, attachment, textureTarget, texture, level);
  }

  @Override
  public void glFramebufferTextureLayer(
      int target, int attachment, int texture, int level, int layer) {
    gl.getGL2ES3().glFramebufferTextureLayer(target, attachment, texture, level, layer);
  }

  @Override
  public int glCheckFramebufferStatus(int target) {
    return gl.glCheckFramebufferStatus(target);
  }

  @Override
  public void glDrawBuffers(int... buffers) {
    es3().glDrawBuffers(buffers.length, buffers, 0);
  }

  // ── Buffer objects ──

  @Override
  public int[] glGenBuffers(int count) {
    int[] names = new int[count];
    gl.glGenBuffers(count, names, 0);
    return names;
  }

  @Override
  public void glDeleteBuffers(int... buffers) {
    gl.glDeleteBuffers(buffers.length, buffers, 0);
  }

  @Override
  public void glBindBuffer(int target, int buffer) {
    gl.glBindBuffer(target, buffer);
  }

  @Override
  public void glBufferData(int target, long size, MemorySegment data, int usage) {
    gl.glBufferData(target, size, buffer(data), usage);
  }

  @Override
  public MemorySegment glMapBufferRange(int target, long offset, long length, int access) {
    ByteBuffer mapped = gl.getGL2ES3().glMapBufferRange(target, offset, length, access);
    return mapped == null ? MemorySegment.NULL : MemorySegment.ofBuffer(mapped);
  }

  @Override
  public boolean glUnmapBuffer(int target) {
    return gl.getGL2ES3().glUnmapBuffer(target);
  }

  // ── Vertex arrays ──

  @Override
  public int glGenVertexArray() {
    int[] names = new int[1];
    gl.getGL2ES3().glGenVertexArrays(1, names, 0);
    return names[0];
  }

  @Override
  public void glBindVertexArray(int array) {
    gl.getGL2ES3().glBindVertexArray(array);
  }

  @Override
  public void glDeleteVertexArrays(int... arrays) {
    gl.getGL2ES3().glDeleteVertexArrays(arrays.length, arrays, 0);
  }

  @Override
  public void glEnableVertexAttribArray(int index) {
    gl.glEnableVertexAttribArray(index);
  }

  @Override
  public void glDisableVertexAttribArray(int index) {
    gl.glDisableVertexAttribArray(index);
  }

  @Override
  public void glVertexAttribPointer(
      int index, int size, int type, boolean normalized, int stride, long offset) {
    gl.glVertexAttribPointer(index, size, type, normalized, stride, offset);
  }

  @Override
  public void glDrawArrays(int mode, int first, int count) {
    gl.glDrawArrays(mode, first, count);
  }

  // ── Shaders and programs ──

  @Override
  public int glCreateShader(int type) {
    return gl.glCreateShader(type);
  }

  @Override
  public void glShaderSource(int shader, String source) {
    gl.glShaderSource(shader, 1, new String[] {source}, (IntBuffer) null);
  }

  @Override
  public void glCompileShader(int shader) {
    gl.glCompileShader(shader);
  }

  @Override
  public int glGetShaderi(int shader, int pname) {
    IntBuffer value = IntBuffer.allocate(1);
    gl.glGetShaderiv(shader, pname, value);
    return value.get(0);
  }

  @Override
  public String glGetShaderInfoLog(int shader) {
    IntBuffer length = IntBuffer.allocate(1);
    ByteBuffer log = ByteBuffer.allocate(LOG_BUFFER_SIZE);
    gl.glGetShaderInfoLog(shader, LOG_BUFFER_SIZE, length, log);
    return readLog(log, length.get(0));
  }

  @Override
  public void glDeleteShader(int shader) {
    gl.glDeleteShader(shader);
  }

  @Override
  public int glCreateProgram() {
    return gl.glCreateProgram();
  }

  @Override
  public void glAttachShader(int program, int shader) {
    gl.glAttachShader(program, shader);
  }

  @Override
  public void glDetachShader(int program, int shader) {
    gl.glDetachShader(program, shader);
  }

  @Override
  public void glLinkProgram(int program) {
    gl.glLinkProgram(program);
  }

  @Override
  public int glGetProgrami(int program, int pname) {
    IntBuffer value = IntBuffer.allocate(1);
    gl.glGetProgramiv(program, pname, value);
    return value.get(0);
  }

  @Override
  public String glGetProgramInfoLog(int program) {
    IntBuffer length = IntBuffer.allocate(1);
    ByteBuffer log = ByteBuffer.allocate(LOG_BUFFER_SIZE);
    gl.glGetProgramInfoLog(program, LOG_BUFFER_SIZE, length, log);
    return readLog(log, length.get(0));
  }

  // The driver log is only valid up to the length it reports.
  private static String readLog(ByteBuffer log, int length) {
    return new String(log.array(), 0, Math.clamp(length, 0, log.capacity()), StandardCharsets.UTF_8)
        .trim();
  }

  @Override
  public void glUseProgram(int program) {
    gl.glUseProgram(program);
  }

  @Override
  public void glDeleteProgram(int program) {
    gl.glDeleteProgram(program);
  }

  @Override
  public int glGetUniformLocation(int program, String name) {
    return gl.glGetUniformLocation(program, name);
  }

  // ── Uniforms ──

  @Override
  public void glUniform1i(int location, int v0) {
    gl.glUniform1i(location, v0);
  }

  @Override
  public void glUniform1ui(int location, int v0) {
    es3().glUniform1ui(location, v0);
  }

  @Override
  public void glUniform1f(int location, float v0) {
    gl.glUniform1f(location, v0);
  }

  @Override
  public void glUniform2f(int location, float v0, float v1) {
    gl.glUniform2f(location, v0, v1);
  }

  @Override
  public void glUniform3f(int location, float v0, float v1, float v2) {
    gl.glUniform3f(location, v0, v1, v2);
  }

  @Override
  public void glUniform3i(int location, int v0, int v1, int v2) {
    gl.glUniform3i(location, v0, v1, v2);
  }

  @Override
  public void glUniform1fv(int location, float[] value) {
    gl.glUniform1fv(location, value.length, value, 0);
  }

  @Override
  public void glUniform3fv(int location, float[] value) {
    gl.glUniform3fv(location, value.length / 3, value, 0);
  }

  @Override
  public void glUniform4fv(int location, float[] value) {
    gl.glUniform4fv(location, value.length / 4, value, 0);
  }

  @Override
  public void glUniformMatrix3fv(int location, boolean transpose, float[] value) {
    gl.glUniformMatrix3fv(location, value.length / 9, transpose, value, 0);
  }

  @Override
  public void glUniformMatrix4fv(int location, boolean transpose, float[] value) {
    gl.glUniformMatrix4fv(location, value.length / 16, transpose, value, 0);
  }

  // ── Compute ──

  @Override
  public void glDispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
    gl4().glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
  }

  @Override
  public void glMemoryBarrier(int barriers) {
    gl4().glMemoryBarrier(barriers);
  }
}
