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

/**
 * A failure a verb describes rather than only states: it carries the {@link CommandError} a remote
 * caller reads, so an agent knows which verb hands out the handle it got wrong instead of parsing
 * the sentence.
 */
public class CommandFailure extends RuntimeException {

  private final transient CommandError error;

  public CommandFailure(CommandError error) {
    super(error.message());
    this.error = error;
  }

  public CommandError error() {
    return error;
  }

  /** A handle that names nothing here, and the verb that lists the ones that do. */
  public static CommandFailure unknownHandle(String message, String producedBy) {
    return new CommandFailure(CommandError.unknownHandle(message, producedBy));
  }
}
