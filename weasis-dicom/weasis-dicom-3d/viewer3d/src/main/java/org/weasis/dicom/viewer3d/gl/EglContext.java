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
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Linux context: a desktop OpenGL core context on the default EGL display, current without any
 * surface when the driver has {@code EGL_KHR_surfaceless_context} and on a 1 × 1 pbuffer otherwise.
 *
 * <p>The default display keeps the GPU the session already uses and honours the PRIME offload
 * variables; when there is no display server at all, the first EGL device is taken instead, which
 * is what makes the renderer usable on a headless build host.
 */
public final class EglContext implements GlContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(EglContext.class);

  private static final Linker LINKER = Linker.nativeLinker();

  private static final int EGL_FALSE = 0;
  private static final int EGL_NONE = 0x3038;
  private static final int EGL_OPENGL_API = 0x30A2;
  private static final int EGL_SURFACE_TYPE = 0x3033;
  private static final int EGL_PBUFFER_BIT = 0x0001;
  private static final int EGL_RENDERABLE_TYPE = 0x3040;
  private static final int EGL_OPENGL_BIT = 0x0008;
  private static final int EGL_RED_SIZE = 0x3024;
  private static final int EGL_GREEN_SIZE = 0x3023;
  private static final int EGL_BLUE_SIZE = 0x3022;
  private static final int EGL_ALPHA_SIZE = 0x3021;
  private static final int EGL_DEPTH_SIZE = 0x3025;
  private static final int EGL_WIDTH = 0x3057;
  private static final int EGL_HEIGHT = 0x3056;
  private static final int EGL_EXTENSIONS = 0x3055;
  private static final int EGL_VENDOR = 0x3053;
  private static final int EGL_CONTEXT_MAJOR_VERSION = 0x3098;
  private static final int EGL_CONTEXT_MINOR_VERSION = 0x30FB;
  private static final int EGL_CONTEXT_OPENGL_PROFILE_MASK = 0x30FD;
  private static final int EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT = 0x0001;
  private static final int EGL_PLATFORM_DEVICE_EXT = 0x313F;
  private static final int EGL_DEVICE_EXT = 0x322C;

  /** Devices probed when the default display lands on a software rasteriser. */
  private static final int MAX_DEVICES = 16;

  private static final String SURFACELESS = "EGL_KHR_surfaceless_context"; // NON-NLS

  /**
   * Either name means {@code eglQueryDevicesEXT} is there: {@code EGL_EXT_device_base} is defined
   * as the union of {@code EGL_EXT_device_enumeration} and {@code EGL_EXT_device_query}, and a
   * driver may advertise only the union.
   */
  private static final List<String> DEVICE_ENUMERATION =
      List.of("EGL_EXT_device_enumeration", "EGL_EXT_device_base"); // NON-NLS

  /** What a device advertises when it rasterises on the processor. */
  private static final String SOFTWARE_DEVICE = "EGL_MESA_device_software"; // NON-NLS

  private static final String LOG_LEVEL = "EGL_LOG_LEVEL"; // NON-NLS

  private static final int STDERR = 2;
  private static final int O_WRONLY = 1;

  private final Arena arena = Arena.ofShared();
  private final MemorySegment display;
  private final MemorySegment context;
  private final MemorySegment surface;
  private final FfmGl gl;
  private final String description;
  private boolean destroyed;

  private EglContext(
      MemorySegment display, MemorySegment context, MemorySegment surface, boolean computeCapable) {
    this.display = display;
    this.context = context;
    this.surface = surface;
    this.gl = new FfmGl(glLookup(), computeCapable);
    this.description =
        "%s — OpenGL %s (%s, EGL %s)"
            .formatted(
                gl.glGetString(Gl.GL_RENDERER),
                gl.glGetString(Gl.GL_VERSION),
                gl.glGetString(Gl.GL_VENDOR),
                queryString(display, EGL_VENDOR));
  }

  // ── EGL entry points ──

  private static final class Egl {
    static final SymbolLookup LIBRARY = openEgl();

    static final MethodHandle GET_ERROR = call("eglGetError", FunctionDescriptor.of(JAVA_INT));
    static final MethodHandle GET_DISPLAY =
        call("eglGetDisplay", FunctionDescriptor.of(ADDRESS, ADDRESS));
    static final MethodHandle INITIALIZE =
        call("eglInitialize", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    static final MethodHandle TERMINATE =
        call("eglTerminate", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    static final MethodHandle BIND_API =
        call("eglBindAPI", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    static final MethodHandle QUERY_STRING =
        call("eglQueryString", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    static final MethodHandle CHOOSE_CONFIG =
        call(
            "eglChooseConfig",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
    static final MethodHandle CREATE_CONTEXT =
        call(
            "eglCreateContext", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    static final MethodHandle DESTROY_CONTEXT =
        call("eglDestroyContext", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    static final MethodHandle CREATE_PBUFFER_SURFACE =
        call("eglCreatePbufferSurface", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    static final MethodHandle DESTROY_SURFACE =
        call("eglDestroySurface", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    static final MethodHandle MAKE_CURRENT =
        call("eglMakeCurrent", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    static final MethodHandle GET_PROC_ADDRESS =
        call("eglGetProcAddress", FunctionDescriptor.of(ADDRESS, ADDRESS));

    /** {@code EGL_EXT_device_query}; absent on a driver that does not expose its devices. */
    static final MethodHandle QUERY_DISPLAY_ATTRIB =
        extension(
            "eglQueryDisplayAttribEXT", // NON-NLS
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

    static final MethodHandle QUERY_DEVICE_STRING =
        extension(
            "eglQueryDeviceStringEXT", // NON-NLS
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

    private static MethodHandle call(String name, FunctionDescriptor descriptor) {
      return LINKER.downcallHandle(
          LIBRARY
              .find(name)
              .orElseThrow(() -> new GlException("EGL entry point not found: " + name)),
          descriptor);
    }

    /** An EXT entry point, which a driver only exposes through {@code eglGetProcAddress}. */
    static MethodHandle extension(String name, FunctionDescriptor descriptor) {
      return procAddress(name).map(a -> LINKER.downcallHandle(a, descriptor)).orElse(null);
    }

    static Optional<MemorySegment> procAddress(String name) {
      try (Arena call = Arena.ofConfined()) {
        MemorySegment address =
            (MemorySegment) GET_PROC_ADDRESS.invokeExact(call.allocateFrom(name));
        return MemorySegment.NULL.equals(address) ? Optional.empty() : Optional.of(address);
      } catch (Throwable t) {
        return Optional.empty();
      }
    }

    private Egl() {}
  }

  /**
   * {@code true} when the environment asks for the driver's own messages. Both of the measures
   * below then stand aside, which is how everything Mesa has to say can be seen again.
   */
  private static boolean driverLogRequested() {
    // Java reads the environment once at start-up, so the value this class sets is not seen here.
    return System.getenv(LOG_LEVEL) != null;
  }

  /**
   * Runs {@code probe} with the standard error of the process pointed at {@code /dev/null}.
   *
   * <p>Enumerating the EGL devices makes Mesa's loader print a line for every device it opens, and
   * unlike its warnings those pass through no logger and obey no variable. The redirection covers
   * the enumeration alone — once per process, for as long as it takes — but standard error belongs
   * to the whole process, so anything another thread writes there in that window is lost with it.
   * When the descriptors cannot be moved the probe simply runs as it would have.
   */
  private static <T> T withoutStandardError(Supplier<T> probe) {
    if (driverLogRequested()) {
      return probe.get();
    }
    System.err.flush();
    int devNull = posix(Posix.OPEN, "/dev/null"); // NON-NLS
    int saved = devNull < 0 ? -1 : posix(Posix.DUP, STDERR);
    boolean redirected = saved >= 0 && posix(Posix.DUP2, devNull, STDERR) >= 0;
    try {
      return probe.get();
    } finally {
      if (redirected) {
        posix(Posix.DUP2, saved, STDERR);
      }
      closeDescriptor(saved);
      closeDescriptor(devNull);
    }
  }

  private static void closeDescriptor(int descriptor) {
    if (descriptor >= 0) {
      posix(Posix.CLOSE, descriptor);
    }
  }

  private static int posix(MethodHandle handle, String path) {
    try (Arena arena = Arena.ofConfined()) {
      return (int) handle.invokeExact(arena.allocateFrom(path), O_WRONLY, 0);
    } catch (Throwable t) {
      return -1;
    }
  }

  private static int posix(MethodHandle handle, int argument) {
    try {
      return (int) handle.invokeExact(argument);
    } catch (Throwable t) {
      return -1;
    }
  }

  private static int posix(MethodHandle handle, int first, int second) {
    try {
      return (int) handle.invokeExact(first, second);
    } catch (Throwable t) {
      return -1;
    }
  }

  /** The few file-descriptor calls the redirection needs. */
  private static final class Posix {
    static final MethodHandle OPEN =
        libc(
            "open", // NON-NLS
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT),
            Linker.Option.firstVariadicArg(2));
    static final MethodHandle DUP = libc("dup", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    static final MethodHandle DUP2 =
        libc("dup2", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT)); // NON-NLS
    static final MethodHandle CLOSE = libc("close", FunctionDescriptor.of(JAVA_INT, JAVA_INT));

    private static MethodHandle libc(
        String name, FunctionDescriptor descriptor, Linker.Option... options) {
      return LINKER.downcallHandle(
          LINKER
              .defaultLookup()
              .find(name)
              .orElseThrow(() -> new GlException("C entry point not found: " + name)),
          descriptor,
          options);
    }

    private Posix() {}
  }

  private static SymbolLookup openEgl() {
    quietenDriverLog();
    return library("libEGL.so.1"); // NON-NLS
  }

  /**
   * Enumerating the EGL devices makes a driver print a block of warnings for every device it cannot
   * open — on a machine that also carries a proprietary driver, that means the very card the
   * renderer is about to use. Those attempts are ours and their outcome is reported by this class,
   * so the driver is asked to say nothing below {@code fatal}.
   *
   * <p>Setting {@value #LOG_LEVEL} in the environment brings them back: the value is never
   * overwritten. This runs once and before the library is loaded, since changing the environment is
   * only safe while nothing else is reading it.
   */
  private static void quietenDriverLog() {
    if (driverLogRequested()) {
      return;
    }
    try (Arena call = Arena.ofConfined()) {
      MethodHandle setenv =
          LINKER.downcallHandle(
              LINKER.defaultLookup().find("setenv").orElseThrow(), // NON-NLS
              FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
      int keepExistingValue = 0;
      int ignored =
          (int)
              setenv.invokeExact(
                  call.allocateFrom(LOG_LEVEL),
                  call.allocateFrom("fatal"), // NON-NLS
                  keepExistingValue);
    } catch (Throwable t) {
      LOGGER.debug("Cannot set {}", LOG_LEVEL, t);
    }
  }

  private static SymbolLookup library(String name) {
    try {
      return SymbolLookup.libraryLookup(name, Arena.global());
    } catch (IllegalArgumentException e) {
      throw new GlException("Cannot load " + name, e);
    }
  }

  // libOpenGL is the glvnd entry point and exports the whole core API; libGL is the older name.
  // Extension entry points are not exported: the driver hands them out through eglGetProcAddress.
  private static SymbolLookup glLookup() {
    SymbolLookup library;
    try {
      library = library("libOpenGL.so.0"); // NON-NLS
    } catch (GlException e) {
      LOGGER.debug("libOpenGL.so.0 is not available, falling back to libGL.so.1");
      library = library("libGL.so.1"); // NON-NLS
    }
    return library.or(Egl::procAddress);
  }

  // ── Creation ──

  static EglContext create() {
    if (call(Egl.BIND_API, EGL_OPENGL_API) == EGL_FALSE) {
      throw new GlException(
          "This EGL driver does not offer desktop OpenGL, only OpenGL ES" + lastError());
    }
    GlException failure = null;
    EglContext software = null;
    for (MemorySegment display : withoutStandardError(() -> ordered(displays()))) {
      try {
        EglContext context = open(display);
        if (!context.looksSoftware()) {
          return context;
        }
        // A software rasteriser is not what a volume renderer needs, but it is better than
        // nothing and it is what a build host offers, so it is kept only if no device answers.
        if (software == null) {
          software = context;
          LOGGER.info(
              "EGL display on a software renderer ({}), looking for a GPU", context.description());
        } else {
          context.destroy();
        }
      } catch (GlException e) {
        failure = e;
      }
    }
    if (software != null) {
      return software;
    }
    throw failure != null
        ? failure
        : new GlException("No EGL display offers a desktop OpenGL context" + lastError());
  }

  /**
   * Puts the displays whose device calls itself a rasteriser last. Asking the device is far cheaper
   * than finding out by creating a context and reading {@code GL_RENDERER}.
   */
  private static List<MemorySegment> ordered(List<MemorySegment> displays) {
    List<MemorySegment> hardware = new ArrayList<>();
    List<MemorySegment> software = new ArrayList<>();
    for (MemorySegment display : displays) {
      (isSoftwareDevice(display) ? software : hardware).add(display);
    }
    if (!software.isEmpty() && !hardware.isEmpty()) {
      // Worth saying: enumerating the devices is what makes a driver that cannot open the card
      // print on the console, and this is the line those messages belong to.
      LOGGER.info(
          "{} of the {} EGL displays rasterise on the processor; a GPU device is used instead",
          software.size(),
          displays.size());
    }
    hardware.addAll(software);
    return hardware;
  }

  private static boolean isSoftwareDevice(MemorySegment display) {
    if (Egl.QUERY_DISPLAY_ATTRIB == null || Egl.QUERY_DEVICE_STRING == null) {
      return false;
    }
    try (Arena call = Arena.ofConfined()) {
      MemorySegment attribute = call.allocate(JAVA_LONG);
      if ((int) Egl.QUERY_DISPLAY_ATTRIB.invokeExact(display, EGL_DEVICE_EXT, attribute)
          == EGL_FALSE) {
        return false;
      }
      MemorySegment device = MemorySegment.ofAddress(attribute.get(JAVA_LONG, 0));
      if (MemorySegment.NULL.equals(device)) {
        return false;
      }
      MemorySegment extensions =
          (MemorySegment) Egl.QUERY_DEVICE_STRING.invokeExact(device, EGL_EXTENSIONS);
      return !MemorySegment.NULL.equals(extensions)
          && extensions
              .reinterpret(Long.MAX_VALUE)
              .getString(0, StandardCharsets.UTF_8)
              .contains(SOFTWARE_DEVICE);
    } catch (Throwable t) {
      LOGGER.debug("Querying the device of an EGL display", t);
      return false;
    }
  }

  /**
   * The displays to try, in order: the one of the session, which honours the PRIME offload
   * variables and the GPU the desktop already uses, then each EGL device, which is how a GPU is
   * reached with no display server — or when the session's display resolves to a rasteriser, as it
   * does when the window system and the GPU driver disagree.
   */
  private static List<MemorySegment> displays() {
    List<MemorySegment> displays = new ArrayList<>();
    MemorySegment session = getDisplay(MemorySegment.NULL);
    if (!MemorySegment.NULL.equals(session) && initialize(session)) {
      displays.add(session);
    }
    displays.addAll(deviceDisplays());
    if (displays.isEmpty()) {
      throw new GlException(
          "No EGL display: neither a display server nor an EGL device is reachable" + lastError());
    }
    return displays;
  }

  private static List<MemorySegment> deviceDisplays() {
    String extensions = clientExtensions();
    if (DEVICE_ENUMERATION.stream().noneMatch(extensions::contains)) {
      return List.of();
    }
    MethodHandle queryDevices =
        Egl.extension(
            "eglQueryDevicesEXT", // NON-NLS
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    MethodHandle platformDisplay =
        Egl.extension(
            "eglGetPlatformDisplayEXT", // NON-NLS
            FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    if (queryDevices == null || platformDisplay == null) {
      return List.of();
    }
    List<MemorySegment> displays = new ArrayList<>();
    try (Arena call = Arena.ofConfined()) {
      MemorySegment devices = call.allocate(ADDRESS, MAX_DEVICES);
      MemorySegment count = call.allocate(JAVA_INT);
      if ((int) queryDevices.invokeExact(MAX_DEVICES, devices, count) == EGL_FALSE) {
        return List.of();
      }
      for (int i = 0; i < Math.min(count.get(JAVA_INT, 0), MAX_DEVICES); i++) {
        MemorySegment display =
            (MemorySegment)
                platformDisplay.invokeExact(
                    EGL_PLATFORM_DEVICE_EXT, devices.getAtIndex(ADDRESS, i), MemorySegment.NULL);
        if (!MemorySegment.NULL.equals(display) && initialize(display)) {
          displays.add(display);
        }
      }
    } catch (Throwable t) {
      LOGGER.debug("Enumerating the EGL devices", t);
    }
    return displays;
  }

  /** Creates the best context this display grants, highest version first. */
  private static EglContext open(MemorySegment display) {
    boolean surfaceless = extensions(display).contains(SURFACELESS);
    MemorySegment config = chooseConfig(display);
    for (int[] version : GlContext.VERSIONS) {
      MemorySegment context = createContext(display, config, version[0], version[1]);
      if (MemorySegment.NULL.equals(context)) {
        continue;
      }
      MemorySegment surface = surfaceless ? MemorySegment.NULL : createPbuffer(display, config);
      if (makeCurrent(display, surface, context)) {
        LOGGER.info(
            "OpenGL {}.{} core context created through EGL{}",
            version[0],
            version[1],
            surfaceless ? ", surfaceless" : ", on a 1x1 pbuffer"); // NON-NLS
        return new EglContext(
            display, context, surface, GlContext.isComputeCapable(version[0], version[1]));
      }
      destroyContext(display, context, surface);
    }
    throw new GlException(
        "No OpenGL %d.%d core context could be created through EGL%s"
            .formatted(GlContext.MINIMUM[0], GlContext.MINIMUM[1], lastError()));
  }

  private static MemorySegment chooseConfig(MemorySegment display) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment attributes =
          call.allocateFrom(
              JAVA_INT,
              EGL_SURFACE_TYPE,
              EGL_PBUFFER_BIT,
              EGL_RENDERABLE_TYPE,
              EGL_OPENGL_BIT,
              EGL_RED_SIZE,
              8,
              EGL_GREEN_SIZE,
              8,
              EGL_BLUE_SIZE,
              8,
              EGL_ALPHA_SIZE,
              8,
              EGL_DEPTH_SIZE,
              0,
              EGL_NONE);
      MemorySegment configs = call.allocate(ADDRESS);
      MemorySegment count = call.allocate(JAVA_INT);
      int ok = (int) Egl.CHOOSE_CONFIG.invokeExact(display, attributes, configs, 1, count);
      if (ok == EGL_FALSE || count.get(JAVA_INT, 0) < 1) {
        throw new GlException(
            "No EGL configuration accepts an RGBA8 desktop OpenGL pbuffer" + lastError());
      }
      return configs.get(ADDRESS, 0);
    } catch (GlException e) {
      throw e;
    } catch (Throwable t) {
      throw new GlException("eglChooseConfig failed", t);
    }
  }

  private static MemorySegment createContext(
      MemorySegment display, MemorySegment config, int major, int minor) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment attributes =
          call.allocateFrom(
              JAVA_INT,
              EGL_CONTEXT_MAJOR_VERSION,
              major,
              EGL_CONTEXT_MINOR_VERSION,
              minor,
              EGL_CONTEXT_OPENGL_PROFILE_MASK,
              EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT,
              EGL_NONE);
      return (MemorySegment)
          Egl.CREATE_CONTEXT.invokeExact(display, config, MemorySegment.NULL, attributes);
    } catch (Throwable t) {
      throw new GlException("eglCreateContext failed", t);
    }
  }

  private static MemorySegment createPbuffer(MemorySegment display, MemorySegment config) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment attributes = call.allocateFrom(JAVA_INT, EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE);
      return (MemorySegment) Egl.CREATE_PBUFFER_SURFACE.invokeExact(display, config, attributes);
    } catch (Throwable t) {
      throw new GlException("eglCreatePbufferSurface failed", t);
    }
  }

  private static boolean makeCurrent(
      MemorySegment display, MemorySegment surface, MemorySegment context) {
    try {
      return (int) Egl.MAKE_CURRENT.invokeExact(display, surface, surface, context) != EGL_FALSE;
    } catch (Throwable t) {
      throw new GlException("eglMakeCurrent failed", t);
    }
  }

  private static void destroyContext(
      MemorySegment display, MemorySegment context, MemorySegment surface) {
    try {
      int ignored = (int) Egl.DESTROY_CONTEXT.invokeExact(display, context);
      if (!MemorySegment.NULL.equals(surface)) {
        ignored = (int) Egl.DESTROY_SURFACE.invokeExact(display, surface);
      }
    } catch (Throwable t) {
      LOGGER.warn("Releasing an EGL context", t);
    }
  }

  private static MemorySegment getDisplay(MemorySegment nativeDisplay) {
    try {
      return (MemorySegment) Egl.GET_DISPLAY.invokeExact(nativeDisplay);
    } catch (Throwable t) {
      throw new GlException("eglGetDisplay failed", t);
    }
  }

  private static boolean initialize(MemorySegment display) {
    try (Arena call = Arena.ofConfined()) {
      MemorySegment major = call.allocate(JAVA_INT);
      MemorySegment minor = call.allocate(JAVA_INT);
      return (int) Egl.INITIALIZE.invokeExact(display, major, minor) != EGL_FALSE;
    } catch (Throwable t) {
      throw new GlException("eglInitialize failed", t);
    }
  }

  /** The extensions of the EGL client itself, queried without a display. */
  private static String clientExtensions() {
    String value = queryString(MemorySegment.NULL, EGL_EXTENSIONS);
    return value == null ? "" : value;
  }

  private static String extensions(MemorySegment display) {
    String value = queryString(display, EGL_EXTENSIONS);
    return value == null ? "" : value;
  }

  private static String queryString(MemorySegment display, int name) {
    try {
      MemorySegment value = (MemorySegment) Egl.QUERY_STRING.invokeExact(display, name);
      return MemorySegment.NULL.equals(value)
          ? null
          : value.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
    } catch (Throwable t) {
      throw new GlException("eglQueryString failed", t);
    }
  }

  /** The last EGL error, as a suffix for a failure message. */
  private static String lastError() {
    try {
      return " (EGL error 0x%s)".formatted(Integer.toHexString((int) Egl.GET_ERROR.invokeExact()));
    } catch (Throwable t) {
      return "";
    }
  }

  private static int call(MethodHandle handle, int argument) {
    try {
      return (int) handle.invokeExact(argument);
    } catch (Throwable t) {
      throw new GlException("An EGL call failed", t);
    }
  }

  // ── GlContext ──

  @Override
  public void makeCurrent() {
    if (!makeCurrent(display, surface, context)) {
      throw new GlException("The EGL context cannot be made current on this thread");
    }
  }

  @Override
  public void release() {
    makeCurrent(display, MemorySegment.NULL, MemorySegment.NULL);
  }

  @Override
  public void destroy() {
    if (destroyed) {
      return;
    }
    destroyed = true;
    release();
    destroyContext(display, context, surface);
    try {
      int ignored = (int) Egl.TERMINATE.invokeExact(display);
    } catch (Throwable t) {
      LOGGER.warn("Terminating the EGL display", t);
    }
    gl.close();
    arena.close();
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public String name() {
    return "EGL"; // NON-NLS
  }

  /** Same criterion as {@code OpenGLInfo}: a rasteriser cannot carry a volume renderer. */
  private boolean looksSoftware() {
    String name = gl.glGetString(Gl.GL_RENDERER);
    if (name == null) {
      return false;
    }
    String lower = name.toLowerCase(Locale.ROOT);
    return lower.contains("llvmpipe") // NON-NLS
        || lower.contains("softpipe") // NON-NLS
        || lower.contains("swrast") // NON-NLS
        || lower.contains("swiftshader"); // NON-NLS
  }

  @Override
  public Gl gl() {
    return gl;
  }
}
