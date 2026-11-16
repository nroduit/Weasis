/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Map;

/**
 * Replaces this process by a program ({@code execv}), keeping its PID.
 *
 * <p>On macOS the bootstrap runs the launcher set in its own process: LaunchServices delivers the
 * open-file and open-URI events to the process it started, and AppKit hands them to the
 * application only when the executable is the launcher of the bundle. The JVM options of that
 * process come from the read-only {@code .cfg}; to add per-user ones (the AOT cache), stage 1
 * replaces itself by the same launcher with them in {@code JAVA_TOOL_OPTIONS}.
 */
final class Exec {

  private Exec() {}

  /**
   * Sets the environment variables, then replaces the process by {@code command} (the program
   * first, then its arguments). Returns only when the call failed, with a description of the
   * failure; the process is unchanged then, except for the variables.
   */
  static String replace(List<String> command, Map<String, String> env) {
    try {
      Linker linker = Linker.nativeLinker();
      SymbolLookup libc = linker.defaultLookup();
      StructLayout state = Linker.Option.captureStateLayout();
      VarHandle errno = state.varHandle(PathElement.groupElement("errno")); // NON-NLS
      MethodHandle setenv =
          linker.downcallHandle(
              libc.find("setenv").orElseThrow(), // NON-NLS
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT));
      MethodHandle execv =
          linker.downcallHandle(
              libc.find("execv").orElseThrow(), // NON-NLS
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
              Linker.Option.captureCallState("errno")); // NON-NLS
      try (Arena arena = Arena.ofConfined()) {
        for (Map.Entry<String, String> e : env.entrySet()) {
          int set =
              (int)
                  setenv.invokeExact(
                      arena.allocateFrom(e.getKey()), arena.allocateFrom(e.getValue()), 1);
          if (set != 0) {
            return "setenv " + e.getKey() + " failed"; // NON-NLS
          }
        }
        MemorySegment argv = arena.allocate(ValueLayout.ADDRESS, command.size() + 1L);
        for (int i = 0; i < command.size(); i++) {
          argv.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(command.get(i)));
        }
        argv.setAtIndex(ValueLayout.ADDRESS, command.size(), MemorySegment.NULL);
        MemorySegment captured = arena.allocate(state);
        int result =
            (int) execv.invokeExact(captured, arena.allocateFrom(command.getFirst()), argv);
        return "execv returned " + result + ", errno " + (int) errno.get(captured, 0L); // NON-NLS
      }
    } catch (Throwable t) {
      return "execv unavailable: " + t; // NON-NLS
    }
  }

  /**
   * {@code JAVA_TOOL_OPTIONS} with the options appended to its current value; an option holding a
   * space is quoted, which the JVM accepts in this variable.
   */
  static String toolOptions(String current, List<String> options) {
    StringBuilder value = new StringBuilder(current == null ? "" : current.strip());
    for (String option : options) {
      if (!value.isEmpty()) {
        value.append(' ');
      }
      value.append(option.chars().anyMatch(Character::isWhitespace) ? '"' + option + '"' : option);
    }
    return value.toString();
  }
}
