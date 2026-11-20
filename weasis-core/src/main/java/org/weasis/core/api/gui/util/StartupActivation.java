/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets an application launched by Weasis on Linux (a browser opening the help, a file manager, a
 * system viewer) come to the front.
 *
 * <p>GNOME and other compositors only raise a launched application when the launcher hands it an
 * activation token ({@code XDG_ACTIVATION_TOKEN} on Wayland, {@code DESKTOP_STARTUP_ID} on X11);
 * without one, a browser that is already running opens the page behind the Weasis window. Weasis
 * runs on X11 (Xwayland in a Wayland session), so it announces a freedesktop startup-notification
 * sequence on the X display, stamped with the current server time, and passes its identifier as
 * both variables. Mutter accepts such an X11 sequence as an activation token for Wayland clients.
 *
 * <p>The variables inherited from the process that started Weasis are always removed: they belong
 * to an earlier, already consumed launch and would only be ignored.
 */
public final class StartupActivation {
  private static final Logger LOGGER = LoggerFactory.getLogger(StartupActivation.class);

  private static final String ACTIVATION_TOKEN = "XDG_ACTIVATION_TOKEN"; // NON-NLS
  private static final String STARTUP_ID = "DESKTOP_STARTUP_ID"; // NON-NLS

  private static final long PROPERTY_CHANGE_MASK = 1L << 22;
  private static final int CLIENT_MESSAGE = 33;
  private static final int PROPERTY_NOTIFY = 28;
  private static final long XA_STRING = 31;
  private static final int PROP_MODE_APPEND = 2;

  /** Size of the XEvent union on LP64. */
  private static final int XEVENT_SIZE = 192;

  /** Bytes of payload carried by one client message of format 8. */
  private static final int CHUNK = 20;

  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  private static volatile X11 x11;
  private static volatile boolean unavailable;

  private StartupActivation() {}

  /**
   * Prepares the environment of a process about to be started: drops inherited activation variables
   * and, on an X11 display, sets a fresh startup identifier.
   */
  public static void prepare(ProcessBuilder builder) {
    if (!SystemInfo.isLinux) {
      return;
    }
    Map<String, String> env = builder.environment();
    env.remove(ACTIVATION_TOKEN);
    env.remove(STARTUP_ID);
    String id = newStartupId();
    if (id != null) {
      env.put(ACTIVATION_TOKEN, id);
      env.put(STARTUP_ID, id);
    }
  }

  /**
   * Announces a new startup sequence on the X display.
   *
   * @return its identifier, or {@code null} when there is no X11 display or the announce failed
   */
  static String newStartupId() {
    X11 lib = library();
    if (lib == null) {
      return null;
    }
    try {
      return lib.announce();
    } catch (Throwable e) { // NOSONAR a failing native call must never prevent the launch
      LOGGER.debug("Cannot announce a startup sequence", e);
      return null;
    }
  }

  private static X11 library() {
    if (unavailable) {
      return null;
    }
    X11 lib = x11;
    if (lib == null) {
      synchronized (StartupActivation.class) {
        lib = x11;
        if (lib == null && !unavailable) {
          try {
            if (GraphicsEnvironment.isHeadless()
                || System.getenv("DISPLAY") == null // NON-NLS
                || !"sun.awt.X11.XToolkit" // NON-NLS
                    .equals(Toolkit.getDefaultToolkit().getClass().getName())) {
              unavailable = true;
              return null;
            }
            lib = new X11();
            x11 = lib;
          } catch (Throwable e) { // NOSONAR no libX11 or no native access: launch without token
            LOGGER.debug("X11 startup notification is not available", e);
            unavailable = true;
            return null;
          }
        }
      }
    }
    return lib;
  }

  /** The few Xlib calls needed to send a startup-notification message (LP64 layouts). */
  private static final class X11 {
    private static final ValueLayout.OfLong UL = ValueLayout.JAVA_LONG;
    private static final ValueLayout.OfInt I = ValueLayout.JAVA_INT;
    private static final AddressLayout P = ValueLayout.ADDRESS;

    private final MethodHandle openDisplay;
    private final MethodHandle closeDisplay;
    private final MethodHandle defaultRoot;
    private final MethodHandle defaultScreen;
    private final MethodHandle createWindow;
    private final MethodHandle destroyWindow;
    private final MethodHandle selectInput;
    private final MethodHandle internAtom;
    private final MethodHandle changeProperty;
    private final MethodHandle windowEvent;
    private final MethodHandle sendEvent;
    private final MethodHandle flush;

    X11() {
      Linker linker = Linker.nativeLinker();
      SymbolLookup lookup = SymbolLookup.libraryLookup("libX11.so.6", Arena.global()); // NON-NLS
      openDisplay = handle(linker, lookup, "XOpenDisplay", FunctionDescriptor.of(P, P));
      closeDisplay = handle(linker, lookup, "XCloseDisplay", FunctionDescriptor.of(I, P));
      defaultRoot = handle(linker, lookup, "XDefaultRootWindow", FunctionDescriptor.of(UL, P));
      defaultScreen = handle(linker, lookup, "XDefaultScreen", FunctionDescriptor.of(I, P));
      createWindow =
          handle(
              linker,
              lookup,
              "XCreateSimpleWindow",
              FunctionDescriptor.of(UL, P, UL, I, I, I, I, I, UL, UL));
      destroyWindow = handle(linker, lookup, "XDestroyWindow", FunctionDescriptor.of(I, P, UL));
      selectInput = handle(linker, lookup, "XSelectInput", FunctionDescriptor.of(I, P, UL, UL));
      internAtom = handle(linker, lookup, "XInternAtom", FunctionDescriptor.of(UL, P, P, I));
      changeProperty =
          handle(
              linker,
              lookup,
              "XChangeProperty",
              FunctionDescriptor.of(I, P, UL, UL, UL, I, I, P, I));
      windowEvent = handle(linker, lookup, "XWindowEvent", FunctionDescriptor.of(I, P, UL, UL, P));
      sendEvent = handle(linker, lookup, "XSendEvent", FunctionDescriptor.of(I, P, UL, I, UL, P));
      flush = handle(linker, lookup, "XFlush", FunctionDescriptor.of(I, P));
    }

    private static MethodHandle handle(
        Linker linker, SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
      return linker.downcallHandle(lookup.findOrThrow(name), descriptor);
    }

    /**
     * Opens a private connection, reads the server time through a property change on a hidden
     * window, then broadcasts {@code new: ID=…_TIME<time>} to the root window as the
     * startup-notification specification describes.
     */
    String announce() throws Throwable {
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment display = (MemorySegment) openDisplay.invokeExact(MemorySegment.NULL);
        if (display.equals(MemorySegment.NULL)) {
          return null;
        }
        try {
          long root = (long) defaultRoot.invokeExact(display);
          int screen = (int) defaultScreen.invokeExact(display);
          long window = (long) createWindow.invokeExact(display, root, -1, -1, 1, 1, 0, 0L, 0L);
          try {
            long time = serverTime(arena, display, window);
            String id =
                "weasis-" // NON-NLS
                    + ProcessHandle.current().pid()
                    + "-"
                    + SEQUENCE.getAndIncrement()
                    + "_TIME" // NON-NLS
                    + time;
            String message = "new: ID=" + id + " NAME=Weasis SCREEN=" + screen; // NON-NLS
            broadcast(arena, display, root, window, message);
            return id;
          } finally {
            int ignored = (int) destroyWindow.invokeExact(display, window);
          }
        } finally {
          int ignored = (int) closeDisplay.invokeExact(display); // flushes pending requests
        }
      }
    }

    private long serverTime(Arena arena, MemorySegment display, long window) throws Throwable {
      int ignored = (int) selectInput.invokeExact(display, window, PROPERTY_CHANGE_MASK);
      long atom =
          (long) internAtom.invokeExact(display, arena.allocateFrom("_WEASIS_TIMESTAMP"), 0);
      ignored =
          (int)
              changeProperty.invokeExact(
                  display, window, atom, XA_STRING, 8, PROP_MODE_APPEND, arena.allocate(1), 0);
      MemorySegment event = arena.allocate(XEVENT_SIZE, 8);
      do {
        ignored = (int) windowEvent.invokeExact(display, window, PROPERTY_CHANGE_MASK, event);
      } while (event.get(I, 0) != PROPERTY_NOTIFY);
      return event.get(UL, 48) & 0xffffffffL; // XPropertyEvent.time
    }

    private void broadcast(
        Arena arena, MemorySegment display, long root, long window, String message)
        throws Throwable {
      long begin =
          (long) internAtom.invokeExact(display, arena.allocateFrom("_NET_STARTUP_INFO_BEGIN"), 0);
      long more =
          (long) internAtom.invokeExact(display, arena.allocateFrom("_NET_STARTUP_INFO"), 0);
      byte[] bytes = (message + '\0').getBytes(StandardCharsets.UTF_8);
      MemorySegment event = arena.allocate(XEVENT_SIZE, 8);
      for (int offset = 0; offset < bytes.length; offset += CHUNK) {
        event.fill((byte) 0);
        event.set(I, 0, CLIENT_MESSAGE);
        event.set(P, 24, display);
        event.set(UL, 32, window);
        event.set(UL, 40, offset == 0 ? begin : more);
        event.set(I, 48, 8); // format
        MemorySegment.copy(
            bytes,
            offset,
            event,
            ValueLayout.JAVA_BYTE,
            56,
            Math.min(CHUNK, bytes.length - offset));
        int ignored = (int) sendEvent.invokeExact(display, root, 0, PROPERTY_CHANGE_MASK, event);
      }
      int ignored = (int) flush.invokeExact(display);
    }
  }
}
