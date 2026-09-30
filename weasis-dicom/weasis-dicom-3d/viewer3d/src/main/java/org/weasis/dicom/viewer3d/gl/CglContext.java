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
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The macOS context, created through CGL — the layer below the AppKit classes, which is what makes
 * it usable from the renderer's own thread: unlike {@code NSOpenGLContext}, CGL has no main-thread
 * constraint.
 *
 * <p>No drawable is attached. A CGL context with none is exactly what an FBO needs, and the viewer
 * never presents to a surface.
 *
 * <p>macOS stopped at OpenGL 4.1, so the compute path is never available here and the renderer
 * takes the FBO path, as it already did under JOGL.
 */
public final class CglContext implements GlContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(CglContext.class);

  private static final Linker LINKER = Linker.nativeLinker();

  private static final int CGL_NO_ERROR = 0;

  // CGLPixelFormatAttribute, from CGLTypes.h.
  private static final int PFA_COLOR_SIZE = 8;
  private static final int PFA_ALPHA_SIZE = 11;
  private static final int PFA_ACCELERATED = 73;
  private static final int PFA_ALLOW_OFFLINE_RENDERERS = 96;
  private static final int PFA_OPENGL_PROFILE = 99;

  /** {@code kCGLOGLPVersion_GL4_Core}, which on macOS means 4.1 at most. */
  private static final int PROFILE_GL4_CORE = 0x4100;

  /** {@code kCGLOGLPVersion_GL3_Core}, the 3.2 core profile that still satisfies the shaders. */
  private static final int PROFILE_GL3_CORE = 0x3200;

  /**
   * The framework is opened by path: since macOS 11 it is not a file on disk but an entry of the
   * dyld shared cache, which {@code dlopen} still resolves by that path.
   */
  private static final List<String> LIBRARIES =
      List.of("/System/Library/Frameworks/OpenGL.framework/OpenGL", "OpenGL"); // NON-NLS

  private final MemorySegment pixelFormat;
  private final MemorySegment context;
  private final FfmGl gl;
  private final String description;
  private boolean destroyed;

  private CglContext(MemorySegment pixelFormat, MemorySegment context, boolean computeCapable) {
    this.pixelFormat = pixelFormat;
    this.context = context;
    this.gl = new FfmGl(Cgl.LIBRARY, computeCapable);
    this.description =
        "%s — OpenGL %s (%s, CGL)"
            .formatted(
                gl.glGetString(Gl.GL_RENDERER),
                gl.glGetString(Gl.GL_VERSION),
                gl.glGetString(Gl.GL_VENDOR));
  }

  // ── Entry points, resolved on first use so nothing is loaded on another platform ──

  private static final class Cgl {
    /** OpenGL.framework exports both CGL and the GL entry points, up to the 4.1 macOS supports. */
    static final SymbolLookup LIBRARY = library();

    static final MethodHandle CHOOSE_PIXEL_FORMAT =
        call(
            "CGLChoosePixelFormat", // NON-NLS
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    static final MethodHandle RELEASE_PIXEL_FORMAT =
        call("CGLReleasePixelFormat", FunctionDescriptor.ofVoid(ADDRESS)); // NON-NLS
    static final MethodHandle CREATE_CONTEXT =
        call(
            "CGLCreateContext", // NON-NLS
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    static final MethodHandle DESTROY_CONTEXT =
        call("CGLDestroyContext", FunctionDescriptor.of(JAVA_INT, ADDRESS)); // NON-NLS
    static final MethodHandle SET_CURRENT_CONTEXT =
        call("CGLSetCurrentContext", FunctionDescriptor.of(JAVA_INT, ADDRESS)); // NON-NLS
    static final MethodHandle ERROR_STRING =
        call("CGLErrorString", FunctionDescriptor.of(ADDRESS, JAVA_INT)); // NON-NLS

    private static SymbolLookup library() {
      for (String name : LIBRARIES) {
        try {
          return SymbolLookup.libraryLookup(name, Arena.global());
        } catch (IllegalArgumentException e) {
          LOGGER.debug("Cannot load {}: {}", name, e.getMessage());
        }
      }
      throw new GlException("Cannot load OpenGL.framework");
    }

    private static MethodHandle call(String name, FunctionDescriptor descriptor) {
      return LINKER.downcallHandle(
          LIBRARY
              .find(name)
              .orElseThrow(() -> new GlException("CGL entry point not found: " + name)),
          descriptor);
    }

    private Cgl() {}
  }

  private static String errorString(int error) {
    try {
      MemorySegment message = (MemorySegment) Cgl.ERROR_STRING.invokeExact(error);
      return MemorySegment.NULL.equals(message)
          ? "CGL error " + error
          : message.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
    } catch (Throwable t) {
      return "CGL error " + error;
    }
  }

  // ── Creation ──

  static CglContext create() {
    GlException refusal = null;
    // The GL4 profile first, then the 3.2 one that still compiles the shaders; accelerated only,
    // then without, so a machine with no GPU renderer still says something useful.
    for (int profile : new int[] {PROFILE_GL4_CORE, PROFILE_GL3_CORE}) {
      for (boolean accelerated : new boolean[] {true, false}) {
        try {
          return open(profile, accelerated);
        } catch (GlException e) {
          refusal = e;
          LOGGER.debug(
              "No CGL context for profile 0x{}{}: {}",
              Integer.toHexString(profile),
              accelerated ? " (accelerated)" : "", // NON-NLS
              e.getMessage());
        }
      }
    }
    throw refusal;
  }

  private static CglContext open(int profile, boolean accelerated) {
    MemorySegment pixelFormat = choosePixelFormat(profile, accelerated);
    MemorySegment context = MemorySegment.NULL;
    CglContext created = null;
    try {
      context = createContext(pixelFormat);
      setCurrent(context);
      // Neither profile CGL offers reaches 4.3, so the compute path is never taken on macOS.
      created = new CglContext(pixelFormat, context, false);
      // CGL grants a profile, not a version: what the driver actually gives is read back.
      int[] version = GlContext.parseVersion(created.gl.glGetString(Gl.GL_VERSION));
      if (!GlContext.isSupported(version)) {
        throw new GlException(
            "This renderer offers OpenGL %d.%d, below the %d.%d the shaders need"
                .formatted(version[0], version[1], GlContext.MINIMUM[0], GlContext.MINIMUM[1]));
      }
      LOGGER.info("OpenGL {}.{} core context created through CGL", version[0], version[1]);
      return created;
    } catch (GlException e) {
      if (created != null) {
        created.destroy();
      } else {
        setCurrentQuietly(MemorySegment.NULL);
        destroyContext(context);
        releasePixelFormat(pixelFormat);
      }
      throw e;
    }
  }

  private static MemorySegment choosePixelFormat(int profile, boolean accelerated) {
    try (Arena call = Arena.ofConfined()) {
      int[] attributes =
          accelerated
              ? new int[] {
                PFA_OPENGL_PROFILE,
                profile,
                PFA_ACCELERATED,
                PFA_ALLOW_OFFLINE_RENDERERS,
                PFA_COLOR_SIZE,
                24,
                PFA_ALPHA_SIZE,
                8,
                0
              }
              : new int[] {PFA_OPENGL_PROFILE, profile, PFA_COLOR_SIZE, 24, PFA_ALPHA_SIZE, 8, 0};
      MemorySegment list = call.allocateFrom(JAVA_INT, attributes);
      MemorySegment result = call.allocate(ADDRESS);
      MemorySegment count = call.allocate(JAVA_INT);
      int error = (int) Cgl.CHOOSE_PIXEL_FORMAT.invokeExact(list, result, count);
      if (error != CGL_NO_ERROR || count.get(JAVA_INT, 0) < 1) {
        throw new GlException("CGLChoosePixelFormat: " + errorString(error));
      }
      return result.get(ADDRESS, 0);
    } catch (GlException e) {
      throw e;
    } catch (Throwable t) {
      throw new GlException("CGLChoosePixelFormat failed", t);
    }
  }

  private static MemorySegment createContext(MemorySegment pixelFormat) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment result = call.allocate(ADDRESS);
      int error = (int) Cgl.CREATE_CONTEXT.invokeExact(pixelFormat, MemorySegment.NULL, result);
      if (error != CGL_NO_ERROR) {
        throw new GlException("CGLCreateContext: " + errorString(error));
      }
      return result.get(ADDRESS, 0);
    } catch (GlException e) {
      throw e;
    } catch (Throwable t) {
      throw new GlException("CGLCreateContext failed", t);
    }
  }

  private static void setCurrent(MemorySegment context) {
    try {
      int error = (int) Cgl.SET_CURRENT_CONTEXT.invokeExact(context);
      if (error != CGL_NO_ERROR) {
        throw new GlException("CGLSetCurrentContext: " + errorString(error));
      }
    } catch (GlException e) {
      throw e;
    } catch (Throwable t) {
      throw new GlException("CGLSetCurrentContext failed", t);
    }
  }

  private static void setCurrentQuietly(MemorySegment context) {
    try {
      int ignored = (int) Cgl.SET_CURRENT_CONTEXT.invokeExact(context);
    } catch (Throwable t) {
      LOGGER.debug("CGLSetCurrentContext failed", t);
    }
  }

  private static void destroyContext(MemorySegment context) {
    if (MemorySegment.NULL.equals(context)) {
      return;
    }
    try {
      int ignored = (int) Cgl.DESTROY_CONTEXT.invokeExact(context);
    } catch (Throwable t) {
      LOGGER.debug("CGLDestroyContext failed", t);
    }
  }

  private static void releasePixelFormat(MemorySegment pixelFormat) {
    if (MemorySegment.NULL.equals(pixelFormat)) {
      return;
    }
    try {
      Cgl.RELEASE_PIXEL_FORMAT.invokeExact(pixelFormat);
    } catch (Throwable t) {
      LOGGER.debug("CGLReleasePixelFormat failed", t);
    }
  }

  // ── GlContext ──

  @Override
  public void makeCurrent() {
    setCurrent(context);
  }

  @Override
  public void release() {
    setCurrentQuietly(MemorySegment.NULL);
  }

  @Override
  public void destroy() {
    if (destroyed) {
      return;
    }
    destroyed = true;
    release();
    destroyContext(context);
    releasePixelFormat(pixelFormat);
    gl.close();
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public String name() {
    return "CGL"; // NON-NLS
  }

  @Override
  public Gl gl() {
    return gl;
  }
}
