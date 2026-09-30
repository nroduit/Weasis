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
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Windows context: a core OpenGL context on an invisible window that is never painted, only
 * used to own a pixel format.
 *
 * <p>WGL gives no way to ask for a core profile directly — {@code wglCreateContextAttribsARB} is
 * itself an extension, so a legacy context has to be created first just to resolve it, and is
 * deleted as soon as the real one exists. The window is created as a message-only child, which
 * receives no broadcast and therefore needs no message pump on the GL thread; drivers that refuse a
 * pixel format on such a window get a hidden top-level one instead.
 *
 * <p>The window class uses {@code DefWindowProcW} itself as the window procedure, so no Java upcall
 * stub is ever called by the system.
 */
public final class WglContext implements GlContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(WglContext.class);

  private static final Linker LINKER = Linker.nativeLinker();

  // ── Win32 structures, as wingdi.h and winuser.h lay them out on x64 ──

  static final StructLayout PIXEL_FORMAT_DESCRIPTOR =
      MemoryLayout.structLayout(
          JAVA_SHORT.withName("nSize"),
          JAVA_SHORT.withName("nVersion"),
          JAVA_INT.withName("dwFlags"),
          JAVA_BYTE.withName("iPixelType"),
          JAVA_BYTE.withName("cColorBits"),
          JAVA_BYTE.withName("cRedBits"),
          JAVA_BYTE.withName("cRedShift"),
          JAVA_BYTE.withName("cGreenBits"),
          JAVA_BYTE.withName("cGreenShift"),
          JAVA_BYTE.withName("cBlueBits"),
          JAVA_BYTE.withName("cBlueShift"),
          JAVA_BYTE.withName("cAlphaBits"),
          JAVA_BYTE.withName("cAlphaShift"),
          JAVA_BYTE.withName("cAccumBits"),
          JAVA_BYTE.withName("cAccumRedBits"),
          JAVA_BYTE.withName("cAccumGreenBits"),
          JAVA_BYTE.withName("cAccumBlueBits"),
          JAVA_BYTE.withName("cAccumAlphaBits"),
          JAVA_BYTE.withName("cDepthBits"),
          JAVA_BYTE.withName("cStencilBits"),
          JAVA_BYTE.withName("cAuxBuffers"),
          JAVA_BYTE.withName("iLayerType"),
          JAVA_BYTE.withName("bReserved"),
          JAVA_INT.withName("dwLayerMask"),
          JAVA_INT.withName("dwVisibleMask"),
          JAVA_INT.withName("dwDamageMask"));

  static final StructLayout WNDCLASSEXW =
      MemoryLayout.structLayout(
          JAVA_INT.withName("cbSize"),
          JAVA_INT.withName("style"),
          ADDRESS.withName("lpfnWndProc"),
          JAVA_INT.withName("cbClsExtra"),
          JAVA_INT.withName("cbWndExtra"),
          ADDRESS.withName("hInstance"),
          ADDRESS.withName("hIcon"),
          ADDRESS.withName("hCursor"),
          ADDRESS.withName("hbrBackground"),
          ADDRESS.withName("lpszMenuName"),
          ADDRESS.withName("lpszClassName"),
          ADDRESS.withName("hIconSm"));

  // ── Win32 and WGL constants ──

  private static final long HWND_MESSAGE = -3L;
  private static final int CS_OWNDC = 0x0020;
  private static final int WS_OVERLAPPEDWINDOW = 0x00CF0000;

  private static final int PFD_DOUBLEBUFFER = 0x00000001;
  private static final int PFD_DRAW_TO_WINDOW = 0x00000004;
  private static final int PFD_SUPPORT_OPENGL = 0x00000020;
  private static final byte PFD_TYPE_RGBA = 0;
  private static final byte PFD_MAIN_PLANE = 0;

  private static final int WGL_CONTEXT_MAJOR_VERSION_ARB = 0x2091;
  private static final int WGL_CONTEXT_MINOR_VERSION_ARB = 0x2092;
  private static final int WGL_CONTEXT_PROFILE_MASK_ARB = 0x9126;
  private static final int WGL_CONTEXT_CORE_PROFILE_BIT_ARB = 0x00000001;

  private static final String CLASS_NAME = "WeasisOpenGL"; // NON-NLS

  private final MemorySegment window;
  private final MemorySegment deviceContext;
  private final MemorySegment context;
  private final MemorySegment moduleHandle;
  private final FfmGl gl;
  private final String description;
  private boolean destroyed;

  private WglContext(
      MemorySegment window,
      MemorySegment deviceContext,
      MemorySegment context,
      MemorySegment moduleHandle,
      boolean computeCapable) {
    this.window = window;
    this.deviceContext = deviceContext;
    this.context = context;
    this.moduleHandle = moduleHandle;
    this.gl = new FfmGl(glLookup(), computeCapable);
    this.description =
        "%s — OpenGL %s (%s, WGL)"
            .formatted(
                gl.glGetString(Gl.GL_RENDERER),
                gl.glGetString(Gl.GL_VERSION),
                gl.glGetString(Gl.GL_VENDOR));
  }

  // ── Entry points, resolved on first use so nothing is loaded on another platform ──

  private static final class Win32 {
    static final SymbolLookup USER32 = library("user32"); // NON-NLS
    static final SymbolLookup GDI32 = library("gdi32"); // NON-NLS
    static final SymbolLookup OPENGL32 = library("opengl32"); // NON-NLS
    static final SymbolLookup KERNEL32 = library("kernel32"); // NON-NLS

    static final StructLayout CAPTURE = Linker.Option.captureStateLayout();
    static final VarHandle LAST_ERROR =
        CAPTURE.varHandle(PathElement.groupElement("GetLastError")); // NON-NLS

    static final MemorySegment DEF_WINDOW_PROC = address(USER32, "DefWindowProcW"); // NON-NLS

    static final MethodHandle GET_MODULE_HANDLE =
        call(KERNEL32, "GetModuleHandleW", FunctionDescriptor.of(ADDRESS, ADDRESS)); // NON-NLS
    static final MethodHandle REGISTER_CLASS =
        captured(USER32, "RegisterClassExW", FunctionDescriptor.of(JAVA_SHORT, ADDRESS));
    static final MethodHandle UNREGISTER_CLASS =
        call(
            USER32,
            "UnregisterClassW", // NON-NLS
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    static final MethodHandle CREATE_WINDOW =
        captured(
            USER32,
            "CreateWindowExW",
            FunctionDescriptor.of(
                ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    static final MethodHandle DESTROY_WINDOW =
        call(USER32, "DestroyWindow", FunctionDescriptor.of(JAVA_INT, ADDRESS)); // NON-NLS
    static final MethodHandle GET_DC =
        call(USER32, "GetDC", FunctionDescriptor.of(ADDRESS, ADDRESS)); // NON-NLS
    static final MethodHandle RELEASE_DC =
        call(USER32, "ReleaseDC", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)); // NON-NLS

    static final MethodHandle CHOOSE_PIXEL_FORMAT =
        call(
            GDI32,
            "ChoosePixelFormat", // NON-NLS
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    static final MethodHandle SET_PIXEL_FORMAT =
        captured(
            GDI32, "SetPixelFormat", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

    static final MethodHandle WGL_CREATE_CONTEXT =
        captured(OPENGL32, "wglCreateContext", FunctionDescriptor.of(ADDRESS, ADDRESS));
    static final MethodHandle WGL_DELETE_CONTEXT =
        call(OPENGL32, "wglDeleteContext", FunctionDescriptor.of(JAVA_INT, ADDRESS)); // NON-NLS
    static final MethodHandle WGL_MAKE_CURRENT =
        captured(OPENGL32, "wglMakeCurrent", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    static final MethodHandle WGL_GET_PROC_ADDRESS =
        call(
            OPENGL32,
            "wglGetProcAddress", // NON-NLS
            FunctionDescriptor.of(ADDRESS, ADDRESS));

    private static SymbolLookup library(String name) {
      try {
        return SymbolLookup.libraryLookup(System.mapLibraryName(name), Arena.global());
      } catch (IllegalArgumentException e) {
        throw new GlException("Cannot load " + name, e);
      }
    }

    private static MemorySegment address(SymbolLookup lookup, String name) {
      return lookup
          .find(name)
          .orElseThrow(() -> new GlException("Win32 entry point not found: " + name));
    }

    private static MethodHandle call(
        SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
      return LINKER.downcallHandle(address(lookup, name), descriptor);
    }

    /** The same, with room for {@code GetLastError}, which is what makes a failure readable. */
    private static MethodHandle captured(
        SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
      return LINKER.downcallHandle(
          address(lookup, name), descriptor, Linker.Option.captureCallState("GetLastError"));
    }

    private Win32() {}
  }

  static long pfd(String field) {
    return PIXEL_FORMAT_DESCRIPTOR.byteOffset(PathElement.groupElement(field));
  }

  static long wndClass(String field) {
    return WNDCLASSEXW.byteOffset(PathElement.groupElement(field));
  }

  private static int lastError(MemorySegment capture) {
    return (int) Win32.LAST_ERROR.get(capture, 0L);
  }

  /**
   * Above OpenGL 1.1 nothing is exported by {@code opengl32.dll}: the driver answers through {@code
   * wglGetProcAddress}, which needs a context to be current — so this lookup is only usable once
   * one is, which is when {@link FfmGl} resolves its handles.
   */
  private static SymbolLookup glLookup() {
    SymbolLookup driver =
        name -> {
          try (Arena call = Arena.ofConfined()) {
            MemorySegment address = procAddress(call, name);
            return isResolved(address) ? Optional.of(address) : Optional.empty();
          } catch (Throwable t) {
            return Optional.empty();
          }
        };
    return driver.or(Win32.OPENGL32);
  }

  private static MemorySegment procAddress(Arena arena, String name) throws Throwable {
    return (MemorySegment)
        Win32.WGL_GET_PROC_ADDRESS.invokeExact(arena.allocateFrom(name, StandardCharsets.US_ASCII));
  }

  /** Some drivers report an unknown entry point as 1, 2, 3 or -1 rather than as a null pointer. */
  private static boolean isResolved(MemorySegment address) {
    long value = address.address();
    return value != 0 && value != 1 && value != 2 && value != 3 && value != -1L;
  }

  // ── Creation ──

  static WglContext create() {
    MemorySegment module = moduleHandle();
    registerWindowClass(module);
    // A message-only window is the cheapest one that can own a pixel format; a driver that turns
    // it down gets a hidden top-level window, which is never shown either.
    GlException refusal = null;
    for (boolean messageOnly : new boolean[] {true, false}) {
      try {
        return open(module, messageOnly);
      } catch (GlException e) {
        refusal = e;
        LOGGER.info(
            "No OpenGL context on a {} window: {}",
            messageOnly ? "message-only" : "hidden top-level", // NON-NLS
            e.getMessage());
      }
    }
    unregisterWindowClass(module);
    throw refusal;
  }

  private static WglContext open(MemorySegment module, boolean messageOnly) {
    MemorySegment window = createWindow(module, messageOnly);
    MemorySegment dc = MemorySegment.NULL;
    MemorySegment legacy = MemorySegment.NULL;
    try {
      dc = getDeviceContext(window);
      setPixelFormat(dc);
      legacy = createLegacyContext(dc);
      makeCurrent(dc, legacy);

      MethodHandle createContextAttribs = contextAttribsArb();
      for (int[] version : GlContext.VERSIONS) {
        MemorySegment core = createCoreContext(createContextAttribs, dc, version[0], version[1]);
        if (MemorySegment.NULL.equals(core)) {
          continue;
        }
        makeCurrent(dc, core);
        deleteContext(legacy);
        LOGGER.info("OpenGL {}.{} core context created through WGL", version[0], version[1]);
        return new WglContext(
            window, dc, core, module, GlContext.isComputeCapable(version[0], version[1]));
      }
      throw new GlException(
          "No OpenGL %d.%d core context could be created through WGL"
              .formatted(GlContext.MINIMUM[0], GlContext.MINIMUM[1]));
    } catch (GlException e) {
      makeCurrentQuietly(MemorySegment.NULL, MemorySegment.NULL);
      deleteContext(legacy);
      releaseDeviceContext(window, dc);
      destroyWindow(window);
      throw e;
    }
  }

  private static MemorySegment moduleHandle() {
    try {
      return (MemorySegment) Win32.GET_MODULE_HANDLE.invokeExact(MemorySegment.NULL);
    } catch (Throwable t) {
      throw new GlException("GetModuleHandleW failed", t);
    }
  }

  private static void registerWindowClass(MemorySegment module) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment name = call.allocateFrom(CLASS_NAME, StandardCharsets.UTF_16LE);
      MemorySegment descriptor = call.allocate(WNDCLASSEXW);
      descriptor.set(JAVA_INT, wndClass("cbSize"), (int) WNDCLASSEXW.byteSize());
      // CS_OWNDC: the device context stays valid for the life of the window.
      descriptor.set(JAVA_INT, wndClass("style"), CS_OWNDC);
      descriptor.set(ADDRESS, wndClass("lpfnWndProc"), Win32.DEF_WINDOW_PROC);
      descriptor.set(ADDRESS, wndClass("hInstance"), module);
      descriptor.set(ADDRESS, wndClass("lpszClassName"), name);

      MemorySegment capture = call.allocate(Win32.CAPTURE);
      short atom = (short) Win32.REGISTER_CLASS.invokeExact(capture, descriptor);
      // 1410 is ERROR_CLASS_ALREADY_EXISTS, which a second start in the same process reaches.
      if (atom == 0 && lastError(capture) != 1410) {
        throw new GlException("RegisterClassExW failed, error " + lastError(capture));
      }
    } catch (GlException e) {
      throw e;
    } catch (Throwable t) {
      throw new GlException("RegisterClassExW failed", t);
    }
  }

  private static void unregisterWindowClass(MemorySegment module) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment name = call.allocateFrom(CLASS_NAME, StandardCharsets.UTF_16LE);
      int ignored = (int) Win32.UNREGISTER_CLASS.invokeExact(name, module);
    } catch (Throwable t) {
      LOGGER.debug("UnregisterClassW failed", t);
    }
  }

  private static MemorySegment createWindow(MemorySegment module, boolean messageOnly) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment name = call.allocateFrom(CLASS_NAME, StandardCharsets.UTF_16LE);
      MemorySegment parent =
          messageOnly ? MemorySegment.ofAddress(HWND_MESSAGE) : MemorySegment.NULL;
      int style = messageOnly ? 0 : WS_OVERLAPPEDWINDOW;
      MemorySegment capture = call.allocate(Win32.CAPTURE);
      MemorySegment window =
          (MemorySegment)
              Win32.CREATE_WINDOW.invokeExact(
                  capture,
                  0,
                  name,
                  name,
                  style,
                  0,
                  0,
                  1,
                  1,
                  parent,
                  MemorySegment.NULL,
                  module,
                  MemorySegment.NULL);
      if (MemorySegment.NULL.equals(window)) {
        throw new GlException("CreateWindowExW failed, error " + lastError(capture));
      }
      return window;
    } catch (GlException e) {
      throw e;
    } catch (Throwable t) {
      throw new GlException("CreateWindowExW failed", t);
    }
  }

  private static MemorySegment getDeviceContext(MemorySegment window) {
    try {
      MemorySegment dc = (MemorySegment) Win32.GET_DC.invokeExact(window);
      if (MemorySegment.NULL.equals(dc)) {
        throw new GlException("This window has no device context");
      }
      return dc;
    } catch (GlException e) {
      throw e;
    } catch (Throwable t) {
      throw new GlException("GetDC failed", t);
    }
  }

  private static void setPixelFormat(MemorySegment dc) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment descriptor = call.allocate(PIXEL_FORMAT_DESCRIPTOR);
      descriptor.set(JAVA_SHORT, pfd("nSize"), (short) PIXEL_FORMAT_DESCRIPTOR.byteSize());
      descriptor.set(JAVA_SHORT, pfd("nVersion"), (short) 1);
      descriptor.set(
          JAVA_INT, pfd("dwFlags"), PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER);
      descriptor.set(JAVA_BYTE, pfd("iPixelType"), PFD_TYPE_RGBA);
      descriptor.set(JAVA_BYTE, pfd("cColorBits"), (byte) 32);
      descriptor.set(JAVA_BYTE, pfd("cAlphaBits"), (byte) 8);
      descriptor.set(JAVA_BYTE, pfd("iLayerType"), PFD_MAIN_PLANE);

      int format = (int) Win32.CHOOSE_PIXEL_FORMAT.invokeExact(dc, descriptor);
      if (format == 0) {
        throw new GlException("No pixel format on this device supports OpenGL");
      }
      MemorySegment capture = call.allocate(Win32.CAPTURE);
      int ok = (int) Win32.SET_PIXEL_FORMAT.invokeExact(capture, dc, format, descriptor);
      if (ok == 0) {
        throw new GlException("SetPixelFormat failed, error " + lastError(capture));
      }
    } catch (GlException e) {
      throw e;
    } catch (Throwable t) {
      throw new GlException("Choosing a pixel format failed", t);
    }
  }

  private static MemorySegment createLegacyContext(MemorySegment dc) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment capture = call.allocate(Win32.CAPTURE);
      MemorySegment context = (MemorySegment) Win32.WGL_CREATE_CONTEXT.invokeExact(capture, dc);
      if (MemorySegment.NULL.equals(context)) {
        throw new GlException("wglCreateContext failed, error " + lastError(capture));
      }
      return context;
    } catch (GlException e) {
      throw e;
    } catch (Throwable t) {
      throw new GlException("wglCreateContext failed", t);
    }
  }

  /** {@code wglCreateContextAttribsARB}, the only way to ask WGL for a core profile. */
  private static MethodHandle contextAttribsArb() {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment address = procAddress(call, "wglCreateContextAttribsARB"); // NON-NLS
      if (!isResolved(address)) {
        throw new GlException(
            "This driver has no WGL_ARB_create_context, so it offers no OpenGL 3.3 core profile");
      }
      return LINKER.downcallHandle(
          address, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    } catch (GlException e) {
      throw e;
    } catch (Throwable t) {
      throw new GlException("Resolving wglCreateContextAttribsARB failed", t);
    }
  }

  private static MemorySegment createCoreContext(
      MethodHandle createContextAttribs, MemorySegment dc, int major, int minor) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment attributes =
          call.allocateFrom(
              JAVA_INT,
              WGL_CONTEXT_MAJOR_VERSION_ARB,
              major,
              WGL_CONTEXT_MINOR_VERSION_ARB,
              minor,
              WGL_CONTEXT_PROFILE_MASK_ARB,
              WGL_CONTEXT_CORE_PROFILE_BIT_ARB,
              0);
      return (MemorySegment) createContextAttribs.invokeExact(dc, MemorySegment.NULL, attributes);
    } catch (Throwable t) {
      throw new GlException("wglCreateContextAttribsARB failed", t);
    }
  }

  private static void makeCurrent(MemorySegment dc, MemorySegment context) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment capture = call.allocate(Win32.CAPTURE);
      int ok = (int) Win32.WGL_MAKE_CURRENT.invokeExact(capture, dc, context);
      if (ok == 0) {
        throw new GlException("wglMakeCurrent failed, error " + lastError(capture));
      }
    } catch (GlException e) {
      throw e;
    } catch (Throwable t) {
      throw new GlException("wglMakeCurrent failed", t);
    }
  }

  private static void makeCurrentQuietly(MemorySegment dc, MemorySegment context) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment capture = call.allocate(Win32.CAPTURE);
      int ignored = (int) Win32.WGL_MAKE_CURRENT.invokeExact(capture, dc, context);
    } catch (Throwable t) {
      LOGGER.debug("wglMakeCurrent failed", t);
    }
  }

  private static void deleteContext(MemorySegment context) {
    if (MemorySegment.NULL.equals(context)) {
      return;
    }
    try {
      int ignored = (int) Win32.WGL_DELETE_CONTEXT.invokeExact(context);
    } catch (Throwable t) {
      LOGGER.debug("wglDeleteContext failed", t);
    }
  }

  private static void releaseDeviceContext(MemorySegment window, MemorySegment dc) {
    if (MemorySegment.NULL.equals(dc)) {
      return;
    }
    try {
      int ignored = (int) Win32.RELEASE_DC.invokeExact(window, dc);
    } catch (Throwable t) {
      LOGGER.debug("ReleaseDC failed", t);
    }
  }

  private static void destroyWindow(MemorySegment window) {
    if (MemorySegment.NULL.equals(window)) {
      return;
    }
    try {
      int ignored = (int) Win32.DESTROY_WINDOW.invokeExact(window);
    } catch (Throwable t) {
      LOGGER.debug("DestroyWindow failed", t);
    }
  }

  // ── GlContext ──

  @Override
  public void makeCurrent() {
    makeCurrent(deviceContext, context);
  }

  @Override
  public void release() {
    makeCurrentQuietly(MemorySegment.NULL, MemorySegment.NULL);
  }

  @Override
  public void destroy() {
    if (destroyed) {
      return;
    }
    destroyed = true;
    release();
    deleteContext(context);
    releaseDeviceContext(window, deviceContext);
    destroyWindow(window);
    unregisterWindowClass(moduleHandle);
    gl.close();
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public String name() {
    return "WGL"; // NON-NLS
  }

  @Override
  public Gl gl() {
    return gl;
  }
}
