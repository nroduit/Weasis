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

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * {@link Gl} through the Foreign Function &amp; Memory API, calling the driver directly.
 *
 * <p>The entry points are resolved once, into {@code static final} handles, by the nested {@link
 * Handles} class. That class is only initialised on the first GL call, hence after a context is
 * current: on Windows nothing above OpenGL 1.1 can be resolved before that.
 *
 * <p>An instance is confined to the thread that owns the context — {@link GlThread} — and reuses
 * one scratch block for the small arrays the API passes by pointer, so a frame allocates nothing.
 */
public final class FfmGl implements Gl {

  private static final Linker LINKER = Linker.nativeLinker();

  /**
   * Set by the context before any call, since it knows how its driver resolves symbols — on Windows
   * that means {@code wglGetProcAddress}, which only answers once a context is current. The handles
   * below are resolved once against it, which is sound because the application owns exactly one
   * context.
   */
  private static volatile SymbolLookup lookup;

  private static final int GL_EXTENSIONS = 0x1F03;
  private static final int GL_NUM_EXTENSIONS = 0x821D;

  /** {@code glMaxShaderCompilerThreadsKHR} argument letting the driver pick the thread count. */
  private static final int DRIVER_CHOSEN_THREADS = 0xFFFFFFFF;

  private static final int SCRATCH_BYTES = 4096;
  private static final int LOG_BUFFER_SIZE = 2048;

  private final Arena arena = Arena.ofConfined();
  private final MemorySegment scratch = arena.allocate(SCRATCH_BYTES, 8);
  private final MemorySegment log = arena.allocate(LOG_BUFFER_SIZE);
  private final boolean computeCapable;
  private boolean parallelShaderCompile;

  FfmGl(SymbolLookup symbols, boolean computeCapable) {
    lookup = Objects.requireNonNull(symbols);
    this.computeCapable = computeCapable;
  }

  /** Releases the scratch block; the instance must not be used afterwards. */
  public void close() {
    arena.close();
  }

  @Override
  public boolean isComputeCapable() {
    return computeCapable;
  }

  @Override
  public boolean enableParallelShaderCompile() {
    String entryPoint;
    if (hasExtension("GL_KHR_parallel_shader_compile")) { // NON-NLS
      entryPoint = "glMaxShaderCompilerThreadsKHR"; // NON-NLS
    } else if (hasExtension("GL_ARB_parallel_shader_compile")) { // NON-NLS
      entryPoint = "glMaxShaderCompilerThreadsARB"; // NON-NLS
    } else {
      return false;
    }
    // Resolved here rather than among the handles: the extension is optional, and a driver that
    // lists it without handing out its entry point just links synchronously.
    try {
      MethodHandle maxThreads = voidCall(entryPoint, JAVA_INT);
      maxThreads.invokeExact(DRIVER_CHOSEN_THREADS);
    } catch (UnsatisfiedLinkError e) {
      return false;
    } catch (Throwable t) {
      throw failed(entryPoint, t);
    }
    parallelShaderCompile = true;
    return true;
  }

  @Override
  public boolean isParallelShaderCompile() {
    return parallelShaderCompile;
  }

  // A core profile lists its extensions one by one; glGetString(GL_EXTENSIONS) is an error there.
  private boolean hasExtension(String name) {
    int count = glGetInteger(GL_NUM_EXTENSIONS);
    for (int i = 0; i < count; i++) {
      try {
        MemorySegment value = (MemorySegment) Handles.GET_STRINGI.invokeExact(GL_EXTENSIONS, i);
        if (!MemorySegment.NULL.equals(value)
            && name.equals(
                value.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8))) {
          return true;
        }
      } catch (Throwable t) {
        throw failed("glGetStringi", t);
      }
    }
    return false;
  }

  // ── Handles ──

  private static MethodHandle voidCall(String name, MemoryLayout... arguments) {
    return call(name, FunctionDescriptor.ofVoid(arguments));
  }

  private static MethodHandle call(String name, FunctionDescriptor descriptor) {
    MemorySegment address =
        lookup
            .find(name)
            .orElseThrow(() -> new UnsatisfiedLinkError("OpenGL entry point not found: " + name));
    return LINKER.downcallHandle(address, descriptor);
  }

  // Descriptors are written once from glcorearb.h; GLboolean is a byte, GLsizeiptr a long, every
  // other integral parameter of the API used here is a 32-bit int.
  private static final class Handles {
    static final MethodHandle CLEAR = voidCall("glClear", JAVA_INT);
    static final MethodHandle CLEAR_COLOR =
        voidCall("glClearColor", JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT);
    static final MethodHandle VIEWPORT =
        voidCall("glViewport", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
    static final MethodHandle ENABLE = voidCall("glEnable", JAVA_INT);
    static final MethodHandle DISABLE = voidCall("glDisable", JAVA_INT);
    static final MethodHandle BLEND_FUNC = voidCall("glBlendFunc", JAVA_INT, JAVA_INT);
    static final MethodHandle FINISH = voidCall("glFinish");
    static final MethodHandle GET_ERROR = call("glGetError", FunctionDescriptor.of(JAVA_INT));
    static final MethodHandle GET_STRING =
        call("glGetString", FunctionDescriptor.of(ADDRESS, JAVA_INT));
    static final MethodHandle GET_STRINGI =
        call("glGetStringi", FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT));
    static final MethodHandle GET_INTEGERV = voidCall("glGetIntegerv", JAVA_INT, ADDRESS);
    static final MethodHandle PIXEL_STOREI = voidCall("glPixelStorei", JAVA_INT, JAVA_INT);

    static final MethodHandle GEN_TEXTURES = voidCall("glGenTextures", JAVA_INT, ADDRESS);
    static final MethodHandle DELETE_TEXTURES = voidCall("glDeleteTextures", JAVA_INT, ADDRESS);
    static final MethodHandle ACTIVE_TEXTURE = voidCall("glActiveTexture", JAVA_INT);
    static final MethodHandle BIND_TEXTURE = voidCall("glBindTexture", JAVA_INT, JAVA_INT);
    static final MethodHandle TEX_PARAMETERI =
        voidCall("glTexParameteri", JAVA_INT, JAVA_INT, JAVA_INT);
    static final MethodHandle TEX_IMAGE_2D =
        voidCall(
            "glTexImage2D",
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            ADDRESS);
    static final MethodHandle TEX_IMAGE_3D =
        voidCall(
            "glTexImage3D",
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            ADDRESS);
    static final MethodHandle TEX_SUB_IMAGE_3D =
        voidCall(
            "glTexSubImage3D",
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            ADDRESS);
    static final MethodHandle BIND_IMAGE_TEXTURE =
        voidCall(
            "glBindImageTexture",
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_BYTE,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT);
    static final MethodHandle READ_PIXELS =
        voidCall(
            "glReadPixels", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS);
    static final MethodHandle GET_TEX_IMAGE =
        voidCall("glGetTexImage", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS);

    static final MethodHandle GEN_FRAMEBUFFERS = voidCall("glGenFramebuffers", JAVA_INT, ADDRESS);
    static final MethodHandle DELETE_FRAMEBUFFERS =
        voidCall("glDeleteFramebuffers", JAVA_INT, ADDRESS);
    static final MethodHandle BIND_FRAMEBUFFER = voidCall("glBindFramebuffer", JAVA_INT, JAVA_INT);
    static final MethodHandle FRAMEBUFFER_TEXTURE_2D =
        voidCall("glFramebufferTexture2D", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
    static final MethodHandle FRAMEBUFFER_TEXTURE_LAYER =
        voidCall("glFramebufferTextureLayer", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
    static final MethodHandle CHECK_FRAMEBUFFER_STATUS =
        call("glCheckFramebufferStatus", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    static final MethodHandle DRAW_BUFFERS = voidCall("glDrawBuffers", JAVA_INT, ADDRESS);

    static final MethodHandle GEN_BUFFERS = voidCall("glGenBuffers", JAVA_INT, ADDRESS);
    static final MethodHandle DELETE_BUFFERS = voidCall("glDeleteBuffers", JAVA_INT, ADDRESS);
    static final MethodHandle BIND_BUFFER = voidCall("glBindBuffer", JAVA_INT, JAVA_INT);
    static final MethodHandle BUFFER_DATA =
        voidCall("glBufferData", JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT);
    static final MethodHandle MAP_BUFFER_RANGE =
        call(
            "glMapBufferRange",
            FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT));
    static final MethodHandle UNMAP_BUFFER =
        call("glUnmapBuffer", FunctionDescriptor.of(JAVA_BYTE, JAVA_INT));

    static final MethodHandle GEN_VERTEX_ARRAYS = voidCall("glGenVertexArrays", JAVA_INT, ADDRESS);
    static final MethodHandle BIND_VERTEX_ARRAY = voidCall("glBindVertexArray", JAVA_INT);
    static final MethodHandle DELETE_VERTEX_ARRAYS =
        voidCall("glDeleteVertexArrays", JAVA_INT, ADDRESS);
    static final MethodHandle ENABLE_VERTEX_ATTRIB_ARRAY =
        voidCall("glEnableVertexAttribArray", JAVA_INT);
    static final MethodHandle DISABLE_VERTEX_ATTRIB_ARRAY =
        voidCall("glDisableVertexAttribArray", JAVA_INT);
    static final MethodHandle VERTEX_ATTRIB_POINTER =
        voidCall(
            "glVertexAttribPointer", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_BYTE, JAVA_INT, ADDRESS);
    static final MethodHandle DRAW_ARRAYS = voidCall("glDrawArrays", JAVA_INT, JAVA_INT, JAVA_INT);

    static final MethodHandle CREATE_SHADER =
        call("glCreateShader", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    static final MethodHandle SHADER_SOURCE =
        voidCall("glShaderSource", JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);
    static final MethodHandle COMPILE_SHADER = voidCall("glCompileShader", JAVA_INT);
    static final MethodHandle GET_SHADERIV = voidCall("glGetShaderiv", JAVA_INT, JAVA_INT, ADDRESS);
    static final MethodHandle GET_SHADER_INFO_LOG =
        voidCall("glGetShaderInfoLog", JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);
    static final MethodHandle DELETE_SHADER = voidCall("glDeleteShader", JAVA_INT);
    static final MethodHandle CREATE_PROGRAM =
        call("glCreateProgram", FunctionDescriptor.of(JAVA_INT));
    static final MethodHandle ATTACH_SHADER = voidCall("glAttachShader", JAVA_INT, JAVA_INT);
    static final MethodHandle DETACH_SHADER = voidCall("glDetachShader", JAVA_INT, JAVA_INT);
    static final MethodHandle LINK_PROGRAM = voidCall("glLinkProgram", JAVA_INT);
    static final MethodHandle GET_PROGRAMIV =
        voidCall("glGetProgramiv", JAVA_INT, JAVA_INT, ADDRESS);
    static final MethodHandle GET_PROGRAM_INFO_LOG =
        voidCall("glGetProgramInfoLog", JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);
    static final MethodHandle USE_PROGRAM = voidCall("glUseProgram", JAVA_INT);
    static final MethodHandle DELETE_PROGRAM = voidCall("glDeleteProgram", JAVA_INT);
    static final MethodHandle GET_UNIFORM_LOCATION =
        call("glGetUniformLocation", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));

    static final MethodHandle UNIFORM_1I = voidCall("glUniform1i", JAVA_INT, JAVA_INT);
    static final MethodHandle UNIFORM_1UI = voidCall("glUniform1ui", JAVA_INT, JAVA_INT);
    static final MethodHandle UNIFORM_1F = voidCall("glUniform1f", JAVA_INT, JAVA_FLOAT);
    static final MethodHandle UNIFORM_2F =
        voidCall("glUniform2f", JAVA_INT, JAVA_FLOAT, JAVA_FLOAT);
    static final MethodHandle UNIFORM_3F =
        voidCall("glUniform3f", JAVA_INT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT);
    static final MethodHandle UNIFORM_3I =
        voidCall("glUniform3i", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
    static final MethodHandle UNIFORM_1FV = voidCall("glUniform1fv", JAVA_INT, JAVA_INT, ADDRESS);
    static final MethodHandle UNIFORM_3FV = voidCall("glUniform3fv", JAVA_INT, JAVA_INT, ADDRESS);
    static final MethodHandle UNIFORM_4FV = voidCall("glUniform4fv", JAVA_INT, JAVA_INT, ADDRESS);
    static final MethodHandle UNIFORM_MATRIX_3FV =
        voidCall("glUniformMatrix3fv", JAVA_INT, JAVA_INT, JAVA_BYTE, ADDRESS);
    static final MethodHandle UNIFORM_MATRIX_4FV =
        voidCall("glUniformMatrix4fv", JAVA_INT, JAVA_INT, JAVA_BYTE, ADDRESS);

    private Handles() {}
  }

  /**
   * The entry points of the compute path, which is OpenGL 4.2 and above. They are held apart
   * because a driver that stops at 4.1 — every Mac — does not export them at all, and resolving
   * them beside the others would fail the whole binding on a renderer that never needs them.
   */
  private static final class ComputeHandles {
    static final MethodHandle BIND_IMAGE_TEXTURE =
        voidCall(
            "glBindImageTexture",
            JAVA_INT,
            JAVA_INT,
            JAVA_INT,
            JAVA_BYTE,
            JAVA_INT,
            JAVA_INT,
            JAVA_INT);
    static final MethodHandle DISPATCH_COMPUTE =
        voidCall("glDispatchCompute", JAVA_INT, JAVA_INT, JAVA_INT);
    static final MethodHandle MEMORY_BARRIER = voidCall("glMemoryBarrier", JAVA_INT);

    private ComputeHandles() {}
  }

  // A GL call never throws by contract; a Throwable here is a broken descriptor, not a driver
  // error.
  private static RuntimeException failed(String name, Throwable t) {
    return new IllegalStateException("OpenGL call failed: " + name, t);
  }

  private static byte glBoolean(boolean value) {
    return value ? (byte) GL_TRUE : (byte) GL_FALSE;
  }

  private MemorySegment ints(int... values) {
    MemorySegment segment = scratch.asSlice(0, (long) values.length * Integer.BYTES);
    MemorySegment.copy(values, 0, segment, JAVA_INT, 0, values.length);
    return segment;
  }

  private MemorySegment floats(float[] values) {
    MemorySegment segment = scratch.asSlice(0, (long) values.length * Float.BYTES);
    MemorySegment.copy(values, 0, segment, JAVA_FLOAT, 0, values.length);
    return segment;
  }

  private MemorySegment out(int count) {
    return scratch.asSlice(0, (long) count * Integer.BYTES).fill((byte) 0);
  }

  private static MemorySegment pointer(MemorySegment segment) {
    return segment == null ? MemorySegment.NULL : segment;
  }

  // ── State and queries ──

  @Override
  public String glGetString(int name) {
    try {
      MemorySegment value = (MemorySegment) Handles.GET_STRING.invokeExact(name);
      return MemorySegment.NULL.equals(value)
          ? null
          : value.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
    } catch (Throwable t) {
      throw failed("glGetString", t);
    }
  }

  @Override
  public int glGetInteger(int pname) {
    try {
      MemorySegment value = out(1);
      Handles.GET_INTEGERV.invokeExact(pname, value);
      return value.get(JAVA_INT, 0);
    } catch (Throwable t) {
      throw failed("glGetIntegerv", t);
    }
  }

  @Override
  public int glGetError() {
    try {
      return (int) Handles.GET_ERROR.invokeExact();
    } catch (Throwable t) {
      throw failed("glGetError", t);
    }
  }

  @Override
  public void glFinish() {
    try {
      Handles.FINISH.invokeExact();
    } catch (Throwable t) {
      throw failed("glFinish", t);
    }
  }

  @Override
  public void glClear(int mask) {
    try {
      Handles.CLEAR.invokeExact(mask);
    } catch (Throwable t) {
      throw failed("glClear", t);
    }
  }

  @Override
  public void glClearColor(float red, float green, float blue, float alpha) {
    try {
      Handles.CLEAR_COLOR.invokeExact(red, green, blue, alpha);
    } catch (Throwable t) {
      throw failed("glClearColor", t);
    }
  }

  @Override
  public void glViewport(int x, int y, int width, int height) {
    try {
      Handles.VIEWPORT.invokeExact(x, y, width, height);
    } catch (Throwable t) {
      throw failed("glViewport", t);
    }
  }

  @Override
  public void glEnable(int cap) {
    try {
      Handles.ENABLE.invokeExact(cap);
    } catch (Throwable t) {
      throw failed("glEnable", t);
    }
  }

  @Override
  public void glDisable(int cap) {
    try {
      Handles.DISABLE.invokeExact(cap);
    } catch (Throwable t) {
      throw failed("glDisable", t);
    }
  }

  @Override
  public void glBlendFunc(int sfactor, int dfactor) {
    try {
      Handles.BLEND_FUNC.invokeExact(sfactor, dfactor);
    } catch (Throwable t) {
      throw failed("glBlendFunc", t);
    }
  }

  @Override
  public void glPixelStorei(int pname, int param) {
    try {
      Handles.PIXEL_STOREI.invokeExact(pname, param);
    } catch (Throwable t) {
      throw failed("glPixelStorei", t);
    }
  }

  // ── Textures ──

  @Override
  public int[] glGenTextures(int count) {
    try {
      MemorySegment names = out(count);
      Handles.GEN_TEXTURES.invokeExact(count, names);
      return names.toArray(JAVA_INT);
    } catch (Throwable t) {
      throw failed("glGenTextures", t);
    }
  }

  @Override
  public void glDeleteTextures(int... textures) {
    try {
      Handles.DELETE_TEXTURES.invokeExact(textures.length, ints(textures));
    } catch (Throwable t) {
      throw failed("glDeleteTextures", t);
    }
  }

  @Override
  public void glActiveTexture(int texture) {
    try {
      Handles.ACTIVE_TEXTURE.invokeExact(texture);
    } catch (Throwable t) {
      throw failed("glActiveTexture", t);
    }
  }

  @Override
  public void glBindTexture(int target, int texture) {
    try {
      Handles.BIND_TEXTURE.invokeExact(target, texture);
    } catch (Throwable t) {
      throw failed("glBindTexture", t);
    }
  }

  @Override
  public void glTexParameteri(int target, int pname, int param) {
    try {
      Handles.TEX_PARAMETERI.invokeExact(target, pname, param);
    } catch (Throwable t) {
      throw failed("glTexParameteri", t);
    }
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
    try {
      Handles.TEX_IMAGE_2D.invokeExact(
          target, level, internalFormat, width, height, border, format, type, pointer(pixels));
    } catch (Throwable t) {
      throw failed("glTexImage2D", t);
    }
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
    try {
      Handles.TEX_IMAGE_3D.invokeExact(
          target,
          level,
          internalFormat,
          width,
          height,
          depth,
          border,
          format,
          type,
          pointer(pixels));
    } catch (Throwable t) {
      throw failed("glTexImage3D", t);
    }
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
    try {
      Handles.TEX_SUB_IMAGE_3D.invokeExact(
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
          pointer(pixels));
    } catch (Throwable t) {
      throw failed("glTexSubImage3D", t);
    }
  }

  @Override
  public void glBindImageTexture(
      int unit, int texture, int level, boolean layered, int layer, int access, int format) {
    try {
      ComputeHandles.BIND_IMAGE_TEXTURE.invokeExact(
          unit, texture, level, glBoolean(layered), layer, access, format);
    } catch (Throwable t) {
      throw failed("glBindImageTexture", t);
    }
  }

  @Override
  public void glReadPixels(
      int x, int y, int width, int height, int format, int type, MemorySegment pixels) {
    try {
      Handles.READ_PIXELS.invokeExact(x, y, width, height, format, type, pointer(pixels));
    } catch (Throwable t) {
      throw failed("glReadPixels", t);
    }
  }

  @Override
  public void glGetTexImage(int target, int level, int format, int type, MemorySegment pixels) {
    try {
      Handles.GET_TEX_IMAGE.invokeExact(target, level, format, type, pointer(pixels));
    } catch (Throwable t) {
      throw failed("glGetTexImage", t);
    }
  }

  // ── Framebuffers ──

  @Override
  public int[] glGenFramebuffers(int count) {
    try {
      MemorySegment names = out(count);
      Handles.GEN_FRAMEBUFFERS.invokeExact(count, names);
      return names.toArray(JAVA_INT);
    } catch (Throwable t) {
      throw failed("glGenFramebuffers", t);
    }
  }

  @Override
  public void glDeleteFramebuffers(int... framebuffers) {
    try {
      Handles.DELETE_FRAMEBUFFERS.invokeExact(framebuffers.length, ints(framebuffers));
    } catch (Throwable t) {
      throw failed("glDeleteFramebuffers", t);
    }
  }

  @Override
  public void glBindFramebuffer(int target, int framebuffer) {
    try {
      Handles.BIND_FRAMEBUFFER.invokeExact(target, framebuffer);
    } catch (Throwable t) {
      throw failed("glBindFramebuffer", t);
    }
  }

  @Override
  public void glFramebufferTexture2D(
      int target, int attachment, int textureTarget, int texture, int level) {
    try {
      Handles.FRAMEBUFFER_TEXTURE_2D.invokeExact(target, attachment, textureTarget, texture, level);
    } catch (Throwable t) {
      throw failed("glFramebufferTexture2D", t);
    }
  }

  @Override
  public void glFramebufferTextureLayer(
      int target, int attachment, int texture, int level, int layer) {
    try {
      Handles.FRAMEBUFFER_TEXTURE_LAYER.invokeExact(target, attachment, texture, level, layer);
    } catch (Throwable t) {
      throw failed("glFramebufferTextureLayer", t);
    }
  }

  @Override
  public int glCheckFramebufferStatus(int target) {
    try {
      return (int) Handles.CHECK_FRAMEBUFFER_STATUS.invokeExact(target);
    } catch (Throwable t) {
      throw failed("glCheckFramebufferStatus", t);
    }
  }

  @Override
  public void glDrawBuffers(int... buffers) {
    try {
      Handles.DRAW_BUFFERS.invokeExact(buffers.length, ints(buffers));
    } catch (Throwable t) {
      throw failed("glDrawBuffers", t);
    }
  }

  // ── Buffer objects ──

  @Override
  public int[] glGenBuffers(int count) {
    try {
      MemorySegment names = out(count);
      Handles.GEN_BUFFERS.invokeExact(count, names);
      return names.toArray(JAVA_INT);
    } catch (Throwable t) {
      throw failed("glGenBuffers", t);
    }
  }

  @Override
  public void glDeleteBuffers(int... buffers) {
    try {
      Handles.DELETE_BUFFERS.invokeExact(buffers.length, ints(buffers));
    } catch (Throwable t) {
      throw failed("glDeleteBuffers", t);
    }
  }

  @Override
  public void glBindBuffer(int target, int buffer) {
    try {
      Handles.BIND_BUFFER.invokeExact(target, buffer);
    } catch (Throwable t) {
      throw failed("glBindBuffer", t);
    }
  }

  @Override
  public void glBufferData(int target, long size, MemorySegment data, int usage) {
    try {
      Handles.BUFFER_DATA.invokeExact(target, size, pointer(data), usage);
    } catch (Throwable t) {
      throw failed("glBufferData", t);
    }
  }

  @Override
  public MemorySegment glMapBufferRange(int target, long offset, long length, int access) {
    try {
      MemorySegment mapped =
          (MemorySegment) Handles.MAP_BUFFER_RANGE.invokeExact(target, offset, length, access);
      return MemorySegment.NULL.equals(mapped) ? MemorySegment.NULL : mapped.reinterpret(length);
    } catch (Throwable t) {
      throw failed("glMapBufferRange", t);
    }
  }

  @Override
  public boolean glUnmapBuffer(int target) {
    try {
      return (byte) Handles.UNMAP_BUFFER.invokeExact(target) != GL_FALSE;
    } catch (Throwable t) {
      throw failed("glUnmapBuffer", t);
    }
  }

  // ── Vertex arrays ──

  @Override
  public int glGenVertexArray() {
    try {
      MemorySegment names = out(1);
      Handles.GEN_VERTEX_ARRAYS.invokeExact(1, names);
      return names.get(JAVA_INT, 0);
    } catch (Throwable t) {
      throw failed("glGenVertexArrays", t);
    }
  }

  @Override
  public void glBindVertexArray(int array) {
    try {
      Handles.BIND_VERTEX_ARRAY.invokeExact(array);
    } catch (Throwable t) {
      throw failed("glBindVertexArray", t);
    }
  }

  @Override
  public void glDeleteVertexArrays(int... arrays) {
    try {
      Handles.DELETE_VERTEX_ARRAYS.invokeExact(arrays.length, ints(arrays));
    } catch (Throwable t) {
      throw failed("glDeleteVertexArrays", t);
    }
  }

  @Override
  public void glEnableVertexAttribArray(int index) {
    try {
      Handles.ENABLE_VERTEX_ATTRIB_ARRAY.invokeExact(index);
    } catch (Throwable t) {
      throw failed("glEnableVertexAttribArray", t);
    }
  }

  @Override
  public void glDisableVertexAttribArray(int index) {
    try {
      Handles.DISABLE_VERTEX_ATTRIB_ARRAY.invokeExact(index);
    } catch (Throwable t) {
      throw failed("glDisableVertexAttribArray", t);
    }
  }

  @Override
  public void glVertexAttribPointer(
      int index, int size, int type, boolean normalized, int stride, long offset) {
    try {
      // With a buffer bound the pointer is an offset into it, not an address.
      Handles.VERTEX_ATTRIB_POINTER.invokeExact(
          index, size, type, glBoolean(normalized), stride, MemorySegment.ofAddress(offset));
    } catch (Throwable t) {
      throw failed("glVertexAttribPointer", t);
    }
  }

  @Override
  public void glDrawArrays(int mode, int first, int count) {
    try {
      Handles.DRAW_ARRAYS.invokeExact(mode, first, count);
    } catch (Throwable t) {
      throw failed("glDrawArrays", t);
    }
  }

  // ── Shaders and programs ──

  @Override
  public int glCreateShader(int type) {
    try {
      return (int) Handles.CREATE_SHADER.invokeExact(type);
    } catch (Throwable t) {
      throw failed("glCreateShader", t);
    }
  }

  @Override
  public void glShaderSource(int shader, String source) {
    // A shader source is far larger than the scratch block and lives only for this call.
    try (Arena call = Arena.ofConfined()) {
      MemorySegment text = call.allocateFrom(source);
      MemorySegment strings = call.allocate(ADDRESS);
      strings.set(ADDRESS, 0, text);
      Handles.SHADER_SOURCE.invokeExact(shader, 1, strings, MemorySegment.NULL);
    } catch (Throwable t) {
      throw failed("glShaderSource", t);
    }
  }

  @Override
  public void glCompileShader(int shader) {
    try {
      Handles.COMPILE_SHADER.invokeExact(shader);
    } catch (Throwable t) {
      throw failed("glCompileShader", t);
    }
  }

  @Override
  public int glGetShaderi(int shader, int pname) {
    try {
      MemorySegment value = out(1);
      Handles.GET_SHADERIV.invokeExact(shader, pname, value);
      return value.get(JAVA_INT, 0);
    } catch (Throwable t) {
      throw failed("glGetShaderiv", t);
    }
  }

  @Override
  public String glGetShaderInfoLog(int shader) {
    try {
      MemorySegment length = out(1);
      Handles.GET_SHADER_INFO_LOG.invokeExact(shader, LOG_BUFFER_SIZE, length, log);
      return readLog(length.get(JAVA_INT, 0));
    } catch (Throwable t) {
      throw failed("glGetShaderInfoLog", t);
    }
  }

  @Override
  public void glDeleteShader(int shader) {
    try {
      Handles.DELETE_SHADER.invokeExact(shader);
    } catch (Throwable t) {
      throw failed("glDeleteShader", t);
    }
  }

  @Override
  public int glCreateProgram() {
    try {
      return (int) Handles.CREATE_PROGRAM.invokeExact();
    } catch (Throwable t) {
      throw failed("glCreateProgram", t);
    }
  }

  @Override
  public void glAttachShader(int program, int shader) {
    try {
      Handles.ATTACH_SHADER.invokeExact(program, shader);
    } catch (Throwable t) {
      throw failed("glAttachShader", t);
    }
  }

  @Override
  public void glDetachShader(int program, int shader) {
    try {
      Handles.DETACH_SHADER.invokeExact(program, shader);
    } catch (Throwable t) {
      throw failed("glDetachShader", t);
    }
  }

  @Override
  public void glLinkProgram(int program) {
    try {
      Handles.LINK_PROGRAM.invokeExact(program);
    } catch (Throwable t) {
      throw failed("glLinkProgram", t);
    }
  }

  @Override
  public int glGetProgrami(int program, int pname) {
    try {
      MemorySegment value = out(1);
      Handles.GET_PROGRAMIV.invokeExact(program, pname, value);
      return value.get(JAVA_INT, 0);
    } catch (Throwable t) {
      throw failed("glGetProgramiv", t);
    }
  }

  @Override
  public String glGetProgramInfoLog(int program) {
    try {
      MemorySegment length = out(1);
      Handles.GET_PROGRAM_INFO_LOG.invokeExact(program, LOG_BUFFER_SIZE, length, log);
      return readLog(length.get(JAVA_INT, 0));
    } catch (Throwable t) {
      throw failed("glGetProgramInfoLog", t);
    }
  }

  // The driver log is only valid up to the length it reports.
  private String readLog(int length) {
    byte[] bytes = log.asSlice(0, Math.clamp(length, 0, LOG_BUFFER_SIZE)).toArray(JAVA_BYTE);
    return new String(bytes, StandardCharsets.UTF_8).trim();
  }

  @Override
  public void glUseProgram(int program) {
    try {
      Handles.USE_PROGRAM.invokeExact(program);
    } catch (Throwable t) {
      throw failed("glUseProgram", t);
    }
  }

  @Override
  public void glDeleteProgram(int program) {
    try {
      Handles.DELETE_PROGRAM.invokeExact(program);
    } catch (Throwable t) {
      throw failed("glDeleteProgram", t);
    }
  }

  @Override
  public int glGetUniformLocation(int program, String name) {
    try (Arena call = Arena.ofConfined()) {
      return (int) Handles.GET_UNIFORM_LOCATION.invokeExact(program, call.allocateFrom(name));
    } catch (Throwable t) {
      throw failed("glGetUniformLocation", t);
    }
  }

  // ── Uniforms ──

  @Override
  public void glUniform1i(int location, int v0) {
    try {
      Handles.UNIFORM_1I.invokeExact(location, v0);
    } catch (Throwable t) {
      throw failed("glUniform1i", t);
    }
  }

  @Override
  public void glUniform1ui(int location, int v0) {
    try {
      Handles.UNIFORM_1UI.invokeExact(location, v0);
    } catch (Throwable t) {
      throw failed("glUniform1ui", t);
    }
  }

  @Override
  public void glUniform1f(int location, float v0) {
    try {
      Handles.UNIFORM_1F.invokeExact(location, v0);
    } catch (Throwable t) {
      throw failed("glUniform1f", t);
    }
  }

  @Override
  public void glUniform2f(int location, float v0, float v1) {
    try {
      Handles.UNIFORM_2F.invokeExact(location, v0, v1);
    } catch (Throwable t) {
      throw failed("glUniform2f", t);
    }
  }

  @Override
  public void glUniform3f(int location, float v0, float v1, float v2) {
    try {
      Handles.UNIFORM_3F.invokeExact(location, v0, v1, v2);
    } catch (Throwable t) {
      throw failed("glUniform3f", t);
    }
  }

  @Override
  public void glUniform3i(int location, int v0, int v1, int v2) {
    try {
      Handles.UNIFORM_3I.invokeExact(location, v0, v1, v2);
    } catch (Throwable t) {
      throw failed("glUniform3i", t);
    }
  }

  @Override
  public void glUniform1fv(int location, float[] value) {
    try {
      Handles.UNIFORM_1FV.invokeExact(location, value.length, floats(value));
    } catch (Throwable t) {
      throw failed("glUniform1fv", t);
    }
  }

  @Override
  public void glUniform3fv(int location, float[] value) {
    try {
      Handles.UNIFORM_3FV.invokeExact(location, value.length / 3, floats(value));
    } catch (Throwable t) {
      throw failed("glUniform3fv", t);
    }
  }

  @Override
  public void glUniform4fv(int location, float[] value) {
    try {
      Handles.UNIFORM_4FV.invokeExact(location, value.length / 4, floats(value));
    } catch (Throwable t) {
      throw failed("glUniform4fv", t);
    }
  }

  @Override
  public void glUniformMatrix3fv(int location, boolean transpose, float[] value) {
    try {
      Handles.UNIFORM_MATRIX_3FV.invokeExact(
          location, value.length / 9, glBoolean(transpose), floats(value));
    } catch (Throwable t) {
      throw failed("glUniformMatrix3fv", t);
    }
  }

  @Override
  public void glUniformMatrix4fv(int location, boolean transpose, float[] value) {
    try {
      Handles.UNIFORM_MATRIX_4FV.invokeExact(
          location, value.length / 16, glBoolean(transpose), floats(value));
    } catch (Throwable t) {
      throw failed("glUniformMatrix4fv", t);
    }
  }

  // ── Compute ──

  @Override
  public void glDispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
    try {
      ComputeHandles.DISPATCH_COMPUTE.invokeExact(numGroupsX, numGroupsY, numGroupsZ);
    } catch (Throwable t) {
      throw failed("glDispatchCompute", t);
    }
  }

  @Override
  public void glMemoryBarrier(int barriers) {
    try {
      ComputeHandles.MEMORY_BARRIER.invokeExact(barriers);
    } catch (Throwable t) {
      throw failed("glMemoryBarrier", t);
    }
  }
}
