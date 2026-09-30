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

import java.lang.foreign.MemorySegment;

/**
 * The OpenGL entry points and constants the volume renderer uses, as plain Java methods. It is the
 * only OpenGL surface the {@code vr} package sees, so the binding underneath can be replaced
 * without touching the renderer.
 *
 * <p>Conventions that differ from the raw C API:
 *
 * <ul>
 *   <li>Pixel and buffer payloads are native {@link MemorySegment}s ({@link MemorySegment#NULL} for
 *       a null pointer); a heap segment is not accepted.
 *   <li>Generators return the names they created and deleters take them as varargs.
 *   <li>Queries that write into a pointer return their value instead.
 *   <li>The {@code count} of the {@code glUniform*v} calls is derived from the array length.
 * </ul>
 *
 * <p>Every method must be called with the context current, which means on the thread that owns it.
 */
public interface Gl {

  int GL_FALSE = 0;
  int GL_TRUE = 1;

  // Errors
  int GL_NO_ERROR = 0;
  int GL_INVALID_ENUM = 0x0500;
  int GL_INVALID_VALUE = 0x0501;
  int GL_INVALID_OPERATION = 0x0502;
  int GL_STACK_OVERFLOW = 0x0503;
  int GL_STACK_UNDERFLOW = 0x0504;
  int GL_OUT_OF_MEMORY = 0x0505;
  int GL_INVALID_FRAMEBUFFER_OPERATION = 0x0506;

  // Strings and limits
  int GL_VENDOR = 0x1F00;
  int GL_RENDERER = 0x1F01;
  int GL_VERSION = 0x1F02;
  int GL_MAX_3D_TEXTURE_SIZE = 0x8073;

  // Buffer clearing and blending
  int GL_DEPTH_BUFFER_BIT = 0x00000100;
  int GL_COLOR_BUFFER_BIT = 0x00004000;
  int GL_BLEND = 0x0BE2;
  int GL_SRC_ALPHA = 0x0302;
  int GL_ONE_MINUS_SRC_ALPHA = 0x0303;

  // Primitives and data types
  int GL_TRIANGLES = 0x0004;
  int GL_UNSIGNED_BYTE = 0x1401;
  int GL_UNSIGNED_SHORT = 0x1403;
  int GL_FLOAT = 0x1406;
  int GL_UNSIGNED_INT_8_8_8_8_REV = 0x8367;

  // Pixel store
  int GL_UNPACK_ALIGNMENT = 0x0CF5;
  int GL_PACK_ALIGNMENT = 0x0D05;

  // Buffer objects
  int GL_ARRAY_BUFFER = 0x8892;
  int GL_PIXEL_PACK_BUFFER = 0x88EB;
  int GL_STATIC_DRAW = 0x88E4;
  int GL_STREAM_READ = 0x88E1;
  int GL_MAP_READ_BIT = 0x0001;

  // Textures
  int GL_TEXTURE_2D = 0x0DE1;
  int GL_TEXTURE_3D = 0x806F;
  int GL_TEXTURE0 = 0x84C0;
  int GL_TEXTURE1 = 0x84C1;
  int GL_TEXTURE2 = 0x84C2;
  int GL_NEAREST = 0x2600;
  int GL_LINEAR = 0x2601;
  int GL_LINEAR_MIPMAP_LINEAR = 0x2703;
  int GL_TEXTURE_MAG_FILTER = 0x2800;
  int GL_TEXTURE_MIN_FILTER = 0x2801;
  int GL_TEXTURE_WRAP_S = 0x2802;
  int GL_TEXTURE_WRAP_T = 0x2803;
  int GL_TEXTURE_WRAP_R = 0x8072;
  int GL_TEXTURE_BASE_LEVEL = 0x813C;
  int GL_TEXTURE_MAX_LEVEL = 0x813D;
  int GL_REPEAT = 0x2901;
  int GL_CLAMP_TO_EDGE = 0x812F;
  int GL_WRITE_ONLY = 0x88B9;

  // Pixel formats
  int GL_RED = 0x1903;
  int GL_RG = 0x8227;
  int GL_RGB = 0x1907;
  int GL_RGBA = 0x1908;
  int GL_BGRA = 0x80E1;
  int GL_RED_INTEGER = 0x8D94;
  int GL_R8 = 0x8229;
  int GL_R16 = 0x822A;
  int GL_R32F = 0x822E;
  int GL_RG32F = 0x8230;
  int GL_R8UI = 0x8232;
  int GL_R16UI = 0x8234;
  int GL_RGB8 = 0x8051;
  int GL_RGBA8 = 0x8058;
  int GL_RGBA16F = 0x881A;
  int GL_RGB32F = 0x8815;
  int GL_RGBA32F = 0x8814;

  // Framebuffers
  int GL_FRAMEBUFFER = 0x8D40;
  int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
  int GL_COLOR_ATTACHMENT0 = 0x8CE0;
  int GL_COLOR_ATTACHMENT1 = 0x8CE1;
  int GL_COLOR_ATTACHMENT2 = 0x8CE2;
  int GL_COLOR_ATTACHMENT3 = 0x8CE3;

  // Shaders
  int GL_FRAGMENT_SHADER = 0x8B30;
  int GL_VERTEX_SHADER = 0x8B31;
  int GL_GEOMETRY_SHADER = 0x8DD9;
  int GL_COMPUTE_SHADER = 0x91B9;
  int GL_COMPILE_STATUS = 0x8B81;
  int GL_LINK_STATUS = 0x8B82;

  // Compute
  int GL_SHADER_IMAGE_ACCESS_BARRIER_BIT = 0x00000020;

  // KHR_parallel_shader_compile (same value as the ARB enumerant)
  int GL_COMPLETION_STATUS_KHR = 0x91B1;

  /** {@code false} on a context below 4.3, where the compute path is unavailable. */
  boolean isComputeCapable();

  /**
   * Lets the driver compile and link on threads of its own when it offers {@code
   * KHR_parallel_shader_compile} or its ARB twin. Called once, when the context is created.
   *
   * @return whether the driver does
   */
  boolean enableParallelShaderCompile();

  /**
   * Whether {@link #GL_COMPLETION_STATUS_KHR} can be polled, so a link started now can be checked
   * later without blocking.
   */
  boolean isParallelShaderCompile();

  // ── State and queries ──

  String glGetString(int name);

  int glGetInteger(int pname);

  int glGetError();

  void glFinish();

  void glClear(int mask);

  void glClearColor(float red, float green, float blue, float alpha);

  void glViewport(int x, int y, int width, int height);

  void glEnable(int cap);

  void glDisable(int cap);

  void glBlendFunc(int sfactor, int dfactor);

  void glPixelStorei(int pname, int param);

  // ── Textures ──

  int[] glGenTextures(int count);

  void glDeleteTextures(int... textures);

  void glActiveTexture(int texture);

  void glBindTexture(int target, int texture);

  void glTexParameteri(int target, int pname, int param);

  void glTexImage2D(
      int target,
      int level,
      int internalFormat,
      int width,
      int height,
      int border,
      int format,
      int type,
      MemorySegment pixels);

  void glTexImage3D(
      int target,
      int level,
      int internalFormat,
      int width,
      int height,
      int depth,
      int border,
      int format,
      int type,
      MemorySegment pixels);

  void glTexSubImage3D(
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
      MemorySegment pixels);

  void glBindImageTexture(
      int unit, int texture, int level, boolean layered, int layer, int access, int format);

  void glReadPixels(
      int x, int y, int width, int height, int format, int type, MemorySegment pixels);

  /** Desktop OpenGL only; {@code pixels} must hold the whole level. */
  void glGetTexImage(int target, int level, int format, int type, MemorySegment pixels);

  // ── Framebuffers ──

  int[] glGenFramebuffers(int count);

  void glDeleteFramebuffers(int... framebuffers);

  void glBindFramebuffer(int target, int framebuffer);

  void glFramebufferTexture2D(
      int target, int attachment, int textureTarget, int texture, int level);

  void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer);

  int glCheckFramebufferStatus(int target);

  void glDrawBuffers(int... buffers);

  // ── Buffer objects ──

  int[] glGenBuffers(int count);

  void glDeleteBuffers(int... buffers);

  void glBindBuffer(int target, int buffer);

  void glBufferData(int target, long size, MemorySegment data, int usage);

  /** The mapped range, or {@link MemorySegment#NULL} when the driver refused to map it. */
  MemorySegment glMapBufferRange(int target, long offset, long length, int access);

  /** {@code false} when the contents were lost while mapped and the read must be repeated. */
  boolean glUnmapBuffer(int target);

  // ── Vertex arrays ──

  /**
   * A core profile has no default vertex array object; one is bound for the life of the context.
   */
  int glGenVertexArray();

  void glBindVertexArray(int array);

  void glDeleteVertexArrays(int... arrays);

  void glEnableVertexAttribArray(int index);

  void glDisableVertexAttribArray(int index);

  void glVertexAttribPointer(
      int index, int size, int type, boolean normalized, int stride, long offset);

  void glDrawArrays(int mode, int first, int count);

  // ── Shaders and programs ──

  int glCreateShader(int type);

  void glShaderSource(int shader, String source);

  void glCompileShader(int shader);

  int glGetShaderi(int shader, int pname);

  String glGetShaderInfoLog(int shader);

  void glDeleteShader(int shader);

  int glCreateProgram();

  void glAttachShader(int program, int shader);

  void glDetachShader(int program, int shader);

  void glLinkProgram(int program);

  int glGetProgrami(int program, int pname);

  String glGetProgramInfoLog(int program);

  void glUseProgram(int program);

  void glDeleteProgram(int program);

  int glGetUniformLocation(int program, String name);

  // ── Uniforms ──

  void glUniform1i(int location, int v0);

  void glUniform1ui(int location, int v0);

  void glUniform1f(int location, float v0);

  void glUniform2f(int location, float v0, float v1);

  void glUniform3f(int location, float v0, float v1, float v2);

  void glUniform3i(int location, int v0, int v1, int v2);

  void glUniform1fv(int location, float[] value);

  void glUniform3fv(int location, float[] value);

  void glUniform4fv(int location, float[] value);

  void glUniformMatrix3fv(int location, boolean transpose, float[] value);

  void glUniformMatrix4fv(int location, boolean transpose, float[] value);

  // ── Compute ──

  void glDispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ);

  void glMemoryBarrier(int barriers);

  /** Name of an error code, for the messages the loaders and the texture allocations log. */
  static String errorString(int error) {
    return switch (error) {
      case GL_NO_ERROR -> "GL_NO_ERROR"; // NON-NLS
      case GL_INVALID_ENUM -> "GL_INVALID_ENUM"; // NON-NLS
      case GL_INVALID_VALUE -> "GL_INVALID_VALUE"; // NON-NLS
      case GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"; // NON-NLS
      case GL_STACK_OVERFLOW -> "GL_STACK_OVERFLOW"; // NON-NLS
      case GL_STACK_UNDERFLOW -> "GL_STACK_UNDERFLOW"; // NON-NLS
      case GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"; // NON-NLS
      case GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"; // NON-NLS
      default -> "0x" + Integer.toHexString(error);
    };
  }
}
