/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-execution context bound by {@link CommandManager} while a command runs: its origin and the
 * files it produces. Commands read it with {@link #current()}.
 */
public final class CommandContext {

  private static final ScopedValue<CommandContext> CURRENT = ScopedValue.newInstance();

  /**
   * The files the commands of this session wrote. A remote caller may overwrite one of them, since
   * it produced it, but not a file that was already there: it cannot know whose it is.
   */
  private static final Set<Path> WRITTEN = ConcurrentHashMap.newKeySet();

  private static final int MAX_WRITTEN = 4096;

  private final CommandOrigin origin;
  private final boolean assumeYes;
  private final CommandPolicy policy;
  private final List<Path> files = Collections.synchronizedList(new ArrayList<>());
  private volatile CommandError error;
  private final List<String> notices = Collections.synchronizedList(new ArrayList<>());

  public CommandContext(CommandOrigin origin) {
    this(origin, false);
  }

  public CommandContext(CommandOrigin origin, boolean assumeYes) {
    this(origin, assumeYes, CommandPolicy.defaults());
  }

  public CommandContext(CommandOrigin origin, boolean assumeYes, CommandPolicy policy) {
    this.origin = Objects.requireNonNull(origin);
    this.assumeYes = assumeYes;
    this.policy = Objects.requireNonNull(policy);
  }

  /** Whether a command is running on this thread. */
  public static boolean isBound() {
    return CURRENT.isBound();
  }

  /** The context of the running command, or a throwaway local one when none is bound. */
  public static CommandContext current() {
    return CURRENT.isBound() ? CURRENT.get() : new CommandContext(CommandOrigin.LAUNCH_ARGS);
  }

  /** Runs {@code op} with {@code context} bound as the current one. */
  public static <T, X extends Throwable> T runWith(
      CommandContext context, ScopedValue.CallableOp<T, X> op) throws X {
    return ScopedValue.where(CURRENT, context).call(op);
  }

  public CommandOrigin origin() {
    return origin;
  }

  public boolean isRemote() {
    return origin == CommandOrigin.REMOTE;
  }

  /** What this instance allows the caller to do; checked before the verb runs. */
  public CommandPolicy policy() {
    return policy;
  }

  /** Whether the confirmations the command would ask the user are answered yes without a dialog. */
  public boolean assumeYes() {
    return assumeYes;
  }

  /** Declares a file written by the command so the caller can find it. */
  public void addFile(Path file) {
    if (file != null) {
      Path absolute = file.toAbsolutePath().normalize();
      files.add(absolute);
      if (WRITTEN.size() < MAX_WRITTEN) {
        WRITTEN.add(absolute);
      }
    }
  }

  /** Records a line the caller should read next to the result, such as a deprecated spelling. */
  public void addNotice(String notice) {
    if (notice != null && !notice.isBlank()) {
      notices.add(notice);
    }
  }

  public List<String> notices() {
    return List.copyOf(notices);
  }

  /** Records why the command failed, so a remote caller reads a structured body, not a line. */
  public void setError(CommandError error) {
    this.error = error;
  }

  /** Why the command failed, or {@code null} when it did not, or when nothing described it. */
  public CommandError error() {
    return error;
  }

  /** Whether a command of this session wrote that file. */
  public static boolean wasWritten(Path file) {
    return file != null && WRITTEN.contains(file.toAbsolutePath().normalize());
  }

  public List<Path> files() {
    return List.copyOf(files);
  }
}
